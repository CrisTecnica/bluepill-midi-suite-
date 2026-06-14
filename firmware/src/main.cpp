/*
 * BluePill MIDI Trigger v2 — STM32F103C8T6
 * 8 piezos (PA0..PA7) -> USB composto: MIDI class-compliant + Serial CDC
 *
 *   MIDI  -> vai direto para o sampler de bateria (Hydrogen, EZdrummer...)
 *   CDC   -> canal de configuração em tempo real (app Tauri "configurator")
 *
 * Protocolo serial (linhas terminadas em \n):
 *   GET                      -> devolve JSON com a config atual
 *   SET <KEY> <IDX> <VAL>    -> altera parâmetro em RAM (IDX = pad, 0 p/ globais)
 *        KEYs: TH NOTE GAMMA MASK SCAN LEN PMAX CH
 *   SAVE                     -> persiste na flash (EEPROM emulada)
 *   MON 0|1                  -> liga/desliga telemetria de hits
 *
 * Telemetria (quando MON 1), uma linha JSON por hit:
 *   {"e":"hit","p":<pad>,"peak":<0..4095>,"vel":<1..127>}
 */

#include <USBComposite.h>
#include <EEPROM.h>
#include <math.h>
#include <string.h>
#include <stdio.h>

#define NUM_PADS     8
#define CFG_MAGIC    0xCAFE
#define CFG_VERSION  1

const uint8 PAD_PIN[NUM_PADS] = { PA0, PA1, PA2, PA3, PA4, PA5, PA6, PA7 };

// ---------------- Configuração em runtime ----------------
struct Config {
  uint16 th[NUM_PADS];    // threshold de disparo (0..4095)
  uint16 note[NUM_PADS];  // nota MIDI (0..127)
  uint16 gammaX100;       // curva de velocity x100 (60 = 0.60)
  uint16 maskMs;          // anti-retrigger (ms)
  uint16 scanUs;          // janela de captura de pico (us)
  uint16 noteLenMs;       // atraso até o Note Off (ms)
  uint16 peakMax;         // leitura ADC que mapeia p/ velocity 127
  uint16 channel;         // canal MIDI (0..15)
};

Config cfg;

static void cfgDefaults() {
  const uint16 defNotes[NUM_PADS] = { 36, 38, 42, 46, 48, 45, 49, 51 };
  for (int i = 0; i < NUM_PADS; i++) { cfg.th[i] = 80; cfg.note[i] = defNotes[i]; }
  cfg.gammaX100 = 60;
  cfg.maskMs    = 30;
  cfg.scanUs    = 2500;
  cfg.noteLenMs = 40;
  cfg.peakMax   = 3500;
  cfg.channel   = 0;
}

// ---------------- Persistência (flash, EEPROM emulada) ----------------
// NOTA: PageBase deve ficar fora da região de código (inicializado após USB)
static bool cfgSave() {
  const uint16 *w = (const uint16 *)&cfg;
  const int n = sizeof(Config) / 2;
  if (EEPROM.write(0, CFG_MAGIC)   != EEPROM_OK) return false;
  if (EEPROM.write(1, CFG_VERSION) != EEPROM_OK) return false;
  for (int i = 0; i < n; i++)
    if (EEPROM.write(2 + i, w[i]) != EEPROM_OK) return false;
  return true;
}

static bool cfgLoad() {
  uint16 v;
  if (EEPROM.read(0, &v) != EEPROM_OK || v != CFG_MAGIC)   return false;
  if (EEPROM.read(1, &v) != EEPROM_OK || v != CFG_VERSION) return false;
  uint16 *w = (uint16 *)&cfg;
  const int n = sizeof(Config) / 2;
  for (int i = 0; i < n; i++)
    if (EEPROM.read(2 + i, &w[i]) != EEPROM_OK) return false;
  return true;
}

// ---------------- USB composto ----------------
USBMIDI midi;
USBCompositeSerial CompositeSerial;

// ---------------- Máquina de estados dos pads ----------------
enum PadState : uint8 { IDLE, SCANNING, MASKED };

struct Pad {
  PadState state;
  uint16   peak;
  uint32   tScanStartUs;
  uint32   tMaskStartMs;
  uint32   tNoteOffMs;
  bool     noteOn;
};

Pad pads[NUM_PADS];
bool   monitor    = false;
uint32 ledOffAtMs = 0;
uint32 lastHbMs   = 0;

static uint8 peakToVelocity(uint16 peak, uint16 threshold) {
  if (peak <= threshold) return 1;
  float gamma = cfg.gammaX100 / 100.0f;
  float norm  = (float)(peak - threshold) / (float)(cfg.peakMax - threshold);
  if (norm > 1.0f) norm = 1.0f;
  float v = powf(norm, gamma) * 126.0f + 1.0f;
  if (v > 127.0f) v = 127.0f;
  return (uint8)v;
}

// ---------------- Protocolo serial ----------------
char  rxBuf[64];
uint8 rxLen = 0;

static void sendConfig() {
  CompositeSerial.print("{\"th\":[");
  for (int i = 0; i < NUM_PADS; i++) {
    CompositeSerial.print(cfg.th[i]);
    if (i < NUM_PADS - 1) CompositeSerial.print(',');
  }
  CompositeSerial.print("],\"note\":[");
  for (int i = 0; i < NUM_PADS; i++) {
    CompositeSerial.print(cfg.note[i]);
    if (i < NUM_PADS - 1) CompositeSerial.print(',');
  }
  CompositeSerial.print("],\"gamma\":");  CompositeSerial.print(cfg.gammaX100);
  CompositeSerial.print(",\"mask\":");    CompositeSerial.print(cfg.maskMs);
  CompositeSerial.print(",\"scan\":");    CompositeSerial.print(cfg.scanUs);
  CompositeSerial.print(",\"len\":");     CompositeSerial.print(cfg.noteLenMs);
  CompositeSerial.print(",\"pmax\":");    CompositeSerial.print(cfg.peakMax);
  CompositeSerial.print(",\"ch\":");      CompositeSerial.print(cfg.channel);
  CompositeSerial.print("}\n");
}

static void handleLine(char *buf) {
  if (!strcmp(buf, "GET"))  { sendConfig(); return; }
  if (!strcmp(buf, "SAVE")) {
    if (cfgSave()) CompositeSerial.print("{\"ok\":\"saved\"}\n");
    else           CompositeSerial.print("{\"err\":\"flash\"}\n");
    return;
  }
  if (!strncmp(buf, "MON ", 4)) { monitor = (buf[4] == '1'); return; }

  if (!strncmp(buf, "SET ", 4)) {
    char key[8]; int idx = 0; long val = 0;
    if (sscanf(buf + 4, "%7s %d %ld", key, &idx, &val) == 3) {
      bool padOk = (idx >= 0 && idx < NUM_PADS);
      if      (!strcmp(key, "TH")    && padOk) cfg.th[idx]   = constrain(val, 0, 4095);
      else if (!strcmp(key, "NOTE")  && padOk) cfg.note[idx] = constrain(val, 0, 127);
      else if (!strcmp(key, "GAMMA"))          cfg.gammaX100 = constrain(val, 10, 300);
      else if (!strcmp(key, "MASK"))           cfg.maskMs    = constrain(val, 1, 1000);
      else if (!strcmp(key, "SCAN"))           cfg.scanUs    = constrain(val, 200, 20000);
      else if (!strcmp(key, "LEN"))            cfg.noteLenMs = constrain(val, 5, 2000);
      else if (!strcmp(key, "PMAX"))           cfg.peakMax   = constrain(val, 200, 4095);
      else if (!strcmp(key, "CH"))             cfg.channel   = constrain(val, 0, 15);
    }
  }
}

static void pollSerial() {
  while (CompositeSerial.available()) {
    char c = (char)CompositeSerial.read();
    if (c == '\n' || c == '\r') {
      if (rxLen) { rxBuf[rxLen] = 0; handleLine(rxBuf); rxLen = 0; }
    } else if (rxLen < sizeof(rxBuf) - 1) {
      rxBuf[rxLen++] = c;
    } else {
      rxLen = 0;
    }
  }
}

// ---------------- Setup / loop ----------------
void setup() {
  for (int i = 0; i < NUM_PADS; i++) {
    pinMode(PAD_PIN[i], INPUT_ANALOG);
    pads[i].state  = IDLE;
    pads[i].noteOn = false;
  }
  pinMode(PC13, OUTPUT);
  digitalWrite(PC13, HIGH);

  cfgDefaults();

  USBComposite.setProductString("BluePill MIDI Trigger");
  midi.registerComponent();
  CompositeSerial.registerComponent();
  USBComposite.begin();
  delay(2000);

  // EEPROM após USB enumerar — flash erase não pode ocorrer durante enumeração
  // (handler USB está em flash e fica inacessível durante PER no STM32F1)
  // Páginas nos últimos 2KB dos 128KB reais: fora do binário (~84KB)
  EEPROM.PageBase0 = 0x0801F800;
  EEPROM.PageBase1 = 0x0801FC00;
  EEPROM.PageSize  = 0x400;
  EEPROM.init();
  if (!cfgLoad()) cfgDefaults();

  // 3 piscadas confirmam que setup() terminou
  for (int i = 0; i < 3; i++) {
    digitalWrite(PC13, LOW);  delay(120);
    digitalWrite(PC13, HIGH); delay(80);
  }
}

void loop() {
  const uint32 nowUs = micros();
  const uint32 nowMs = millis();

  pollSerial();

  for (int i = 0; i < NUM_PADS; i++) {
    Pad &p = pads[i];
    const uint16 v = analogRead(PAD_PIN[i]);

    switch (p.state) {

      case IDLE:
        if (v >= cfg.th[i]) {
          p.state        = SCANNING;
          p.peak         = v;
          p.tScanStartUs = nowUs;
        }
        break;

      case SCANNING:
        if (v > p.peak) p.peak = v;
        if ((uint32)(nowUs - p.tScanStartUs) >= cfg.scanUs) {
          const uint8 vel = peakToVelocity(p.peak, cfg.th[i]);
          midi.sendNoteOn(cfg.channel, cfg.note[i], vel);
          p.noteOn       = true;
          p.tNoteOffMs   = nowMs + cfg.noteLenMs;
          p.state        = MASKED;
          p.tMaskStartMs = nowMs;

          digitalWrite(PC13, LOW);
          ledOffAtMs = nowMs + 30;

          if (monitor) {
            CompositeSerial.print("{\"e\":\"hit\",\"p\":");
            CompositeSerial.print(i);
            CompositeSerial.print(",\"peak\":");
            CompositeSerial.print(p.peak);
            CompositeSerial.print(",\"vel\":");
            CompositeSerial.print(vel);
            CompositeSerial.print("}\n");
          }
        }
        break;

      case MASKED:
        if (p.noteOn && (int32)(nowMs - p.tNoteOffMs) >= 0) {
          midi.sendNoteOff(cfg.channel, cfg.note[i], 0);
          p.noteOn = false;
        }
        if ((uint32)(nowMs - p.tMaskStartMs) >= cfg.maskMs && v < cfg.th[i] && !p.noteOn) {
          p.state = IDLE;
        }
        break;
    }
  }

  if (ledOffAtMs && (int32)(nowMs - ledOffAtMs) >= 0) {
    digitalWrite(PC13, HIGH);
    ledOffAtMs = 0;
  }

  // Heartbeat: 50ms a cada 1s quando sem flash de hit ativo
  if (ledOffAtMs == 0) {
    if (nowMs - lastHbMs >= 1000) {
      digitalWrite(PC13, LOW);
      lastHbMs = nowMs;
    } else if (nowMs - lastHbMs >= 50) {
      digitalWrite(PC13, HIGH);
    }
  }
}

# BluePill MIDI Trigger Suite

STM32F103C8T6 como módulo trigger-to-MIDI com configuração em tempo real.

```
┌─────────────┐  USB composto   ┌──────────────────────────────┐
│  Blue Pill  │ ──── MIDI ────► │ Sampler (Hydrogen, EZdrummer)│
│  8x piezo   │ ──── CDC  ────► │ Configurator (Tauri)         │
└─────────────┘  simultâneos    └──────────────────────────────┘
```

A placa enumera como **dois dispositivos ao mesmo tempo**: porta MIDI
class-compliant (consumida pelo sampler) e porta serial `/dev/ttyACM0`
(consumida pelo configurador). Ajustes valem na hora; `Salvar na placa`
persiste na flash e sobrevive ao desligar.

## 1. Firmware (`firmware/`)

```bash
cd firmware
pio run -t upload     # ST-Link conectado (SWDIO, SWCLK, 3.3V, GND)
```

Verificação após plugar o USB da placa:

```bash
aconnect -l           # deve listar "BluePill MIDI Trigger" (MIDI)
ls /dev/ttyACM*       # deve existir a porta serial CDC
```

Circuito de condicionamento por canal (piezo gera dezenas de volts — não
ligue direto no ADC):

```
piezo (+) ──┬──[ R1 1MΩ ]── GND
            │
            └──[ R2 10kΩ ]──┬──► PAx (ADC)
                            ├──|>|── 3.3V   D1 1N4148
                   GND ──|>|┘                D2 1N4148
piezo (–) ── GND
```

## 2. Configurator (`configurator/`)

App Tauri 2 (Rust + React/Vite/TS).

Dependências de sistema no Pop!_OS:

```bash
sudo apt install libwebkit2gtk-4.1-dev build-essential curl wget file \
  libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev libudev-dev
# Rust, se ainda não tiver:
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
# Acesso à serial sem sudo:
sudo usermod -aG dialout $USER   # relogar depois
```

Rodar:

```bash
cd configurator
npm install
npm run tauri dev      # primeira compilação do Rust demora alguns minutos
```

Empacotar depois (opcional): ative `bundle.active` no `tauri.conf.json`,
gere ícones com `npx tauri icon <png>` e rode `npm run tauri build`.

## 3. Protocolo serial (115200, linhas `\n`)

| Comando               | Efeito                                              |
|-----------------------|-----------------------------------------------------|
| `GET`                 | devolve JSON com a config atual                     |
| `SET TH <pad> <v>`    | threshold do pad (0–4095)                           |
| `SET NOTE <pad> <v>`  | nota MIDI do pad (0–127)                            |
| `SET GAMMA 0 <v>`     | curva de velocity x100 (60 = 0.60)                  |
| `SET MASK 0 <v>`      | anti-retrigger em ms                                |
| `SET SCAN 0 <v>`      | janela de captura de pico em µs                     |
| `SET LEN 0 <v>`       | atraso do Note Off em ms                            |
| `SET PMAX 0 <v>`      | leitura ADC que mapeia para velocity 127            |
| `SET CH 0 <v>`        | canal MIDI (0–15)                                   |
| `SAVE`                | grava na flash → responde `{"ok":"saved"}`          |
| `MON 1` / `MON 0`     | liga/desliga telemetria de hits                     |

Telemetria (uma linha por hit): `{"e":"hit","p":2,"peak":1834,"vel":98}`

O protocolo é texto puro — dá pra testar sem o app:

```bash
picocom -b 115200 /dev/ttyACM0     # digite GET e Enter
```

## 4. Fluxo de calibração

1. Conecte no configurador → os meters mostram cada batida com as marcas
   de **limiar** (tracejado âmbar) e **pico máx** (tracejado vermelho).
2. Bata de leve no pad: o pico deve cruzar o limiar. Bata forte: deve
   chegar perto do pico máx (senão, abaixe `PICO MÁX`).
3. Disparo fantasma ou crosstalk entre pads → suba o limiar do canal.
4. Nota dupla num hit só → suba `MÁSCARA`.
5. Ajuste `CURVA` ao gosto (< 1.0 dá mais resposta em toques leves).
6. `Salvar na placa`.

## Troubleshooting

- **USB não enumera**: clones da Blue Pill costumam vir com R10 (pull-up
  de PA12) de 10kΩ/4.7kΩ em vez de 1.5kΩ — solde 1.5k–1.8kΩ entre PA12 e 3.3V.
- **Permissão negada em /dev/ttyACM0**: grupo `dialout` (comando acima).
- **Config não persiste**: alguns clones têm flash de 64KB real; os
  endereços da EEPROM emulada já estão configurados para isso no firmware
  (`0x0800F800` / `0x0800FC00`).

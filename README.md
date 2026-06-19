# BluePill MIDI Trigger Suite

Sistema completo de trigger piezo → MIDI usando um STM32F103C8T6 (BluePill/CluePill) com configuração em tempo real via USB.

```
┌─────────────────┐   USB composto (1 cabo)   ┌──────────────────────────────────────┐
│  BluePill       │ ───── MIDI ─────────────► │ Sampler (Hydrogen, EZdrummer...)     │
│  8 piezos       │ ───── CDC Serial ────────► │ Configurador (app Tauri ou Android)  │
│  PA0 … PA7      │   simultâneos             └──────────────────────────────────────┘
└─────────────────┘
```

A placa enumera como **dois dispositivos ao mesmo tempo** no mesmo cabo USB:
- **MIDI class-compliant** → consumido direto pelo sampler, sem drivers
- **Porta serial CDC** (`/dev/ttyACM0` no Linux, `COMx` no Windows, USB Host no Android) → consumida pelo configurador

Ajustes feitos no app valem imediatamente (sem reiniciar a placa). "Salvar na placa" persiste na flash e sobrevive ao desligar.

---

## Por que BluePill em vez de Arduino?

Este projeto nasceu de duas limitações concretas das soluções baseadas em Arduino que incomodam quem leva a bateria eletrônica a sério:

### Custo

| Placa | Preço típico (AliExpress) | USB MIDI nativo |
|---|---|---|
| Arduino Uno R3 | ~U$ 3–5 | Não (shield extra ~U$ 8) |
| Arduino Mega | ~U$ 8–12 | Não (shield extra) |
| **STM32F103C8T6 (BluePill)** | **~U$ 1,50–2** | **Sim (classe nativa)** |

A BluePill sai praticamente pela metade do preço de um Uno e já tem USB de hardware — sem shields, sem conversores FTDI, sem drivers proprietários.

### Latência

O gargalo de latência em trigger MIDI não está na transmissão USB, está no **ciclo ADC + detecção de pico**:

| Plataforma | Clock CPU | ADC | Janela de detecção típica | Latência hit → Note On |
|---|---|---|---|---|
| Arduino Uno (ATmega328P) | 16 MHz | ~100 µs / amostra | 3–5 ms | ~5–8 ms |
| **STM32F103C8T6 (BluePill)** | **72 MHz** | **~1 µs / amostra** | **1–3 ms** | **< 2 ms** |

Com 72 MHz e ADC de 12 bits correndo a 1 MHz, a BluePill captura o pico do piezo em menos de 3 ms e coloca a nota MIDI no barramento USB imediatamente após — sem polling lento, sem timer de 1 ms, sem overhead de biblioteca genérica.

O resultado prático é um feel visivelmente mais responsivo: flams ficam tighter, rudimentos em alta velocidade saem limpos, e a diferença de velocity entre toque leve e forte é consistente mesmo em redobradas rápidas.

---

## Índice

1. [Hardware](#1-hardware)
2. [Firmware](#2-firmware)
3. [Instalação do Configurador (Desktop)](#3-instalação-do-configurador-desktop)
4. [Aplicativo Android (Sampler)](#4-aplicativo-android-sampler)
5. [Manual do Configurador Desktop](#5-manual-do-configurador-desktop)
6. [Protocolo Serial](#6-protocolo-serial)
7. [Calibração](#7-calibração)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Hardware

### Pinagem dos sensores

Os 8 piezos ligam nos pinos analógicos do **lado direito** da BluePill:

```
        ┌──── USB ────┐
 B12 ── │●           ●│ ── GND
 B13 ── │●           ●│ ── GND
 B14 ── │●           ●│ ── 3.3V
 B15 ── │●           ●│ ── NRST
  A8 ── │●           ●│ ── VDDA
  A9 ── │●           ●│ ── PA0  ←── Pad 1 (Kick)
 A10 ── │●           ●│ ── PA1  ←── Pad 2 (Snare)
 A11 ── │●           ●│ ── PA2  ←── Pad 3 (HH Fechado)
 A12 ── │●           ●│ ── PA3  ←── Pad 4 (HH Aberto)
 A15 ── │●           ●│ ── PA4  ←── Pad 5 (Tom Alto)
  B3 ── │●           ●│ ── PA5  ←── Pad 6 (Tom Médio)
  B4 ── │●           ●│ ── PA6  ←── Pad 7 (Crash)
  B5 ── │●           ●│ ── PA7  ←── Pad 8 (Ride)
  B6 ── │●           ●│ ── PB0
  B7 ── │●           ●│ ── PB1
  B8 ── │●           ●│ ── PC13 (LED onboard)
  B9 ── │●           ●│ ── PC14
  5V ── │●           ●│ ── PC15
 GND ── │●           ●│ ── PD0
 3.3 ── │●           ●│ ── PD1
        └─────────────┘
```

### Notas MIDI padrão por pad

| Pad | Pino | Nota | Nome GM |
|-----|------|------|---------|
| 1   | PA0  | 36   | Kick |
| 2   | PA1  | 38   | Snare |
| 3   | PA2  | 42   | HH Fechado |
| 4   | PA3  | 46   | HH Aberto |
| 5   | PA4  | 48   | Tom Alto |
| 6   | PA5  | 45   | Tom Médio |
| 7   | PA6  | 49   | Crash |
| 8   | PA7  | 51   | Ride |

Todas as notas são reconfiguráveis individualmente pelo configurador.

### Circuito de condicionamento (recomendado)

O piezo gera picos de tensão acima de 3.3 V que podem danificar o ADC. Use este circuito por canal:

```
                 ┌─── R1 1MΩ ───┐
                 │               │
Piezo (+) ───────┤               GND
                 │
                 └─── R2 10kΩ ──┬──── PA0…PA7 (ADC)
                                 │
                           D1 ──►│── 3.3V    (clamp superior)
                           D2 ──►│── GND     (clamp inferior)
                             1N4148 × 2

Piezo (−) ─── GND
```

**Versão mínima (teste rápido):** piezo (+) direto em PA0…PA7, piezo (−) em GND. Sem proteção — evite batidas muito fortes.

### LED onboard

O LED no pino **PC13** é ativo em LOW:
- **Pisca a cada 1 s por 50 ms** → firmware rodando normalmente (heartbeat)
- **Pisca no hit** → pad detectado (pulso de 30 ms)
- **3 piscadas rápidas na inicialização** → setup() concluído com sucesso

---

## 2. Firmware

### Requisitos

- [PlatformIO](https://platformio.org/) (extensão VSCode ou CLI)
- ST-Link V2 (gravação SWD)
- Conexões SWD: `SWDIO → PA13`, `SWCLK → PA14`, `GND`, `3.3V`

### Gravar

```bash
cd firmware
pio run -e bluepill -t upload
```

O ambiente `bluepill` usa:
- **Core:** Maple (libmaple) com linker customizado em `0x08000000`
- **Biblioteca USB:** USBComposite for STM32F1 (commit 43d2ad6, fixado)
- **Flag:** `-D GENERIC_BOOTLOADER` (inicialização correta do D+ no CS32F103)

### Verificar após plugar o cabo USB da placa

```bash
# Linux
lsusb | grep -i "bluepill\|1eaf"     # deve mostrar "Leaflabs BluePill MIDI Trigger"
ls /dev/ttyACM*                        # deve existir /dev/ttyACM0
cat /proc/asound/cards | grep Trigger  # deve listar o dispositivo MIDI
```

### Outros ambientes disponíveis

| Ambiente | Descrição |
|---|---|
| `bluepill` | **Principal** — USB composto MIDI+CDC, Maple core |
| `bluepill_uart` | Fallback — MIDI USB + configuração via UART1 (PA9/PA10) |
| `blink_test` | Pisca o LED — verifica se upload funciona |
| `cdc_test` | CDC serial simples — testa USB básico |

---

## 3. Instalação do Configurador (Desktop)

### Via instalador (recomendado)

Baixe o instalador para o seu sistema em **[Releases](../../releases)**:

| Sistema | Arquivo | Notas |
|---|---|---|
| Linux (Ubuntu/Debian) | `.deb` | Execute com `sudo dpkg -i ...` — instala udev rule e adiciona usuário ao grupo `dialout` |
| Linux (outros) | `.AppImage` | Torne executável (`chmod +x`) e abra — configure permissões manualmente |
| Windows 10/11 | `.exe` ou `.msi` | Execute como Administrador — cria atalho na Área de Trabalho |

**Permissão de porta serial no Linux (após instalar o .deb ou .AppImage):**

```bash
sudo usermod -aG dialout $USER   # necessário apenas se o .deb não fizer automaticamente
# Faça logout e login para aplicar
```

### Via código-fonte (desenvolvimento)

**Dependências:**

```bash
# Ubuntu / Pop!_OS
sudo apt install libwebkit2gtk-4.1-dev build-essential curl \
  libssl-dev libayatana-appindicator3-dev librsvg2-dev libudev-dev

# Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Node.js 18+ (via nvm ou apt)
```

**Rodar:**

```bash
cd configurator
npm install
npm run tauri dev     # primeira compilação Rust: ~3-5 min
```

**Build de produção:**

```bash
npm run tauri build   # gera .deb, .AppImage (Linux) ou .exe/.msi (Windows)
```

---

## 4. Aplicativo Android (Sampler)

O app Android substitui o computador no setup completo: o celular funciona ao mesmo tempo como **sampler** (toca os samples quando recebe MIDI) e como **configurador** da placa (ajusta thresholds, notas e parâmetros globais), tudo pelo mesmo cabo OTG.

```
┌──────────┐  cabo USB-C OTG  ┌───────────────┐
│ BluePill │ ════════════════ │ Android (OTG) │
│  8 pads  │  MIDI class      │  ● SoundPool  │
│          │  CDC Serial      │  ● Configura  │
└──────────┘                  └───────────────┘
```

### Requisitos

- Android 8.0+ (API 26)
- Cabo USB OTG (USB-A do host → USB-C ou micro-USB do celular)
- Android Studio Meerkat ou superior para compilar

### Compilar e instalar

```bash
cd android-sampler
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Ou abra a pasta `android-sampler/` no Android Studio e clique **Run**.

### Funcionamento

1. **Conecte a BluePill ao celular** com um cabo OTG. O app aparece automaticamente (intent `USB_DEVICE_ATTACHED` está registrado no `AndroidManifest.xml`).
2. O app abre a interface MIDI e a porta serial CDC simultaneamente no mesmo dispositivo USB.
3. **Toque os pads** — cada hit chega por MIDI, o app mapeia a nota ao pad correspondente e dispara o sample via `SoundPool` com volume proporcional à velocity.
4. Para **trocar um sample**, toque e segure o pad desejado na interface, escolha o arquivo WAV/OGG no seletor de arquivos. A associação é salva localmente e restaurada na próxima vez.
5. Para **configurar a placa**, role a tela para a seção de configuração (thresholds, gamma, máscara, etc.) — os comandos CDC são enviados em tempo real pelo mesmo cabo.

### Arquitetura do app

```
android-sampler/
└── app/src/main/java/com/example/drumsamplebluepill/
    ├── MainActivity.kt      # activity única, landscape obrigatório
    ├── DrumViewModel.kt     # estado da UI + orquestração dos engines
    ├── MidiEngine.kt        # recebe Note On/Off via USB MIDI class-compliant
    ├── SerialEngine.kt      # CDC serial: GET/SET/SAVE + telemetria de hits
    ├── SampleEngine.kt      # SoundPool: carrega e toca WAV/OGG por pad
    └── ui/
        ├── DrumPadScreen.kt # tela principal: 8 pads + painel de configuração
        └── kit/
            ├── KitScreen.kt    # visualização gráfica do kit
            ├── KitCanvas.kt    # canvas com posições dos pads
            ├── KitModels.kt    # modelos de dados do kit
            └── KitViewModel.kt # viewmodel da tela de kit
```

**Engines independentes, mesmo dispositivo USB:**
- `MidiEngine` abre a interface MIDI do dispositivo USB (`UsbMidiDevice`) e processa eventos de Note On em tempo real.
- `SerialEngine` abre a interface CDC (`UsbInterface` com classe `USB_CLASS_CDC_DATA`) no mesmo dispositivo e envia/recebe JSON pelo bulk endpoint a 115200 baud.
- `SampleEngine` usa `SoundPool` com atributos de áudio de jogo (`USAGE_GAME`) para minimizar latência de reprodução.

### Permissões USB

O `device_filter.xml` autoriza automaticamente a BluePill (VID/PID do composite device) sem diálogo extra. Se precisar adicionar outra placa, edite `app/src/main/res/xml/device_filter.xml`.

---

## 5. Manual do Configurador Desktop

### Visão geral da interface

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ● BLUEPILL · MIDI TRIGGER    [/dev/ttyACM0 ▼] [⟳] [Conectar] [Salvar] │  ← Barra superior
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬───── │
│ ● PAD 1  │ ● PAD 2  │ ● PAD 3  │ ● PAD 4  │ ● PAD 5  │ ● PAD 6  │ ... │  ← Grade de pads
│   v64    │   —      │   —      │   —      │   —      │   —      │     │
│  ┌────┐  │  ┌────┐  │  ...     │          │          │          │     │
│  │████│  │  │    │  │          │          │          │          │     │
│  │████│  │  │    │  │  VU      │          │          │          │     │
│  │    │  │  │    │  │ meter    │          │          │          │     │
│  └────┘  │  └────┘  │          │          │          │          │     │
│ pico 823 │ pico —   │          │          │          │          │     │
│ LIMIAR   │          │          │          │          │          │     │
│ NOTA     │          │          │          │          │          │     │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴─────┤
│ MASTER: CURVA  MÁSCARA  JANELA  NOTA OFF  PICO MÁX  CANAL MIDI        │  ← Rodapé global
└─────────────────────────────────────────────────────────────────────────┘
```

---

### Barra superior

#### Indicador de conexão (ponto colorido)
Círculo à esquerda do título.
- **Cinza** → desconectado
- **Verde** → conectado e comunicando com a placa

#### Seletor de porta serial
Menu dropdown com todas as portas seriais disponíveis no sistema.
- No Linux: `/dev/ttyACM0`, `/dev/ttyUSB0`, etc.
- No Windows: `COM3`, `COM4`, etc.
- Desabilitado enquanto conectado

#### Botão ⟳ (Atualizar portas)
Recarrega a lista de portas disponíveis sem desconectar.
Útil quando a placa é plugada depois que o app já estava aberto.

#### Botão Conectar / Desconectar
- **Conectar:** abre a porta serial, envia `MON 1` (ativa telemetria de hits) e `GET` (lê configuração atual). Um segundo `MON 1` é enviado automaticamente após 2 s como garantia contra race condition na enumeração USB.
- **Desconectar:** envia `MON 0`, fecha a porta e limpa a tela.

> A porta MIDI **continua ativa** independentemente de o configurador estar conectado ou não.

#### Botão Salvar na placa
Envia o comando `SAVE` que grava a configuração atual na **flash não-volátil** da placa (EEPROM emulada).
- Habilitado apenas quando conectado e com configuração carregada
- Exibe toast de confirmação: *"Configuração salva na flash da placa"*
- Em caso de falha de flash: *"Erro: falha ao gravar na flash — tente novamente"*

> Sem clicar neste botão, os ajustes feitos no app são perdidos ao desligar a placa.

---

### Grade de Pads (8 cards)

Cada card representa um canal de entrada (PA0 a PA7). Todos os controles são independentes por pad.

#### LED de Hit
Acende por 220 ms cada vez que um hit é detectado naquele pad.

#### VU Meter
Mostra a intensidade do último hit como percentual do valor ADC máximo. Decai em 140 ms.

Duas marcações fixas:
- **Tracejado âmbar** → posição do LIMIAR atual
- **Tracejado vermelho** → posição do PICO MÁX atual

---

#### Controle: LIMIAR

| Campo | Valor |
|---|---|
| Faixa | 0 – 4095 |
| Protocolo | `SET TH <pad> <valor>` |

Define o limiar mínimo de amplitude para que um hit seja reconhecido.

---

#### Controle: NOTA

| Campo | Valor |
|---|---|
| Faixa | 0 – 127 |
| Protocolo | `SET NOTE <pad> <valor>` |

Define qual nota MIDI é enviada quando este pad é atingido.

---

### Rodapé Master (controles globais)

#### CURVA (Gamma de Velocity)

| Faixa | Padrão | Protocolo |
|---|---|---|
| 0.10 – 3.00 | 0.60 | `SET GAMMA 0 <valor x100>` |

- `gamma < 1.0` → toques leves já produzem velocities médias
- `gamma = 1.0` → linear
- `gamma > 1.0` → requer batida forte para velocity alta

#### MÁSCARA (Anti-Retrigger)

| Faixa | Padrão | Protocolo |
|---|---|---|
| 1 – 1000 ms | 30 ms | `SET MASK 0 <ms>` |

Tempo mínimo entre dois hits no mesmo pad. Evita double-trigger dos bounces mecânicos do piezo.

#### JANELA (Scan Window)

| Faixa | Padrão | Protocolo |
|---|---|---|
| 200 – 20000 µs | 2500 µs | `SET SCAN 0 <µs>` |

Duração da janela de captura de pico após o threshold ser cruzado.

#### NOTA OFF

| Faixa | Padrão | Protocolo |
|---|---|---|
| 5 – 2000 ms | 40 ms | `SET LEN 0 <ms>` |

Tempo entre Note On e Note Off. Para percussão, samplers ignoram o Note Off — o padrão de 40 ms funciona para tudo.

#### PICO MÁX

| Faixa | Padrão | Protocolo |
|---|---|---|
| 200 – 4095 | 3500 | `SET PMAX 0 <valor>` |

Define qual amplitude ADC corresponde a velocity 127.

#### CANAL MIDI

| Faixa | Padrão | Protocolo |
|---|---|---|
| 1 – 16 | Canal 1 | `SET CH 0 <0-15>` |

Para percussão GM: use **Canal 10** (internamente 9).

---

## 6. Protocolo Serial

Comunicação via porta CDC a 115200 baud, linhas terminadas em `\n`. Pode ser usado diretamente por qualquer terminal serial.

```bash
# Linux
picocom -b 115200 /dev/ttyACM0

# Windows (PowerShell)
# Use PuTTY ou Tera Term em COMx, 115200 8N1
```

### Comandos enviados ao firmware

| Comando | Efeito | Resposta |
|---|---|---|
| `GET` | Lê configuração completa | JSON com todos os parâmetros |
| `SET TH <pad> <v>` | Threshold do pad 0–7 (0–4095) | silencioso |
| `SET NOTE <pad> <v>` | Nota MIDI do pad 0–7 (0–127) | silencioso |
| `SET GAMMA 0 <v>` | Curva de velocity ×100 (10–300) | silencioso |
| `SET MASK 0 <v>` | Anti-retrigger em ms (1–1000) | silencioso |
| `SET SCAN 0 <v>` | Janela de pico em µs (200–20000) | silencioso |
| `SET LEN 0 <v>` | Atraso do Note Off em ms (5–2000) | silencioso |
| `SET PMAX 0 <v>` | Pico máximo ADC (200–4095) | silencioso |
| `SET CH 0 <v>` | Canal MIDI 0-indexado (0–15) | silencioso |
| `SAVE` | Persiste na flash | `{"ok":"saved"}` ou `{"err":"flash"}` |
| `MON 1` | Ativa telemetria de hits | silencioso |
| `MON 0` | Desativa telemetria | silencioso |

### Resposta do GET

```json
{
  "th":  [80,80,80,80,80,80,80,80],
  "note":[36,38,42,46,48,45,49,51],
  "gamma": 60,
  "mask":  30,
  "scan":  2500,
  "len":   40,
  "pmax":  3500,
  "ch":    0
}
```

### Telemetria de hit (MON 1)

Uma linha JSON por hit detectado:

```json
{"e":"hit","p":0,"peak":1823,"vel":64}
```

| Campo | Descrição |
|---|---|
| `e` | Tipo de evento (`"hit"`) |
| `p` | Índice do pad (0–7) |
| `peak` | Valor ADC do pico (0–4095) |
| `vel` | Velocity MIDI calculada (1–127) |

---

## 7. Calibração

### Passo a passo

1. **Conecte no configurador** → espere carregar os 8 pads

2. **Ajuste o LIMIAR por pad:**
   - Bata levinho no pad. O VU meter deve mostrar uma barra e o LED piscar.
   - Se disparar sozinho (sem tocar) → suba o LIMIAR
   - A marcação âmbar no VU meter mostra onde está o threshold

3. **Ajuste o PICO MÁX:**
   - Bata com a força máxima que usará em performance
   - Ajuste para que a barra chegue quase no topo nessa batida

4. **Ajuste a CURVA** ao gosto:
   - `0.60` → mais expressão em toques leves (padrão)
   - `1.00` → linear
   - `2.00` → exige força para chegar a velocities altas

5. **Verifique a MÁSCARA:**
   - Bata rápido em sequência; se ouvir doubles → suba a máscara
   - Se rufos saem lentos → abaixe a máscara

6. **Ajuste a JANELA** se as velocidades forem inconsistentes:
   - Muito variável → aumente a janela para capturar o pico real do piezo
   - Latência percebida → diminua a janela

7. **Clique Salvar na placa** para persistir

### Diagnosticar sem o app

```bash
# Ativa telemetria e monitora hits
stty -F /dev/ttyACM0 115200 raw -echo
echo "MON 1" > /dev/ttyACM0
cat /dev/ttyACM0
# Bata nos pads e observe as linhas JSON
```

---

## 8. Troubleshooting

### Placa não aparece no USB

**Sintoma:** `lsusb` não mostra "BluePill MIDI Trigger"

- Clone CS32F103 precisa de um cabo **de dados** (não de carga) — teste com outro dispositivo primeiro
- Verifique o LED de heartbeat: deve piscar a 1 Hz. Se não piscar → regrave com ST-Link
- Resistor de pull-up de D+ (PA12): alguns clones vêm com 10kΩ em vez de 1.5kΩ — solde 1.5kΩ entre PA12 e 3.3V

### Permission denied ao conectar

```bash
sudo usermod -aG dialout $USER
# Faça logout e login
```

### Configuração não persiste (perdida ao desligar)

Clicar **Salvar na placa** é obrigatório — ajustes feitos no app ficam só na RAM.

### App Android não reconhece o dispositivo

- Verifique se está usando um cabo OTG ativo (com pino ID) — cabos passivos de carga não funcionam
- O VID/PID do seu clone pode ser diferente — edite `device_filter.xml` com o VID/PID exibido em `adb shell lsusb`
- Alguns celulares exigem ativar "USB Host Mode" nas opções de desenvolvedor

### App fica em "Lendo configuração da placa…"

**Causa raiz:** o `USBCompositeSerial` do STM32 só processa dados recebidos quando o sinal DTR está alto.

| Plataforma | Comportamento do DTR |
|---|---|
| Linux | `cdc_acm` assert DTR automaticamente no `open()` |
| Windows | O app faz isso explicitamente via `dtr_on_open(true)` |
| Android | `SerialEngine` envia SET_CONTROL_LINE_STATE (DTR+RTS = 0x03) no `controlTransfer` de setup |

### Double-trigger (nota dupla num hit)

Suba a **MÁSCARA**. Comece com 50 ms e aumente até parar.

### Pad não dispara com toque leve

Baixe o **LIMIAR** do pad. Sem o resistor R1 (1MΩ) o sinal pode estar muito atenuado.

### Velocity sempre máxima (127)

Baixe o **PICO MÁX**. O valor atual está abaixo do que o piezo entrega em toques médios.

### MIDI não aparece no sampler

```bash
aconnect -l    # deve listar "BluePill MIDI Trigger" nas portas de entrada
```

---

## Estrutura do repositório

```
bluepill-midi-suite/
├── firmware/
│   ├── platformio.ini          # ambientes de build
│   ├── ld/
│   │   └── stlink_c8_128k.ld  # linker: 0x08000000, 128KB flash
│   └── src/
│       └── main.cpp            # firmware principal (USB composto MIDI+CDC)
├── configurator/
│   ├── src/
│   │   └── App.tsx             # interface React (Tauri)
│   └── src-tauri/
│       ├── tauri.conf.json
│       ├── linux/              # udev rule, desktop entry
│       └── src/
│           └── main.rs         # backend Rust (serial, Tauri commands)
├── android-sampler/
│   ├── app/src/main/java/com/example/drumsamplebluepill/
│   │   ├── MainActivity.kt     # activity única
│   │   ├── DrumViewModel.kt    # estado + orquestração
│   │   ├── MidiEngine.kt       # USB MIDI class-compliant
│   │   ├── SerialEngine.kt     # CDC serial (GET/SET/SAVE)
│   │   ├── SampleEngine.kt     # SoundPool: WAV/OGG por pad
│   │   └── ui/                 # Compose screens
│   └── app/src/main/res/xml/
│       └── device_filter.xml   # VID/PID autorizado (OTG)
└── .github/
    └── workflows/
        └── release.yml         # CI: build Linux + Windows por tag
```

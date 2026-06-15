# BluePill MIDI Trigger Suite

Sistema completo de trigger piezo → MIDI usando um STM32F103C8T6 (BluePill/CluePill) com configuração em tempo real via USB.

```
┌─────────────────┐   USB composto (1 cabo)   ┌──────────────────────────────────┐
│  BluePill       │ ───── MIDI ─────────────► │ Sampler (Hydrogen, EZdrummer...) │
│  8 piezos       │ ───── CDC Serial ────────► │ Configurador (app Tauri)         │
│  PA0 … PA7      │   simultâneos             └──────────────────────────────────┘
└─────────────────┘
```

A placa enumera como **dois dispositivos ao mesmo tempo** no mesmo cabo USB:
- **MIDI class-compliant** → consumido direto pelo sampler, sem drivers
- **Porta serial CDC** (`/dev/ttyACM0` no Linux, `COMx` no Windows) → consumida pelo configurador

Ajustes feitos no app valem imediatamente (sem reiniciar a placa). "Salvar na placa" persiste na flash e sobrevive ao desligar.

---

## Índice

1. [Hardware](#1-hardware)
2. [Firmware](#2-firmware)
3. [Instalação do Configurador](#3-instalação-do-configurador)
4. [Manual do Configurador](#4-manual-do-configurador)
5. [Protocolo Serial](#5-protocolo-serial)
6. [Calibração](#6-calibração)
7. [Troubleshooting](#7-troubleshooting)

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

O LED no pino **PC13** é ativo em LOW (acende com sinal LOW):
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

## 3. Instalação do Configurador

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

**Gerar releases automaticamente (CI):**

```bash
git tag v1.2.0 && git push origin v1.2.0
# O GitHub Actions builda para Linux e Windows e publica em Releases
```

---

## 4. Manual do Configurador

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
- Desabilitado enquanto conectado

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

#### LED de Hit (ponto colorido no cabeçalho do pad)
Acende por 220 ms cada vez que um hit é detectado naquele pad. Útil para confirmar visualmente que o sinal chega sem olhar o terminal.

#### Exibição de Velocidade (`v<número>` ou `—`)
Mostra a última velocidade MIDI enviada para aquele pad (1–127).
- `—` → nenhum hit desde a conexão

#### VU Meter (barra vertical)
Mostra a intensidade do último hit como percentual do valor ADC máximo (0–4095 → 0–100%).
Decai automaticamente em 140 ms após cada hit.

Duas marcações fixas:
- **Tracejado âmbar** → posição do LIMIAR atual (sinal precisa cruzar essa linha para disparar)
- **Tracejado vermelho** → posição do PICO MÁX atual (batida máxima calibrada = velocity 127)

#### Exibição de Pico (`pico <número>` ou `pico —`)
Valor ADC bruto (0–4095) do pico do último hit, antes da conversão em velocity.

---

#### Controle: LIMIAR

| Campo | Valor |
|---|---|
| Faixa | 0 – 4095 |
| Tipo | Slider horizontal |
| Protocolo | `SET TH <pad> <valor>` |

**O que faz:** define o limiar mínimo de amplitude para que um hit seja reconhecido.

- **Muito baixo** → pad dispara com vibração, toque leve ou crosstalk de pads vizinhos
- **Muito alto** → pad só dispara com batidas muito fortes; toques leves são ignorados

**Calibração:** bata levinho no pad e suba o limiar até os falsos disparos desaparecerem. A marcação âmbar no VU meter mostra onde está.

---

#### Controle: NOTA

| Campo | Valor |
|---|---|
| Faixa | 0 – 127 |
| Tipo | Campo numérico (input) |
| Protocolo | `SET NOTE <pad> <valor>` |

**O que faz:** define qual nota MIDI é enviada quando este pad é atingido.

O label mostra o nome da nota (ex.: `C3 · Kick`) usando nomenclatura General MIDI para notas de percussão (canal 10). Você pode digitar qualquer valor de 0 a 127 diretamente.

> Para percussão MIDI: o canal da placa deve estar em **9** (CANAL MIDI = 10 no app, exibido como 1-indexado) para que o sampler trate como bateria.

---

### Rodapé Master (controles globais)

Estes controles afetam **todos os pads** ao mesmo tempo.

---

#### CURVA (Gamma de Velocity)

| Campo | Valor |
|---|---|
| Faixa | 0.10 – 3.00 (exibido) / 10 – 300 (interno x100) |
| Tipo | Slider |
| Padrão | 0.60 |
| Protocolo | `SET GAMMA 0 <valor x100>` |

**O que faz:** define a forma da curva de mapeamento de amplitude → velocity MIDI.

A fórmula é: `velocity = (amplitude_normalizada ^ gamma) × 126 + 1`

- **gamma < 1.0** (ex.: 0.60) → curva logarítmica: toques leves já produzem velocities médias, diferença entre leve e forte é mais suave. Boa para mãos sensíveis.
- **gamma = 1.0** → curva linear: velocity proporcional à amplitude.
- **gamma > 1.0** (ex.: 2.00) → curva exponencial: toques leves geram velocity muito baixa, requer batida forte para chegar a velocities altas. Boa para quem bate forte.

---

#### MÁSCARA (Anti-Retrigger)

| Campo | Valor |
|---|---|
| Faixa | 1 – 1000 ms |
| Tipo | Slider |
| Padrão | 30 ms |
| Protocolo | `SET MASK 0 <ms>` |

**O que faz:** tempo mínimo entre dois hits no **mesmo pad**. Após um hit, o pad fica "mascarado" (bloqueado) por este tempo.

- **Muito baixo** → um hit forte gera double-trigger (a placa detecta os bounces mecânicos do piezo como hits separados)
- **Muito alto** → impossível fazer flams ou rufos rápidos no mesmo pad

**Calibração:** se você está ouvindo notas duplas num hit único, suba a máscara. Se não consegue fazer rufos rápidos, baixe.

---

#### JANELA (Scan Window)

| Campo | Valor |
|---|---|
| Faixa | 200 – 20000 µs |
| Passo | 100 µs |
| Padrão | 2500 µs (2.5 ms) |
| Protocolo | `SET SCAN 0 <µs>` |

**O que faz:** duração da janela de captura de pico após o threshold ser cruzado. O firmware monitora o sinal ADC por este período e envia a nota com a amplitude máxima capturada.

- **Muito curto** → velocities inconsistentes: a nota dispara antes do pico real do piezo
- **Muito longo** → latência percebida aumenta; pode capturar o decaimento como pico

O valor ideal varia com o tipo e tamanho do piezo. Piezos menores têm pulso mais rápido (200–1000 µs); piezos maiores ou pads com borracha amortecedora precisam de janelas maiores (3000–8000 µs).

---

#### NOTA OFF (Note Length)

| Campo | Valor |
|---|---|
| Faixa | 5 – 2000 ms |
| Padrão | 40 ms |
| Protocolo | `SET LEN 0 <ms>` |

**O que faz:** tempo entre o Note On e o Note Off que a placa envia para cada hit.

Para percussão, samplers ignoram o Note Off (usam one-shot), então o valor padrão de 40 ms funciona para tudo. Só ajuste se o seu sampler precisar de Note Off para terminar uma nota sustentada (ex.: pad de hold de hi-hat).

> **Importante:** este valor deve ser **maior** que a MÁSCARA. Se LEN < MÁSCARA, o Note Off nunca é enviado e o sampler sustenta a nota indefinidamente.

---

#### PICO MÁX (Peak Max)

| Campo | Valor |
|---|---|
| Faixa | 200 – 4095 |
| Passo | 5 |
| Padrão | 3500 |
| Protocolo | `SET PMAX 0 <valor>` |

**O que faz:** define qual amplitude ADC corresponde a velocity 127 (golpe máximo).

- **Muito alto** → mesmo batendo forte, a velocity nunca chega a 127 (dinâmica comprimida para cima)
- **Muito baixo** → qualquer batida média já chega a 127 (sem headroom de dinâmica)

**Calibração:** bata o mais forte que usará normalmente e observe o pico no VU meter. Ajuste PICO MÁX para que a marcação vermelha fique no topo da barra nessa batida forte.

---

#### CANAL MIDI

| Campo | Valor |
|---|---|
| Faixa | 1 – 16 (exibido) / 0 – 15 (interno) |
| Tipo | Slider |
| Padrão | Canal 1 |
| Protocolo | `SET CH 0 <0-15>` |

**O que faz:** define o canal MIDI usado para **todos** os Note On/Off enviados pela placa.

Para percussão GM: use **Canal 10** (o app mostrará "10", internamente 9). Todos os samplers de bateria esperam percussão no canal 10.

---

### Mensagens de toast (notificações)

Aparecem na parte inferior da tela por alguns segundos:

| Mensagem | Causa |
|---|---|
| *Configuração salva na flash da placa* | SAVE executado com sucesso |
| *Erro: falha ao gravar na flash — tente novamente* | Flash desgastada ou endereço inválido |
| *Falha ao conectar: Permission denied* | Usuário não está no grupo `dialout` |
| *Falha ao conectar: \<porta\>* | Porta não existe ou ocupada por outro processo |
| *Erro ao salvar: \<detalhe\>* | Falha de comunicação ao tentar salvar |
| *Placa não respondeu — verifique o cabo e tente reconectar* | Porta abriu mas firmware não respondeu ao GET em 8 s |
| *Erro na porta serial: \<detalhe\>* | Driver serial reportou erro ao ler dados (ver log de diagnóstico) |

---

## 5. Protocolo Serial

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

## 6. Calibração

### Passo a passo

1. **Conecte no configurador** → espere carregar os 8 pads

2. **Ajuste o LIMIAR por pad:**
   - Bata levinho no pad. O VU meter deve mostrar uma barra e o LED piscar.
   - Se disparar sozinho (sem tocar) → suba o LIMIAR
   - A marcação âmbar mostra visualmente onde está o threshold

3. **Ajuste o PICO MÁX:**
   - Bata com a força máxima que usará em performance
   - A marcação vermelha mostra onde está o PICO MÁX
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

## 7. Troubleshooting

### Placa não aparece no USB

**Sintoma:** `lsusb` não mostra "BluePill MIDI Trigger"

- Clone CS32F103 precisa de um cabo **de dados** (não de carga) — teste com outro dispositivo primeiro
- Verifique o LED de heartbeat: deve piscar a 1 Hz. Se não piscar, o firmware não está rodando → regrave com ST-Link
- Resistor de pull-up de D+ (PA12): alguns clones vêm com 10kΩ em vez de 1.5kΩ — solde 1.5kΩ entre PA12 e 3.3V

### Permission denied ao conectar

```bash
sudo usermod -aG dialout $USER
# Faça logout e login
```

Se instalou o `.deb`, o postinst já faz isso automaticamente.

### Configuração não persiste (perdida ao desligar)

- Clicar **Salvar na placa** é obrigatório — ajustes feitos no app ficam só na RAM
- Se o toast de erro aparecer ao salvar, a EEPROM emulada pode estar com problema — tente uma vez mais

### App fica em "Lendo configuração da placa…" (especialmente no Windows)

**Sintoma:** a porta abre sem erro, mas a configuração nunca aparece.

O painel agora exibe dois aids de diagnóstico:

- **"Aguardando resposta da placa — enviando GET…"** → nenhum dado recebido ainda. Possíveis causas:
  - O firmware ainda não terminou a enumeração USB (aguarde e tente **Desconectar → Conectar** novamente)
  - Cabo sem fio de dados (tente outro)
  - No Windows: driver USB CDC não instalado (verifique Gerenciador de Dispositivos)

- **"DADOS RECEBIDOS (não reconhecidos como JSON válido):"** seguido do texto bruto → a porta está comunicando mas enviando dados fora do protocolo. Possíveis causas:
  - Porta errada selecionada (outro dispositivo CDC na mesma COM)
  - Firmware antigo ou corrompido — regrave com ST-Link
  - No Windows: `\r\n` ou BOM inesperado — o app faz trim automaticamente, mas se o texto mostrar lixo, pode ser clonagem de cristal instável

- **"Erro na porta serial: \<texto\>"** vermelho → o driver reportou falha ao ler. O erro exato está no texto (ex.: "Access denied", "The device does not exist", "I/O error"). Use esse texto para buscar solução específica.

### Double-trigger (nota dupla num hit)

Suba a **MÁSCARA**. Comece com 50 ms e aumente até parar.

### Pad não dispara com toque leve

Baixe o **LIMIAR** do pad. Verifique também o circuito: sem o resistor R1 (1MΩ) o sinal pode estar muito atenuado.

### Velocity sempre máxima (127)

Baixe o **PICO MÁX**. O valor atual está abaixo do que o piezo entrega mesmo em toques médios.

### MIDI não aparece no sampler

```bash
aconnect -l    # deve listar "BluePill MIDI Trigger" nas portas de entrada
```

Verifique se o canal MIDI da placa coincide com o canal que o sampler espera (geralmente 10 para percussão GM).

---

## Estrutura do repositório

```
bluepill-midi-suite/
├── firmware/
│   ├── platformio.ini          # ambientes de build (bluepill, bluepill_uart...)
│   ├── ld/
│   │   └── stlink_c8_128k.ld  # linker: código em 0x08000000, 128KB flash
│   └── src/
│       └── main.cpp            # firmware principal (USB composto MIDI+CDC)
├── configurator/
│   ├── src/
│   │   └── App.tsx             # interface React
│   └── src-tauri/
│       ├── tauri.conf.json     # configuração bundle (ícones, instaladores)
│       ├── icons/              # ícones em todos os tamanhos
│       ├── linux/              # udev rule, desktop entry, postinst
│       └── src/
│           └── main.rs         # backend Rust (serial, Tauri commands)
└── .github/
    └── workflows/
        └── release.yml         # CI: build automático Linux + Windows por tag
```

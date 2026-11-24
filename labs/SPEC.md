# Mobilepad Lab — especificação comum

Infraestrutura de medição reproduzível do Mobilepad. Três partes falam o mesmo formato:

| Parte | Onde | Papel |
|---|---|---|
| **LabRunner** (app Android) | `android/.../lab/` | gera comandos sintéticos pelo mesmo caminho do toque; registra o tempo de cada etapa no celular |
| **Lab** (companion Windows) | `pc-companion/Core/Lab/` + tela "Lab" | registra `recv`/`submit`; dispara os testes pelo `adb`, sincroniza relógios, observa o controle como um jogo (XInput e, para Bluetooth, Windows.Gaming.Input), coleta, analisa e mostra tudo na tela Lab (ao vivo, resultados, histórico) |

O usuário faz tudo pela tela **Lab** do companion: escolhe o teste e o link, clica em Rodar, vê o progresso ao vivo e depois os gráficos. `tools/latency/run_latency.py` fica como referência da primeira versão.

Tudo desligado por padrão: a instrumentação só existe enquanto um teste do Lab roda.

## 1. Relógio e unidades

- **Todo timestamp é `t_ns`: nanossegundos de relógio de parede UTC** (Unix epoch).
  - Android: `WallClock` (âncora em uma virada de `System.currentTimeMillis()` + `System.nanoTime()`), o mesmo de `input/LatencyTest.kt`. É CLOCK_REALTIME, o relógio que `adb shell date +%s%N` lê.
  - Windows (companion): `GetSystemTimePreciseAsFileTime` (C#: `DateTime.UtcNow` em .NET 8 já usa a versão precisa) convertido para ns desde 1970.
- O Lab do companion mede o deslocamento celular−PC por trocas pelo `adb shell` persistente (menor RTT), antes e depois de cada teste, e registra **offset, deriva e incerteza (= RTT mínimo / 2)**.

## 2. Impressão digital do estado (correlação entre etapas)

Cada passo de teste produz um **estado único**, reconhecível em qualquer etapa sem mudar o protocolo:
- `seq` (0, 1, 2…) é gravado no **eixo X do analógico direito**: `rx = 1000 + (seq * 7919) % 30000` (sempre positivo, distinto para 30000 passos seguidos; os testes usam rx só como marca).
- Quando o teste não pode usar o analógico direito (ex.: teste de analógicos), usa `ry` com a mesma fórmula; o campo `mark` do meta diz qual eixo.
- Botões, gatilhos e analógico esquerdo variam conforme o teste.
- Atenção: o Bluetooth HID pode reduzir a resolução dos eixos. O Lab casa pelo valor mais próximo dentro de ±(resolução) e registra a resolução observada.

## 3. Arquivos de um teste

Pasta da execução (no PC): `%LOCALAPPDATA%\Mobilepad\lab\runs\<AAAAMMDD-HHMMSS>-<teste>-<link>/` (o companion também exporta para onde o usuário escolher)

| Arquivo | Quem escreve | Conteúdo |
|---|---|---|
| `meta.json` | companion (+ campos do app) | teste, parâmetros, seed, link (`usb`/`wifi`/`bt`), versão e commit do app, modelo do celular, PC, sincronização (offset, deriva, incerteza), horários |
| `phone.csv` | app | `seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt` |
| `pc.csv` | companion | `seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt` (o companion deduz o `seq` pela marca; −1 quando não reconhece) |
| `obs.csv` | companion (observador) | `t_ns,stage,buttons,lx,ly,rx,ry,lt,rt` com `stage=obs` (como um jogo vê) |
| `res.csv` | companion | recursos ao longo do teste: `t_ns,source,metric,value` (CPU/memória do companion, bateria/temperatura/corrente do celular) |
| `summary.json` | companion | métricas calculadas |

`buttons` em hexadecimal (`0x1000`), eixos como inteiros com sinal (−32768..32767), gatilhos 0..255. Coluna vazia = não se aplica.

### Etapas (`stage`)

| stage | onde | momento |
|---|---|---|
| `cmd` | app | o teste decidiu o passo e chama `input.touch` (equivale ao toque) |
| `state` | app | `GamepadInput` publicou o estado mesclado (depois de remapeamento e macros) |
| `send` | app | o estado foi entregue ao socket (Wi-Fi/USB) ou a `sendReport` (Bluetooth) |
| `recv` | companion | estado decodificado de um pacote |
| `submit` | companion | `SubmitReport` do ViGEm retornou |
| `obs` | companion (observador) | XInput (USB/Wi-Fi) ou Windows.Gaming.Input (Bluetooth) viu o estado mudar, lido como um jogo lê |

Latência por etapa = diferença entre etapas do mesmo `seq`; total = `obs − cmd`.

## 4. Disparo pelo PC (intent)

`adb shell am start -f 0x20000000 -n <pkg>/io.github.pedropasinn.mobilepad.MainActivity --ez lab true --es labTest <teste> [--ei labCount N] [--el labSeed S] [--ei labRateHz R] [--ei labSeconds T] [--es labRun <id>]`

O app grava em `<external files>/lab/<labRun>/phone.csv` e, ao terminar, `<...>/done.json` (`{"ok":true,"count":N,"app_version":…,"app_commit":…,"device":…,"started_ns":…,"ended_ns":…}`). Erros vão em `done.json` com `ok:false` e `error`.

### Testes do LabRunner

| `labTest` | O que faz | Mede |
|---|---|---|
| `latency` | pulsos de um botão fora de combos, intervalos aleatórios (seed); marca em rx | latência por etapa, perdas, duplicados |
| `multi` | combinações aleatórias de 1–6 botões + gatilhos + analógico esquerdo, simultâneos | integridade (estado chegou inteiro?), perdas parciais |
| `analog` | varredura do analógico esquerdo (círculo + rampas) a `labRateHz` (padrão 250); marca em ry | taxa efetiva, resolução, erro de valor, latência dos eixos |
| `throughput` | o mais rápido possível por `labSeconds` (padrão 10) | estados/s entregues, perdas sob carga |
| `reliability` | N estados aleatórios rápidos (padrão 20 000) e termina em neutro | perdidos, duplicados, fora de ordem, botões presos no fim |
| `reconnect` | desconecta e reconecta o link atual `labCount` vezes, com um botão pressionado antes de cair | tempo de reconexão, botão liberado após a queda |

Segurança: todo teste solta tudo em `finally` (estado neutro), recusa iniciar se outro estiver rodando (guarda atômica) e para se o app sair do primeiro plano.

## 5. Companion: tela Lab e motor

- **Registro `recv`/`submit`**: ligado só durante um teste; buffer em memória pré-alocado, gravado ao fim. Custo medido e registrado no `meta.json`. Fora de teste, custo zero.
- **Motor** (`Core/Lab`): sincronização (shell `adb` persistente, ≥200 trocas, menor RTT, antes e depois), disparo do teste pelo intent da seção 4, observador em thread de alta prioridade, coleta do `phone.csv` pelo `adb`, análise (casamento por marca; percentis P50/P90/P95/P99, média, desvio, histograma, CDF, perdas, duplicados, fora de ordem, presos, taxa efetiva, resolução dos eixos; intervalo de confiança da mediana por bootstrap), recursos (CPU/memória do companion; bateria, temperatura e corrente do celular por `dumpsys battery` a cada 2 s).
- **Tela Lab**: escolher teste, link e parâmetros (contagem, seed, taxa); Rodar/Parar; progresso e números ao vivo; resultado com histograma, CDF, série no tempo, barras por etapa, tabela de métricas com a marcação medido/estimado; histórico de execuções por versão/commit do app e comparação entre duas execuções; exportar CSV/JSON.

## 6. O que é medido, estimado ou exige instrumento externo

| Grandeza | Tipo |
|---|---|
| tempos entre etapas no mesmo aparelho (cmd→state→send; recv→submit) | **medido** (relógio monotônico local) |
| send→recv, cmd→obs (celular→PC) | **estimado**: depende da sincronização; incerteza = RTT mínimo/2 + deriva |
| polling efetivo, perdas, duplicados, integridade, botões presos | **medido** |
| latência física do toque (vidro → app) | **externo**: câmera rápida ou sensor |
| latência até a imagem do jogo (PC → monitor) | **externo**: câmera rápida, PresentMon para a parte do PC |

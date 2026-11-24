# Auditoria do primeiro teste de latência

Versão auditada: `input/LatencyTest.kt` (app) + `tools/latency/run_latency.py` (PC), de 24/11/2026.

## O que o teste mede

Do instante em que o app decide apertar um botão (`input.touch`) até o instante em que o Windows entrega a mudança a um jogo (SDL, que lê XInput no USB/Wi-Fi e HID no Bluetooth). Ou seja: **pipeline do app + transporte + companion/ViGEm (ou pilha Bluetooth do Windows) + leitura pelo jogo**.

Não mede: o vidro do celular até o app (toque físico), nem o jogo até a imagem no monitor.

## Problemas encontrados e o que já foi corrigido

| # | Problema | Efeito | Estado |
|---|---|---|---|
| 1 | Sincronização com um `adb shell date` novo por amostra | ida e volta de ~57 ms e assimétrica (o celular lê o relógio no fim da viagem): latências ~270 ms falsas | **corrigido**: shell persistente, 200 trocas, ~8 ms de ida e volta, offset estável em ±0,005 ms entre sincronizações |
| 2 | Casamento "n-ésimo envio ↔ próxima chegada" | um aperto perdido desloca todos os seguintes: mediana falsa de ~190 ms | **corrigido**: cada chegada vai para o último envio antes dela |
| 3 | Macro de combinação (A + LB, janela de 50 ms) | todo A esperava 50 ms; toques < 50 ms sumiam | **corrigido no app** (toque curto vira toque) e o teste evita botões de combos |
| 4 | Incerteza da sincronização | a latência celular→PC é estimada; o erro é até RTT mínimo/2 (~4 ms) | **a fazer**: registrar e mostrar a incerteza em cada resultado (Lab) |
| 5 | Só uma etapa medida (total) | não diz onde está o atraso | **a fazer**: etapas `cmd/state/send/recv/submit/obs` (Lab) |
| 6 | 150–200 apertos | só ~2 amostras acima do P99 | **a fazer**: contagens de 1 000 a 100 000 e IC da mediana (Lab) |
| 7 | Um botão por vez, espaçado | não testa uso real (acelerar + drift + volante juntos) | **a fazer**: testes `multi`, `analog`, `throughput` (Lab) |
| 8 | Cancelamento no meio do aperto | botão pode ficar preso | **a fazer**: `finally` com estado neutro e guarda atômica (Lab) |
| 9 | Observador por polling (~160 mil leituras/s) | viés de no máximo alguns µs | aceitável; registrar a taxa de polling em cada execução |
| 10 | Relógio de parede do app ancorado em `currentTimeMillis` | âncora com erro < 1 ms (borda detectada por espera ativa) | aceitável; entra na incerteza |

## Resultados já medidos (S25 → notebook, 150 apertos, botão fora de combos)

| Link | P50 | P90 | P99 | Máx | Variação (desvio) | Perdidos |
|---|---|---|---|---|---|---|
| USB (companion + ViGEm) | **6,2 ms** | 6,9 ms | 11,0 ms | 11,2 ms | 0,9 ms | 0 |
| Bluetooth (QoS atual: best effort, 11,25 ms) | **10,7 ms** | 20–24 ms | 30–33 ms | 35–36 ms | 6–7 ms | 0–1 |
| Bluetooth (QoS "garantido", 3,75 ms) | 14,0 ms | 22–32 ms | 42–45 ms | 47–99 ms | 8–10 ms | 0–2 |
| Bluetooth + reenvio a cada 8 ms ("manter acordado") | 11–13 ms | 26–33 ms | 54–66 ms | 57–81 ms | 10–12 ms | 0–1 |

Incerteza da sincronização: ~±4 ms em cada número (RTT mínimo ~8 ms). Comparações entre linhas feitas do mesmo jeito são mais confiáveis que o valor absoluto.

Conclusões:
- **USB é a referência**: rápido e estável (desvio < 1 ms).
- **Bluetooth fica ~4–5 ms mais lento na mediana e tem caudas bem maiores** (P99 ~3x o do USB). Perde de vez em quando 1 aperto em 150.
- Pedir QoS "garantido" ou manter o rádio acordado **piorou**. Ficou a configuração original.
- Para jogar a sério (Mario Kart), USB; Bluetooth é bom para jogos menos exigentes.

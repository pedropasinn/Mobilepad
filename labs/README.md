# Mobilepad Lab

Laboratório de medição do Mobilepad: latência por etapa, variação, perdas, integridade, analógicos, vazão, reconexão e recursos, com dados brutos e histórico por versão.

- [`SPEC.md`](SPEC.md): o contrato entre o app, o companion e a análise (formatos, etapas, marca de correlação, disparo).
- [`AUDITORIA.md`](AUDITORIA.md): o que o primeiro teste media, os erros encontrados e os resultados já obtidos (USB × Bluetooth).

## Como usar

1. Celular ligado ao notebook pelo **cabo** (depuração USB): o cabo é usado para sincronizar relógios e disparar os testes, mesmo quando o link testado é Wi-Fi ou Bluetooth.
2. Conecte o controle pelo link que quer testar (USB, Wi-Fi ou Bluetooth) e deixe o Mobilepad aberto no controle.
3. No companion do Windows, abra a tela **Lab**, escolha o teste e os parâmetros e clique em **Rodar**.
4. Ao terminar, a tela mostra histograma, curva acumulada, série no tempo, barras por etapa e a tabela de métricas; cada execução fica no histórico (`%LOCALAPPDATA%\Mobilepad\lab\runs\`) com a versão e o commit do app.

## Testes

| Teste | Para quê |
|---|---|
| Latência | tempo por etapa e total, percentis, perdas |
| Múltiplos botões | vários botões, gatilhos e analógico ao mesmo tempo chegam inteiros? |
| Analógicos | taxa efetiva, resolução, erro e atraso dos eixos |
| Vazão | quantos estados por segundo passam sem perda |
| Confiabilidade | milhares de estados: perdidos, duplicados, fora de ordem, botões presos |
| Reconexão | tempo para voltar e se o botão pressionado na queda é liberado |

## O que é medido, estimado ou externo

Medido: tempos dentro do mesmo aparelho, perdas, integridade, taxas. Estimado: celular → PC (depende da sincronização de relógios; a incerteza aparece em cada resultado). Externo (exige câmera rápida ou sensor): vidro do celular → app e PC → imagem no monitor.

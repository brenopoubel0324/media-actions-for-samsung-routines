# Execução do plano pós-review 0.18

Execução técnica dividida conforme o [plano aprovado](planos/proximas-medidas-pos-review-0.18.md). Base documental `196821d73fc5335cf36c2c2360def13fb6106dd1`; base do código phone 0.18/Wear 0.8. Sem aparelho conectado. Medidas de energia física ainda pendentes.

| Entrega | Implementação/evidência | Review |
| --- | --- | --- |
| Phone 0.19 | Contagens da base e escolha global 5/15/30 s; padrão 5, liga/desliga por widget preservado | [405 testes; aprovado](reviews/execucao-intervalo-0.19.md) |
| Phone 0.20 | Snapshot por lote, mesma sessão/estado/posição; UNKNOWN explícito prevalece | [413 testes; aprovado](reviews/execucao-snapshot-0.20.md) |
| Phone 0.21 / Wear 0.9 | Conclusão Spotify com horário, cópia local, falhas Wear com contexto; três launchers/protocolo v1 preservados | [429 testes; aprovado](reviews/execucao-diagnostico-0.21.md) |
| Phone 0.22 | Usar visual padrão por instância, modo/rascunho/concorrência, preserva local | [453 testes; aprovado](reviews/execucao-heranca-0.22.md) |

As versões experimentais, imagens Android/Skia, APKs, manifestos, certificados, resultados e checksums estão em `downloads/vX-experimental/`. Cada entrega tem tag/release e commit próprio; Wear só mudou na entrega de diagnóstico. Imagens foram mostradas antes de publicar mudanças de UI. Os testes usam API 33/36; capturas nativas não são testes de firmware Samsung.

## Resultado energético verificável

Em um minuto simulado, seis widgets em 5 s: **96 → 36 leituras de sessão e 84 → 24 de progresso**, mantendo 12 ticks e 72 atualizações. Um widget permanece em 36/24; o ganho elimina repetição entre instâncias. O lote faz até uma seleção/uma leitura de progresso; o ciclo inclui leituras adicionais do observer, contabilizadas. Em 15/30 s há 4/2 ticks por minuto; callbacks continuam imediatos. Desligado, pausa, tela não interativa e ausência de duração não mantêm ticks periódicos.

[Dados e protocolo 0.19](medicoes/energia-progresso-0.19.md) · [Comparação 0.20](medicoes/energia-progresso-0.20.md). Não há coletor energético permanente, wake lock, alarme ou sincronização Wear periódica novos. Menos consultas/ticks não prova autonomia maior.

## Pendências que exigem aparelho/evidência

- **Energia, CPU e alocações físicas: NÃO MEDIDO.** Sem conclusão de ganho de bateria. Protocolo prevê isolamento, condições equivalentes, pelo menos três repetições alternadas e INCONCLUSIVO quando a resolução não permite atribuir consumo ao app.
- **Spotify, LockStar e WBR no Galaxy S25/Watch 8: NÃO TESTADO.** A retomada sem sessão precisa ser confirmada no Spotify real; diagnóstico correto não equivale a reprodução funcionando.
- Preencher versões exatas de Android/One UI/Wear OS/LockStar/WBR; executar a [ficha física por cenário](testes/validacao-galaxy-watch.md).
- O ajuste adicional de lockscreen ainda não foi descrito. Não foi assumido como padding ou edição externa e não bloqueou as entregas independentes.

Portanto, as etapas técnicas 1 (contagens), 2, 3, 4 e 6 foram implementadas; a etapa 5 e a parte física da etapa 1 permanecem abertas. Nenhuma compatibilidade de menu LockStar ou restauração de teclas Samsung é prometida pelo app.

# Self-audit — progresso circular e padding vertical negativo

Skill breno-review-audit:self-audit-loop, autoavaliação sem outro agente. Base 4338a54cfc5ad2d0afc60de014fac1eeb2525379. Escopo: borda opcional somente no centro, progresso branco com marcador escuro, padding negativo somente vertical, preferências/rascunho, atualizações limitadas e prévias antes de publicar.

## Rodada 1 — achados antes da correção

- **Médio — WidgetPlaybackObserver activeListener/keyListener:** trocar o alvo de mídia sem mudar PLAYING pode manter o arco da sessão anterior até o próximo tick. Correção: invalidar o progresso nesses callbacks mesmo quando o enum de reprodução é igual.
- **Baixo — WidgetProgressArtwork:** traço de 1 px na imagem base de 128 unidades pode ficar fino demais ao reduzir para um widget compacto. Correção: escala visual mínima do arco, documentando que ele é um indicador separado da borda estática.

Limites: não é seek; duração desconhecida mostra trilho; expansão vertical pode ser cortada pelo host; nenhuma alegação de animação contínua ou teste físico Samsung.

## Rodada 2 — correções e prévia nativa

Os achados da rodada 1 foram corrigidos; testes focados passaram. A primeira prévia nativa mostrou **baixo — tamanho do glifo**: o ícone central ficava menor ao ativar progresso devido à dupla redução pelo viewport do vetor e escala do bitmap. Ajustar o tamanho interno mantendo o anel separado do ajuste do ícone; preservar sombra opcional e cobertura de reapply ao desligar progresso.

Ainda na rodada 2: **médio — marcador com traço máximo**: a posição fixa do arco podia cortar o marcador nas espessuras maiores. Correção: inset do círculo acompanha o raio do marcador, mantendo o raster limitado e sem cortar sua borda.

**Médio — RemoteViews com múltiplos tamanhos:** compartilhar um único bitmap do centro entre as variantes de tamanho de uma instância, evitando rasterizações e memória duplicadas em hosts que reportam até 16 tamanhos. A prévia/testes de um único tamanho seguem usando o mesmo renderizador.


## Rodada 3 — APROVADO

Revisão final do diff, arquivos completos e callers após as correções: nenhum achado aberto, bloqueante ou alto. Três médios e dois baixos corrigidos nas rodadas anteriores. Simplificação preservou uma única fonte de seleção de mídia e um bitmap limitado por instância, compartilhado entre variantes responsivas. Não houve alteração de ações de mídia, protocolo Wear, permissões ou fallback Spotify.

Validação final: `:app:assembleDebug testDebugUnitTest lintDebug --console=plain`, BUILD SUCCESSFUL, **315 testes** (app 284, wear 9, shared 22), sem falhas/erros/pulos; lint dos três módulos sem apontamentos; `git diff --check` aprovado. Testes incluem ticks condicionais, tela on/off, pausa/desativação/close, seek/duração sem mudança do enum, borda somente central, transparência/marker, traço máximo, reapply, campo assinado apenas vertical, limites, preferências e rascunho. Prévia nativa exibida antes de publicar.

Bug sweep sem defeito aberto no escopo. Permanecem os limites físicos documentados: host pode cortar expansão vertical, duração depende do player, atualização aproximada de 5 s e AOD sem ticks. Sem benchmark ou validação física Samsung.

# Validação — 0.14 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 325 testes** (app 294, wear 9, shared 22), sem falhas/erros/pulos. Lint dos três módulos sem apontamentos. Integridade: [VALIDATION.json](downloads/v0.14-experimental/VALIDATION.json).

Os novos testes nativos verificam indicador somente em PLAYING, equivalência com aparência estática em PAUSED/TRANSITIONING/UNKNOWN com sombra e feedback ligados/desligados; reapply em sequências de Play/Pause/UNKNOWN/TRANSITIONING sem bitmap/background/padding antigos; borda decorativa removida em UNKNOWN e restaurada em PAUSED; prévia comparativa real antes da publicação.

A suíte anterior de observer, ticks condicionais, duração disponível/indisponível, seleção de mídia, controles, padding e Wear permanece incluída. O editor exibe reprodução/progresso de demonstração identificado; estado do widget real vem do player, sem antecipar confirmação após clique.

Phone 0.14, código 14, assinatura/applicationId preservados e zero uses-permission. Wear 0.8, fallback Spotify e intervalo de 5 s inalterados. Sem teste físico Samsung/player ou benchmark de bateria/FPS. Estado UNKNOWN inclui ausência de sessão e outras indisponibilidades; bordas dos botões são ocultadas em todos esses casos.

[Review](docs/reviews/playing-only-0.14.md) · [Configuração](docs/CIRCULAR_PROGRESS.md).

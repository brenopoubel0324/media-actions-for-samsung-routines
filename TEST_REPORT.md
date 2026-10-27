# Validação — 0.13 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 321 testes** — app 290, wear 9, shared 22 — sem falhas, erros ou pulos. Lint dos três módulos sem apontamentos. Integridade em [VALIDATION.json](downloads/v0.13-experimental/VALIDATION.json).

A review da 0.12 encontrou dois problemas médios reproduzíveis: ticks sem duração e redesenho de widgets sem indicador em atualizações somente de progresso. Os dois testes de regressão, executados em APIs 33/36, falharam antes da correção. A suíte corrigida inclui também chegada/perda de duração para iniciar/parar os ticks, além da cadência de 5 s, tela on/off, pausa, desativação, close, posição/duração, reapply, aparência, padding vertical assinado e regressões de mídia/Wear.

A duração conhecida é uma fixture explícita nos testes de agendamento; os testes de ausência de duração e filtragem exercitam os caminhos de produção sem substituir a entrega ao widget. Estes testes não comprovam comportamento do Spotify ou host Samsung físico.

Phone 0.13, código 13, mesmo applicationId/certificado, zero uses-permission. Wear 0.8/protocolo, fallback Spotify e aparência inalterados. Sem teste físico de player/Samsung/LockStar/Watch 8 nem benchmark de FPS/GPU/bateria. O intervalo continua em 5 s, e padding negativo continua sujeito ao corte pelo host.

[Review pós-publicação](docs/reviews/post-release-0.12-audit.md) · [Configuração](docs/CIRCULAR_PROGRESS.md).

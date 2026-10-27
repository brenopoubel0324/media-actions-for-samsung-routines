# Validação — 0.12 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 315 testes** (app 284, wear 9, shared 22), zero falhas/erros/pulos. Lint dos três módulos sem apontamentos. Integridade: [VALIDATION.json](downloads/v0.12-experimental/VALIDATION.json).

As novas verificações cobrem projeção monotônica da posição, pausa, seek, duração desconhecida, limites e valores inválidos; borda só no centro; anel branco/marcador escuro e transparência; atualização/remoção no reapply; espessura máxima sem cortar o marcador; padding negativo somente vertical; entrada numérica assinada, limites, isolamento por instância e rascunho após recriação.

Observação: testes em APIs 33/36 verificam ticks de cinco segundos apenas com opção ativada, parada ao desativar, ao pausar, apagar a tela ou fechar o observer; retomada com tela acesa; callbacks de seek/duração mesmo sem mudar PLAYING. A suíte anterior de mídia, widget e Wear continua incluída. Renderização nativa Android/Skia em API 33 gera a prévia antes da publicação.

Phone 0.12, código 12, mesmo applicationId/certificado, zero uses-permission. Wear 0.8/protocolo e fallback Spotify inalterados. Não houve teste físico de player/Samsung/LockStar/Watch 8 nem benchmark de FPS/GPU/bateria. O host pode cortar expansão vertical e a cadência é aproximada.

[Self-audit](docs/reviews/progress-ring-0.12.md) · [Comportamento](docs/CIRCULAR_PROGRESS.md).

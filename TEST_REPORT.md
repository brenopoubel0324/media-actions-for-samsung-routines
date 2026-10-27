# Validação — 0.11 experimental

`./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain` valida a versão phone 0.11 (código 11). Companion Wear 0.8 inalterado. **BUILD SUCCESSFUL: 294 testes** — app 263, wear 9, shared 22 — sem falhas, erros ou pulos. Lint dos três módulos sem apontamentos.

A suíte nova valida fallback apenas em Play/Pause sem sessão elegível; pacote/componente explícitos do Spotify; um gesto DOWN/UP de Play; nenhuma Activity; prioridade da mídia existente; ausência de fallback em Anterior/Próxima, acesso negado, ambiguidade, transição, comando não suportado ou falha. Receivers privados, desabilitados, ambíguos ou com permissão indisponível não recebem comandos. Consultar estado não inicia reprodução nem inventa estado do Spotify.

Regressões existentes de mídia, widget, bordas independentes, padding e protocolo Wear fazem parte da execução completa. Contratos Android testados em APIs 33/36; imagens existentes do widget em Android/Skia, sem alterações visuais nesta versão.

Mesmo applicationId `dev.local.mediaactions`, assinatura de atualização preservada, zero uses-permission. A única consulta de pacote adicionada é `com.spotify.music`.

[Self-audit](docs/reviews/spotify-fallback-0.11.md) · [Comportamento e limitações](docs/SPOTIFY_FALLBACK.md) · [Metadados e integridade](downloads/v0.11-experimental/VALIDATION.json).

**Limite:** a suíte simula o contrato de entrega Android, não a execução real pelo Spotify. Não foi executado teste físico no S25/LockStar/Watch 8, nem benchmark de FPS/GPU/bateria. Envio de Play não é confirmação de reprodução, e retomada sem sessão depende do receiver/estado da versão instalada do Spotify. O protocolo Wear não foi ampliado para iniciar players sem sessão.

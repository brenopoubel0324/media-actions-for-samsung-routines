# Validação — 0.17 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 373 testes** (app 342, wear 9, shared 22), sem falhas/erros/pulos. Lint dos três módulos sem apontamentos; `git diff --check` aprovado. Integridade: [VALIDATION.json](downloads/v0.17-experimental/VALIDATION.json).

A cobertura nova confirma que indicação ausente/inválida/combinada não vira tela inicial, leitura de mudanças do host, prioridade manual e retorno ao automático, rascunho/recriação, salvar/cancelar por instância, bloqueio de salvamento com campo inválido ou widget removido, encaminhamento da seleção ao ID correto, exclusão de identificação e ausência dessa opção no editor de padrões. Também verifica que salvar só o local preserva a herança de visual e os overrides já existentes. O número/dimensões continuam no editor após erro de validação.

Prévia nativa Android/Skia em pt-BR reúne o diálogo real de seleção e o editor de local da instância. As categorias e dimensões são dados simulados do host, não resultado de um Galaxy/LockStar físico. Toda a suíte anterior de geometria, preferências, configuração externa, progresso, mídia, Spotify assíncrono e Wear continua incluída.

Phone 0.17, código 17, mesmo applicationId/certificado, zero uses-permission e nenhuma dependência nova. Mecanismos de mídia/Spotify/Wear, geometria e intervalo/regras do progresso inalterados. Não houve teste físico de One UI/LockStar/Spotify/Watch 8 nem benchmark de bateria/FPS.

[Review](docs/reviews/widget-locations-0.17.md) · [Local das instâncias](docs/WIDGET_LOCATIONS.md) · [UX](docs/SETTINGS_UX.md).

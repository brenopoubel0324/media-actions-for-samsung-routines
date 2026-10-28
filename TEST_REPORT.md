# Validação — 0.16 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 358 testes** (app 327, wear 9, shared 22), sem falhas/erros/pulos. Lint dos três módulos sem apontamentos. Integridade: [VALIDATION.json](downloads/v0.16-experimental/VALIDATION.json).

A nova cobertura valida navegação para editor privado de padrões, ausência do editor completo na tela inicial, progresso ligado/desligado sem bordas, isolamento por widget, salvar/cancelar, herança de padrão com preservação de overrides, rascunho e seção aberta após recriação, erro em grupo recolhido, uma seção aberta por vez, prévia sem comando de mídia e rodapé acessível com 320 dp/fonte ampliada/teclado.

Prévias nativas Android/Skia usam pt-BR, com tela inicial, editor, controle de progresso ligado/desligado e modo escuro. Testes antigos usam CompoundButton (contrato de checked/click) para continuar validando a mesma lógica com os novos Switches nativos. A suíte existente de preferências, configuração externa, geometria, estado, mídia, Spotify assíncrono e Wear permanece incluída.

Phone 0.16, código 16, mesmo applicationId/certificado e zero uses-permission. Activity de padrões não exportada; Activity externa continua validando instância do host. Não houve alteração do mecanismo Spotify, protocolo Wear, transporte de mídia, intervalo ou regras de visibilidade do progresso. Sem teste físico de One UI/LockStar/Spotify/Watch 8 ou benchmark de bateria/FPS.

[Review](docs/reviews/settings-ux-0.16.md) · [UX e navegação](docs/SETTINGS_UX.md).

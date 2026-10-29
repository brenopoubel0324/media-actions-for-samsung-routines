# Validação — 0.18 experimental

Comando: `./gradlew :app:assembleDebug :wear:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 394 testes** (app 363, wear 9, shared 22), sem falhas/erros/pulos. Lint dos três módulos sem apontamentos; `git diff --check` aprovado. Integridade: [VALIDATION.json](downloads/v0.18-experimental/VALIDATION.json).

A review geral leu os fluxos atuais do celular, widget e Wear/shared. Três bugs médios foram reproduzidos antes das correções em API 33/36: perda de rascunho inválido após recriação, resultado antigo de Spotify sobrescrevendo toque mais recente e edição somente de local sobrescrevendo visual atualizado externamente. Uma defesa adicional evita parse inseguro ao restaurar campo numérico desativado.

A nova cobertura também confirma cursor/cancelamento, valores vazios e desativados, serialização de todas as opções independentes de aparência, alterações visuais reais/revertidas, default e override externos durante recriação, uso repetido do editor e salvamento acessível em 320 × 640 dp com Medidas aberto e fonte 200%. A imagem nativa Android/Skia mostra rascunho −25 preservado e a correção para −12; não é captura Samsung física.

As suites anteriores de acesso/seleção, comando único, geometria, bordas, padding, progresso, configuração externa, identificação por instância, browser Spotify, protocolo Wear, leases/deduplicação/deadlines continuam incluídas. As oportunidades de melhoria estão separadas de defeitos confirmados na [review](docs/reviews/app-audit-0.18.md).

Phone 0.18, código 18, mesmo applicationId/certificado e zero uses-permission. Wear permanece 0.8. Sem alteração no transporte, envio de comandos, retomada Spotify, geometria, intervalo de cinco segundos ou regras de progresso. Sem prova física de One UI/LockStar/Spotify/WBR no Watch 8 nem benchmark de bateria/FPS. A review não declara o aplicativo livre de bugs.

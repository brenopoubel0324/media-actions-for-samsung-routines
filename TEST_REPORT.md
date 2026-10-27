# Validação — 0.8-experimental

`./gradlew :app:assembleDebug :wear:assembleDebug testDebugUnitTest lintDebug` terminou com BUILD SUCCESSFUL.

| Módulo | Testes | Falhas/erros/pulos | Lint |
| --- | ---: | --- | --- |
| app | 182 | 0/0/0 | Sem apontamentos |
| wear | 9 | 0/0/0 | Sem apontamentos |
| shared | 22 | 0/0/0 | Sem apontamentos |

Total: **213 testes**. APIs 33 e 36 nos contratos Android; renderização Skia em API 33. Os testes originais do celular continuam cobertos, com integração RPC adicional.

Verificações: três componentes launcher no APK Wear, Intent explícito sem action/data do WBR e ACTION_MAIN; ação fixa independente de extras, recriação sem reexecução e fechamento sem iniciar outra Activity. Endpoint cobre identidade de sessão, acesso revogado, estado alterado, comandos suportados, mensagens inválidas, expiração durante consulta, deadline local, duplicação, ledger cheio e reinício.

Os dois APKs têm pacote dev.local.mediaactions, versionCode 8, versionName 0.8-experimental e zero uses-permission. Assinatura verificada e certificado igual ao das versões anteriores: 21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a. A autorização de listener continua sendo concessão separada do sistema.

Lint foi executado sem baseline/global suppression. Há exclusões locais justificadas para o serviço SDK cujo Binder valida GMS e para o array de capability descoberto por nome pelo SDK. Uma análise lint em cache reteve aviso antigo de backup; execução nova confirmou a regra de exclusão de backup presente. A execução final completa passou.

[Self-audit](docs/reviews/wear-button-remapper-implementation.md) e [resultados/checksums dos APKs](downloads/v0.8-experimental/VALIDATION.json). Nenhum emulador/dispositivo está conectado. Binding físico WBR, acesso local no Watch, transporte entre aparelhos, bateria e comportamento Samsung/LockStar/tela bloqueada não foram demonstrados por estes testes.

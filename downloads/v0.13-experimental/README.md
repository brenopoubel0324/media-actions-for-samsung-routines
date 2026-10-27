# Media Actions 0.13 experimental

[Baixar APK do celular](media-actions-phone-0.13-experimental-debug.apk) · [Prévia nativa](progresso-circular.png) · [Review e correções](../../docs/reviews/post-release-0.12-audit.md) · [Checksums](SHA256SUMS.txt) · [Validação](VALIDATION.json)

Correções encontradas na revisão da 0.12:

- Progresso sem posição/duração calculáveis não mantém ticks periódicos; um callback informando duração retoma o agendamento.
- Atualizações somente de progresso atingem apenas widgets com indicador ativado. Mudanças reais de Play/Pause continuam atualizando todos.

O intervalo permanece em 5 segundos. Aparência, borda só no centro, padding negativo somente vertical e fallback Spotify preservados. Instale como atualização sem desinstalar. Mesmo applicationId/certificado e configurações existentes.

Companion Wear 0.8 continua compatível e inalterado. A prévia mostra renderização Android/Skia com progresso de demonstração; não é captura Samsung física. Teste físico e benchmark de bateria/FPS continuam pendentes.

# Media Actions 0.14 experimental

[Baixar APK do celular](media-actions-phone-0.14-experimental-debug.apk) · [Prévia por estado](progresso-so-em-play.png) · [Review](../../docs/reviews/playing-only-0.14.md) · [Checksums](SHA256SUMS.txt) · [Validação](VALIDATION.json)

- Tocando: indicador circular aparece se ativado.
- Pausado: sem indicador; borda estática conforme configuração.
- Sem sessão/estado disponível: sem indicador nem borda decorativa dos botões.

A moldura externa e contornos do próprio desenho da família de ícones são independentes. Buffering/estado transitório não mostram indicador. O widget espera confirmação do player, sem presumir mudança após clique. Intervalo de 5 segundos e otimizações da 0.13 preservados.

Instale como atualização sem desinstalar. Mesmo applicationId/certificado e configurações. Companion Wear 0.8 e fallback Spotify inalterados. Prévia Android/Skia com progresso de demonstração; sem teste físico Samsung/player ou benchmark de bateria/FPS.

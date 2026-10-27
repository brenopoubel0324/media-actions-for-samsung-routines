# Media Actions 0.11 experimental

[Baixar APK do celular](media-actions-phone-0.11-experimental-debug.apk) · [Checksums](SHA256SUMS.txt) · [Validação](VALIDATION.json) · [Review](../../docs/reviews/spotify-fallback-0.11.md)

Play/Pause continua controlando a mídia selecionada. Quando não encontra sessão elegível, tenta retomar o Spotify em segundo plano por um gesto Play direcionado ao receiver público da versão instalada. Sem abrir interface. Não é acionado por Anterior/Próxima ou por falha/ambiguidade de uma sessão existente.

Instale como atualização, sem desinstalar. Assinatura e configurações do widget preservadas. O [companion Wear 0.8](../v0.8-experimental/media-actions-wear-0.8-experimental-debug.apk) permanece compatível; o fallback desta versão é dos controles locais do celular.

O envio não confirma reprodução. Spotify deve estar instalado, ter fila/login e aceitar a retomada. Não houve teste físico do Spotify; receiver/estado/restrições do sistema podem impedir a execução. [Detalhes e roteiro no aparelho](../../docs/SPOTIFY_FALLBACK.md).

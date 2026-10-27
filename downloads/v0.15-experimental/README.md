# Media Actions 0.15 experimental

[Baixar APK do celular](media-actions-phone-0.15-experimental-debug.apk) · [Fluxo e teste no aparelho](../../docs/SPOTIFY_FALLBACK.md) · [Diagnóstico no app](spotify-diagnostico.png) · [Review](../../docs/reviews/spotify-browser-0.15.md) · [Checksums](SHA256SUMS.txt) · [Validação](VALIDATION.json)

Correção do fallback Spotify sem sessão: conecta ao serviço de mídia público do Spotify, pede prepare + play e aguarda PLAYING por até seis segundos. O broadcast anterior foi removido. Uma tentativa por vez, sem retry após envio, sem abrir Activity ou usar tecla global. Acesso/player são revalidados antes de tocar. Callbacks/tokens de outros pacotes são rejeitados.

A linha **Last Spotify resume** no app mostra o último resultado concluído, incluindo recusa, serviço indisponível, timeout e falta de confirmação. Não confundir essa linha com estado atual da música. A prévia é do app real em Android/Skia, com serviço ausente simulado para mostrar a mensagem; não é captura de aparelho Samsung.

Instale como atualização, sem desinstalar. Mesmo applicationId/certificado e configurações. Wear 0.8/protocolo, aparência do widget e intervalo de 5 segundos preservados.

Ainda é necessário testar a 0.15 no aparelho. O novo caminho depende do Spotify autorizar a conexão e ter login/fila reproduzível; não confere privilégios SystemUI nem garante áudio. A imagem e os testes Android simulados não comprovam a execução física do Spotify.

# Media Actions 0.12 experimental

[Baixar APK do celular](media-actions-phone-0.12-experimental-debug.apk) · [Prévia nativa](progresso-circular.png) · [Configuração e limites](../../docs/CIRCULAR_PROGRESS.md) · [Review](../../docs/reviews/progress-ring-0.12.md) · [Checksums](SHA256SUMS.txt)

Em Widget settings:

1. Ative Borders around each button e Border only around Play/Pause para ter só a borda central.
2. Ative Track progress around Play/Pause para trocar a borda central por um arco branco com marcador escuro. O tamanho do ícone continua ajustável.
3. Widget vertical padding aceita −24 a 144 dp; negativo expande para cima/baixo e pode ser cortado pelo host. Horizontal mantém 0–144 dp.

Progresso real da sessão, com atualização aproximada a cada 5 segundos enquanto toca e a tela está interativa; tela apagada/AOD não mantém ticks. Sem duração conhecida, mostra apenas o trilho. Não é controle de busca. A imagem usa valores de demonstração e fundo de exemplo, sem captura de LockStar físico.

Instale como atualização sem desinstalar. Mesmo applicationId/certificado e configurações antigas preservadas; novas opções começam desativadas. Companion Wear 0.8 continua compatível e não precisa atualizar. Spotify fallback da 0.11 preservado.

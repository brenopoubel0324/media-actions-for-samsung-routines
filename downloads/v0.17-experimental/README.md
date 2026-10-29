# Media Actions 0.17 experimental

[Baixar APK do celular](media-actions-phone-0.17-experimental-debug.apk) · [Prévia por local](widgets-por-local.png) · [Guia](../../docs/WIDGET_LOCATIONS.md) · [Review](../../docs/reviews/widget-locations-0.17.md) · [Checksums](SHA256SUMS.txt)

A seleção de widgets mostra local, origem da informação, número e tamanho. No editor de cada instância, **Local deste widget** permite detectar automaticamente ou marcar **Tela de bloqueio / Tela inicial / Outro local**. Use Salvar alterações para manter; Cancelar descarta.

A identificação manual prevalece sobre a indicação do host. Quando o sistema não informa uma categoria reconhecida, a lista mostra Local não informado. O app não presume tela inicial. A informação automática depende do launcher/LockStar e pode estar ausente ou incorreta. Para identificar com certeza uma instância do LockStar sem indicação, abra a configuração a partir dela no editor do host, quando disponível, e marque Tela de bloqueio.

A marcação não move o widget, não muda o visual nem interrompe a herança do padrão. As configurações de aparência continuam individuais; os ajustes anteriores são preservados. O indicador de progresso mantém sua chave de ligar/desligar em Reprodução e toque.

Instale como atualização sem desinstalar. Mesmo applicationId/certificado, zero permissão nova, Wear 0.8 compatível. O intervalo de cinco segundos e as regras de progresso/estado, geometria e mecanismos de mídia/Spotify/Wear permanecem iguais.

A imagem reúne controles reais renderizados em Android/Skia, com valores de host simulados. Não é captura de um Galaxy S25. O local informado pelo LockStar físico e a disponibilidade de Editar ainda precisam de validação no aparelho.

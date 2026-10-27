# Media Actions 0.10 experimental

- [Instalar APK do celular](media-actions-phone-0.10-experimental-debug.apk)
- [Configuração das bordas e padding](../../docs/INDEPENDENT_BORDERS.md)
- [Quatro combinações de bordas](bordas-independentes.png)
- [Padding somente do widget inteiro](padding-widget.png)
- [Controles reais do editor](bordas-independentes-config.png)
- [Review aprovado](../../docs/reviews/independent-borders-0.10.md)
- [Checksums](SHA256SUMS.txt)
- [Validação e assinatura](VALIDATION.json)

Em Widget settings, configure Borders around each button e Border around the whole widget separadamente: ativação, espessura e cor/opacidade próprias. É possível usar as duas, uma ou nenhuma. Cada grupo de botões compartilha o mesmo estilo; os centros permanecem transparentes.

Widget inner padding ajusta horizontal e vertical **somente do widget inteiro**, mesmo com as bordas desligadas. Use o campo numérico ou −/+ para passos de 1 dp. A prévia mostra os valores efetivos, limitados pelo tamanho disponível para preservar o toque mínimo. Sem dimensões do host, o widget usa padding efetivo zero até recebê-las.

Instale o APK como atualização sobre a versão anterior, sem desinstalar. Assinatura e configurações antigas preservadas; bordas dos botões começam desativadas e padding em zero. Esta atualização é do celular. O [companion Wear 0.8](../v0.8-experimental/media-actions-wear-0.8-experimental-debug.apk) permanece compatível.

270 testes e lint aprovados; self-audit concluído em três rodadas sem achados abertos. As imagens são renderizações nativas Android/Skia, com seções do editor compostas para leitura. Host Samsung/LockStar físico e FPS/GPU/bateria não foram medidos.

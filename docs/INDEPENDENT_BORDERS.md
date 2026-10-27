# Bordas independentes e padding — 0.10 experimental

Em Widget settings, cada instância pode ter duas bordas separadas:

| Grupo | Ativação | Espessura | Cor |
| --- | --- | --- | --- |
| Borders around each button | Contorno dos três botões com o mesmo estilo | 1–8 dp | Preset ou hexadecimal próprio |
| Border around the whole widget | Moldura externa única | 1–8 dp | Preset ou hexadecimal próprio |

Use as duas, uma ou nenhuma. Os centros permanecem transparentes. Cor aceita `#RRGGBB` ou `#AARRGGBB`; `AA` determina a opacidade. Desligar uma borda preserva sua espessura/cor para uso futuro. O sombreamento opcional dos ícones continua separado.

![Quatro combinações](../downloads/v0.10-experimental/bordas-independentes.png)

## Padding somente do widget inteiro

Widget inner padding tem valores **horizontal e vertical separados**, sem configuração de padding dos botões. Campo numérico, slider e botões −/+ permitem passos de 1 dp, de 0 a 144 dp. Funciona com ou sem bordas e salva por instância ao confirmar a edição.

O padding estabelece espaço mínimo entre os controles e a parte interna do widget. A moldura externa continua no limite do widget. O grupo compacto permanece centralizado; valores pequenos podem não deslocar os botões quando já existe espaço livre maior. O modo de ocupar a largura distribui os controles na área restante.

O tamanho disponível limita o valor efetivo para preservar a linha mínima de três áreas de toque de 48 dp e sua altura de 48 dp. Gaps também são reduzidos quando necessário. Se o próprio host oferecer menos que o tamanho mínimo, o app não pode criar esse espaço. A prévia informa os valores efetivos e avisa quando limita o pedido.

Sem dimensões do host, o editor usa uma amostra identificada como prévia; o widget real usa fallback conservador de 144 × 48 dp, com padding efetivo zero, até receber dimensões. A aparência final e a área reservada pelo LockStar dependem do host.

![Comparação de padding](../downloads/v0.10-experimental/padding-widget.png)

[Configurações reais do editor](../downloads/v0.10-experimental/bordas-independentes-config.png) — seções renderizadas em Android/Skia e compostas para mostrar todos os campos. Não é captura de tela de um aparelho Samsung.

## Atualização e renderização

Instale o APK phone 0.10 como atualização sobre a versão anterior, sem desinstalar. Mesmo applicationId e certificado. A moldura configurada na 0.9 é preservada; a nova borda dos botões começa desativada e o padding H/V começa em zero. O companion Wear 0.8 permanece compatível e não foi alterado.

As bordas usam drawables nativos de traço e tint separados, sem bitmap de borda ou blur. O feedback de toque continua disponível. Não há benchmark de FPS/GPU/bateria nem teste físico de Samsung/LockStar nesta versão; as imagens são renderizações nativas Android/Skia com fundo de exemplo.

[Validação](../TEST_REPORT.md) · [Self-audit](reviews/independent-borders-0.10.md) · [APK e integridade](../downloads/v0.10-experimental/README.md)

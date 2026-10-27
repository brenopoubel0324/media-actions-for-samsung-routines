# Moldura do widget — 0.9 experimental

Uma única borda arredondada envolve a área completa do widget. Ela não contorna cada botão e não preenche o centro. Espaçamento, tamanho dos ícones e áreas de toque dos botões são preservados. O widget continua redimensionável pelo host.

Em Widget settings, ative **Border around the whole widget**. Ajuste **Border thickness** de 1 a 8 dp por slider, valor numérico ou passos de 1 dp. Em **Border color**, escolha White, Dark, Mint, Blue ou Gold; Custom aceita #RRGGBB e #AARRGGBB, com AA para opacidade. Entrada inválida impede salvar.

As alterações aparecem na prévia e só são persistidas ao salvar/aplicar. A moldura pode ser configurada por instância ou como padrão; configurações antigas iniciam com a moldura desligada. Desligar preserva largura e cor para uso posterior. Acesso externo à configuração continua dependendo do suporte do host Android/LockStar; a opção Edit widget no app permanece disponível.

Para o visual mais simples e menor custo de geração de imagens, use a moldura e desligue **Soft shadow behind icons**. A sombra continua opcional e independente. A moldura é um contorno nativo com centro transparente; a cor de fundo das imagens é papel de parede de exemplo. Não houve benchmark de desempenho/bateria nem captura do host físico.

As imagens de comparação e configuração foram renderizadas com o código real em Android/Skia e exibidas antes da publicação. O APK do celular passa para 0.9 (código 9), com a mesma assinatura. O companion Wear 0.8 permanece compatível e não precisa ser atualizado para esta mudança de aparência do celular.

[Revisão](reviews/widget-border-0.9.md) · [Comparação](../downloads/v0.9-experimental/borda-widget-comparacao.png) · [Editor](../downloads/v0.9-experimental/borda-widget-configuracao.png).

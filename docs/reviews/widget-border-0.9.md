# Self-audit — moldura do widget, 0.9-experimental

Skill: breno-review-audit:self-audit-loop. Autoavaliação do executor, sem outro agente.

Escopo: diff contra 51e1d8d3c4836f92adb84d15a2f207a4353ae9ce e arquivos novos. Requisitos vigentes: uma borda única no widget completo, interior transparente, espessura/cor configuráveis, geometria dos botões preservada e imagens antes da atualização do GitHub. A proposta inicial de círculos por botão foi retirada antes de publicação.

## Rodada 1 — achados antes das correções

- **Médio — WidgetBorderSection / preset após cor inválida:** o callback comparava apenas a última cor válida; um preset igual àquela cor não corrigia texto inválido no campo. Correção: marcar a seleção como personalizada enquanto o texto estiver inválido e comparar o preset ao texto visível; adicionar regressão que seleciona novamente a mesma cor válida após erro.
- **Baixo — WidgetRendererTest.stylePickerAndAutomaticSpacingApplyTogether:** o teste antigo pressupunha um único Spinner na página. Com o seletor de cor adicional ele não identifica mais o controle desejado. Correção: selecionar o campo de estilo pela descrição semântica e continuar verificando os mesmos resultados de estilo/espaçamento.

Nenhum bloqueante/alto identificado nesta rodada. As verificações finais serão registradas após corrigir os achados e repetir os checks afetados. Risco residual: renderização/toque pelo host Samsung físico e custo de renderização no aparelho não foram medidos.

## Rodada 2 — releitura e veredito

**APROVADO.** Nenhum achado bloqueante/alto e nenhum novo médio/baixo em aberto. O médio de recuperação do campo de cor e o baixo do seletor de testes foram corrigidos. Releitura dos arquivos novos/alterados, preferências, restauração de draft, RemoteViews e chamadores confirmou o escopo.

Validação: BUILD SUCCESSFUL para assembleDebug + testDebugUnitTest + lintDebug; 231 testes (app 200, wear 9, shared 22), zero falhas/erros/pulos e lint sem apontamentos. Native Skia confirma moldura única no limite do widget, centro/gaps transparentes, espessura/cor/alpha renderizados e remoção correta no reapply sem mover controles. APIs 33/36 verificam armazenamento por instância, validação de entradas, preset após erro e draft após recriação.

Bug sweep: preferências legadas mantêm borda desligada, limites 1–8 dp, cores RGB/ARGB, alpha opcional, desativação sem apagar valores, isolamento entre widgets, input inválido sem salvar, seletor de estilo existente, versão 9 e mesma assinatura de atualização. Não foram modificados comandos de mídia, transporte Wear ou permissões.

A moldura usa um GradientDrawable nativo com stroke e tint, sem bitmap de borda nem blur. Isso evita o custo de geração/cache da sombra para quem usa apenas a moldura; não houve benchmark de FPS, bateria ou GPU no aparelho. Sombra continua opcional e independente. A limitação externa restante é o host físico Samsung/LockStar.

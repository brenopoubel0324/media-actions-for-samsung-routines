# Validação — 0.31 estável

BUILD SUCCESSFUL. 627 testes app Debug +22 shared Debug +15 Wear Debug e627 app Release: 1291 execuções, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou.

[Review](docs/reviews/wallpaper-colors-0.31.md) · [Integridade](downloads/v0.31/VALIDATION.json) · [Comportamento e limites](docs/WALLPAPER_COLORS.md).

Novos testes: contraste/hints/reserva, destinos independentes, eventos repetidos/coalescidos, consulta atrasada, mudança de origem, tela apagada/retomada, remoção/manual, falha/retry, cancelamento/pausa do editor, backup v1/v2/v3, perda de autorização e reinício do observador. Renderizações nativas Android/Skia incluem mídia/volume e editor. Contadores simulados verificam zero enumerações de sessões por evento de cor com observador pronto, zero releituras de wallpaper em duplicatas e zero entregas de cores com tela apagada.

APK original Release não depurável, assinatura histórica e code31. Sem medição de bateria ou validação física de Depth/LockStar/atualização. Esses resultados demonstram trabalho limitado no ambiente simulado; não substituem comparação no mesmo dispositivo. Wear não foi alterado.

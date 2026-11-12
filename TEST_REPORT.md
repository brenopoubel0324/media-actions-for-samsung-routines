# Validação — 0.33 estável

BUILD SUCCESSFUL. App Debug 635, app Release 635, shared Debug 22 e Wear Debug 15: **1307 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou.

[Review](docs/reviews/compact-height-0.33.md) · [Integridade](downloads/v0.33/VALIDATION.json).

Compacta aceita16/24/32/40/48/56dp com largura e alvos48dp preservados, host com/sem dimensões, editor com os dois modos originais e salvamento32dp. Store/Bundle/backup mantêm altura menor e leem a opção removida como Compacta sem perder os demais campos. Testes antigos da opção removida foram substituídos pelos testes do comportamento pedido. Prévias nativas Android/Skia exibidas antes de publicar. Regressões de mídia, wallpaper, volume e Wear passaram.

Mesmo package/certificado histórico, Release não depurável, code33. Sem validação física de encaixe/toque/atualização no LockStar e sem medição de bateria. Sem trabalho recorrente novo. Wear não foi alterado.

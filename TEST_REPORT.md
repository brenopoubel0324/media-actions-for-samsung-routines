# Validação — 0.32 estável

BUILD SUCCESSFUL. App Debug 642, app Release 642, shared Debug 22 e Wear Debug 15: **1321 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou.

[Review](docs/reviews/fitted-frame-0.32.md) · [Integridade](downloads/v0.32/VALIDATION.json).

Testes API33/36 cobrem contorno decorativo mais justo, alvo48dp preservado, escala/espaçamento, reserva de ring/bordas, volumeH/V, padding/espessura, hosts limitados e sem dimensões, reaplicação, editor/salvamento, Store/Bundle e backup1/2/3/4. Renderização nativa Android/Skia exibida antes da publicação. Regressões de wallpaper, mídia/recuperação, volume e Wear passaram.

Mesmo package/certificado histórico, Release não depurável, code32. Sem validação física de LockStar/encaixe/toque/atualização e sem medição de bateria. A moldura adiciona apenas cálculo por renderização, sem trabalho recorrente novo. Wear não foi alterado.

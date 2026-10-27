# Validação — 0.9 experimental

`./gradlew :app:assembleDebug testDebugUnitTest lintDebug` terminou com BUILD SUCCESSFUL. Total: **231 testes** — app 200, wear 9, shared 22 — sem falhas, erros ou pulos. Lint dos três módulos sem apontamentos. Contratos Android em APIs 33/36, renderização Skia em API 33.

A validação nova cobre borda única do widget com centro/gaps transparentes, cores/alpha reais, espessuras 1/3/8 dp, tamanho mínimo, famílias de ícone, geometria preservada e remoção no reapply. Editor cobre preferências antigas, valores inválidos, recuperação por preset, isolamento por instância e rascunho após recriação. Regressões existentes de widget, mídia e Wear permaneceram aprovadas.

APK phone 0.9, código 9, applicationId dev.local.mediaactions e certificado de atualização preservados. Zero uses-permission. Companion Wear 0.8 não mudou.

[Self-audit: 2 rodadas](docs/reviews/widget-border-0.9.md) — um médio e um baixo corrigidos; nenhum bloqueante/alto restante. [Metadados e integridade](downloads/v0.9-experimental/VALIDATION.json).

Imagens são renderizações Android/Skia. Host Samsung/LockStar físico e benchmark de FPS/GPU/bateria não foram executados. A moldura evita gerar/cachear um bitmap de borda; não se afirma ganho de FPS medido.

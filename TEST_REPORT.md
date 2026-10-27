# Validação — 0.10 experimental

`./gradlew :app:assembleDebug testDebugUnitTest lintDebug` terminou com BUILD SUCCESSFUL. Total: **270 testes** — app 239, wear 9, shared 22 — sem falhas, erros ou pulos. Lint dos três módulos sem apontamentos. Contratos Android em APIs 33/36, renderização Skia em API 33.

A validação nova cobre as quatro combinações das bordas independentes, cores/alpha, transparência dos centros e gaps, espessuras e atualização/remoção no reapply. O padding H/V do widget é validado por geometria em 864 combinações, renderização real, limites que preservam áreas de toque de 48 dp quando o host comporta o tamanho mínimo, fallback sem dimensões e retorno a zero. O editor cobre preferências antigas, valores inválidos sem salvamento parcial, recuperação por preset, isolamento por instância, uso sem bordas e rascunho após recriação. Regressões existentes de widget, mídia e Wear permaneceram aprovadas.

APK phone 0.10, código 10, applicationId dev.local.mediaactions e certificado de atualização preservados. Zero uses-permission. Companion Wear 0.8 não mudou.

[Self-audit: 3 rodadas](docs/reviews/independent-borders-0.10.md) — três achados baixos corrigidos, nenhum achado aberto. [Metadados e integridade](downloads/v0.10-experimental/VALIDATION.json).

Imagens são renderizações Android/Skia. Host Samsung/LockStar físico e benchmark de FPS/GPU/bateria não foram executados. As bordas usam drawables nativos sem gerar/cachear um bitmap de borda nem aplicar blur; não se afirma ganho de FPS medido.

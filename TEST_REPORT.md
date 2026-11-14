# Validação — 0.35 estável

Cadeia local completa aprovada na fonte `37319ad0d4a3f23649a55456bb4d8cfe47e9b1af`: app Release 713, app Debug 713, Preview 4, shared Debug 22 e Wear Debug 15, total **1467 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovados. APKs Release/Preview/Wear inspecionados; oficial com mesmo package/certificado, code35 e sem debug/testOnly.

Três médios corrigidos em revisão: feedback de autorização, padding negativo recortando alvos e falha de um host interrompendo o lote. Plugins Permission Manager e CI eficiente e self-audit concluídos. [Execução e limites](docs/reviews/combined-widget-and-identity-0.35.md) · [Integridade](downloads/v0.35/VALIDATION.json).

Propostas Talhe/Tocavo/Tavumo apresentadas, sem identidade definitiva aplicada. Sem validação física ou bateria medida; dez gestos de volume em observador preparado não adicionam consultas de mídia nem RemoteViews nos testes API33/36. Atualização sobre o original, sem desinstalar.

# Validação — 0.34 estável

BUILD SUCCESSFUL. App Debug 653, app Release 653, shared Debug 22 e Wear Debug 15: **1343 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou.

[Review](docs/reviews/wallpaper-diagnostic-0.34.md) · [Integridade](downloads/v0.34/VALIDATION.json).

Testes API33/36 verificam status com grupos fechados, null/erro/resultado, cópia sem mensagem de exceção, primária/hints da fonte simulada, releitura explícita sem concorrência duplicada, origens independentes, dados tardios, ausência de consultas extras ao copiar e nenhuma enumeração de sessão por diagnóstico/releitura. Preferências não são salvas pelas ações de diagnóstico; modo manual não lê wallpaper. Prévias Android/Skia exibidas antes de publicar.

Mesmo pacote/certificado histórico, Release não depurável, code34. Não foi confirmado no aparelho por que os controles ficaram brancos no fundo branco; a detecção não mudou; o uso da reserva nas bordas foi corrigido e a resolução do relato não é declarada. Sem medição de bateria, atualização física ou compatibilidade Depth/LockStar confirmada. Wear não foi alterado.

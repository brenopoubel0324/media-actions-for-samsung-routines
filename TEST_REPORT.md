# Validação — 0.34 estável

BUILD SUCCESSFUL. App Debug 653, app Release 653, shared Debug 22 e Wear Debug 15: **1343 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou.

[Review](docs/reviews/wallpaper-diagnostic-0.34.md) · [Integridade](downloads/v0.34/VALIDATION.json).

Testes API33/36 verificam status com grupos fechados, null/erro/resultado, cópia sem mensagem de exceção, primária/hints da fonte simulada, releitura explícita sem concorrência duplicada, origens independentes, dados tardios, ausência de consultas extras ao copiar e nenhuma enumeração de sessão por diagnóstico/releitura. Preferências não são salvas pelas ações de diagnóstico; modo manual não lê wallpaper. Prévias Android/Skia exibidas antes de publicar.

Mesmo pacote/certificado histórico, Release não depurável, code34. Não foi confirmado no aparelho por que os controles ficaram brancos no fundo branco; a detecção não mudou; o uso da reserva nas bordas foi corrigido e a resolução do relato não é declarada. Sem medição de bateria, atualização física ou compatibilidade Depth/LockStar confirmada. Wear não foi alterado.

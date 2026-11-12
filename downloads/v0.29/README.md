# Media Actions 0.29 estável

Atualização estável do app original: os widgets de mídia e volume ignoram callbacks equivalentes de tamanho/opções. Preserva resize real, edição, atualização explícita, primeira entrega, exclusão e retry após falha. Aparência e comandos permanecem com os contratos existentes.

**[Baixar Media Actions 0.29](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/raw/refs/tags/v0.29/downloads/v0.29/media-actions-phone-0.29.apk)**

Instale sobre o original, sem desinstalar. Mesmo package e certificado histórico, code29. Backup formato1 compatível; Preview e Wear não precisam de atualização para esta correção.

Nos testes API 33/36, dez callbacks equivalentes após a primeira entrega produzem zero novos updateAppWidget e zero consultas de sessão, inclusive com observer ativo e progresso desativado. Resizes reais H/V e falhas de entrega/notificação continuam sendo tratados. **O efeito sobre o tempo de apagamento do LockStar ainda não foi confirmado no aparelho; não há alegação de economia de bateria ou correção física do sintoma.**

Validação: 1149 execuções de testes Debug/Release, lint, build e review. [Review](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/blob/v0.29/docs/reviews/options-dedup-0.29.md) · [Detalhes e protocolo físico](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/blob/v0.29/docs/WIDGET_OPTIONS_UPDATES.md) · [Integridade](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/tree/v0.29/downloads/v0.29).

SHA-256: `dde1be70b1cc8fd445175de562ab080e3e8634ccc580a2e733a474d303adfc6e`.

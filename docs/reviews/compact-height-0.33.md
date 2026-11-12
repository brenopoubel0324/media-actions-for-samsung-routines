# Self-audit — altura do Compacta 0.33

Escopo: diff contra 0583a7174c74fe52e2ef2627e406b2533aeb440e (main/v0.32). Correção de escopo pedida pelo usuário: permitir altura menor que 56 dp no modo Compacta e retirar Justa aos botões. Skill breno-review-audit:self-audit-loop, revisão do próprio agente.

## Rodada 1

Simplificação: remoção do cálculo automático de contorno, do terceiro item do editor e dos testes exclusivos dessa opção. Mantida apenas uma leitura compatível do valor antigo FIT_BUTTONS como COMPACT para não perder os demais ajustes. Um único limite mínimo compartilhado pelo model/editor, 16 dp. Backup permanece v4, sem novo campo ou formato.

Leitura dos arquivos completos alterados e callers de geometria, renderer, editor, Store/Bundle, backup e caches de apresentação. Sem achados bloqueantes/altos/médios/baixos nesta passagem. A altura visual foi desacoplada do limite de 48 dp dos alvos; largura preservada. Volume vertical conserva espaço para dois botões. Caminho sem dimensões mantém limpeza de mínimos/largura e restauração dos eixos na reaplicação.

## Resultado — APROVADO

Uma rodada de self-audit, nenhum bloqueante/alto ou outro achado restante, sem follow-ups de código. Build `:app:assembleRelease :app:testReleaseUnitTest testDebugUnitTest :app:lintRelease lintDebug` terminou com BUILD SUCCESSFUL. App Debug 635 + app Release 635 + shared Debug 22 + Wear Debug 15 = **1.307 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug sem issues; git diff --check passou. Testes da opção retirada foram substituídos por cobertura do ajuste solicitado, incluindo migração de preferências/Bundle/backup.

Prévias Android/Skia em 64/48/32 dp exibidas antes da publicação. Mesmo pacote e certificado histórico; sem serviços/permissões/timers novos e sem mudança no protocolo Wear. Assinatura/manifest/hash e publicação são conferidos nos artefatos da distribuição.

Riscos residuais: revisão do próprio agente, sem segunda revisão independente; encaixe/toque em LockStar e atualização física do APK não foram testados pelo agente. Bateria não foi medida. A altura é decorativa e não reduz a área reservada pelo host; valores muito baixos podem atravessar a arte dos controles. Volume vertical preserva a altura necessária aos dois botões. Backups com altura menor podem ser recusados por apps anteriores à 0.33.

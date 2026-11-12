# Self-audit — wallpaper automático 0.31

Escopo: diff contra c9726166aa4def938250786f4d01b83439fb3e1a (main/v0.30), pedido de cores automáticas com wallpaper claro/escuro, reserva manual, mídia/volume, bloqueio/inicial e prioridade de consumo. Skill breno-review-audit:self-audit-loop, revisão pelo próprio agente, sem segundo agente independente.

## Rodada 1 — achados antes da correção

- **Médio — WallpaperColorCoordinator.updateConsumers:** o cache podia reter uma origem que já não tinha consumidores. Se ela fosse selecionada de novo após um período sem observação, a cor antiga poderia dispensar a leitura inicial. Corrigir retendo apenas origens necessárias e invalidando consultas em voo quando muda o conjunto; ignorar callbacks sem consumidores naquela origem. Acrescentar teste de sair/voltar a uma origem e de consulta atrasada.
- **Baixo — WallpaperColorCoordinator, retentativa de assinatura:** após falha ao registrar o listener, uma nova assinatura bem-sucedida podia reutilizar o valor da consulta anterior. Fazer uma leitura inicial na nova assinatura, mesmo com cache conhecido.
- **Baixo — callback de cores idênticas:** ainda enfileirava uma passagem que terminava sem entregas. Descartar antes de enfileirar quando o contraste não mudou, preservando retentativa de entrega falha no próximo evento.
- Sem bloqueantes/altos nesta passagem. A integração já agrupa o snapshot de reprodução por lote, verifica autorização uma vez por lote e mantém cache de entrega somente após sucesso.

## Correções

Os três achados foram corrigidos na primeira rodada. O conjunto de origens controla a retenção do cache e invalida consultas antigas; callbacks sem consumidores são ignorados. Uma assinatura nova bem-sucedida consulta as cores atuais. Callback repetido não enfileira passagem vazia; falha de entrega conserva a possibilidade de retry no próximo evento. Testes específicos exercitam todas essas ramificações, inclusive resultado tardio após apagar/acender a tela.

## Rodada 2 — APROVADO

Releitura dos arquivos completos alterados e callers: Application, coordenador, providers, renderer/ring, apresentação do observador, editor/lifecycle, persistência/Bundle e backup. Sem novos achados bloqueantes/altos/médios/baixos. Sem refatoração de comandos ou do protocolo Wear. Ações continuam usando seu gateway fresco; estado de apresentação não executa transport controls.

Validação final: `:app:assembleRelease :app:testReleaseUnitTest testDebugUnitTest :app:lintRelease lintDebug`, BUILD SUCCESSFUL. App Debug 627, app Release 627, shared Debug 22 e Wear Debug 15 = **1.291 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug sem issues; `git diff --check` passou. Novas coberturas API 33/36: contraste/reserva, source separado, callbacks repetidos, consultas tardias, tela apagada/retomada, falha/retry, preview cancelado/pausado, backup 1/2/3, grant revogado e observador reiniciado. Prévias Android/Skia geradas e exibidas ao usuário antes de publicar.

Bug sweep: sem bloqueante/alto restante; um médio e dois baixos corrigidos, nenhum follow-up de código desta revisão. Conferência de manifest/certificado/hash do APK e main/tag/release registrada nos artefatos de distribuição.

Limites: review do próprio agente, sem segunda revisão independente; Robolectric/Skia não substituem aparelho físico. Depth pode não informar todas as trocas ou cores do bloqueio. Contraste é global, não amostra sob o widget. Processo encerrado depende do próximo início; não há promessa após Forçar parada. Comparação antes/depois no mesmo aparelho, energia real, renderização/timeout do LockStar e atualização física do APK seguem pendentes. Os contadores demonstram trabalho limitado na simulação, não economia de bateria medida.

# Self-audit — moldura justa 0.32

Escopo: diff contra f8a995f3fee7552ad804e60281659578a14c1a5f (v0.31/main). Pedido: aproximar a borda dos botões sem reduzir a confiabilidade do toque. Skill breno-review-audit:self-audit-loop; revisão do próprio agente, sem segundo agente independente.

## Rodada 1 — achados antes da correção

- **Baixo — WidgetAppearanceSection/frameControls:** o novo modo automático ainda exibe a altura manual desativada, que pode mostrar 64 dp enquanto a moldura tem 34 dp. Ocultar o grupo de altura em Justa aos botões e testar o salvamento pelo editor.
- Sem bloqueantes/altos/médios. Geometria de toque preservada; borda decorativa pode ficar dentro da área clicável. Reservar espaço para ring/bordas mantém tamanho estável quando muda play/pause. Native WRAP_CONTENT limita a camada no fallback sem dimensões. Reaplicação deve limpar largura e mínimos anteriores.

O grupo de altura manual foi ocultado no modo Justa; o teste do editor confirma seleção e salvamento.

## Rodada 2 — achado antes da correção

- **Médio — WidgetRenderer, fallback sem dimensões/reapply:** sair de Justa para Preencher/Compacta no mesmo RemoteViews pode manter a largura/altura explícita anterior do grupo, pois o fallback não restaura MATCH_PARENT. Restaurar os dois eixos conforme o modo e estender o teste de reaplicação para host sem dimensões. Não afeta o tamanho físico de toque nos hosts com dimensões conhecidas, mas precisa de correção para restaurar os modos anteriores com fidelidade.

## Rodada 3 — validação e correção de fixture

- **Baixo — WallpaperRuntimeTest.screenOffHasNoDeliveriesAndManualModeReleasesListener:** a fixture emite cor clara durante tela apagada, mas a consulta seguinte ainda devolve null. Dependendo da ordem da fila, é correto permanecer com a reserva anterior sem entrega; o teste exigia entrega e falhou em Release. Atualizar a fonte simulada para a mesma cor do callback antes de acender a tela, sem enfraquecer as asserções nem mudar produção. A simulação passa a representar a troca real de wallpaper do cenário.
- O achado de reaplicação foi corrigido restaurando os dois eixos no fallback; o teste agora alterna todos os modos com e sem dimensões conhecidas. Sem novos bloqueantes/altos.


## Resultado final — APROVADO

Três rodadas, nenhum bloqueante/alto encontrado ou restante. Um médio de reaplicação e dois baixos de UI/fixture corrigidos, sem follow-ups de código desta revisão. A terceira passagem releu as alterações e callers de geometria, renderização, editor, Store/Bundle e backup; asserções não foram enfraquecidas para acomodar falha de produção.

Validação final: `:app:assembleRelease :app:testReleaseUnitTest testDebugUnitTest :app:lintRelease lintDebug`, BUILD SUCCESSFUL. App Debug 642 + app Release 642 + shared Debug 22 + Wear Debug 15 = **1.321 execuções**, zero falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug sem issues; git diff --check passou. Testes API 33/36 incluem alvo 48 dp, arte/escala/gap, ring/bordas, volume H/V, padding/espessura, host limitado/sem dimensões, reaplicação nos três modos e dois tipos de dimensões, editor/salvamento, Store/Bundle e importação de backup 1/2/3/4. Prévias Android/Skia exibidas ao usuário antes de publicar.

O backup v4 permite o novo valor de enum; v1/v2/v3 continuam legíveis. Modos/configurações antigos preservados. Nenhum timer, serviço, permissão ou leitura privilegiada nova; a mudança adiciona somente geometria decorativa na renderização já existente. Protocolo e app Wear não foram alterados.

Limites: revisão do próprio agente, sem segundo auditor; sem validação física de LockStar, toque/encaixe ou atualização do APK e sem medição de bateria. A moldura pode ficar dentro do alvo clicável e não reduz a área reservada pelo host. Progresso/bordas individuais reservam espaço para sua própria arte. Distribuição automática mantém a distância entre controles calculada pelo host. Integridade/assinatura e publicação conferidas nos artefatos da distribuição.

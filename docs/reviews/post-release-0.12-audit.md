# Review pós-publicação 0.12

Skill: breno-review-audit:self-audit-loop, autoavaliação sem agente independente.

Worktree inicialmente limpo. Escopo principal: delta da 0.12 publicada (`b553d2a29ddfee25e69e5c8fbc7e7dc016e49964`) contra a 0.11 (`4338a54cfc5ad2d0afc60de014fac1eeb2525379`), mais leitura dos callers locais e fallback Spotify. Tags não existem no checkout local; os SHAs publicados resolvem o mesmo diff. Contrato: pedidos do usuário para aparência/progresso/padding, preservando mídia e Wear. A recomendação de intervalo de 15 s não foi autorizada como alteração e não será aplicada nesta correção.

## Rodada 1 — achados antes das correções

1. **Médio — WidgetPlaybackObserver.updatePulse:** verifica PLAYING/opção/tela, mas não verifica disponibilidade da posição/duração. Um player ao vivo ou sem duração mantém consultas/redesenhos de um trilho invariável a cada 5 s. Correção: só agendar ticks quando houver progresso calculável; callbacks de duração retomam o agendamento.
2. **Médio — WidgetPlaybackObserver.refresh/emit e MediaWidgetProvider.refreshAll:** o caminho de progresso usa refreshAll mesmo com enum inalterado. Com dois widgets, um sem progresso também é redesenhado periodicamente. Correção: atualização por progresso deve filtrar instâncias com indicador ativado; mudanças reais do enum continuam atualizando todas.
3. **Baixo — WidgetRingVisualTest export:** chamada a parentFile potencialmente nulo gera warning do compilador Kotlin. Correção: tornar a pré-condição explícita no caminho fixo do artefato.

Não identificado bloqueante/alto. Testes de regressão reproduzirão os dois médios antes da alteração. Riscos residuais: teste físico Samsung/player e benchmark não disponíveis; padding negativo continua sujeito ao corte do host; Spotify fallback depende de receiver público e não confirma reprodução.

Reprodução: `:app:testDebugUnitTest --tests '*WidgetProgressReviewTest'` executou quatro casos (dois contratos × APIs 33/36) e os quatro falharam na versão original: redraws sem duração e widget simples redesenhado no callback de progresso. Não são hipóteses de benchmark; são comportamentos observáveis do código.


## Rodada 2 — APROVADO

Releitura do diff corrigido e callers completos. Os dois médios e o baixo foram corrigidos; nenhum achado aberto, bloqueante ou alto. Sem mudanças em comandos de mídia, permissões, Wear, renderer ou intervalo configurado de 5 s. A leitura de progresso é uma dependência explícita para testar a chegada/perda de duração sem fabricar estado do player no código de produção.

Validação final: `:app:assembleDebug testDebugUnitTest lintDebug --console=plain`, BUILD SUCCESSFUL, **321 testes** (app 290, wear 9, shared 22), zero falhas/erros/pulos. Lint dos três módulos sem apontamentos e `git diff --check` aprovado. Os quatro casos que falhavam na 0.12 agora passam; teste adicional cobre duração chegando e desaparecendo, iniciando/parando o timer. Mudanças reais de estado continuam alcançando o widget sem indicador.

Bug sweep concluído sem defeito aberto no escopo inspecionado. Correções distribuídas na 0.13 experimental com mesma assinatura de atualização. Riscos residuais: sem teste físico Samsung/Spotify/Wear ou benchmark de bateria/FPS; cadência de 5 s continua sendo escolha experimental; falta de visibilidade exata do widget pelo host impede prometer atualização apenas quando ele estiver visível; padding negativo pode ser cortado pelo host e fallback Spotify não confirma reprodução.

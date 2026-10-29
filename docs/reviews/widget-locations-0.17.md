# Self-audit — identificação dos widgets, 0.17

Skill [breno-review-audit:self-audit-loop](skill://plugins_6adbdd91adac8191be37b31c2087947e/self-audit-loop/SKILL.md), autoavaliação sem agente independente. Base: cd0f92cbb3e9b37fecfb55b2e8e1753bbd832ebe (0.16). Escopo: distinguir instâncias por local, número e dimensões, usar indicação do host sem presumir tela inicial, permitir identificação manual por instância e preservar salvar/cancelar, rascunho e configurações existentes.

## Rodada 1 — achados antes das correções

- **Médio — WidgetAppearanceSection.applyChanges:** salvar somente a identificação também salva o visual herdado como um override. Isso prende a instância ao visual atual e interrompe a herança de alterações futuras do padrão, apesar de nenhuma mudança visual. Correção: salvar o visual da instância somente se ele diferir do valor atualmente carregado; manter os overrides já existentes. Verificar que a marcação manual não interrompe a herança.
- **Baixo — WidgetLocationStore.load:** consulta as mesmas preferências dentro do predicado de cada enum. Ler a string uma única vez antes de resolver o valor.
- **Baixo — recursos/teste visual:** remover import sem uso e alteração incidental na string de tamanho reportado; manter o diff restrito ao local das instâncias.
- **Baixo — WidgetConfigureActivity/status:** lint SetTextI18n rejeitou concatenação de duas linhas no TextView de identificação. Usar recurso formatado e reaproveitá-lo na lista de instâncias.

Nenhum bloqueante/alto identificado. A localização é um rótulo organizacional: não deve inferir LockStar pelo tamanho, alterar o host, adicionar permissões ou mudar renderização/mídia.

## Rodada 2 — identidade durante erro de validação

As correções da rodada 1 foram aplicadas. Os primeiros 13 casos específicos de localização/prévia passaram; lint detectou somente a concatenação agora convertida em recurso formatado.

- **Baixo — WidgetConfigureActivity/status:** ao mostrar erro de validação, a Activity substitui toda a legenda e perde o número/dimensões da instância em edição. Preservar a primeira linha de identificação também nos erros, com recurso formatado e teste.

Nenhum bloqueante/alto encontrado. Revisados armazenamento separado por ID, prioridade manual sobre indicação do host, ausência de inferência para categoria desconhecida, rascunho/recriação, isolamento, exclusão e encaminhamento da seleção para a Activity correta. Validar novamente após preservar a legenda nos erros.

## Rodada 3 — APROVADO

Revisão integral do diff, arquivos novos e callers. Nenhum achado aberto, bloqueante ou alto. O salvamento da identificação agora preserva a herança do padrão, a legenda de número/dimensões permanece mesmo após erro, recursos formatados passam no lint e o armazenamento não duplica leitura. Não houve alteração no renderer, geometria, mídia, Spotify ou Wear.

Validação final após as correções: `:app:assembleDebug testDebugUnitTest lintDebug --console=plain`, BUILD SUCCESSFUL; **373 testes** (app 342, wear 9, shared 22), zero falhas/erros/pulos. Lint de todos os módulos sem apontamentos e `git diff --check` aprovado. A cobertura específica verifica categorias ausentes/inválidas/combinadas, mudança da indicação do host, prioridade manual e retorno ao automático, rascunho/recriação, salvar/cancelar, isolamento, validação/remoção, seleção da instância correta, exclusão e preservação de herança/overrides. Prévia real Android/Skia do diálogo e editor foi exibida antes da publicação.

Bug sweep sem defeito aberto no escopo. Riscos residuais: a categoria é uma indicação fornecida pelo host e não prova a localização física; LockStar pode omitir ou enviar indicação incorreta. A escolha manual corrige a identificação, mas não move widgets nem muda a presença de Editar no host. Sem teste físico de Galaxy/LockStar/Spotify/Watch 8 ou benchmark. Mesmo applicationId/certificado e zero uses-permission.

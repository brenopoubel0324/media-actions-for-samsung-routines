# Revisão independente — widget de reprodução e volume

Base: HEAD b610b9a + diff/untracked em `/workspace/media-actions-review`, 14/11/2026. Leitura independente, sem execução de Gradle ou alteração de código. O plano foi lido integralmente e confrontado com provider/registry/renderer/layouts/editor/observer/listener/wallpaper/backup e chamadores de ações. Identidade ainda em fase de escolha; não foi tratada como rebranding implementado.

## Achados

1. **Médio — geometria do combinado com padding negativo em FILL.** `WidgetContentLayout.kt:21–24` retorna a geometria original em FILL; `VolumeWidgetLayout.kt:25–31` permite que padding vertical negativo aumente a altura calculada além do host. Exemplo: host 48×144 dp, `widgetPaddingVerticalDp=-24`, `fillWidth=true`: botão48, gap24, linha192. Com centralização, a linha começa em y=-24; os controles de volume extremos têm somente24dp dentro do retângulo físico do host. Isso viola a preservação de alvos48dp e limites físicos da seção7 do plano. **Correção concreta:** limitar a geometria do novo tipo ao tamanho físico, mantendo preferências e comportamento legado dos outros widgets; teste deve conferir bounds visíveis/globais em FILL vertical/horizontal, não só width/height do botão. Informado ao root e encaminhado ao agente de UI para correção.

2. **Médio — falha de entrega de um ID interrompe o lote novo de teardown.** `WidgetRegistry.updatePlayback` entrega os targets em um loop sem isolamento. Se a primeira chamada a `updateAppWidget` lança uma exceção na desconexão/destruição do listener, o catch externo de `stopPlayback` impede crash, mas os demais centros não recebem UNKNOWN. O observador já foi fechado; outros widgets podem continuar mostrando estado confirmado até outro evento. **Correção concreta:** tentar todos os targets, guardar o primeiro erro e relançá-lo ao final para preservar a semântica de falha; teste de teardown com falha no primeiro ID deve provar que o segundo centro é limpo. O loop legado do PresentationCache tem limitação parecida, mas a recomendação atual é focada no lote novo de teardown.

Não foram encontrados achados altos ou bloqueantes na revisão dos demais contratos.

## Contratos conferidos

- Tipagem do terceiro provider chega ao catálogo, editor, renderização, cache e wallpaper; nenhum ramo binário roteia combinado ao provider antigo.
- Volume executa um ajuste local e independe de acesso às notificações; somente o centro usa o dispatcher/fallback existentes e verificações frescas.
- Novos comandos validam componente/ID/provider/URI e usam PendingIntents imutáveis distintos. Exclusão cancela tokens e remove configurações.
- Um observador atende mídia e combinado; fallback sem espaço não é consumidor de playback. Revogação/destruição limpa estado, encerra pulso e recuperação não repete comandos.
- Snapshots de lotes são compartilhados, callbacks iguais deduplicados e cores usam cache do observador sem rescan próprio.
- Backup v5 representa três tipos, aceita1–4 sem regravação automática, rejeita tipo novo em versão antiga e aplicação entre tipos distintos; limites e escrita durável mantidos.
- Editor mantém rascunho, cancelamento e guardas de tipo, inclusive recriação; preview não executa comandos.

## Validação e limites

Os novos testes de provider, UI, backup e integração foram inspecionados. O root coordena execução focada e completa; esta revisão não declara seus resultados como próprios. A compatibilidade física com LockStar/One UI e políticas de áudio exige aparelhos. Redução de consultas não comprova economia de bateria. A identidade visual depende da escolha do usuário.

## Estado

**APROVADO na revisão de código após segunda leitura.** Os dois achados médios foram corrigidos; sem achados altos/bloqueantes ou médios remanescentes nesta fatia.

- Geometria: `WidgetContentLayout.calculate` recalcula apenas o combinado com padding vertical efetivo zero quando a linha calculada excede os limites físicos; configuração negativa continua salva, valores que cabem e widgets legados são preservados. O editor sinaliza o valor efetivo diferente. Teste nativo de UI acrescentado confere bounds relativos à raiz em H/V, FILL/COMPACT e autofill.
- Entrega em lote: `WidgetRegistry.updatePlayback` e `MediaWidgetProvider.PresentationCache.refresh` tentam todos os IDs e relançam o primeiro erro somente ao final. O cache só confirma entregas bem-sucedidas. Os testes novos cobrem teardown com primeiro ID falhando, entrega ao segundo tipo, cancelamento do pulso e repetição somente da entrega que falhou no próximo evento. Não há retry periódico ou replay de comando.

`git diff --check` passou na segunda leitura. A execução dos novos testes e da cadeia completa continua sob coordenação do root, sem passe de build declarado por esta revisão independente.

# Recuperação de gravação e atualização do editor de widgets — investigação 0.23

**Base remota inspecionada:** `main@1f09e3a2598372a7e829d25d25eed15431e67f0e` (phone 0.22).  
**Situação:** trabalho isolado em branch; não é entrega estável nem APK validado.
**Origem:** sintomas reais relatados no Galaxy S25 (sem acesso à instalação física).

## Escopo

- S1: salvar, fechar e reabrir com aparência anterior.
- S2: editor recupera valores, porém widget/LockStar não mostra alteração.
- S3: salvar não fecha e não informa claramente a falha.

Preservar `applicationId`, stores `widget_appearance[_id]`, `has_override`, localização independente, intents KEEP/INHERIT/CUSTOM, `EXTRA_APPWIDGET_ID`, provider, ações de mídia, Wear e ritmo periódico. Não migrar storage nem alterar recursos Spotify sem reprodução.

## Auditoria da base

1. `WidgetAppearanceSection.applyChanges` validava campos e retornava `Boolean`, atualizava stores separados e chamava refresh, porém tratava a ausência de exceção como conclusão.
2. `MediaWidgetProvider.refreshWidget` retornava em silêncio quando um widget deixava de existir; as falhas de `updateAppWidget` podiam alcançar o editor sem erro específico.
3. `WidgetConfigureActivity` convertia qualquer `false` em mensagem genérica de valores inválidos ou ID ausente.
4. `WidgetStyleStore.save` usa `SharedPreferences.apply()`: read-back imediato valida consistência **na memória**, não durabilidade em disco. A hipótese não está confirmada como causa de S1.
5. `WidgetRenderer` limita espaçamento e padding conforme tamanho fornecido pelo host. Isso pode explicar um valor não refletido visualmente sem que haja erro de gravação.
6. `updateAppWidget` não confirma que LockStar pintou a mudança. Não prometer confirmação visual por API.

## Implementação desta primeira rodada

- `refreshWidgetResult` retorna `REQUESTED`, `MISSING_WIDGET` ou `FAILED`. Não atribuir `REQUESTED` a sucesso visual.
- `applyChanges` verifica leitura lógica de aparência, modo de override e local após gravação; distingue falhas de aparência/local e falhas de refresh. Preserva rascunho para retry se não houver atualização bem-sucedida.
- `WidgetConfigureActivity` passa a mostrar a causa contextual do erro, mantendo `RESULT_CANCELED` quando houve falha.
- Testes de ID removido, aparência de outra instância e mensagem de validação.

**Lacuna intencional:** o armazenamento permanece baseado em `apply()`, e não é possível declarar a durabilidade em disco sem uma segunda implementação em worker. Como os sintomas não foram reproduzidos neste ambiente, não introduzir `commit()` síncrono na main thread nem uma migração não comprovada.

## Próxima etapa antes de publicar APK

1. Verificar o worktree da sessão Codex anterior; a API remota não oferece `git status` de workspaces não enviados.
2. Validar build, Robolectric (API 33/36) e lint; executar `git diff --check`.
3. Instrumentar, se necessário, IDs, `has_override` e aparência efetiva na ação salvar (somente dados locais; não registrar conteúdo Spotify).
4. Se a falha S1 persistir, analisar concorrência e durabilidade e introduzir fila serializada de gravações com retorno de `commit()` em worker; testes de falha parcial e recreação da Activity são obrigatórios.
5. Se S2 persistir com read-back correto, comparar `RemoteViews` em memória com as dimensões/condições reais do LockStar; coletar logcat e captura no S25.
6. Não declarar `APROVADO` sem cobertura de falha de storage, concorrência entre dois editores e validação no dispositivo.

## Matriz mínima

| Cenário | Resultado esperado | Onde |
|---|---|---|
| Editar widget A e reabrir A | aparência carregada corresponde ao save | Robolectric + S25 |
| Editar A, consultar B | B permanece inalterado | Robolectric + S25 |
| Restaurar padrão em A | `has_override=false`, localização preservada | Robolectric + S25 |
| Campo inválido | editor e rascunho preservados; mensagem de validação | Robolectric |
| Widget removido | refresh retorna MISSING_WIDGET | Robolectric |
| Update de `RemoteViews` falha | mensagem distinta do erro de gravação | harness com falha injetada |
| Alterar aparência enquanto música toca | callback não restaura estilo antigo | Robolectric + S25 |
| LockStar ignora mudança ou não oferece editar | documentação do limite, nunca sucesso simulado | S25 |

## Gate de publicação

**Candidato experimental:** testes, build e lint concluídos, hash e assinatura verificados.  
**Estável:** também exige observação de save/reopen/refresh no Galaxy S25, LockStar quando suportado, e upgrade sem perda de dados.

**Riscos residuais atuais:** `apply()` sem confirmação de disco; duas stores sem atomicidade; concorrência de dois editores; host sem ACK de pintura; ausência de testes físicos; mudanças locais de Codex não acessíveis desta conexão. Esses riscos são **abertos**, não corrigidos nesta rodada.

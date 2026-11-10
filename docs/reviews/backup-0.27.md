# Self-audit — backup e transferência, 0.27

Base 0e7650565b48f90037423fab72b1385c6a68bca5 (0.26). Escopo: pedido de exportação/importação para preservar ajustes ao migrar a instalação. Formato JSON versionado, padrão e intervalo globais, perfis portáveis de mídia/volume com aparência, herança e identificação de local. Widgets/posições e autorizações continuam sob controle do Android; rotinas/remapeador externos não fazem parte dos dados deste app.

## Rodada 1 — achados antes dos ajustes de robustez

- **Alto — SettingsBackupCodec/decode:** limite em bytes não impede recursão profunda do parser JSON de causar StackOverflow. Limitar profundidade estrutural antes de chamar o parser e testar string com delimitadores escapados, JSON muito profundo e arquivos inválidos antes de qualquer gravação.
- **Médio — BackupTransfer/exportação:** afirmar sucesso apenas ao fechar OutputStream não confere o arquivo que será usado para migração. Reabrir o documento e comparar o backup decodificado ao exportado; só confirmar exportação após essa verificação. Já ajustado nesta etapa, faltando regressão de arquivo divergente/falha de escrita/leitura.
- **Médio — importação/continuidade:** testar cancelamento, falha de leitura, confirmação pendente após rotação e retenção de transferência em andamento. A leitura nunca aplica ajustes. Confirmar importação é a única entrada de restauração, seguida de seleção explícita de widgets por tipo.
- **Médio — rollout Preview:** para exportar dados da antiga Preview, a atualização com backup precisa existir também para o mesmo package/certificado Preview. Gerar APK opcional de migração para quem já tem Preview instalada, sem torná-lo requisito para usuários do app original ou reinstalar outro aplicativo.

Validação inicial: 22 testes de codec/store em API 33/36 passaram. Nenhuma mudança nos comandos, listener de reprodução, protocolo Wear, agendamento ou permissões. Escritas entre diferentes SharedPreferences não são transação única: salvar perfis primeiro, verificar commits/readback, avisar gravação parcial ou falha de atualização e manter o arquivo original disponível para repetir.

## Rodada 2 — validação focada e revisão dos caminhos de migração

Codec/store + Activity passaram em API 33/36, incluindo cancelamento, leitura sem gravação, confirmação após rotação/recriação de processo, transferência retida em rotação e arquivo divergente na conferência. Guarda de profundidade antecede JSONTokener. Testes adicionais verificam erro de I/O e commit sem falso sucesso. Documentado que SharedPreferences múltiplas não são transação; perfis já persistidos e arquivo original permitem repetir em falha parcial.

Releitura de MainActivity, editor existente, stores e manifest. O editor existente mantém sua lógica de conflito/readback e escrita assíncrona habitual; apenas a restauração explícita utiliza commits duráveis fora da UI. Sem export de IDs, grant, rotinas ou conteúdo de mídia. Sem achado bloqueante/alto novo confirmado nesta rodada; falta suíte completa, lint, inspeção visual e conferência dos dois APKs de migração.

Lint da rodada 2 encontrou **médio — strings/backup_summary**: quantidade de perfis exige plural para o caso singular e traduções. Converter para recurso plurals/getQuantityString, sem suprimir a verificação. Os 47 testes focados passaram; o comando completo desta rodada falhou somente nesse lint. Corrigir antes da validação final.


## Rodada 3 — APROVADO para entrega experimental

Releitura completa de codec/store, transferência retida, Activity, stores existentes, chamadas de UI/atualização e manifest; simplificação restrita ao necessário para compartilhar a escrita de aparência sem alterar os salvamentos existentes. O alto da rodada 1 (profundidade do parser) e os médios foram corrigidos. Recursos plurais passaram no lint após o ajuste. Bug sweep sem novo defeito confirmado; nenhum bloqueante/alto/médio/baixo de código pendente. Revisão do próprio agente, sem subagentes, em três rodadas.

**573 testes Debug** (app 536, Wear 15, shared 22) e **48 testes Preview** de codec/store/transferência/isolamento: zero falhas/erros/pulos. Build Debug original/Preview/Wear, lint Debug dos três módulos e git diff --check passaram após a última mudança. Casos incluem persistência sem widgets, seleção do tipo/destino, herança/cópia, leitura/cancelamento sem gravação, restauração de confirmação por Bundle, transferência em rotação, erros de I/O/commit, arquivo divergente, profundidade e UTF-8 inválidos. Testes de segundo plano e regressões dos widgets existentes também passaram sem mudança de orçamento.

Imagens Android/Skia clara, confirmação e escura/fonte ampliada exibidas antes da publicação. Distribuição principal: original 0.27/code27, mesmo package/certificado histórico. APK Preview opcional 0.27 somente para atualizar uma instalação Preview já existente e exportar dela; package separado e mesmo certificado de desenvolvimento verificados no empacotamento. Wear permanece 0.9.

Riscos/limites documentados: gravação entre stores não é transação global (falhas parciais são informadas e o arquivo de origem deve ser mantido); posição/tamanho/instâncias/permissões/rotinas/bindings de outros apps não são transferidos. Encerramento de processo durante I/O pode exigir repetição. Migração real entre instalações, assinatura de produção e provedores Samsung/LockStar em aparelho físico não foram testados (adb sem dispositivos). Não afirmar migração física validada nem ganho de bateria. [Guia de migração e contrato](../BACKUP.md).

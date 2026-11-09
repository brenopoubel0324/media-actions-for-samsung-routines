# Self-audit — widget de volume 0.24

Base c3bd157 (original 0.23); pedido: Tab S10+, volume local por clique. Skill self-audit-loop, sem subagentes. Escopo: dois botões −/+, provider privado, geometria com dois alvos, editor compartilhado e roteamento por tipo. Sem alteração dos comandos Samsung/Wear nem novos timers.

## Rodada 1 — achados antes da validação completa

- **Médio — atualização global:** uma primeira versão de refreshAll do volume ignorava FAILED retornado pela atualização da instância. Propagar a falha para o editor de padrões manter o rascunho e mostrar erro, como no caminho de mídia. Corrigido antes da próxima rodada.
- **Baixo — editor:** ocultar opções de progresso, família de playback e borda apenas central no volume; prévia estática sem comandos, título/interação próprios. Permitir reconhecimento dos dois tipos na seleção/diagnóstico, mantendo validação exclusiva de cada receiver de comando.

Geometria conserva comportamento de três botões por padrão e limita dois alvos de 48dp no volume. Comandos de volume usam uma chamada adjustStreamVolume STREAM_MUSIC por clique, com flags 0; respeitam a política do sistema/saída fixa e não fazem retries. MODIFY_AUDIO_SETTINGS é permissão normal, sem diálogo runtime, para áudio local; Wear continua sem uses-permission. Fontes oficiais: AudioManager.adjustStreamVolume e AudioService Android 16 (android.googlesource.com/platform/frameworks/base). Compatibilidade física Tab S10+/Bluetooth/LockStar e energia permanecem não testadas.

## Rodada 2 — testes focados e ajustes

O primeiro teste detectou que o ShadowAudioManager 4.17 altera todos os streams no helper interno de ajuste. O fixture foi corrigido para modelar o stream recebido; asserções conferem também chamada, flags, direção e ausência de outras ações. Isso não é teste do AudioService físico.

**Médio — ID reatribuído a outro tipo:** validar somente que o ID pertence ao app permite salvar um rascunho de volume em uma instância de mídia se o host mudar o provider. Validar também o tipo capturado pelo editor. Corrigido e coberto antes da validação final. Acrescentado teste de falha global do host e retry.

## Rodada 3 — APROVADO para entrega experimental

Sem bloqueante/alto/médio pendente no escopo implementado. Corrigidos refresh global com erro silenciado e validação do tipo de provider no editor. Cópia da prévia e resumos do editor ajustados para volume; a comparação visual foi alinhada e mostrada antes da publicação. Removidos imports sem uso.

Validação: **497 testes Debug** (app 460, Wear 15, shared 22), build phone/Wear e lint dos três módulos aprovados; **20 testes Preview** de volume e isolamento em API 33/36 também passaram, com assemblePreview. Zero falhas/erros/pulos. `git diff --check` aprovado. Casos de volume verificam roteamento STREAM_MUSIC, um comando por clique, flags 0, limites do modelo, recusa/fixo sem retry, ID removido/URI incorreta/outro provider, rascunho/recriação/herança, atualização global com falha/retry e comandos de mídia sem redraw do volume.

Imagens Android/Skia nativas do widget e editor foram mostradas. O APK original continua dev.local.mediaactions, versionCode 24, com certificado histórico conferido no empacotamento. A única permissão solicitada no phone é MODIFY_AUDIO_SETTINGS; não depende de autorização de notificações para controlar volume. Nenhum novo timer/coletor ou mudança de binding Wear.

Bug sweep sem defeito novo confirmado no escopo. Tab S10+, rotas Bluetooth, DND, limites reais, host Samsung/LockStar e CPU/bateria físicos seguem NÃO TESTADO/NÃO MEDIDO. Doubles de AudioManager conferem despacho/modelo, não garantem execução da política Samsung. Sem alegação de economia de bateria. Revisão do próprio agente, três rodadas.

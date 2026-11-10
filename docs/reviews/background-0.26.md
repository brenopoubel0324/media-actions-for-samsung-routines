# Revisão de segundo plano — 0.26

Base 43dcd29/0.25; pedido de 10/11/2026. Skill self-audit-loop, sem subagentes. Escopo: apresentação do widget/listener, consultas, deduplicação e recuperação. Comandos, seleção de destino no Wear e fallback Spotify continuam verificando estado/autorização atuais.

## Rodada 1 — achados antes das correções

- **Médio — WidgetPlaybackObserver/ciclo:** três seleções e duas leituras de metadados por tick, mesmo para apresentar a mesma sessão. Alimentar cache efêmero de apresentação pelos callbacks, projetar posição pelo relógio monotônico, validar autorização no ciclo e compartilhar snapshot com renderização/agendamento.
- **Médio — eventos de mesmo estado:** callbacks repetidos em pausa redesenham widgets com indicador configurado, embora o indicador esteja oculto. Deduplicar por dados visuais, estilo/tamanho e progresso quantizado de apresentação; gravar chave somente depois de updateAppWidget bem-sucedido.
- **Médio — sem widgets:** registrar callbacks e consultar sessões sem consumidor de apresentação. Manter somente receptor de configuração para ativar observação quando necessário; sem polling de descoberta.
- **Requisito de confiabilidade:** nunca usar cache de apresentação para autorizar ações. Revogação/desconexão/destruição invalidam apresentação; nenhuma tarefa/callback antigo pode reativar observador fechado. Ressincronizar em início, retorno de tela e mudança de sessões/destino quando necessário.

Linha de base em testes API 33/36, mesmo fixture de comparação: seis widgets/5s/60s = 36 consultas de sessão, 24 de metadados e 72 atualizações. Dispositivo físico indisponível (adb sem dispositivos); comparação física/bateria permanece NÃO MEDIDO. Não converter contagens simuladas em economia de energia.

## Rodada 2 — revisão da implementação (achados antes dos ajustes)

- **Médio — WidgetPlaybackObserver/watch:** o callback já traz PlaybackState/metadados, mas a implementação inicial relia o controller após cada evento. Aproveitar os campos necessários do evento e guardar somente estado/posição/tempo/velocidade/duração; manter leitura atual no resync e nos comandos.
- **Médio — configurações/entrega externa:** a chave visual do observer pode ficar defasada depois de um refresh explícito do provider, ou deixar de invalidar um widget cujo indicador foi desligado. Invalidar as chaves na notificação de configuração e permitir entrega a todos os alvos nesse evento, mantendo deduplicação em callbacks/ticks.
- **Baixo — comparabilidade do fixture:** as várias janelas de 60s compartilhavam timestamp e acabavam atingindo o fim da faixa; isso mede saturação em vez de avanço estável. Reiniciar timestamp antes de cada janela em ambos os códigos e recapturar a base num worktree de 43dcd29. Captura concluída: mesmo avanço em todas as janelas, nenhuma produção modificada na base.

- **Médio — WidgetPlaybackObserver/falhas transitórias:** descartar inscrições em qualquer RuntimeException remove a fonte de eventos de recuperação; preservar as inscrições em falhas transitórias, invalidar somente os dados e refazer snapshot no próximo evento. Revogação/SecurityException continua descartando as inscrições. Falha de entrega deve permanecer pendente mesmo sem mudança de estado/indicador.

Sem achados bloqueantes/altos nesta rodada. Correções acima e testes de recuperação antes do veredito final. Não há aparelho adb conectado, então recuperação e economia física continuam pendentes de validação em hardware.

## Rodada 3 — integração com refresh explícito (achado antes do ajuste)

- **Médio — MediaWidgetProvider/refreshAll:** ações da Activity/companion e retorno à tela do app também entregam snapshots atuais fora do observer. Sem sinal de invalidação, a chave/cache antigo pode impedir a atualização seguinte de um widget simples, ou reaplicar estado defasado após edição. Sinalizar resync de sessão somente nas entregas explícitas que capturam estado atual (refreshAll/refreshWidget/resize), além de invalidar as chaves. Configuração de intervalo sozinha não exige enumerar sessões; teardown UNKNOWN não pode reacender a observação. Acrescentar regressão de estado alterado antes de chegar o callback.


### Veredito final — APROVADO para entrega experimental

Releitura completa do observer/provider/renderer, entradas Activity/PhoneWearService/editor, teardown do listener e caminhos de comando; simplificação restrita ao escopo. O achado da rodada 3 foi corrigido e sua regressão passou. Bug sweep sem outro defeito confirmado; zero bloqueantes/altos encontrados e nenhum médio/baixo aberto no código deste escopo. Revisão do próprio agente em três rodadas, não revisão independente.

**526 testes Debug** (app 489, Wear 15, shared 22) e **22 testes Preview** de recuperação/isolamento passaram em API 33/36; zero falhas, erros ou pulos. Build Debug phone/Wear, assemblePreview, lint Debug dos três módulos e git diff --check passaram após a última alteração. O APK distribuído é somente o original phone 0.26/code26; Preview não é necessária. Conferência de package/certificado histórico no empacotamento. Wear permanece 0.9, sem mudança de protocolo.

Orçamentos controlados confirmados nas duas APIs: 6 widgets/5s/60s, sessões 36→0 e metadados 24→0, conservando 72 entregas com progresso diferente; dez eventos idênticos de pausa, sessões 20→0 e entregas 60→0; sem widget, consultas por dez callbacks 10→0. Inicialização/resync ainda consultam o sistema, e cada refresh/tick verifica autorização. Comandos confirmam estado/autorização atuais e não recebem targets do cache visual. Handler de uptime permanece sem mecanismo de wakeup; o avanço termina ao atingir limite e só eventos reativam um observador sem progresso.

**Follow-up físico obrigatório, fora da validação concluída:** comparação antes/depois no mesmo Galaxy/Tab, recuperação real Samsung, CPU/wakeups e bateria/energia NÃO MEDIDOS. adb sem dispositivos. Não declarar economia de bateria nem garantia de rebind após Forçar parada. Há [protocolo e campos pendentes](../medicoes/energia-0.26/protocolo-dispositivo.md) e [dados/limites da comparação simulada](../medicoes/energia-0.26/README.md). Risco residual: eventos e rebind dependem do Android/One UI/host/player; o cache volta a consultar ao reconectar, ao acender a tela e nas atualizações explícitas. Nenhum polling extra foi criado para mascarar falta de evento.

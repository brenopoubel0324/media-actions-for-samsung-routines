# Próximas medidas após a review 0.18

Status: plano de execução proposto, revisado contra o código atual. Base: phone 0.18, código 18; Wear 0.8, código 8; commit `dd2a0367a29790a337dab42cf4fc9bd043feef84`. A validação anterior teve 394 testes aprovados; nenhum teste físico é considerado concluído neste plano.

Objetivo: priorizar o consumo energético do celular e do relógio, preservando a confiabilidade dos controles. Medir o custo atual, permitir escolher a cadência do progresso e reduzir trabalho repetido antes de ampliar as configurações visuais. A implementação será dividida em entregas pequenas, com imagens antes de publicar mudanças visuais e review ao final de cada entrega.

## Ordem e dependências

| Etapa | Entrega | Critério de saída |
| --- | --- | --- |
| 1 — prioridade energética | Linha de base de consumo e conferência das proteções existentes | Contagens verificadas e protocolo físico preparado; ganho de bateria ainda não presumido |
| 2 — progresso | Intervalo global de 5, 15 ou 30 segundos | Escolha explícita, um único tick pendente e interrupção quando os requisitos deixam de valer |
| 3 — eficiência | Reduzir consultas repetidas se houver benefício demonstrado | Comparação antes/depois, sem regressão de estado, destino ou envio de comandos |
| 4 — diagnóstico | Horário, motivos claros e roteiro de teste | Falhas identificáveis com coleta sob demanda e sem monitoramento contínuo novo |
| 5 — validação física | Energia, Spotify, LockStar e WBR no S25/Watch 8 | Resultado por cenário, versões exatas, condições de medição e limitações registradas |
| 6 — UX | Voltar a usar o visual padrão por widget | Restaurar afeta somente a instância escolhida e preserva sua identificação |

Começar pelas contagens e pela linha de base da 0.18 em 5 s, comparada com indicador desligado. Comparar 15/30 s depois de implementar a etapa 2, evitando depender de uma opção ainda inexistente. Só otimizar a etapa 3 com evidência de trabalho redundante ou custo relevante. Preparar diagnóstico e roteiro físico em paralelo às medidas energéticas, sem esperar a etapa 5 para registrar condições de teste.

Sem aparelho disponível, avançar nos testes de agendamento, contagens e configuração; manter energia/bateria como NÃO MEDIDO. Não bloquear a opção de intervalo por falta de medição física, preservando o padrão de 5 s e sem anunciar economia comprovada. A validação física é necessária para concluir ganhos de energia. A UX permanece no plano, após as entregas energéticas prioritárias; diagnóstico e UX independentes podem avançar com a etapa 5 ainda pendente. Falha funcional relevante abre correção específica antes de ampliar o comportamento afetado.

## Contratos que devem permanecer

- Manter applicationId `dev.local.mediaactions`, certificado e atualização sobre o app instalado; sem desinstalação para migrar preferências.
- Preservar chaves existentes de aparência, `has_override`, identificação por ID e o estado temporário de original/rascunho da 0.18. Identificação do local continua separada da aparência.
- Manter a chave de progresso por widget: ligado/desligado independentemente das bordas. Indicador somente durante PLAYING; sem sessão, sem borda decorativa dos botões. Padding negativo permanece apenas vertical.
- Manter um comando por invocação, sem retry após envio incerto, sem mudar silenciosamente de aparelho/player e sem broadcast global de teclas.
- Preservar URIs dos atalhos, três componentes launcher Wear, protocolo `/mediaactions/v1/`, prazo/leases e deduplicação. Nenhuma tela de player, widget ou quarta entrada na home do relógio.
- Não registrar título, artista, capa, conteúdo de notificações, tokens de sessão ou IDs de nós nos diagnósticos compartilháveis. Sem servidor ou telemetria nova.
- Não prometer restauração das teclas Samsung pelo app nem presença de Editar no menu do LockStar: são comportamentos controlados pelo host/sistema.

## Consumo energético como requisito de todas as entregas

A preocupação energética é um critério de aceite de todas as entregas, incluindo diagnóstico e Wear. Os controles devem reagir a eventos e invocações; instrumentação e novas telas não justificam polling permanente.

- Conservar a pausa dos ticks com mídia pausada/sem sessão, tela não interativa, posição/duração indisponíveis ou nenhum widget com progresso ativado. Isso já existe na 0.18; é uma proteção a verificar, não uma economia nova já medida.
- Manter um único agendamento periódico do observador, com no máximo um tick futuro pendente. Trocar o intervalo, reiniciar o observer ou retornar de tela apagada não pode acumular timers nem reproduzir ticks atrasados em rajada.
- Atualizar imediatamente o estado real quando houver callback. A ausência de ticks periódicos não impede atualizações pontuais necessárias para corrigir Play/Pause ou ocultar o indicador.
- Preservar atualização somente dos widgets com indicador em ticks de progresso. Não redesenhar widgets simples nem regenerar suas imagens por causa da passagem do tempo.
- Não introduzir wake lock, alarme, serviço permanente ou varredura contínua para descobrir se o widget está visível. Tela interativa não comprova visibilidade; medir também o cenário com outro app aberto.
- No Wear, continuar com consulta e comando limitados à invocação, cleanup e prazos existentes. Sem sincronização periódica nova com o celular, fila para reconexão ou retry de comando incerto.
- Coletar diagnóstico sob demanda ou na conclusão de uma tentativa. Contadores/perfilamento devem ser temporários, em teste/debug ou habilitados explicitamente para a medição, sem coletor de alta frequência ou logs detalhados permanentes na versão distribuída.
- Manter a escolha de desligar o indicador e preservar os 5 s atuais até uma escolha explícita do usuário. Recomendar um perfil energético somente com evidência suficiente; não mudar o padrão silenciosamente.

A economia procurada é o custo adicional dos controles/widget/ponte, separado do consumo de tocar música. Queda de bateria do aparelho inteiro e número de ticks são dados distintos; nenhum deles, sozinho, comprova a energia atribuível ao app.

## Etapa 1 — medir a linha de base e verificar as proteções

Usar primeiro a 0.18 com indicador desligado e com 5 s. Preparar a mesma comparação em 15/30 s para depois da etapa 2. Cobrir 1, 3 e 6 widgets, incluindo mistura de widgets com/sem progresso, e registrar a configuração exata. Um controle sem widgets pode ajudar a separar custo do observador do custo de renderização.

Separar os testes de relógio simulado, que verificam lógica/contagens, da medição no aparelho, que pode observar CPU/energia. CPU ou tempo de um teste Robolectric não servem como medição de consumo do Galaxy/Watch.

| Medida | Coleta e critério |
| --- | --- |
| Ticks periódicos | Contar separadamente de callbacks; zero quando os requisitos não forem satisfeitos |
| Consultas e RemoteViews | Contar leituras de sessão/metadados e atualizações por lote/instância; widget simples não deve receber ticks de progresso |
| CPU e alocações | Comparar no aparelho/perfilador quando disponível, com mesma carga de áudio e condições registradas |
| Energia/bateria | Usar medição do sistema quando permitir atribuição; porcentagem total de bateria sem isolamento não comprova consumo do app |
| Wear/ponte | Registrar atividade de CPU/comunicação em repouso e em uma sequência igual de gestos; nenhuma consulta periódica nova fora das invocações |

Na comparação física, manter aparelho, player, áudio, rota Bluetooth/alto-falante, rede, brilho, tela, duração, quantidade de widgets e estado de carregamento/temperatura comparáveis. Repetir cada comparação pelo menos três vezes, alternando a ordem das configurações. Registrar ferramenta, unidade, duração e variação entre repetições; ampliar a coleta ou marcar INCONCLUSIVO se a resolução/variação não permitir distinguir os resultados.

Incluir reprodução com widget visível, outro app em primeiro plano, pausa, tela apagada/AOD, ausência de duração e indicador desligado. Não atribuir aos nossos controles a energia do player, da tela ou do transporte de áudio. Sem isolamento suficiente, relatar apenas as métricas observadas, sem percentual de economia de bateria.

**Aceite:** contagens e condições de elegibilidade documentadas; protocolo reproduzível; resultados por cenário como MEDIDO, NÃO MEDIDO ou INCONCLUSIVO. A medição física permanece pendente enquanto não houver aparelho e dados suficientes. Criar regressões para o orçamento de ticks antes de alterar o agendamento; comparar novamente após as etapas 2 e 3.

**Pontos principais:** `WidgetPlaybackObserver`, `MediaWidgetProvider.refreshAll`, leituras do player e execução por invocação de `WearActionReceiver`; instrumentação de teste/debug sem dados de mídia.

## Etapa 2 — escolher o intervalo do progresso

**Decisão proposta:** intervalo global com opções **5, 15 e 30 segundos**. Cada widget continua podendo ligar/desligar seu próprio indicador. Um único agendador evita timers concorrentes por instância. Manter **5 s como valor inicial e fallback**, preservando o comportamento atual; apresentar a cadência de cada opção sem prometer economia; uma recomendação de perfil depende da comparação da etapa 1.

Criar um armazenamento global separado da aparência, por exemplo `widget_progress_settings/interval_seconds`, aceitando somente 5/15/30. Converter segundos para milissegundos apenas no agendador. Preferência ausente/inválida retorna 5 s; não reescrever campos de `WidgetAppearance`, `has_override` ou `progressRing`.

Na seção Seu widget da tela inicial, oferecer **Atualização do progresso**, abrindo um seletor nativo com Salvar/Cancelar e aviso de que vale para todos os widgets com indicador. O controle por instância **Mostrar progresso da faixa** continua no editor; não apresentar um intervalo global como se fosse propriedade exclusiva daquela instância.

Tela interativa não significa widget visível: com outro app aberto, o telefone ainda pode satisfazer essa condição. Esta etapa não promete detectar a visibilidade do LockStar. Registrar esse caso na medição; se surgir um mecanismo suportado de visibilidade, avaliá-lo separadamente antes de usá-lo como critério de agendamento.

Em `WidgetPlaybackObserver.updatePulse`, ler o intervalo validado. Salvar uma mudança deve cancelar o tick antigo e recalcular o próximo, sem acumular callbacks. Manter os requisitos existentes: PLAYING, tela interativa, duração/posição calculáveis e ao menos um widget com progresso ativo. Callback real de play/pause/seek/metadados continua imediato, independentemente do intervalo.

Cadência nominal enquanto elegível: 5/15/30 s correspondem a aproximadamente 12/4/2 ticks por minuto, sem contar callbacks. Isso não é uma estimativa de economia de bateria; o sistema pode atrasar callbacks.

**Aceite:** 5/15/30 s produzem a cadência escolhida no relógio simulado; trocar durante reprodução deixa um único tick; pausa/tela apagada/AOD/opção desligada/duração perdida interrompem ticks; retorno das condições reativa o agendamento; atualizações só de progresso não redesenham widgets simples. Cancelar ou recriar a tela não altera o intervalo gravado; recriar preserva a seleção ainda não salva. Salvar aparência ou local não reseta o intervalo global. Não adicionar wake lock, alarme ou serviço permanente.

**Arquivos principais:** novo store global pequeno, `WidgetPlaybackObserver.kt`, `MainActivity.kt`, strings e testes em `WidgetProgressObserverTest`/`WidgetProgressReviewTest`; preservar `MediaWidgetProvider.refreshAll(progressOnly)`.

## Etapa 3 — reduzir trabalho repetido com evidência

Se consultas repetidas tiverem custo relevante, compartilhar um snapshot efêmero e coerente de sessão/estado/posição dentro de uma chamada de `MediaWidgetProvider.refreshAll`, com leitura de progresso só quando necessária. Ler posição e estado do mesmo player. Não persistir cache, reaproveitar snapshot entre comandos nem deixar o despacho de mídia depender desse cache.

**Aceite:** comparar com a linha de base da etapa 1 e demonstrar redução de consultas/custo no cenário medido, mantendo imagens/estados, atualização de widgets simples apenas quando necessária e limites dos bitmaps. Troca de sessão e revogação de acesso não podem usar dados antigos em ações. Se não houver ganho relevante, documentar a medição e manter a implementação atual.

Reexecutar as contagens e comparações energéticas após a mudança, incluindo widgets simples e o Wear em repouso. Diminuição de consultas pode ser comprovada por teste; afirmar redução de consumo energético exige a medição física correspondente. Ganho dentro da variação da coleta permanece INCONCLUSIVO. Sem benefício demonstrado, manter a implementação atual e registrar a evidência.

## Etapa 4 — diagnóstico útil antes de mudar a integração

**Spotify no celular:** em `SpotifyPlaybackResumer.writeStatus`, gravar o status terminal e o horário de conclusão na mesma edição das preferências `spotify_resume`. Manter `status` compatível e acrescentar uma chave opcional `completed_at_epoch_ms`. Usar relógio de calendário para exibir horário; `elapsedRealtime` continua exclusivo dos prazos. Formatar conforme idioma e fuso do aparelho, sem fixar UTC ou um fuso brasileiro.

Na Ajuda/diagnóstico da `MainActivity`, mostrar **Última tentativa concluída**, resultado e horário. Para registros antigos sem horário, informar que ele não está disponível. CONNECTING não vira resultado persistente; BUSY de uma chamada concorrente não substitui a tentativa ativa. Manter a proteção da 0.18 para a resposta antiga não sobrescrever o resultado do toque atual.

Adicionar **Copiar diagnóstico** com versão do app, Android, último status/horário e informações técnicas da instância escolhida, quando pertinente: ID, categoria informada e dimensões. A cópia é local e iniciada pelo usuário. One UI, LockStar e WBR devem ser anotados pela versão efetivamente instalada, sem depender de API privada. Não copiar pacotes de players arbitrários ou dados de mídia.

**Wear:** mapear os resultados existentes de `LinkResult` para mensagens breves de acesso negado, ausência de sessão, conexão indisponível, ocupado e comando sem confirmação. Preservar o contexto do destino internamente em `WearActionReceiver` quando houver destino definido, sem mudar o payload. Se os probes não permitirem escolher um destino, não inventar qual aparelho falhou. SENT significa comando enviado; UNCONFIRMED não autoriza retry nem troca de aparelho.

**Aceite:** registros antigos continuam legíveis; horário só acompanha a conclusão correspondente; mudança de fuso não altera timeouts; duplicações não repetem comando; resultados tardios não mudam o toque atual. Testar os mapeamentos Wear e continuar encontrando exatamente três entradas launcher. Atualizar o APK Wear somente se seu código mudar.

**Arquivos principais:** `SpotifyPlaybackResumer.kt`, `MainActivity.kt`, `WearActionReceiver.kt`, strings pt-BR/inglês e testes próximos em `SpotifyBrowserResumeTest`, `AppAuditRegressionTest` e `WearLauncherTest`.

**Aceite energético:** atualizar a tela ao abrir/retomar, por resultado de comando ou atualização manual. Gravar status/horário terminal sem criar timer de diagnóstico ou consulta recorrente ao relógio. A ajuda de diagnóstico não deve criar atividade contínua quando a tela do app não está em uso.

## Etapa 5 — fechar a evidência no aparelho

Preparar uma ficha por cenário com: versão/commit dos APKs, Android, One UI, LockStar/WBR, acesso à mídia, estado inicial, origem da ação, resultado esperado/observado, diagnóstico e evidência disponível. O teste no aparelho será executado pelo usuário ou numa sessão com dispositivo conectado; hoje essa dependência permanece aberta.

| Grupo | Cenários mínimos | Resultado a verificar |
| --- | --- | --- |
| Spotify | Sessão pausada; nenhuma sessão elegível com Spotify instalado/logado e fila anterior; outro player tocando; acesso revogado | Play/Pause acompanha estado real; fallback só no caso previsto; nenhum controle do player errado |
| Origem da ação | App, widget e atalho/Routines; repetir com outro app em primeiro plano | Uma chamada por gesto; não abrir o player para disfarçar falha de retomada |
| Processo | Retomada por atalho sob pressão/morte do processo; Spotify forçado a parar como caso separado | Registrar sobrevivência/limitação sem confundir force-stop com ausência normal de sessão; não repetir comando após reinício |
| LockStar | Um widget na tela inicial e outro no bloqueio; seleção externa de Editar quando oferecida; informação automática e marcação manual | ID correto no editor, configurações isoladas, local desconhecido não vira tela inicial por suposição |
| Aparência | Bordas independentes, centro/progresso, tamanhos e padding vertical negativo em hosts de dimensões diferentes | Sem alteração de outros widgets; registrar corte/margens impostos pelo host |
| Progresso/energia | Play, pausa, sem sessão, duração desconhecida, tela apagada/AOD, opção desligada, outro app aberto e 5/15/30 s quando disponíveis | Estado correto; orçamento de ticks respeitado; comparação com a linha de base, sem inferir visibilidade pela tela acesa |
| Watch 8/WBR | Três bindings; só celular tocando, só relógio, ambos, ambos pausados e desconexão | Política de destino preservada; desconhecido não é pausa; sem fila para reconexão e sem execução duplicada |

No teste de retomada, playback confirmado é o aceite funcional. Se o Spotify rejeitar a conexão, registrar a falha explícita e investigar a causa; diagnóstico correto sozinho não comprova reprodução. Se o menu Editar não aparecer, registrar o comportamento do host e conferir o manifesto antes de concluir incompatibilidade. A marcação manual não comprova detecção automática.

O “ajuste adicional da lockscreen” citado pelo usuário ainda não foi especificado. O roteiro deve esclarecer o comportamento desejado com base no caso observado; não assumir que se trata de padding, edição externa ou outro visual. Essa pendência não bloqueia as medidas energéticas, o diagnóstico nem a restauração do visual padrão.

**Saída:** relatório funcional com PASSOU/FALHOU/NÃO TESTADO por cenário e relatório energético com MEDIDO/NÃO MEDIDO/INCONCLUSIVO, conforme a etapa 1. Não anunciar compatibilidade física de itens sem evidência. Falhas específicas viram tarefas com reprodução e aceite próprios; não reescrever a integração inteira por hipótese.

## Etapa 6 — voltar ao visual padrão de uma instância

No editor de um widget instalado, oferecer **Usar visual padrão** e indicar se ele segue o padrão ou usa visual próprio. O padrão é o definido pelo usuário no app, não uma restauração de fábrica.

A ação prepara a mudança no rascunho e atualiza a prévia; nada é persistido até **Salvar alterações**. Cancelar preserva o override anterior. Ao salvar a herança, validar novamente que o widget pertence ao provedor e usar `WidgetStyleStore.remove(context, id)` somente para a aparência da instância. Não remover `WidgetLocationStore`, padrões globais ou preferências de outros widgets.

Guardar uma intenção temporária de herança separada do modelo `WidgetAppearance`, preservada em recriação. Ao seguir o padrão, textos inválidos do visual customizado descartado não bloqueiam Salvar. Editar novamente uma opção visual cancela a intenção de herança e cria um rascunho próprio a partir do padrão. Não adicionar o comando de restaurar instância à tela global de Visual padrão.

Se o padrão mudar durante a edição, o Save em modo de herança deve continuar usando o padrão atual, sem gravar um snapshot antigo como override. Ao retomar o editor, atualizar a prévia herdada. Preservar a comparação original/rascunho da 0.18 para edição customizada e edição somente de local.

**Aceite:** após restaurar/salvar, mudar o padrão altera essa instância; outros widgets customizados permanecem iguais; local manual permanece; Cancelar/recriar não apagam preferências; widget removido não é salvo; editar um campo após restaurar volta ao modo customizado. Testar também mudança externa de padrão/override e rascunho inválido anterior à restauração.

**Arquivos principais:** `WidgetAppearanceSection.kt`, `WidgetConfigureActivity.kt`, `WidgetAppearanceState.kt` se necessário ao estado temporário e `WidgetStyleStore` em `WidgetAppearance.kt`; regressões `WidgetLocationTest`, `WidgetConfigurationTest` e `AppAuditRegressionTest`.

## Validação, publicação e reversão por entrega

Para código, rodar regressões proporcionais em API 33/36, build/lint dos módulos afetados e `git diff --check`. Nas entregas completas, usar a suíte documentada:

```sh
./gradlew :app:assembleDebug :wear:assembleDebug testDebugUnitTest lintDebug --console=plain
```

Os 394 testes são a base histórica, não uma contagem futura fixa. Acrescentar casos do comportamento novo e preservar as regressões da 0.18. Para UI, gerar e mostrar imagens reais antes da publicação: pt-BR, claro/escuro, janela estreita, fonte ampliada, teclado, salvar/cancelar e acesso externo ao editor.

Cada entrega deve terminar com a skill de self-audit, correções dos achados, README/relatório atualizados, commit no GitHub, versão de APK crescente quando houver código, certificado/applicationId conferidos e links públicos/checksums verificados. Mudança somente documental não exige APK/tag nova. O fluxo atual usa APKs em `downloads/vX-experimental` e release com links; não depender do upload de anexos que falhou no ambiente.

Publicar separadamente intervalo, otimizações comprovadas, diagnóstico e restauração. Cada entrega deve informar o resultado das contagens e se houve medição física de energia; se ainda não houve, manter NÃO MEDIDO e não anunciar autonomia maior. Wear só recebe APK novo se alterado, mantendo interoperabilidade com o protocolo v1. Teste físico pendente deve continuar explícito, sem impedir a publicação de mudanças independentes validadas.

Reversão: descartar mudanças não salvas com Cancelar; manter opção de desligar progresso e voltar a 5 s. Se uma versão introduzir regressão, publicar correção com código de versão maior, preservando preferências; não depender de downgrade/desinstalação. Restaurar o visual padrão após Salvar é uma alteração intencional daquela instância: não prometer recuperar automaticamente o visual customizado anterior.

Fora de escopo: ajuste automático de frequência sem evidência de visibilidade, monitoramento energético permanente, novas famílias de ícones, redesign adicional amplo, SDK/login Spotify, fallback Wear para iniciar Spotify sem ofertas de sessão, captura global de teclas e alteração das políticas Samsung. Esses itens exigem plano próprio se forem solicitados.

[Review deste plano](../reviews/plano-proximas-medidas-pos-0.18.md) · [Review que originou as prioridades](../reviews/app-audit-0.18.md)

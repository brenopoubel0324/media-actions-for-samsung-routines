# Próximas medidas após a review 0.18

Status: plano de execução proposto, revisado contra o código atual. Base: phone 0.18, código 18; Wear 0.8, código 8; commit `dd2a0367a29790a337dab42cf4fc9bd043feef84`. A validação anterior teve 394 testes aprovados; nenhum teste físico é considerado concluído neste plano.

Objetivo: aumentar a confiabilidade no aparelho, simplificar a configuração de cada widget e permitir escolher o custo das atualizações de progresso. A implementação será dividida em entregas pequenas, com imagens antes de publicar mudanças visuais e review ao final de cada entrega.

## Ordem e dependências

| Etapa | Entrega | Critério de saída |
| --- | --- | --- |
| 1 — prioridade imediata | Diagnóstico com horário, motivos claros e roteiro de teste | Falhas podem ser identificadas sem confundir envio com reprodução confirmada |
| 2 — validação física | Spotify, LockStar e WBR no S25/Watch 8 | Resultado por cenário, versões exatas e limitações reproduzidas |
| 3 — UX | Voltar a usar o visual padrão por widget | Restaurar afeta somente a instância escolhida e preserva sua identificação |
| 4 — progresso | Intervalo global de 5, 15 ou 30 segundos | Preferência explícita, sem ticks duplicados ou atualização periódica com tela apagada |
| 5 — desempenho | Medir vários widgets e otimizar se houver ganho | Comparação antes/depois, sem alterar o destino dos comandos ou o visual |

Dependências: preparar o roteiro da etapa 2 já na etapa 1; o teste físico usa o diagnóstico novo quando disponível. As etapas 3 e 4 são independentes da disponibilidade dos aparelhos e podem avançar enquanto aguardamos os resultados físicos. A etapa 5 vem depois da 4. Uma falha física relevante abre uma correção específica antes de ampliar o comportamento afetado.

## Contratos que devem permanecer

- Manter applicationId `dev.local.mediaactions`, certificado e atualização sobre o app instalado; sem desinstalação para migrar preferências.
- Preservar chaves existentes de aparência, `has_override`, identificação por ID e o estado temporário de original/rascunho da 0.18. Identificação do local continua separada da aparência.
- Manter a chave de progresso por widget: ligado/desligado independentemente das bordas. Indicador somente durante PLAYING; sem sessão, sem borda decorativa dos botões. Padding negativo permanece apenas vertical.
- Manter um comando por invocação, sem retry após envio incerto, sem mudar silenciosamente de aparelho/player e sem broadcast global de teclas.
- Preservar URIs dos atalhos, três componentes launcher Wear, protocolo `/mediaactions/v1/`, prazo/leases e deduplicação. Nenhuma tela de player, widget ou quarta entrada na home do relógio.
- Não registrar título, artista, capa, conteúdo de notificações, tokens de sessão ou IDs de nós nos diagnósticos compartilháveis. Sem servidor ou telemetria nova.
- Não prometer restauração das teclas Samsung pelo app nem presença de Editar no menu do LockStar: são comportamentos controlados pelo host/sistema.

## Etapa 1 — diagnóstico útil antes de mudar a integração

**Spotify no celular:** em `SpotifyPlaybackResumer.writeStatus`, gravar o status terminal e o horário de conclusão na mesma edição das preferências `spotify_resume`. Manter `status` compatível e acrescentar uma chave opcional `completed_at_epoch_ms`. Usar relógio de calendário para exibir horário; `elapsedRealtime` continua exclusivo dos prazos. Formatar conforme idioma e fuso do aparelho, sem fixar UTC ou um fuso brasileiro.

Na Ajuda/diagnóstico da `MainActivity`, mostrar **Última tentativa concluída**, resultado e horário. Para registros antigos sem horário, informar que ele não está disponível. CONNECTING não vira resultado persistente; BUSY de uma chamada concorrente não substitui a tentativa ativa. Manter a proteção da 0.18 para a resposta antiga não sobrescrever o resultado do toque atual.

Adicionar **Copiar diagnóstico** com versão do app, Android, último status/horário e informações técnicas da instância escolhida, quando pertinente: ID, categoria informada e dimensões. A cópia é local e iniciada pelo usuário. One UI, LockStar e WBR devem ser anotados pela versão efetivamente instalada, sem depender de API privada. Não copiar pacotes de players arbitrários ou dados de mídia.

**Wear:** mapear os resultados existentes de `LinkResult` para mensagens breves de acesso negado, ausência de sessão, conexão indisponível, ocupado e comando sem confirmação. Preservar o contexto do destino internamente em `WearActionReceiver` quando houver destino definido, sem mudar o payload. Se os probes não permitirem escolher um destino, não inventar qual aparelho falhou. SENT significa comando enviado; UNCONFIRMED não autoriza retry nem troca de aparelho.

**Aceite:** registros antigos continuam legíveis; horário só acompanha a conclusão correspondente; mudança de fuso não altera timeouts; duplicações não repetem comando; resultados tardios não mudam o toque atual. Testar os mapeamentos Wear e continuar encontrando exatamente três entradas launcher. Atualizar o APK Wear somente se seu código mudar.

**Arquivos principais:** `SpotifyPlaybackResumer.kt`, `MainActivity.kt`, `WearActionReceiver.kt`, strings pt-BR/inglês e testes próximos em `SpotifyBrowserResumeTest`, `AppAuditRegressionTest` e `WearLauncherTest`.

## Etapa 2 — fechar a evidência no aparelho

Preparar uma ficha por cenário com: versão/commit dos APKs, Android, One UI, LockStar/WBR, acesso à mídia, estado inicial, origem da ação, resultado esperado/observado, diagnóstico e evidência disponível. O teste no aparelho será executado pelo usuário ou numa sessão com dispositivo conectado; hoje essa dependência permanece aberta.

| Grupo | Cenários mínimos | Resultado a verificar |
| --- | --- | --- |
| Spotify | Sessão pausada; nenhuma sessão elegível com Spotify instalado/logado e fila anterior; outro player tocando; acesso revogado | Play/Pause acompanha estado real; fallback só no caso previsto; nenhum controle do player errado |
| Origem da ação | App, widget e atalho/Routines; repetir com outro app em primeiro plano | Uma chamada por gesto; não abrir o player para disfarçar falha de retomada |
| Processo | Retomada por atalho sob pressão/morte do processo; Spotify forçado a parar como caso separado | Registrar sobrevivência/limitação sem confundir force-stop com ausência normal de sessão; não repetir comando após reinício |
| LockStar | Um widget na tela inicial e outro no bloqueio; seleção externa de Editar quando oferecida; informação automática e marcação manual | ID correto no editor, configurações isoladas, local desconhecido não vira tela inicial por suposição |
| Aparência | Bordas independentes, centro/progresso, tamanhos e padding vertical negativo em hosts de dimensões diferentes | Sem alteração de outros widgets; registrar corte/margens impostos pelo host |
| Progresso | Play, pausa, sem sessão, duração desconhecida, tela apagada/AOD, opção desligada | Estado correto, sem ticks periódicos quando os requisitos não forem satisfeitos |
| Watch 8/WBR | Três bindings; só celular tocando, só relógio, ambos, ambos pausados e desconexão | Política de destino preservada; desconhecido não é pausa; sem fila para reconexão e sem execução duplicada |

No teste de retomada, playback confirmado é o aceite funcional. Se o Spotify rejeitar a conexão, registrar a falha explícita e investigar a causa; diagnóstico correto sozinho não comprova reprodução. Se o menu Editar não aparecer, registrar o comportamento do host e conferir o manifesto antes de concluir incompatibilidade. A marcação manual não comprova detecção automática.

O “ajuste adicional da lockscreen” citado pelo usuário ainda não foi especificado. O roteiro deve esclarecer o comportamento desejado com base no caso observado; não assumir que se trata de padding, edição externa ou outro visual. Essa pendência não bloqueia as etapas 1, 3 e 4.

**Saída:** relatório com PASSOU/FALHOU/NÃO TESTADO por cenário. Não anunciar compatibilidade física de itens sem evidência. Falhas específicas viram tarefas com reprodução e aceite próprios; não reescrever a integração inteira por hipótese.

## Etapa 3 — voltar ao visual padrão de uma instância

No editor de um widget instalado, oferecer **Usar visual padrão** e indicar se ele segue o padrão ou usa visual próprio. O padrão é o definido pelo usuário no app, não uma restauração de fábrica.

A ação prepara a mudança no rascunho e atualiza a prévia; nada é persistido até **Salvar alterações**. Cancelar preserva o override anterior. Ao salvar a herança, validar novamente que o widget pertence ao provedor e usar `WidgetStyleStore.remove(context, id)` somente para a aparência da instância. Não remover `WidgetLocationStore`, padrões globais ou preferências de outros widgets.

Guardar uma intenção temporária de herança separada do modelo `WidgetAppearance`, preservada em recriação. Ao seguir o padrão, textos inválidos do visual customizado descartado não bloqueiam Salvar. Editar novamente uma opção visual cancela a intenção de herança e cria um rascunho próprio a partir do padrão. Não adicionar o comando de restaurar instância à tela global de Visual padrão.

Se o padrão mudar durante a edição, o Save em modo de herança deve continuar usando o padrão atual, sem gravar um snapshot antigo como override. Ao retomar o editor, atualizar a prévia herdada. Preservar a comparação original/rascunho da 0.18 para edição customizada e edição somente de local.

**Aceite:** após restaurar/salvar, mudar o padrão altera essa instância; outros widgets customizados permanecem iguais; local manual permanece; Cancelar/recriar não apagam preferências; widget removido não é salvo; editar um campo após restaurar volta ao modo customizado. Testar também mudança externa de padrão/override e rascunho inválido anterior à restauração.

**Arquivos principais:** `WidgetAppearanceSection.kt`, `WidgetConfigureActivity.kt`, `WidgetAppearanceState.kt` se necessário ao estado temporário e `WidgetStyleStore` em `WidgetAppearance.kt`; regressões `WidgetLocationTest`, `WidgetConfigurationTest` e `AppAuditRegressionTest`.

## Etapa 4 — escolher o intervalo do progresso

**Decisão proposta:** intervalo global com opções **5, 15 e 30 segundos**. Cada widget continua podendo ligar/desligar seu próprio indicador. Um único agendador evita timers concorrentes por instância. Manter **5 s como valor inicial e fallback**, preservando o comportamento atual; apresentar 15 s como opção equilibrada, sem alegar economia de bateria medida.

Criar um armazenamento global separado da aparência, por exemplo `widget_progress_settings/interval_seconds`, aceitando somente 5/15/30. Converter segundos para milissegundos apenas no agendador. Preferência ausente/inválida retorna 5 s; não reescrever campos de `WidgetAppearance`, `has_override` ou `progressRing`.

Na seção Seu widget da tela inicial, oferecer **Atualização do progresso**, abrindo um seletor nativo com Salvar/Cancelar e aviso de que vale para todos os widgets com indicador. O controle por instância **Mostrar progresso da faixa** continua no editor; não apresentar um intervalo global como se fosse propriedade exclusiva daquela instância.

Tela interativa não significa widget visível: com outro app aberto, o telefone ainda pode satisfazer essa condição. Esta etapa não promete detectar a visibilidade do LockStar. Registrar esse caso na medição; se surgir um mecanismo suportado de visibilidade, avaliá-lo separadamente antes de usá-lo como critério de agendamento.

Em `WidgetPlaybackObserver.updatePulse`, ler o intervalo validado. Salvar uma mudança deve cancelar o tick antigo e recalcular o próximo, sem acumular callbacks. Manter os requisitos existentes: PLAYING, tela interativa, duração/posição calculáveis e ao menos um widget com progresso ativo. Callback real de play/pause/seek/metadados continua imediato, independentemente do intervalo.

**Aceite:** 5/15/30 s produzem a cadência escolhida no relógio simulado; trocar durante reprodução deixa um único tick; pausa/tela apagada/AOD/opção desligada/duração perdida interrompem ticks; retorno das condições reativa o agendamento; atualizações só de progresso não redesenham widgets simples. Cancelar ou recriar a tela não altera o intervalo gravado; recriar preserva a seleção ainda não salva. Salvar aparência ou local não reseta o intervalo global. Não adicionar wake lock, alarme ou serviço permanente.

**Arquivos principais:** novo store global pequeno, `WidgetPlaybackObserver.kt`, `MainActivity.kt`, strings e testes em `WidgetProgressObserverTest`/`WidgetProgressReviewTest`; preservar `MediaWidgetProvider.refreshAll(progressOnly)`.

## Etapa 5 — medir antes de otimizar

Comparar 1, 3 e 6 widgets, com/sem progresso, em intervalos 5/15/30 s. Usar sessões de mesma duração e condições controladas de tela, brilho e player; registrar duração e condições reais. Separar contagem de ticks periódicos dos callbacks de estado. Medir consultas de sessão/metadados, atualizações RemoteViews, tempo/CPU e alocação; bateria é uma medida separada no aparelho. Menos ticks não equivale ao mesmo percentual de economia de bateria.

Se consultas repetidas tiverem custo relevante, compartilhar um snapshot efêmero e coerente de sessão/estado/posição dentro de uma chamada de `MediaWidgetProvider.refreshAll`, com leitura de progresso só quando necessária. Ler posição e estado do mesmo player. Não persistir cache, reaproveitar snapshot entre comandos nem deixar o despacho de mídia depender desse cache.

**Aceite:** demonstrar redução de consultas/custo no cenário medido, mantendo imagens/estados, atualização de widgets simples apenas quando necessária e limites dos bitmaps. Troca de sessão e revogação de acesso não podem usar dados antigos em ações. Se não houver ganho relevante, documentar a medição e manter a implementação atual.

## Validação, publicação e reversão por entrega

Para código, rodar regressões proporcionais em API 33/36, build/lint dos módulos afetados e `git diff --check`. Nas entregas completas, usar a suíte documentada:

```sh
./gradlew :app:assembleDebug :wear:assembleDebug testDebugUnitTest lintDebug --console=plain
```

Os 394 testes são a base histórica, não uma contagem futura fixa. Acrescentar casos do comportamento novo e preservar as regressões da 0.18. Para UI, gerar e mostrar imagens reais antes da publicação: pt-BR, claro/escuro, janela estreita, fonte ampliada, teclado, salvar/cancelar e acesso externo ao editor.

Cada entrega deve terminar com a skill de self-audit, correções dos achados, README/relatório atualizados, commit no GitHub, versão de APK crescente quando houver código, certificado/applicationId conferidos e links públicos/checksums verificados. Mudança somente documental não exige APK/tag nova. O fluxo atual usa APKs em `downloads/vX-experimental` e release com links; não depender do upload de anexos que falhou no ambiente.

Publicar separadamente diagnóstico, restauração e intervalo. Wear só recebe APK novo se alterado, mantendo interoperabilidade com o protocolo v1. Teste físico pendente deve continuar explícito, sem impedir a publicação de mudanças independentes validadas.

Reversão: descartar mudanças não salvas com Cancelar; manter opção de desligar progresso e voltar a 5 s. Se uma versão introduzir regressão, publicar correção com código de versão maior, preservando preferências; não depender de downgrade/desinstalação. Restaurar o visual padrão após Salvar é uma alteração intencional daquela instância: não prometer recuperar automaticamente o visual customizado anterior.

Fora de escopo: novas famílias de ícones, redesign adicional amplo, SDK/login Spotify, fallback Wear para iniciar Spotify sem ofertas de sessão, captura global de teclas e alteração das políticas Samsung. Esses itens exigem plano próprio se forem solicitados.

[Review deste plano](../reviews/plano-proximas-medidas-pos-0.18.md) · [Review que originou as prioridades](../reviews/app-audit-0.18.md)

# Widget de reprodução e volume — plano de execução

**Atualização:** 13/11/2026. **Base inspecionada:** `962bfa8f8e60c99c28ad0c091bf610d45d6a7fe4`, app 0.34. **Estado:** implementado na fonte candidata 0.35 em 14/11/2026; validação automatizada completa aprovada e compatibilidade física pendente. Ver [execução e revisões](../reviews/combined-widget-and-identity-0.35.md). Este documento substitui a proposta implícita da conversa e incorpora os cinco achados da revisão.

## 1. Objetivo e escopo fechado

Oferecer **Reprodução e volume** como um terceiro tipo de widget no mesmo APK, com três comandos:

- Horizontal: **diminuir volume / Play-Pause / aumentar volume**.
- Vertical: **aumentar volume / Play-Pause / diminuir volume**, quando o espaço informado permitir.

Manter os widgets atuais de mídia e de volume, suas instâncias, atalhos, aparência e configuração. Não converter instâncias instaladas nem criar outro aplicativo. O volume é o `STREAM_MUSIC` do aparelho onde o widget está; Play/Pause usa o player elegível já selecionado pelo app. Isso não promete controlar o volume de um Spotify Connect remoto ou de outro aparelho.

Fora de escopo: cinco botões, anterior/próxima no combinado, slider/porcentagem de volume, gestos de repetição, interceptação de teclas, alterações em Wear/protocolo Data Layer, novas permissões, provedor privilegiado, correção da detecção de cores do Depth ou refatoração geral do app. Reutilizar a aparência existente, incluindo Compacta 16–160 dp, cantos, cores e bordas; não reintroduzir a opção Justa aos botões.

## 2. Evidências e achados incorporados

| Achado da revisão | Evidência atual | Correção exigida neste plano |
| --- | --- | --- |
| Alto: terceiro tipo fora da atualização | `WidgetRegistry` só enumera dois providers; `WidgetPlaybackObserver.loadTargets` só carrega `MediaWidgetProvider`; desconexão força UNKNOWN apenas nesse provider | Identidade explícita de três tipos e integração em todos os caminhos de estado, edição, atualização e cores; observador único |
| Alto: backup não representa o combinado | `BackupProfile.volume` é booleano; codec v4 só aceita `media`/`volume`; aplicação e deduplicação usam esse booleano | Tipo explícito no perfil, exportação v5 e importação v1–v4 preservada, com validação por tipo exato |
| Médio: comandos de outro provider são rejeitados | `VolumeWidgetProvider.isOwnWidget` e receiver validam somente o provider de volume | Novo receiver valida a própria instância; compartilhar execução de volume, não reutilizar PendingIntents de outro provider |
| Médio: geometria incompleta | Alvos mínimos de 48 dp; metadados do widget de mídia limitam largura mínima 144 dp e altura máxima 144 dp | Metadados próprios, dois layouts e fallback não interativo quando as dimensões faltarem ou não comportarem os três alvos |
| Médio: apresentação e autorização acopladas | `WidgetRenderer` usa o estado de reprodução para bordas e flags de volume para remover o centro/progresso | Modelar capacidades e apresentação por botão: volume independe do estado da mídia; apenas o centro acompanha reprodução |

A ausência de acesso a notificações já impede Play/Pause no dispatcher atual; volume usa a permissão normal `MODIFY_AUDIO_SETTINGS`, já declarada. Grant do listener não é prova de conexão ou de reprodução. Não há justificativa para acrescentar acessibilidade, isenção de bateria, serviço permanente ou Shevery nesta função.

## 3. Identidade e registro

Introduzir `WidgetKind { MEDIA, VOLUME, PLAYBACK_VOLUME }`, com nomes finais ajustáveis na implementação, e capacidades derivadas `hasPlayback`/`hasVolume`. O tipo de uma instância deve vir do `ComponentName` validado por `AppWidgetManager`, nunca de um extra arbitrário ou do visual padrão.

Criar `PlaybackVolumeWidgetProvider` e metadados próprios, mantendo os dois componentes existentes e suas identidades. O novo receiver será não exportado e usará o editor existente, com `reconfigurable|configuration_optional`, categorias home/keyguard e `updatePeriodMillis=0`.

Atualizar `WidgetRegistry`: identificação, enumeração, nome, atualização individual/global e enumeração de consumidores de reprodução. Trocar decisões binárias por tipo/capacidade onde determinam roteamento, sem substituir booleanos que continuam tendo significado específico. O padrão global permanece apenas visual: não muda o tipo nem os comandos de widgets existentes.

Mapear também `WidgetConfigureActivity`, `WidgetAppearanceSection`, `WidgetOptionsUpdates.Key`, seleção/listagem de widgets e caches de apresentação. O editor deve verificar o tipo original ao salvar, rejeitar instância removida/trocada e conservar Salvar/Cancelar, herança, conflitos, rascunho e recriação. A prévia nunca executa ações. A prévia global pode continuar representando mídia, como hoje; a do combinado deve usar seu tipo real.

## 4. Execução dos comandos e isolamento

Extrair do provider de volume um executor pequeno, por exemplo `VolumeActions`, usado pelo widget de volume existente e pelo novo provider. Conservar stream/direção/flags atuais: um passo via `adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER/RAISE, 0)`, consulta de volume fixo e feedback de erro, sem replay ou retry automático. Não alterar alarme, toque ou DND por código próprio.

O combinado aceita exatamente três comandos tipados. Cada PendingIntent terá componente, ação, URI e ID coerentes; usar identidade imutável e diferenciada por comando. Validar instância, provider e correspondência URI/ID antes de executar. Não ampliar a autorização do receiver antigo para aceitar qualquer widget. Cancelar PendingIntents e remover caches/preferências da instância na exclusão, preservando a regra existente de rejeitar comandos antigos.

Play/Pause chama exclusivamente `PhoneMediaActions.execute(PLAY_PAUSE)`, mantendo verificações frescas, seleção e capacidades do player. Preservar o fallback Spotify atual somente para esse comando e nas condições já admitidas pelo helper. Não adicionar volume ao enum `MediaAction` nem ao protocolo compartilhado com Wear.

Volume não passa pelo dispatcher de mídia, não chama Spotify, não abre Activity e não faz refresh completo de reprodução após o clique. Feedback visual usa o ripple existente. Play/Pause assíncrono deve finalizar `goAsync()` uma única vez nos caminhos de sucesso/falha; nenhum comando pode ser repetido ao reconectar serviços ou recuperar permissões.

## 5. Estado, recuperação, caches e cores

Reutilizar **o mesmo** `WidgetPlaybackObserver` e listener. Não criar outro monitor de sessões. A lista de alvos deve incluir os tipos com reprodução efetivamente exibida. Um lote compartilha snapshot e seleção, independentemente da quantidade de widgets de mídia/combinados.

Cobrir explicitamente:

1. Inicialização, conexão, callbacks de estado/metadados e escolha do player.
2. Atualização solicitada pelo host, opções/resize, salvar, padrão global e aplicar perfil.
3. Tela apagada/acesa, revogação, desconexão, destruição e reconexão do listener.
4. Renderização, deduplicação e atualização por cores do wallpaper.
5. Exclusão, último consumidor removido e descarte de callbacks antigos.

Na perda de conexão/autorização, a apresentação dos centros afetados passa a UNKNOWN e deixa de mostrar progresso/estado confirmado. Preservar o comportamento de execução do helper, sem equiparar conexão perdida a revogação. Volume permanece acionável conforme a política de áudio. Avisos de autorização devem se referir à reprodução, sem bloquear o widget inteiro nem solicitar acessos durante sua renderização.

Revisar `MediaNotificationListener.stopPlayback`, `MediaWidgetProvider.refreshAll`, `PresentationCache`, `WidgetPlaybackObserver.loadTargets`, `WidgetRegistry` e `WidgetWallpaperRuntime`. Criar operações de lote para os consumidores de reprodução sem alargar silenciosamente métodos destinados apenas ao provider antigo. No runtime de wallpaper, substituir a decisão binária de ownership/render pelo tipo real. Não confiar no ramo else como sinônimo de MediaWidgetProvider.

As chaves de configuração/apresentação devem representar tipo, geometria/orientação, aparência e os dados realmente exibidos. Estado igual não produz outro RemoteViews. IDs/comandos continuam sujeitos à verificação fresca necessária às ações; cache serve à apresentação. Configuração/atualização explícita e recuperação não podem ser indevidamente suprimidas pelo cache de resize.

## 6. Renderer e regras por botão

Manter uma descrição explícita de cada botão: ação, ícone, nome acessível e dependência de playback. O novo tipo tem três botões; não usar `volume=true` no renderer atual, pois isso remove o centro e desliga progresso/estilos de reprodução.

- Centro: família de ícone de reprodução, Play/Pause sincronizado, estado neutro quando desconhecido e progresso opcional somente durante reprodução confirmada, conforme regras atuais.
- Laterais: símbolos −/+, descrições corretas e ações de volume, inclusive sem sessão ou autorização de notificações.
- Bordas individuais: respeitar configuração e `centerBorderOnly`. Estado UNKNOWN só pode afetar a decoração dependente da reprodução; não deve remover bordas dos controles de volume quando elas estiverem habilitadas para esses botões.
- Moldura/padding/tamanho de ícones: mesmas opções existentes. No horizontal, Compacta altera apenas o contorno, sem reduzir o alvo. No vertical, a moldura deve envolver os três controles; mostrar dimensões efetivas no editor quando o espaço ou essa restrição limitar o valor desejado.
- A mudança H/V não troca o significado dos cliques: + em cima, − embaixo e Play/Pause no centro. Atualizar acessibilidade e limpar dimensões/paddings/backgrounds anteriores em reapply.

## 7. Dimensões, orientação e fallback

Usar as variantes válidas de tamanho informadas pelo host, incluindo o fallback min/max já implementado em `WidgetRenderer.reportedSizes`. Três alvos precisam de **144 dp no eixo principal e 48 dp no transversal**, antes dos gaps/padding. Reduzir primeiro gaps/padding conforme a política existente; não reduzir alvos para caber.

Para cada variante:

1. Horizontal é utilizável com largura ≥144 dp e altura ≥48 dp; vertical, com altura ≥144 dp e largura ≥48 dp.
2. Se apenas um eixo comportar os controles, usá-lo. Se ambos comportarem, altura maior que largura escolhe vertical; demais casos escolhem horizontal.
3. Aplicar espaçamento no eixo dos botões e padding nos eixos físicos. Calcular gaps/padding efetivos preservando alvos de 48 dp e limites físicos do host.
4. Se nenhum eixo comportar, usar layout instrutivo **Redimensione o widget**, sem PendingIntents de reprodução/volume. Não ocultar comandos mantendo áreas clicáveis antigas.
5. Se não houver dimensão confiável, usar o mesmo fallback seguro não interativo, sem inventar que o host comporta três alvos. A prévia do editor pode mostrar um exemplo horizontal identificado como tal; isso não confirma o tamanho real.

Os novos metadados podem usar tamanho inicial horizontal 180×56 dp, target 3×1, mínimos de resize 48×48 dp e máximos 432×432 dp. Os mínimos não garantem que qualquer retângulo comporte os controles: o fallback acima é obrigatório. Não mudar os metadados dos dois providers antigos.

Se LockStar nunca fornecer dimensões utilizáveis, o combinado não terá compatibilidade confirmada nesse host; preservar os widgets atuais e registrar o limite, sem prometer funcionamento. Testar em aparelho antes de declarar suporte. Widgets combinados sem nenhuma variante utilizável não devem sustentar progresso periódico ou renders causados apenas por mudança de playback. Reavaliar alvos/caches no resize para entrar e sair desse estado sem recriar a instância.

## 8. Persistência e backup v5

Substituir `BackupProfile.volume` por tipo explícito nos pontos de serialização, captura, deduplicação, listagem e aplicação. IDs locais continuam fora do arquivo exportado.

| Versão | Tipos aceitos | Comportamento |
| --- | --- | --- |
| v1–v4 | Strings originais `media` e `volume` | Mapear aos tipos atuais preservando campos, defaults legados, herança e localização |
| v5 | `media`, `volume`, `playback_volume` | Representar o novo tipo sem convertê-lo em mídia ou volume isolados |

Exportar v5; importar v1–v5. Tipo/versão desconhecidos ou tipo novo indevido em arquivo antigo devem ser rejeitados antes de gravar. Conservar os limites atuais de 256 KiB/100 perfis, validação de profundidade/tipos/ranges e semântica de confirmação/commit/readback/falha parcial. Não migrar o storage visual nem renomear suas chaves.

Atualizar `SettingsBackupCodec`, `SettingsBackupStore` e `BackupActivity`: filtros e guardas por tipo exato, rótulos e deduplicação incluindo o tipo. Dois perfis visualmente idênticos de tipos diferentes não podem desaparecer na deduplicação. Não oferecer aplicação direta de um perfil combinado a um widget antigo. Usar um perfil como padrão visual não converte instâncias.

Preservar a leitura compatível de preferências antigas já existente, inclusive o antigo valor FIT_BUTTONS como Compacta. Não converter ou regravar automaticamente widgets antigos ao instalar a atualização. Backups v1–v4 já salvos continuam legíveis. Documentar que apps antigos recusam v5; seguir o retorno seguro da seção 13, preservando também os backups anteriores.

## 9. Consumo e observabilidade

Sem monitor de nível/porcentagem de volume, alarmes, serviços novos, wake locks, varreduras por filesystem ou novos pedidos de bateria. Atualização por estado/callback existente. Progresso segue o único pulso atual, apenas com consumidor utilizável, opção ligada, mídia avançando e tela interativa. Tela apagada remove o pulso conforme o contrato atual.

Registrar contadores de trabalho somente em testes/instrumentação optativa, sem conteúdo de mídia:

- Com processo/observador preparados e progresso desligado, dez cliques válidos de volume geram dez ajustes no máximo, zero enumerações de sessão, zero leituras de metadados e zero RemoteViews causados pelos cliques.
- Com progresso ativo, os cliques não acrescentam ticks além da cadência existente. Separar esses ticks dos eventos da ação ao medir.
- Callbacks duplicados com os mesmos dados não entregam novas views. Acrescentar combinados não multiplica snapshots por ID.
- Cold start pode ter o trabalho normal de inicialização/reconexão. Medir separadamente; não declarar zero consultas totais no processo a partir do teste de volume preparado.
- Layout instrutivo sem controles não mantém progresso nem varia sua apresentação a cada mudança de playback.

Comparar cenários controlados no mesmo aparelho antes/depois: widgets atuais versus combinado, com configurações equivalentes, tela acesa/apagada, pausa/play, progresso ligado/desligado e saída Bluetooth. Separar redução comprovada de trabalho de economia de bateria efetivamente medida. Sem medição física, informar apenas o limite demonstrado pelos testes.

## 10. Sequência de implementação

1. Criar tipo/capacidades e catálogo de providers; adicionar testes de reconhecimento/ownership sem alterar instâncias atuais.
2. Extrair o executor de volume preservando os testes antigos; criar provider/comandos/PendingIntents do combinado e testes de isolamento.
3. Integrar lotes, listener, recuperação, caches e wallpaper; provar funcionamento independente e ausência de consultas de mídia nos cliques de volume.
4. Implementar layouts horizontal/vertical/instrutivo, renderer e metadados próprios; adaptar editor, preview e reapply.
5. Implementar perfil tipado e codec v5, mantendo leitores v1–v4; atualizar captura, deduplicação, aplicação e UI de backup.
6. Rodar testes focados por bloco, revisar o diff e corrigir achados. Não repetir toda a matriz por cada ajuste de texto/CI.
7. Gerar prévias nativas do combinado e do editor e mostrá-las ao usuário antes de publicar o app. Executar a cadeia completa na fonte final e inspecionar o artefato.
8. Fazer validação física e entrega conforme os gates abaixo; atualizar documentação operacional e relatório com evidências reais.

Os nomes novos neste documento são propostas, não classes já existentes. Não adicionar dependência/arquitetura nova quando os helpers e RemoteViews atuais resolverem o caso.

## 11. Matriz mínima de aceite

| Cenário | Resultado exigido |
| --- | --- |
| Tipos atuais e combinado instalados juntos | Listagem, editor, salvar/refresh, cores e aparência atendem cada instância; nenhuma conversão dos antigos |
| Autorização ausente/revogada | Volume continua funcionando; centro não anuncia estado confirmado e orienta correção somente após gesto pertinente |
| Listener desconectado/destruído e depois reconectado | Centro/progresso saem do estado confirmado e recuperam por eventos; volume funciona; nenhum comando antigo é repetido |
| Sem sessão ou sessão em transição | Centro segue o helper atual, inclusive Spotify somente nas condições existentes; laterais não iniciam player nem consultam sessões |
| IDs removidos, tipo errado, URI/ID incompatíveis ou PendingIntent antigo | Nenhuma ação indevida; limpeza de caches/PendingIntents testada |
| Volume no mínimo/máximo, fixo ou política negada | No máximo uma tentativa por gesto; sem modificar outros streams ou reenviar |
| Horizontal/vertical, mudança de orientação e múltiplos tamanhos | Sem inversão de +/−, recortes ou herança de dimensões antigas; 48 dp por alvo quando utilizável |
| Área insuficiente ou dimensões ausentes | Mensagem instrutiva, nenhum comando invisível; sem consumidores/ticks de reprodução desnecessários |
| Progresso/bordas sem sessão, com pausa/play e tela apagada | Somente o centro depende da mídia; volume respeita suas bordas/feedback; pulso segue condições atuais |
| Editor cancelado, recriado, conflito entre editores ou instância removida | Preferências/rascunhos preservam as regras existentes; preview não envia comandos |
| Importação v1–v4 e round-trip v5 | Antigos preservados; combinado identificado; aplicação cruzada rejeitada; deduplicação não perde tipos diferentes |
| Callbacks/resize repetidos e cliques de volume | Contadores confirmam deduplicação e os limites de trabalho da seção 9 |

Usar doubles/sombras existentes em `VolumeWidgetTest`, `WidgetProgressObserverTest`, `BackgroundReliabilityTest`, `BackgroundWorkComparisonTest`, `WidgetOptionsUpdatesTest`, `SettingsBackupTest` e testes de editor, com integração do provider/registry/render reais. Helpers isolados não substituem as provas de roteamento e recuperação. Testar API 33/36, regressões dos widgets antigos e isolamento Preview.

## 12. Gate de validação e publicação

Manifest, backup e caminhos sensíveis tornam a implementação uma mudança de escopo **full** no [CI](../CI.md). Antes da entrega, executar a cadeia completa vigente: Debug/testes/lint dos três módulos, Release do app com testes/lint, isolamento Preview e inspeção de APK. Usar o comando canônico de `docs/CI.md`, mantendo o filtro Preview restrito ao task correspondente. Concluir `git diff --check` e a skill de self-audit sobre a implementação.

A última verificação remota registrou bloqueio de faturamento. Verificar a situação quando necessário, sem reruns em loop. Se continuar bloqueado, seguir a execução completa local rastreável prevista em [STABLE_RELEASE.md](../STABLE_RELEASE.md), sem chamar o CI de aprovado nem contornar checks obrigatórios. Esta atualização documental não exige build do app, emulador ou assinatura.

Registrar fonte/commit, comandos, resultados, APK exato, manifest, SHA256 e certificado histórico do original. Preservar pacote e chave; não publicar Preview como atualização oficial e não acrescentar app/atalhos de launcher do Wear. Definir versionCode/versionName somente na implementação, conforme a versão corrente.

Validação física mínima antes de anunciar compatibilidade: Galaxy S25 e Tab S10+, launcher e LockStar, dimensões reais, clique correto nos três alvos, saída local/Bluetooth, permissão/serviço perdidos e recuperados, resize, tela apagando normalmente e upgrade sobre o original. Identificar dispositivo/Android/One UI/LockStar usados. Fonte/host sem dados e limitações OEM permanecem pendências explícitas.

## 13. Rollout e retorno seguro

Disponibilizar somente como widget adicional escolhido pelo usuário. Os dois existentes continuam disponíveis, com seus mesmos componentes, stores e comportamento. Não adicionar opção que converta todos os widgets pelo visual padrão.

Se houver regressão antes da entrega, não publicar. Se detectada após entrega, orientar uso dos widgets antigos e preparar atualização corretiva com versionCode maior e a mesma assinatura. Manter leitor v5 e identificação do terceiro tipo na correção; não descartar perfis novos nem mapeá-los a tipos incompatíveis. Se for necessário suspender ações do combinado, manter seu componente reconhecido e apresentar estado indisponível em vez de executar ações erradas; não remover componentes/dados de instalações existentes silenciosamente.

Não recomendar downgrade como rollback padrão: apps anteriores não leem v5 e o Android pode rejeitar código de versão menor. Não desinstalar para testar assinatura/retorno. Backup anterior e perfis exportados são proteção adicional, não permissão para perder dados. Retorno seguro deve preservar os ajustes antigos e deixar claro o estado das instâncias combinadas.

## 14. Revisão do plano atualizado

**Veredito: APROVADO para implementação.** Os cinco achados da proposta foram incorporados às decisões, à sequência e à matriz de aceite; não restam achados altos ou bloqueantes nesta revisão documental. A aprovação não confirma o comportamento do app ainda não implementado.

Risco residual principal: dimensões e interação fornecidas por launcher/LockStar, dependentes de validação física. A execução também deverá registrar as evidências locais/remotas de validação e manter explícito qualquer bloqueio de faturamento do CI.

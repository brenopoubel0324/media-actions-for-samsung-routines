# Media Actions no Galaxy Watch 8 — Wear Button Remapper

## Escopo corrigido

O pedido é associar botões/gestos do Watch aos atalhos **Anterior, Play/Pause e Próxima**. O fluxo desejado é:

`Gesto configurado → entrada de ação → escolher aparelho/player → executar uma ação → encerrar`

Não haverá tela de reprodução, widget, Tile, complicação, seletor visual de aparelho ou personalização visual no Watch. A imagem de três telas entregue anteriormente ficou superada por esta correção de escopo. Configuração/autorização mínima só será adicionada se necessária para o binding ou exigida pelo sistema; não será uma tela de controles para uso diário.

Mantém-se o requisito anterior: controlar a música do celular e, quando estiver tocando localmente no relógio, controlar o relógio. As melhorias do aplicativo do celular e sua atualização no GitHub continuam na entrega.

## O que já existe e o que precisa ser demonstrado

Na base anterior a esta implementação, o repositório continha apenas `:app`, aplicação `dev.local.mediaactions`, prévia local 0.7. `shortcuts.xml` expõe três atalhos que chamam `ActionActivity` com URIs exatas. Essa Activity executa uma ação local e termina, sem tela. Naquela base não havia módulo Wear ou ponte para o celular. O estado atual está na seção de implementação ao final; continua sem remapeamento global próprio de botões.

A Samsung documenta o duplo toque no Home para abrir um aplicativo. Isso não prova que o seletor do Watch 8 liste os três atalhos Android nem que existam três gestos livremente atribuíveis. A API de botões Wear, por sua vez, trata ações dentro do app em primeiro plano; não deve ser apresentada como mecanismo de binding global.

O usuário escolheu **Wear Button Remapper (WBR)**. A inspeção estática do APK público atual do autor, versão 1.06, confirmou consulta de Activities MAIN/LAUNCHER, rótulo por Activity e armazenamento do componente completo. A abertura é por Intent explícito, inclusive sem action/data. Portanto, usar três Activities launcher concretas no mesmo APK, cada uma com ação fixa e ícone/nome próprios; o aplicativo de mídia do mesmo autor também usa múltiplas Activities launcher. Não criar três pacotes/APKs auxiliares e não depender do menu nativo de botões.

O WBR continua responsável por identificar o gesto e escolher o componente. Nosso app apenas executa a ação, sem remapeamento global próprio ou dependência de Routines+ no relógio. A inspeção estática comprova o contrato, mas o pareamento real no Watch 8 ainda será testado.

## Etapa 1 — provar o binding e o acesso local

1. Criar APK Wear experimental com três entradas launcher: **MA · Anterior**, **MA · Play/Pause** e **MA · Próxima**. Verificar sua seleção individual no WBR instalado no Watch 8.
2. Testar cada componente com o Intent explícito usado pelo WBR (sem action/data) e com ACTION_MAIN usado pelo launcher. Extras do chamador não alteram a ação fixa do componente.
3. Demonstrar chamada fora do app, com outro app aberto e com tela apagada/bloqueada. Registrar wake/unlock, animação de abertura e limites do sistema. Sem tela própria de controle; não prometer ausência de animação do sistema antes do teste.
4. Se houver diferença na versão instalada do WBR ou no firmware, registrar o comportamento encontrado e ajustar a integração por componente. Não substituir o binding por um player visual.
5. No mesmo diagnóstico, verificar um caminho suportado de autorização para observar e controlar sessões de outro player instalado no relógio. Testar estado, play/pause, anterior e próxima conforme as ações anunciadas pelo player.

**Critérios de saída:** mecanismo de binding demonstrado e comandos locais demonstrados separadamente. Autorização de notificações no celular não concede autorização no Watch. Não presumir permissão privilegiada `MEDIA_CONTENT_CONTROL`. Solução que dependa de ADB deverá ser identificada como experimental.

Se os bindings não forem possíveis pelo caminho escolhido, o suporte a botões não será anunciado como pronto. Se apenas o acesso ao player local falhar, o caminho Celular poderá avançar, mas o controle local permanecerá uma limitação explícita.

## Etapa 2 — implementação mínima das ações

Adicionar `:wear` e compartilhar apenas os contratos/seleção que forem realmente usados. Manter a ferramenta atual (AGP 8.13.2, Kotlin 2.2.21, SDK 36) sem atualização lateral. Preservar as URIs existentes do celular, preferências do widget e seleção de sessões.

As entradas do relógio devem identificar a ação sem abrir o player. Validar componente/ação/URI por allowlist; invocação inválida ou recriação de Activity não repete o comando. Não copiar simplesmente o `ActionActivity` atual para iniciar uma tarefa assíncrona e encerrar: a execução remota precisa de componente com ciclo de vida e prazo próprios, sem tarefa solta após `finish()` e sem serviço permanente.

O backend local usa sessão/ação anunciada pelo player, sem broadcast global de teclas como fallback. Reutilização do gateway deve permitir o componente de listener correto em cada aparelho; hoje ele fixa o listener do celular. Não é necessário criar uma sincronização permanente para uma tela inexistente: consultar o estado necessário à invocação.

## Etapa 3 — ponte para o celular e escolha do destino

Usar Wear OS Data Layer, com mesmo applicationId/certificado no celular e relógio e manifesto Wear identificado como dispositivo watch. Verificar permissões dos manifestos mesclados. Essa infraestrutura permanece necessária para controlar música do celular a partir do relógio, mesmo sem interface de reprodução.

Política automática preservada:

| Estado recente | Destino |
| --- | --- |
| Só celular tocando | Celular |
| Só relógio tocando | Relógio |
| Ambos tocando | Celular |
| Ambos pausados/parados | Último aparelho controlado; celular inicialmente |
| Um tocando e outro desconhecido | Aparelho com reprodução confirmada |
| Nenhum tocando e destino preferido indisponível | Não executar em outro destino silenciosamente |

Não tratar desconhecido/desconectado/sem permissão como pausado. Persistir somente o último tipo de aparelho, não estado de reprodução. Dentro do aparelho escolhido, manter o seletor existente; sessão ambígua não recebe comando.

### Garantias mínimas de comunicação

- Resolver um único peer compatível; nunca enviar uma ação para todos os nós.
- Usar `MessageClient` para comandos, sem fila persistente de ações e sem retry automático. DataItems não armazenam comandos.
- Protocolo versionado, caminhos/ações validados e payload pequeno limitado (4 KiB). Ações de rede explícitas PLAY/PAUSE/NEXT/PREVIOUS, separadas da enum legada do celular; escolher play/pause a partir de consulta recente.
- Cada invocação fixa aparelho e sessão, com requestId único, época do receptor e prazo curto concedido pelo receptor. Nunca comparar `elapsedRealtime` dos dois aparelhos; validar idade no receptor e limitar RTT da consulta. Referência de sessão é opaca, sem transportar `MediaSession.Token`.
- Revalidar sessão, estado e capacidade imediatamente antes da execução. Se mudou, rejeitar a invocação em vez de controlar outro player. Serializar validação/deduplicação/envio; registrar requestId antes da chamada ao player e manter o resultado pelo prazo de validade, com ledger limitado. Reinício muda a época e invalida pedidos antigos.
- Confirmar envio não equivale a confirmar mudança de reprodução. Timeout após envio não prova falha: não repetir e não transferir ao outro aparelho. Sem conexão, não guardar próxima/anterior para executar mais tarde.
- Usar limite de execução compatível com o componente escolhido, incluindo consulta, envio e limpeza. Não criar polling contínuo ou serviço em primeiro plano permanente.

Data Layer pode usar Bluetooth ou infraestrutura de nuvem. Não prometer Bluetooth exclusivo, nem usar `isNearby` como garantia de transporte local. Não haverá servidor próprio, áudio, título/artista ou conteúdo de notificações nos payloads. Logs limitam-se a resultado técnico, sem tokens, metadados musicais ou IDs pessoais.

## Etapa 4 — testes, review e GitHub

Critérios de aceite:

- Binding real chama a ação fora do app e executa no máximo uma chamada ao player por gesto. Documentar quais gestos foram efetivamente testados, sem afirmar que três bindings nativos existem antes da prova.
- Play/pause usa estado atual; próxima/anterior respeitam as ações anunciadas. Sessão ambígua, acesso revogado e invocação inválida não executam comando.
- Cenários celular tocando, relógio tocando, ambos e pausados demonstrados; controle local sem celular conectado, quando o acesso local estiver validado.
- Duplicação, atraso, expiração, ACK perdido, reconexão, reinício e morte de processo não provocam repetição ou mudança silenciosa de destino.
- Testes/build/lint dos módulos, verificação de package/certificado/manifestos e regressão dos cenários dos 180 testes já aprovados no celular. Testes existentes e emuladores não substituem os testes físicos de binding e autorização no Watch.
- Rodar a skill de self-audit após implementar e corrigir os achados. A [revisão deste plano](../reviews/wear-os-galaxy-watch-8.md) aprova o diagnóstico por fases, não afirma compatibilidade física já comprovada.

**GitHub é parte obrigatória da entrega:** atualizar código, README, licenças/créditos e relatório em [media-actions-for-samsung-routines](https://github.com/brenopoubel0324/media-actions-for-samsung-routines), criar tag/release da versão validada e anexar APKs identificados com checksums. Conferir commit/tag e links de download e entregar o link da release ao usuário.

As melhorias já prontas no celular podem receber release própria, sem aguardar o diagnóstico Wear. Preservar as alterações locais e conferir o remoto antes de integrar, sem sobrescrever trabalho existente. Apresentar imagens das mudanças visuais do celular antes de publicar; para o Wear sem tela de controles, entregar evidência do binding/configuração real, não novos mockups de player.

Rollback da integração: desfazer o binding nas configurações/remapeador e remover/desativar o companion Wear, preservando o uso atual do celular. Não depender de downgrade do APK. Nesta revisão não houve implementação ou publicação; não há dispositivos conectados ao ambiente.

## Fontes

- [Samsung: personalizar o botão Home](https://www.samsung.com/us/support/answer/ANS10003380/) — abertura de app por duplo toque; disponibilidade varia por modelo/firmware.
- [Android: botões físicos no Wear OS](https://developer.android.com/training/wearables/user-input/physical-buttons) — tratamento no app em primeiro plano.
- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager) e [permissões Wear](https://developer.android.com/training/wearables/apps/permissions) — autorização local.
- [Data Layer](https://developer.android.com/training/wearables/data/overview) e [tipos de cliente](https://developer.android.com/training/wearables/data/client-types) — identidade, transporte e comportamento das mensagens.

## Estado de implementação

A prévia 0.8-experimental contém os módulos :wear/:shared, três Activities launcher concretas, receiver privado com execução breve e RPC companion no celular. O acesso local é conferido antes de cada comando; não existe fallback por teclas globais. O APK de teste também serve à validação física pendente. Consulte [as instruções WBR](../WEAR_BUTTON_REMAPPER.md) e [a revisão de implementação](../reviews/wear-button-remapper-implementation.md). Não confundir código implementado/testado no host com suporte físico já confirmado.

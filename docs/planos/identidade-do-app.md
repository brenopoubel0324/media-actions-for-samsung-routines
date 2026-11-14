# Identidade do app — nome, símbolo e posicionamento

**Data:** 14/11/2026. **Base:** `0970d86e32caf60e0086fd86059a8988ae116d6d`, celular 0.34 e Wear 0.9 experimental. **Estado:** plano de descoberta, criação e aplicação futura; marca e nome ainda não escolhidos. Nenhum recurso do aplicativo foi alterado.

## 1. Problema e objetivo

Media Actions descreve uma função, mas oferece pouca identidade. O ícone atual do celular (`app/src/main/res/drawable/ic_app.xml`) é um fundo verde com triângulo e barra brancos, próximo de um botão de próxima faixa. O produto já oferece atalhos de mídia, widgets de mídia/volume e ações para o relógio: sua apresentação deve comunicar essa proposta com uma assinatura visual própria.

Objetivo: desenvolver um nome memorável e um símbolo reconhecível que expressem **controle de mídia acessível pelo caminho escolhido pelo usuário**, com visual discreto e ajustável. O significado deve ficar claro pelo conjunto nome, símbolo, descritor e exemplos de uso; o ícone sozinho não precisa explicar todas as integrações.

O pedido atual autoriza o plano. Na execução futura, apresentar imagens comparativas e colher a escolha do usuário antes de aplicar a identidade definitiva ou publicar o app, conforme a preferência já expressa na conversa. Este documento pode ser publicado no repositório sem alterar o APK.

## 2. Pesquisa inicial e diferencial defensável

Fontes primárias consultadas em 14/11/2026. São descrições públicas dos produtos, não testes comparativos nem auditoria exaustiva do mercado.

| Referência | Fato observado | Implicação para a identidade |
| --- | --- | --- |
| [Audio Widget Pack](https://play.google.com/store/apps/details?id=com.widgets.music&hl=en) | Descreve controle de reprodução pelo launcher e seleção automática/manual do player | Controle de player por widget já é atributo da categoria |
| [KWGT — documentação oficial](https://docs.kustom.rocks/) | Apresenta criação personalizada de widgets, incluindo música | Personalização isolada não distingue suficientemente nossa marca |
| [Alternate Music Widget](https://play.google.com/store/apps/details?id=com.kissdevs.kdmusicwidget&hl=en) | Descreve widget transparente em vários tamanhos; página informa atualização de 2017 | Transparência tem antecedentes; isso não confirma funcionamento atual desse produto |
| [Wear Media Toggle — Freepoc](https://freepoc.org/downloads/) | Apresenta ações de reprodução e volume por ícones no relógio, integração com WBR e companion para o celular | Ações separadas no Wear e controle remoto de mídia também têm antecedentes |
| [Button Mapper](https://play.google.com/store/apps/details?id=flar2.homebutton&hl=en_US) | Oferece remapeamento de teclas para atalhos, mídia e volume | Controle por botão físico também não é uma reivindicação exclusiva |

**Síntese de posicionamento, como hipótese a testar:** reunir uma experiência focada em comandos de mídia, acessível por widget personalizável, rotina Samsung e ações selecionáveis no relógio. A combinação, o cuidado com ajuste fino e a continuidade entre essas entradas orientam a marca. Esta amostra não demonstra que a combinação seja exclusiva no mundo.

| Pilar da proposta | Evidência no projeto | Como comunicar |
| --- | --- | --- |
| Escolha do caminho de acesso | Shortcuts para Rotinas; widgets; três componentes launcher Wear para WBR | Mesma família de marca apresentada em toque, rotina e botão; informar as dependências de cada integração |
| Ajuste ao espaço e ao wallpaper | Transparência, tamanho de ícones, distância, padding, cantos e bordas por instância | Mostrar controles discretos em contextos diferentes, com exemplos fiéis das opções existentes |
| Comando direto | Entradas executam ações sem uma tela própria de player | Evidenciar o gesto e seu resultado; documentar possíveis transições impostas pelo host |
| Cuidado com continuidade e trabalho em segundo plano | Recuperação por eventos, caches de apresentação e progresso condicionado já documentados | Explicar funcionamento e limites na documentação; não prometer superioridade de bateria sem medição |

O widget combinado de Play/Pause e volume tem [plano separado](widget-reproducao-e-volume.md), ainda sem implementação. Não utilizá-lo como prova de uma função entregue. A mudança de identidade pode ocorrer independentemente dele.

## 3. Briefing de marca

- **Personalidade:** precisa, discreta, tátil e pessoal. Deve sugerir acesso a um comando, com legibilidade antes de ornamentação.
- **Proposta verbal a testar:** “Sua mídia, ao seu comando.”
- **Descritor factual:** “Widgets e atalhos de mídia”. Em materiais de apresentação, complementar com “No widget, na rotina e no botão do relógio”, identificando o companion como opcional e WBR como integração externa.
- **Reconhecimento:** buscar forma própria reconhecível sem depender da cor, de uma inicial ou de um símbolo padrão de Play/Pause. Usar símbolos funcionais conhecidos nos comandos propriamente ditos.
- **Originalidade:** criar desenho próprio e comparar com referências da categoria. Pesquisa de similaridade orienta a escolha, sem certificar exclusividade absoluta ou disponibilidade jurídica.

Evitar promessas não comprovadas: “único”, compatibilidade universal, funcionamento garantido com tela apagada em qualquer host, restauração automática de bindings, consumo zero, detecção infalível do Depth ou menor consumo que concorrentes. Acesso a sessões e Data Layer mantêm os limites atuais de autorização e transporte.

## 4. Nome: processo e critérios de escolha

Criar 8–12 candidatos, selecionar três e apresentar cada um com motivo, pronúncia e aplicação visual. Preferir nomes curtos, com 2–3 sílabas, fáceis de escrever e pronunciar em PT-BR e inglês, capazes de representar mídia, volume e atalhos. A marca pode ser abstrata; o descritor carrega a explicação funcional.

Pesquisar nomes exatos, variantes de escrita e semelhantes fonéticos na web, Google Play, Galaxy Store, GitHub e bases de marcas pertinentes ao público pretendido, incluindo INPI quando aplicável. Registrar data, URLs e possíveis conflitos; indisponibilidade de uma fonte permanece pendência explícita. Domínio é opcional e não deve comandar a escolha; não reservar, comprar ou registrar nesta etapa.

Avaliar de 0–5, com pesos: distinção e risco de confusão 30%, relação com a proposta 25%, pronúncia/memória 20%, legibilidade no launcher/WBR 15%, expansão para outras entradas 10%. Conflito relevante, associação enganosa ou ilegibilidade eliminam o candidato, independentemente da média. Os pesos organizam a discussão e não substituem a escolha do usuário.

Não incorporar Samsung, Spotify ou marcas de terceiros ao nome. Não limitar o nome a “Player”, “Widget” ou “Watch”. Os títulos dos territórios abaixo são apenas referências de criação; não são nomes escolhidos nem candidatos com disponibilidade verificada.

## 5. Três territórios visuais distintos

| Território | Construção e ligação com o diferencial | Principal cuidado |
| --- | --- | --- |
| **Encaixe — recomendado** | Duas peças geométricas complementares, massas assimétricas e um recorte central próprio; representa o comando que se encaixa em diferentes formas de acesso | Não parecer peça de quebra-cabeça ou logotipo genérico de integração |
| **Cadência** | Traço contínuo com mudança de direção e corte ritmado; representa passagem do gesto à ação, com ritmo visual próprio | Não virar onda sonora, equalizador ou traço genérico de streaming |
| **Órbita** | Massa circular interrompida com ponto/corte deslocado; aproxima toque e controle no pulso, com silhueta distinta das outras duas | Não se confundir com fitness, sincronização ou o próprio anel de progresso |

Desenvolver as formas primeiro em preto e branco. Cada direção precisa funcionar por sua silhueta, não apenas por mudança de fonte, espessura ou paleta. Não condensar miniaturas de celular, relógio e rotina dentro do ícone: esses contextos serão explicados na apresentação da marca.

Para cada direção, propor uma cor de destaque e pares claros/escuros com contraste suficiente. Cor é apoio ao reconhecimento, não o único identificador. Usar tipografia de sistema na interface; eventual tipografia específica de logotipo deve ter origem/licença registradas, sem download de fontes em runtime. Não impor a paleta da marca aos widgets configurados.

## 6. Entregáveis visuais e escolha

1. **Prancha comparativa:** três direções com silhuetas diferentes e três nomes triados, indicando a combinação sugerida e permitindo trocar nome/símbolo. Mostrar proposta verbal e ligação concreta com os pilares da seção 2.
2. **Aplicações em escala:** ícone colorido e monocromático; 24/48 dp e ampliação; máscaras circular e arredondada; launcher; cabeçalho das configurações em claro/escuro; lista de widgets; três entradas Wear lado a lado.
3. **Comparação com referências:** painel de símbolos e nomes consultados, com fontes, para procurar semelhança de silhueta, iniciais, composição e combinação de cores. Usar referências para contraste, não como peças para copiar.
4. **Teste de compreensão:** apresentação breve, depois perguntar o que o app faz e como o acessaria. Conferir se o conjunto comunica comandos acessíveis por diferentes caminhos e se a forma é reconhecida em monocromia. Se viável, testar com 3–5 pessoas além do autor; amostra exploratória, sem alegação estatística.
5. **Refinamento:** escolher uma direção com o usuário, ajustar proporções/cores/nome e registrar a aprovação da versão exata. Apresentar a arte final novamente se houver mudança material.

Conceitos ou montagens devem ser identificados como tais. Antes da publicação do app, mostrar também renderizações nativas dos recursos Android efetivamente incorporados. Uma imagem de conceito não comprova aparência no launcher Samsung, LockStar ou WBR. Se um teste de compreensão ou acesso a pessoas não puder ocorrer, registrar o limite e usar a avaliação do usuário; não inventar validação.

## 7. Aplicação futura: mapa de arquivos e contratos

| Superfície | Pontos atuais de aplicação | Diretriz |
| --- | --- | --- |
| Nome/ajuda do celular | `app/src/main/res/values/strings.xml` e `values-pt-rBR/strings.xml` | Revisar nome, listener, instruções de Rotinas/LockStar e mensagens de backup em EN/PT-BR |
| Preview | `app/src/preview/res/values/strings.xml` e `values-pt-rBR/strings.xml` | Aplicar marca escolhida conservando “Preview” e isolamento da variante; distribuição oficial continua sendo o original |
| Ícone do celular | `app/src/main/AndroidManifest.xml`, `res/drawable/ic_app.xml` e novos recursos adaptive/mipmap a definir | Foreground/background vetoriais, camada monochrome, fallback e inspeção de máscaras; não basta substituir o vetor quadrado atual |
| Relógio/WBR | `wear/src/main/AndroidManifest.xml`, strings `values`/`values-en` e `drawable/ic_launcher_{previous,toggle,next}.xml` | Mesma família visual, nomes curtos com ação legível e símbolos funcionais diferentes; preservar três entradas launcher, sem uma quarta só para a marca |
| UI/diagnóstico | `SettingsUi.kt`, recursos de cores/estilos, `LocalDiagnostics.kt`, `WidgetWallpaperDiagnostic.kt` e nome sugerido em `BackupActivity.kt` | Rever marca visível e, se aprovado, o destaque da UI existente; manter navegação e comportamento. Texto de marca pode usar recursos, sem refatoração geral |
| Documentação/créditos | README, guias de uso atuais e `THIRD_PARTY_NOTICES.md` | Explicar a mudança, manter “anteriormente Media Actions” no anúncio/guia de transição e registrar origem/licenças dos novos ativos |

Seguir a [especificação Android de adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive): camadas de 108×108 dp, marca essencial na região segura central de 66×66 dp e versão monocromática reconhecível. Avaliar `roundIcon` conforme o desenho/host, não como substituto do adaptive. Ícones temáticos do launcher dependem do sistema/host e não corrigem a adaptação de cor dos widgets ao wallpaper.

**Congelar os contratos técnicos:** `applicationId`/namespace `dev.local.mediaactions`, certificado histórico, `.preview`, componentes instalados de Activities/listeners/services/providers, IDs dos shortcuts `next_track`/`previous_track`/`toggle_playback`, seus targets e URIs `mediaactions://run/{next,previous,toggle}`, comandos dos widgets e protocolo `/mediaactions/v1/`. Conservar chaves de preferências e formato `media-actions-settings`. Branding sozinho não exige novo schema de backup; se o combinado for implementado antes, preservar o schema vigente naquele momento.

Não usar substituição global de “Media Actions/mediaactions/MA”. Separar texto de apresentação de identificadores persistidos. Nome sugerido de arquivo exportado pode mudar; aceitação do backup continua baseada no conteúdo compatível. Não renomear repositório, caminhos históricos de APK ou classes por estética. Famílias de ícones e aparência salva dos widgets permanecem independentes da marca do launcher.

O companion Wear é opcional para celular/tablet e existe para expor as três ações ao WBR e controlar mídia do celular ou local conforme seleção/autorização atuais. A identidade não acrescenta configurações de binding próprias, outro pacote ou captura de teclas. Não confundir esse companion com a Preview de desenvolvimento.

## 8. Sequência e critérios de aceite

Executar por etapas: **posicionamento e triagem → três propostas visuais → escolha/refinamento → recursos nativos e textos → revisão/validação → entrega**. Reavaliar a base do repositório no início da implementação para respeitar contribuições paralelas. Pode-se executar a descoberta sem esperar o widget combinado; apresentar qualquer função apenas planejada com identificação explícita.

| Critério | Evidência necessária |
| --- | --- |
| Diferencial compreensível | Prancha relaciona os pilares da seção 2 ao nome/símbolo/mensagem; feedback do usuário e eventual teste exploratório registrados |
| Identidade distinta | Três propostas diferem em silhueta; nome final triado e comparação de similaridade documentada, com pendências visíveis |
| Ícone robusto | Inspeção colorida/monocromática, 24/48 dp, máscaras e claro/escuro; componentes Wear distinguíveis sem depender da cor |
| Texto consistente | Revisão EN/PT-BR do launcher, configurações, listener, widgets, ajuda, backup, diagnósticos e WBR, sem truncamento relevante |
| Atualização do mesmo app | APK comparado com anterior: package/certificado, componentes, shortcuts, endpoints e permissões preservados; só mudanças visuais esperadas |
| Continuidade | Upgrade sem desinstalar preserva configurações/widgets/acesso concedido; rotinas, atalhos e bindings existentes continuam acionando os mesmos componentes; backup anterior importável |
| Sem trabalho novo em segundo plano | Recursos estáticos locais, sem rede, serviço, timer ou animação contínua adicionados pela marca; não alegar economia de bateria não medida |

Reutilizar `VariantIsolationTest`, `WearLauncherTest`, `WearLauncherVisualTest` e testes de backup/widgets existentes. Atualizar expectativas de labels e imagens quando necessário sem retirar assertivas de componentes/ações. Validar recursos e integração em API 33/36; compilar/inspecionar APKs das variantes afetadas. Antes de distribuir, cumprir a cadeia completa de [CI](../CI.md), inspeção e assinatura de [STABLE_RELEASE](../STABLE_RELEASE.md) e concluir self-audit do diff. A atualização deste plano é somente documental e dispensa build/emulador.

Registrar evidência física em S25/Tab S10+ e Watch 8 nos contextos realmente verificados. Launcher, Rotinas e WBR podem manter nome/ícone em cache: documentar comportamento observado, sem exigir limpar dados, recriar vínculos ou reinstalar por padrão. Validar o Wear somente se seu APK for atualizado e não anunciar como testado um host inacessível. Bloqueio de CI por faturamento continua uma pendência de infraestrutura até nova verificação; usar as alternativas locais já documentadas, sem declarar run bloqueado aprovado.

## 9. Entrega, retorno e pendências

Entregar guia curto com nome, símbolo, paleta, uso monocromático, tamanhos/restrições, exemplos e créditos; manter fontes editáveis e exports em diretório de identidade a definir. Publicar notas explicando que é atualização do mesmo app, sem necessidade de reinstalação ou migração dos ajustes. Manter links históricos de download funcionais e distinguir mockups de evidências reais.

Se a identidade aprovada tiver problema após entrega, corrigir os recursos numa versão de código maior e mesma assinatura, preservando dados e componentes. Reversão visual não implica downgrade, exclusão de preferências ou reinstalação. Uma entrega posterior deve referenciar o commit exato da arte e do app validado.

**Pendências antes da identidade definitiva:** shortlist e triagem dos nomes; comparação visual dos símbolos; escolha do usuário; recursos nativos; testes físicos possíveis. A pesquisa inicial não autoriza promessa de exclusividade global, compatibilidade integral ou superioridade de consumo. Não há nome, cor ou logotipo final selecionado neste plano.

## 10. Revisão do plano

**Veredito: APROVADO**, conforme a skill execution-plan-review, com leitura independente do documento e dos contratos técnicos citados. Sem novos achados. A aprovação se refere à execução das etapas propostas, não à escolha de uma marca definitiva ou à compatibilidade física já demonstrada.

Riscos residuais: disponibilidade e similaridade dos nomes/símbolos ainda por pesquisar na etapa de criação; preferência visual do usuário; caches e continuidade de nome/ícone nos hosts após upgrade. Esses itens permanecem critérios de escolha e validação, não conclusões desta revisão.

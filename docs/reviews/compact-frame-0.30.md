# Self-audit — moldura compacta, 0.30

Base 74fd6c0e8e3a4a07697af931d69590962d514940 (0.29). Pedido: moldura menor verticalmente apesar da área reservada pelo LockStar. Modo FILL preserva configurações existentes; modo COMPACT, altura desejada56–160dp e padrão64dp, controla apenas a moldura e mantém controles compactos em48dp onde há espaço. Volume mantém eixo do host e precisa de duas áreas de toque. Nenhuma mudança em timers, comandos, permissões ou integrações privilegiadas.

## Rodada 1 — achados antes dos ajustes

- **Médio — layouts/compatibilidade:** a primeira implementação colocou a linha de controles dentro de novo FrameLayout. Embora o desenho permanecesse equivalente, mudava o referencial de coordenadas e quebrou regressão de padding. Manter controles no pai original e usar uma camada decorativa irmã, não interativa, para a moldura compacta. Revalidar padding e reaplicação Fill→Compact→Fill.
- **Médio — altura/limites:** altura fixa pode ser cortada pelo host, especialmente sem dimensões ou com padding negativo. Usar WRAP_CONTENT com minimumHeight nativo e limitar a altura conhecida à área física; preservar min48dp quando há espaço. Padding negativo não pode fazer a moldura ultrapassar os limites conhecidos do host.
- **Médio — backup:** novos campos não podem ser omitidos silenciosamente por importadores antigos. Exportar formato2 com campos obrigatórios e continuar importando formato1 para Fill/64dp. O app0.29 recusa v2 por versão; documentar e testar ambos.

Validação inicial: 59 testes focados, somente a regressão de padding acima falhou. Novo editor/estado e codec passaram em API33/36; corrigir estrutura e rodar novamente. A declaração de altura não reduz a área que o launcher/LockStar reserva. Não prometer alteração do timeout físico nem economia de bateria.

## Rodada 2 — geometria, persistência e prévia

Após tornar a moldura uma camada irmã decorativa, os 59 testes focados passaram. Em seguida passaram os testes visuais nativos adicionais, com pixels do contorno contidos na faixa central e remoção/reaplicação da borda. Comparação Fill/Compact64/Compact96 e editor real exibidos ao usuário antes de publicar; são Android/Skia, não montagem do screenshot nem captura em Galaxy.

Releitura de geometry/layouts, renderer, editor, draft/store, codec e caches existentes. Modo/altura integram WidgetAppearance e portanto suas chaves de dedup; o cache não fica congelado ao mudar a moldura. Aparência antiga e backup v1 usam Fill; exportação v2 preserva os novos campos e importador aceita v1/v2. Sem achado bloqueante/alto confirmado nesta rodada. Falta suíte completa/lint/artefato e revisão final.

Durante a rodada 2 o usuário acrescentou ajuste de arredondamento. Incorporar seis escolhas (Automático/0/8/16/24/32dp), independentes da altura e válidas nos dois modos; AUTOMATIC mantém16dp no Fill e cápsula no Compact. Drawables nativos, sem bitmap nem animação. Incluir em store/draft e no formato2 ainda não publicado; formato1 continua migrando para automático. Revalidar pixels distintos, edição e persistência antes do veredito.

## Rodada 3 — prévia global e validação final

**Médio — WidgetAppearanceSection/prévia sem host:** o exemplo antigo de56dp limitava visualmente a altura compacta, fazendo ajustes maiores parecerem não funcionar no editor do padrão global. Quando Compacta está ativa e não há dimensões do host, usar exemplo fixo de160dp para demonstrar a folga; dimensões reais continuam com o limite informado pelo host. Testar alteração64→96 na prévia sem salvar. A suíte completa com cantos já passou; repetir após esse ajuste final.

### Veredito final — APROVADO

Após o ajuste da prévia, BUILD SUCCESSFUL: **617 testes Debug** (app580, Wear15, shared22) e **580 testes do app Release**, 1.197 execuções, sem falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou. São execuções em duas variantes, não1.197 testes únicos. A prévia global64→96 foi testada sem gravar o rascunho; estado do editor/persistência, cenários legados, reaplicação, backupv1/v2, pixels/cantos distintos e áreas de toque em espaço viável passaram.

Releitura completa dos arquivos alterados e dos caminhos de configuração, importação e dedup. Os médios identificados foram corrigidos. Nenhum bloqueante/alto/médio/baixo de código pendente no escopo; bug sweep sem defeito adicional confirmado. Três rodadas pelo próprio agente, sem subagentes. UI sem integração privilegiada: skill de privilégio Android não se aplica a esta mudança.

Artefato original Release0.30/code30, não depurável, certificado idêntico ao0.29, verificado com apksigner/zipalign e manifest. Sem permissão, dependência, timer ou alteração de comandos nova. Imagens nativas exibidas antes de publicar. Configurações antigas permanecem Fill/Automático até escolha explícita; exportação v2 e importação v1/v2 verificadas.

Limites conhecidos: a moldura não controla a área reservada pelo host; espaço físico insuficiente pode limitar altura/toque, e a moldura precisa envolver os controles e espaçamentos configurados. Volume vertical distribuído automaticamente por toda a altura pode exigir moldura alta; usar espaçamento manual para aproximar os controles. A matriz de encaixe e recuperação em Samsung/LockStar físico e bateria continuam não medidas pelo agente. Adaptação de cores ao wallpaper permanece apenas planejada.

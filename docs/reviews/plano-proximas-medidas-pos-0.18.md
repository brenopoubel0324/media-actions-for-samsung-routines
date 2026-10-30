# Review do plano de próximas medidas após a 0.18

Skill utilizada: breno-review-audit:execution-plan-review. Plano: [proximas-medidas-pos-review-0.18.md](../planos/proximas-medidas-pos-review-0.18.md). Base de código: `dd2a0367a29790a337dab42cf4fc9bd043feef84`, phone 0.18/Wear 0.8. Revisão do plano contra o código e contratos atuais; não é uma validação física das integrações.

## Achados antes dos ajustes

- **Médio — intervalo (etapa 4 inicial, etapa 2 atual) / WidgetPlaybackObserver.updatePulse:** o critério de tela interativa não comprova que o widget está visível, especialmente quando o telefone está desbloqueado em outro app. O plano deve registrar esse limite e incluí-lo na medição, evitando anunciar agendamento restrito à visibilidade do LockStar.
- **Baixo — intervalo (etapa 4 inicial, etapa 2 atual) / seletor global:** Salvar/Cancelar estão definidos, mas falta explicitar recriação da tela com seleção ainda não salva. Acrescentar aceite de preservação do rascunho e de ausência de gravação por recriação/cancelamento.

Nenhum achado bloqueante/alto. Os dois pontos devem ser incorporados ao plano antes do fechamento desta review.

## Contratos conferidos

- `WidgetStyleStore` guarda `has_override` na aparência por ID, faz fallback para o padrão e dispõe de remove por instância. `WidgetLocationStore` é independente; restaurar aparência não deve removê-lo.
- `WidgetAppearanceSection` valida campos e pertencimento do ID antes de persistir. O original/rascunho e texto bruto da 0.18 são estado temporário da Activity, não novas preferências visuais.
- `WidgetPlaybackObserver` tem um pulse de 5.000 ms, depende de PLAYING/tela interativa/progresso calculável/widget ativado e recebe notificação de mudança das configurações. O intervalo global proposto aproveita esse agendamento, sem acrescentar timers por widget.
- `SpotifyPlaybackResumer` persiste status terminal em `spotify_resume/status`, mantém tentativa ativa limitada e evita sucesso sem PLAYING. Timestamp opcional deve ser atômico com o status e não alterar deadlines monotônicos.
- `WearActionReceiver` retorna `LinkResult`; explicar melhor um resultado exige preservar contexto dos probes/destino internamente, não inferir detalhes perdidos por um NO_SESSION final.
- As três Activities Wear e o protocolo v1 podem permanecer compatíveis; novos diagnósticos não justificam mudar comando, prazo, retry ou política de destino.

Conferidos o código citado, testes próximos de configuração/local/rascunho/progresso/Spotify/Wear e documentação de UX, identificação e progresso. Não se pressupõe uma API Samsung privada nem que declarar reconfiguração force o menu Editar.

## Veredito — APROVADO para execução incremental

Os achados médio e baixo foram incorporados ao intervalo (agora etapa 2): limite de visibilidade explícito e aceite para recriação/cancelamento do seletor. Não restam achados bloqueantes/altos nem mudanças obrigatórias no plano. Escopo, sequência, preservação de dados, validação, publicação e reversão estão definidos.

Validação mínima para implementar: os testes e critérios por etapa, a suíte/lint/build indicados, imagens de UI antes de publicar e self-audit após cada entrega. Nesta alteração somente documental, foram conferidos links relativos e `git diff --check`; a suíte do app não foi reexecutada. Os 394 testes citados pertencem à validação da 0.18.

Riscos e pendências: os aparelhos e versões exatas ainda precisam ser consultados durante a etapa física; Spotify pode rejeitar a retomada, LockStar pode omitir categoria/menu e acesso local no Watch pode depender do firmware. O ajuste adicional de lockscreen continua sem especificação. Aprovar a execução do plano não encerra essas pendências nem comprova compatibilidade física. As entregas independentes podem prosseguir sem esperar por elas.

## Revisão solicitada — consumo energético como prioridade

Achados antes de atualizar o plano:

- **Médio — ordem e dependências:** a medição aparece depois das melhorias de UX e pode ficar adiada, contrariando a prioridade energética agora explicitada pelo usuário. Trazer a linha de base e as proteções para o início, seguidas pelo intervalo e pela otimização condicionada a evidência.
- **Médio — aceite energético transversal:** faltam critérios comuns de comparação, distinção entre custo dos controles e reprodução de áudio, e limites para instrumentação/diagnóstico. Definir controle com progresso desligado, condições comparáveis, resultado inconclusivo quando necessário, ausência de monitoramento permanente e orçamento de timers também para o Wear.

Conferência técnica focada: o observador já pausa os ticks quando seus requisitos não são satisfeitos e o receiver Wear usa execução limitada por invocação. O plano deve conservar essas propriedades, sem anunciá-las como economia nova nem transformar contagem de ticks em percentual de bateria.

**Reavaliação — APROVADO para execução incremental.** Os dois achados médios foram resolvidos: a nova ordem começa com linha de base, intervalo e eficiência; requisitos energéticos passam a valer para todas as entregas. O plano distingue simulação de medição física, controla a carga de áudio/tela/rede, prevê repetições e resultado inconclusivo, e limita o custo da própria instrumentação. A comparação 15/30 s depende da etapa que disponibiliza essas opções, sem dependência circular.

Nenhum bloqueante/alto ou mudança obrigatória pendente após a atualização. Foram conferidos ordem/referências/links e `git diff --check`. Alteração somente em documentação; código/APK não foram alterados, a suíte do app não foi reexecutada e não houve medição de energia nesta revisão. A economia real e a visibilidade no LockStar permanecem sem comprovação; o plano registra esses limites e permite avançar nas partes independentes sem atribuir ganho de bateria a contagens simuladas.

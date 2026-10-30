# Review do plano de próximas medidas após a 0.18

Skill utilizada: breno-review-audit:execution-plan-review. Plano: [proximas-medidas-pos-review-0.18.md](../planos/proximas-medidas-pos-review-0.18.md). Base de código: `dd2a0367a29790a337dab42cf4fc9bd043feef84`, phone 0.18/Wear 0.8. Revisão do plano contra o código e contratos atuais; não é uma validação física das integrações.

## Achados antes dos ajustes

- **Médio — etapa 4 / WidgetPlaybackObserver.updatePulse:** o critério de tela interativa não comprova que o widget está visível, especialmente quando o telefone está desbloqueado em outro app. O plano deve registrar esse limite e incluí-lo na medição, evitando anunciar agendamento restrito à visibilidade do LockStar.
- **Baixo — etapa 4 / seletor global:** Salvar/Cancelar estão definidos, mas falta explicitar recriação da tela com seleção ainda não salva. Acrescentar aceite de preservação do rascunho e de ausência de gravação por recriação/cancelamento.

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

Os achados médio e baixo foram incorporados à etapa 4: limite de visibilidade explícito e aceite para recriação/cancelamento do seletor. Não restam achados bloqueantes/altos nem mudanças obrigatórias no plano. Escopo, sequência, preservação de dados, validação, publicação e reversão estão definidos.

Validação mínima para implementar: os testes e critérios por etapa, a suíte/lint/build indicados, imagens de UI antes de publicar e self-audit após cada entrega. Nesta alteração somente documental, foram conferidos links relativos e `git diff --check`; a suíte do app não foi reexecutada. Os 394 testes citados pertencem à validação da 0.18.

Riscos e pendências: os aparelhos e versões exatas ainda precisam ser consultados durante a etapa física; Spotify pode rejeitar a retomada, LockStar pode omitir categoria/menu e acesso local no Watch pode depender do firmware. O ajuste adicional de lockscreen continua sem especificação. Aprovar a execução do plano não encerra essas pendências nem comprova compatibilidade física. As entregas independentes podem prosseguir sem esperar por elas.

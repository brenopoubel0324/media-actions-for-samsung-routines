# Self-audit — orientação dinâmica do volume, 0.25

Base b858a8a/0.24. Pedido: vertical e ajuste dinâmico pelo tamanho. Skill self-audit-loop, sem subagentes. Sem preferência/migração nova: volume fica vertical quando o retângulo informado é mais alto que largo; quadrado e tamanho ausente continuam horizontais. + acima/− abaixo; mídia de três botões permanece horizontal.

## Rodada 1 — revisão do desenho antes da validação

- **Médio — geometria:** trocar apenas largura/altura também trocaria o significado dos paddings e perderia padding vertical negativo na normalização horizontal. Manter padding nos eixos físicos e calcular a distribuição no eixo escolhido. Implementado em helper específico do volume.
- **Médio — host/reaplicação:** orientação também muda pesos e ordem visual. Usar layouts distintos por orientação permite ao host reinflar quando muda o layoutId; IDs de ação e PendingIntents permanecem estáveis. Não mudar o volume durante resize/preview.

Um único provider/widget, sem timer ou consulta de sessão nova. Exige que o host informe tamanhos/opções; sem isso o fallback é horizontal. Dois alvos de 48dp quando há espaço para 96dp no eixo de distribuição; host que force áreas menores pode limitar/cortar. Validar API 33/36, mudança H→V→H, paddings negativos, limites, variantes responsivas e fallback.

## Rodada 2 — testes focados

A troca de eixo H→V→H, ordem, paddings e persistência passou em API 33/36. O teste de NaN construía SizeF inválido, que o Android rejeita no construtor antes da chamada. Corrigido o fixture e evitada a construção intermediária de SizeF no helper; parâmetros float inválidos seguem o fallback horizontal existente. Sem novo bloqueante/alto confirmado. A captura é Android/Skia, não um Tab físico.

A cópia inglesa da distribuição automática dizia “full width”; ajustada para “available space” para descrever corretamente ambos os eixos. Não altera valores salvos. A prévia continua limitada à área de demonstração do editor; o diagnóstico é calculado sobre o primeiro tamanho informado, não é ACK do desenho no host.

## Rodada 3 — APROVADO para entrega experimental

Nenhum bloqueante/alto/médio pendente no escopo. Paddings preservados nos eixos físicos; orientação muda por layoutId com ações/ID da instância estáveis. A cópia inglesa genérica foi corrigida e validada novamente.

**504 testes Debug** (app 467, Wear 15, shared 22), build phone/Wear e lint dos três módulos passaram. **27 testes Preview** de volume, orientação e isolamento também passaram após a última alteração de recursos, com assemblePreview. Zero falhas/erros/pulos; `git diff --check` passou. Casos incluem H→V→H no provider, + acima de −, permanência de estilo e PendingIntent, alvos de 48dp em limites válidos, padding vertical negativo, quadrado/fallback, valores inválidos e variantes de tamanhos. Mídia de três botões permanece horizontal.

Imagem nativa Android/Skia foi mostrada antes da publicação; não é captura de hardware. Nenhum novo sensor, timer, chamada de áudio em resize ou alteração de storage/comandos foi introduzido. Package original e certificado histórico conferidos no empacotamento; Wear continua 0.9. Bug sweep sem defeito novo confirmado no escopo. Tab S10+/launcher/LockStar e energia físicos seguem não testados/não medidos. Revisão do próprio agente em três rodadas.

# Self-audit — bordas independentes, 0.10-experimental

Skill: breno-review-audit:self-audit-loop. Autoavaliação do executor, sem outro agente.

Base: 6f579e7df23f14d1e99fdbc0b4ee97e0165e77c6. Escopo: borda circular dos botões e moldura do widget como grupos separados, com ativação, largura/cor próprias e uso simultâneo. Preservar valores publicados na 0.9, transparência, geometria e comportamento de mídia.

## Rodada 1 — achados antes das correções

- **Baixo — testes nativos de atualização/alpha:** o teste de reapply desenhava antes da passagem de layout solicitada pela mudança de background, e o teste de opacidade exigia alpha exato em um pixel da borda curva suavizada. Correção: simular a passagem de layout do host e distinguir alpha nominal da cobertura antialias da curva, mantendo validação de transparência, cores e ausência de tint duplicado.
- **Baixo — imagem das configurações:** o campo inferior fora da área inicial da janela não era capturado por inteiro. Correção: renderizar cada seção real em uma janela visível e compor as duas renderizações com identificação clara, sem simular captura de host Samsung.

Nenhum bloqueante/alto de código identificado nesta rodada. Releitura e resultados finais serão registrados depois dos checks.

## Ampliação de escopo durante a execução

O usuário pediu padding horizontal/vertical e esclareceu que se aplica **somente ao widget inteiro**. Acrescentados controles independentes das bordas, preservação do rascunho e cálculo do padding efetivo limitado pelo tamanho do host. Nenhum padding próprio dos botões foi adicionado. A aprovação final considera esse escopo completo.

## Rodada 2 — escopo completo e clareza

A revisão passou a incluir o padding horizontal/vertical somente do widget. Os testes confirmaram limites por dimensão, toque mínimo, estado por instância e uso sem bordas.

- **Baixo — descrições de prévia/formato:** identificar o padding como efetivo na prévia, pois um host sem dimensões usa amostra no editor e fallback conservador no widget. Evitar prometer círculos em todo host, já que as bordas se adaptam às áreas dos botões. Correção: rótulos mais precisos e documentação desse fallback.

Nenhum bloqueante/alto encontrado. As duas configurações de borda continuam independentes; padding não foi adicionado aos botões.

## Rodada 3 — APROVADO

Revisão final do diff e dos arquivos novos no escopo completo. Os três achados baixos das rodadas anteriores foram corrigidos; nenhum achado aberto, bloqueante, alto ou médio.

Evidência: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain` terminou com BUILD SUCCESSFUL; **270 testes** (app 239, wear 9, shared 22), sem falhas, erros ou pulos. Lint dos três módulos sem apontamentos e `git diff --check` aprovado. A suíte inclui renderização nativa, reapply com passagem de layout, independência das cores/toggles, migração das preferências 0.9, rascunhos, validação sem salvamento parcial e limites H/V em 864 combinações de geometria.

O padding pertence somente ao widget inteiro e funciona com as bordas desligadas. Os novos controles não alteram mídia, atalhos, permissões ou código Wear. Valores legados da moldura continuam válidos; bordas de botão e padding começam desligados/zero.

Limites residuais: host Samsung/LockStar físico e desempenho de FPS/GPU/bateria não foram medidos. A amostra do editor não garante dimensões reais do host; sem dimensões o widget usa fallback conservador, com padding efetivo zero. A presença do menu externo Editar continua sob controle do host.

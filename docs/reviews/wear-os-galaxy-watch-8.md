# Revisão do plano — atalhos por botões no Galaxy Watch 8

## Escopo vigente

O usuário deseja somente associar botões/gestos aos atalhos de mídia. A proposta anterior de tela de reprodução, seletor visual, ajuste de aparência, Tile e complicação foi retirada. O [plano vigente](../planos/wear-os-galaxy-watch-8.md) substitui aquela proposta. A atualização do aplicativo no GitHub permanece parte da entrega.

## Achados antes da revisão do plano

1. **Alto — interface fora do pedido.** O plano anterior destinava fases a uma tela de três controles e opções visuais que o usuário não quer. Correção: retirar esses entregáveis e limitar a interface à configuração indispensável para escolher ação/autorizar acesso, se exigida pelo sistema.
2. **Alto — binding global não demonstrado.** A documentação Samsung confirma abertura de app por duplo Home, não três atalhos Android independentes. O tratamento `onKeyDown` do Wear funciona no app em primeiro plano e não fornece um remapeador global. Correção: testar primeiro a descoberta das entradas pelo seletor real e os gestos disponíveis; não prometer três bindings nem capturar globalmente os botões.
3. **Alto — controle de players locais não demonstrado.** Autorização do celular não concede acesso às sessões de outros aplicativos no Watch. Correção: validar acesso no Watch 8 antes de habilitar o destino local.
4. **Médio — ciclo de vida do atalho.** A Activity atual usa Theme.NoDisplay e termina em onCreate. Reutilizá-la para iniciar trabalho assíncrono sem componente que mantenha a execução pode perder comandos remotos. Correção: execução breve com ciclo de vida e timeout próprios, sem uma tela de player ou serviço permanente.
5. **Alto — duplicação ou destino errado.** Repetir após timeout ou trocar de aparelho depois do envio pode executar uma ação duas vezes. Correção: destino fixo por invocação, identificação/deduplicação/validade de comandos e nenhum retry/fallback após envio.

## Contexto e fontes conferidos antes da implementação

- Repositório `/workspace/media-actions`, branch `preview/small-icons-families`; preservar as alterações locais da prévia 0.7.
- Só existe `:app`; lidos manifesto, shortcuts.xml, ActionActivity, seleção de sessão, dispatcher, gateway Android e Gradle.
- O app atual expõe três atalhos por URIs exatas, sem remapear botões globalmente.
- A documentação de botões Samsung e Wear OS foi consultada; links e limites estão no plano.
- Nenhum Watch ou celular está conectado ao ambiente. O usuário foi consultado sobre configuração nativa versus remapeador já utilizado; o plano não presume um remapeador instalado.

## Veredito

**APROVADO para executar o diagnóstico e avançar conforme seus resultados.** As premissas não verificadas viraram critérios explícitos de saída; não restam achados bloqueantes ou altos sem tratamento no plano revisado.

Isso não confirma que os três atalhos já podem ser vinculados nativamente, nem que players locais do relógio já podem ser controlados. Essas são as duas validações físicas obrigatórias. Se o binding nativo não atender, documentar o limite e avaliar o remapeador escolhido pelo usuário antes de adicionar dependências ou permissões.

## Validação mínima e entrega

- Entradas descobertas pelo seletor real e binding executado fora do app, com outro app ativo e com tela apagada/bloqueada, registrando os limites encontrados.
- Exatamente uma ação por gesto; nenhum comando em invocação inválida/recriação, mensagem duplicada/expirada ou sessão alterada.
- Controle do celular e controle local do Watch testados separadamente; conexão/ACK perdido não provoca segundo envio.
- Regressão dos atalhos e widget do celular, testes/build/lint quando houver implementação e self-audit ao terminar o código.
- Publicação de código, README, release/APKs e links no GitHub; melhorias prontas do celular podem sair independentemente do diagnóstico Wear. Apresentar imagens das mudanças visuais do celular antes de publicar.

Nesta etapa só foram revisados documentos. O risco residual principal é o suporte real do firmware aos bindings pretendidos e à autorização das sessões locais.

## Atualização — remapeador escolhido

O usuário especificou Wear Button Remapper. O APK 1.06 do autor foi inspecionado e confirma enumeração MAIN/LAUNCHER por Activity e lançamento pelo componente completo. O plano foi ajustado para três Activities concretas em um APK. A incerteza sobre três bindings nativos deixou de ser requisito; permanece a validação física da versão instalada do WBR e do acesso local às sessões. A execução está documentada na [revisão de implementação](wear-button-remapper-implementation.md).

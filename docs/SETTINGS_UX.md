# Configurações organizadas — 0.16 experimental

A tela inicial reúne Conexão, Seu widget e Testar controles. Ajuda/diagnóstico ficam recolhidos. O editor completo não fica mais misturado aos testes de mídia.

- **Editar widget instalado:** mantém a seleção por instância, incluindo escolha quando há vários. O host externo continua abrindo diretamente a Activity de configuração validada.
- **Visual padrão:** nova tela privada para novos widgets e os que não têm ajuste individual. Salvar o padrão não sobrescreve instâncias com configuração própria.

## Editor

Prévia fixa no topo, com estados Tocando, Pausado e Sem sessão. Esses controles são demonstrações; não enviam comandos de mídia. Medidas mostra os valores efetivos e limitações do tamanho sem ocupar espaço o tempo todo. A prévia recolhe em janela curta/com teclado, mantendo Salvar acessível.

Quatro grupos recolhíveis: Ícones, Espaçamento, Bordas e Reprodução e toque. Apenas um fica aberto por vez. Detalhes de borda aparecem ao ativá-la; borda só no Play/Pause fica logo abaixo da ativação dos botões. O espaço manual entre botões fica oculto durante a distribuição automática.

**Mostrar progresso da faixa** é uma chave independente em Reprodução e toque. Pode ligar/desligar mesmo com todas as bordas desligadas. Ao salvar, usa o mesmo campo progressRing existente: ligado mostra o indicador só durante reprodução confirmada; desligado remove o indicador. Pausa/ausência de sessão continuam seguindo as regras da 0.14. Intervalo continua em cinco segundos.

Campos exatos, sliders e passos de uma unidade continuam disponíveis. Padding negativo permanece somente vertical. Salvar valida todos os grupos e abre o grupo do campo inválido, sem salvar parcialmente. Cancelar descarta o rascunho. Recriação preserva valores ainda não salvos, grupo aberto e estado de demonstração.

## Aparência e idioma

Superfícies com contraste, botões com áreas mínimas de toque e altura flexível, modo claro/escuro conforme o sistema e tradução para português brasileiro. O rodapé pode crescer com fontes maiores; seções expõem ações de expandir/recolher para acessibilidade. Sem dependência nova de UI.

As prévias publicadas são renderizações reais Android/Skia em pt-BR, incluindo modo escuro e progresso ligado/desligado. Não são capturas do One UI/LockStar físico. A aparência do widget e os mecanismos de mídia/Spotify/Wear permanecem iguais.

[Prévia geral](../downloads/v0.16-experimental/configuracoes-organizadas.png) · [Chave de progresso](../downloads/v0.16-experimental/configuracoes-progresso-opcoes.png) · [Modo escuro](../downloads/v0.16-experimental/configuracoes-escuro.png) · [Review](reviews/settings-ux-0.16.md)

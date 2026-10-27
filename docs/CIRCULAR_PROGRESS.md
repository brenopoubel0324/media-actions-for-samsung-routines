# Centro com progresso e padding vertical — 0.12 experimental

Em Widget settings, ative **Borders around each button** e **Border only around Play/Pause** para manter a borda estática somente no centro, com Anterior/Próxima livres. A moldura externa continua independente. Nenhuma preferência antiga muda automaticamente.

Ative **Track progress around Play/Pause** para usar o indicador circular no centro: trecho concluído branco, trilho branco translúcido e marcador cinza escuro com contorno branco. O indicador substitui a borda estática central; as bordas laterais continuam obedecendo à opção de somente centro. Ícones, tamanho, sombra, gaps, feedback de toque e moldura externa permanecem configuráveis. A espessura do grupo dos botões também ajusta a escala do traço do indicador, com mínimo visual próprio. As cores do progresso são fixas nesta versão.

O progresso é a posição/duração da sessão selecionada, com projeção pelo relógio monotônico e velocidade anunciada pelo player. Não lê nem armazena título, artista ou capa. Atualiza em callbacks de estado, busca, duração e troca de sessão, e aproximadamente a cada **5 segundos** enquanto toca, há widget com indicador ativado e a tela está interativa. Não usa wake lock, alarmes ou serviço novo. Tela apagada/AOD pausa os ticks; pausa da mídia ou opção desativada também. Não há promessa de animação contínua ou benchmark de bateria/FPS.

Duração desconhecida, acesso indisponível ou sessão ausente mostram apenas o trilho, sem fabricar percentual. Pausa mantém a posição informada. O indicador não permite arrastar para buscar. A prévia do editor usa progresso de demonstração identificado; o widget real usa a sessão.

## Padding vertical negativo

**Vertical: −24 a 144 dp**, com campo numérico assinado e passos de 1 dp. Aplica-se simetricamente a cima/baixo do widget inteiro. **Horizontal: 0 a 144 dp**, como antes, sem valores negativos. Não há padding próprio dos botões.

Negativo permite expandir os controles na direção vertical; não é deslocamento do widget nem remoção da margem do LockStar. O grupo continua centralizado e limitado pela largura disponível. O host pode cortar a parte que ultrapassa sua área. Em widgets muito estreitos a largura pode impedir crescimento adicional. Sem dimensões do host, usa-se padding zero conservador. Padding positivo continua limitado para preservar a linha mínima de áreas de toque quando o host comporta o tamanho mínimo.

![Prévia nativa](../downloads/v0.12-experimental/progresso-circular.png)

A imagem compara borda central, progresso de demonstração de 25%/70% e padding vertical positivo/zero/negativo. Painéis azuis são o fundo de exemplo; o widget permanece transparente. Renderização Android/Skia, sem captura de host Samsung real.

Instale o APK phone como atualização. Mesmo applicationId/certificado, preferências anteriores preservadas. Companion Wear 0.8 e protocolo de comandos não mudaram. O fallback Spotify da 0.11 continua igual.

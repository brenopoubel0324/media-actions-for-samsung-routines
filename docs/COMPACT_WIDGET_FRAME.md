A partir da **0.32**, há também [Justa aos botões](FITTED_WIDGET_FRAME.md), que aproxima o contorno do desenho dos ícones nos dois eixos sem diminuir a área de toque. O guia abaixo registra os modos lançados na0.30; o backup atual é v4.

# Moldura compacta e arredondamento — 0.30

Em **Editar widget instalado → Bordas**, ative a moldura e escolha **Formato da moldura → Compacta**. Ajuste a altura entre56 e160dp (padrão64) e o arredondamento. Salve para aplicar. O editor mantém rascunho/prévia; Cancelar não grava.

**Preencher widget** conserva o desenho anterior, com moldura no limite externo. **Compacta** desenha uma camada decorativa central, sem preencher toda a altura reservada pelo host. O fundo permanece transparente. A altura adiciona folga ao redor dos controles compactos, sem aumentar seus botões. O launcher ou LockStar continua decidindo a área reservada e os passos de redimensionamento; esta opção não reduz essa área nem muda o timeout da tela.

Arredondamento independente: Automático, Reto(0dp), Suave(8dp), Médio(16dp), Arredondado(24dp) e Bem arredondado(32dp). Automático mantém os cantos16dp em Preencher e usa cápsula em Compacta. O desenho nativo limita os raios aos limites físicos da moldura. Vale para moldura de mídia e volume; bordas individuais dos botões mantêm suas configurações próprias.

Controles compactos mantêm áreas de48dp quando há espaço. Ícones e espaçamento continuam editáveis. Comparado ao modo antigo, ativar Compacta pode reduzir os ícones porque deixa de ampliar os botões com a altura do host; o ajuste do tamanho dos ícones continua disponível. Alterar apenas a altura compacta não altera tamanho/posição dos botões. Volume preserva a orientação determinada pelo retângulo do host; na vertical precisa de espaço para dois botões e pode ficar mais alto que a altura desejada. Espessuras grandes também exigem folga para o contorno. Se o volume vertical estiver distribuído automaticamente por toda a altura, a moldura precisa envolver essa distribuição; escolha espaçamento manual para aproximar os controles. Hosts que forcem áreas menores podem limitar moldura e toque.

A moldura compacta usa WRAP_CONTENT e altura mínima nativa, limitada ao espaço do pai, com clamp adicional quando o tamanho físico é conhecido. Sem tamanho informado, a medição nativa respeita o limite do host e os controles usam fallback conservador. Padding externo permanece nos eixos físicos; padding vertical negativo não expande a moldura além do tamanho físico conhecido. Nos dois modos, reapply atualiza/limpa corretamente a camada anterior.

Os campos pertencem ao padrão global ou à aparência individual conforme o editor existente. Configurações antigas continuam em Preencher; não há ativação automática nos widgets instalados. Store e Bundle sem os novos campos usam Fill/64/Automático. A igualdade de WidgetAppearance inclui os campos novos, invalidando as chaves de apresentação e opções quando há mudança.

Backup **formato2** preserva modo, altura e cantos; importa também arquivos **formato1**, mantendo o visual legado. Apps0.27–0.29 recusam formato2 e não devem ser usados para restaurar esses arquivos. O APK Preview0.27 de migração continua exportando formato1, aceito na0.30. Bibliotecas v1 já salvas no app continuam legíveis. O formato2 ainda não havia sido distribuído antes desta versão.

Implementação com drawables nativos transparentes com stroke; presets de cantos são recursos estáticos, sem bitmap de moldura, shader animado ou blur. Nenhum timer, callback recorrente, chamada privilegiada, serviço ou permissão nova. Ícones/progresso continuam no caminho existente. Não houve medição física de bateria nem validação de encaixe em LockStar pelo agente.

![Comparação nativa](../downloads/v0.30/moldura-compacta-comparacao.png)
![Cantos nativos](../downloads/v0.30/moldura-arredondamento.png)
![Editor real](../downloads/v0.30/moldura-compacta-configuracao.png)

As imagens são Android/Skia com tamanho controlado, não capturas do aparelho do usuário. Testar no host real a posição, reaplicação, toque, altura limitada e volume H/V. [Review](reviews/compact-frame-0.30.md).

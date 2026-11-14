# Widget Reprodução e volume

Um terceiro tipo de widget reúne **− / Play/Pause / +** no mesmo aplicativo. Os widgets atuais de controles de mídia e de volume continuam disponíveis; a atualização não converte suas instâncias nem altera sua aparência salva.

## Adicionar e editar

1. Na lista de widgets do launcher, escolha **Media Actions → Reprodução e volume**. No LockStar, use a área de adição de widgets quando essa opção estiver disponível.
2. Reserve espaço para os três controles. Redimensionar a mesma instância muda a orientação automaticamente.
3. Para personalizar, abra **Editar widget instalado** no app e escolha a instância pelo local, número e dimensões. O host também pode oferecer sua própria entrada de edição externa; a presença desse menu é controlada pelo launcher/LockStar.
4. Ajuste a aparência e confirme em **Salvar alterações**. **Cancelar** descarta o rascunho. A prévia não envia comandos nem altera o volume.

O editor permite herdar o visual padrão ou manter um visual próprio por instância. Família do ícone central, tamanho, espaçamento, padding, cantos, moldura e bordas dos botões seguem as opções existentes.

## Orientação, tamanho e padding

| Espaço informado pelo host | Disposição |
| --- | --- |
| Horizontal utilizável | **− / Play/Pause / +** |
| Vertical utilizável | **+ acima / Play/Pause no centro / − abaixo** |
| Sem espaço suficiente ou sem dimensões confiáveis | **Redimensione o widget**, sem controles acionáveis |

São necessários pelo menos **144 × 48 dp** na horizontal ou **48 × 144 dp** na vertical, antes do espaçamento e padding. Se os dois eixos comportarem os controles, retângulo mais alto usa vertical; quadrado ou mais largo usa horizontal. O app limita o espaçamento e o padding efetivos para conservar três áreas de toque de pelo menos 48 dp, inteiramente dentro do espaço físico informado.

Padding horizontal e vertical permanecem nos eixos físicos. O valor vertical negativo continua salvo, mas, neste widget combinado, seu efeito é limitado quando faria os controles ultrapassarem o retângulo do host. O editor mostra os valores efetivos e indica a limitação; ampliar o widget pode permitir outro resultado. A moldura Compacta horizontal pode ser menor que a área de toque; na vertical precisa envolver os três controles.

Se o host não informar dimensões, o editor mostra um exemplo identificado como prévia. Isso não habilita comandos no widget instalado: ele aguarda dimensões utilizáveis. O aviso de redimensionamento não mantém áreas clicáveis ocultas nem progresso periódico. O menu de edição e as dimensões fornecidas pelo LockStar dependem do firmware; funcionamento nesse host exige teste físico.

## Reprodução, volume e acessos

**− e +** alteram um passo do volume de mídia do aparelho onde o widget está instalado. Usam a permissão normal de áudio já declarada pelo app e **não exigem acesso às notificações**, sessão ativa ou companion do relógio. Não abrem o player, não iniciam Spotify e não alteram outros tipos de volume por código próprio. Volume fixo ou política de áudio que impeça a operação podem produzir aviso; não há reenvio automático. Spotify Connect remoto e volume de outro aparelho não são controlados por esses botões.

**Play/Pause** usa o mesmo caminho dos atalhos de mídia: exige acesso às sessões de mídia, escolhe uma sessão elegível e respeita as ações anunciadas pelo player. Sem sessão elegível, aplica-se a tentativa de retomada do Spotify já existente, com seus limites. Consulte o [fluxo do Spotify](SPOTIFY_FALLBACK.md). Um clique enviado não é confirmação de reprodução.

O ícone central acompanha o estado confirmado. Sem acesso, sem sessão ou com serviço desconectado, ele mostra o estado neutro; as laterais de volume permanecem independentes. As bordas individuais das laterais continuam disponíveis quando habilitadas. **Borda só no Play/Pause** conserva essa restrição; sem estado de mídia conhecido, a decoração dependente da reprodução do centro é removida.

**Mostrar progresso da faixa** continua opcional por widget. Só aparece durante reprodução confirmada; pausa mantém apenas a borda estática escolhida. Revogação de autorização ou perda do listener interrompe a apresentação confirmada e o progresso; a reconexão recupera o estado por eventos, sem repetir comandos antigos.

## Backup e continuidade

A exportação usa o formato **v5**, distinguindo perfis de mídia, volume e reprodução com volume. Backups v1–v4 continuam importáveis; um perfil combinado só pode ser aplicado a uma instância do mesmo tipo. Aplicar um perfil como visual padrão não converte widgets existentes. Apps até 0.34 recusam v5: preserve também seus backups anteriores e atualize o mesmo pacote sem desinstalar. [Guia de backup e transferência](BACKUP.md).

## Consumo e verificação

O combinado reutiliza o observador de reprodução existente. Não adiciona monitor de nível de volume, serviço, alarmes, wake locks ou timer próprio. Um clique de volume faz no máximo um ajuste de áudio, sem consulta de sessão ou atualização de reprodução causada pelo clique. Inicialização do processo e reconexão do serviço são trabalhos distintos.

O progresso usa o pulso existente de 5/15/30 segundos, apenas quando ativado, com posição calculável avançando, tela interativa e ao menos um consumidor utilizável. Tela apagada ou ausência de controles utilizáveis não sustenta esse pulso. Repetições sem mudança de dados são deduplicadas.

`PlaybackVolumeUiTest`, `PlaybackVolumeVisualTest` e os testes de provider, recuperação e backup cobrem geometria, comandos, edição, integração e apresentação em ambiente Android simulado. Imagens Android/Skia geradas desses recursos não são fotografias de launcher Samsung ou de LockStar.

Continuam pendentes verificações físicas em Galaxy S25, Tab S10+ e hosts usados: upgrade, ações, dimensões, Bluetooth, perda/retorno de autorização ou serviço, edição, tela apagando normalmente e consumo. Redução de trabalho demonstrada por contadores/testes não equivale a economia de bateria medida no aparelho.

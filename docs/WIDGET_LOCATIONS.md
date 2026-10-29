# Como identificar cada widget — 0.17 experimental

Em **Editar widget instalado**, a lista passa a mostrar o local, a origem da informação, o número da instância e suas dimensões. A tela de personalização também mostra número e tamanho no topo. Se houver apenas um widget instalado, o app abre seu editor diretamente.

O grupo **Local deste widget** permite escolher:

- **Detectar automaticamente**: usa a indicação de local enviada pelo host Android.
- **Tela de bloqueio**: identificação manual, útil para o widget do LockStar.
- **Tela inicial**: identificação manual para o launcher.
- **Outro local**: identificação manual para outra superfície.

A lista diferencia **informado pelo sistema** de **definido por você**. Sem informação reconhecida, mostra **Local não informado**; não presume tela inicial. As indicações automáticas são consultadas ao abrir a lista/editor, sem manter uma cópia que possa ficar desatualizada. Informação ausente, inválida ou combinada fica desconhecida. A preferência manual tem prioridade até você voltar a Detectar automaticamente.

Depois de escolher o local, use **Salvar alterações**. Cancelar descarta a identificação e as alterações visuais. O rascunho sobrevive à recriação da tela; valores inválidos ou uma instância removida impedem salvar. Identificar um widget não altera outros widgets, não muda a aparência e não interrompe a herança do visual padrão. Widgets que já tinham visual próprio continuam com ele. Remover uma instância apaga sua identificação.

Quando o LockStar não informa o local corretamente, abra a configuração diretamente a partir do widget no editor do LockStar, se o host oferecer essa opção, e marque Tela de bloqueio. Assim a identificação fica associada ao ID que o próprio host enviou. Pelo app, número/dimensões e a prévia ajudam a reconhecer a instância; widgets idênticos e sem indicação do host não podem ser distinguidos automaticamente com certeza.

Essa escolha é organizacional: não move o widget, não cria perfis globais por local e não muda a disponibilidade do menu Editar no host. As opções existentes de tamanho, padding vertical negativo e configuração externa continuam disponíveis. [Pesquisa da edição no LockStar](LOCKSTAR_WIDGET_EDITING.md).

## Contrato Android e limites

[AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY](https://developer.android.com/reference/android/appwidget/AppWidgetManager#OPTION_APPWIDGET_HOST_CATEGORY) fornece uma **indicação do host**, não uma prova de localização física. KEYGUARD é tratado como tela de bloqueio, HOME_SCREEN como tela inicial e SEARCHBOX como outro local. Não há heurística por tamanho, pacote instalado ou estado de bloqueio do aparelho. Não foi confirmado quais valores o LockStar do Galaxy S25 envia.

Nenhuma permissão nova, consulta a apps instalados ou monitoramento de tela foi adicionado. A informação manual fica nas preferências privadas da instância. Mídia, Spotify, Wear OS, geometria, frequência e visibilidade do progresso permanecem iguais à 0.16.

[Prévia nativa](../downloads/v0.17-experimental/widgets-por-local.png) · [Review](reviews/widget-locations-0.17.md)

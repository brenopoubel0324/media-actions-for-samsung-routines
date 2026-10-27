# Edição externa do widget no LockStar

Pesquisa e verificação do código em 26/10/2026. A versão local é `0.6-preview`, código 6. Nenhuma mudança desta prévia foi publicada no GitHub.

## O fluxo pedido

O usuário quer selecionar o widget no editor da tela de bloqueio e abrir **Configurações do widget / Widget settings** pelo menu do host. A configuração abre uma Activity dedicada, sem passar pela tela principal do aplicativo. Não existe engrenagem nem botão adicional no conteúdo do widget.

Não se deve confundir o editor do LockStar com a tela bloqueada em uso normal, com o menu da tela inicial do One UI Home ou com os Brief widgets nativos da Samsung. O nome do item e o gesto exato podem variar com a versão.

## Evidências primárias

- [Android Developers — declaração, resultado e reconfiguração](https://developer.android.com/develop/ui/compose/glance/configuration): `android:configure` aponta para uma Activity; `reconfigurable` permite reabrir a configuração; `configuration_optional` permite adicionar usando padrões. O host inicia a Activity e recebe `RESULT_OK` ou `RESULT_CANCELED` com o mesmo `EXTRA_APPWIDGET_ID`.
- [Android Developers — widgets Views](https://developer.android.com/develop/ui/views/appwidgets/configuration): confirma que o contrato de configuração é compartilhado com Glance; a atualização das Views ocorre por AppWidgetManager/RemoteViews.
- [Smartspacer #432](https://github.com/KieronQuinn/Smartspacer/issues/432): em 21/07/2026 o mantenedor relata teste próprio no Android 16 / One UI 8.5. Informa que é preciso selecionar o widget **dentro do LockStar** e escolher as configurações do widget. O autor confirma no dia seguinte que funcionou. É evidência do mecanismo nesse host, não teste deste aplicativo.
- [Samsung — widgets Galaxy](https://www.samsung.com/us/support/answer/ANS10000945/): documenta o menu Settings ao segurar widgets da tela inicial. A seção não comprova o mesmo gesto no LockStar.

## Integração implementada

`media_widget_info.xml` declara `android:configure="dev.local.mediaactions.WidgetConfigureActivity"` e `android:widgetFeatures="reconfigurable|configuration_optional"`.

A Activity é exportada, aceita `APPWIDGET_CONFIGURE` e `DEFAULT` (também permite resolução implícita limitada ao pacote), valida o ID vinculado ao próprio provedor e salva preferências por instância. Não recebe comandos de mídia, não contorna o bloqueio e não abre a MainActivity. O app também oferece um acesso alternativo “Edit widget” que seleciona uma instância instalada; ele não substitui o menu externo solicitado.

O ajuste exato aceita números inteiros e passos de 1 dp. O valor é a lacuna entre as áreas de toque; a prévia também mostra a distância entre centros. A opção automática desativa os controles manuais e preserva o valor manual. O máximo solicitado é 144 dp. A largura disponível pode limitar o valor aplicado, com aviso explícito.

A prévia da tela dedicada fica fixa enquanto os controles rolam e usa as dimensões reportadas pelo host. Sem dimensões, é identificada como amostra; o widget mantém uma lacuna de 0 dp para proteger as áreas de toque. Sombra é opcional e desligada por padrão; o fundo continua transparente.

## Limite da validação

Testes locais verificam manifesto, metadados, resolução externa, ID, salvar/cancelar, isolamento e renderização. Não há um Galaxy S25 conectado. A presença do menu e a abertura pelo LockStar neste aparelho continuam **pendentes de teste físico**. Não há causa confirmada para a ausência reportada; não atribuir automaticamente a falta de suporte do LockStar.

Para o teste físico, instalar o APK `0.6-preview`, confirmar a versão no topo do app, entrar no editor do LockStar, selecionar Media controls e procurar as configurações do widget. Registrar se o item aparece e se abre a tela dedicada da instância correta. O Android pode pedir desbloqueio. Uma instalação antiga `0.4-experimental` não contém a Activity de configuração.

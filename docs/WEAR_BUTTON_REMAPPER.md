# Wear Button Remapper — Media Actions 0.9-experimental

## Entradas independentes em um APK

O aplicativo do relógio oferece três itens na lista de apps:

| Nome | Componente Android | Ação fixa |
| --- | --- | --- |
| MA · Anterior | dev.local.mediaactions.PreviousActionActivity | Faixa anterior |
| MA · Play/Pause | dev.local.mediaactions.ToggleActionActivity | Pausar se tocando; reproduzir se pausado/parado |
| MA · Próxima | dev.local.mediaactions.NextActionActivity | Próxima faixa |

São três Activities MAIN/LAUNCHER, com rótulos/ícones distintos, no **mesmo pacote/APK**. Não são três instalações nem atalhos dependentes de long press no launcher. A seleção de cada item pelo Wear Button Remapper (WBR) chama o componente específico. Ele executa uma ação e encerra, sem tela própria de reprodução. O WBR cuida dos gestos; Media Actions não captura botões globalmente nem solicita acessibilidade.

A comparação visual entregue usa ícones e nomes reais do APK renderizados com Android/Skia; não é uma captura da home Samsung ou do seletor WBR.

## Instalação e configuração

1. Instalar o APK **phone** no celular e o APK **wear** no relógio. Ambos usam o mesmo applicationId/certificado; são instalados em aparelhos diferentes. O APK phone atualiza as versões anteriores sem desinstalação.
2. No celular, manter o acesso de notificações do Media Actions concedido e iniciar um player local. Não é necessário login Spotify no nosso app.
3. No WBR do relógio, selecionar cada gesto que quiser configurar e escolher **MA · Anterior**, **MA · Play/Pause** ou **MA · Próxima** na lista de apps. Cada associação pode escolher uma ação diferente.
4. Testar inicialmente uma ação por vez, com outro aplicativo aberto. Depois verificar tela apagada/bloqueada e conflitos com o gesto padrão. O nome e a disponibilidade dos gestos dependem do WBR/firmware; a instalação do nosso APK não muda os bindings sozinha.

A integração foi conferida estaticamente no APK público **WBR 1.06** do autor. Ele enumera MAIN/LAUNCHER, usa o rótulo da Activity e persiste pacote + Activity. Na execução, pode usar Intent explícito sem action/data; nossas entradas aceitam essa forma e ACTION_MAIN. Isso foi coberto em APIs 33 e 36. **Ainda falta testar o WBR instalado no Watch 8 real.**

## Celular ou relógio

As entradas usam seleção automática: apenas um tocando → esse aparelho; ambos tocando → celular; ambos pausados → último aparelho controlado, com celular como padrão inicial. Ausência de permissão/conexão não significa pausado. Quando nenhum está tocando e o destino preferido está indisponível, não retomar outro aparelho silenciosamente.

Use o APK phone indicado no README junto do Wear 0.9; o protocolo v1 permanece compatível. A ponte usa Google Play services Data Layer, por Bluetooth ou infraestrutura de nuvem conforme a conectividade. Não há servidor próprio, envio de áudio, título/artista ou conteúdo de notificações. O manifesto dos dois APKs não solicita permissões comuns. Os atalhos e widget do celular continuam executando localmente.

Para controlar players instalados no relógio, o **próprio relógio** precisa permitir acesso às sessões de mídia pelo listener de notificações. A autorização do telefone não concede esse acesso. Se o firmware oferecer “Configurações adicionais” na informação do app, essa opção abre a tela de autorização do sistema. Essa entrada não cria um quarto ícone no launcher.

Para abrir a mesma configuração em teste com ADB já autorizado, sem conceder acesso automaticamente:

```sh
adb -s SERIAL_DO_RELOGIO shell am start -a android.intent.action.APPLICATION_PREFERENCES -n dev.local.mediaactions/.MediaAccessSettingsActivity
```

Esse comando só abre configurações do sistema. Não usa root, não contorna permissões e não instala um remapeador. Se o firmware não expuser essa autorização, o controle local deve ser considerado **não disponível/não validado**; o caminho celular continua utilizável quando há conexão e player elegível. Não há fallback de teclas globais.

## Execução e falhas

Uma invocação fixa o aparelho/player e não é repetida automaticamente. O protocolo usa consultas recentes, tickets curtos vinculados ao peer, deduplicação e comandos explícitos. Troca de sessão, prazo vencido e ação não anunciada impedem o envio. ACK indica envio ao player, não comprova mudança de reprodução.

Timeout após enviar informa “sem confirmação”: não reenviar ao outro aparelho. Ações não ficam em fila para reconexão. Wear 0.9 mostra motivos breves de acesso negado, nenhuma sessão, ocupado, falha, prazo ou comando sem confirmação. Quando o destino foi escolhido, o aviso indica Celular/Relógio; sem destino, não inventa o aparelho que falhou. Probe sem resposta informa conexão/consulta indisponível, sem afirmar que um comando foi enviado. Sucesso não abre tela. Execução usa receiver privado com goAsync, limite de tempo e watchdog, sem serviço permanente/polling.

O Android ou o WBR ainda podem exibir transições do sistema ou a interface do app que o WBR esteja substituindo. A ausência de tela própria não garante zero animação do sistema nem preservação de foco em todo firmware.

## Evidência e limites

- Testes de manifesto/launcher verificam três componentes, três nomes/ícones e as formas de Intent do WBR/launcher.
- Testes de integração verificam encaminhamento fixo e nenhuma Activity de player/configuração aberta pelas ações; recriação não executa novamente.
- Endpoint/protocolo e RPC do celular verificam permissão, sessão esperada, comandos, expiração e repetição.
- S25/LockStar, Watch 8, WBR físico, acesso local no relógio e consumo real de bateria ainda precisam de validação nos aparelhos.
- Remover/desfazer os bindings no WBR e desinstalar o companion Wear reverte a integração sem mexer nos atalhos do celular.

Fontes: [Freepoc — downloads do autor](https://freepoc.org/downloads/), [documentação Data Layer](https://developer.android.com/training/wearables/data/overview) e [RPC WearableListenerService](https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService). APKs de referência só foram inspecionados fora do repositório; nenhum código/asset Freepoc foi incorporado ao projeto.

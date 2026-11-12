# Cores automáticas pelo wallpaper — 0.31

Em **Editar widget instalado → Ícones → Cores**, escolha **Automático pelo wallpaper**. Funciona nos widgets de mídia e volume. Ajustes antigos continuam em **Manual**. **Cor de reserva** mantém a sua escolha branco/escuro quando o Android não disponibiliza as cores. **Adaptar também as bordas** é opcional: aplica o mesmo contraste às duas bordas, preservando a transparência das cores salvas. Desativar o modo automático recupera as cores manuais; a detecção nunca é gravada no perfil.

**Origem do wallpaper** permite escolher Tela inicial, Tela de bloqueio ou Seguir localização do widget. Uma localização marcada pelo usuário prevalece sobre a indicação do host. Quando LockStar não informa o local, escolha Tela de bloqueio explicitamente. Local desconhecido/Outro local usa a reserva, sem adivinhar. A ausência de cores do bloqueio também usa a reserva; não troca silenciosamente pela tela inicial.

Ícones, feedback e indicador de progresso seguem o contraste no modo automático; as bordas acompanham somente com a opção correspondente. O indicador continua limitado a reprodução confirmada, tela acesa e opção ligada. O widget permanece transparente. No modo manual, o indicador branco anterior é preservado.

## Fonte e limites

A implementação usa apenas [WallpaperManager.getWallpaperColors](https://developer.android.com/reference/android/app/WallpaperManager#getWallpaperColors(int)) e [OnColorsChangedListener](https://developer.android.com/reference/android/app/WallpaperManager.OnColorsChangedListener). Não lê arquivos, captura a tela ou solicita armazenamento, acesso privilegiado ou nova permissão. Prioriza HINT_SUPPORTS_DARK_TEXT/DARK_THEME. Sem hints, compara o contraste da cor primária informada com branco e o tom escuro do app.

É uma estimativa do wallpaper inteiro, não uma amostra da área atrás do widget. Fundos mistos, vídeo, rolagem e alterações em regiões pequenas podem exigir escolha manual. Um live wallpaper precisa implementar a computação e chamar [notifyColorsChanged](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#notifyColorsChanged()) quando suas cores mudarem. Não foi confirmado em aparelho físico se **Depth Wallpapers & Live Clock** informa cada troca e ambos os destinos. Se faltar informação, a reserva continua funcionando. Não há promessa de amostragem de cada quadro da animação.

## Trabalho e ciclo de vida

A Application faz uma reconciliação das instâncias ao iniciar o processo. Eventos de configuração/host reconciliam a lista, coalescidos. A observação de cores existe somente com consumidor automático de origem conhecida: widget instalado ou prévia de um editor em primeiro plano. A prévia não salva configurações; pausar o editor remove sua assinatura. Sem consumidores, listener, receiver e executor de leitura são liberados. Não há serviço novo, alarmes, jobs, wake lock ou polling.

As consultas iniciais rodam em executor fora da UI: uma por destino necessário. A Application guarda apenas dois valores de contraste, sem imagem. Callback de mudança usa seu próprio payload, sem consultar novamente o wallpaper nem varrer instâncias. Cores repetidas que resultam no mesmo visual não entregam novos RemoteViews. Callbacks e entregas próximas são agrupados em uma passagem da fila principal. Respostas de consultas anteriores à mudança/configuração não sobrescrevem dados mais novos.

Com a tela apagada, callbacks apenas alteram o cache; não entregam RemoteViews. Ao acender, consulta novamente os destinos necessários e entrega somente diferenças de visual. Isso recupera mudanças perdidas, sem timer. A retomada das sessões continua sob a política existente do observador de mídia, independente das cores.

Eventos de cor não pedem rescan de MediaSessionManager. Com o listener de mídia pronto, usam seu snapshot em cache e verificam autorização. Sem observador pronto, apresentam estado desconhecido e não inventam play/pause/progresso. Conexão e recuperação do listener fazem a atualização normal. Os botões preservam suas verificações frescas antes de agir. Volume dispensa acesso a notificações.

Encerramento do processo elimina a observação; o próximo início por app, host ou listener reconstrói o cache. Não há garantia de atualização instantânea com processo morto ou após Forçar parada. Sem grant de notificações, as cores/volume continuam independentes; a sincronização de reprodução exige reautorizar e reconectar. Falha de entrega não é marcada como sucesso e é tentada no próximo evento, sem loop de retry.

Backups v3 incluem modo/origem/reserva/opção de bordas. Importa v1/v2 como Manual e v3 integralmente. Apps até 0.30 recusam v3; mantenha um backup antigo se precisar voltar. O cache detectado não é exportado.

## Validação e consumo

Testes determinísticos API33/36 exercitam luz/escuro/ausência de cores, destinos separados, duplicatas, consulta atrasada, edição cancelada, tela apagada/retomada, remoção e falha/retry de entrega. Contadores simulados verificam zero consultas adicionais ao wallpaper em callbacks repetidos, zero enumerações de sessão por mudança de cor com o observador pronto e zero entregas com tela apagada. Isso demonstra limites de trabalho nos cenários testados; **não mede economia de bateria**.

Comparar no mesmo Galaxy S25/Tab S10+ e mesma versão do Depth/LockStar: manual versus automático, mesmos widgets/configurações, três ciclos de fundo claro/escuro, pausar/retomar música, indicador desligado/ligado, tela apagada e acesa. Repetir removendo/restaurando autorização de notificações e encerrando/reabrindo o listener/processo. Registrar callbacks, consultas, entregas e tempo para recuperar cor/estado. Não comparar apenas porcentagens curtas de bateria: medir duração/energia sob condições reproduzíveis, com e sem música e troca automática do wallpaper. Essa etapa física continua pendente.

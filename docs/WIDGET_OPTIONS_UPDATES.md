# Atualizações de tamanho/opções — 0.29

Os providers de mídia e volume agora ignoram callbacks equivalentes de opções após a primeira entrega bem-sucedida. Isso reduz trabalho em um host que repita os mesmos pedidos. Não é correção confirmada do timeout da tela de bloqueio: o app não possui keepScreenOn, wake lock ou chamada de userActivity, e ainda não há trace físico do comportamento do LockStar.

## Contrato

A chave considera tipo, tamanhos normalizados pelo renderer (incluindo variantes responsivas e fallback), categoria do host, aparência normalizada, vínculo ao padrão e localização manual. Extras do Bundle não usados no desenho não provocam atualização. Comparar Bundle por identidade ou comparar apenas largura/altura seria insuficiente.

Cache efêmero, até 128 instâncias por aplicação, com chave de aplicação fraca. Guarda somente dados pequenos e tentativas em andamento; não guarda Context forte, RemoteViews, bitmap, sessão ou conteúdo de mídia. Recriação do processo começa vazia. Exclusão e pedidos explícitos de atualização/save/restore/recovery invalidam entradas. Entregas antigas não repovoam uma entrada removida.

Uma entrega pendente impede callbacks reentrantes equivalentes. Nova chave pode substituir a pendente; conclusão antiga não sobrescreve a mais recente. A chave concluída só é atualizada após a chamada ao host e, no widget de mídia, a notificação ao observer retornarem. Falha invalida a confirmação e o próximo pedido idêntico pode tentar novamente. Retorno de updateAppWidget confirma solicitação, não que o host efetivamente desenhou o conteúdo.

A aparência capturada na chave é passada ao renderer para não recarregar outro visual durante edição concorrente. Atualização de playback/progresso permanece sob controle do observer existente. Comandos continuam com verificações atuais de propriedade, autorização, sessão e ações suportadas. Não há timer, retry periódico, permissão, dependência ou integração privilegiada nova.

## Validação automatizada

Em API 33/36, testes de ambos os providers verificam dez callbacks equivalentes com zero novos updateAppWidget após a primeira entrega, inclusive mídia com observer conectado e indicador desligado, sem novo resync/getActiveSessions. Verificam extras irrelevantes, resize real, orientação do volume, categoria do host, aparência/herança/localização, fallback sem tamanho, retry de falhas de entrega/notificação, onUpdate/refresh explícito/exclusão e reentrância com chave nova.

Contadores vivem somente nas fontes de teste. A contagem central de bitmap foi ajustada para aceitar o widget de volume sem botão central; a contagem de solicitações ao host segue cobrindo os dois tipos. A redução de trabalho nos cenários controlados não comprova economia de bateria nem reinício de timeout pelo host.

## Verificação no Galaxy/LockStar — pendente

Antes de atualizar da 0.28, se possível, anotar três tempos de apagamento nas mesmas condições; depois repetir com a 0.29. Manter firmware/LockStar, estado da música, configurações de tela e wallpaper comparáveis. Evitar interação após acender a tela, carregamento ou observação da câmera frontal que alterem a política de tela. Registrar diferenças de condições; esse teste sequencial não elimina todas as interferências.

Comparar, sem mudar o restante: nenhum widget nosso na tela de bloqueio; apenas volume; mídia com indicador desligado; mídia com indicador ligado. Repetir pelo menos três vezes por condição e registrar também o tamanho, localização escolhida e intervalo global. Se o aparelho já estiver na 0.29, fazer a comparação com/sem widget nessa versão; não desinstalar nem forçar downgrade apenas para obter uma linha de base.

Registrar se o host repete callbacks e se há novas entregas na mesma frequência por trace/diagnóstico de desenvolvimento quando houver acesso ao aparelho. Se o problema persistir sem novas entregas, investigar configuração de timeout e comportamento do host/sistema separadamente. Manter o live wallpaper e suas trocas sob controle na comparação. Não desligar a tela programaticamente nem mudar políticas de bloqueio para mascarar o sintoma.

Nenhum aparelho está conectado ao ambiente do agente. Resultado do timeout, consumo físico e causa do sintoma permanecem não confirmados. [Review](reviews/options-dedup-0.29.md).

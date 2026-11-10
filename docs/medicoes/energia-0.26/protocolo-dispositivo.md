# Protocolo antes/depois no mesmo dispositivo

**Status: PENDENTE / NÃO MEDIDO.** `adb devices -l` não listou aparelho. Não preencher os resultados abaixo com contagens Robolectric ou estimativas de bateria do Android.

## Preparação e comparação

1. Usar o mesmo Galaxy S25 ou Tab S10+ para as duas versões; registrar modelo, Android, One UI, Modes and Routines, LockStar, Spotify e versão dos dois APKs. Registrar hashes e certificado nos VALIDATION.json das entregas 0.25 e 0.26. Mesmo package `dev.local.mediaactions`, certificado e dados/configuração; não instalar Preview. Se ambos os aparelhos forem medidos, produzir comparações separadas.
2. Instalar a base 0.25 com `adb install -r -d <apk-base>` (downgrade debug; não limpar dados) e candidata com `adb install -r <apk-candidata>`. Confirmar versão instalada em `adb shell dumpsys package dev.local.mediaactions`. Registrar falhas de downgrade e não misturar dados reinstalados com dados preservados. Salvar estilos/IDs dos widgets para repetir as mesmas condições.
3. Mesma faixa/local/volume/roteamento Bluetooth, rede, brilho, taxa de atualização, temperatura, carga inicial, widgets e permissões. Evitar carregamento durante a janela de bateria. Aguardar estabilização após cada instalação. Não alterar otimizações Samsung entre versões. Rodar pelo menos três pares em ordem alternada A/B e B/A, com duração igual, registrando dispersão e possíveis interferências.
4. Medir janelas separadas: 0 widgets; indicador desligado; PLAYING com 1 e 6 widgets em 5/15/30 s; pausado; outra aplicação aberta; tela apagada; AOD. Pelo menos 10 min para trabalho/CPU por cenário; janelas de 60–120 min repetidas para descarga, sem usar uma diferença de 1% como conclusão. Não manter adb por USB carregando para a medição de descarga; coletar antes/depois ou usar depuração sem fio igual nas duas versões.
5. Coletar Perfetto (CPU/sched, Binder e wakeups disponíveis no firmware), `dumpsys batterystats` e dados de bateria/temperatura antes/depois. Marcar o início/fim das janelas fora da música e identificar PID/UID do app e trabalho no host do widget. Relatar CPU e wakeups medidos separadamente de contagens de API dos testes. Configuração/categorias Perfetto precisam ser verificadas no aparelho; não presumir todos os eventos disponíveis no Samsung.
6. Batterystats atribui/estima consumo; não é medidor direto de energia. Informar explicitamente se o resultado é descarga total (charge counter, quando disponível), estimativa por UID ou medição elétrica externa. Calcular mAh/mWh somente a partir de unidades/fontes válidas; mostrar repetição/variabilidade e possíveis custos no launcher/SystemUI. Uma descarga total menor isolada não prova causalidade do app.

## Recuperação: executar em ambas as versões

| Caso | Passos e resultado esperado | Base 0.25 | Candidata 0.26 |
| --- | --- | --- | --- |
| Revogação durante PLAYING | Remover acesso a notificações nas configurações. Comandos devem recusar imediatamente; widget deve abandonar confirmação quando desconectado ou no próximo refresh. Nenhum timer continua após a invalidação. | NÃO TESTADO | NÃO TESTADO |
| Nova concessão | Conceder novamente, observar conexão do listener; testar Play/Pause/Anterior/Próxima e indicador sem reiniciar celular. Se o sistema não reconectar, registrar e testar evento de tela/configuração, sem declarar recuperação automática. | NÃO TESTADO | NÃO TESTADO |
| Serviço/processo encerrado | Registrar PID/estado em `dumpsys activity services dev.local.mediaactions`; provocar encerramento por crash controlado (`adb shell am crash dev.local.mediaactions`, se permitido no firmware). Registrar callbacks/conexão, tempo de recuperação e ausência de duplicação após retorno do sistema. O sistema pode impor restrições de rebind. | NÃO TESTADO | NÃO TESTADO |
| Forçar parada | Forçar parada nas configurações, aguardar, reabrir o app e verificar conexão/estado/comandos. Não exigir rebind automático de um pacote explicitamente parado. | NÃO TESTADO | NÃO TESTADO |
| Tela / AOD | Reproduzir, apagar tela por 10 min e reacender; estado/progresso precisam ressincronizar. Confirmar ausência de ticks recorrentes que acordem o aparelho. | NÃO TESTADO | NÃO TESTADO |
| Estado alterado antes do callback | Mudar reprodução fora do widget e executar ação imediatamente; comando deve usar estado atual, sem alternância otimista do cache. | NÃO TESTADO | NÃO TESTADO |
| Editar/redimensionar/remover | Alterar indicador/estilo, rodar em H/V, remover último widget e adicionar outro; interface deve refletir configuração e observação deve acompanhar consumidores. | NÃO TESTADO | NÃO TESTADO |
| Sem sessão / Spotify | Encerrar sessão, acionar Play/Pause e confirmar resultado real e timeout limitado; não chamar encaminhamento de comando de reprodução confirmada. | NÃO TESTADO | NÃO TESTADO |

## Registro obrigatório por janela

Guardar versão/SHA do APK, dispositivo/firmware, configuração, ordem A/B, timestamps, duração, temperatura/carga inicial/final, CPU ms, wakeups, fonte/unidade de energia, latência de recuperação, comandos corretos/total, logs/traces e observações. Não coletar conteúdo de notificações ou títulos de faixas. Qualquer ganho deve vir acompanhado de resultados funcionais equivalentes; se perder confiabilidade, não aprovar a otimização com base apenas em menor atividade.

| Resultado | Base 0.25 | Candidata 0.26 | Conclusão |
| --- | --- | --- | --- |
| CPU no mesmo dispositivo | NÃO MEDIDO | NÃO MEDIDO | Pendente |
| Wakeups no mesmo dispositivo | NÃO MEDIDO | NÃO MEDIDO | Pendente |
| Descarga/energia no mesmo dispositivo | NÃO MEDIDO | NÃO MEDIDO | Pendente; sem alegação de economia de bateria |
| Recuperação Samsung física | NÃO TESTADO | NÃO TESTADO | Testes simulados não substituem esta etapa |

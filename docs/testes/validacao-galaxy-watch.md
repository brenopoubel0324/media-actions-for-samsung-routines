# Validação física — Galaxy S25 / Galaxy Watch 8

**Pendente: nenhum dispositivo conectado nesta execução. Todos os cenários abaixo estão NÃO TESTADO; CPU/bateria/energia estão NÃO MEDIDO.** Testes Robolectric validam a lógica, não comprovam integração com Spotify, Samsung, LockStar ou Wear Button Remapper (WBR).

## Identificação da sessão de teste

Preencher antes da coleta:

| Campo | Valor |
| --- | --- |
| Data/hora e fuso | Pendente |
| Phone APK / código / commit / SHA-256 | Consultar VALIDATION.json da versão efetivamente instalada |
| Wear APK / código / commit / SHA-256 | Consultar VALIDATION.json da versão efetivamente instalada |
| Android / One UI / Modes and Routines / Routines+ | Versões exatas pendentes; “mais recente” não identifica o firmware |
| Wear OS / One UI Watch / WBR | Pendente |
| LockStar / launcher / Spotify | Pendente |
| Acesso à mídia em cada aparelho | Pendente |
| Wi-Fi / Bluetooth / rota de áudio / rede | Pendente |
| Origem da ação / bindings físicos | Pendente |

As versões e hashes por entrega estão em `downloads/vX-experimental/VALIDATION.json`; registrar o commit da tag e conferir que o APK instalado é aquele artefato. One UI/LockStar/WBR são anotados manualmente. Copiar diagnóstico pela Ajuda após a tentativa e registrar o horário, sem incluir títulos, artistas, notificações, tokens ou IDs de nós.

## Ficha funcional por cenário

Copiar esta ficha para cada linha da matriz: **estado inicial, ações exatas, esperado, observado, PASSOU/FALHOU/NÃO TESTADO, horário, diagnóstico, evidência e limitações**. Reproduzir falhas com uma chamada por gesto; nunca assumir que timeout permite repetir um comando automaticamente.

| ID | Preparação/ação | Esperado | Estado |
| --- | --- | --- | --- |
| S1 | Spotify pausado; Play/Pause em app, widget e atalho | Uma ação, estado real acompanha a sessão | NÃO TESTADO |
| S2 | Nenhuma sessão elegível; Spotify instalado/logado com fila anterior; Play | Reprodução confirmada, sem abrir Activity do player | NÃO TESTADO |
| S3 | Outro player tocando; Play/Pause | Controla a sessão selecionada, sem iniciar Spotify | NÃO TESTADO |
| S4 | Revogar acesso; acionar | Falha clara, nenhum envio | NÃO TESTADO |
| S5 | Retomada por atalho com processo morto/pressão de memória | Sem duplicação; registrar se serviço sobrevive até conclusão | NÃO TESTADO |
| S6 | Spotify em force-stop, separado de S2 | Registrar limitação específica; não confundir com ausência normal de sessão | NÃO TESTADO |
| L1 | Um widget home, outro LockStar; editar por seleção do app | IDs corretos, alterações isoladas, local informado/manual identificável | NÃO TESTADO |
| L2 | Menu externo de Editar no host, se oferecido | Abre editor da instância; ausência do item deve ser registrada como comportamento do host | NÃO TESTADO |
| L3 | Local automático ausente/desconhecido | Não presumir tela inicial | NÃO TESTADO |
| V1 | Centro com borda/progresso, borda externa independente, ícones pequenos, padding vertical negativo | Geometria consistente; registrar cortes/margens impostos pelo host | NÃO TESTADO |
| V2 | Restaurar padrão, editar, Cancelar/Salvar, alterar padrão depois | Respeitar intenção salva somente naquela instância; preservar local | NÃO TESTADO |
| W1 | Três componentes MA no WBR; cada binding | Anterior, Play/Pause e Próxima correspondem a uma invocação | NÃO TESTADO |
| W2 | Só phone tocando / só watch / ambos | Política existente: phone quando toca; caso contrário watch quando toca | NÃO TESTADO |
| W3 | Ambos pausados | Destino anterior somente se elegível; nenhum destino inventado | NÃO TESTADO |
| W4 | Desconexão antes do probe / durante comando | Feedback de contexto correto; sem fila/retry/troca após envio incerto | NÃO TESTADO |
| W5 | Acesso negado / sem sessão / ocupado | Mensagem específica; alvo indicado somente se definido | NÃO TESTADO |

S2 só passa com reprodução confirmada. Recusa de conexão com mensagem correta é diagnóstico útil, mas não é retomada funcionando. Em L2, conferir o manifesto e o host; o app não controla o menu Samsung. O ajuste adicional da lockscreen citado anteriormente ainda precisa de descrição do comportamento desejado; não foi interpretado como padding ou edição externa.

## Protocolo energético

Comparar a base 0.18 e o candidato a 5 s, ambos também com indicador desligado. Depois comparar 5/15/30 s dentro do mesmo candidato. Usar 1, 3 e 6 widgets, incluindo mistura de instâncias sem indicador. Registrar a configuração exata, quais IDs têm progresso, duração e instrumento. Incluir um controle sem widgets quando possível.

Manter aparelho, player/fila, áudio/volume/rota, brilho, temperatura inicial, carregamento, rede e tempo comparáveis. Repetir pelo menos três vezes alternando a ordem; usar a mesma instrumentação em todos os grupos. Começar com janelas iguais de pelo menos 15 minutos e ampliar se a resolução/variação não separar resultados; isso não garante que 15 minutos bastem para energia. Registrar unidade, duração, dispersão e limites da ferramenta.

| Cenário | Indicador/tela | Critério lógico | CPU/energia |
| --- | --- | --- | --- |
| E1 | Play; widget visível; 5/15/30 s | Cadência nominal 12/4/2 ticks/min, callbacks à parte | NÃO MEDIDO |
| E2 | Play; outro app aberto; tela acesa | Pode continuar elegível; tela acesa não comprova widget visível | NÃO MEDIDO |
| E3 | Pausado / sem sessão | Sem tick periódico; callbacks pontuais permitidos | NÃO MEDIDO |
| E4 | Tela apagada / AOD | Sem tick periódico quando não interativa | NÃO MEDIDO |
| E5 | Duração/posição indisponível | Sem tick periódico | NÃO MEDIDO |
| E6 | Indicador desligado | Sem custo periódico de progresso | NÃO MEDIDO |
| E7 | Watch/ponte em repouso, depois sequência igual de bindings | Sem sincronização periódica nova; contar invocações/comunicação | NÃO MEDIDO |

Usar ferramentas do sistema/perfilador para CPU/alocações e, quando houver atribuição suficiente, energia do app. Registrar ferramenta/versão/configuração e custo da instrumentação. Porcentagem total de bateria sozinha não isola nossos controles do player/tela/Bluetooth. Sem atribuição suficiente, relatar a métrica observada e **INCONCLUSIVO** para ganho de autonomia. Não converter redução de consultas em percentual de economia.

As contagens simuladas e seus limites estão nos relatórios `docs/medicoes/energia-progresso-0.19.md` e `energia-progresso-0.20.md`. Não usar tempo/CPU do Robolectric como energia do Galaxy. Antes de concluir, conferir que o controle a 5 s e desligado não regrediu além da variação; abrir correção específica se houver falha reproduzível.

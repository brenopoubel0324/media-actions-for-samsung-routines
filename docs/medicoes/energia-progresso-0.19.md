# Linha de base e intervalos — 0.18/0.19

**Energia/bateria e CPU no aparelho: NÃO MEDIDO.** ADB não encontrou dispositivos. Os dados abaixo são contagens Robolectric em API 33/36, não energia ou tempo físico.

Base: código funcional dd2a036 (0.18), instrumentado com o patch [registrado](energia/base-0.18-instrumentation.patch). O patch usa contexto zero (`git apply --unidiff-zero`). O [teste-base](energia/energy-test-base-0.18.kt.txt) registra o harness usado; o candidato usa os mesmos shadows e hook. O hook somente informa origem a um callback de teste/debug quando instalado; não há contadores, polling, armazenamento ou log de métricas no app normal. Não foi produzido APK físico de baseline instrumentado e não se atribui ao APK histórico um hash dessa medição. Os hashes dos APKs candidatos estão nos pacotes publicados.

Fixture: uma sessão PLAYING controlada, duração 300.000 ms, posição 1.000 ms, tela interativa, ícones sem sombra, widgets sem dimensões responsivas. Janela de 60 s simulados após startup/idle, excluindo a atualização inicial. MediaController shadow fornece estado/metadados consistentes inclusive aos wrappers pelo mesmo token. Em cada cenário há 1, 3 ou 6 indicadores ativos novos; instâncias de cenários anteriores ficam desativadas.

| Indicadores | Base 5 s: ticks / sessões / progresso | Candidato 5 s | Candidato 15 s | Candidato 30 s |
| --- | --- | --- | --- | --- |
| 1 | 12 / 36 / 24 | 12 / 36 / 24 | 4 / 12 / 8 | 2 / 6 / 4 |
| 3 | 12 / 60 / 48 | 12 / 60 / 48 | 4 / 20 / 16 | 2 / 10 / 8 |
| 6 | 12 / 96 / 84 | 12 / 96 / 84 | 4 / 32 / 28 | 2 / 16 / 14 |

Com todos os indicadores desligados: zero tick, evento, consulta ou update periódico na janela medida, tanto na base quanto no candidato. Isso não significa ausência de callbacks necessários durante uso real. O controle equivalente de 5 s mantém as mesmas contagens e permite investigar a redundância que cresce com N. Atualizações e bitmaps do centro continuam proporcionais ao número de indicadores; `bitmap_builds` representa imagens bitmap observadas nas atualizações dessa fixture, não todas as alocações do host.

Dados completos: [base API 33](energia/base-0.18-sdk-33.json), [base API 36](energia/base-0.18-sdk-36.json), [candidato API 33](energia/intervalo-0.19-sdk-33.json), [candidato API 36](energia/intervalo-0.19-sdk-36.json).

Cadência reduzida produz menos trabalho periódico nesta simulação. Não há percentual de economia de bateria nem comprovação de autonomia. A comparação física deverá registrar APK/commit, variante, coleta, áudio, rede, brilho, temperatura, carregamento e pelo menos três repetições, conforme o plano.

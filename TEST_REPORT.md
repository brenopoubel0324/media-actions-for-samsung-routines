# Validação — 0.26 experimental

BUILD SUCCESSFUL. **526 testes**: app 489, wear 15, shared 22. Zero falhas/erros/pulos. Lint dos três módulos aprovado; git diff --check verificado antes de publicar.

Apresentação do widget orientada a eventos, cache efêmero e deduplicação visual; comandos mantêm verificações atuais. Em 60 s simulados com seis widgets/5 s: consultas de sessão 36→0 e metadados 24→0, mantendo 72 atualizações com avanço real. Dez callbacks idênticos em pausa: 60→0 atualizações. Sem polling de descoberta ou timer que acorde o aparelho; recuperação coberta em testes. Comparação física e economia de bateria NÃO MEDIDAS.

[Review](docs/reviews/background-0.26.md) · [Integridade](downloads/v0.26-experimental/VALIDATION.json).

Sem teste físico de Tab S10+/Galaxy/LockStar/Spotify/WBR/Watch 8, medição de bateria ou benchmark físico de CPU. As contagens simuladas não equivalem a economia de energia.

Também passaram 22 testes Preview de recuperação e isolamento, assemblePreview e verificação de package separado. Somente o APK original é distribuído. [Dados e protocolo de consumo](docs/medicoes/energia-0.26/README.md).

# Validação — 0.20 experimental

BUILD SUCCESSFUL. **413 testes**: app 382, wear 9, shared 22. Zero falhas/erros/pulos. Lint dos três módulos aprovado; git diff --check verificado antes de publicar.

Snapshot efêmero por lote reduz leituras repetidas entre widgets. Em seis widgets/5 s, 96→36 consultas de sessão e 84→24 de progresso por minuto simulado; bateria ainda não medida.

[Review](docs/reviews/execucao-snapshot-0.20.md) · [Integridade](downloads/v0.20-experimental/VALIDATION.json).

Sem teste físico de Galaxy/LockStar/Spotify/WBR/Watch 8, medição de bateria ou benchmark físico de CPU. As contagens simuladas não equivalem a economia de energia.

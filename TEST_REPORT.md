# Validação — 0.27 experimental

BUILD SUCCESSFUL. **573 testes**: app 536, wear 15, shared 22. Zero falhas/erros/pulos. Lint dos três módulos aprovado; git diff --check verificado antes de publicar.

Backup manual com exportação JSON conferida e importação revisada antes de aplicar. Preserva padrão, intervalo e perfis portáveis de widgets de mídia/volume, incluindo herança e localização. Perfis podem ser aplicados a novos widgets após reinstalar; posições e permissões do Android não são transferidas. Atualização do app original, sem novo timer nem permissão de armazenamento.

[Review](docs/reviews/backup-0.27.md) · [Integridade](downloads/v0.27-experimental/VALIDATION.json).

Sem teste físico de Tab S10+/Galaxy/LockStar/Spotify/WBR/Watch 8, medição de bateria ou benchmark físico de CPU. As contagens simuladas não equivalem a economia de energia.

Também passaram **48 testes Preview** de codec/store/Activity/isolamento. O APK opcional de migração foi conferido com package dev.local.mediaactions.preview, code27 e certificado histórico. [Fluxo e limites do backup](docs/BACKUP.md). Migração física Samsung e troca para assinatura de produção ainda não foram testadas.

# Self-audit — aplicativo original 0.23

Base: main após merge do PR #1 (`f72205d`); solicitação de 07/11/2026 para ficar com um aplicativo no telefone. Skill self-audit-loop; sem subagentes. Escopo: entrega do código revisado no pacote original com incremento de versão e assinatura compatível; sem migração de stores ou modificação dos comandos.

## Rodada 1 — antes de empacotar

- **Alto de entrega — versão 22 repetida:** o merge trouxe as correções para main, mas o APK publicado continua na 0.22. Incrementar para versionCode 23/versionName 0.23-experimental e fornecer somente o APK original como atualização principal.
- **Requisito de compatibilidade:** conferir o certificado do novo APK contra o APK histórico 0.22 e manter applicationId dev.local.mediaactions; não substituir a chave nem pedir desinstalação do original.
- **Limite de dados:** Preview possui outro package e preferências privadas próprias. Esta entrega preserva os dados do original; não alega copiar automaticamente ajustes da Preview. A remoção apenas da Preview pode ser feita após atualizar e conferir o original. Não oferecer limpeza de dados como migração.

Os requisitos físicos permanecem pendentes. A entrega experimental foi solicitada pelo usuário após o merge; não equivale a declarar a falha Samsung resolvida no dispositivo. Manter opção Preview apenas para desenvolvimento, sem exigir sua instalação para usar as correções.

## Rodada 2 — APROVADO para entrega experimental solicitada

Incremento aplicado para code 23 / 0.23-experimental. APK normal inspecionado: package `dev.local.mediaactions`, três atalhos destinados ao mesmo package; certificado SHA-256 `21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a`, igual ao APK histórico 0.22. SHA-256 do novo APK `655b1327e744c3a677450a00a36544778fee071e773f98775ceb6405427e6e82`.

479 testes (app 442, Wear 15, shared 22), build dos dois módulos e lint dos três passaram; zero falhas/erros/pulos. Nenhum teste novo de implementação trivial: identidade/versão/assinatura e alvos dos atalhos foram conferidos no APK. Imagem real Android/Skia do original 0.23 foi mostrada antes da publicação. `git diff --check` aprovado.

Sem achado alto/bloqueante aberto no escopo desta entrega. Não houve alteração de armazenamento, modo de herança, ritmo de progresso ou comportamento Wear desde a revisão anterior. Não há exportação/importação automática entre packages. Orientação explícita de manter o original instalado e remover apenas a Preview após conferir configurações e recriar eventuais widgets pertencentes a ela.

Bug sweep de entrega sem defeito novo confirmado. A ausência de aparelho conectado mantém migração física, persistência após reinício, Spotify/LockStar/WBR e energia como NÃO TESTADO/NÃO MEDIDO. A publicação experimental foi solicitada pelo usuário e não é declaração de compatibilidade física. Revisão em duas rodadas, sem agente independente.

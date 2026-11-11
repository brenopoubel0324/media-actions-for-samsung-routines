# Self-audit — primeira distribuição estável do original, 0.28

Base 1d07fefc5f4328e92a7fdb1cb33f1bdca2c5b16a (0.27). Pedido: publicar app original estável após o usuário relatar funcionamento normal, preservando atualização e configurações. Skill self-audit-loop, sem subagentes. Adaptação ao wallpaper permanece somente planejada. Escopo funcional do relato do usuário não foi detalhado; não equivale a teste de todos os cenários de migração/LockStar/Wear.

## Rodada 1 — achados antes dos ajustes

- **Alto — assinatura de distribuição:** renomear o APK ou gerar outra chave interromperia a atualização compatível. Gerar Release sem debuggable, assinar fora do repositório com a chave histórica e exigir fingerprint 21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a, package dev.local.mediaactions e code28 antes de publicar. Não introduzir chave privada no git nem fallback automático para chave nova.
- **Médio — build release:** a configuração existente habilita R8, sem distribuição anterior desse artefato validada. Para promoção conservadora, manter minificação desativada nesta entrega e testar a variante Release real, além das regressões Debug. Minificação fica para mudança própria com validação proporcional.
- **Baixo — identificação:** retirar o sufixo experimental do original e o aviso desatualizado da ajuda. Identificar Debug como dev e preservar Preview como variante separada de desenvolvimento. Publicação estável refere-se ao phone; Wear 0.9 continua na distribuição histórica.

Usar versionName 0.28/code28, mesmos stores/backup formato1/IDs. Não alterar permissões nem comportamento de mídia, volume, listener ou backups.

## Rodada 2 — revisão do diff e validação do canal

Releitura de gradle, MainActivity, strings traduzidas, manifest e stores/backup existentes. Simplificação: nenhuma nova variante, preferência, migração, permissão ou alteração de lógica funcional. Release não minificado evita introduzir mudança de ofuscação nesta promoção; Debug passa a exibir dev, e Preview continua separado. O Gradle não recebe chave privada nem assinatura automática de fallback.

A conferência final do artefato exige certificado histórico, code28, versão 0.28, package original, debuggable/testOnly ausentes ou falsos e somente a permissão normal MODIFY_AUDIO_SETTINGS. O relato do usuário é registrado como relato, não como validação física executada pelo agente. Sem novo achado bloqueante/alto/médio confirmado no diff. Assinatura, lint e resultados finais ainda devem ser conferidos antes do veredito.

**Médio — wrapper/lintRelease, identificado nesta rodada:** a validação falhou em AndroidGradlePluginVersion porque o wrapper 8.14.6 tem patch 8.14.7 disponível. Os testes passaram, mas não declarar build/lint aprovado antes de corrigir. Atualizar somente a URL e SHA-256 da distribuição para 8.14.7, com checksum obtido no endpoint oficial Gradle, e repetir a validação completa. Não suprimir o detector.

## Rodada 3 — APROVADO

Após a atualização do wrapper para 8.14.7 com SHA oficial, a validação completa terminou em BUILD SUCCESSFUL. **573 testes Debug** (app 536, Wear 15, shared 22) e **536 testes do app Release**: 1.109 execuções, sem falhas/erros/pulos. Lint phone Debug/Release e shared/Wear Debug aprovado; git diff --check passou. São execuções nas duas variantes, não 1.109 testes únicos.

O APK assinado foi conferido com apksigner e zipalign: package original, code28, versionName0.28, não depurável e não testOnly, certificado idêntico ao original 0.27, uma permissão normal já existente. Assinatura histórica mantida fora do repositório. Sem novo bloqueante/alto/médio/baixo pendente no escopo; bug sweep sem defeito funcional confirmado. Revisão em três rodadas pelo próprio agente.

Canal estável do phone autorizado pelo usuário após relato de funcionamento normal. A atualização exata 0.27→0.28 não foi instalada em aparelho pelo agente; persistência é suportada pelo mesmo package/certificado/stores, sem migração. Matriz Samsung, assinatura de loja e medição de bateria não foram validadas nesta execução. A Preview 0.27 permanece somente como ferramenta histórica de migração; o Wear 0.9 não foi promovido. Adaptação ao wallpaper não foi executada.

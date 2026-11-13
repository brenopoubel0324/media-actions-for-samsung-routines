# Distribuição estável do celular

A partir da 0.28, o canal principal é estável; o download atual é `media-actions-phone-0.34.apk`, build **Release** sem depuração e sem sufixo experimental. O canal foi promovido após relato de funcionamento normal do usuário e validação automatizada. Isso não certifica toda a matriz de aparelhos, players, provedores de arquivos ou hosts Samsung. Wear 0.9 continua na distribuição histórica experimental.

## Atualização e identidade

Package `dev.local.mediaactions`, code34 maior que os anteriores, mesma identidade de assinatura histórica:

`21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a`

Instalar sobre o **Media Actions original**, sem desinstalar. Android mantém os dados da atualização compatível; o backup atual exporta v4 e importa v1/v2/v3/v4, com os mesmos stores e identificadores de componentes. A Preview é outro package e precisa do fluxo de exportação/importação do [guia de backup](BACKUP.md). A adaptação opcional de cores foi implementada na0.31; veja [funcionamento e limites](WALLPAPER_COLORS.md).

## Build e conferências

Gerar `:app:assembleRelease`; o Gradle produz APK sem assinatura. A distribuição é assinada separadamente com a chave histórica mantida fora do repositório. Essa identidade veio das distribuições de desenvolvimento e foi preservada por compatibilidade; não foi criada outra chave nem declarada publicação na Google Play. Não gerar chave substituta nem publicar APK com certificado diferente. Se a chave histórica não estiver disponível, interromper o empacotamento.

Validar com apksigner e zipalign; conferir package, versionName/code, ausência de debuggable/testOnly, permissões e comparação do certificado com o APK anterior. Não modificar o APK após assinatura. Registrar hashes e manifest em `downloads/v0.34`. Não adicionar o keystore privado ao git. Minificação fica desativada nesta primeira promoção; introduzir R8 é uma alteração própria, com validação adequada.

Executar testes do app Release, regressões Debug dos três módulos e lint phone Debug/Release + shared/Wear Debug. Publicar GitHub Release sem flag prerelease e conferir main/tag/target, hashes dos arquivos públicos e flag de canal. Os APKs Debug têm sufixo dev; Preview mantém sufixo preview e package separado. Nenhum deles é o download principal estável.

## Gate de CI antes da distribuição

A [validação completa](CI.md) da fonte relevante deve estar aprovada antes de entregar. `main` e mudanças de risco acionam esse gate; tags não repetem o mesmo build. Também pode ser solicitado manualmente com `validation=full` na ref desejada. Não considerar o escopo cotidiano ou documental suficiente para publicação.

Os candidatos compilados em CI são inspecionados, porém o Release fica sem a assinatura oficial. O workflow não recebe a chave histórica nem publica. Para distribuir binário já validado, registrar a fonte/commit e suas evidências junto dos hashes/manifest/certificado; não assumir associação pelo diretório ou nome do APK. O gate de assinatura/integridade descrito acima continua obrigatório. Mudanças em `downloads/` não têm atalho documental.

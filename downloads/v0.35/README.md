# Media Actions 0.35

[Baixar o APK oficial do celular](media-actions-phone-0.35.apk). Instale sobre o **Media Actions original**, sem desinstalar: mesmo pacote, certificado histórico e código de versão maior. Não é Preview e não instala um segundo aplicativo no celular.

Novo widget opcional **Reprodução e volume**: − / Play-Pause / + na horizontal; + / Play-Pause / − na vertical. Adicione pela lista de widgets e configure em Editar widget. Os dois widgets atuais continuam disponíveis. Volume independe do acesso às notificações; reprodução mantém o requisito existente. [Uso e limites](../../docs/PLAYBACK_VOLUME_WIDGET.md).

Backup exporta v5 e importa v1–v5, distinguindo três tipos de perfil. Apps até 0.34 recusam v5; preserve também os backups anteriores. A atualização não converte widgets, renomeia chaves ou exige migração. [Backup](../../docs/BACKUP.md).

Nome e ícone atuais preservados: a nova identidade aguarda escolha entre as [três propostas apresentadas](../../docs/identidade/naming.md). O companion Wear distribuído continua na versão 0.9 experimental, sem alteração de pacote, componentes ou protocolo nesta entrega.

## Validação e integridade

- Fonte compilada e testada: `a335765582802859f9a15b79e4dc0e49927334cb`.
- Cadeia local completa: **1467 execuções de testes**, zero falhas/erros/pulos; Debug/Release/Preview e lints previstos aprovados. [Evidências](VALIDATION.json) e [log](validation-local.log).
- APK Release não depurável, package `dev.local.mediaactions`, code35/name0.35, única permissão comum `MODIFY_AUDIO_SETTINGS`.
- Certificado SHA256 histórico: `21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a`.
- APK SHA256: `99ddef1956bf4bc01e1d17922ece74cc1bc44f572a017fe7dde03c81d1d3c132`. [Todos os hashes](SHA256SUMS), [assinatura](phone-signature.txt), [badging](phone-badging.txt), [manifest](phone-manifest.txt).
- Payload ZIP do APK assinado comparado com o Release compilado; apksigner/zipalign e inspeções de variantes passaram. Nenhuma chave privada acompanha a distribuição.

[Execução e revisões](../../docs/reviews/combined-widget-and-identity-0.35.md). Os testes demonstram os limites de trabalho simulados; não há medição de bateria nem teste físico nesta sessão. LockStar, upgrade no aparelho, dimensões e recuperação OEM continuam sujeitos à validação no dispositivo. Sem dimensões utilizáveis, o combinado apresenta Redimensione o widget e não envia comandos.

# Media Actions 0.8 experimental

- [APK do celular](media-actions-phone-0.8-experimental-debug.apk)
- [APK do relógio](media-actions-wear-0.8-experimental-debug.apk)
- [Instruções Wear Button Remapper](../../docs/WEAR_BUTTON_REMAPPER.md)
- [Review](../../docs/reviews/wear-button-remapper-implementation.md)
- [Integridade SHA-256](SHA256SUMS.txt)
- [Resultados e assinaturas](VALIDATION.json)

Instalar phone no celular como atualização e wear no relógio. Um APK Wear oferece MA · Anterior, MA · Play/Pause e MA · Próxima para selecionar separadamente no WBR. As duas instalações usam o mesmo applicationId/certificado. Acesso às sessões do celular precisa estar autorizado; controle local no relógio depende de autorização separada disponível no firmware.

213 testes de host e lint passaram. S25/LockStar/Watch 8/WBR reais ainda precisam de validação física. Imagens usam recursos reais e renderização Android/Skia, não são capturas desses hosts. Versão experimental, não validação de funcionamento no aparelho.

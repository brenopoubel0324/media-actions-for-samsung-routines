# Media Actions 0.18 experimental

[Baixar APK do celular](media-actions-phone-0.18-experimental-debug.apk) · [Review e melhorias propostas](../../docs/reviews/app-audit-0.18.md) · [Prévia da correção de rascunho](auditoria-configuracoes.png) · [Checksums](SHA256SUMS.txt)

Três bugs reproduzidos e corrigidos na review geral:

- Texto de número/cor inválido desaparecia ao recriar a tela, substituído silenciosamente pelo último valor válido. O rascunho agora mantém o texto/cursor e exige corrigir antes de salvar. Só valores validados chegam às preferências.
- Uma resposta antiga do Spotify podia substituir o resultado de um toque mais recente nos testes de controle. O resultado atual agora acompanha a última invocação; o diagnóstico histórico continua atualizado.
- Salvar somente o local de um widget podia sobrescrever um visual alterado em outra tela enquanto seu editor estava aberto. A edição agora compara com o visual original, inclusive após recriação, preservando alterações externas quando o usuário não mudou o visual.

Campos numéricos desativados também tratam rascunhos inválidos sem parse inseguro. A serialização temporária foi centralizada mantendo as chaves antigas e a independência das opções.

Validação: 394 testes (app 363, wear 9, shared 22), ambos APKs montados, lint sem apontamentos, três rodadas de review. A imagem reúne controles reais Android/Skia e dados de demonstração; não é captura de um Galaxy.

Instale como atualização sem desinstalar: applicationId/certificado preservados e zero uses-permission. Wear continua 0.8 e não precisa de atualização. Configurações anteriores, marcação de local, controles de mídia, Spotify, progresso opcional/intervalo de cinco segundos e protocolo Wear permanecem compatíveis.

As oportunidades propostas, ainda não implementadas, incluem Restaurar visual padrão por widget, intervalo de progresso selecionável e diagnóstico mais específico. Validação física com S25/LockStar/Spotify/WBR no Watch 8 e medição de bateria/FPS continuam pendentes.

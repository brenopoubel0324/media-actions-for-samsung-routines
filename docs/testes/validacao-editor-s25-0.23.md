# Validação no Galaxy S25 — editor de widget 0.23 (Preview isolado)

**Distribuição atual — 07/11/2026:** o usuário solicitou unificar o uso no aplicativo original. A entrega phone 0.23 usa `dev.local.mediaactions` e o certificado histórico. Instalar sobre o original, conferir seus widgets e depois remover somente a Preview. Os cenários abaixo podem ser executados no original atualizado, substituindo os nomes Preview pelos do aplicativo normal. O isolamento descrito a seguir registra a fase de investigação anterior. A disponibilidade desta atualização experimental não comprova resultado físico dos cenários.


**Status:** protocolo de teste, não resultado físico. A integração com LockStar depende da versão instalada da Samsung.

## Instalação sem perda de dados

O workflow `.github/workflows/widget-save-ci.yml` gera um APK de **Media Actions Preview** somente quando build, testes, lint e verificação de pacote passam. Ele tem o package ID **`dev.local.mediaactions.preview`**, diferente do aplicativo existente **`dev.local.mediaactions`**. Portanto não substitui o aplicativo atual e não compartilha as suas preferências.

- Usar o artefato `media-actions-s25-isolated-preview` da execução verde correspondente ao SHA do PR.
- Não desinstalar o Media Actions instalado, nem limpar seus dados.
- A versão Preview exige nova permissão de acesso às notificações/sessões de mídia se as ações de reprodução forem testadas.
- A assinatura de debug pode variar entre execuções do GitHub Actions. Se uma Preview posterior não atualizar a Preview atual, remova **somente a Preview**, não o aplicativo original.
- **Limite:** uma instalação paralela testa a lógica do editor, o provider e o host; não valida migração de preferências nem atualização da instalação atual.

## Cenários

| Caso | Passos | Esperado |
| --- | --- | --- |
| P1: persistência | Adicionar widget **Media controls (Preview)**; alterar cor/escala; Salvar; reabrir editor | Valores e modo de aparência conservados |
| P2: renderização | Comparar aparência do próprio widget após salvar | Visual atualizado ou limitação efetiva explicitada no diagnóstico |
| P3: dois widgets | Criar duas instâncias Preview; alterar apenas a primeira | A segunda não muda |
| P4: herança | Usar visual padrão em uma instância; salvar; mudar os padrões do app | Instância herdada acompanha padrão, personalizada não |
| P5: cancelamento | Alterar rascunho; tocar Cancelar; reabrir | Preferências salvas não mudam |
| P6: reinício | Reabrir depois de reiniciar o aparelho (sem apagar dados) | Preferências ainda presentes |
| P7: identificação | Marcar tela inicial/LockStar manualmente; alterar apenas cor depois | Identificação do local permanece igual |
| P8: LockStar | Criar o widget Preview na tela bloqueada quando oferecido; editar pelo host e pelo aplicativo | Reportar separadamente se o menu do host existe, se abre o ID correto e se atualiza visual |
| P9: mensagem | Inserir tamanho/valor inválido e tocar Salvar | Editor continua aberto e destaca o campo incorreto |

## Evidência mínima se um caso falhar

No app **Media Actions Preview**, tocar **Copiar diagnóstico dos widgets** na seção **Seu widget** após tentar salvar. Registrar qual caso falhou e, se possível, duas capturas: editor com valores e widget com aparência. O diagnóstico informa IDs, modo de override e dimensões efetivas; não comprova gravação física em disco nem renderização pelo host.

**Não relatar `REQUESTED` como `RENDERED`.** A API Android aceita a solicitação de `RemoteViews` sem confirmar que One UI/LockStar a desenhou.

## Critérios para atualizar a instalação original

Somente depois de testes físicos e conferência da assinatura do APK instalado (e versão incremental). A Preview paralela não dá acesso à chave de assinatura do aplicativo existente. Não publicar atualização incompatível com a assinatura original.

## Complemento da review de 05/11/2026

O listener e os três atalhos da Preview foram corrigidos para a instalação isolada. O CI agora testa esses componentes na variante Preview, além de conferir o package do APK. Após falha de atualização, testar também editar novamente o rascunho e tentar salvar, inclusive depois de recriar a tela. A escrita anterior do próprio editor não deve gerar conflito falso; alteração posterior de outro editor deve continuar protegida.

A Preview não valida a ponte com o Wear original, pois o Data Layer exige package/certificado correspondentes. Para o teste de persistência após reinício, comparar valores/modo reais; o hash auxiliar do diagnóstico não é um identificador durável entre processos. [Review desta rodada](../reviews/rodada-editor-preview-2026-11-05.md).

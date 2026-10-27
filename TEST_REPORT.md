# Validação — 0.15 experimental

Comando: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug --console=plain`. **BUILD SUCCESSFUL: 344 testes** (app 313, wear 9, shared 22), zero falhas/erros/pulos. Lint dos três módulos sem apontamentos. Integridade: [VALIDATION.json](downloads/v0.15-experimental/VALIDATION.json).

A suíte nova substitui os testes que verificavam somente envio de broadcast. Verifica descoberta restrita do serviço público do Spotify, EXTRA_RECENT, conexão mantida até PLAYING, retorno sem falsa confirmação, prepare/play e ausência de Activity/tecla global. Abrange serviço ausente/privado/desabilitado/protegido/ambíguo, pacote incorreto no token, recusa/suspensão, timeout, erro de reprodução, callbacks atrasados/duplicados, tentativa concorrente e exceção no callback do host.

Também verifica o caminho PhoneMediaActions sem sessão até confirmação, prioridade dos players existentes, revogação de acesso/troca de player durante a conexão, limpeza de browser/callback/timer, PendingResult do widget pendente até o encerramento e atalho NoDisplay que encerra imediatamente enquanto a conexão pertence ao aplicativo. Um teste nativo exporta a mensagem real do último resultado concluído. O deadline absoluto é verificado avançando relógio sem processar a fila e entregando callback atrasado.

Os testes de conexão usam um shadow explícito de MediaBrowser e controlador de sessão simulado. Eles comprovam a máquina de estados/lifecycle do app, não autorização do Spotify instalado nem reprodução física. A falha relatada pelo usuário na implementação 0.11–0.14 não é apresentada como resolvida em hardware sem novo teste.

Phone 0.15, código 15, assinatura/applicationId preservados, zero uses-permission. Armazena localmente apenas o enum do último resultado concluído, sem conta/fila/título. Wear 0.8 e protocolo, controles de sessão existentes, aparência e intervalo do widget inalterados. Sem teste físico Samsung/Spotify/Wear ou benchmark de bateria/FPS.

[Review](docs/reviews/spotify-browser-0.15.md) · [Fontes e comportamento](docs/SPOTIFY_FALLBACK.md).

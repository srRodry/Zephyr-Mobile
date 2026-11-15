# Zephyr beta 2 — integração Emerald e desempenho

## Resultado esperado

A branch `integration/zephyr-beta2` combina a interface compartilhada por toque e
controle do PR #5 com a beta de desempenho do PR #4 (`417a7c5c`). A `main` continua
preservada. A integração não representa uma nova medição de FPS no Edge 30 Ultra.

Correções adicionais:

- A seção de estado dos controles continua disponível ao alternar o modo de entrada.
- Voltar do Android e cancelamento do gamepad seguem a mesma rota do botão da tela,
  incluindo retorno à página inicial de um deep link sem histórico.
- A mudança de seção e o fechamento do guia recuperam foco para navegação.
- Segurar X/Y não repete abertura de ficha nem alternância de favorito.
- A confirmação no palco respeita a preparação de jogo e ignora repetição da tecla.
- Os testes de controles e configurações verificam destinos, retorno e preservação
  de valores em ambos os modos, em vez de procurar textos da interface antiga.
- A biblioteca tem testes de seleção circular pelo direcional, confirmação,
  favoritos e bloqueio de confirmação durante preparação.

## Desempenho e compatibilidade

Os perfis experimentais e as dependências conservadoras Vulkan são os do PR #4.
Consulte [performance-beta1.md](performance-beta1.md) para os limites e comparação
A/B. Esta rodada não habilita experimentos automaticamente, não altera a resolução
360p existente, o áudio ou clocks. Forza Horizon e Forza Horizon 2 continuam tendo
perfis separados. Não há evidência nova que identifique a causa de uma falha do
jogo no aparelho; falhas do CI da interface não diagnosticam falhas do emulador.

## Distribuição e validação

O workflow existente aceita a branch de integração e identifica seu APK como
`Zephyr-Performance_beta2_<commit>`. Mantém o pacote Research, a chave permanente e
a sequência de versionCode do mesmo workflow. Antes de empacotar, executa testes
dos hubs, biblioteca, paleta e perfis. Não publica uma GitHub Release.

Validação de aparelho ainda necessária: conectar/desconectar controle, retrato e
paisagem, troca A/B de confirmação, abrir/fechar guia, abrir o jogo, menu durante a
sessão e 20–30 minutos de gameplay. Os testes JVM não substituem esses passos.

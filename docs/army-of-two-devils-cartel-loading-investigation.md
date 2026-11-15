# Army of Two: The Devil's Cartel — diagnóstico de carregamento (Android)

## Evidência inicial (2026-11-14)
- Vídeos do usuário: tela de título é renderizada; depois há longa tela de carregamento sem jogabilidade visível.
- A duração observada não demonstra por si só loop infinito ou ausência de progresso interno.
- Title ID de referência: `454109AB` (confirmar o Title ID real em `xe.log`).
- Xenia upstream: https://github.com/xenia-project/game-compatibility/issues/577
- Relato de carregamento infinito no Canary: https://github.com/xenia-canary/game-patches/issues/391
- Outro projeto independente (recompilação Windows, não um patch compatível direto): https://github.com/BrewSparrow/ArmyOfTwoTDC-Recomp

## Pré-condições
1. Verificar cópia própria íntegra do jogo e todos os arquivos de dados; confirmar eventuais pedidos de instalação de conteúdo HD. Não contornar checagens substituindo arquivos sem evidência.
2. Confirmar Title ID e versão no log; salvar logs da mesma versão do APK/driver.
3. Não apagar saves, conteúdos instalados nem cache global durante o diagnóstico.
4. Registrar driver Vulkan (sistema/Turnip, versão), versão do APK e comportamento exato.

## Matriz A/B (uma mudança de cada vez)
A. Executar sem overrides do jogo; capturar `xe.log` e diagnóstico do app no último segundo de progresso.
B. Aplicar perfil experimental `ao2dc-loading-baseline`: `Vulkan|vulkan_async_skip_draws=false`. Reiniciar o jogo e comparar com A.
C. Restaurar o perfil anterior. Aplicar `ao2dc-loading-page-state`: também `GPU|clear_memory_page_state=true`. Reiniciar e comparar com B; reverter caso haja regressão.
D. Caso ambos fiquem na mesma tela, abandonar hipótese de FPS/resolução e investigar chamadas bloqueadas: VFS/arquivos, thread wait, XAM/storage, shader/pipeline stall, exceção guest.

## Dados necessários no log
- Últimas 150-300 linhas antes do bloqueio, linhas do Title ID, erros de leitura de arquivos, chamadas XAM/storage, guest exceptions, filas de jobs e erros Vulkan.
- Distinguir stall do game thread vs stall GPU vs falha de asset e solicitação de instalação de conteúdo.
- Se houver incidente estável, instrumentar timeout e rastreamento com timestamp, sem forçar retorno bem-sucedido de APIs para ocultar a falha.

## Critério de sucesso
Chegar ao menu e entrar em uma missão, com movimento e renderização funcional por pelo menos cinco minutos, três inicializações consecutivas. Somente depois medir FPS, frametimes e temperatura.

**Status:** perfis experimentais implementados; nenhuma execução no Edge 30 Ultra nem APK validada neste commit. Não apresentar como correção de compatibilidade.

# Zephyr-Mobile: auditoria Army of Two TDC, AOT nativo e custos de inicialização

18/11/2026 · branch `research/army-tdc-native-hotpath-20261118` · dispositivo-alvo Edge 30 Ultra / Snapdragon 8+ Gen 1 / Adreno 730 · base Beta 17 (`540959e6`).

**Escopo:** triagem de compatibilidade, import ausente de HLE, dois pontos de logging excessivo, verificação offline de insumos AOT e testes NDK. Não altera `main`, assinatura, pacote, resolução nem configurações gráficas, nem afirma ganho de FPS sem teste no aparelho.

## 1. Registros reais, não hipóteses

Arquivos do proprietário estudados localmente, sem subir logs ou dados de jogos ao Git: `xendroid-diagnostics-20261116-234422-800.zip` e `xendroid-diagnostics-20261117-061433-110.zip`, ambos para **Title ID 454109AB** (*The Devil's Cartel*). Arquivos xe.log têm aproximadamente 1 MiB cada e podem estar truncados.

| Evidência | 16/11 | 17/11 | Implicação |
|---|---:|---:|---|
| Override de boot `AO4.Main.xex` | Presente | Presente | É DLL e não deve ser ponto de entrada |
| Fallback/início `Default.xex` | Sim | Sim | O launcher **já funciona** quando selecionado |
| `AO4.Splash.xex` e `AO4.Main.xex` carregados por código guest | Sim | Sim | A transição entre módulos ocorre; erro posterior não é simplesmente “não carregar XEX” |
| `Engine.BuildInfo.xex` | Sim | Sim | Parte do runtime foi inicializada |
| Chamada ausente `XamBackgroundDownloadSetMode` | Sim | Sim | Ordinal XAM `0x9C4` em tempo de execução não implementado |
| Linhas `XEvent signal` | 6.001 | 6.001 | Flood síncrono de diagnóstico no kernel |
| Linhas `MemoryPollPark: parked poll loop` | 1.464 | 1.464 | Log por bloco compilado pelo JIT, não por quadro |
| Janelas com waits acordados pelo backstop 64ms | 54 / 69 / 58 / 70 | 59 | **Há perda de oportunidade de acordar waits**, mas mecanismo específico ainda não isolado |
| `VkFrameSync` / `VkPipelineWait` / `GpuFrame` | Ausentes | Ausentes | Nenhum diagnóstico conclusivo sobre GPU busy, pipelines ou FPS |

Um registro de 17/11 contém ANR do processo `:emu`, `Input dispatching timed out` após cinco segundos. ANR não prova, sozinho, deadlock da emulação: pode envolver a thread principal do host. Para afirmar causa, será necessário correlacionar stack simbólica da mesma compilação e thread responsável. O registro de espera não implica corrupção permanente de um sinal: algumas esperas só foram reavaliadas tardiamente.

### Linha de execução verificada

1. O override tenta `AO4.Main.xex`, cujo XEX declara `XEX_MODULE_DLL_MODULE`. O carregador recusa anexar a DLL a uma thread do host.
2. O aplicativo volta a `Default.xex`, executável título (`XEX_MODULE_TITLE`, sem DLL).
3. `Default.xex` carrega `AO4.Splash.xex`, posteriormente `AO4.Main.xex` e `Engine.BuildInfo.xex`.
4. Com módulos carregados, surgem chamadas não implementadas, vblank ressíncronizado, waits resgatados pelo backstop e relatórios de ausência de quadro.

**Decisão de operação:** retirar `AO4.Main.xex` de `General|launch_module` e iniciar `Default.xex` para esse título. Não introduzir patch de salto para o Main baseado em endereços fixos: a revisão exata do jogo ainda não foi verificada por hash.

## 2. Código implementado nesta branch

### 2.1 XAM 0x9C4

`kernel/xam/xam_content.cc` agora define `XamBackgroundDownloadSetMode_entry` como no-op que retorna `X_ERROR_SUCCESS`, e registra a exportação já existente em `xam_table.inc`. A referência upstream é `has207/xenia-edge`, que usa a mesma política: não existe fila de downloads da dashboard no emulador. Não simular conclusão de download inexistente nem interceptar outro ordinal.

É correção semântica de HLE, **não prova de que o carregamento termina**.

### 2.2 Hot paths kernel/JIT

- `kernel/xevent.cc`: `Kernel|zephyr_event_signal_trace=false` por padrão. A atualização de quem sinalizou o evento foi preservada para diagnóstico; a emissão das primeiras 6.000 mensagens de `XEvent::Set` agora é opt-in e usa contador saturado (sem operações atômicas inúteis após esgotar o orçamento).
- `cpu/compiler/passes/memory_poll_park_pass.cc`: aceitar um loop de espera continua inserindo `SpinBackoff(1)`, mas registrar cada loop compilado exige `CPU|log_memory_poll_park=true`; a opção já existia, embora anteriormente ocultasse somente rejeições.
- `tools/compile-native-regressions.py` passou a incluir as três unidades de tradução de produção; `tools/test-army-tdc-native-hotpath.py` impede regressões no ordinal, nos guardas de logging e no quirk do jogo de 2008.

Efeito previsível: menos alocação, formatação e I/O do logger durante o boot; o impacto em FPS estacionário permanece **desconhecido**.

### 2.3 Diagnóstico reproduzível

- `python3 tools/analyze-army-tdc-boot.py /caminho/diagnostico.zip` inspeciona o ZIP *offline*, sem extrair arquivos no disco; classifica módulos, imports não resolvidos, backstops e mensagens de log, distinguindo ausência de métricas de zero.
- `python3 tools/verify-army-tdc-aot-inputs.py /caminho/assets` compara SHA-1 dos três XEX do usuário com a revisão retail 0.0.0.11 publicada pela recompilação ReXGlue. Não modifica nem transmite arquivos.

## 3. Diferenças entre AOT do Army no Windows e JIT do Zephyr

Fonte primária: https://github.com/BrewSparrow/ArmyOfTwoTDC-Recomp

O projeto ReXGlue recompile `default.xex`, `AO4.Main.xex` e `Engine.BuildInfo.xex` para C++ antes de construir um aplicativo Windows x86-64. Seu renderer exige D3D12 + Rasterizer Ordered Views, e usa hooks específicos de shaders, memexport, occlusion, XMA e frame pacing. O README pede até 16 GiB de RAM para a etapa de geração e uma cópia legítima compatível do jogo, que **não está armazenada neste repositório**.

Conversão `PowerPC → C++ → binário ARM64` resolve uma parte da CPU, **não** converte automaticamente `Direct3D 12 → Vulkan/Adreno`, nem implementa todo o kernel Xbox 360, texturas/EDRAM, áudio XMA e sincronização com a mesma semântica. Um porte Android AOT demandaria:
- validar exatamente os três XEX da revisão suportada e os ativos do próprio proprietário;
- fornecer compilação AOT AArch64 com convenções de chamada e endianness validadas;
- desenvolver/testar backend Vulkan por título em vez dos hooks D3D12 do projeto original;
- portar as dependências de kernel, entrada, armazenamento, áudio e shaders;
- verificar equivalência de rendering e sincronização antes de atribuir ganhos.

**Não será apresentada uma “recompilação nativa Android funcionando” enquanto não houver binário ARM64 reproduzível, renderer Vulkan operacional e teste no dispositivo.** O caminho mais curto para compatibilidade hoje é continuar corrigindo HLE e scheduler do Zephyr, mantendo o projeto AOT como pesquisa separada.

## 4. Melhorias upstream já presentes; evitar reimplementação

- Tratamento TDC para reuso de objetos de dispatcher já está em `kernel/xobject.cc::GetNativeObject`: verifica handle/guest pointer, tipo correspondente e recria objetos inválidos.
- O atalho de seleção de armazenamento do TDC já está em `kernel/xam/xam_ui.cc::XamShowDeviceSelectorUI_entry`, com conclusão `CompleteOverlappedImmediate` quando há único dispositivo/headless.
- `sync`, `eieio` e barreiras guest geram barreiras na camada de HIR/A64; importar a correção AOT de fences de x86 criaria redundância.
- O quirk `0x454107F8` refere-se ao **Army of Two de 2008**, não ao TDC `0x454109AB`; não aplicar `clear_memory_page_state` ao título errado.

Comparar os patches do ReXGlue um a um é necessário: a alegação de aceleração por outra arquitetura **não é evidência transferível** para o SM8475.

## 5. Critérios de aceitação e próximas decisões

**Antes de qualquer beta:**
- CI de pesquisa passa, inclusive testes de XAM/trace e teste do classificador;
- CI Android ARM64 compila as unidades `xam_content.cc`, `xevent.cc`, `memory_poll_park_pass.cc` reais e os testes Kotlin;
- não alterar `main`, certificado Research, pacote, defaults de Vulkan nem artefatos do usuário.

**No Edge 30 Ultra:**
- iniciar TDC via `Default.xex` em ambas as versões; comparar 3 aberturas frias e quentes;
- comparar tempo até `AO4.Main.xex` carregado e até primeiro quadro de gameplay, com as mesmas opções/driver, sem o logging intensivo;
- verificar ausência do import `XamBackgroundDownloadSetMode` e ocorrência de resgates de 64ms; se continuarem, capturar sinais + stacks apenas em uma execução diagnóstica;
- perfis separados para `guest_scheduler` e `park_memory_poll_loops`: mudar uma variável por vez; evitar desligar proteção de sincronização de forma global;
- em Forza, medir guest FPS/frametimes e cargas CPU/GPU da mesma cena, com 3 capturas por braço, estado de cache e temperatura equivalentes. Não transformar ausência de `VkFrameSync` em “GPU livre”.

**Status:** implementação de código-fonte e CI; sem decompilação de um jogo do usuário (os executáveis não foram fornecidos ao processo de recompilação), sem demonstração de gameplay ou FPS novos.

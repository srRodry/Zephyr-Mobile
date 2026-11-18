# Zephyr Mobile — auditoria de gargalos e validade do benchmark (17/11/2026)

**Escopo:** `research/optimization-round3` (HEAD inicial `55096e305c9d11d6028c934e0bd506f504ce23b5`), Motorola Edge 30 Ultra / SM8475 / Adreno 730 / Android 15. Trabalho isolado em `research/edge30-diagnostics-audit-20261117`. `main`, assinatura Research, pacote e cvars padrão não são alterados nesta auditoria.

## 1. Conclusão condicionada à evidência

O relatório externo de 19 páginas ("Pesquisa Zephyr Mobile: desempenho, compatibilidade e recompilação no emulador de Xbox 360", 17/11/2026) prioriza EDRAM, resolves e a thread de comandos, mas declara que **não mediu diretamente o Zephyr no Edge 30 Ultra**. O diagnóstico do repositório para a captura real de Forza Horizon 2 também **não contém GPU busy/fence/pipeline-wait pareados por quadro**. Assim, é prematuro afirmar que a GPU é o gargalo dominante no aparelho.

A captura documentada em `docs/zephyr-run-analysis-2026-10-14.md` (build `285eb7dc`, Forza Horizon 2, Title ID `4D530AA4`) tem:

| Campo | Valor | Limite de interpretação |
| --- | --- | --- |
| FPS mediano | 26 | Mediana das médias guest das amostras, não média da condução |
| Amostras FPS <=20 | 182/405 = 44,94% | Mistura boot, menu, pausa e condução |
| Quadros >=50 ms | 1330/9332 = 14,25% | Present-to-present guest, não GPU busy |
| Guest CPU 0 | 96,86% de um núcleo em média | Ocupação alta; não distingue execução útil de espera ativa |
| GPU Commands | 40,39% de um núcleo em média | Uso da CPU na thread de comandos, não uso físico da GPU |
| APU/JIT/pipelines | Traduções/criações registradas | Tempos de worker agregados não equivalem a stalls do quadro |
| GPU busy/pipeline wait | **Não medidos na captura** | Não interpretar zeros/ausência como folga de GPU |

**Decisão:** não transportar atalhos de precisão, desativar resolves nem alterar política de threads sem primeiro separar CPU guest, espera e GPU no mesmo cenário reproduzível.

## 2. Recursos existentes que não devem ser implementados novamente

- JIT ARM64, otimizações de permutação `REV32/ZIP`, truncamentos de comparação e testes em `tools/test-round3-a64.py`, `tools/test-a64-vector-select.py`.
- Memoização de textura/view e deduplicação de fila, cobertas em `tools/test-round3-cache.py`; os perfis `zephyr-edge30-round3-memo-off/on` já existem.
- Compilação assíncrona, cache persistente e pré-criação de pipelines, reuso de descritores; há experimentos separados para quantidade de workers.
- ADPF, estatísticas de scheduler em `kernel/guest_scheduler.cc`, fases opt-in e `gpu_performance_hint` (desligado por padrão); uma experiência POCO F7 não mostrou melhora com este último.
- `tools/vulkan_diagnostics.py` e `tools/compare-benchmarks.py`; o primeiro **não calcula GPU/frame multiplicando médias de janelas diferentes** e o segundo exige metadados comparáveis e ≥3 execuções por braço.
- `tools/zephyr-graphics-ab.py` e testes para comparar configurações sem ativar vários atalhos simultaneamente.

Ver `docs/optimization-round3-research.md`, `docs/zephyr-gpu-diagnostic-audit-2026-10-14.md`, `performance-tests/ab13-results.md` e `docs/zephyr-scheduler-phases-2026-10-14.md`. A série AB13 do POCO F7/Adreno 825 demonstra redução de instruções do guest, não ganho automaticamente reproduzível no Adreno 730.

## 3. Falha real localizada e corrigida: categoria de CVAR no benchmark

Antes da correção, `tools/zephyr-graphics-ab.py` emitia **todas** as opções em `[GPU]`. O código C++ registra `vulkan_async_skip_draws` e `vulkan_skip_gpu_written_texture_loads` na categoria **`Vulkan`**:

- `gpu/vulkan/vulkan_pipeline_cache.cc`: `DEFINE_bool(vulkan_async_skip_draws, ..., "Vulkan")`.
- `gpu/vulkan/vulkan_texture_cache.cc`: `DEFINE_int32(vulkan_skip_gpu_written_texture_loads, ..., "Vulkan")`.

Como `config::ReadGameConfig` usa `categoria.nome`, usar `[GPU]` faz esses dois overrides **não serem aplicados** sem necessariamente gerar erro visível. A referência de A/B podia, portanto, ser diferente do que o arquivo alegava — em especial `vulkan_async_skip_draws`, cujo default atual é true. As oito outras opções deste script estão declaradas em `GPU`, **inclusive** várias com prefixo `vulkan_`; não se deve inferir categoria pelo nome.

Correção nesta branch:

1. `tools/zephyr-graphics-ab.py` emite `[GPU]` e `[Vulkan]` explicitamente, preservando valores e a única diferença de instrumentação entre reference e light-timing.
2. `tools/test-zephyr-graphics-ab.py` valida conteúdo, invariantes entre braços, erros e cada categoria contra o `DEFINE_*` real de produção.
3. O workflow `zephyr-research.yml` roda o teste da categoria nesta branch.

**Atenção:** `vulkan_async_skip_draws=false` pode bloquear na criação de pipelines com cache frio; esta é a política de renderização deliberadamente conservadora da referência, não um preset de máximo FPS. Ambos os braços A/B devem manter esse valor efetivo idêntico. Não aplicar os snippets automaticamente a todos os jogos.

## 4. Próxima campanha de medição no Edge 30 Ultra (não executada aqui)

Primeiro repetir Forza Horizon 1 (`4D5309C9`) e Forza Horizon 2 (`4D530AA4`), com jogo/salvamento/cena controlados e o mesmo driver, resolução interna (inclusive 360p, se for a configuração efetiva), patches, cap, temperatura inicial e estado do cache. Não misturar menu, boot, cutscene e condução em um número único.

1. Executar pelo menos três sessões de referência e três do candidato em ordem intercalada (ABBA/BAAB quando viável), 5 min de gameplay comparável após aquecimento, com outra execução sustentada de 20–30 min para estabilidade térmica.
2. Coletar FPS **guest**, frame time p50/p95/p99, timestamp da GPU por submissão (se suportado), custo da thread de comandos, fases do Guest CPU 0 e frequência/estado térmico; distinguir tempos por submissão de tempos por quadro.
3. Só para diagnóstico: `GPU|log_gpu_frame_time_breakdown=true` e `GPU|log_gpu_frame_time_breakdown_passes=false`, comparando a sobrecarga da instrumentação; não tentar timestamps detalhados por pass nessa build dedicada: a política Edge 30 os bloqueia para evitar distorção grave no Turnip; uma build diagnóstica diferente exigiria validação independente.
4. Testar diferentes drivers Turnip **apenas** com compatibilidade verificada e isolamento de variáveis; comparar três drivers no futuro não substitui a linha de base inicial.
5. Rodar `python3 tools/vulkan_diagnostics.py xe.log --last 30` e `python3 tools/compare-benchmarks.py --reference ... --candidate ...` com metadados válidos; inspecionar visualmente (sombras, transparência, depth, texturas, travas).
6. **Não atribuir ganho** a esta correção do script, pois ela não altera o runtime nem foi medida no aparelho.

## 5. Próximas decisões, com gates

| Hipótese | Evidência mínima requerida | Ação se confirmada |
| --- | --- | --- |
| Guest CPU 0 ocupa um núcleo em espera/re-poll | Perfil de fases + CPU time na mesma janela; sem custo excessivo do coletor | Reduzir repolls/wakeups sem remover semântica de notificação, com flag e teste de concorrência |
| A thread GPU Commands espera por pipelines | `pipeline_wait_stats` e `GpuFrame`, timestamps correlacionados | Pré-aquecimento específico por título, sem descartar draws |
| EDRAM/resolves dominam em condução | GPU busy/passes/tamanhos e perdas visuais verificadas no mesmo driver | Otimização conservadora de resolve/transfer com verificação de equivalência |
| Quedas decorrem de limitação térmica | Frequências/thermal status/headroom ao longo da execução | Política reversível, sem forçar clock ou fingir benchmark |
| Regressão entre builds | Capturas comparáveis, hashes/config efetiva, pelo menos 3 por braço | Reverter o commit causal ou manter o recurso atrás de flag |

**Fora de escopo neste incremento:** recompilação estática por jogo, portar código GPL incompatível sem revisão de licença, trocar arquitetura do scheduler, alterar APU, ativar skip draws ou mudar flags de precisão. Não foi produzida nem publicada uma nova beta nesta etapa.

## 6. Acesso real à instrumentação no build dedicado Edge 30 Ultra

A inspeção adicional revelou que a política `xe::edge30::SchedulerStats` devolvia **false sempre que `ZEPHYR_EDGE30_ULTRA=1`**. Portanto, nessa variante, nem mesmo um override `[Kernel] guest_scheduler_stats=true` habilitava os contadores ou a medição de fases do Guest CPU 0.

A correção preserva o perfil de desempenho padrão e destrava somente o diagnóstico solicitado:

- `guest_scheduler_stats` **mantém o default legado true**, sem invalidar arquivos de configuração já existentes. Na variante dedicada, esse valor por si só permanece inefetivo;
- o novo `zephyr_edge30_scheduler_diagnostics=false` (categoria `[Kernel]`) exige autorização **adicional** na versão dedicada, impedindo que um arquivo antigo com `guest_scheduler_stats=true` ative instrumentação inesperadamente;
- para coletar no Edge, configurar **ambos** `[Kernel] guest_scheduler_stats=true` e `[Kernel] zephyr_edge30_scheduler_diagnostics=true`, reiniciar e confirmar os logs; a variante genérica continua respeitando `guest_scheduler_stats` sem depender da segunda opção;
- o teste `tools/test-edge30-policy.py` executa produção do `IoWorkerLoop` e `CollectSchedulerStats` nos modos Edge e genérico, com o coletor ligado/desligado, verificando clocks, contagens, wakeups e conclusão I/O;
- `xe::edge30::DetailedGpuTiming` **permanece false** no dedicado: os timestamps detalhados por passe/resolve continuam desativados para evitar esperas BOTTOM_OF_PIPE excessivas no Turnip. O diagnóstico `light-timing` usa apenas os timestamps por submissão e estatísticas CPU do comando gráfico.

**Limite:** a coleta com as duas flags ligadas no Edge tem custo e requer teste A/B contra a mesma build com a coleta desligada; não confundir os dois braços com uma comparação de desempenho. Os testes de host não demonstram FPS nem segurança térmica no aparelho. Nenhuma modificação foi feita em afinidade, deadlines, preempção, timers ou número de workers.

## 7. Comparação reproduzível do custo de observação (incremento seguinte)

O comparador `tools/compare-benchmarks.py` agora admite um teste *separado* do custo da instrumentação. A verificação padrão continua rejeitando diferenças na chave `instrumentation` do `benchmark-context.json`. Apenas a opção explícita `--allow-instrumentation-change` permite essa diferença, **desde que** `commit` e `apk_sha256` sejam idênticos nos dois braços. Os modos também precisam ser homogêneos dentro de cada braço; o uso da opção sem duas modalidades diferentes é rejeitado.

Caso o próprio arquivo de configuração do título seja alterado para ligar/desligar o diagnóstico, o SHA-256 do arquivo mudará. Isso exige adicionalmente `--allow-config-change`. As flags autorizam a **comparação descritiva**, não verificam que as únicas CVARs distintas são as de diagnóstico: faça diff dos arquivos de configuração efetivamente carregados. Não alterar simultaneamente driver, frequência, shaders, resolução, cache, cap de FPS, estado térmico ou versão do APK.

Exemplo com três capturas independentes de cada condição, após preencher corretamente o `benchmark-context.json` em cada diretório:

```sh
python3 tools/compare-benchmarks.py \
  --reference performance-tests/edge30-off-1 performance-tests/edge30-off-2 performance-tests/edge30-off-3 \
  --candidate performance-tests/edge30-on-1 performance-tests/edge30-on-2 performance-tests/edge30-on-3 \
  --allow-config-change --allow-instrumentation-change \
  --output performance-tests/edge30-telemetry-overhead.json
```

A comparação exige seis diretórios diferentes e durações próximas (diferença máxima de 10%). O resultado usa **FPS de apresentação** e percentis dos intervalos apresentados: não converte isso em FPS guest, trabalho GPU ou benefício real de emulação. Se a coleta reduzir os FPS ou distorcer os p95/p99, desabilitar o diagnóstico durante os testes de desempenho; ativá-lo somente em sessões curtas de investigação. Não misturar as capturas diagnósticas e não diagnósticas na mesma amostra de eficácia.

## 8. Leitura conservadora das fases do Guest CPU 0

O analisador offline `tools/scheduler-diagnostics.py` interpreta exatamente as linhas `GuestSchedulerCPU0: window ...` emitidas pelo código nativo. Exemplo:

```sh
python3 tools/scheduler-diagnostics.py performance-tests/edge30-on-1/xe.log --last 30
```

O relatório separa `repoll`, `fiber` e `idle`: número de chamadas, taxas baseadas na **duração real da janela de log**, parede somada, média por chamada e maior chamada registrada. Rejeita linhas truncadas ou ambíguas; a primeira janela (`window 0 ms`) não entra em taxas. Em logs `xe.log` com várias execuções anexadas, uma nova linha `Storage root:` delimita sessão e o analisador descarta os contadores da execução anterior, evitando misturar jogos e capturas antigas. Se não houver janelas válidas, os agregados são `null` (dados indisponíveis), nunca o valor numérico zero.

**Interpretação:** estes são tempos de parede, sujeitos a preempção do host; `fiber` **não** significa execução PPC útil, `idle` **não** prova sleep do kernel e o somatório das três categorias **não** representa o uso total de CPU 0. Os tempos são arredondados pelo logger. Para investigar gargalo, correlacionar com CPU time do processo e com GPU Commands/VkFrameSync, dentro de capturas da mesma cena, e medir separadamente o custo de ligar a instrumentação usando o protocolo da seção 7. Nenhum desses campos sozinho comprova ganho de FPS ou resolve uma falha gráfica.

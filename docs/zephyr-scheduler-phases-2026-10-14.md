# Segunda captura e fases do worker CPU 0 — 14/10/2026

Base 05b494b, UI #71 aprovado. Build no aparelho 285eb7dc; nenhum ganho de FPS
atribuído a esta mudança. Logs/report/video brutos e identificadores ficam fora
do repositório.

## Captura real

Relatório ZIP 7384 bytes, CRC válido, SHA256
38a69e0b7a1cc9664526d1067f374f3e5dd1e012368d4c1dc1583085934da774.
Log ZIP 476967 bytes, CRC externo/nested válidos, SHA256
3d4d94210e0ef8e9ba528581f305baf1561fcc9ca8681e17bb5ad94bc9fafce8.
Possui duas sessões arquivadas e current. Análise numérica abaixo usa current,
pareado ao report por seis workers guest e 337 pipelines (todos coincidem).
Mesmo driver Turnip Mesa 26.3.0-devel git-30da39187d e build registrado no report.
Captura entre 10:38:21 e 10:40:50 UTC-4, 148,286 s, fim voluntário.

| Medida | Segunda captura |
| --- | --- |
| Guest CPU 0 (25 janelas) | 94,61% de um núcleo médio; pico 98,31% |
| Guest CPU 5 | 49,32% |
| GPU Commands (CPU, não GPU busy) | 26,84% |
| FPS mediano / percentil 5 | 16 / 11, por amostras guest de ~1 s |
| Amostras FPS <=20 | 70% de 130 amostras |
| Quadros no histograma | 2395 |
| Mediana / p95 / p99 de frametime | <35 / <151 / <185 ms |
| Quadros >=100 ms | 16,03% |
| JIT | 42063 traduções, zero falhas; 11110,35 ms somados |
| Pipelines | 337 criações, 114 ms somados |
| AAudio | 24646 blocos, zero concealed, 2 xruns |

Não comparar FPS 16 vs 26 como regressão de código: mesmo APK, durações/cenas,
cache e instrumentação distintos. CPU 0 novamente ocupado apesar de pipelines
muito baratos enfraquece compilação de pipelines como explicação única da
lentidão sustentada. GPU/renderização/resolve não foram excluídos.

Vídeo complementar: 99,683265 s de contêiner. Quadros visuais mostram trecho
escuro/cutscene com HUD 30 e condução com HUD menor; não medir frametime guest
pela cadência da gravação nem assumir pareamento exato com eventos do report.

## Scheduler: o que os contadores significam

guest_scheduler_stats=true confirmado no dump. 148 linhas de estatísticas:
476840 repoll passes, 428301 waiters re-readied, 723212 dispatches, 12 backstop
rescues, 6422 chamadas I/O. Janela mediana: 3057,5 repolls e 4745,5 switches;
máximos 11475 / 12314. Embora log rotule /s, são contagens resetadas a cada
ReportStatsIfDue: o intervalo é no mínimo 1000 ms e pode atrasar. Não converter
para taxa exata sem intervalo real. São agregados dos seis workers, não CPU 0.

- Repolls conta chamadas de RereadyBlocked, não quantidade de objetos testados
  nem tempo gasto no mutex/lista.
- forced preempts=0 conta somente escape de defers em IRQL elevado após 4096
  safepoints recusados. Não mede todas as preempções normais.
- 12 backstop rescues satisfizeram force_all && !poked com epoch alterado e
  deadline não vencido. O poked foi consumido antes de adquirir lock_; sinal
  novo entre exchange e leitura do epoch pode satisfazer esse tripwire mesmo
  com uma notificação pendente. Não declarar 12 perdas reais nem removê-lo.
- I/O queue max registrado 7187 us, maior média por janela 429 us.
  Não extrapolar ausência de I/O caro de outras chamadas/threads.
- Os dois dumps no current ocorrem no início (frame 0) e após PAUSED (2399).
  Não demonstram deadlock contínuo na condução.

AAudio teve duas reconstruções de stream em frames 448/466; saída restaurada.
apu_aaudio_log_stats=false continua efetivo, portanto sem curva de fila/taxa/
clipping/locks. Nenhuma amostra GPU/fences/pipeline wait. Não corrigir áudio
nem ativar opções gráficas agressivas a partir de contadores ausentes.

## Incremento: medir as fases de CPU 0, sem alterar políticas

Novo header SchedulerPhaseStats e log GuestSchedulerCPU0, somente com
guest_scheduler_stats ativo. Mede wall clock de cinco operações já existentes:
RereadyBlocked, SwitchTo e três formas de Wait do ready_event. Categorias
repoll / fiber / idle. Preserva mutexes, predicados, eventos, argumentos das
chamadas, quantum, affinities, prioridade, delays e fallback de waits.

CPU 0 possui os contadores sem atomics/locks novos. Outros workers usam apenas
Measure(false), que invoca a ação e retorna sem tocar counters ou clocks.
ReportStatsIfDue já é chamado só por CPU 0 e retira/reset os três buckets.
O log contém intervalo real entre relatórios, chamadas, parede somada e máximo
POR CHAMADA. Primeira janela usa intervalo 0 (bootstrap; não calcular taxa).
Não alternar a flag no meio da comparação: acumuladores não representam uma
janela uniforme depois de toggles.

Escopos:
- fiber inclui SwitchTo, execução guest e shims nativos até retornar à fiber
  de dispatch; também descheduling. Não é tempo exclusivo de PPC útil nem
  execução de uma única guest thread; spins dentro do guest ficam aqui.
- repoll inclui espera de lock e processamento da lista. Não é CPU útil.
- idle inclui chamada de Wait e atraso de escalonamento, inclusive sinal já
  disponível. Não prova espera real no kernel.
- DequeueReady, migration/suspend, reclaim, logging e demais bookkeeping
  permanecem fora dessas três fases. Não chamar a soma de todo o custo da CPU.
- Somente CPU 0, não tempos somados dos seis workers/GPU. Correlacionar com CPU
  time do report. Overhead habilitado precisa ser medido no aparelho.
- Desligado não há clocks adicionais do coletor, mas os clocks preexistentes
  do scheduler não foram removidos. Não afirmar zero custo de todo o core.

## Validação

Teste compila header real: 100000 chamadas off + 100000 off concorrentes com
zero clocks; 10000 gravações pelo owner; categorias/reset/rollback; 200
handshakes; overload chrono real. Runner remove somente os cinco wrappers e
compara todo RunLoop normalizado à baseline 05b494b: nenhum outro predicado/
operação mudou. Quatro regressões de seleção NDK locais aprovadas.

CI agora compila SETE TUs NDK Release, incluindo guest_scheduler.cc, sem link/
assemble/APK. Conferir aprovação antes de entregar novo build. Header test e
integração NDK não provam desempenho ou compatibilidade no telefone.
A captura atual usa 285eb7dc e NÃO contém o log novo. Precisará de APK posterior
validado para avaliar fases; não pedir ao usuário dados que esse build não emite.

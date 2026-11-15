# Beta 4 dedicada — Motorola Edge 30 Ultra

Alvo exclusivo desta beta: SM8475 / Snapdragon 8+ Gen 1, Adreno 730, 12 GB.
Base: beta 3 `7e9ddb2a`. Pesquisa e implementação em 15/11/2026.
A main é preservada. Pacote e certificado Research permanecem os mesmos.

## Auditoria e mudanças executáveis

| Caminho | Antes | Beta dedicada | Limite da conclusão |
|---|---|---|---|
| Compilação nativa | `-march=armv8-a` | `-march=armv8.2-a+fp16+dotprod+lse -mtune=cortex-x2 -mno-outline-atomics` | Ajusta o runtime compilado e permite LSE direto; não reescreve automaticamente o código guest emitido pelo JIT. Não usa SVE/Armv9 opcionais. |
| Pipelines Vulkan | 4 com a configuração da interface; 6 em auto numa CPU de 8 núcleos | No máximo 2 workers nas filas de criação e tradução inicial; 0 continua síncrono | Hipótese de menor disputa, não ganho de FPS medido. Pode aumentar o tempo de compilação inicial e pop-in com cache frio. |
| Scheduler | Contadores atômicos atualizados mesmo com relatório desligado | Na beta dedicada, medições e contadores de diagnóstico são eliminados por constante de compilação | Preserva `switch_seq`, filas, locks, deadlines, preempção, watchdog e conclusão de I/O. Não remove sincronização do jogo. |
| I/O do scheduler | 3 consultas de relógio por chamada, contadores e CAS do máximo | Sem consultas/atomics de diagnóstico na beta dedicada | Chamada executada uma vez, `done.store(release)` e WakeAll mantidos. |
| Diagnóstico fino Vulkan | Passes/resolves cronometrados quando habilitados no TOML | Não aloca os dois pools de medição detalhada, mesmo com configuração antiga ativada | Mantém caminho já existente com medição por passe desativada; métricas básicas por submissão e FPS continuam disponíveis. Nenhum ganho atribuído se o diagnóstico já estava desligado. |

O build dedica os flags ao runtime nativo inteiro, incluindo o backend ARM64,
tradutor gráfico e dependências recompiladas. O CI verifica a linha real de
compilação, incluindo a última opção `-march`, e compila os objetos de produção.
O header de política é testado em modos genérico/dedicado para detectar mudanças
acidentais fora da beta. Nenhuma seleção de dispositivo ocorre por draw/quadro.

## Compatibilidade que não custa FPS no aparelho

O CMake já exclui o JIT x64, D3D12, Metal, áudio SDL e frontends de desktop no
build Android ARM64. Apagar seus fontes não elimina trabalho adicional do APK.
As consultas de recursos Vulkan no início permitem usar o driver instalado
(Qualcomm ou Turnip) com segurança; não são um tradutor entre celulares.
Removê-las pode acionar uma extensão ausente e produzir device loss.

Não há evidência local que justifique eliminar barreiras, fences, readbacks UMA,
watches de textura ou precisão do PPC: são semântica do Xbox 360, inclusive
no Edge. Os 12 GB de RAM não alteram a memória guest do console. Aumentar caches
sem taxa de evicção/pressão observada não é uma otimização demonstrada.

Não foram forçados clocks, afinidade, modo GMEM, fast-math, frequência guest,
resolução, áudio ou redução de precisão. Não foi inventado perfil PGO.
A política experimental de fast cores já existente continua opt-in.

## Pesquisa primária e decisões

- Qualcomm: https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-plus-gen-1-mobile-platform
- ARM: https://support.arm.com/compute-ip/cortex-a710
- ARM: https://support.arm.com/compute-ip/cortex-a510
- LLVM Cortex-X2/mtune: https://reviews.llvm.org/D112459
- Android: https://source.android.com/docs/core/perf/performance-hint-api
- Android: https://developer.android.com/agi/sys-trace/threads-scheduling

`-mtune` seleciona o modelo de otimização sem exigir o conjunto completo de
instruções opcionais de `-mcpu=cortex-x2`. A ISA permanece um subconjunto do
SM8475, utilizável também pelos A710/A510; o sistema pode migrar threads.
Afinidade permanente pode impedir resposta térmica e redistribuição de carga.
A escolha de 2 workers é uma hipótese mensurável para os quatro núcleos fortes,
não uma afirmação de que os quatro A510 sejam inúteis.

## Testes e lançamento

- `tools/test-edge30-policy.py`: 266.514 combinações da política de workers;
  executa o corpo de produção do worker de I/O com fakes de host, nas duas
  configurações, com diagnósticos pedidos/desligados e timestamp presente/ausente.
  Confere execução única, publicação de done, wake e zero consultas de relógio
  quando o diagnóstico não mede. UBSan ativo. Não executa Vulkan nem ARM.
- `tools/test-scheduler-phase-stats.py`: preserva comparação do RunLoop com
  baseline congelado, removendo somente os wrappers de instrumentação.
- CI compila o alvo especializado e confirma flags de CPU, GPU e boot.
- O APK exige invariantes nativas, regressões da interface e atualizador antes
  de compilar/assinar. A publicação exige pacote e certificado permanente,
  tamanho e SHA-256 do asset; só então sai de rascunho como prerelease Research.

A beta 3 com atualizador já foi publicada como
`Zephyr-Research-v100014-7e9ddb2a`. A beta 4 mantém o esquema `100000 + run_number`
do mesmo workflow. Não altera versionCode de modo incompatível com o instalado.
No canal Preview, uma beta 3 instalada poderá oferecer a beta 4 quando publicada.
Uma versão anterior sem o atualizador precisa primeiro da instalação manual.

## Medição no aparelho

Não há ganho de FPS medido nesta implementação. Comparar beta 3 e beta 4 com
mesmo jogo/Title ID/versão, save, driver, resolução e opções. Cache aquecido e
percurso repetível; depois sessão de 20–30 minutos para temperatura. Registrar
FPS guest e p95/p99, artefatos e estabilidade. O orçamento de pipelines pode
piorar cache frio; não atribuir travadas de compilação a throughput do jogo.
Esta beta inteira compara compilação, workers e diagnóstico em conjunto;
não permite atribuir um ganho individual sem builds adicionais controlados.

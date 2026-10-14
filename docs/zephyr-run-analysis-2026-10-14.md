# Diagnóstico da execução real 285eb7dc — 14/10/2026

Relatório enviado pelo usuário em ZIP (run-report.json + timeline.txt).
8.301 bytes, CRC válido, SHA256
98c5273352942aebd0d70a7fe4db97bf148d11fd077ef947e9f1625f96788803.
Dados brutos, PIDs, TIDs, UUIDs e caminhos pessoais não são publicados.

## Identidade e escopo

Build instalado e registrado: 285eb7dc. Forza Horizon 2 (4D530AA4),
Motorola Edge 30 Ultra / SM8475 / Android 15. Execução encerrada pelo usuário,
445,727 segundos de início a fim; 405 amostras com FPS e 38 amostras idle.
Não chamar todas as amostras de gameplay: há boot, pausas, menus e perda de foco.
Não há marca temporal que pareie o vídeo 1000392058.mp4 com quadros deste relatório.

Driver custom Turnip, Mesa 26.3.0-devel git-30da39187d, API 1.3.363,
nome declarado Turnip Adreno 725. Nome do driver não redefine o hardware físico.
Frame generation: zero submissões sintéticas e zero segundos registrados.
Limite guest: 30 FPS. Display observado 60/90 Hz.

## Números reproduzidos dos histogramas

| Medida | Resultado | Unidade/limite |
| --- | --- | --- |
| FPS mediano | 26 | percentil de médias guest por amostra de aproximadamente 1 s |
| FPS percentil 5 | 13 | NÃO 1% low por quadro |
| Amostras FPS <=20 | 182 / 405 (44,94%) | não classificar por cena sem série temporal |
| Quadros no histograma | 9332 | guest present-to-present; não GPU busy |
| Tempo mediano | <34 ms | bucket 33, limite superior conforme RunPerformance |
| Tempo percentil 95 | <103 ms | bucket 102 |
| Tempo percentil 99 | <142 ms | bucket 141 |
| Tempo percentil 99,9 | >=250 ms | último bucket aberto |
| Quadros >=50 ms | 1330 / 9332 (14,25%) | soma de buckets 50 em diante |
| Quadros >=100 ms | 723 / 9332 (7,75%) | soma de buckets 100 em diante |

Contagem de presentSubmissions (9315) e histograma (9332) possuem baselines/escopos
distintos. Não corrigir um para igualar o outro nem dividir FPS por duração total
como se contagens/pausas tivessem a mesma janela.

## CPU real por thread do processo host

| Thread | Média de um núcleo | Pico de janela |
| --- | --- | --- |
| Guest CPU 0 | 96,86% | 99,42% |
| Guest CPU 5 | 49,80% | 69,01% |
| GPU Commands | 40,39% | 68,35% |
| Guest CPU 1 | 38,67% | 70,87% |
| XMA Decoder | 10,75% | 27,37% |
| Audio Worker | 2,62% | 3,58% |

80 janelas válidas para esses workers, leituras aproximadamente a cada 5 s.
GPU Commands é CPU do command processor, NÃO utilização da GPU.
Guest CPU 0 é um worker do escalonador que executa fibers guest; não afirmar
que é exclusivamente a main thread do jogo. 96,86% de um núcleo é forte
evidência de ocupação quase contínua; não distingue trabalho útil, busy-wait,
syscalls ou overhead do dispatcher.

CPU 0 foi visto nos processadores [0,2,4,5,6,7]. HostThreadCpu define isso como
processadores vistos NO INSTANTE da leitura, não residência, afinidade, frequência,
topologia big/little ou número de migrações. Não fixar afinidade por essa lista.

Coletor: 84 tentativas, zero leituras indisponíveis/incompletas/truncadas;
3151,30 ms somados, 37,52 ms/leitura média, máximo 209,58 ms.
Tempo de parede da leitura no monitor inclui escalonamento; não atribuir esse
máximo a stall do worker guest nem dizer que overhead é zero.

## Compilação e áudio

- JIT: 42243 traduções, zero falhas, 9244,37 ms somados de parede dos compiladores.
  Média 0,219 ms/tradução; histogramas de duração presentes. NÃO equivale a stall.
- Pipelines: 330 criações, 22485 ms somados. Evento de 12.254 ms ao longo de uma
  janela de 6 s demonstra por que worker time não deve ser chamado de pausa.
- Eventos de compilação concentram-se inicialmente. Após 146,155 s, a timeline
  registra duas janelas JIT (26/29 traduções, 19/16 ms) e quatro janelas pipeline
  (1/1/1/4 criações; 178/109/120/335 ms). Eventos resumidos não substituem cada
  amostra e ausência de evento não prova ausência de compilação.
- AAudio: 76432 blocos, 3 blocos concealed (0,00393%), 3 device xruns.
  Eventos de underrun resumidos aparecem no início; não há novos eventos audio
  depois de 49,823 s. Isso enfraquece underruns persistentes na saída como
  explicação única do relato, sem provar qualidade do conteúdo.
- Código AAudio possui ajuste de taxa habilitado por default que pode chegar a
  0,9x quando a fila esvazia, sem exigir xrun. Este relatório não contém curva
  de taxa/ocupação/clipping/latência de callback ou locks. Não afirmar que esse
  mecanismo foi acionado ou causou a distorção.
- Override explícito apu_aaudio_log_stats=false, apesar de default=true no
  pacote de testes. Explica ausência esperada do log detalhado de AAudio se
  mantido durante a sessão; não alterar áudio/default para mascarar sintomas.

Bateria registrada 36–38 °C não exclui limitação térmica. A timeline registra
thermal watch near limit com headroom 0,86 em 45,807 s. Não prova frequência
reduzida nem permite quantificar throttling.
GPU real/resolve/fences/pipeline-wait não são medidos neste ZIP de relatório.
Campos generationGpu vazios não significam GPU sem trabalho.

## Decisão

Prioridade: separar execução útil de Guest CPU 0 de espera ativa e custo de
escalonamento, depois correlacionar com frame pacing e produção de áudio.
O core já tem guest_scheduler_stats opt-in para repolls/switches/preemptions/I/O.
Não aplicar guest_yield_sleep_us, afinidade, quantum, context_promote_vec128 ou
remoção de waits sem uma captura que valide o caminho afetado e compatibilidade.

Compilação pode contribuir para travadas iniciais; os totais não demonstram que
ela explique desempenho baixo após aquecimento. GPU também não foi excluída.
Solicitar logs sanitizados da MESMA execução para sinais do scheduler e GPU,
sem exigir que o usuário tenha PC. Se opções opt-in não forem acessíveis pelo
app, investigar sua exposição antes de pedir comandos de shell.

Nenhuma otimização de runtime, áudio, política de scheduling ou default aplicada
nesta revisão. Base e760f0b com UI #69 aprovado; APK #50/CI #68 aprovados em 285eb7dc.
Esta etapa registra dados reais e muda a prioridade, não demonstra ganho de FPS.

## Correlação com logs complementares recebidos às 10h22

ZIP xendroid-logs-20261014-102145.zip: 132.447 bytes, CRC válido,
SHA256 32c942f6f939473e46b2e9a0e66d85070ff50bc9c565963134ea98934d718229.
Contém somente current/xe.log (22.557 linhas) e current/logcat.txt (2.283).
Não há contexto de build separado no ZIP. Correlação forte com o relatório:
os seis identificadores dos dispatch workers guest correspondem às threads
reportadas; PID aparece no logcat nos horários de início/fim; versão do driver
coincide; 330 linhas de pipelines criados correspondem às 330 criações.
Identificadores pessoais e logs integrais ficam fora do repositório.

### Os avisos do watchdog não provam três deadlocks de gameplay

Há três dumps de ausência de novos frames, nos frames 764, 813 e 9335.
O texto do watchdog indica 2000 ticks, NÃO uma medida exata de 2000 ms:
escalonamento e atraso do watchdog podem variar.

- Frame 764: não precedido de marcador PAUSED; há pipelines sendo criados
  antes e depois do dump. Últimos sinais registrados abrangem uptime guest
  34496–34576 ms. Contexto compatível com o burst inicial de compilação e
  início dos poucos underruns; não prova causa ou deadlock de scheduler.
- Frame 813: existe marcador EMULATOR PAUSED antes do dump, e RESUMED depois.
  Últimos sinais em 38493–38538 ms; relatório registra pausa/resume nesse trecho.
- Frame 9335: dump segue imediatamente o marcador EMULATOR PAUSED; últimos
  sinais 419099–419102 ms, próximos de menu opened, guest paused em 419148 ms.
  Não classificar a pausa controlada do menu como travada causada por colisão.

O jogo progride até mais de 9300 frames. Não há evidência aqui de deadlock
persistente. Todos os last_safepoint apresentados são zero: esses dumps não
fornecem amostragem confiável de hotspot PPC por endereço. LR não é PC atual.

### Configuração efetiva e dados ainda ausentes

guest_scheduler=true, quantum=1000 us, guest_scheduler_stats=false.
AAudio: um log de inicialização (48000 Hz, 2 canais, callback 256 frames,
buffer 1024/1024); apu_aaudio_log_stats=false. Nenhuma janela detalhada de
ocupação/taxa/clipping/tempo de callback/locks. Sem GpuFrame, VkFrameSync,
VkFences ou VkPipelineWait em xe.log/logcat. Ausência permanece desconhecido.

Não foram vistos DEVICE_LOST/VK_ERROR literais; isso não prova ausência de
falhas. Logcat também inclui sondagem inicial do driver system: não confundí-la
com o loader custom realmente usado pela emulação (confirmado por xe/report).

### Próximo teste possível no próprio app, sem novo APK

Código do build 285eb7dc já declara Kernel|guest_scheduler_stats no SettingsSchema.
SettingCatalog o inclui no grupo DEBUG, nível ALL; XdSettings pesquisa também
chaves técnicas e traduz ALL como “Tudo”. Não há tradução específica do título
no SettingTexts, então o título pode aparecer em inglês.

Com jogo encerrado: Ajustes, nível Tudo, buscar guest_scheduler_stats e ativar
somente essa opção no escopo correto. Confirmar no próximo config dump que
o valor efetivo é true (overrides de jogo podem prevalecer).
Captura curta com mesmo driver/patches/opções, trecho normal seguido de
aceleração/colisão, depois exportar relatório e logs. Desligar após a captura;
medir diferença de overhead antes de comparar FPS com execução sem coleta.
A opção gera estatísticas e não altera quantum/afinidade/prioridade por si.
Não pedir trace_function*, CPU debug ou logs de todas as chamadas de kernel.

Este complemento não aplica otimização nem prova que a espera ativa é a causa.
Permite direcionar coleta ao worker saturado usando funcionalidade existente.
UI #70 aprovado na base 91db30e; conferir CI do commit documental complementar.

# GPU / CPU–GPU: unidades e lacunas de diagnóstico — 14/10/2026

Base auditada: `27d93cce534099323ebe7ac758d84cbdee754124`, exclusivamente
`feature/zephyr-mobile-branding`. UI #62 aprovado antes desta alteração.
APK #46, solicitado por outra continuação, terminou; esta etapa não o disparou,
não alterou o gate e não gerou outro APK. Nenhuma medição nova no aparelho.

## Achado concreto

Os números do `VkFrameSync` não têm todos o mesmo denominador. Os utilitários
históricos `forza_segstats.py` e `forza_passres.py` estimam GPU/frame multiplicando
média GPU/submissão por submissões/frame; `forza_ab_report.py` multiplica médias
de relatórios separados. Isso não é uma medição exata de GPU/frame: submissões
novas e timestamps completados podem pertencer a janelas diferentes. Esses
scripts não foram modificados para preservar os relatórios históricos. Não
usar sua coluna GPU/frame como prova isolada de um gargalo ou ganho no Forza.

Novo `tools/vulkan_diagnostics.py` lê o log existente offline e mantém as unidades
originais. Não toca no aparelho, não liga instrumentação e não modifica cvars.
Os campos CPU/frame são ponderados pelo número de frames de cada janela; as
médias GPU/submissão continuam individuais, sem multiplicação nem atribuição de
gargalo. Logs ausentes produzem `null` nas médias CPU, não zero. Linhas inválidas,
truncadas ou com campos duplicados são rejeitadas e contabilizadas. GPU zero
permanece ambíguo: o formato não informa quantos timestamps válidos houve.

```sh
python3 tools/vulkan_diagnostics.py xe.log --last 30
python3 tools/test-vulkan-diagnostics.py
```

`--last 30` significa últimas 30 janelas válidas, NÃO 30 segundos. Usar um log
de uma única execução/condição; não concatenar braços A/B ou comparar menus com
gameplay. JSON vai para stdout e contém limitações explícitas. Dados textuais
são arredondados, portanto mesmo a ponderação correta continua aproximada.

## Mapa verificado no código de produção

| Campo / operação | O que mede | O que não prova |
| --- | --- | --- |
| `gpu exec avg` | `gpu_exec_ns / gpu_samples`: intervalo TOP→BOTTOM de uma submissão com timestamp válido | Tempo total por frame ou ocupação percentual da Adreno |
| `submit->fence` | Parede entre registro após submit e posterior observação da conclusão pela CPU | Execução pura da GPU; inclui atraso de coleta |
| `await` | Parede de `AwaitSubmissionAndUpdateCompleted` para pedidos não zero, dividida pelos frames | Tempo útil de CPU, ou custo independente que possa ser somado à GPU |
| `blocking` | Pedido maior que o completed index conhecido antes da atualização | Prova de que houve um `vkWaitForFences`; a atualização pode só colher um fence já sinalizado |
| `VkFences poll/wait` | Tempo dentro das chamadas do driver na timeline do command processor | Custo adicional ao `await`; os escopos podem ser aninhados; não cobre todas as timelines do presenter |
| `replay` | Do início do replay principal até o retorno de aquisição de fence/queue submit | Tempo puro de gravação de comandos ou espera por compilação de pipelines |
| `resolve_ms` | Resolve GPU/frame amostrado quando o anel detalhado existe | Zero custo de resolves quando os timestamps detalhados estão desligados |

Arquivos auditados em `emulator-core/src/main/cpp/xenia/src/xenia/`:

- `gpu/vulkan/vulkan_command_processor.cc`: `PerformSwap`,
  `CheckSubmissionCompletionAndDeviceLoss`, `EndSubmission` e criação dos anéis.
- `gpu/vulkan/vulkan_pipeline_cache.cc`: `EndSubmission`,
  `AwaitPipelineCompletion` e evento de conclusão dos workers.
- `ui/gpu_completion_timeline.h`: retorno antecipado para pedidos já completados.
- `ui/vulkan/vulkan_gpu_completion_timeline.{cc,h}`: polls, waits e coleta limitada.

## Lacuna a medir antes de otimizar

`pipeline_cache_->EndSubmission()` pode esperar pelos workers, mas ocorre antes
de `replay_begin_ns`. Replay de setup também fica fora desse intervalo. Tempo
somado de criação de pipelines nos workers não substitui medição do bloqueio
da thread GPU Commands. Há ainda `AwaitPipelineCompletion` em outros caminhos.

Próximo incremento possível: contador opt-in do tempo realmente gasto nas
esperas por conclusão dos pipelines, separado de execução GPU e de compilação
somada dos workers. Exige regressões de caminho desativado/ativado e concorrência,
e compilação NDK do arquivo afetado. Não foi implementado nesta etapa: diagnóstico
insuficiente para alegar que pipelines causam as quedas com colisões/NPCs.

Outro candidato menor: `TimelineNowNs()` e estatísticas de poll/wait são
atualmente atualizados mesmo quando o breakdown está desligado. O custo disso
no telefone não foi medido; não atribuir milissegundos ou FPS a dois clocks.

## Coleta conservadora e limites

Para primeira coleta GPU, verificar efetivamente
`log_gpu_frame_time_breakdown=true` e
`log_gpu_frame_time_breakdown_passes=false` ANTES da inicialização gráfica.
Nenhum default foi alterado nem allowlist contornada. O default de passes true
faz a flag principal ativar os anéis detalhados se não houver override. Mesmo
sem passes, timestamps/reset/copy têm custo; comparar com coleta desligada.
Nesse modo, resolve GPU fica sem amostra detalhada; não interpretar zeros como
ausência de custo. Só ligar passes numa segunda captura curta, se necessário.

`bounded_collection` permanece como estava. Os comentários sobre Turnip/kgsl
são histórico do projeto, não uma constatação do driver atual do Edge 30 Ultra.
Não ligar skip-draws ou remover fences para esconder uma espera: preservação de
draws e coerência de memória são requisitos de compatibilidade.

Fontes primárias para semântica de timestamp, consultadas em 14/10/2026:

- [Vulkan timestamp queries](https://docs.vulkan.org/samples/latest/samples/api/timestamp_queries/README.html).
- [vkCmdWriteTimestamp](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdWriteTimestamp.html).

O par de timestamps mede seu escopo de comandos/estágios, não uso percentual nem
tempo de CPU. Não extrapolar limites/counter wrap do aparelho sem consultar as
propriedades da queue family (`timestampValidBits`) e o driver efetivo.

## Validação e continuidade

Oito regressões sintéticas cobrem formato/unidades/maxima, ponderação por frames,
recusa de GPU/frame inferido, falta de dados, valores inválidos/duplicados,
janela parcial, zeros ambíguos e CLI JSON. Não são medições do telefone. CI passa
a executar o analisador; confirmar o novo HEAD antes de qualquer incremento.
Áudio, assinatura, JIT, sincronização GPU e defaults do app não foram alterados.

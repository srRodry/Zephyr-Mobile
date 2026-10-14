# Tempo de espera por pipelines — 14/10/2026

Base: `74f6968927577d45d43d3b62d88365d8148cd799`, UI #63 aprovado.
Branch exclusiva `feature/zephyr-mobile-branding`. Não foi solicitado APK.

## Lacuna e incremento

A auditoria GPU encontrou `VulkanPipelineCache::EndSubmission()` antes do
cronômetro de replay. Tempo de criação somado dos workers não informa quanto a
thread de comandos esperou. Novo coletor mede somente a chamada existente a
`xe::threading::Wait(creation_completion_event_.get(), false)` em:

1. `EndSubmission`: espera antes de reproduzir os comandos da submissão.
2. `AwaitPipelineCompletion`: espera explícita antes de consumir estado gráfico.

As decisões de esperar, mutex, reset do evento, notificações, política de
skip-draws e compilação dos workers permanecem inalteradas. A medição envolve a
mesma chamada uma vez; não a substitui por polling nem retira sincronização.
A espera de preload do armazenamento e join dos workers não são instrumentados.

## Ativação, unidades e custo

Usa exclusivamente `log_gpu_frame_time_breakdown`, já opt-in e false por padrão.
Nenhum default ou allowlist foi alterado. Preparar a coleta antes do boot, com
`log_gpu_frame_time_breakdown_passes=false` na primeira captura, conforme a
auditoria anterior. Não há novo JNI nem exportação automática para RunReport.

Desligado: chama Wait normalmente, sem clocks nem atualização de estatísticas.
Ligado e somente quando o código já decidiu esperar: duas leituras de
`steady_clock`, acumulação de calls/ns/max. A amostra inclui tempo de parede
da API e escalonamento do host; o evento pode já ter sido sinalizado antes da
chamada. `calls` NÃO conta necessariamente bloqueios reais do sistema operacional.
Não confundir com GPU busy, tempo útil de CPU ou tempo somado dos workers.

No mesmo intervalo de aproximadamente um segundo do `VkFrameSync`, o log inclui:

```text
VkPipelineWait: N frames | submission calls=X/fr time=Yms/fr max=Zms | explicit calls=X/fr time=Yms/fr max=Zms
```

Tempo/frame divide os ns acumulados pelos frames dessa janela; max é maior
chamada da janela, não maior frame. `TakePipelineWaitStats()` reinicia ambos os
buckets após a cópia. Snapshot e gravação pertencem à thread de comandos; workers
nunca tocam no coletor. Não é uma API de estatísticas compartilhadas entre threads.
Não alternar a flag no meio de uma comparação: uma janela que atravesse a troca
pode incluir contribuição anterior, assim como outros contadores do breakdown.

Os dois clocks e o log acrescentam custo quando habilitados. Comparar captura
ligada/desligada no aparelho antes de usar os resultados; zero custo observado
não é prova de ausência de espera fora destes dois pontos. O analisador offline
anterior continua tratando `VkFrameSync`; este novo log separado deve ser
preservado na exportação nativa, sem somar métricas com escopos sobrepostos.

## Validação

- Header real compilado em C++17 com otimização e pthread.
- 100.000 chamadas desligadas preservam Wait uma vez e fazem zero clocks.
- Categorias independentes, acumulação/máximo, reset, rollback defensivo de clock,
  overload chrono real e isolamento de instâncias.
- 200 handshakes com worker sinalizando conclusão concorrentemente e waiter
  como único proprietário do coletor; metade habilitada e metade desligada.
- Verificação dos dois call sites de produção: Wait uma vez, dentro do predicado
  existente e com a flag de diagnóstico. Isso é complemento, não execução Vulkan.
- Seletor NDK ampliado aos dois arquivos GPU afetados: `vulkan_pipeline_cache.cc`
  e `vulkan_command_processor.cc`, além dos três objetos CPU/JNI existentes.
  Continua sem link nativo, assinatura ou empacotamento de APK.
- Regressões novas entram no UI checks; conferir seu resultado no novo HEAD.

Nenhum FPS ou duração no Edge 30 Ultra foi medido. Esse incremento permite
testar se os stalls observados correspondem a esperas de pipelines; não prova
que esse é o gargalo, não modifica áudio e não justifica ligar skip-draws.

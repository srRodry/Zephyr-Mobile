# Auditoria da timeline de fences — 14/10/2026

Base `19f0340b338bea2f74c4e8324a67295c38fc7bca`, UI #65 aprovado.
Branch exclusiva `feature/zephyr-mobile-branding`. Sem alteração runtime/APK.

## Evidência e regressão

O header real `ui/gpu_completion_timeline.h` retorna imediatamente quando o
índice solicitado já está no completed index conhecido. Isso inclui zero:
uma chamada com zero não implica polling nem espera no driver.

Se o índice ainda não é conhecido como completo, a classe chama
`UpdateCompletedSubmissionUpTo` antes da possível espera e novamente após essa
fase, rechecando o completed index. O seletor real Vulkan usa:

```cpp
PollPendingFences(bounded_collection() ? awaited_submission : UINT64_MAX);
```

O novo teste compila o header de produção e essa definição extraída literalmente
do `.cc`, substituindo somente assertions e estados/chamadas do driver por um
fixture controlado. Não duplica o algoritmo de espera da classe base. Os hashes
locais foram comparados ao conteúdo do HEAD no GitHub:

- Base header: `bd09e025831d75849c312f6e4ad4f32ce5bc3daf`.
- Vulkan `.cc`: `1b99a22691318bb4105566aa4929d3037048f06b`.

Sete grupos verificam zero/cached-complete, conclusão conhecida só pelo driver,
pedido pendente, coleta de progresso adicional, falha de espera e helpers de
limite/all. 200.000 pedidos zero/cached-complete não fazem chamadas ao driver
falso. Resultados locais aprovados; o teste entra no CI.

Caso sintético: quatro submissões pendentes no cache da CPU, somente 1 e 2
sinalizadas no driver falso; pedido para concluir 2. A política limitada consulta
`[1,2]`; a sem limite consulta `[1,2,3,3]`, pois a segunda atualização volta a
consultar a terceira ainda não sinalizada. Nenhuma precisa chamar Wait explícito
nesse caso e ambas retornam completed=2 corretamente.

Isso demonstra consultas adicionais, NÃO tempo perdido real. Se consultar o
fence 3 for barato, a diferença pode ser irrelevante. Se o driver ativo bloquear
nessa consulta, pode haver espera além do pedido. O fixture não implementa
Turnip/KGSL/Vulkan e não mede nanossegundos nem FPS.

Outro caso mostra: com todas as submissões sinalizadas, o modo sem limite avança
até 4, enquanto o limitado só até 2. Isso muda quando recursos são reclamados;
não é apenas um contador. Há tratamento específico de readbacks UMA quando a
coleta limitada está ativa. Política e defaults permaneceram intactos.

## Validação necessária no aparelho

1. Confirmar build/hash, driver ativo, valores efetivos, mesmo trecho e caches
   aquecidos. Não transportar relato de outro driver/aparelho para o Edge 30 Ultra.
2. Coleta inicial: breakdown opt-in ligado antes do boot e passes=false; verificar
   overhead com coleta desligada, sem ativar passes automaticamente.
3. Preservar VkFences (polls/slow/poll_ms e waits/wait_ms), VkFrameSync,
   VkPipelineWait e CPU por thread. Zero Wait explícito não prova ausência de
   tempo em polls. O limiar slow do código é 50 us, não prova isolada de bloqueio.
4. Só comparar limitado/sem limite com suporte ao override existente e ambiente
   controlado. Não mudar default nem contornar permissões/allowlist. Verificar
   readbacks, memória guest, encerramento e correção gráfica.
5. Não somar tempos aninhados de await/fences e GPU para deduzir FPS.

`TimelineNowNs()` ainda mede polls/Waits mesmo sem breakdown; o relatório GPU
drena esses contadores. Há duas leituras de relógio por chamada medida. Custo
no aparelho não foi medido: não prometer FPS nem mudar instrumentação sem testar
seu ciclo de vida.

O NDK passa a compilar também `vulkan_gpu_completion_timeline.cc`: seis unidades
CPU/GPU/UI/JNI, sem link ou assemble/APK. Os testes do seletor mantêm seleção
estrita. Conferir o novo CI; objetos não substituem link completo.

Nenhuma otimização de sincronização, áudio ou default foi aplicada. Próximo passo
é obter tempos do driver no telefone antes de decidir se as consultas adicionais
pertencem ao caminho crítico. Evidência sintética não comprova gargalo real.

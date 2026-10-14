# Medir compilação CPU antes de atribuir travamentos ao JIT

Branch exclusiva `feature/zephyr-mobile-branding`. Base documental `dacc5c4`,
UI #53 aprovado. Não gerar APK à noite. Áudio/interface permanecem suspensos.

## Lacuna e implementação

Já havia métricas de criação de pipelines gráficos. Elas não medem tradução
PowerPC→ARM64. O novo escopo em `PPCTranslator::Translate` cobre scanner,
construção HIR, otimização, montagem, dumps opcionais e limpeza de pools. Mede
**tempo de parede** de traduções finalizadas, bem-sucedidas ou falhas; não mede
execução do código guest, espera anterior para obter um translator, nem duração
exata de um travamento. Um abort do processo não finaliza o escopo.

A coleta é desligada por padrão e ativada antes do boot somente pelo pacote
`.zephyr.test`, via setter JNI. Pacotes normais fazem apenas uma leitura de flag
por tradução, sem relógio/mutex/histograma. Nenhum contador é inserido nas
instruções que o guest executa. Quando ativa: duas leituras monotônicas e uma
atualização breve sob mutex por tradução; snapshot coerente sob o mesmo mutex.
O custo no telefone ainda precisa ser avaliado. Não há gravação/upload por função.

O JNI conserva os três valores históricos de `shader_compile_stats` e acrescenta
um sufixo opcional de nove: traduções concluídas, soma dos ns de parede, falhas e
seis buckets (<1/<4/<16,7/<33,3/<100/≥100 ms). Off ou biblioteca antiga: apenas
prefixo, JIT ausente, nunca falsamente zero. Setter indisponível em biblioteca
antiga deixa diagnóstico indisponível sem impedir o boot; o host registra aviso.

`RunPerformance.jitCompilation` conserva deltas após a primeira leitura, inclusive
após reinício dos contadores. A primeira leitura é baseline: não atribuir ao
resumo compilações que aconteceram antes dela. Falhas de leitura/sufixo malformado
são ignorados. Tempo somado de workers pode exceder tempo real decorrido: **não
converter em uso de CPU, FPS ou tempo de frame**. Novos eventos `jit_compile`
registram janelas com ≥16,7 ms somados, separadas de criação de pipeline. Podem
acontecer no boot; o evento de primeiro frame ajuda a separar fases. Janelas de
um segundo também não localizam precisamente a colisão. Preview mostra contagem,
falhas, soma e todos os buckets antes de compartilhar. Relatórios antigos têm
campo nulo. Não muda regras de emulação nem opções de compatibilidade.

## Regressões

Teste nativo compila o header de produção e verifica caminho desligado sem
leituras de relógio, sucesso/falha/retorno antecipado, flag capturada pelo escopo,
rollback sintético, bordas dos seis buckets e 120.000 gravações concorrentes com
snapshots coerentes. Não substitui compilação ARM64 completa ou teste no aparelho.
Checker JNI compara método Java, tabela de registro e assinatura C++ (91 natives,
90 registros, um símbolo exportado). Regressões JVM verificam prefixo antigo,
unidades ns→ms, cópia defensiva, resets, dados malformados e preview/serialização.
CI executa essas regressões, além das anteriores. Registrar seu resultado após
commit; não presumir aprovação. Compilação nativa Android completa fica para a
preparação matinal, conforme autorização do usuário.

## Achados que ainda são hipóteses

`a64_perf_map=true` escreve um mapa de símbolos com mutex e `fflush` para cada
função colocada no cache. Isso serve ao simpleperf/perf; `fflush` não é `fsync`
e não prova gravação física síncrona em flash. O custo desse caminho está dentro
da tradução medida, mas não sabemos se pesa nos eventos do usuário. Não foi
desativado nem alterado sem comparação.

Promoção de contexto escalar já ocorre com `CPU|debug=false` e
`CPU|disable_context_promotion=false`. O log do translator registra estado
real. `context_promote_vec128=false` tem motivos documentados no próprio código:
regressão de pose no Halo 4 e teste Forza sem ganho. Não habilitar globalmente por
intuição. `CPU|debug` é distinto de APK Android debuggable; habilitar debug CPU
retira essa otimização.

A medição por thread também não distingue trabalho útil de spin. Se o relatório
não bastar, há [System Tracing no próprio Android](https://developer.android.com/topic/performance/tracing/on-device)
para coleta pelo celular, com compartilhamento de arquivo Perfetto. Disponibilidade
e categorias no Motorola ainda precisam ser verificadas. Não prometer perfis
nativos completos por essa coleta: simpleperf, símbolos JIT e análise de chamadas
podem exigir outras ferramentas. O projeto já produz mapas de símbolos; não
precisamos reinventá-los. Não é necessário ativar timestamps GPU por passe,
conhecidamente perturbadores, para coletar contadores de compilação CPU.

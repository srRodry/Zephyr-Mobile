# Zephyr — investigação da noite de 13 para 14/10/2026

Fuso do usuário: America/Boa_Vista (UTC−4). Branch exclusiva:
`feature/zephyr-mobile-branding`. O APK fica para a manhã de 14/10.
A meta de testes públicos é sábado, 17/10; não há autorização para release oficial.

## Autorizações e direção

O usuário passou a priorizar diagnóstico do caminho crítico após testes de
configurações com desempenho semelhante. Áudio e interface ficam suspensos.
Há autorização para investigar fontes externas, implementar correções necessárias
com regressões e commits na branch e produzir um APK experimental amanhã.
Não prometer trabalho contínuo: foram criadas etapas agendadas, de hora em hora
entre meia-noite e 6h, e preparação matinal em torno de 8h. Execução/compilação
futuras podem falhar; verificar resultados, não presumir sucesso. Após 7h, somente
leitura e síntese para não conflitar com a preparação do APK.

## Controle de APKs

O job APK de `XenDroid.yml` não executa em pushes de desenvolvimento comuns.
A UI/regressão permanece automática. Um dispatch manual na branch ou um commit
explicitamente solicitado com `[zephyr-apk]` libera o APK de teste. **Não usar essa
marca nos commits noturnos, nem mesmo no corpo da mensagem.** Main e PRs mantêm
seu comportamento anterior; a main não foi editada. Caso não exista ferramenta
para dispatch, a preparação matinal pode fazer uma mudança de comentário no
workflow com a marca, após conferir CI aprovado e que o código não mudou.
Apenas comentário de disparo não exige nova implementação. Conferir o CI desse
commit e a compilação antes de entregar. O pacote dessa branch é
`xendroid.compose.zephyr.test`; releases oficiais são ignoradas.

## Primeiro incremento: CPU por thread

Arquivos: `sessions/HostThreadCpu.kt`, integração em `EmulatorHostActivity`,
`RunPerformance` e `RunReport`, regressões de parser/acumulador/leitor/exportação.

- Ativo somente no pacote experimental `.zephyr.test`; sem mudança de layout ou APU.
- Leitura de `/proc/self/task/*/stat` em Dispatchers.IO a cada 5 s durante gameplay;
  no máximo 128 threads por leitura, 256 identidades acumuladas e 32 no relatório.
- Usa `sysconf(_SC_CLK_TCK)` e delta de utime+stime para CPU relativa a **um núcleo**.
  Uma thread com 100% equivale a um núcleo ocupado, não à CPU inteira do telefone.
- Identidade inclui TID e starttime para evitar que reuso de TID crie falso pico.
  Pausa, leituras ausentes, contadores reiniciados e lacunas >15 s renovam baseline.
- Média ponderada pelo tempo, pico por janela e CPUs vistas no instante da leitura.
  CPUs vistas não são residência, afinidade nem clocks. Janela de 5 s não mede
  o frame individual e pode diluir um travamento curto.
- Custo total/máximo das leituras e falhas ficam no relatório. Telemetria tem custo:
  medir esse custo no aparelho antes de adotá-la numa versão pública permanente.
- Nomes de threads são redigidos na cópia de compartilhamento; o usuário vê todas
  as threads incluídas antes de enviar. Sem upload automático.
- Relatórios antigos continuam legíveis. O JSON da execução compartilhada passa
  a incluir `performance.hostThreadCpu`.

Isso separa execução de CPU por thread, mas NÃO distingue código útil de spin,
conteúdo do JIT, espera de GPU ou frequência dos núcleos. A thread `GPU Commands`
mede CPU que prepara comandos, não a execução da GPU Adreno. Nenhum FPS ganho é
atribuído à instrumentação.

## Próximas etapas, em ordem

1. Conferir o CI deste incremento e o workflow APK ignorado. Corrigir falhas antes
   de novas alterações. Não compilar APK para "ver se melhorou" durante a noite.
2. Auditar pressão de registradores do A64, spills, chamadas de helpers, branches
   e sincronização de stack. Há sete registradores inteiros alocáveis (x22–x28);
   isso é candidato, não gargalo demonstrado. Expandir o conjunto exige preservar
   ABI, callbacks, thunks, safepoints e retorno ao host; não trocar um contador.
3. Inspecionar contadores existentes de GPU/resolve/espera. Integrar métricas
   precisas e de baixo custo ao relatório quando possível. Tempos de criação de
   pipeline já existem, mas não cobrem toda execução gráfica. Timestamps podem
   perturbar o trabalho e devem ser opt-in para investigação quando necessário.
4. Investigar uma alteração estrutural apenas com evidência: evitar trabalho
   repetido, substituir tradução genérica por uma variante equivalente elegível,
   ou retirar espera redundante. Teste deve comparar semântica relevante, não só
   confirmar que uma flag muda.
5. Registrar fontes externas primárias e experiências de primeira mão. Distinguir
   driver/build/jogo/aparelho, cache frio/quente, FPS do guest e interpolação.
   Tutoriais, vídeos editados e contadores agregados não comprovam causalidade.
6. Preparar comparação controlada amanhã no Edge 30 Ultra: mesmo trecho, driver,
   patches, configurações e caches aquecidos; 120 s e validação de 10 min. Exportar
   relatório. Confirmar driver ativo e valores efetivos antes de concluir nada.

## Lições que evitam repetir trabalho

- Switch NCE e PS4 x86-64 em PC não têm o mesmo caminho CPU do Xbox PowerPC→ARM.
- Release já usa O3/ThinLTO. O hash de descritores já usa scratch e XXH3 reutilizados.
- `docs/gw-gpu-bottleneck-investigation.md` contém hipóteses posteriormente
  refutadas. Ler o resultado 12.7: reduzir renderArea não mudou trabalho nem FPS.
  As medições são RP5/Geometry Wars, não Edge 30 Ultra/Forza Horizon 2.
- `docs/forza-poco-f7-shader-optimization.md` trata Forza Horizon 1/Adreno 825.
  É pista técnica, não resultado transportável para Forza Horizon 2/Adreno 730.
- A correção POSIX do commit b8fafecc retira consultas de resolução e corrige
  unidades, mas não resolve um caminho crítico de 16,7 ms necessário para 20→30 FPS.
- Perfis locais experimentais existem em `app/src/test/resources/zephyr-performance-experiments.json`;
  não estão ativos nem validados no aparelho. Resultados neutros precisam de
  confirmação de que cada valor foi efetivamente aplicado.

## Critérios para sábado

CI aprovado, assinatura/integridade identificadas, inicialização e encerramento
sem falhas nos testes, dados/perfis preservados e resultados por jogo/build/driver.
Não declarar compatibilidade geral a partir do Forza nem prometer 30 FPS estáveis.
A assinatura persistente continua pendente até o usuário ter computador: não
expor chave/senha nem apagar dados para superar conflito de certificados.
Testes públicos devem ser descritos como experimentais, com limitações claras.

## Registro de continuidade

Cada execução agendada deve acrescentar abaixo: commit, problema concreto,
verificação, CI, limitação e próxima etapa. Se não existir correção segura, não
inventar uma. Consulta a fóruns/vídeos não requer login nem contato com autores.

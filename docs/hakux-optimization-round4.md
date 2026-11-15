# Rodada 4 — hakuX, recompilações e gargalos transferíveis

Pesquisa e implementação em 15/11/2026. Alvo: Edge 30 Ultra, Snapdragon 8+ Gen 1,
Adreno 730. Base: `96042dbd03f1c832f7de6bd9b002c54342e4ac68`, rodada 3.
Branch isolada: `research/hakux-optimization`. Não altera a main.

## Resultado e limites

Duas otimizações novas de produção: extração do cache Vulkan por uma cópia
privada, e empacotamento de constantes float4 em blocos contíguos. Ambas têm
controle A/B no catálogo avançado e quatro perfis experimentais para o Edge.
Não há medição nova de FPS, temperatura ou frame time no telefone. Os testes
locais verificam dados, limites, falhas e vida útil; não medem um driver Vulkan.

A rodada 3 já trouxe codegen ARM64 de permutações/truncamentos, memoização de
texturas/views e melhorias ZAR. Esta rodada parte desse código e não contabiliza
essas mudanças como novidades. Ver `optimization-round3-research.md`.

## O que mudou no hakuX

Snapshot inspecionado: `rfandango/hakuX`, master
`a7cdb5a975e31730d488c47f6f10440b16c1e6f4`, de 31/10/2026.

A versão 1.0 substituiu o núcleo QEMU por um fork FEXCore x86→ARM64 e kernel
Xbox reimplementado. O renderizador decodifica o push buffer NV2A para Vulkan.
Essa mudança reduz camadas, mas o README/changelog não fornece um benchmark
controlado que permita atribuir um fator universal ao novo núcleo. Os mods de
60/120 FPS são alterações por título, não prova de velocidade geral do emulador.

O salto não é uma evolução do hardware do Xbox original: é do software que o
emula. O Xbox 360 tem CPU PowerPC/VMX128 e GPU Xenos/EDRAM. FEX, o kernel Xbox
e o decodificador NV2A não substituem os componentes equivalentes do Zephyr.

### Matriz de aplicabilidade

| Técnica observada | Estado no Zephyr / decisão |
|---|---|
| Kernel nativo + JIT direto | Zephyr já usa HLE Xenia e JIT PPC→ARM64. Não adicionar FEX, que traduz outra ISA. |
| Cache de pipelines por título, gravação só quando sujo | Já existe no Zephyr, com worker, intervalo de 20 s e validação por identidade do driver. |
| Serializar cópia privada do cache | Novo nesta rodada. `vkMergePipelineCaches` copia o conteúdo para um destino privado; só esse destino é serializado. |
| Menos consultas por draw e descritores reaproveitados | Já há memo de texturas/views, gate de descritores, fast sampler e último pipeline. Não duplicar estruturas. |
| NEON para unswizzle | O layout NV2A difere do Xenos. Zephyr já tem loaders compute coalescidos; não substituir por um algoritmo de outro console. |
| Evicção sem waits/downloads desnecessários | Princípio útil. Remover espera requer provar vida útil e coerência de CPU/GPU, inclusive readbacks; nenhum bypass global aplicado. |
| Evitar redraw quando nada muda | Precisa preservar overlays, UI e novas submissões. O presenter do Zephyr tem fluxo próprio; nenhuma supressão baseada só no endereço do framebuffer. |
| I/O que espera só a thread solicitante | Semântica de kernel distinta. Não importar scheduler do Xbox original nem alterar timings de I/O sem regressão de jogos/saves. |
| Quirks por driver e compatibilidade Vulkan | Já existe seleção por features. Evitar aplicar um workaround Mali ao Adreno 730. |
| Cache SPIR-V persistente | Candidato posterior: precisa incluir versão do tradutor, features e modificações na chave, não apenas hash do shader. |

Fontes hakuX: [README](https://github.com/rfandango/hakuX/blob/a7cdb5a975e31730d488c47f6f10440b16c1e6f4/README.md),
[changelog](https://github.com/rfandango/hakuX/blob/a7cdb5a975e31730d488c47f6f10440b16c1e6f4/CHANGELOG.md),
[pcache.c](https://github.com/rfandango/hakuX/blob/a7cdb5a975e31730d488c47f6f10440b16c1e6f4/hw/xbox/hle/vk/pcache.c),
[timeline.c](https://github.com/rfandango/hakuX/blob/a7cdb5a975e31730d488c47f6f10440b16c1e6f4/hw/xbox/hle/vk/timeline.c),
[swizzle](https://github.com/rfandango/hakuX/blob/a7cdb5a975e31730d488c47f6f10440b16c1e6f4/hw/xbox/hle/tex/xg_swizzle.c).
Implementação própria no Zephyr, sem importar arquivos GPL do hakuX.

## Recompilações e descobertas adicionais

| Projeto / fonte primária | Evidência e decisão |
|---|---|
| [XenonRecomp](https://github.com/hedge-dev/XenonRecomp) | Converte PPC para C++; não entrega runtime. AOT por jogo não é substituto imediato do JIT geral. |
| [ReXGlue](https://github.com/rexglue/rexglue-sdk) | SDK/runtime AOT. Suporte ARM64 desktop não significa suporte Android; forks têm adaptações próprias. |
| [Forza ReImagined: Android](https://github.com/FH1-ReImagined/ForzaHorizon1-ReImagined/blob/main/docs/android-port.md) | O documento ainda descreve estudo e etapas de port. Não há fundamento para anunciar que seu runtime Android foi transplantado ao Zephyr. |
| [Rayman: desempenho Android](https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/ANDROID_PERFORMANCE.md) | Relata 43–46→60 FPS ao eliminar recriação de swapchain por SUBOPTIMAL; espera de áudio 73%→7% de um núcleo ao trocar polling. São resultados do port e aparelho descritos. Zephyr já trata suboptimal persistente sem recriação em loop (`Presenter::PaintAndPresent`) e commit de memória usa Protect/mprotect; não havia motivo para repetir esses patches. |
| [CrownPark: mudanças ReXGlue](https://github.com/CrownParkComputing/Xbox360-Native-Ports/blob/main/docs/REXGLUE_FORK_CHANGES.md) | Identifica custos em registros, bindings e constantes. Zephyr já evita fetch writes idênticos e tem escrita em bloco; faltava agrupar cópias de constantes densas, implementado agora. Caches de estado com chave mascarada exigem auditar todos os registradores lidos; adiados para não reutilizar pipeline incorreto. |
| [FEX 2611](https://fex-emu.com/FEX-2611/) | Ganhos publicados de ~1,9× em SHA256 e ~5× em divisão de 128 bits são microcaminhos x86. Não são ganhos de FPS PPC nem instruções que se podem simplesmente ativar no Xbox 360. |
| [Xendroid Mali](https://github.com/DEGROUCHO/xendroid-mali) | Resultados em Mali/MediaTek e workarounds específicos não qualificam um preset Adreno. O custo de render passes/banda merece medição no próprio telefone antes de novos atalhos EDRAM. |

A documentação de Rayman contém resumos antigos de bring-up ao lado de resultados
posteriores. O relatório de desempenho e o código de cada alteração são mais
úteis que interpretar uma frase antiga de status como limite atual de todo port.

IA aparece no processo de desenvolvimento e revisão, inclusive no HANDOFF do
hakuX. Não é um acelerador automaticamente instalado na emulação. O resultado
vem de algoritmos e remoção de trabalho demonstráveis. Nenhuma inferência neural
foi adicionada ao frame loop, e quantidade de código gerado não valida otimização.

## Implementação: cópia do cache Vulkan

Chave: `Vulkan|vulkan_pipeline_cache_save_snapshot`, padrão true nesta branch de
pesquisa. Mantém worker e dirty/retry existentes. Cria cache vazio, faz merge do
cache ativo, extrai somente da cópia e destrói a cópia em todos os caminhos.
Falha não dispara leitura oculta do cache ativo. Desligar usa o caminho direto
para comparação. A API é Vulkan 1.0; destino e origem são distintos.

A [especificação](https://docs.vulkan.org/refpages/latest/refpages/source/vkMergePipelineCaches.html)
exige sincronização do destino: neste caso, ele só pertence ao worker. O cache
ativo continua com sincronização interna padrão e vida útil protegida pelo join
no shutdown. Compilações concorrentes continuam marcando dirty para próximo save.

A serialização tem limite de 256 MiB menos o envelope antes da alocação do blob;
VK_INCOMPLETE, zero e tamanho final inconsistente são recusados. O limite não é
um teto para a memória interna do driver: a cópia pode consumir memória adicional.
O merge também pode disputar locks. Redução de contenção é uma hipótese a medir,
não uma garantia. Logs incluem snapshot, extract_ms e write_ms por salvamento.
Esses tempos não são duração de frame ou tempo bloqueado do render thread.

## Implementação: constantes contíguas

Chave: `Vulkan|vulkan_coalesce_float_constant_uploads`, padrão true. Quatro máscaras
de 64 bits selecionam os mesmos 256 registros float4 em ordem. Sequências de bits
1 viram uma cópia; lacunas não são copiadas. Máscara totalmente densa: quatro
cópias em vez de 256, mesmos 4.096 bytes. Isso é contagem de operações, não 64× FPS.
Máscaras esparsas podem não ganhar. Bits de NaN, infinito e zero negativo são
preservados. A subida completa de registros do placeholder intérprete permanece.
O caminho desligado copia registro por registro para permitir A/B.

## Validação e comparação

- Teste de constantes: 87.619 máscaras nos dois modos, contra um empacotador
  independente; todos os intervalos contíguos de 64 bits, 65.536 padrões de 16
  bits, padrões aleatórios, guardas e destino desalinhado. ASan + UBSan.
- Teste do extrator real do cache com driver fake: sucesso, crescimento concorrente,
  tamanho reduzido, limites, falhas de create/merge/leitura, VK_INCOMPLETE e
  limpeza em todos os retornos. ASan + UBSan. Não executa driver real.
- Oito guardas de agendamento/dirty/shutdown do cache passam; o teste existente
  de persistência em disco passa; os testes de cache/VFS da rodada 3 passam.
- Workflow beta4 exige os dois novos testes antes do APK. CI Kotlin e compilação
  ARM64/NDK estão pendentes; não há SDK/NDK configurado neste host.
  Envio da branch bloqueado pela revisão automática, aguardando autorização
  explícita para esse destino; o workflow remoto não foi iniciado.
- Inventário do schema corrigido para 180 entradas/126 bools: a base tinha 178
  entradas/124 bools, mas os asserts antigos ainda esperavam 176/122.
- A tentativa da suíte nativa ampla local encontrou exigência SSSE3 no teste de
  áudio preexistente, sem relação com estas mudanças. Não é teste de integração
  Android aprovado. LeakSanitizer local é desativado por incompatibilidade com
  ptrace; ASan/UBSan permanecem ativos; CI mantém leak check padrão.

Perfis experimentais rodada 4 isolam snapshot on/off e constants on/off. Restore
antes de mudar, confira a prévia efetiva e reinicie o jogo. Use mesmo driver,
resolução 360p, título/versão/save e percurso. Compare cada opção separadamente,
com cache aquecido e A/B intercalados. Para snapshot, inclua trecho com novas
compilações por mais de 20 s: cache inteiramente estático pode não salvar nada.
Depois teste 20–30 min para aquecimento. Julgue frame time p95/p99, FPS guest,
artefatos, falhas e temperatura; frames interpolados não contam como simulação.

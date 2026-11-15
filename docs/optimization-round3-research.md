# Rodada 3 — recompilação, ARM64 e custo de tradução gráfica

Pesquisa e inspeção de código: 15/11/2026. Alvo: Motorola Edge 30 Ultra,
Snapdragon 8+ Gen 1 / Adreno 730. Base Zephyr `aad470ff`; branch de pesquisa
`research/optimization-round3`. A `main` e a beta 2 são preservadas.

## Decisão executiva

Manter o JIT ARM64 como caminho geral. Transportar melhorias pequenas com
invariantes verificáveis, em vez de substituir o runtime por um recompilador
estático ou misturar atalhos de precisão. Esta entrega modifica código executado:
permutações vetoriais, truncamentos usados por comparações, consultas de texturas
e views, fila de carregamento de texturas e abertura de arquivos ZAR.

Não há teste novo no telefone, ganho de FPS medido, correção confirmada da falha
do Forza ou promessa de 30 FPS sustentados. Os ganhos do POCO F7/Adreno 825, de
PCs ou do Apple M4 não são resultados do Edge 30 Ultra. Reduzir o custo de uma
operação só aumenta FPS se ela estiver no caminho crítico do quadro.

## O cenário novo e o que realmente se transfere

| Frente | Constatação em fonte primária | Decisão para Zephyr |
|---|---|---|
| XenonRecomp, hedge-dev | Traduz executáveis PPC para C++; não fornece runtime. Locais por função reduzem tráfego do contexto, mas pressupõem ABI e compatibilidade do título. ARM64 usa SIMDe. | Inspira redução de trabalho no JIT. Não converter automaticamente todos os jogos nem remover salvamentos/registradores sem análise de liveness. |
| ReXGlue SDK v0.11.0 | Runtime e gerador AOT baseados em Xenia, com alvos ARM64 de desktop. Detectar ARM64 no build não implementa o Android. | Bom candidato para um port individual; não intercambiável com o runtime/JIT atual sem adaptação. |
| Skate3-Mobile | Recompilação ARM64 e renderizador Vulkan próprio reconstruindo submissões do jogo. O perfil de desempenho reduz resolução, distância e efeitos. | Evidência de port individual viável. Hooks e substituição de renderizador de Skate 3 não são otimizações genéricas de Forza. Nenhum código gerado de jogo foi importado. |
| Forza Horizon 1 ReImagined | Documento Android verificado diz que nada está portado e não há APK Android. Execução ARM64 em M4 e compilação Bionic são etapas, não validação Snapdragon. | Acompanhar o bring-up. Não anunciar um Forza AOT para o Edge nem incorporar um runtime incompleto ao fluxo normal. |
| nimauria/Xenon-Recomp | Runtime AOT experimental; README não confirma jogo comercial jogável. Android/ARM64 ainda não são plataforma entregue. | Referência arquitetural; fora do candidato executável desta rodada. É projeto distinto de hedge-dev/XenonRecomp. |
| xemu oficial | JIT DSP56300 foi integrado para o áudio do Xbox original. | DSP e CPU/GPU do Xbox original diferem do Xbox 360. Não importar esse JIT para a APU XMA do Zephyr. Áudio continua fora desta rodada. |
| Mainkill1/xemu | Comparação publicada A/B de 48 tentativas mostra ganhos e regressões por máquina e cena, não aceleração universal. | Reaproveitar o método e princípios de evitar consultas/recompilações, mantendo identidade e invalidação; não importar NV2A/TCG diretamente. |
| N64Recomp | Tradução estática MIPS e runtime separado; overlays e chamadas indiretas continuam exigindo serviços do runtime. | Confirma a separação entre tradução e compatibilidade, não um plugin universal PPC. |
| RPCS3/ARMSX3 | Há propostas ARM64/NEON e um relato de macros LSE2 com atomics lentos. SPU, RSX e os hosts do relato não são o nosso runtime. | Fonte de hipóteses. Não habilitar ISA global nem trocar atomics/sincronização com base em uma issue de outro emulador. |
| Mesa Turnip | GMEM e SYSMEM têm custos diferentes. Autotune considera banda/perfis; preferências e TU_DEBUG não são equivalentes. | Manter a escolha atual do aparelho. Testar driver e modo separadamente; não remover `sysmem` globalmente, devido aos históricos de device loss. |

### O salto do Xbox original, com os limites publicados

O README do Mainkill1/xemu compara upstream v0.8.136 instrumentado com o fork
`ee5ce48`, Vulkan 1×. Em PGR2 parado no início, Windows passou de 14,66 para
30,00 guest flips/s (+104,67%). Na cena de PGR2 do Steam Deck, passou de 7,38
para 7,15 (-3,18%), com p99 pior. Isso demonstra um ganho em cenário específico,
não que qualquer Xbox passou a ser duas vezes mais rápido em todo hardware.
São cadências guest; não FPS apresentados. O relatório declara diferenças de
cena/cache e ausência de controle rígido de temperatura/energia. Fonte:
[README primário](https://github.com/Mainkill1/xemu/blob/d1fed222e531f8cb544925595876aa2fc62810c9/README.md).

### O papel real da IA

Os históricos revisados incluem assistência de Codex e Claude e revisões
automatizadas. O mecanismo de aceleração está no código: instruções mais curtas,
menos consultas, menos cópias, armazenamento de resultados e melhores decisões
de sincronização. Não foi encontrada base para adicionar uma rede neural ao
loop de emulação e esperar aceleração automática. IA ajuda a formular e testar
hipóteses; autoria e volume de commits não substituem correção nem benchmark.

## Implementações desta rodada

| Mudança | Mecanismo e limite | Origem |
|---|---|---|
| Permutações ARM64 de bytes/halfwords | REV32 das tabelas substitui XOR dos controles; formas de merge usam ZIP + REV. Máscaras e byte order PPC mantidos. O fast path de merge de words já existia no Zephyr e foi preservado. | `ae21c2ea5771c5d362e8cb2cb092170e0fd03a2a`, adaptação só de PERMUTE_V128 |
| Truncamento I64→I32 para comparações | Elide `mov wN,wN` apenas com destino aliased e todos os leitores sendo comparações de 32 bits. ZERO_EXTEND, consumidores mistos e destino diferente mantêm a instrução. | `74c1ec87c866019bdcf0930658df561fd5831331` |
| Memo de textura e view | Tabela direta de 512 entradas com comparação da chave inteira; cada textura guarda sua última view. Descarte, recriação e limpeza invalidam o memo, inclusive quando desligado. Não guarda conteúdo de texels nem substitui suas watches. | `87739627598f6afc900b2f0e0066736751b77000` |
| Carregamento repetido | Mesma textura em múltiplas constantes de fetch entra uma única vez na fila, preservando a primeira ocorrência e o limite de 64 entradas. | `fc5283400`, SHA completo no inventário de proveniência |
| Resolução de caminhos ZAR | Remove LookUp duplicado e logs informativos por abertura/entrada; delega à mesma árvore de entries e conserva logs de erro. Só afeta jogos em ZAR. | `273da4d8be6f054cc2aa8ff19d89ba15843c88af` |

A origem é phforner0/Xendroid-Plus, com autores e upstreams originais registrados
no inventário. Não é merge global do upstream. O snapshot auditado do upstream é
`e4003ba664d6e625d49ace3aa7c74c38d748f61b`.

O commit de memoização do upstream relata queda da participação de GetView nas
amostras de CPU em RDR e GTA IV, mas diz explicitamente que a diferença de FPS
ficou dentro do ruído. Não apresentamos essas amostras como ganho de FPS do Zephyr.

## O que já existia e foi mantido

Caches de shaders e pipelines, precriação pelo histórico, compilação assíncrona,
estados dinâmicos Vulkan, descritores reutilizados, JIT ARM64, ThinLTO e -O3 já
estavam presentes. Não foram renomeados como novos recursos. A otimização
experimental de barreiras por regiões e os saltos estruturados da beta 1 também
já existiam e continuam com suas escolhas anteriores.

Não foram alterados a configuração 360p, precisão de FPU, relógio guest, áudio,
readbacks, occlusion queries, clocks ou VSync. Quadros interpolados continuam
separados da velocidade de simulação. Não adicionar fast-math global.

## Candidatos adiados e por quê

- Pool de constantes v128 e thunks de extern sem spill vetorial: promissores,
  porém dependem de layout de contexto, ilhas de literais, alcance de branches,
  reemissão e ABI/FPCR. Exigem execução ARM64 e testes próprios antes de transporte.
- Novo scheduler e I/O assíncrono upstream: série extensa de semântica de kernel,
  cancelamento, DPC, locks e completions. Não misturar em um pacote sem uma
  campanha de regressão de threads e saves.
- Ubershaders genéricos: o Zephyr já tem intérprete/placeholder experimental;
  substituir shader por placeholder não garante imagem correta. Um fallback
  exato de NV2A não interpreta os shaders Xenos.
- Cache persistente de SPIR-V traduzido: oportunidade distinta do cache do
  driver. Precisa de chave completa de tradução, versão e features, arquivo
  atômico, validação de tamanho e invalidação entre versões. Não usar cache
  velho para uma tradução com outras regras.
- Ajustes Halo: Reach/partículas e redução de MSAA: o resultado publicado
  de 17→30 FPS é de Halo: Reach no POCO F7. Não extrapolar para Forza e não
  ativar atalhos que mudam qualidade no preset genérico.
- Renderizador nativo por jogo: maior potencial, mas é um projeto de port,
  envolvendo análise do executável e equivalência de materiais/EDRAM. Sem
  binário compatível e captura do jogo não há implementação verificável.
- PGO/LTO adicional: ThinLTO já existe; PGO requer perfis representativos do
  aparelho. Não inventar perfil ou afirmar benefício só por mudar flags.

## Validação executável e limites

- `python3 tools/test-round3-a64.py`: extrai o emissor de produção, usa o
  Xbyak vendorizado e interpreta independentemente as instruções emitidas.
  55.296 casos bit a bit: constantes/registros, aliases, controles com bits altos,
  merges de bytes/halfwords e preservação de outros registradores. Caminho
  dinâmico: cinco instruções em vez de sete. O carregador de constantes do
  harness é próprio; o materializador de produção não foi alterado.
- `python3 tools/test-a64-vector-select.py`: usa HIR/DCE/allocator reais;
  estendido para dez opcodes de comparação, leitores mistos, uso por
  zero-extension, valores sem uso e encodings de truncamento de 0/4 bytes.
- `python3 tools/test-round3-cache.py`: ASan/UBSan, 20.000 ciclos de consultas e
  invalidação, colisões, desligar/religar, falhas de criação, 16.384 combinações
  de view, dedup de 64 entradas e delegação ZAR. Fábrica de textura/view e VFS
  são fakes de host; não é teste de Vulkan nem montagem real de ISO/ZAR.
- Integração NDK compila os arquivos de produção modificados, incluindo
  `a64_seq_vector.cc`, `texture_cache.cc`, `vulkan_texture_cache.cc` e ZAR.
- CI Kotlin/Compose e perfis verifica a nova chave, os valores padrão e os
  perfis experimentais isolados. O APK exige os testes antes de empacotar.

A execução dos encodings no interpretador de teste não mede throughput real
nem substitui execução ARM64 no Edge. O sanitizer local desativa apenas a
checagem de vazamento incompatível com ptrace; o CI mantém seu padrão.

## Como comparar no Edge 30 Ultra

Em Configurações do jogo → perfis, os dois novos perfis de rodada 3 mudam
somente `GPU|texture_lookup_memo`, true/false. São experimentais, sem evidência
fictícia. O recurso também tem controle avançado no catálogo. Conferir a prévia:
overrides próprios podem preservar a escolha anterior. Restaure o perfil antes
de trocar. Isso isola o memo, não desliga o novo codegen ARM64.

Para o pacote completo, comparar a beta 2 de `aad470ff` com a beta 3, usando
mesmo driver, jogo/Title ID/versão, save, percurso, configuração efetiva,
resolução 360p e cache aquecido. Pelo menos três A/B intercalados, sem gravar a
tela durante medição; depois 20–30 minutos para aquecimento. Usar
`tools/compare-benchmarks.py` e metadados reais. Relatar tempo de quadro p50,
p95/p99, FPS guest, estabilidade, artefatos e temperatura. A memória ganha
cerca de 12 KiB para as 512 entradas e um pequeno registro por textura, não um
cache ilimitado. Não prometer correção de black screen sem o relatório de falha.

## Fontes primárias adicionais

- https://github.com/hedge-dev/XenonRecomp
- https://github.com/rexglue/rexglue-sdk
- https://github.com/Buku313/Skate3-Mobile
- https://github.com/FH1-ReImagined/ForzaHorizon1-ReImagined/blob/main/docs/android-port.md
- https://github.com/nimauria/Xenon-Recomp
- https://github.com/xemu-project/xemu/pull/2801
- https://github.com/N64Recomp/N64Recomp
- https://github.com/RPCS3/rpcs3/issues/19369
- https://github.com/RPCS3/rpcs3/issues/19611
- https://docs.mesa3d.org/drivers/freedreno.html

Revisões e decisões exatas: `optimization-round3-provenance.json`.

# Zephyr-Mobile — Auditoria aprofundada de recompilação nativa
18/11/2026 | Auditoria comparativa AOT → JIT ARM64 | PR #12

**Base:** Beta 16 Research, commit 6a0a78d0; branch de trabalho research/native-recomp-semantics-20261118.
**Dispositivo:** Motorola Edge 30 Ultra, Snapdragon 8+ Gen 1 (SM8475), Adreno 730, Android 15.
**Preservados:** main, pacote e assinatura Research, configurações padrão de Vulkan, scheduler, APU e afinidade.
**Evidência:** fontes dos projetos e código Zephyr inspecionados; testes host/CI. Não foi realizado teste no telefone nesta rodada.

## 1. Diferenciar recompilação AOT de emulação JIT

| Camada | Recompilação nativa por jogo | JIT universal do Zephyr | Reuso viável |
|---|---|---|---|
| PowerPC/VMX128 | XEX conhecido traduzido para C++ antes de executar | HIR e ARM64 compilados em tempo de execução | Semântica PPC, otimização de IR, regressões |
| Dispatch indireto | Funções/endereço conhecidos, tabela estática | DLLs, código descoberto ou gerado em execução | Apenas depois de guardas/validação |
| Memória/HLE | Runtime por jogo ou compartilhado | HLE Xbox 360 e espaço de memória guest | Testes de concorrência, alinhamento, estados |
| GPU | Pode interceptar D3D e usar renderizador específico | PM4/Xenos, EDRAM, shader JIT, Vulkan | Hipóteses testáveis, não substituição direta |
| Shaders | Pré-traduzidos e vinculados ao título | Tradutor SPIR-V e caches por conteúdo/driver | Pré-aquecimento opcional por evidência |
| Empacotamento | Binário específico e assets do próprio título | APK generalista, vários formatos e jogos | Separar eventual port AOT |

**Implicação:** eliminar o JIT de CPU não remove o custo da GPU, da tradução de shader, dos resolves, das esperas em fences nem da semântica de kernel. Um grande ganho numa recompilação com *renderer nativo específico* não se transfere automaticamente a Forza dentro do Zephyr.

## 2. Projetos verificados e resultados

### ReXGlue SDK, versão 0.11.0 (14/11/2026)

Fonte: https://github.com/rexglue/rexglue-sdk

- Atualizações semânticas auditadas: commit 02a002f8199 (carry em shifts grandes), ab61f2e693f (NaNs e CR6 no vcmpbfp) e 6069ba1 (máscara de FZ em ARM64).
- Biblioteca AOT PowerPC com runtime próprio; também há correções para acesso desalinhado, validade de mapeamentos, dispatcher e exportações.
- **Aproveitado:** duas correções do tradutor PPC do Zephyr, descritas na seção 3.
- **Não transplantado:** runtime inteiro, mecanismo de endereçamento fixo e política de FPCR sem equivalência comprovada.
- Licença ReXGlue: BSD-style permissiva com preservação dos avisos; arquivos derivados podem ter outros avisos.

### XenonRecomp (hedge-dev)

Fonte: https://github.com/hedge-dev/XenonRecomp

- Recompila PowerPC de XEX conhecido para C++; não fornece sozinho o runtime, GPU nem kernel para rodar o jogo.
- Usa SIMDe no ARM64 e tabelas de funções indiretas quando os alvos são conhecidos.
- **Aproveitável:** oráculos semânticos, VMX128, ABI e tratamento de branches.
- **Não aplicável genericamente:** substituir o despachante JIT por tabela AOT fixa, pois DLLs, self-modifying code, endereços e patches mudam durante a execução.

### XenosRecomp (hedge-dev)

Fonte: https://github.com/hedge-dev/XenosRecomp

- Converte shader Xenos para HLSL, compilável para DXIL ou SPIR-V, com regras específicas de Unleashed Recompiled.
- Documenta lacunas de vertex formats, vertex input, swizzles, R11G11B10, half-pixel e reverse-Z.
- **Aproveitável:** testes diferenciais de shader e análise de coordenadas.
- **Não usar como substituto direto:** o Zephyr traduz shaders dinamicamente, precisa de semântica muito mais ampla e já mantém compilação assíncrona e cache persistente.

### Army of Two: The Devil's Cartel Recomp

Fonte: https://github.com/BrewSparrow/ArmyOfTwoTDC-Recomp

- Reporta barreiras PPC que antes eram NOP em seu codegen, correção de exponencial vetorial para negativos extremos, leitura GPU→CPU seletiva, sincronização de memexport, oclusão e bugs XMA.
- No Zephyr, sync/eieio/isync já geram barreiras. Em A64 o MEMORY_BARRIER emite DMB ISH, e LOAD_BARRIER emite DMB ISHLD.
- No Zephyr, vexptefp já usa um helper baseado em std::exp2 por lane e flush de denormais.
- **Decisão:** não duplicar correções já presentes nem generalizar hacks de oclusão ou pulos de readback sem rastrear dependências.

### Rayman Origins Recompiled — Android e renderer nativo

Fontes:
https://github.com/XDanfr/RaymanOriginsRecomp
https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/NATIVE_RENDERER.md

- AOT ARM64 e renderização Vulkan por interceptação de chamadas D3D específicas do título, com shaders preparados offline.
- O projeto relata aproximadamente 60 FPS no Galaxy S23/Adreno 740 com o renderer nativo.
- **Lição:** o maior potencial vem da retirada do caminho genérico de emulação GPU, algo que exige um port específico.
- **Limite:** nenhuma medida equivalente existe para Forza Horizon no Edge 30 Ultra; não extrapolar FPS do S23.

### Xerenge — Burnout Revenge

Fonte: https://github.com/shipa-2/Xerenge

- Recompilação AOT de versões identificadas por hash, renderer Plume específico e opção de backend Xenos genérico para comparação.
- O README relata funcionamento em Android com Vulkan e suporte a corridas. O ganho é inseparável dos hooks e do renderer dedicado.
- **Lição aplicável:** comparar backend específico vs genérico com a mesma cena e asset, isolar exatamente onde ocorre o custo.
- **Limite:** renderer e código de hooks por jogo não podem ser copiados como otimizações universais. Licença do projeto é GPL-3.0.

### Xenon-Recomp (nimauria)

Fonte: https://github.com/nimauria/Xenon-Recomp

- Arquitetura modular com runtime compartilhado, backend gráfico D3D12/Vulkan e análise de funções reutilizável entre revisões.
- Marco relatado em 28/10/2026: logo inicial de Ace Combat 6. Sem título comercial completamente jogável confirmado; ARM64/Android ainda não constituem backend validado.
- **Lição:** módulos e regressões de EDRAM são referência conceitual, não prova de solução pronta para telefone.

### ViaXTrace/Xbox360RecompAndroid

Fonte: https://github.com/ViaXTrace/Xbox360RecompAndroid

- README descreve JIT ARM64, HLE e Vulkan. A existência de um README/estrutura não demonstra compatibilidade funcional, precisão de FPS ou equivalência de hardware.
- **Decisão:** requerer APK reproduzível, testes de tradução, frame captures, jogos verificados, condições térmicas e logs antes de considerar qualquer incorporação.

## 3. Falhas no tradutor do Zephyr: correções efetivas

### 3.1 XER.CA de sradx e srawx

Arquivo: emulator-core/src/main/cpp/xenia/src/xenia/cpu/ppc/ppc_emit_alu.cc

O código limitava a contagem a 63 (sradx) e 31 (srawx) e obtinha carry apenas ao reconstruir o valor pré-shift. Quando todos os bits são removidos, o PowerPC exige CA = sinal da fonte, mesmo se a reconstrução truncada coincidir.

Casos discriminantes:

| Instrução | Fonte | Shift | Destino de 64 bits | CA esperado |
|---|---|---:|---|---:|
| sradx | 8000000000000000 | 64 | FFFFFFFFFFFFFFFF | 1 |
| sradx | 8000000000000000 | 63 | FFFFFFFFFFFFFFFF | 0 |
| srawx | 80000000 (low 32) | 32 | FFFFFFFFFFFFFFFF | 1 |
| srawx | 80000000 (low 32) | 31 | FFFFFFFFFFFFFFFF | 0 |
| srawx | 7FFFFFFF | 40 | 0000000000000000 | 0 |

**Correção:** detectar bit 0x40 da contagem srad (7 bits) ou 0x20 da sraw (6 bits), combinar com a detecção de bits perdidos e só então condicionar ao sinal. Removida uma segunda operação Min redundante na sraw.

**Classificação:** correção objetiva de semântica. Não atribuir ganho de FPS.

### 3.2 vcmpbfp NaN e CR6

Arquivo: emulator-core/src/main/cpp/xenia/src/xenia/cpu/ppc/ppc_emit_altivec.cc

- Para NaN em qualquer operando, comparações ordenadas devolvem falso. A primeira condição anterior não invertia comparação ordenada, causando perda do bit 0x80000000.
- A nova condição superior representa NOT(vB >= vA), incluindo unordered; a inferior continua NOT(vA >= -vB).
- Resultado NaN esperado por lane: C0000000.
- O record-form agora passa a máscara de dois bits para UpdateCR6. Assim CR6[0] fica zero mesmo se todas as lanes estiverem fora dos limites; CR6[2] assinala somente todas as lanes dentro.

**Classificação:** correção objetiva de precisão PPC/VMX. Requer ainda verificação no binário A64 e em títulos.

### 3.3 Casos já corretamente tratados

- **Memória:** sync/eieio/isync têm emissores; backend A64 usa DMB ISH/ISHLD.
- **vexptefp:** helper A64 baseado em std::exp2, sem wrap de conversão inteira para negativos extremos.
- **Pipeline/shaders:** compilação assíncrona, cache, memoização e instrumentos de análise já existem.
- **Diagnóstico Edge30:** instrumentação do scheduler é opt-in, bloqueada no modo dedicado por padrão.

Não alterar FPSCR/FZ/FZ16, afinidade, número de threads, cache de shader ou precisão de GPU sem teste que isole a variável.

## 4. GPU, memória compartilhada e Xenos

### 4.1 EDRAM e resolves

Evitar assumir GPU bound apenas porque o jogo está a 360p. A captura anterior de Forza Horizon 2 mostrou Guest CPU 0 intensamente ocupado, mas não continha tempos GPU busy/fence/pipeline correlacionados por quadro.

Melhorias possíveis exigem:
1. Capturar o mesmo trecho de gameplay com VkFrameSync e VkPipelineWait, submissões e tempos válidos.
2. Identificar o recurso EDRAM, a versão da textura, tamanho e quem escreve/lê cada resolve.
3. Comprovar que a transferência pode ser agrupada/adiada sem usar dados antigos no CPU guest.
4. Comparar screenshots, transparências, HUD, sombras, colisões, readbacks de física e saves.
5. Reverter qualquer mudança que produza corrupção, mesmo com FPS melhor.

**Não ativar globalmente:** skip draws, desativação de GPU-written texture loads, pular clears, eliminar depth ou falsificar query.

### 4.2 Pré-compilação de shaders e pipelines

Os ports AOT conhecem as shaders do jogo com antecedência. O Zephyr conhece apenas shaders efetivamente vistos e já possui caches. Próximo experimento viável: pré-aquecer hashes de shader/pipeline capturados por título **sem misturar driver e cache inválido**, medindo custo inicial, taxa de acerto, tempo de carregamento e stutter. Isso não é uma funcionalidade implementada nesta rodada.

### 4.3 Renderer nativo por jogo

A verdadeira substituição Xenos→renderer Vulkan dedicado é um projeto de engenharia reversa para um título e revisão específicos. Demandaria hooks D3D, interpretação de formatos e swizzles, tradução dos shaders, reconstrução de render targets, sincronização e emulação das exceções de GPU e do kernel que o jogo ainda utiliza.

Para o Zephyr universal, o retorno esperado atualmente não justifica misturar esse sistema à árvore principal sem um protótipo isolado, interface de plugin e testes reais.

## 5. Números, teste e estratégia no Edge 30 Ultra

A suíte tools/test-native-ppc-edgecases.py:
- Extrai **as funções de produção** do próprio código-fonte Zephyr, não clones.
- Executa cerca de 62.288 casos de shift aritmético e 15.256 de comparação vetorial.
- Testa sinais, contagens 0–255, subnormais, zeros sinalizados, infinidades e NaNs quiet/signaling.
- Usa oráculos independentes, compilação C++20 com UndefinedBehaviorSanitizer e sem fast-math.
- **Não executa máquina ARM64 real:** a tradução final, jogos e desempenho dependem dos checks NDK/telefone.

A campanha posterior deve separar:
- Boot, menus, cutscenes, condução estável e colisões.
- FPS de apresentação vs frames guest.
- Frame times p50/p95/p99, crashes, recompilações, stalls de GPU e filas de trabalho.
- Mínimo 3 execuções independentes por braço, mesma versão do jogo, cena, driver, configuração, resolução **efetiva**, estado de cache e condição térmica comparável.
- Execução curta aquecida de 5 minutos e teste de 20–30 minutos para throttling.
- Se o ganho não exceder ruído/repetibilidade, não anunciar melhoria de desempenho.

## 6. Matriz de decisão

| Oportunidade | Evidência | Benefício potencial | Risco | Decisão |
|---|---|---|---|---|
| Corrigir srad/sraw XER.CA | Alto | Compatibilidade | Baixo | Implementado |
| Corrigir vcmpbfp unordered/CR6 | Alto | Compatibilidade e precisão | Médio | Implementado |
| Barreiras PPC→A64 | Já existentes | Nenhum ganho por duplicação | Alto se relaxar | Preservar |
| vexptefp negativo extremo | Já tratado via exp2 | Nenhum ganho comprovado | Médio | Preservar |
| Revisão FPCR FZ16 | Indício upstream, sem divergência comprovada | Precisão | Alto | Auditar antes de mudar |
| Shader prewarm por título | Plausível | Reduz stutter de pipeline | Médio/alto | Prototipar com logs |
| Memexport/EDRAM seletivo | Sem trace pareado Edge30 | Alto em certos títulos | Muito alto | Medir antes |
| AOT nativo por jogo | Arquitetural | Menor custo CPU de tradução | Muito alto | Projeto separado |
| Renderer Vulkan de Forza | Não demonstrado | Alto se houver port completo | Extremo | Não integrar genericamente |
| Workers/afinidade/thermal | Não há ensaio controlado | Incerto | Alto | Não mudar defaults |

## 7. Gates de integração

1. Workflow de pesquisa e oráculo PPC devem passar no commit final.
2. Integração Android ARM64, testes Kotlin/Compose e regressão de JNI devem passar; esse gate não equivale a jogar no telefone.
3. Não alterar pacote/assinatura Research nem branch main.
4. Validar Forza Horizon 1 e 2: boot, gameplay, menus, pausa, saída, save/load, gráficos e colisões, além de um jogo intensivo em VMX se disponível.
5. Só publicar **preview Research** após empacotamento e assinatura verificados. Nunca promover automaticamente para estável por sucesso da compilação.

**Conclusão:** duas falhas semânticas identificadas e corrigidas em PowerPC/VMX, uma operação HIR redundante eliminada e uma nova regressão que executa o emissor real. As vantagens maiores dos projetos AOT derivam do pré-processamento específico do título e, sobretudo, de renderizadores nativos próprios — técnicas que exigem experimento isolado, não transplante indiscriminado ao JIT universal do Zephyr.

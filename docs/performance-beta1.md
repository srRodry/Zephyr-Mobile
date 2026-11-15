# Zephyr Performance beta 1 — Edge 30 Ultra

## Conteúdo e limite da beta

Base visual congelada no commit remoto `0ac1738b1cbddc60f75aab15a3ffc7071eb80041`.
Branch `perf/edge30-beta1`; main preservada. Alvo: Snapdragon 8+ Gen 1 / Adreno 730,
Forza Horizon (`4D5309C9`) e Forza Horizon 2 (`4D530AA4`). São títulos diferentes.
A meta é sustentar a simulação a 30 FPS e reduzir picos de frame time.
Não há medição nova em aparelho nem ganho percentual afirmado.

A beta inclui rastreamento conservador de dependências da memória compartilhada:
`Vulkan|vulkan_disjoint_memexport_barriers`, desligado por padrão. Evita uma barreira
entre draws apenas se leituras de vértices/índices e exportações forem independentes.
Dependências RAW, WAR e WAW mantêm ordenação. Nenhuma exceção para atualizar memória
no mesmo lugar, shaders iguais ou batches. A abordagem é mais conservadora que
`006979f29ea959ef1ef3566f83fcbfa9a6fd6744` do Xendroid+: o commit foi auditado como
referência, não aplicado nem apresentado como seu equivalente de desempenho.

`Use()` invalida o rastreador e ordena acessos externos; a entrada inicial passa
pelo caminho original. Buffers zero-copy/host-importados, local read attachments e regiões
inválidas mantêm o caminho anterior. Máximo de 256 regiões; saturação força uma
barreira. Operações desconhecidas não recebem uma promessa de independência.
Os testes com sanitizers cobrem riscos, limites e 50.000 comparações com um modelo
por byte. Isso não substitui validação Vulkan nem gameplay no Adreno.

## Experimentos acessíveis no telefone

Configurações do jogo → perfis → prévia. Só aplicar após ler o que muda. Os perfis
são marcados `EXPERIMENTAL`, sem evidências inventadas, separados das recomendações
validadas. Restrições: Motorola Edge 30 Ultra e Adreno 730. Se ainda não houver
identificação de GPU, abrir uma sessão primeiro para registrar os dados.

| Perfil | Mudança | Possível limite |
|---|---|---|
| Referência | Desliga os dois experimentos novos | Preserva driver, resolução e opções anteriores |
| Teste 1 | Barreiras por regiões independentes | Pode não ter efeito em jogos com acessos sobrepostos ou caminhos excluídos |
| Teste 2 | Saltos SPIR-V estruturados já importados do Xendroid+ | Primeiro uso pode recompilar; conferir imagem e aquecer caches |
| Teste 3 | Duas threads de compilação de pipelines | Menor disputa de CPU pode aumentar compilação/pop-in |
| Teste 4 | Gate upstream para reuso de descritores | Conferir texturas, transparência e sombras |

Restaurar o perfil anterior antes de passar ao próximo teste. Overrides próprios
são preservados pelo mecanismo existente: conferir a prévia para saber se o teste
foi realmente aplicado. Reiniciar o jogo após mudar. Os perfis não combinam ajustes,
não mudam áudio, não forçam clocks e não substituem a configuração 360p existente.

Para a instrumentação existente de Vulkan, a linha `ZephyrMemexport` informa quantas
chamadas Use() foram puladas por independência de regiões no intervalo, por frame.
Esse contador não é a diferença líquida de barreiras GPU: uma dependência pode ser
ordenada mais tarde, e nem toda chamada original emitiria uma barreira. Não é uma
medida de FPS ou de tempo de GPU economizado. Comparar os tempos de quadro com a
mesma instrumentação nos dois braços. Usar os relatórios de sessão
e `tools/compare-benchmarks.py`; pelo menos três rodadas A/B intercaladas, mesma cena,
driver, resolução efetiva, patches, cache aquecido e temperatura inicial semelhante.
Depois verificar 20–30 minutos para aquecimento, falhas e imagem. Não comparar uma
rodada com cache frio a outra quente, nem FPS gerados a velocidade de simulação.

## Tecnologias auditadas

| Tecnologia | Estado / decisão |
|---|---|
| JIT ARM64, otimizações do compilador e ThinLTO | Já existem; não prometer ganho por apenas renomear flags |
| Shaders/pipelines em cache e compilação assíncrona | Já existem; testar disputa de threads, sem aumentar todas simultaneamente |
| Turnip GMEM/sysmem | Driver permite ambos; não trocar globalmente sem teste de estabilidade |
| VRS e redução de amostras | Já disponíveis; reduzem qualidade e dependem do caminho/driver |
| FSR/SGSR e outros upscalers | Reconstrução de imagem; não reduzem automaticamente o trabalho do guest |
| ADPF, GameManager e modo sustentado | Infraestrutura existente; não equivale a clocks máximos nem impede throttling |
| Frame generation / ZeroFG | Quadros apresentados não são FPS de simulação; custo/latência devem ser medidos separadamente |
| AOT / recompilação estática de ports | Requer port e adaptação por jogo; não é uma biblioteca de aceleração universal para o JIT |
| Tesselação e outras mudanças recentes upstream | Requerem auditoria de dependências e regressão; não adicionadas em bloco |
| Fast-math global, readbacks/occlusion falsos | Não usados para inflar resultado ou perder correção silenciosamente |

Fontes: código atual do projeto; histórico público do
[Xendroid+](https://github.com/phforner0/Xendroid-Plus/commit/006979f29ea959ef1ef3566f83fcbfa9a6fd6744),
[Android ADPF](https://developer.android.com/games/optimize/adpf) e
[Mesa Freedreno/Turnip](https://docs.mesa3d.org/drivers/freedreno.html).

## Distribuição

APK ARM64 release de teste, pacote `xendroid.compose.zephyr.research`, mesmo certificado
permanente já verificado (`22e1b7cd…053353`). Atualiza a instalação Research compatível;
não migra automaticamente os dados da instalação regular. Fazer backup antes de trocar
entre pacotes; não desinstalar para resolver assinatura sem preservar dados.

O workflow gera APK assinado, SHA-256, certificado e identidade do commit. Não publica
release pública nem altera a chave. `versionCode` da série: 100000 + número da execução
deste workflow. Experimentos novos começam desligados; ativar um perfil por vez.

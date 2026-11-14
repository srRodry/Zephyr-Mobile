# Pesquisa X360 no Android — primeira etapa

## Estado verificado em 14/11/2026

Base preservada: `feature/zephyr-mobile-branding`, commit
`999eb79aeba1775688399e8b297357cf901f980f`. A branch `main` não é o ponto
de desenvolvimento desta pesquisa. Trabalho novo em `research/android-x360-foundation`.

A comparação da API GitHub entre essa base e `phforner0:main` reportou
divergência: 516 commits exclusivos do upstream e 281 exclusivos do Zephyr,
com ancestral comum `1066f8fd6cab89302b364b341ba8687781d47182`.
Essas contagens incluem mudanças de interface, merges e infraestrutura;
não representam quantidade de otimizações. A resposta consultada contém
apenas 250 commits e no máximo 300 arquivos: não é um inventário completo.
Uma auditoria exaustiva exige histórico completo/paginação e revisão por subsistema.

A inspeção inicial foi feita pela API enquanto o clone Git estava em andamento.
O clone terminou posteriormente. Não há compilação Android nem medição física
nesta etapa.

## Decisão de arquitetura

1. Manter o núcleo JIT ARM64 existente como caminho geral.
2. Auditar e transportar mudanças pequenas de CPU/Vulkan com dependências explícitas.
3. Avaliar um port recompilado individual em etapa separada, após uma base comparável.
4. Compartilhar inicialmente a camada Android quando as interfaces permitirem.
   Não pressupor intercâmbio automático de memória, threads ou funções entre JIT e AOT.

## Fontes e prioridades

| Fonte | Evidência disponível | Próxima análise |
|---|---|---|
| [Xendroid+](https://github.com/phforner0/Xendroid-Plus) | Código e histórico; mudanças em ARM64, Vulkan e shaders | Mapear dependências e licenças; avaliar correções antes de atalhos por jogo |
| [Xenra](https://github.com/Yebot32/xenra) | Declara Xenia Edge para X360 e xemu para Xbox original | Identificar alterações reais no núcleo X360, sem importar a frente Xbox original |
| [X360 Mobile](https://github.com/Ashnar2602/X360-Mobile---OFFICIAL) | Repositório público de distribuição; núcleo ativo não publicado ali | Usar como comparação externa; nenhum código de núcleo extraído de APKs |
| [ReXGlue](https://github.com/rexglue/rexglue-sdk) | SDK/runtime de recompilação estática | Prova de conceito individual; revisar fronteiras do runtime |
| [XenonRecomp](https://github.com/hedge-dev/XenonRecomp) | Conversão PPC para C++; runtime fica a cargo do port | Investigar técnicas ARM64 e hooks; sem prometer portabilidade direta |
| [Skate3-Mobile](https://github.com/Buku313/Skate3-Mobile) | Declara renderizador Vulkan nativo específico | Separar infraestrutura reaproveitável das adaptações do jogo |

Candidatos identificados por título de commit na comparação (ainda não aprovados):

- `006979f29ea959ef1ef3566f83fcbfa9a6fd6744`: barreiras de exportação de memória por intervalo.
- `4f0d10aa353e3be729862411df59827889de6cc9`: saltos de shader estruturados em SPIR-V.
- `97e55406dc2347be6c4db0dda6f2dfbc40a4a17e`: tesselação Vulkan.

Revisar o diff, pré-requisitos e cobertura antes de escolher qualquer candidato.
Não copiar um arquivo inteiro para resolver uma divergência de histórico.
Antes de incorporar código, registrar licença por componente e preservar créditos;
o Xendroid+ atual declara AGPL-3.0-or-later para suas próprias contribuições.

## Comparação implementada

`tools/benchmark_forza.py` já gera `summary.json`, capturas, log nativo e
estado térmico. O novo `tools/compare-benchmarks.py` consome essas capturas
sem alterar o aparelho. Os relatórios herdados em `performance-tests/` descrevem
um POCO F7; não são medições do Motorola Edge 30 Ultra.

Em cada diretório de captura, criar `benchmark-context.json` com strings reais:

```json
{
  "device": "Motorola Edge 30 Ultra",
  "soc": "Snapdragon 8+ Gen 1 / Adreno 730",
  "android_build": "PREENCHER",
  "driver": "PREENCHER versão e hash",
  "game_title_id": "4D5309C9",
  "game_executable_sha256": "PREENCHER SHA-256 real de 64 caracteres",
  "scene": "PREENCHER save, percurso, câmera e duração",
  "internal_resolution": "PREENCHER resolução efetiva",
  "config_sha256": "PREENCHER SHA-256 real de 64 caracteres",
  "cache_state": "warm",
  "frame_generation": "off",
  "instrumentation": "PREENCHER flags de diagnóstico",
  "commit": "PREENCHER SHA do código",
  "apk_sha256": "PREENCHER SHA-256 real de 64 caracteres"
}
```

O exemplo exige preenchimento e não é um resultado de benchmark.
Para comparar uma alteração de configuração, o padrão recusa hashes diferentes. Para um experimento deliberado de configuração,
`--allow-config-change` registra essa diferença e ainda exige consistência dentro
de cada versão. Consulte [Experimento 01](structured-jumps-experiment.md).

Coletar pelo menos três rodadas independentes por versão, intercaladas A/B,
com duração semelhante, mesma cena, mesmo driver, cache aquecido e temperatura
inicial semelhante. Após isso:

```sh
python3 tools/compare-benchmarks.py \
  --reference performance-tests/a1 performance-tests/a2 performance-tests/a3 \
  --candidate performance-tests/b1 performance-tests/b2 performance-tests/b3 \
  --output performance-tests/research-comparison.json
python3 tools/test-compare-benchmarks.py
```

O relatório apresenta medianas e intervalos das rodadas, incluindo p95/p99
dos tempos de apresentação. Não agrega percentis como se fossem quadros brutos.
Recusa contexto incompatível, capturas duplicadas, builds misturados na mesma
versão, duração divergente em mais de 10%, valores inválidos e geração de quadros.
Os metadados são informados pelo operador: não substituem verificação dos arquivos.

## Critério para avançar ao núcleo

- Compilar o APK de referência a partir do commit identificado.
- Executar a cena no Edge 30 Ultra e obter capturas válidas.
- Selecionar uma mudança com suas dependências e compilar o candidato.
- Comparar fluidez, imagem e estabilidade; medir também sessão de 20–30 minutos.
- Separar FPS apresentados de velocidade real de simulação e custo de CPU/GPU.
- Só afirmar ganho após repetição; só adicionar um segundo título após validar o primeiro.

Não há ganho de FPS, correção gráfica ou compatibilidade nova comprovados por
este commit. Ele estabelece a comparação reproduzível e a trilha de pesquisa.


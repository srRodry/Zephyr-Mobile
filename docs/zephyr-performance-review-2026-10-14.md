# Revisão de desempenho — Edge 30 Ultra / Forza Horizon 2

Referência técnica em UTC: 14/10/2026. Relato do usuário em 13/10, horário de Boa Vista.

## Decisão e evidência

O usuário percebeu piora na versão de teste após a correção de áudio; o áudio
continua ruim. Isso é um resultado negativo de uso e não deve ser tratado como
melhoria. Sem execuções pareadas, configuração efetiva, driver, cache e estado
termal, não é possível atribuir a diferença ao commit de áudio. Novos ajustes
APU ficam suspensos. Não alterar múltiplos parâmetros de precisão para buscar FPS.

O alvo é Motorola Edge 30 Ultra, Adreno 730, Android 15. A captura anterior
marcava 28 FPS, CPU 64%, GPU 82%, RAM 75%, 43 °C. Esses agregados não identificam
qual thread limita o jogo nem comprovam throttling. Quedas em colisões/NPCs
podem envolver lógica, shaders, efeitos ou sincronização; ainda são hipóteses.

## O que já existe e não é uma novidade a acrescentar

- Release do Xenia já configura `-O3`, inlining, unroll e ThinLTO. `-Ofast`/fast-math
  indiscriminado altera semântica numérica e não é a escolha para compatibilidade.
- O app já declara `appCategory="game"` e informa loading/playing/pause ao
  GameManager. ADPF do presenter é opcional; sustained mode é separado. Nenhum
  deles garante clocks máximos nem desempenho superior.
- O reuso de descritores já usa scratch reutilizado e XXH3; não há alocação nova
  deliberada por draw nessa função. A alternativa do upstream evita esse hash,
  mas precisa de teste de correção visual antes de substituir o padrão.
- Cache de shaders, pipelines assíncronos e reuso de descritores já estão ligados
  no template. Mais threads não significa menor tempo do frame em um SoC com
  quatro núcleos grandes disputados pelo guest e pela compilação.

## Correção implementada no relógio nativo

`host_tick_count_platform()` POSIX retorna `sec * 1e9 + nsec`, portanto sua
frequência de representação é sempre `1e9` ticks/s. `clock_getres` descreve a
menor granularidade, não a unidade do retorno. Usar `1e9 / resolução` era
inconsistente em resoluções superiores a 1 ns e fazia uma consulta a cada
leitura de frequência/uptime. A correção retorna `1e9` diretamente; Apple e
contador de hardware mantêm seus caminhos. Não muda time scalar nem acelera o
relógio do jogo.

O teste compila o arquivo POSIX de produção, injeta resoluções 1, 10 e 1000 ns,
confere contagem/uptime e executa 10.000 leituras sem consultas de resolução.
O arquivo anterior falha. Isso comprova a unidade e a retirada da operação,
**não ganho de FPS no Motorola**. Não é apresentado como solução de 20→30 FPS.

## Experimentos com maior potencial, um por vez

Arquivo local de experimentos:
`app/src/test/resources/zephyr-performance-experiments.json`.
É compatível com o leitor existente de perfis locais, não é incluído nos assets,
não possui evidência fictícia e não é uma recomendação validada. O Title ID
`4D530AA4` refere-se ao Forza Horizon 2; conferir o ID da instalação antes de usar.

| Ordem | Alteração só para o jogo | Hipótese | Limite / retorno |
|---|---|---|---|
| 1 | Turnip: `turnip_debug` de `sysmem` para vazio | Deixar o driver escolher GMEM/sysmem pode reduzir tráfego de memória | Somente se o driver ativo for Turnip. Pode causar device loss; voltar a `sysmem` se falhar |
| 2 | Threads de pipeline: 4 para 2 | Reduzir disputa com CPU do guest nos picos | Pode aumentar compilação/pop-in. Comparar com caches aquecidos |
| 3 | Divisão periódica: 1300 para 0 | Reduzir custo de submissões e abertura/fechamento de passes | Pode reduzir sobreposição CPU/GPU. Se já for 0, este teste não muda nada |
| 4 | Gate de descritores upstream: false para true | Evitar cálculo de hashes em draws repetidos | Requer cache de descritores ligado. Reverter se surgirem texturas/sombras incorretas |

Aplicar nas configurações **do jogo**, nunca globalmente. Cada candidato tem uma
chave. Ao usar o arquivo de perfis, colocá-lo na pasta `settings-profiles` dos
dados do app, abrir as configurações do jogo e revisar a prévia. Overrides
próprios são preservados; se a prévia disser que mantém sua escolha, o
experimento não foi aplicado. Restaurar o perfil anterior antes de outro teste.
As mesmas chaves podem ser alteradas manualmente, sem importar o arquivo.

Para ganhos que aceitem perda de qualidade, o menu em jogo já oferece VRS
2×1/1×2/2×2 e MSAA 4× como 2×. VRS 2×2 reduz a frequência nominal de shading de
quatro pixels para uma invocação em áreas elegíveis; não reduz todo o trabalho
da GPU em 75%. Depende do suporte efetivo do driver e de o gargalo ser fragment
shading. MSAA reduz amostragem/qualidade e pode afetar bordas. Testar separado;
não combinar com falso resultado de occlusion ou eliminação de readback.
Mudar a resolução reportada ao console não garante mudar a resolução renderizada
por um jogo que aloca render targets fixos.

## Identidade de benchmark

Há casos documentados de firmware que reconhece benchmarks por identidade e
aplica políticas diferentes; UL confirmou isso em aparelhos MediaTek em 2020.
Não encontramos evidência equivalente para este Edge 30 Ultra/Android 15.
Renomear o pacote não otimiza JIT, shaders ou sincronização. Um eventual efeito
vem da política OEM e precisa de comparação no mesmo aparelho.

Um APK com outro applicationId também cria instalação, dados e caches separados;
compará-lo com caches frios confunde o experimento. Pode conflitar com o benchmark
real instalado. Não foi criado APK disfarçado nem alterado o pacote de testes.
Se essa hipótese for retomada, usar um experimento isolado com mesma compilação,
configuração, caches aquecidos e cena, medindo 2 e 10 minutos, temperaturas e
FPS do guest. O modo Android de performance fixa para benchmark usa ADB e não
promete clocks máximos; não resolve a situação atual de uso só pelo celular.

## Critério de escolha

No APK que for mais estável, aquecer a cena, medir 120 s sem gravação de tela,
repetir A/B e B/A e validar o candidato por 10 min. Manter driver, patches,
resolução e FPS cap. Compartilhar o relatório da última execução e identificar
qual experimento foi usado. Comparar FPS do guest, frame time, pipelines,
crashes e imagem. Não atribuir o efeito de frame generation ao avanço da lógica.
Se não houver melhora repetível, restaurar. Antes de alterar JIT/escalonador,
precisamos localizar a thread limitante; percentuais globais de CPU não bastam.

Fontes primárias:

- [Android ADPF](https://developer.android.com/games/optimize/adpf)
- [Android Fixed Performance Mode](https://developer.android.com/games/optimize/adpf/fixed-performance-mode)
- [Game Mode interventions](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-interventions)
- [Mesa Freedreno / Turnip](https://docs.mesa3d.org/drivers/freedreno.html)
- [UL: reconhecimento de benchmarks em firmware MediaTek](https://benchmarks.ul.com/news/ul-delists-mediatek-powered-phones-with-suspect-benchmark-scores)
- [Xenia: Title ID do Forza Horizon 2](https://github.com/xenia-project/game-compatibility/issues/514)

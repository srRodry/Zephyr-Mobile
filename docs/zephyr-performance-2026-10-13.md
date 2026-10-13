# Zephyr-Mobile — desempenho com compatibilidade

## Alvo e limites da evidência

Edge 30 Ultra, Adreno 730, Android 15, 12 GB. O APK v32 (`63b6e5a2`) foi identificado na tela Sobre; as capturas enviadas mostram Forza Horizon 2 no título e no gameplay. Ainda faltam confirmação de que a sessão usou essa mesma instalação, duração, FPS do jogo, driver ativo e recuperação dos saves. Nenhum ganho de FPS é atribuído às alterações abaixo antes de uma comparação no aparelho.

Os documentos herdados sobre Geometry Wars na Adreno 650 e Forza Horizon na Adreno 825 não são benchmarks deste aparelho nem do Forza Horizon 2.

## Primeira alteração: custo do host

- ADPF desligado, suspenso ou indisponível por API: não consulta mais o trabalho nativo do presenter nem o limite de FPS para essa função. A função continua opcional; não foi ativada por padrão.
- A amostra de FPS do loop de sessão é compartilhada por estatísticas, avaliação de frame generation e readout: uma leitura nativa em vez de até três por segundo.
- No HUD, RAM e temperaturas passam a acompanhar a cadência de 1 Hz da bateria. A 4 Hz, cada uma passa de quatro consultas para uma por segundo. FPS, frame time e utilização CPU/GPU mantêm sua cadência.
- Se não houver contador GPU acessível, a descoberta dos arquivos sysfs ocorre uma vez por ciclo de exibição/configuração do HUD. Uma alteração nas métricas ou reabertura do HUD permite nova descoberta.

São reduções de consultas, alocações e I/O de diagnóstico. Não alteram shaders, render targets, guest scheduling, resolução, saves, precisão ou a política de frames. Não equivalem a uma melhoria de FPS comprovada.

## Configuração atual merece cautela

O template já contém `store_shaders=true`, escala 1×1, pipelines assíncronos com quatro threads, dynamic pipeline state, render target path performance, `readback_resolve=uma`, `occlusion_query=fast`, `vulkan_mid_frame_submission_draws=1300`, `vulkan_async_skip_draws=true` e `turnip_debug=sysmem`. A configuração efetiva de um jogo pode sobrepor esses valores.

`vulkan_async_skip_draws=true` pode omitir draws durante compilação e causar pop-in. Não é uma otimização sem perda de fidelidade. `turnip_debug=sysmem` é uma proteção contra hangs para o Turnip; não se deve removê-la globalmente. Não desativar readback, sincronização de stack, precisão ou limites térmicos só para elevar um contador. Frame generation deve ser avaliada separadamente dos FPS do guest.

## Protocolo no celular

1. Manter APK, jogo/executável, driver, resolução, limite de FPS e patches iguais. Registrar o driver efetivo e configurações da sessão no Diagnóstico. Um driver disponível no aparelho não é necessariamente o usado pelo jogo.
2. Escolher trecho/cena reproduzível. Aquecer o cache antes da medição; registrar carregamento/compilação a frio separadamente. Não apagar caches entre A e B.
3. Fazer duas comparações pareadas A/B e B/A, com 120 segundos por trecho e condições térmicas iniciais semelhantes. Depois validar a opção candidata por pelo menos dez minutos. Usar o mesmo HUD compacto nas duas opções e evitar sobreposições externas variáveis.
4. Comparar FPS do guest e distribuição de frame time (mediana/p99), tempo de criação de pipelines, áudio, estado térmico, falhas e imagem. O FPS baixo calculado pelo app é um percentil de amostras de um segundo, não o convencional 1% low por frame. Velocímetro não é FPS. Temperatura da bateria não é temperatura da GPU.
5. Aplicar uma alteração por comparação e somente ao jogo em teste. Conferir direção, transparências, sombras, HUD, saves e encerramento/reabertura. Reverter em caso de regressão. Ganhos menores que a variação entre repetições são inconclusivos.

## Ordem dos experimentos após obter o baseline

1. Comparar custo do host e HUD, sem mudar a renderização.
2. Se houver indicação de custo de comandos/render passes: comparar mid-frame submissions 1300 versus 0, só no jogo alvo. O valor 1300 não foi demonstrado ideal no Forza Horizon 2/Adreno 730.
3. Se stutter coincidir com criação de pipelines: comparar threads 4 versus 2 mantendo as demais opções e o mesmo cache aquecido. Mais threads podem disputar CPU com o guest; nenhuma opção é declarada superior antecipadamente.
4. Somente se o driver ativo for Turnip: investigar sysmem versus caminho padrão numa configuração isolada, preservando retorno ao sysmem e observando device loss, imagem e estabilidade. Sem ganho comprovado e sem estabilidade, manter a proteção.
5. Modificações nativas de shaders/CPU ficam condicionadas ao gargalo medido e a regressões de precisão. Resolução e efeitos são decisões de qualidade separadas, não prova de eficiência do emulador.

Referências primárias: [Android ADPF](https://developer.android.com/games/optimize/adpf), [Thermal API](https://developer.android.com/games/optimize/adpf/thermal), [PerformanceHintManager.Session](https://developer.android.com/reference/android/os/PerformanceHintManager.Session). As recomendações do Android descrevem o sistema; não comprovam ganhos neste emulador.

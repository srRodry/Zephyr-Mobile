# Evidência das capturas enviadas — 14/10/2026

Revisão sobre o HEAD `7d58d83c933eed4195a7ae3311eab7f74a3a6b81`, com UI #66
aprovado. Uma etapa documental, sem alterações de emulação, áudio ou defaults.
Nenhum APK solicitado. Logs brutos permanecem fora do repositório.

## Proveniência e integridade

Inspecionado o ZIP `xendroid-logs-20261014-015847.zip` enviado para diagnóstico,
203.376 bytes, SHA256
`5683f4a1edc1116fe4761590d9978b07969c6c4b53bf0f753b9d87c048c0fe02`.
CRC do ZIP externo conferido. Ele contém:

- Uma sessão arquivada, com xe.log, logcat, context.json e exit-info.
- xe.log e logcat da execução atual no momento da exportação.
- Nenhum `run-report.json`, nem relatório estruturado de CPU por thread/JIT.

O contexto da sessão ARQUIVADA informa `appVersion=5fe5d475`. Não há um contexto
de build separado para o par current neste pacote: não assumir seu APK exato.
O build arquivado antecede a adição de VkPipelineWait em `456befe`.
Não publicar caminhos de arquivos do telefone, UUIDs, PIDs, identificadores
da biblioteca privada ou trechos integrais dos logs.

## Achados reais, sem inferência de FPS

As duas execuções identificam o Forza Horizon 2 e o modelo Edge 30 Ultra nos
registros. Isso não comprova compatibilidade geral ou execução sem falhas.

| Evidência | Sessão arquivada | Current na exportação |
| --- | --- | --- |
| Loader Vulkan registrado | system | custom |
| Driver registrado | Qualcomm proprietário | turnip Mesa driver |
| Informação do driver | Build 0c393b63cf / I94e2bd5684 | Mesa 26.3.0-devel, git-30da39187d |
| Nome de GPU declarado pelo driver | Adreno (TM) 730 | Turnip Adreno (TM) 725 |
| API Vulkan utilizada registrada | 1.1.128 | 1.3.363 |
| Config dump: Vulkan turnip_debug | sysmem | string vazia |
| Config dump: vulkan_async_skip_draws | true | true |

Os nomes de GPU são os declarados por cada driver, não prova de troca do hardware
nem de uma GPU física diferente. O efeito de turnip_debug com o loader system
não foi medido. Driver e configuração não são constantes entre os dois logs;
não usar esse pacote como um A/B controlado de otimização.

Ambos registram patches de desativação de motion blur, sombras e hash checks.
Preservar a mesma seleção na comparação; não atribuir efeito a uma mudança de
código quando patches/caches/driver/trecho também puderem variar.

## O que os logs não permitem medir

Busca em ambos os xe.log e logcat: nenhuma amostra GpuFrame, VkFrameSync,
VkFences ou VkPipelineWait. O analisador do projeto retorna zero janelas válidas
e valores agregados CPU/pipeline `null`, não duração zero. Não foram encontradas
amostras malformadas desses prefixos; eles simplesmente não estão presentes.

O ZIP não contém dados estruturados hostThreadCpu ou jitCompilation. Isso não
prova que os coletores estavam desligados: o exportador de logs não equivale ao
exportador de relatório da execução. Não é possível calcular CPU por thread,
tempo real de GPU, custo de compilação ou esperas a partir desse pacote.

Não foram encontrados os marcadores literais DEVICE_LOST/VK_ERROR nos xe.log
inspecionados. Ausência desses marcadores não prova estabilidade, ausência de
falhas ou causa do encerramento. Nenhum FPS foi medido nesta revisão.

## Flag herdada que exige interpretação

O código do HEAD declara `vulkan_async_skip_draws=true` por padrão, com histórico
de ativação em 06/10/2026. Portanto true no dump não prova que um preset recente
ou o usuário o ativou. Esse default não foi alterado nesta etapa.

Quando a compilação assíncrona está em uso e a criação do pipeline ainda não
terminou, essa política pode descartar draws em vez de esperar na submissão.
Isso não prova que houve draws descartados neste teste nem explica o áudio.
Uma futura coleta pode ter espera de submissão zero porque a política não
espera ali, e ainda assim ter compilação nos workers, espera explícita ou GPU
cara. Não interpretar VkPipelineWait isoladamente como todo o caminho crítico.

Fonte primária: vulkan_pipeline_cache.cc, definição da flag e EndSubmission.
Não extrapolar o comentário de teste histórico com outro jogo/aparelho.

## Próxima captura necessária, pensando no uso pelo celular

1. Identificar o build efetivamente instalado e exportar também o relatório
   da execução. O RunReport do HEAD grava `run-report.json`; nele ficam
   performance.hostThreadCpu e performance.jitCompilation quando disponíveis.
   Usar a exportação redigida do app, revisar o conteúdo e compartilhar só o
   necessário. O arquivo de logs de Diagnóstico é complementar, não substituto.
2. Fixar driver, patches, resolução, opções efetivas e mesmo trecho; aquecer
   caches e comparar separadamente trecho normal, colisões e concentração de NPCs.
3. GPU: usar somente o opt-in já existente com passes=false antes do boot quando
   houver suporte à configuração. Não contornar allowlist/permissões nem exigir
   shell/PC como se o usuário os tivesse disponíveis. Se a opção não estiver
   acessível, registrar essa limitação em vez de afirmar que a coleta aconteceu.
4. Preservar VkFrameSync, VkFences e VkPipelineWait do mesmo teste, quando emitidos.
   Comparar overhead com coleta desligada; não ativar passes detalhados na primeira
   captura. Não alterar defaults nem a política de fences a partir deste ZIP.

16 regressões do analisador e sete grupos da timeline passaram localmente nesta
revisão. O CI do commit documental ainda precisa ser confirmado. A evidência
concreta é uma diferença de ambiente e uma lacuna de coleta, não uma otimização
demonstrada. Link completo e avaliação no aparelho continuam necessários.

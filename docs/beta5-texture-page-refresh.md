# Beta 5 — corrupção gráfica em Army of Two e relato de travamento

Data: 15/11/2026. Base: beta4 `4ce1d8e7`. Alvo: Edge 30 Ultra / SM8475.

## Evidência recebida

Duas imagens mostram ruído colorido em partes da máscara e roupa no menu do
primeiro Army of Two. O vídeo de 69,706 s mostra biblioteca, rotação/início de
sessões e Spider-Man: Web of Shadows chegando à cena dos prédios; amostras
finais permanecem visualmente na mesma cena. Não há log nativo anexado nem
comparação anterior na mesma configuração. O hash instalado não aparece
nas capturas. O contador de FPS da sobreposição
não prova progresso da simulação guest. Não atribuir a causa ao compilador
Cortex-X2, driver, cache de textura ou aquecimento apenas pelas imagens.

## Erro reproduzido e corrigido

`SharedMemory::MakeRangeValid` alterava `system_page_flags_valid_` tanto em
uploads CPU→GPU como em escritas GPU, mas marcava `dirty_blocks_` e
`gpu_written_data_dirty_` apenas nas escritas GPU. O refresh parcial no fim do
quadro usa as duas máscaras. Portanto, podia ignorar um upload CPU-only ou
um upload localizado fora dos chunks modificados pela GPU. Nesse caso o estado
válido antigo sobrevivia ao refresh solicitado.

O teste executa os corpos de produção de MakeRangeValid e do refresh, com
um backend de memória fake, contra um modelo independente de refresh completo.
No source da beta4, o primeiro caso falha: uma página carregada pela CPU
permanece válida. Com a marcação dos chunks movida para o caminho comum dos
uploads, o teste passa. A cópia parcial, preservação de páginas GPU, locks,
barreiras e watches continuam com os mecanismos existentes. Não é limpeza
completa de cache por quadro nem mudança do conteúdo dos texels.

60.000 atualizações mistas em páginas 4/16/64 KiB passaram com UBSan, incluindo
CPU-only, chunks distintos, sobrescrita de página GPU pela CPU, refresh esparso
e caminho denso. O teste valida os estados de memória e não renderiza o jogo.

## Ajuste por jogo

Army of Two original (454107F8) passa a receber `clear_memory_page_state=true`
pela tabela de quirks. A configuração própria do título ainda pode sobrescrever
o valor. Não se aplica ao The 40th Day, The Devil's Cartel ou Spider-Man.

Fonte primária do ajuste por título:
https://github.com/xenia-manager/optimized-settings/blob/main/settings/454107F8.toml

O rastreador do Xenia também registra corrupção gráfica no título:
https://github.com/xenia-project/game-compatibility/issues/167

O ajuste externo usa também readback_resolve=fast para bloom; não foi importado
porque esse caminho e seu custo não foram validados neste aparelho. Não foram
importados workarounds de D3D12 como se fossem equivalentes ao Vulkan.

`tools/test-army-quirk.py` compila o dispatcher real com um backend de cvars fake,
confere Title ID exato, preservação dos demais títulos e a ordem da prioridade
no source de config.cc. O CI compila shared_memory.cc e game_quirks.cc reais
com NDK além dos objetos anteriores.

## Limites e próxima evidência

Esta beta corrige uma inconsistência reproduzível no código e ativa um ajuste
conhecido para Army. Ainda não comprova que a máscara do usuário foi corrigida.
Não há correção confirmada para o travamento de Spider-Man. É preciso exportar
o ZIP dos logs da sessão travada para distinguir espera de CPU, memória,
shader/pipeline, fence ou apresentação. Não remover waits, fabricar resultados
de queries nem habilitar memexport por hipótese visual.

Exportação existente no app: “Exportar logs da sessão para Downloads”. Enviar
logo após o travamento, antes de abrir outros jogos, para identificar a sessão.
Manter resolução e driver durante a comparação. Reiniciar o jogo após atualizar.

A beta mantém as opções de compilação SM8475 e o canal Research, pacote e chave
permanentes. Publicação somente após testes, build, assinatura e validação de
pacote/tamanho/SHA-256. main preservada.

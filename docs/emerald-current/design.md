# Zephyr · Emerald Current

## Composição de console

A biblioteca inicial foi reconstruída a partir da composição das referências:
um palco único, capa selecionada dominante, até quatro vizinhas em perspectiva,
reflexo no piso e ações vinculadas à seleção. O Z original continua sendo a marca
do Zephyr. As imagens conceito são referências visuais; não são capturas do app.

| Condição | Navegação | Biblioteca inicial | Ações |
|---|---|---|---|
| Retrato | Marca e quatro lâminas curvas no topo | Palco central; seletor compacto abaixo das ações | Título, jogar e ficha abaixo do palco |
| Paisagem a partir de 600 dp | Lâminas laterais e marca no cabeçalho | Palco ocupa toda a área restante à esquerda das ações | Vidro integrado com arte, título, jogar e ficha |
| Fonte ampliada | Rótulos de navegação roláveis | Conteúdo vertical rolável | Ações permanecem alcançáveis |
| Busca, filtros e lista completa | Botão de busca abre a biblioteca detalhada | Mantém os filtros, ordenação e vistas existentes | O mesmo botão retorna ao palco quando não há busca/filtro ativo |

A tela inicial não repete a grade completa nem o painel técnico persistente.
A ficha do jogo mantém informações, configurações, tempo e histórico existentes.
Selecionar capas, tocar nas setas, deslizar ou usar LB/RB altera a seleção sem
iniciar a emulação. Somente a ação Jogar inicia o fluxo de execução.
As bibliotecas com menos de cinco títulos não duplicam capas para preencher o palco.

## Materiais e identidade

Lâminas usam curvas assimétricas contínuas. Fundo com fitas largas esmeralda,
bordas finas iluminadas e iluminação localizada. O palco tem elipses sobrepostas,
reflexos discretos e capas com perspectiva, sombra, borda e bisel. As capas vêm
das entradas reais da biblioteca; o código não inclui capas substitutas geradas.

O botão Jogar tem um bisel luminoso e área central verde escura sob o texto
branco. As outras ações mantêm alvos de pelo menos 48 dp. A seleção usa posição,
tamanho e estado semântico, além da cor.

## Custo e limites

- Fundo, palco e biséis usam geometria/gradientes; não há bitmap de tela inteira.
- O palco monta no máximo cinco capas e seus reflexos.
- O seletor compacto permanece lazy.
- Não há temporizadores, partículas, blur em tela inteira ou rotação infinita.
- Não adiciona métricas de CPU/GPU/FPS ilustrativas à interface.
- Não altera resolução, cache do núcleo, assinatura ou comportamento dos jogos.

Sombras, transparências e reflexos têm custo de composição. Não houve medição
física no Edge 30 Ultra; não se presume ganho de desempenho.

## Verificação

O workflow Zephyr Emerald interface compila a interface e verifica biblioteca,
ficha, Configurações, Controles, navegação e fonte ampliada. Capturas Roborazzi
mostram o código real, usando capas e dados de exemplo das fixtures. A validação
funcional e a inspeção visual são etapas distintas: testes aprovados não provam
fidelidade estética ao conceito. O APK de distribuição não é gerado por esse workflow.

# Zephyr · Emerald Current

## Direção

Uma interface de console portátil com marca própria: preto esmeralda, verde
luminoso, lâminas curvas e capas em perspectiva. As referências enviadas em
14/11/2026 orientam a composição, não são screenshots do aplicativo nem prova
de disponibilidade das funções e métricas ali ilustradas.

O emblema continua sendo o Z original do Zephyr, acompanhado por dois arcos
abertos. Não adota a esfera Xbox como marca do produto. O fundo desenha uma
corrente curva discreta, em vez de preencher a leitura com fibra de carbono,
linhas de HUD e partículas. Painéis usam cantos alternados e reflexo estático.

## Composição adaptativa

| Condição | Navegação | Biblioteca | Detalhes |
|---|---|---|---|
| Retrato | Marca e quatro lâminas acima do conteúdo | Destaque central com até quatro capas reais vizinhas, seguido da biblioteca completa | Ações no destaque e ficha por toque |
| Paisagem a partir de 840 dp | Lâminas na lateral | Capas com escala/perspectiva de seleção e lista filtrável | Painel persistente de 260–300 dp |
| Paisagem de 600–839 dp | Lateral | Conteúdo ocupa a largura restante | Toque abre ficha; não comprime uma terceira coluna |
| Janela abaixo de 600 dp | Navegação superior | Composição compacta | Conteúdo rolável |

Tamanhos são calculados pela área real da janela, não pelo modelo do aparelho.
A navegação de Configurações e Controles preserva seu histórico e seus destinos.
Diálogos mantêm posição inferior em retrato e central em paisagem. Capas ao
redor do destaque podem ser tocadas para selecionar outro título; não iniciam
a emulação. A lista e o carrossel existentes também mantêm seleção e detalhes.

## Tokens

| Papel | Cor |
|---|---|
| Fundo | `#04110F` |
| Painel | `#0B231E` |
| Superfície elevada | `#113128` |
| Ação | `#15573F` |
| Destaque | `#87F16D` |
| Texto principal | `#E9F8F0` |
| Texto secundário | `#C0D9CE` |
| Texto auxiliar | `#A7C4B7` |

Contraste calculado mínimo entre textos/destaque e as superfícies opacas: 5,53:1.
Cores semânticas de erro e compatibilidade continuam independentes da marca.
O modo controle conserva a adaptação de atmosfera por capa, com o mesmo
vocabulário de superfícies e geometria. O fundo de arte fica menos intenso e
não utiliza mais o blur grande daquele frame.

## Movimento e custo

- A corrente de fundo e os painéis são vetores/gradientes com `drawWithCache`.
- O emblema orbital é estático; não há temporizadores, partículas ou rotação infinita.
- O carrossel anima escala apenas quando a seleção muda.
- O palco usa no máximo cinco capas e a lista permanece lazy.
- Não adiciona consultas de CPU/GPU, sensores nem FPS ilustrativos.
- Não altera resolução, cache do núcleo, assinatura ou comportamento do jogo.

Não houve medição de custo no Edge 30 Ultra; o desenho reduz efeitos contínuos
por construção, mas isso não é prova de ganho de desempenho.

## Verificação

O workflow `Zephyr Emerald interface` compila a interface Kotlin e executa os
testes reais de biblioteca, ficha do jogo, Configurações e Controles. Produz
capturas Roborazzi em retrato, paisagem e fonte ampliada (1,4×), além dos
relatórios de teste. As capas e dados dessas capturas vêm das fixtures do projeto,
não de uma sessão real do usuário. O resultado de cada execução deve ser
consultado no GitHub; este documento não presume aprovação antecipada.

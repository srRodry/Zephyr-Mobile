# Zephyr — biblioteca como ambiente de capas

Data: 2026-10-14. Base: 280b4a875ef4bd0870d8a56f1afedf925baa5e16, UI #85 aprovado.

## Implementação
- LibraryShowcase.kt: superfície prata local, arte de jogo, prateleira LazyRow, ações do título selecionado e escolha de ordenação/exibição com alvos 48dp.
- LibraryTouch.kt: remove slogan e hero editorial, incorpora ferramentas/prateleira/lista ao mesmo LazyVerticalGrid. Mantém gridState e callbacks da tela, sem mudar viewmodels/modelo, persistência ou lançamento.
- A prateleira usa toda a lista filtrada/ordenada, com chaves stableId; carregamento visual lazy. Clique seleciona, toque longo abre ficha. X abre ficha e Y alterna favorito conforme contrato anterior do modo toque. Entrar em foco seleciona; mudança de seleção/filtro traz o título para a faixa visível quando necessário.
- Retrato/compacto: ações Jogar, Ficha e favorito sob a prateleira. Paisagem larga: painel lateral preserva tempo jogado, sessão anterior, patches, perfil e ajustes rápidos.
- Busca: botão explícito; campo 48dp aparece quando aberto ou quando há query. Limpar fecha campo e remove query, mantendo filtro. Limpar no estado sem resultados remove query e filtro como antes. Busca ativa remove prateleira redundante e prioriza os resultados.
- Exibição/ordenação continuam visíveis e quebram linha via FlowRow. Opções pequenas/lista/médias/grandes mantêm o enum CoverDensity e a preferência ui_look/cover_density; MEDIUM continua lista no modo compacto e grade em paisagem larga. Nenhum default de configuração alterado.
- Todos/Favoritos/coleções/formatos continuam disponíveis com contagens do modelo existente. O título Todos os jogos só aparece no conjunto sem filtro/busca; demais conjuntos usam Jogos.
- Botões e filtros 48dp; capas/listas têm seus próprios alvos maiores. Rótulos longos e campos de busca ainda precisam de verificação física com fonte ampliada.
- Perfil ativo, instalação de conteúdo, preparação de launch, multidisco e diálogo de ações continuam fornecidos por GameLibraryScreen/GameActions existentes; esses arquivos não foram modificados.

## Arte e verdade dos dados
Nenhuma capa gerada da projeção foi embutida. A mesma fonte artOf/cache existente fornece os arquivos. Capa real preenche a moldura 3:4; ícone extraído pequeno fica contido em até64dp dentro de fundo neutro, sem blur, sombra ou expansão artificial. Estrela de favorito e indicação real de disco são preservadas. A projeção com capas completas é ilustrativa e não garante a existência dessas capas no acervo do usuário.
Sem FPS, tempos de sessão, compatibilidade ou datas fictícios. Resultado de compatibilidade continua sendo o registro local; texto escuro legível acompanha a indicação colorida.

## Superfícies e regressões
A prata só é fornecida ao viewport e painel da biblioteca. Shell, outras telas e modo controle não são recoloridos. Avisos conservam seus tokens semânticos sobre superfície escura; componentes existentes de ajustes rápidos também conservam seus tokens escuros, sem mudar valores ou momento de aplicação.
Contraste calculado: #155ED2/#F5F7FA 5,49:1; #354254/#F5F7FA 9,50:1; #48576B/#F5F7FA 6,86:1; branco/#155ED2 5,89:1.

## Validação
XML de recursos inglês/pt-BR analisado com chaves únicas e paridade de nomes. Contratos revisados contra a base. CI do commit desta etapa deve compilar Compose e executar regressões existentes; resultado efetivo está no workflow, sem inferir aprovação antecipada.
Testes existentes cobrem modelos de biblioteca, dados/persistência, navegação e menu em jogo. A regressão Compose/Robolectric da prateleira descrita abaixo verifica interação real no ambiente simulado; não substitui teste físico.
Pendentes no aparelho: 0/1/3/muitos jogos, busca/filtros, retorno da ficha, troca de orientação, fonte ampliada, ícones sem capas, multidisco, uso de teclado/controle e gestos de refresh. Retorno de foco e posições são preservados pelos estados existentes, mas ainda não comprovados fisicamente para a composição nova.

## Continuidade
Ficha, perfis, hubs e guia em jogo ainda devem receber a nova apresentação; esta etapa reconstrói somente biblioteca touch e seu painel lateral. Layout específico LibraryController, núcleo, áudio, assinatura e workflows não foram modificados. Nenhum APK disparado nesta etapa.

## Seleção ao consultar ficha
Correção de revisão: todas as aberturas pela prateleira/lista/toque longo/X passam por openSelected, que atualiza onSelect antes de onOpen. Evita retornar à biblioteca com destaque de outro jogo. Contratos de lançamento e estado selectedId do host são reutilizados; retorno visual ainda depende de teste no aparelho.

## Regressão de seleção e rolagem
UI #87 compilou Compose e aprovou integração ARM64, mas falhou em BaseShots.libraryLand: o seletor de contentDescription do cartão antigo não estava composto na viewport. A prateleira agora expõe explicitamente o nome completo do jogo aos serviços de acessibilidade. BaseShots rola o grid até a prateleira e a prateleira até a capa, seleciona e exige selected=true, zero callbacks de abertura de ficha e detalhes pertencentes à identidade correta. Novo caso equivalente em retrato. Sem remover asserções de seleção nem substituir clique por chamada direta de callback. Tags delimitam os dois layouts lazy e evitam pressupor posição/altura de tela. Resultado final deve ser consultado no CI seguinte.

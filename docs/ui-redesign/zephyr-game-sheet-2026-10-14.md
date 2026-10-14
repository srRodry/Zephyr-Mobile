# Zephyr — ficha de jogo com identidade e planos de navegação

Data: 2026-10-14. Base: 9c85ce85564557ccd3996f5957cfe97b80f4cd28, UI #88 aprovado.

## Implementação
- GameTouchSheet.kt: nova apresentação exclusiva do modo toque, integrada às lâminas do shell em retrato/compacto e paisagem larga.
- Identidade única com arte real contida, nome, formato, resultado local de compatibilidade e ações Jogar/Iniciar com. Favorito e menu de ações continuam disponíveis.
- Title ID e Media ID continuam acessíveis por Identificação do jogo; ausência é mostrada como traço, sem inventar valores.
- Três planos principais: Visão geral, Ajustes e Dados. Ajustes conserva Todos os ajustes e todos os grupos set:<grupo> em menu explícito. Dados conserva Saves e dados, Patches e conteúdo e Desempenho em opções internas visíveis.
- IDs GameSections, callbacks de lançamento, LaunchFormState, escopo global/por jogo, patches, conteúdo, saves, reports e gerenciamento continuam originais. A apresentação não modifica defaults nem métodos de persistência.
- XdSectionedScreen recebe touchFrame opcional depois da seleção/histórico comuns; o caminho de controle continua o anterior e ignora essa apresentação. Pop/back usa o mesmo XdSectionHistory/BackHandler. Overlay continua no wrapper. Último argumento onPad preservado para lambdas finais.
- Links de Visão geral usam onSection e registram retorno; seleção manual por planos e submenus usa history.pick como o antigo menu. Voltar para <origem> aparece somente quando há passo de histórico. Biblioteca continua saída explícita da ficha.
- Identidade, ações, abas e conteúdo rolam juntos sob a barra de retorno: evita consumir todo o viewport em paisagem baixa ou fonte ampliada. ScrollState segue a seleção da seção, como no corpo anterior.
- GameCards.Overview remove hero duplicado e usa links claros no plano prata. Dados reais de sessão, gráficos, compatibilidade, patches e ajustes rápidos continuam nas implementações existentes.
- Painéis técnicos mantêm os tokens semânticos escuros para preservar avisos, erros e gráficos; prata fica na moldura de conteúdo/identidade/navegação. Não aplicar cores claras indiscriminadamente às telas antigas.
- Forma de botão passa a ser local: telas legadas continuam com o arredondamento anterior; planos prata da biblioteca/ficha usam 4dp. Nenhum valor de configuração, interação de botão ou cor de status foi alterado por essa opção visual.

## Arte e dados
Reutiliza LibraryArtwork e a mesma fonte/cache CoverArt. Capas reais preenchem moldura; ícones pequenos ficam contidos, sem blur nem ampliação artificial. Nenhuma imagem gerada da projeção foi embarcada. Nome completo aparece uma vez na identidade principal, sem IDs na barra superior. Não inventar medições, sessões ou compatibilidade.

## Verificação
XML inglês/pt-BR analisado, 20 chaves únicas com paridade no arquivo de UI2. Contratos e blocos de controle revisados contra a base.
CI antes desta etapa só executava BaseShots.library*, deixando casos existentes de ficha fora do gate. O filtro passa a BaseShots inteiro: inclui fichas retrato/paisagem, grupos de ajustes, desempenho, conteúdo/dados, controle e Iniciar com.
Novos casos Compose/Robolectric verificam link Ajustes -> retorno Visão geral, navegação Dados -> conteúdo -> desempenho e expansão/recolhimento dos identificadores reais. Casos existentes de deep link também exigem área principal e conteúdo corretos. Iniciar com agora rola até a ação antes de tocar, conforme a nova viewport.
Consultar CI do commit para resultado efetivo; não inferir aprovação antes de terminar. Registros e dados do Fixture são artificiais e exclusivos de teste, sem valor como medição do telefone.

## Limites e continuidade
Pendente no Motorola: legibilidade com fonte ampliada, rolagem em paisagem baixa, foco ao retornar de diálogos, launch/preparação real, identificação sem Title ID e modos de controle físico. Testes Robolectric não comprovam a experiência no aparelho.
A apresentação própria por controle, os corpos técnicos e os diálogos conservam partes da linguagem anterior; perfis/hubs/guia em jogo e polimento transversal ainda devem seguir a direção nova.
Núcleo, áudio, assinatura e workflow APK não foram modificados. Sem marcador APK nesta etapa; sem release.

# Zephyr: reconstrução completa da experiência visual
Data: 2026-10-14. Base auditada: 7211e80b37cc0fa3a62010fc48ef373eeb32383c.
Status: pesquisa concluída e primeira proposta visual produzida; implementação ainda não iniciada. A direção foi solicitada pelo usuário; o desenho específico ainda não recebeu avaliação dele.

## Por que substituir a direção anterior
O usuário considera a UI atual genérica e pede reconstrução integral. A biblioteca atual foi confirmada por gravações: busca/densidade/filtros, título editorial, destaque e coleção de cartões, com navegação inferior. Melhorar densidade e hierarquia não altera essa arquitetura.
A nova proposta substitui o padrão de cartões arredondados e navegação inferior por um ambiente contínuo de biblioteca, identidade, áreas e guia. Não promete melhorar desempenho de emulação.

## Evidência e limites da pesquisa
Os anexos X360-Mobile---OFFICIAL-0.5.2.zip (20740 bytes) e 0.5.3_preview.zip (21288 bytes) contêm cinco READMEs, não APKs/código. A preview acrescenta Hey Matty! (388 bytes). Única diferença nos cinco READMEs: Android 12 -> Android 11. CRCs verificados sem erros. Não sustentam análise da implementação.
Capturas públicas visualmente inspecionadas:
- https://elpoderdelandroideverde.com/wp-content/uploads/2026/04/x360-mobile3.jpg — biblioteca com capas, abas superiores, seleção e comandos de controle no rodapé.
- https://elpoderdelandroideverde.com/wp-content/uploads/2026/04/x360-mobile4.jpg — hub de configurações com grandes blocos.
- https://elpoderdelandroideverde.com/wp-content/uploads/2026/04/x360-mobile5.jpg — detalhes de configurações com seções.
- https://elpoderdelandroideverde.com/wp-content/uploads/2026/04/x360-mobile.jpg — assistente inicial com progresso.
Contexto dessas imagens: artigo descreve alfa 0.4; imagens não devem ser atribuídas como prova de 0.5.3 ou da versão atual.
Artigo: https://elpoderdelandroideverde.com/x360-mobile/
Fonte primária: https://github.com/Ashnar2602/X360-Mobile---OFFICIAL/releases/tag/v0.6.0 — notas de 7 de agosto descrevem interface híbrida Metro/Blades, experiências Player/Debug, foco e posição preservados, navegação por controle e telas responsivas. São alegações/notas do mantenedor, não teste independente do APK.
Fontes históricas: https://news.xbox.com/en-us/2011/06/06/cloud-storage-and-more-coming-to-xbox-live/ e https://news.xbox.com/en-us/2011/12/05/a-letter-from-marc-whitten-a-new-xbox-360-experience/
O site https://x360mobile.com/ teve extração textual incompleta. Busca pública por demonstrações não forneceu vídeo utilizável nesta etapa; não foi examinado fluxo em vídeo nem executado X360 Mobile.

## Direção proposta
- Identidade Zephyr: grafite #15191F, azul #237BFF, prata #F5F7FA. Superfície clara grande com texto escuro dentro de moldura escura. Versão escura equivalente deve permanecer disponível.
- Lâminas como linguagem de navegação espacial: conteúdo principal ocupa um plano; área ativa tem borda/forma e rótulo, não somente cor. Diagonal discreta remete ao Z; nenhuma geometria reduz área de toque.
- Retirar slogans grandes da biblioteca, decoração vazia e cartões redundantes.
- Capas reais do acervo em prateleira; lista/grade completa permanecem disponíveis para coleções grandes. Sem arte gerada como recurso de jogo no APK.
- Identidade do perfil presente no shell; menu do perfil contém troca de perfil e atribuições existentes, sem serviços sociais fictícios.
- Sem logotipo, som de inicialização, dashboard original ou recursos de Xbox Live. Inspiração na estrutura, com identidade própria.

## Mapa de navegação proposto
As quatro áreas do mockup são agrupamentos de apresentação; preservar XdArea e contratos de navegação existentes na primeira migração.
| Área principal | Funções existentes acessíveis |
| --- | --- |
| Jogos | Biblioteca, busca, filtros, ordenação, densidade, favoritos, coleções, ficha e lançamento |
| Conteúdo | Instalação, títulos/atualizações/DLCs e fluxos atuais de conteúdo |
| Perfis | Listar/criar/editar/ativar, jogadores, saves, importação/exportação e lixeira |
| Sistema | Configurações, Controles, Drivers, Diagnóstico, comparação e Sobre |
Controles e Drivers precisam ter entradas com rótulos explícitos no hub Sistema. Não eliminar rotas, atalhos, callbacks nem navegação de retorno.

## Telas e comportamento
1. Shell: cabeçalho compacto com marca e perfil; lâminas superiores em retrato, laterais em paisagem quando houver largura. Texto não truncado; overflow/rolagem acessível quando necessário.
2. Biblioteca: prateleira do conjunto filtrado e foco identificável; nenhuma escolha de título por compatibilidade inventada. Ações Jogar e Ficha distintas; tocar capa seleciona, ação explícita inicia. Grade/lista continuam navegáveis; preservar estado de busca, filtro, ordenação, seleção e rolagem.
3. Ficha: identidade uma vez; Jogar e Iniciar com; planos Visão geral/Ajustes/Dados com links explícitos a patches, conteúdo, saves e última sessão. Sessão anterior ausente nunca vira data ou FPS de exemplo.
4. Perfis: cartão de identidade, perfil ativo e slots reais; XUID em Detalhes; criação única, ações secundárias contextualizadas. Preservar recuperação/lixeira e exportação.
5. Sistema: blocos funcionais para controles, imagem, configurações, drivers, dados e diagnóstico, vinculados às telas existentes; mostrar valores efetivos e momento de aplicação apenas quando contrato existente comprovar.
6. Guia durante o jogo: painel opaco sobre a cena, navegação de categorias e ação Retomar; Encerrar separado e confirmado. Estado pausado precisa vir do host; abrir guia não deve alegar pausa sem efetivá-la. Preservar todas as categorias/controles atuais mesmo que mockup mostre só uma amostra.
7. Diálogos/estados: mesma linguagem para erro, carregamento, biblioteca vazia, arquivo ausente, importação, confirmação e retorno.

## Acessibilidade e custo
Alvos >=48dp; textos redimensionáveis; contraste medido antes de implementação. Azul #237BFF não implica aprovação de qualquer texto pequeno branco: medir combinações e usar azul mais escuro para controles quando necessário. Foco visual e semântica explícitos. Toque e controle usam a mesma estrutura; dicas de botões seguem contratos e inversão A/B configurada, não legendas hardcoded do mockup.
Evitar blur em tempo real, shaders decorativos, animação contínua ou novas consultas de métricas. Carregamento de capas reutiliza cache e modelo existente. Respeitar redução de movimento e manter recuperação de foco após diálogos. Recursos visuais não precisam depender de dados remotos.

## Arquivos e sequência de implementação
1. XdShell.kt / XdTheme.kt / XdControls.kt: camada de shell e tokens novos, mantendo XdNavigator e XdArea. Revisar hosts chamadores antes de alterar assinatura.
2. LibraryTouch.kt / GameLibraryScreen.kt / LibraryController.kt: prateleira e plano de ações; manter viewmodels, filtros, coleções e GameActions.
3. GameScreen.kt / GameCards.kt: ficha como plano; preservar parâmetros/rotas de lançamento, perfis e instalação multidisco.
4. ProfilesScreen.kt / SettingsScreen.kt: hubs e detalhes; preservar persistência e gerenciamento de dados.
5. InGameMenu.kt / InGameMenuState.kt e telas de conteúdo/controles/drivers/diagnóstico: guia e consistência final.
6. Estados vazios/erros, diálogos, strings pt-BR/en, acessibilidade e matriz de navegação.
Entregar por etapas verificáveis dentro da feature/zephyr-mobile-branding. Não remover a apresentação antiga antes de a substituta cobrir os fluxos correspondentes. Não adicionar uma preferência visual temporária persistente sem necessidade.

## Critérios de aceitação
- Coleção de 0/1/3/muitos jogos; nomes longos, capa ausente, busca sem resultado, favoritos/coleções e arquivo ausente.
- Retrato 360dp; paisagem baixa <400dp de altura; fonte aumentada; tablet. Ações não cortadas.
- Toque, D-pad, A/B conforme configuração, retorno, menus, cancelamento; foco e rolagem preservados.
- Perfil ativo/troca/slots, saves e backup/lixeira mantidos.
- Jogar/Iniciar com/instalação de conteúdo mantêm callbacks existentes.
- Menu em jogo, retomada e confirmação de encerramento mantêm host e ciclo de vida.
- CI Compose/recursos e regressões relevantes aprovado; só depois gerar APK quando solicitado/necessário.
- Validação física de navegação é distinta dos testes JVM. Nenhum ganho de FPS, compatibilidade ou áudio prometido.

## Entrega desta etapa
Primeira prancha visual com biblioteca retrato/paisagem, ficha e guia. Dados, capas, avatar, cena e última sessão do desenho são ilustrativos, não recursos/dados do aplicativo. A prancha é proposta de design, não implementação.
Alteração de repositório exclusivamente documental. Nenhuma mudança em runtime, áudio, núcleo, assinatura ou gate APK.

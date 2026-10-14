# Zephyr — primeira etapa da reconstrução: navegação em lâminas

Data: 2026-10-14. Base: 235d1de0b5646288bfc7972f7bce19b2cec07685, UI #83 aprovado.

## Mudança implementada
- Novo XdBlades.kt: quatro grupos de apresentação, marca e nome real do perfil, menu de destinos existentes.
- Retrato: navegação passa da barra inferior para acima do conteúdo; paisagem: rail textual com quatro lâminas, rolagem em baixa altura e acesso ao perfil.
- Jogos abre Biblioteca/Coleções. Conteúdo e Perfis navegam diretamente. Sistema abre Ajustes/Controles/Drivers/Diagnóstico/Comparação/Sobre.
- XdArea e XdNavigator permanecem idênticos; agrupamento não muda rotas nem persistência. Ganho de um grupo visual não elimina destinos.
- XdSectionedScreen e XdSingleScreen usam a nova moldura em modo toque; fluxo modal showNav=false continua sem navegação de áreas.
- LibraryTouch usa a mesma navegação. Filtros, busca, densidade, ações e gerenciamento da biblioteca permanecem existentes.
- Insets: header consome topo/laterais; viewport mantém espaço inferior de gestos em retrato. Rail mantém cutout lateral/topo/baixo em paisagem.
- Lâmina selecionada tem marca branca e semântica selected além da cor; alvo mínimo 56dp, ações de perfil/menus 48dp. Rótulos das áreas podem quebrar linha. Menus fecham antes de navegar e estado é reiniciado ao mudar área.
- Texto branco sobre #155ED2 usa a combinação previamente calculada de contraste ~5,89:1. Não aplicar branco pequeno indiscriminadamente sobre #237BFF.
- Forma estática; sem blur, animações contínuas, relógios, consultas de métricas nem arte externa.

## Validação e limites
Recursos inglês/pt-BR foram analisados como XML, com chaves únicas. Revisão dos três hosts touch e contratos de navegação. CI remoto deve compilar Compose e executar as regressões existentes; consultar o workflow do commit desta etapa para o resultado efetivo.
Não adicionar testes que só repitam a implementação de agrupamento. A revisão visual, fontes ampliadas, menus com controle/teclado, preservação física de foco e insets no Motorola exigem próximo APK e teste no aparelho.
A busca GitHub de referências devolveu incomplete_results=true; não foi usada como prova de ausência de outros chamadores. A compilação de todas as fontes Compose é o gate para referências removidas.

## Escopo ainda pendente
Esta etapa não completa o redesign: ainda há biblioteca editorial/cartões antigos, corpo de configurações/perfis anterior e apresentação específica de controle anterior. Seguem pendentes prateleira de capas, plano de ficha, hubs, superfícies prata/escura coerentes e guia em jogo. Nenhum ganho de FPS ou alteração de áudio prometido.
Não houve alteração no núcleo, áudio, assinatura, viewmodels ou workflows. Commit sem marcador APK; não solicitar APK antes de CI aprovado.

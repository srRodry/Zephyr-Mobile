<h1 align="center">ZEPHYR MOBILE</h1>

<p align="center"><strong>Emulação de Xbox 360 no Android, com uma interface pensada para toque e controles.</strong></p>

<p align="center">
  <img alt="Status: em desenvolvimento" src="https://img.shields.io/badge/status-em%20desenvolvimento-237BFF">
  <img alt="Android 10 ou superior" src="https://img.shields.io/badge/Android-10%2B-263241?logo=android&logoColor=white">
  <img alt="Arquitetura ARM de 64 bits" src="https://img.shields.io/badge/ABI-arm64--v8a-263241">
  <img alt="API gráfica Vulkan" src="https://img.shields.io/badge/API-Vulkan-263241">
</p>

<p align="center">
  <a href="README.md">English</a> · <b>Português (Brasil)</b>
</p>

<p align="center">
  <a href="https://github.com/srRodry/Zephyr-Mobile/actions/workflows/zephyr-ui-check.yml">Testes da interface</a> ·
  <a href="https://github.com/srRodry/Zephyr-Mobile/actions/workflows/XenDroid.yml">Compilação do APK</a> ·
  <a href="https://github.com/srRodry/Zephyr-Mobile/issues">Problemas e sugestões</a>
</p>

---

## Sobre o projeto

O **Zephyr-Mobile** é um projeto experimental de emulação de Xbox 360 para Android, desenvolvido no repositório `srRodry/Zephyr-Mobile`. Nosso objetivo é aprimorar a experiência em dispositivos móveis com navegação mais clara, identidade visual consistente, suporte a controles e uma biblioteca de jogos acessível.

O projeto **aproveita tecnologias e código aberto existentes**: [Xendroid+](https://github.com/phforner0), o port anterior [XenDroid](https://github.com/rfandango) e o núcleo de emulação [Xenia](https://github.com/xenia-project/xenia) / Xenia Canary. O Zephyr-Mobile **não é um núcleo de emulação criado do zero**. Mantemos os créditos e as obrigações de licenciamento dos projetos de origem.

> [!IMPORTANT]
> **Versão de desenvolvimento — ainda não é um lançamento estável.** Melhorias na interface e nos processos de compilação não garantem que um jogo funcione corretamente. A compatibilidade depende do título, do dispositivo, do driver Vulkan e do núcleo do emulador. Um teste de interface aprovado não comprova que o APK é instalável nem que os jogos são executados corretamente.

## O que estamos desenvolvendo

| Área | Trabalho atual |
| --- | --- |
| **Identidade visual** | Superfícies em grafite (`#15191F`), azul de destaque (`#237BFF`), tipografia legível e componentes consistentes em Jetpack Compose. |
| **Biblioteca de jogos** | Busca, filtros, favoritos, coleções, capas e estados de seleção, aprimorando funcionalidades herdadas do aplicativo. |
| **Navegação por toque** | Interface adaptada aos modos retrato e paisagem, com ações práticas e informações dos jogos. |
| **Navegação por controle** | Indicadores de foco, seleção e navegação na biblioteca. |
| **Acessibilidade** | Identificação mais clara dos jogos, foco visível e diferenciação dos elementos selecionados. |
| **Testes automatizados** | Compilação Kotlin/Compose e testes de regressão da biblioteca e da navegação por controle. |
| **APK experimental** | Workflow ARM64 separado, com assinatura e verificação de conteúdo e identificação do pacote. |

Esses itens descrevem implementações ou processos **em desenvolvimento**, não uma garantia de funcionamento completo em aparelhos reais. O projeto também incorpora funcionalidades preexistentes do código de origem, documentadas nos arquivos técnicos.

## Andamento do desenvolvimento

| Etapa | Situação |
| --- | --- |
| Nome Zephyr-Mobile e identidade visual básica | Implementados na branch de desenvolvimento |
| Melhorias da biblioteca para toque e controle | Implementadas; faltam mais testes em aparelhos |
| Compilação e testes automatizados da interface | Configurados no GitHub Actions |
| APK experimental separado (`xendroid.compose.zephyr.test`) | Workflow configurado; compilação concluída e instalação ainda exigem confirmação |
| Compatibilidade de jogos Xbox 360 no Android | Experimental; deve ser verificada por jogo e dispositivo |
| Versão pública estável | **Ainda não anunciada** |

A branch de desenvolvimento é [`feature/zephyr-mobile-branding`](https://github.com/srRodry/Zephyr-Mobile/tree/feature/zephyr-mobile-branding). A branch `main` permanece separada enquanto validamos as alterações.

### Próximas etapas

1. Confirmar a conclusão do workflow de APK, verificar o arquivo assinado e testar a instalação em um dispositivo ARM64 real.
2. Validar as interfaces de toque, a navegação por controle, a acessibilidade e os estados da biblioteca.
3. Testar abertura de jogos e estabilidade, distinguindo problemas de interface dos problemas do núcleo de emulação.
4. Registrar resultados verificáveis de compatibilidade e preparar um processo reproduzível de versões experimentais.

## Requisitos

- **Android 10 ou superior**, com arquitetura **ARM64 (`arm64-v8a`)**.
- **GPU compatível com Vulkan** e driver apropriado; Snapdragon/Adreno com drivers Turnip compatíveis é um dos focos da base técnica utilizada.
- Cópias de jogos de Xbox 360 obtidas legalmente, em formatos aceitos pelo emulador de origem.

Hardware mais potente pode ajudar, mas **nenhum dispositivo ou jogo tem funcionamento garantido**. As medições de desempenho dos projetos de origem não devem ser apresentadas como benchmarks do Zephyr-Mobile.

## Como testar um APK experimental

1. Acesse o workflow [Zephyr Android APK](https://github.com/srRodry/Zephyr-Mobile/actions/workflows/XenDroid.yml).
2. Procure uma execução **concluída com sucesso** na branch `feature/zephyr-mobile-branding`.
3. Se houver um artefato disponível, baixe o APK `Zephyr-Mobile_test_<commit>` na página da execução.
4. Instale o APK assinado em um Android compatível e verifique primeiro a abertura do app, a biblioteca e a navegação por controle.

> [!CAUTION]
> O APK **pode ainda não estar disponível**. Os artefatos expiram, e uma execução incompleta ou com erro não representa um aplicativo validado. As versões experimentais usam o identificador `xendroid.compose.zephyr.test`. Se duas versões forem assinadas com chaves temporárias diferentes, talvez seja necessário desinstalar a anterior. Faça backup dos dados antes disso.

## Como compilar

A compilação nativa herdada utiliza **JDK 21**, Android **SDK 35**, **NDK 29.0.14206865**, **CMake 3.30.3**, Gradle **8.11.1**, Python e ferramentas de shaders SPIR-V. Consulte [BUILD.md](BUILD.md) para configurar o ambiente, as ferramentas nativas e a assinatura do APK.

Para compilar a variante experimental usando o identificador do Zephyr-Mobile:

```bash
./gradlew -PxendroidReleaseIdSuffix=.zephyr.test -PxendroidReleaseDebuggable :app:assembleRelease
```

O Gradle compila o aplicativo, mas **isso não significa que o APK resultante esteja assinado e pronto para instalação**. O workflow do GitHub Actions executa separadamente a assinatura e a verificação do pacote.

Para executar os testes de interface configurados no CI:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testUitestUnitTest
```

### Estrutura do repositório

| Caminho | Função |
| --- | --- |
| `app/` | Aplicativo Android em Kotlin/Jetpack Compose, biblioteca e interface. |
| `emulator-core/` | Núcleo nativo de emulação Xbox 360 herdado e integração com Android. |
| `.github/workflows/zephyr-ui-check.yml` | Compilação e testes de regressão da interface. |
| `.github/workflows/XenDroid.yml` | Compilação, assinatura e verificação do APK ARM64. |
| `docs/ui-redesign/` | Estudos de design e documentação da interface (incluindo materiais anteriores ao Zephyr). |
| `performance-tests/` | Investigações de desempenho herdadas; não equivalem a uma validação específica do Zephyr. |

## Documentação

- [Guia de compilação e instalação](BUILD.md)
- [Configurações específicas por jogo](GAME_COMPAT.md)
- [Documentação do redesenho da interface](docs/ui-redesign/README.md)
- [Registro técnico de recursos e validações](docs/experiencia-em-jogo-status.md)
- [Licenças e componentes de terceiros](THIRD-PARTY-NOTICES.md)

## Créditos e licenças

O Zephyr-Mobile é desenvolvido neste repositório por **[srRodry](https://github.com/srRodry)**, com importantes contribuições herdadas de:

- **Xendroid+**, criado e mantido por **[phforner0](https://github.com/phforner0)**.
- **XenDroid**, port anterior para Android desenvolvido por **[rfandango](https://github.com/rfandango)**.
- **[Xenia](https://github.com/xenia-project/xenia)** e Xenia Canary, responsáveis pela tecnologia central de emulação.
- Outros projetos de código aberto, fontes, drivers e componentes gráficos citados em [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

As licenças e atribuições originais continuam válidas. O arquivo de avisos de terceiros descreve, inclusive, obrigações relacionadas ao binário combinado e a seus componentes. **Alterar o nome ou a interface do aplicativo não transfere a autoria dessas tecnologias.**

## Aviso legal

O Zephyr-Mobile é um projeto independente e experimental, **sem vínculo ou endosso da Microsoft**. Xbox e Xbox 360 são marcas da Microsoft Corporation.

**Este repositório não distribui jogos comerciais, imagens de jogos, firmware, chaves de consoles ou outros dados protegidos pertencentes aos jogos.** Utilize cópias de segurança próprias, respeitando a legislação aplicável.

---

<p align="center"><sub>Zephyr-Mobile · Em desenvolvimento · Interface, usabilidade e validação em primeiro lugar.</sub></p>

# SDL Android sample wrapper — diferença esclarecida

Nenhum arquivo SDL foi alterado nesta auditoria. Main consultada por SHA:
`1066f8fd6cab89302b364b341ba8687781d47182`. Desenvolvimento consultado em
`ade50f603331b12be99878b5aa3c23a60a2380c9`.

Arquivo: `emulator-core/src/main/cpp/xenia/third_party/SDL3/android-project/gradle/wrapper/gradle-wrapper.jar`.

| Estado | Bytes | SHA-256 |
| --- | ---: | --- |
| Main | 54213 | `2c23278a62dc9f96ab11bee897d9f53e6479d6419135e4560ce822a6431df5e5` |
| Desenvolvimento | 43583 | `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |

Ambos os ZIPs passaram na verificação CRC. O original contém 49 entradas e o
atual 33; há classes adicionadas/removidas/alteradas, não apenas timestamps.
No original `build-receipt.properties` declara `versionNumber=3.4-rc-2` e o
manifesto `Implementation-Version: 3.4`. O properties do sample atualmente
aponta para a distribuição Gradle 8.12.

A consulta ao **histórico completo do arquivo no GitHub** identifica a mudança
no commit [afec0241](https://github.com/srRodry/Zephyr-Mobile/commit/afec0241be1639912ebaa7cfc7436788ddf0d306),
`fix(build): replace legacy SDL Android wrapper with official Gradle 8.12`.
Sua mensagem explica o wrapper antigo rejeitado pela validação de CI e registra
o hash verificado. O hash atual coincide com o publicado pelo próprio Gradle
para o wrapper 8.12, na [referência oficial de checksums](https://gradle.org/release-checksums/).

A primeira consulta Git local apontou falsamente para `abfe177` e tamanho
anterior zero, pois esse commit é a fronteira rasa do clone. Não usar a diff
contra a fronteira rasa para atribuir origem. O histórico do GitHub corrigiu
essa limitação e mostra `afec0241` e o commit inicial como únicos resultados.

Conclusão: diferença intencional de ferramenta de build, com hash oficial
confirmado. Não constitui alteração do núcleo SDL de execução nem explica a
queda de FPS. Não foi restaurada nem substituída nesta sessão. A auditoria não
é um teste de gameplay ou uma auditoria completa de todas as dependências.

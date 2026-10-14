# Integração real ARM64 sem produzir APK

Base `994316e`: UI #55 aprovado, APK #40/release ignorados. A etapa de 01h revisou
os limites dos testes existentes. Emitters são extraídos e codificados em Linux
x86-64 com adaptadores; isso valida semântica relevante, porém não compila a
unidade completa com seus tipos de operandos, includes e flags do NDK. JNI é
conferido estaticamente. Há uma lacuna antes do APK matinal, não uma falha de FPS
comprovada nem um motivo para alterar políticas do núcleo.

## Incremento único: job Android ARM64 de integração

`zephyr-ui-check.yml` passa a ter um job independente de integração nativa.
Usa JDK 17, Android SDK 35, NDK 29.0.14206865, CMake 3.30.3 e as ferramentas SPIR-V
exigidas pela configuração. Gradle configura CMake Release como no APK experimental
(`xendroidReleaseDebuggable` mantém código nativo Release). Em seguida, um seletor
lê o compilation database real e manda o Ninja compilar somente três objetos:

- `cpu/backend/a64/a64_sequences.cc`: operações alteradas, tipos e helpers reais.
- `cpu/ppc/ppc_translator.cc`: integração do escopo opt-in JIT com o fluxo real.
- `emulator_xendroid.cpp`: integração JNI e snapshots no lado Android.

O Ninja resolve dependências de headers gerados. Dois compiladores simultâneos
limitam memória. O seletor exige uma entrada exata por fonte, target Android
AArch64, build directory comum, outputs `.o` dentro desse diretório e usa o Ninja
configurado em CMakeCache. Não executa comandos crus do database nem escolhe
arquivos por nome isolado. Databases ambíguos falham com instrução clara; erros
reais do compiler/Ninja são propagados, não ocultados. Não chama assemble,
assinatura, publicação ou o workflow APK; o gate deste permanece intacto.

Quatro regressões do seletor cobrem comandos com caminhos entre aspas, seleção
exata, entradas ausentes/duplicadas, target host incorreto, output de biblioteca
ou externo, diretórios misturados e propagação da falha do Ninja. Essas regressões
não simulam compilação ARM64. A compilação real deve ser confirmada no job novo.
Os 140 testes JVM e os testes semânticos nativos anteriores continuam separados.

## Limites e continuidade

Compilar objetos fecha uma lacuna de integração, mas não faz link de libe.so,
valida registro JNI em ART, instala aplicativo ou testa jogos. A compilação completa,
integridade/assinatura do APK e desempenho no Edge 30 Ultra continuam pendentes.
Não atribuir ganho de FPS a este incremento. Antes da próxima edição, confirmar
ambos os jobs desse workflow. Se falharem, corrigir primeiro a configuração ou
o erro concreto, sem fazer novas otimizações. Nenhum default de emulação,
scheduler, precisão, áudio, interface ou produção foi alterado.

## CI selector correction

UI #59 configured the Android Release build successfully but stopped before object compilation because the selector required CMAKE_MAKE_PROGRAM:FILEPATH. CMake cache entries can carry another type when supplied by the Android configuration. Read the exact variable with its typed cache syntax instead; require one existing executable and keep object-only targets and failure propagation unchanged. The invocation regression now covers FILEPATH, STRING and UNINITIALIZED. This correction does not establish native compilation success until the next CI completes.

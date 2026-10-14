# Investigação ARM64 — seleção vetorial e evidências externas

Branch: `feature/zephyr-mobile-branding`; base `54c74f0`. Sem APK nesta noite.
Áudio e interface permanecem suspensos. O diagnóstico CPU por thread da base
passou no UI checks #51: 136 testes JVM sem falhas. APK #37 e release ignorados.

## Trabalho redundante identificado

`a64_sequences.cc`, `SELECT_V128_V128`, traduz a seleção por **bit** usada por
`vsel`/`vsel128` PowerPC. O resultado é `(mask & src3) | (~mask & src2)`.
Quando o destino já contém src2 ou src3, o código anterior copiava a máscara para
v0, fazia BSL e copiava o resultado de volta: três instruções, 12 bytes.

- Destino=src2: BIT insere src3 onde a máscara é 1, preservando os outros bits.
- Destino=src3: BIF insere src2 onde a máscara é 0, preservando os outros bits.
- Destino=máscara: BSL continua sendo a escolha, inclusive com fontes repetidas.
- Destino distinto: continua copiando a máscara e fazendo BSL, duas instruções.

Os dois caminhos elegíveis passam a **uma instrução, 4 bytes**, sem scratch v0.
Isso é uma redução local de instruções emitidas; não significa menos 67% de CPU
no jogo, nem comprova ganho de FPS. Não sabemos ainda a frequência dinâmica
dessa operação no Forza Horizon 2 nem o gargalo predominante no Edge 30 Ultra.
Não muda precisão, bits selecionados, registradores alocáveis, ABI, sincronização,
flags de compatibilidade, materialização de constantes ou ordem do guest.

## Regressão usando código de produção

`tools/test-a64-vector-select.py` compila o builder HIR, DCE, alocador e codificador
Xbyak AArch64 reais. Extrai o corpo SELECT diretamente do arquivo de produção;
a interface de operandos é um adaptador de teste, não o backend inteiro.
As instruções ARM64 emitidas são decodificadas e interpretadas por um avaliador
bit a bit independente. Não executa num processador ARM e não mede desempenho.
Logging/profiling são excluídos; assertions permanecem ligadas. Clang é preferido.
O fallback GCC usa `-fpermissive` por uma declaração MMIO não utilizada no teste;
nenhum header de produção é alterado para contornar isso.

O teste cobre todas as 256 combinações de aliases entre quatro registradores,
1.024 padrões por combinação, máscaras zero/um/alternadas/bits isolados, fontes
repetidas, scratch já materializado e registradores altos. Confere tamanho e
opcode emitidos, resultado de 128 bits e preservação dos demais registradores.
Dois fixtures passam pelo DCE/alocador original: destino reutilizando src2 ou
src3. Ambos preservam SELECT e atingem o caminho otimizado. O emitter anterior,
compilado separadamente a partir da base, falha na regressão de uma instrução,
como esperado. A materialização de constantes completa e o backend Android
inteiro continuam dependendo da compilação ARM64 matinal e dos testes no aparelho.

## Hipótese descartada antes de publicação

O alocador genérico consulta `preferred_reg.set` recém-zerado em vez do conjunto
da fonte. Um teste de ASSIGN demonstrou potencial coalescimento, porém o DCE
remove esses ASSIGNs antes da alocação real. Esse exemplo isolado não comprova
menos trabalho no jogo. A alteração experimental foi retirada; o alocador fica
idêntico à base. Qualquer correção futura exige medir efeitos para outros opcodes,
spills e aliases, e não apenas tornar esse primeiro fixture verde.

## Fontes e limites da pesquisa

- [Fonte PowerPC do próprio projeto](../emulator-core/src/main/cpp/xenia/src/xenia/cpu/ppc/ppc_emit_altivec.cc):
  `InstrEmit_vsel_` registra explicitamente seleção por bit e ordem das fontes.
- [Simulador AArch64 do GDB/Linaro, correção BIT/BIF](https://sourceware.org/legacy-ml/gdb-patches/2017-02/msg00110.html):
  operações bitwise e fórmulas de inserção verdadeira/falsa. Não fornece medidas
  de latência nem resultados no nosso aparelho. Encoder Xbyak vendorizado também
  declara e emite BIT/BIF; o teste usa essa implementação, sem atualizar a dependência.
- [XenDroid, GAME_COMPAT do desenvolvedor](https://github.com/rfandango/XenDroid/blob/main/GAME_COMPAT.md):
  configurações por jogo exigem categoria correta; opções de montagem são lidas
  cedo demais para override por jogo. Há resultado de divisão de submissions em
  Forza no Adreno 830, que não valida um valor no Adreno 730. Buffers constantes
  dinâmicos têm problema documentado em drivers proprietários Qualcomm: não foram
  ativados globalmente. Exemplos de outros jogos não autorizam desligar readback
  ou mecanismos de compatibilidade no Forza.
- [Investigação GPU original](https://github.com/rfandango/XenDroid/blob/main/docs/gw-gpu-bottleneck-investigation.md):
  ler conclusões e revisões, especialmente a hipótese de renderArea refutada.
  Geometry Wars/Adreno 650 não é benchmark do nosso Forza/Adreno 730.
- Relatos em fóruns e vídeos encontrados variam build, jogo, driver e aparelho.
  Resultados de Adreno 830/825 ou Snapdragon mais novo não formam um A/B válido.
  Metadados de vídeo não significam que seu conteúdo foi assistido ou medido.
  Buscas por TikTok/Instagram não trouxeram fonte técnica verificável que
  justificasse uma alteração; não houve login, contato ou download de APK alheio.

## Próxima medição

Amanhã: compilar ARM64 somente após CI aprovado, testar o mesmo trecho de Forza,
mesmo driver/configuração/cache aquecido e exportar o relatório de sessão.
CPU por thread deve indicar onde concentrar investigação seguinte; suas janelas
de 5 s não explicam um choque individual. Não concluir ganho/compatibilidade
sem comparação no aparelho. Ainda faltam tempos GPU/espera e perfil dinâmico JIT
para atribuir o gargalo. Evitar identidade falsa de benchmark ou novas flags
agressivas enquanto o caminho crítico não estiver demonstrado.

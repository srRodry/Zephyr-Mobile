# AND/AND_NOT ARM64: máscaras imediatas sem carregar temporário

Branch exclusiva `feature/zephyr-mobile-branding`, base `b34cf39` com UI #54
aprovado. Continuação manual iniciada às 23:45 do dia 13 (UTC−4). Sem APK noturno.

## Problema e mudança

O backend usava materialização em x0 seguida de AND em toda máscara constante
64-bit, mesmo quando o ISA consegue AND imediato. O encoder vendorizado tem
helper `and_imm` de 32 bits, porém não o equivalente de 64 bits. O comentário de
AND_NOT_I64 já descrevia a otimização, mas o código ainda usava MOV+AND.

- AND_I64 com uma fonte constante: emitir AND imediato quando a máscara cabe.
- AND_NOT_I64 com src2 constante: mesma escolha para o complemento de 64 bits.
- AND_NOT_I32 com src2 constante: usar o helper existente somente se o complemento
  cabe. Caso contrário, manter a materialização **original** de src2 e BIC.
- Predicado constexpr separado reconhece uma faixa de bits ligada, rotacionável,
  repetida em elementos de 2/4/8/16/32/64 bits. Zero/all-ones não são codificáveis
  e seguem o caminho original. Predicado 32-bit usa repetição nas duas metades.

No caminho elegível, materialização+operação passam a uma instrução AND. Sem
registrador temporário nem alteração de NZCV; AND não é ANDS. Fallbacks comparados
conservam os mesmos bytes e tamanhos do padrão anterior. Não ampliar essa mudança
para 8/16 bits sem testar a representação dos bits superiores dos operandos.
Nenhuma alteração de alocador, scheduler, precisão floating-point, driver ou flags
agressivas. Dependências não são atualizadas.

Há exemplos reais no frontend, como máscaras de significando/NaN e alinhamento em
`ppc_emit_memory.cc`. Isso demonstra que a operação é utilizada pelo tradutor,
mas não informa sua frequência dinâmica no Forza Horizon 2. Ganho de FPS e
relevância para colisões/NPCs ainda não foram medidos no aparelho. O reconhecedor
roda na compilação JIT; o custo adicional dessa escolha também merece observação.

## Verificação

`tools/test-a64-logical-immediate.py` extrai o helper e os três emitters de
produção; compila com o Xbyak vendorizado. Adaptadores representam operandos de
32/64 bits. Um interpretador independente decodifica o código ARM64 gerado
(AND/BIC/ORR e MOVs), sem executar num processador ARM.

A referência constrói máscaras bit a bit a partir dos campos N/immr/imms no estilo
DecodeBitMasks, diferente do algoritmo de reconhecimento por transições. A
varredura completa encontra 5.334 máscaras distintas de 64 bits e 1.302 de 32;
todas são testadas no encoder real. Mais 200.000 verificações de reconhecedor
comparam valores adicionais com esses conjuntos. 207.880 casos verificam código
emitido, resultados, destino coincidindo com fonte ou distinto, constantes nas
duas posições, casos especiais e preservação dos demais registradores. Fontes
zero/all-ones e lixo nos bits superiores de valores usados como WReg são cobertos.
Os fallbacks e caminhos constantes inalterados são comparados byte a byte com o
padrão antigo; os novos caminhos nunca podem emitir mais bytes que esse padrão.

Assertions permanecem ativas. CI deve executar essa regressão junto das anteriores.
Compilação completa do backend Android e gameplay continuam pendentes para manhã;
este teste de emitter não é benchmark nem prova global de compatibilidade.

Referências: encoder Xbyak vendorizado e frontend do projeto; pseudocódigo ARM
[AND imediato](https://www.scs.stanford.edu/~zyedidia/arm64/and_log_imm.html),
[teste de DecodeBitMasks do projeto Go](https://go.googlesource.com/go/+/7ceb06b2ea957783ab9661f793348b77b8ca5352/src/cmd/internal/obj/arm64/inst_test.go).
Não foi copiada implementação dessas fontes para produção.

## Outras hipóteses revisadas e preservadas

Os loops de espera já têm gate de preempção no caminho comum, parking adaptativo
e restrições para não bloquear produtores. O código registra que aplicar parking
indiscriminadamente já derrubou Forza em teste do projeto; não mudar essa política
por intuição. Memória e sincronização continuam preservadas. O diagnóstico CPU
por thread, de compilação guest e de pipelines ajudará a escolher o próximo alvo.

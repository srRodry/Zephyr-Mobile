# Experimento 01: saltos estruturados de shaders

## Mudança e origem

Transplante isolado do commit
[4f0d10aa](https://github.com/phforner0/Xendroid-Plus/commit/4f0d10aa353e3be729862411df59827889de6cc9).
O patch dos quatro arquivos C++ se aplicou sem alteração. O runner de testes
foi integrado à versão local, sem copiar testes de outros subsistemas.
Os avisos originais foram preservados e a licença AGPL do upstream foi incluída
nos assets, nos créditos e na verificação de empacotamento.

A opção `spirv_structured_forward_jumps` fica **false por padrão**.
Com ela desativada, a análise conserva o caminho anterior. Com ela ativada,
saltos para frente que formam regiões estruturadas são traduzidos em if/else.
Saltos incompatíveis continuam no despacho anterior; shaders com chamadas de
subrotina não entram no experimento. A revisão verificou que as interfaces
`uses_subroutine_calls` e `cf_pair_index_bound` já existem nesta base.

Não foram importadas a otimização de barreiras de memória, a tesselação ou
outras mudanças do upstream. O ganho relatado pelo autor pertence a Halo: Reach
e não demonstra benefício no Forza Horizon ou no Edge 30 Ultra.

## Validação realizada

- Os 15 casos do teste C++ importado passaram localmente.
- O mesmo teste passou com AddressSanitizer e UndefinedBehaviorSanitizer.
  Detecção de vazamentos foi desativada apenas na execução local porque o
  ambiente usa ptrace; o workflow mantém a configuração normal do sanitizer.
- A suíte de lógica nativa passou com o compilador host e SSSE3 habilitado.
  A tentativa inicial sem SSSE3 falhou no código SIMD de áudio existente;
  a repetição usou um wrapper local de compilador, sem alterar flags do APK.
- O teste de APC/threads Android foi ignorado por falta do NDK local.
- Os sete testes Python do comparador passaram, incluindo o novo modo explícito
  `--allow-config-change`. Ele só permite variar o hash de configuração entre
  versões; dentro de cada versão o hash precisa continuar consistente.
- Compilação e execução no Android ainda dependem do resultado do workflow e
  do teste físico. Testes do analisador não validam por si sós o SPIR-V gerado.

## APKs de referência e candidato

O workflow `Zephyr research APK pair` usa:

- Referência fixa: `223522d66aa8f15c6c5dff0fab810c5d6e18e771`.
- Candidato: o commit que dispara o workflow nesta branch.
- Mesmo pacote isolado: `xendroid.compose.zephyr.research`.
- Mesmas versões de NDK, SDK, CMake e flags do pipeline existente.
- Compilação sequencial no mesmo job. Usa a chave de teste ou de release
  configurada; sem essas chaves, gera uma única chave temporária para o par,
  mantém o segredo apenas no runner e apaga a chave ao final. APKs do mesmo
  par podem substituir um ao outro; pares de execuções diferentes não terão
  necessariamente a mesma assinatura.
- APK, comprovante de assinatura e manifesto JSON com commit e SHA-256.

Não publica release nem modifica o app regular. A execução em push exige
`[zephyr-apk]` no commit; mudanças rotineiras não recompilam os dois APKs.
O pipeline de referência utiliza o verificador de APK da referência; o
candidato exige também o texto da licença adicionado neste experimento.

## Teste físico, em duas etapas

1. **Regressão da integração:** referência e candidato com a opção desligada,
   mesma configuração e três rodadas por versão. Confirmar visual, saves,
   estabilidade e tempos de quadro. Se o candidato já apresentar regressão,
   interromper o experimento antes de ativar a opção.
2. **Efeito da opção:** no candidato, comparar false e true, três rodadas por
   condição, intercaladas. Usar `--allow-config-change` e registrar a diferença
   exata dos arquivos TOML junto ao relatório. Nenhuma outra flag deve mudar.

Trecho individual, na configuração do jogo:

```toml
[GPU]
spirv_structured_forward_jumps = true
```

Para a referência, usar false. A opção atua em shaders traduzidos depois da
mudança. Não reutilizar cegamente caches traduzidos na condição anterior:
utilizar o gerenciamento de cache do app, preservar backups, preparar uma
condição por vez e aquecer o cache dessa condição antes de medir.
Não limpar dados do aplicativo nem apagar saves para trocar a opção.

Manter geração de quadros desligada e identificar o jogo pelo Title ID/hash.
Começar pelo Forza Horizon, já usado nos testes do usuário; considerar Halo:
Reach apenas se houver cópia e cena reproduzível disponíveis. Uma sessão de
20–30 minutos verifica sustentação térmica, mas não substitui os A/B curtos.

O comparador permite o mesmo commit nos dois braços para testar configurações.
Se o hash de configuração variar, o relatório declara a diferença; não a oculta
como ganho exclusivamente de código. Inspecionar manualmente temperaturas,
logs, screenshots e velocidade real do jogo.

## Correção do primeiro workflow

A execução 43641755558 parou na verificação de assinatura, antes de compilar:
nenhuma chave de teste/release está configurada. O pipeline foi convertido de
matriz paralela para dois builds sequenciais que compartilham uma chave de teste
quando necessário. A chave privada não é publicada em artifacts ou no código.

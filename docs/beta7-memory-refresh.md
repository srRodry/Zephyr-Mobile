# Beta 7 — atualização seletiva da memória gráfica

## Evidência disponível

Relatório do Edge 30 Ultra, build d706eafc, Title ID 454107F8, sessão de
15/11/2026: 3.525 segundos amostrados, mediana de 15 FPS, 1.303 pipelines,
104.802 ms acumulados de criação de pipelines (tempo de trabalho, não tempo
necessariamente bloqueado), 39.322/650.003 blocos de áudio compensados (6,0%),
bateria até 45 °C e eventos de limitação térmica. Encerramento pelo usuário,
sem backtrace nativo. O driver se identifica como Turnip Adreno 725 apesar do
SM8475; essa string isolada não demonstra qual configuração de GPU foi usada.
O relatório antecede a beta 6 e não comprova regressão ou ganho nela.

## Mudança

A atualização dos flags de memória compartilhada usava chunks de 64 entradas.
Uma única entrada alterada fazia ler 64; alterações esparsas em mais de 16
chunks faziam percorrer todas as 2.048 entradas em páginas de 4 KiB.

Um segundo bitmap de 256 bytes identifica cada entrada modificada. O primeiro
bitmap continua identificando os chunks. Chunks com mais de 32 entradas sujas
mantêm um percurso linear. Uma alteração localizada agora visita uma entrada;
uma entrada em cada um dos 32 chunks visita 32, em vez de 2.048. Isso reduz
98,4375% das entradas visitadas nesses casos específicos, não o custo total
da emulação nem necessariamente o tempo total desse método.

Escritas da GPU deixam os bits afetados válidos nos dois masks. Portanto não
introduzem bits `valid & ~gpu_written`, que é o trabalho consumido pelo refresh.
Elas deixam de marcar trabalho redundante; bits sujos de uploads anteriores da
CPU continuam pendentes. Invalidações da CPU continuam marcadas, todas as
mutações continuam protegidas pela mesma região crítica e o reset inicializa
os dois níveis do bitmap. A correção de uploads da CPU da beta 5 permanece.

## Verificação e limites

`python tools/test-shared-page-refresh.py` executa os métodos de produção
contra uma referência que percorre todas as páginas. Cobre 60.000 operações
mistas, páginas de 4/16/64 KiB, reset com alterações pendentes, uploads densos,
fronteiras de chunks, sobrescrita CPU/GPU e atualização vazia. Conta leituras
no método real para verificar uma entrada no caso localizado, 32 no caso
esparso de 4 KiB e zero após escrita somente da GPU. UBSan habilitado.

A compilação Android e os testes de política dedicada e de quirk de Army of Two
complementam o teste. Não há medição de FPS desta beta no telefone. Não se
atribui a esta mudança a solução das texturas coloridas nem dos travamentos:
ela reduz trabalho de CPU com a mesma semântica de coerência. O gargalo de
compilação e a limitação térmica do relatório continuam exigindo medição no
aparelho; nenhum mecanismo de sincronização foi removido.

Mesmo pacote Research e assinatura permanente; publicação pelo workflow
existente com VersionCode crescente e atualização pelo canal Preview.

## Publicação verificada

Beta 7, build 20, VersionCode 100020; workflow 43778429093 concluído com
sucesso. Compilação Android, testes nativos e testes selecionados do aplicativo
(incluindo atualizador), assinatura e empacotamento aprovados.

Release: https://github.com/srRodry/Zephyr-Mobile/releases/tag/Zephyr-Research-v100020-30bdfbf4

APK: `Zephyr-Performance_beta7-memory_30bdfbf4.apk`, 52.507.672 bytes.
SHA-256: `5bcad2aa604b38701cc1ff4673f6bc5408d66cdd2fcb76b6bedf2a6b1d80d3c6`.
Certificado Research permanente confirmado:
`22e1b7cd028e034eb6e483a40f96927fd91fab8601bf50aeb87798c64d053353`.

A execução do teste novo sobre os métodos anteriores também confirmou o
percurso redundante: falha na asserção de uma leitura por alteração localizada.

# Electron Totem

Electron separado para o totem de autoatendimento.

Ele carrega a rota `/autoatendimento`, roda em fullscreen por padrao, injeta um teclado virtual para telas touch e expoe uma ponte TEF mockada em `window.totemAPI`.

## Rodar em desenvolvimento

1. Suba o frontend em outra janela:

```bash
cd frontend
npm start
```

2. Rode o Electron do totem:

```bash
cd frontend/electron-totem
npm run dev
```

Por padrao, o totem abre `http://localhost:4200/autoatendimento`.

Para usar outro endereco:

```bash
set TOTEM_URL=http://localhost:8080/autoatendimento
npm run dev
```

## Variaveis uteis

- `TOTEM_URL`: URL exata carregada pelo totem.
- `TOTEM_FULLSCREEN_ENABLED`: `true` ou `false`.
- `TOTEM_KIOSK_ENABLED`: `true` trava mais o app para uso real.
- `TOTEM_MOBILE_EMULATION_ENABLED`: aplica user-agent mobile, bloqueia selecao de texto e permite arrastar para rolar com mouse.
- `TOTEM_VIRTUAL_KEYBOARD_ENABLED`: liga/desliga teclado virtual.

## TEF (cartao presencial)

A ponte TEF fica em `window.totemAPI` (preload) e delega para um driver plugavel
resolvido no processo main (`tef/index.js`).

Selecao do driver:

- `TOTEM_TEF_DRIVER`: `mock` (default), `paygo` ou `auttar`.

### Driver mock (dev/CI)

- `TOTEM_TEF_MOCK_RESULT`: `APROVADO` (default), `NEGADO`, `TIMEOUT` ou `ERRO`.
- `TOTEM_TEF_MOCK_DELAY_MS`: atraso simulado (default 1200).
- `TOTEM_TEF_MOCK_BANDEIRA` / `TOTEM_TEF_MOCK_ULTIMOS_DIGITOS`: dados do cartao simulado.
- Retrocompatibilidade: sem `TOTEM_TEF_DRIVER`, `TOTEM_TEF_MOCK_ENABLED=false`
  responde `NAO_CONFIGURADO` (comportamento antigo).

### Driver paygo (teste/homologacao com PinPad)

Esqueleto pronto aguardando o spike do PayGo Web (mecanismo de comunicacao local).
Sem endpoint/cliente configurado, responde `NAO_CONFIGURADO`.

- `TOTEM_TEF_PAYGO_ENDPOINT`: endereco do intermediador PayGo local (obrigatoria).
- `TOTEM_TEF_PAYGO_PONTO_CAPTURA`: identificacao do ponto de captura.
- `TOTEM_TEF_PAYGO_TIMEOUT_MS`: timeout da transacao (default 90000).
- `TOTEM_TEF_PAYGO_ADQUIRENTE`: adquirente configurada no PayGo (ex.: `GETNET`).

### Driver auttar (producao Getnet)

Caminho oficial do TEF Getnet. Integra pelo **WebSocket do CTFClient** (`tipointegracao=websocket`
em `configCTFClient.xml`) — JSON puro, sem FFI, sem sidecar e sem troca de arquivos. Usa o
`WebSocket` global do Node 22, entao nao ha dependencia nova.

Fluxo de pagamento: envia a operacao do meio escolhido, e — so quando o CTF aprova (`retorno = 0`) —
envia a **confirmacao** (operacao 6). Se a confirmacao falhar, ou se a transacao cair por timeout ou
erro de comunicacao, dispara o **desfazimento total** (operacao 191), que derruba as transacoes nao
confirmadas da fase de recebimento.

Operacoes usadas (Guia Rapido de Integracao WebSocket v01.17):

| Meio | Operacao |
| --- | --- |
| `CARTAO_CREDITO` | 112 (credito a vista) |
| `CARTAO_DEBITO` | 101 |
| `CARTAO_VOUCHER` | 106 |
| `PIX` | 422 |

- `TOTEM_TEF_AUTTAR_URL`: WebSocket do CTFClient, ex.: `ws://127.0.0.1:2500` (obrigatoria).
- `TOTEM_TEF_AUTTAR_TIMEOUT_MS`: timeout da transacao (default 90000).
- `TOTEM_TEF_AUTTAR_VERSAO_AC`: versao da automacao enviada em cada requisicao (opcional).

Sem a URL, responde `NAO_CONFIGURADO`.

> **Antes do primeiro teste com o CTFClient real:** o pacote portable nao vem com credencial.
> `estabelecimento`, `loja` e `terminal` no `configCTFClient.xml`, mais o login/senha/CNPJ da
> operacao 801 (ou o codigo de ativacao Multi-EC), precisam vir da Auttar. Homologacao aponta para
> `201.87.167.97:1996` com `homologacao=true`, e o terminal ainda precisa ser ativado no Portal
> Auttar (TEF / Cadastro / Terminal Loja).

### CTFClient falso (dev sem kit/credencial)

```bash
node scripts/fake-ctfclient.js --porta 2500 --resultado APROVADO
```

`--resultado` aceita `APROVADO`, `NEGADO`, `CANCELADO` ou `TIMEOUT` (nao responde); `--atraso <ms>`
simula a demora do PinPad. Em outro terminal, rode o totem com `TOTEM_TEF_DRIVER=auttar` e
`TOTEM_TEF_AUTTAR_URL=ws://127.0.0.1:2500`.

### Confirmacoes pendentes (venda aprovada que o backend nao gravou)

Entre a aprovacao no CTF e o POST `confirmarCartao` o dinheiro ja saiu do cartao e o backend ainda
nao sabe de nada. Se o totem cair nesse intervalo, a venda ficaria capturada e sem registro — passou
do ponto em que o desfazimento (191) resolve.

Por isso o processo principal grava a confirmacao em
`<userData>/tef-confirmacoes-pendentes/<correlationId>.json` assim que a transacao e aprovada, e so
apaga quando o renderer avisa que o backend gravou. O arquivo tem duas partes:

```json
{
  "confirmacao": { "correlationId": "...", "aprovado": true, "nsuTef": "...", "comprovanteCliente": "..." },
  "estorno": { "correlationId": "...", "nsu": "...", "dataTransacao": "AAMMDD", "valorCentavos": 4579 }
}
```

`confirmacao` e exatamente o corpo do POST `confirmarCartao`. `estorno` sao os campos que a
operacao 128 exige — o unico caminho de volta depois que o dinheiro foi capturado.

Na abertura seguinte o totem reenvia o que sobrou; o endpoint de confirmacao e idempotente por
`correlationId`, entao reenvio repetido nao cobra de novo. Resposta 5xx ou rede fora mantem o
arquivo para a proxima abertura. Resposta 4xx e recusa definitiva: o renderer chama
`cancelarPagamentoTef` com o bloco `estorno` (operacao 128 na adquirente) e so entao baixa a
pendencia. Vale para o reenvio na abertura e para a venda ao vivo — os dois passam pelo mesmo
`confirmarComEstorno`.

Nao existe opcode de abortar transacao em curso no protocolo WebSocket: so 128 (cancelamento de
venda ja capturada) e 191 (desfazimento, antes da confirmacao). Por isso a tela `CARTAO_PROCESSANDO`
nao tem botao de cancelar — quem cancela e o cliente na propria PinPad (retorno 6) ou o timeout do
driver.

### Comprovante (via do cliente)

O CTF devolve `cupomCliente` / `cupomEstabelecimento` / `cupomReduzido` como listas de `{ "linha" }`.
O driver junta `cupomCliente` (com fallback para `cupomReduzido`) em `comprovanteCliente`, que segue
para o backend e aparece na tela de sucesso do totem. So a via do cliente e capturada: o totem nao
tem impressora nem operador para a via do estabelecimento.

### PinPad recomendado

Gertec PPC930 (padrao ABECS, USB) — serve tanto ao PayGo quanto ao Auttar/Getnet.
Alternativas: Ingenico Lane 3000, Verifone P200.

### Teste rapido no console

No console da tela do totem:

```js
await window.totemAPI.iniciarPagamentoTef({
  correlationId: 'teste-1',
  valorCentavos: 2500,
  meio: 'CARTAO_CREDITO'
});
```

### Testes automatizados

```bash
npm test        # unit tests dos drivers (node --test, sem hardware)
npm run check   # verificacao de sintaxe
```

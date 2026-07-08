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

### Driver auttar (producao Getnet — Fase 0)

Caminho oficial do TEF Getnet (proposta WSGE + kit CTFClient da Auttar). Ainda nao
implementado: responde `NAO_CONFIGURADO` ate a Fase 0 da spec
`docs/superpowers/specs/2026-07-08-totem-tef-driver-plugavel-getnet-design.md`.

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

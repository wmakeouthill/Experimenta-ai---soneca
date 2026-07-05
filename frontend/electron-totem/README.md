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
- `TOTEM_TEF_MOCK_ENABLED`: mantem TEF fake para desenvolvimento.
- `TOTEM_TEF_MOCK_RESULT`: `APROVADO`, `NEGADO`, `TIMEOUT` ou `ERRO`.

## Ponte TEF mock

No console da tela:

```js
await window.totemAPI.iniciarPagamentoTef({
  correlationId: 'teste-1',
  valorCentavos: 2500,
  meio: 'CARTAO_CREDITO'
});
```

Essa ponte ainda nao chama Stone/AutoTEF real. Ela existe para validar o fluxo local do totem antes das credenciais Stone.

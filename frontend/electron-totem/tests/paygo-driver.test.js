'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { createPayGoDriver } = require('../tef/drivers/paygo-driver');

const ENV_OK = {
  TOTEM_TEF_PAYGO_ENDPOINT: '127.0.0.1:60906',
  TOTEM_TEF_PAYGO_PONTO_CAPTURA: 'TOTEM-01',
  TOTEM_TEF_PAYGO_TIMEOUT_MS: '5000',
  TOTEM_TEF_PAYGO_ADQUIRENTE: 'GETNET',
};

const PAYLOAD = { correlationId: 'corr-7', valorCentavos: 3500, meio: 'CARTAO_DEBITO' };

function clientStub(resposta) {
  const chamadas = [];
  return {
    chamadas,
    iniciarTransacao(args) {
      chamadas.push({ operacao: 'iniciar', args });
      return Promise.resolve(resposta);
    },
    cancelarTransacao(args) {
      chamadas.push({ operacao: 'cancelar', args });
      return Promise.resolve(resposta);
    },
  };
}

test('paygo sem endpoint responde NAO_CONFIGURADO', async () => {
  const driver = createPayGoDriver({ env: {}, client: clientStub({}) });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'NAO_CONFIGURADO');
  assert.equal(resultado.sucesso, false);
});

test('paygo sem client responde NAO_CONFIGURADO', async () => {
  const driver = createPayGoDriver({ env: ENV_OK, client: null });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'NAO_CONFIGURADO');
});

test('paygo valida valor antes de chamar o cliente', async () => {
  const stub = clientStub({ resultado: 'APROVADO' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento({ correlationId: 'x', valorCentavos: -1 });
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.mensagem, 'Valor invalido para pagamento TEF.');
  assert.equal(stub.chamadas.length, 0);
});

test('paygo traduz aprovacao e repassa config ao cliente', async () => {
  const stub = clientStub({
    resultado: 'APROVADO',
    nsu: 'NSU77',
    bandeira: 'MASTERCARD',
    autorizacao: 'AUT77',
    ultimosDigitos: '5678',
    codigoResposta: '00',
    mensagem: 'Aprovado',
  });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);

  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'APROVADO');
  assert.equal(resultado.nsu, 'NSU77');
  assert.equal(resultado.bandeira, 'MASTERCARD');
  assert.equal(resultado.autorizacao, 'AUT77');
  assert.equal(resultado.ultimosDigitos, '5678');
  assert.equal(resultado.adquirente, 'GETNET');
  assert.equal(resultado.correlationId, 'corr-7');
  assert.equal(resultado.valorCentavos, 3500);

  assert.equal(stub.chamadas.length, 1);
  assert.deepEqual(stub.chamadas[0].args, {
    correlationId: 'corr-7',
    valorCentavos: 3500,
    meio: 'CARTAO_DEBITO',
    pontoCaptura: 'TOTEM-01',
    timeoutMs: 5000,
  });
});

test('paygo usa adquirente default PAYGO quando env nao define', async () => {
  const env = { ...ENV_OK };
  delete env.TOTEM_TEF_PAYGO_ADQUIRENTE;
  const stub = clientStub({
    resultado: 'APROVADO',
    nsu: 'N',
    bandeira: 'B',
    autorizacao: 'A',
  });
  const driver = createPayGoDriver({ env, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.adquirente, 'PAYGO');
});

test('paygo traduz negacao', async () => {
  const stub = clientStub({ resultado: 'NEGADO', codigoResposta: '51', mensagem: 'Saldo insuficiente' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'NEGADO');
  assert.equal(resultado.codigoResposta, '51');
});

test('paygo traduz timeout', async () => {
  const stub = clientStub({ resultado: 'TIMEOUT' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'TIMEOUT');
});

test('paygo traduz cancelamento pelo operador em iniciar', async () => {
  const stub = clientStub({ resultado: 'CANCELADO', mensagem: 'Operacao cancelada' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'CANCELADO');
});

test('paygo devolve ERRO quando cliente lanca excecao', async () => {
  const driver = createPayGoDriver({
    env: ENV_OK,
    client: {
      iniciarTransacao() {
        return Promise.reject(new Error('conexao recusada'));
      },
      cancelarTransacao() {
        return Promise.reject(new Error('conexao recusada'));
      },
    },
  });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'ERRO');
  assert.ok(resultado.mensagem.includes('conexao recusada'));
});

test('paygo devolve ERRO para resposta invalida do cliente', async () => {
  const stub = clientStub(null);
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'ERRO');
});

test('paygo devolve ERRO para resultado desconhecido do cliente', async () => {
  const stub = clientStub({ resultado: 'COISA_ESTRANHA' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.status, 'ERRO');
});

test('paygo cancelar traduz CANCELADO como sucesso', async () => {
  const stub = clientStub({ resultado: 'CANCELADO', mensagem: 'Cancelado no TEF' });
  const driver = createPayGoDriver({ env: ENV_OK, client: stub });
  const resultado = await driver.cancelarPagamento({ correlationId: 'corr-7' });
  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'CANCELADO');
  assert.equal(stub.chamadas[0].operacao, 'cancelar');
});

test('paygo expoe nome', () => {
  assert.equal(createPayGoDriver({ env: {} }).nome, 'paygo');
});

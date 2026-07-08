'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { createMockDriver } = require('../tef/drivers/mock-driver');

const ENV_RAPIDO = { TOTEM_TEF_MOCK_DELAY_MS: '1' };
const PAYLOAD = { correlationId: 'corr-1', valorCentavos: 2500, meio: 'CARTAO_CREDITO' };

test('mock aprova por default com nsu, bandeira e autorizacao', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'APROVADO');
  assert.equal(resultado.correlationId, 'corr-1');
  assert.equal(resultado.valorCentavos, 2500);
  assert.ok(resultado.nsu.startsWith('NSU'));
  assert.ok(resultado.autorizacao.startsWith('AUT'));
  assert.equal(resultado.bandeira, 'VISA');
  assert.equal(resultado.ultimosDigitos, '1234');
  assert.equal(resultado.adquirente, 'STONE_TEF_MOCK');
});

test('mock respeita TOTEM_TEF_MOCK_BANDEIRA e ULTIMOS_DIGITOS', async () => {
  const driver = createMockDriver({
    ...ENV_RAPIDO,
    TOTEM_TEF_MOCK_BANDEIRA: 'ELO',
    TOTEM_TEF_MOCK_ULTIMOS_DIGITOS: '9999',
  });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.bandeira, 'ELO');
  assert.equal(resultado.ultimosDigitos, '9999');
});

test('mock nega quando TOTEM_TEF_MOCK_RESULT=NEGADO', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO, TOTEM_TEF_MOCK_RESULT: 'NEGADO' });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'NEGADO');
  assert.equal(resultado.codigoResposta, '51');
});

test('mock devolve TIMEOUT quando TOTEM_TEF_MOCK_RESULT=TIMEOUT', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO, TOTEM_TEF_MOCK_RESULT: 'TIMEOUT' });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'TIMEOUT');
  assert.equal(resultado.codigoResposta, 'TIMEOUT');
});

test('mock devolve ERRO quando TOTEM_TEF_MOCK_RESULT=ERRO', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO, TOTEM_TEF_MOCK_RESULT: 'ERRO' });
  const resultado = await driver.iniciarPagamento(PAYLOAD);
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.codigoResposta, 'ERRO_TECNICO');
});

test('mock devolve ERRO para valor invalido', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO });
  const resultado = await driver.iniciarPagamento({ correlationId: 'x', valorCentavos: 0 });
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.mensagem, 'Valor invalido para pagamento TEF.');
});

test('mock gera correlationId quando payload nao informa', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO });
  const resultado = await driver.iniciarPagamento({ valorCentavos: 100 });
  assert.ok(resultado.correlationId);
  assert.equal(typeof resultado.correlationId, 'string');
});

test('mock cancelar devolve CANCELADO com sucesso e ecoa correlationId', async () => {
  const driver = createMockDriver({ ...ENV_RAPIDO });
  const resultado = await driver.cancelarPagamento({ correlationId: 'corr-9' });
  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'CANCELADO');
  assert.equal(resultado.correlationId, 'corr-9');
});

test('mock expoe nome', () => {
  assert.equal(createMockDriver({}).nome, 'mock');
});

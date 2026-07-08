'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { resolveTefDriver } = require('../tef');
const { createAuttarDriver, MENSAGEM_PENDENCIA_AUTTAR } = require('../tef/drivers/auttar-driver');

const MENSAGEM_LEGADO = 'TEF real ainda nao foi configurado neste Electron do totem.';

test('auttar responde NAO_CONFIGURADO em iniciar e cancelar', async () => {
  const driver = createAuttarDriver();
  assert.equal(driver.nome, 'auttar');

  const iniciar = await driver.iniciarPagamento({ correlationId: 'x', valorCentavos: 100 });
  assert.equal(iniciar.status, 'NAO_CONFIGURADO');
  assert.equal(iniciar.mensagem, MENSAGEM_PENDENCIA_AUTTAR);

  const cancelar = await driver.cancelarPagamento({ correlationId: 'x' });
  assert.equal(cancelar.status, 'NAO_CONFIGURADO');
});

test('sem env nenhuma, resolve para mock (comportamento atual)', () => {
  assert.equal(resolveTefDriver({}).nome, 'mock');
});

test('TOTEM_TEF_DRIVER=mock resolve para mock', () => {
  assert.equal(resolveTefDriver({ TOTEM_TEF_DRIVER: 'mock' }).nome, 'mock');
});

test('TOTEM_TEF_DRIVER=paygo resolve para paygo', () => {
  assert.equal(resolveTefDriver({ TOTEM_TEF_DRIVER: 'paygo' }).nome, 'paygo');
});

test('TOTEM_TEF_DRIVER=auttar resolve para auttar', () => {
  assert.equal(resolveTefDriver({ TOTEM_TEF_DRIVER: 'auttar' }).nome, 'auttar');
});

test('TOTEM_TEF_DRIVER e case-insensitive e tolera espacos', () => {
  assert.equal(resolveTefDriver({ TOTEM_TEF_DRIVER: '  PayGo ' }).nome, 'paygo');
});

test('driver desconhecido responde NAO_CONFIGURADO com orientacao', async () => {
  const driver = resolveTefDriver({ TOTEM_TEF_DRIVER: 'stone' });
  assert.equal(driver.nome, 'nao-configurado');
  const resultado = await driver.iniciarPagamento({ valorCentavos: 100 });
  assert.equal(resultado.status, 'NAO_CONFIGURADO');
  assert.ok(resultado.mensagem.includes('stone'));
  assert.ok(resultado.mensagem.includes('mock, paygo ou auttar'));
});

test('retrocompat: TOTEM_TEF_MOCK_ENABLED=false sem driver responde mensagem legado', async () => {
  const driver = resolveTefDriver({ TOTEM_TEF_MOCK_ENABLED: 'false' });
  assert.equal(driver.nome, 'nao-configurado');
  const resultado = await driver.iniciarPagamento({ valorCentavos: 100 });
  assert.equal(resultado.status, 'NAO_CONFIGURADO');
  assert.equal(resultado.mensagem, MENSAGEM_LEGADO);
});

test('retrocompat: TOTEM_TEF_MOCK_ENABLED=true sem driver resolve para mock', () => {
  assert.equal(resolveTefDriver({ TOTEM_TEF_MOCK_ENABLED: 'true' }).nome, 'mock');
});

test('retrocompat: valores sim/1/on habilitam mock', () => {
  for (const valor of ['sim', '1', 'on', 'yes']) {
    assert.equal(resolveTefDriver({ TOTEM_TEF_MOCK_ENABLED: valor }).nome, 'mock');
  }
});

test('TOTEM_TEF_DRIVER explicito vence TOTEM_TEF_MOCK_ENABLED=false', () => {
  const driver = resolveTefDriver({
    TOTEM_TEF_DRIVER: 'mock',
    TOTEM_TEF_MOCK_ENABLED: 'false',
  });
  assert.equal(driver.nome, 'mock');
});

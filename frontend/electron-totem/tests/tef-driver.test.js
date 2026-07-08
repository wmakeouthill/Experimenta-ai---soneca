'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const {
  STATUS_TEF,
  normalizarResultadoTef,
  criarResultadoErro,
  criarResultadoNaoConfigurado,
} = require('../tef/tef-driver');

test('STATUS_TEF contem exatamente os status do contrato', () => {
  assert.deepEqual(
    [...STATUS_TEF].sort(),
    ['APROVADO', 'CANCELADO', 'ERRO', 'NAO_CONFIGURADO', 'NEGADO', 'TIMEOUT'].sort()
  );
});

test('normalizarResultadoTef aprova iniciar com campos obrigatorios', () => {
  const resultado = normalizarResultadoTef(
    { status: 'APROVADO', nsu: 'NSU1', bandeira: 'VISA', autorizacao: 'AUT1' },
    { operacao: 'iniciar' }
  );
  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'APROVADO');
  assert.ok(resultado.dataHora);
});

test('normalizarResultadoTef vira ERRO quando aprovado sem nsu/bandeira/autorizacao', () => {
  const resultado = normalizarResultadoTef({ status: 'APROVADO' }, { operacao: 'iniciar' });
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'ERRO');
});

test('normalizarResultadoTef forca sucesso=false para status nao aprovado em iniciar', () => {
  const resultado = normalizarResultadoTef(
    { status: 'NEGADO', sucesso: true },
    { operacao: 'iniciar' }
  );
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'NEGADO');
});

test('normalizarResultadoTef trata status desconhecido como ERRO', () => {
  const resultado = normalizarResultadoTef({ status: 'QUALQUER' }, { operacao: 'iniciar' });
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.sucesso, false);
});

test('normalizarResultadoTef em cancelar considera CANCELADO como sucesso', () => {
  const resultado = normalizarResultadoTef({ status: 'CANCELADO' }, { operacao: 'cancelar' });
  assert.equal(resultado.sucesso, true);
  assert.equal(resultado.status, 'CANCELADO');
});

test('normalizarResultadoTef em cancelar nao considera APROVADO como sucesso', () => {
  const resultado = normalizarResultadoTef(
    { status: 'APROVADO', nsu: 'N', bandeira: 'B', autorizacao: 'A' },
    { operacao: 'cancelar' }
  );
  assert.equal(resultado.sucesso, false);
});

test('normalizarResultadoTef rejeita resposta nao-objeto', () => {
  const resultado = normalizarResultadoTef(undefined, { operacao: 'iniciar' });
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.sucesso, false);
});

test('normalizarResultadoTef preserva campos informativos', () => {
  const resultado = normalizarResultadoTef(
    {
      status: 'APROVADO',
      nsu: 'NSU9',
      bandeira: 'MASTERCARD',
      autorizacao: 'AUT9',
      correlationId: 'abc-123',
      adquirente: 'PAYGO',
      ultimosDigitos: '4321',
      valorCentavos: 2500,
      codigoResposta: '00',
      mensagem: 'ok',
    },
    { operacao: 'iniciar' }
  );
  assert.equal(resultado.correlationId, 'abc-123');
  assert.equal(resultado.adquirente, 'PAYGO');
  assert.equal(resultado.ultimosDigitos, '4321');
  assert.equal(resultado.valorCentavos, 2500);
  assert.equal(resultado.codigoResposta, '00');
});

test('criarResultadoNaoConfigurado devolve shape seguro', () => {
  const resultado = criarResultadoNaoConfigurado('msg de pendencia');
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.status, 'NAO_CONFIGURADO');
  assert.equal(resultado.mensagem, 'msg de pendencia');
  assert.ok(resultado.dataHora);
});

test('criarResultadoErro devolve ERRO com mensagem', () => {
  const resultado = criarResultadoErro('falhou');
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.sucesso, false);
  assert.equal(resultado.codigoResposta, 'ERRO_TECNICO');
  assert.equal(resultado.mensagem, 'falhou');
});

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { criarStoreConfirmacoes } = require('../tef/confirmacoes-pendentes');

function novoStore() {
  const diretorio = fs.mkdtempSync(path.join(os.tmpdir(), 'tef-pendentes-'));
  return { diretorio, store: criarStoreConfirmacoes({ diretorio }) };
}

const APROVADO = Object.freeze({
  sucesso: true,
  status: 'APROVADO',
  correlationId: 'a1b2c3d4-0000-4000-8000-000000000001',
  nsu: '37384',
  bandeira: 'MASTERCARD',
  autorizacao: '006299',
  adquirente: 'GETNET_AUTTAR',
  mensagem: 'TRANSAC APROVADA',
  comprovanteCliente: 'REDE GETNET\nCARTAO ************6479',
  dataTransacao: '220615',
  valorCentavos: 4579,
});

test('venda aprovada fica pendente ate o backend registrar', () => {
  const { store } = novoStore();

  store.registrar(APROVADO);

  assert.deepEqual(store.listar(), [
    {
      confirmacao: {
        correlationId: APROVADO.correlationId,
        aprovado: true,
        nsuTef: '37384',
        bandeira: 'MASTERCARD',
        codigoAutorizacao: '006299',
        codigoAdquirente: 'GETNET_AUTTAR',
        comprovanteCliente: 'REDE GETNET\nCARTAO ************6479',
      },
      estorno: {
        correlationId: APROVADO.correlationId,
        nsu: '37384',
        dataTransacao: '220615',
        valorCentavos: 4579,
      },
    },
  ]);

  store.concluir(APROVADO.correlationId);
  assert.deepEqual(store.listar(), []);
});

/** O comprovante e a via do cliente, nao a linha de display do PinPad. */
test('pendencia guarda a via do cliente, nao a mensagem de display', () => {
  const { store } = novoStore();

  store.registrar(APROVADO);

  const [pendente] = store.listar();
  assert.match(pendente.confirmacao.comprovanteCliente, /REDE GETNET/);
  assert.doesNotMatch(pendente.confirmacao.comprovanteCliente, /TRANSAC APROVADA/);
});

test('venda nao aprovada nao vira pendencia', () => {
  const { store } = novoStore();

  store.registrar({ ...APROVADO, sucesso: false, status: 'NEGADO' });

  assert.deepEqual(store.listar(), []);
});

test('correlationId com travessia de caminho nao vira arquivo', () => {
  const { store, diretorio } = novoStore();

  store.registrar({ ...APROVADO, correlationId: '../fora' });
  store.concluir('../fora');

  assert.deepEqual(store.listar(), []);
  assert.equal(fs.existsSync(path.join(diretorio, '..', 'fora.json')), false);
});

test('listar ignora arquivo corrompido em vez de derrubar a abertura do totem', () => {
  const { store, diretorio } = novoStore();
  store.registrar(APROVADO);
  fs.writeFileSync(path.join(diretorio, 'lixo.json'), '{ nao e json');

  assert.equal(store.listar().length, 1);
});

test('diretorio inexistente lista vazio', () => {
  const store = criarStoreConfirmacoes({
    diretorio: path.join(os.tmpdir(), 'tef-pendentes-que-nunca-existiu'),
  });

  assert.deepEqual(store.listar(), []);
});

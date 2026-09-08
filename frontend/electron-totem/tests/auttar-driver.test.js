'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { createAuttarDriver } = require('../tef/drivers/auttar-driver');
const { iniciarFakeCtfClient } = require('../scripts/fake-ctfclient');

const PAGAMENTO = { correlationId: 'ped-1', valorCentavos: 2500, meio: 'CARTAO_CREDITO' };

async function criarAmbiente({ timeoutMs = 5000, ...opcoesFake } = {}) {
  const fake = iniciarFakeCtfClient({ onErro: () => {}, ...opcoesFake });
  const url = await fake.url();
  const driver = createAuttarDriver({
    env: { TOTEM_TEF_AUTTAR_URL: url, TOTEM_TEF_AUTTAR_TIMEOUT_MS: String(timeoutMs) },
  });
  return { fake, driver, url };
}

test('credito aprovado devolve APROVADO e confirma no CTF', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.equal(resultado.status, 'APROVADO');
    assert.equal(resultado.sucesso, true);
    assert.equal(resultado.correlationId, 'ped-1');
    assert.equal(resultado.bandeira, 'MASTERCARD');
    assert.equal(resultado.adquirente, 'GETNET');
    assert.equal(resultado.ultimosDigitos, '6479');
    assert.ok(resultado.nsu);
    assert.ok(resultado.autorizacao);

    const [requisicao, confirmacao] = fake.recebidas;
    assert.equal(requisicao.operacao, '112');
    assert.equal(requisicao.valorTransacao, '2500');
    assert.equal(requisicao.documento, 'ped-1');
    assert.equal(confirmacao.operacao, '6');
    assert.equal(confirmacao.numeroTransacao, '1');
    assert.equal(fake.recebidas.length, 2);
  } finally {
    await fake.parar();
  }
});

/** A adquirente exige a via do cliente de toda transacao aprovada. */
test('resultado aprovado carrega a via do cliente, nao a linha de display', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.match(resultado.comprovanteCliente, /REDE GETNET/);
    assert.match(resultado.comprovanteCliente, /CREDITO A VISTA/);
    assert.equal(resultado.mensagem, 'TRANSAC APROVADA');
    assert.ok(resultado.dataTransacao, 'estorno pela 128 depende da dataTransacao');
  } finally {
    await fake.parar();
  }
});

test('nao vaza o PAN: guarda apenas os quatro ultimos digitos', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);
    assert.equal(JSON.stringify(resultado).includes('515590'), false);
  } finally {
    await fake.parar();
  }
});

test('pix usa a operacao 422 e aprova sem cartao', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.iniciarPagamento({ ...PAGAMENTO, meio: 'PIX' });

    assert.equal(resultado.status, 'APROVADO');
    assert.equal(resultado.ultimosDigitos, undefined);
    assert.equal(fake.recebidas[0].operacao, '422');
  } finally {
    await fake.parar();
  }
});

test('retorno 5 vira NEGADO e nao confirma', async () => {
  const { fake, driver } = await criarAmbiente({ resultado: 'NEGADO' });

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.equal(resultado.status, 'NEGADO');
    assert.equal(resultado.sucesso, false);
    assert.equal(resultado.codigoResposta, '0051');
    assert.equal(fake.recebidas.length, 1);
  } finally {
    await fake.parar();
  }
});

test('retorno 6 vira CANCELADO sem sucesso no fluxo de pagamento', async () => {
  const { fake, driver } = await criarAmbiente({ resultado: 'CANCELADO' });

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.equal(resultado.status, 'CANCELADO');
    assert.equal(resultado.sucesso, false);
  } finally {
    await fake.parar();
  }
});

test('confirmacao recusada desfaz a transacao e devolve ERRO', async () => {
  const { fake, driver } = await criarAmbiente({ confirmacao: 'NEGADO' });

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.equal(resultado.status, 'ERRO');
    assert.equal(resultado.sucesso, false);
    assert.deepEqual(
      fake.recebidas.map(mensagem => mensagem.operacao),
      ['112', '6', '191']
    );
  } finally {
    await fake.parar();
  }
});

test('sem resposta do CTFClient devolve TIMEOUT e pede desfazimento', async () => {
  const { fake, driver } = await criarAmbiente({ resultado: 'TIMEOUT', timeoutMs: 200 });

  try {
    const resultado = await driver.iniciarPagamento(PAGAMENTO);

    assert.equal(resultado.status, 'TIMEOUT');
    assert.equal(resultado.sucesso, false);
    assert.deepEqual(
      fake.recebidas.map(mensagem => mensagem.operacao),
      ['112', '191']
    );
  } finally {
    await fake.parar();
  }
});

test('cancelamento com nsu usa a operacao 128', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.cancelarPagamento({
      correlationId: 'ped-1',
      valorCentavos: 2500,
      nsu: '37384',
      dataTransacao: '220615',
    });

    assert.equal(resultado.status, 'CANCELADO');
    assert.equal(resultado.sucesso, true);
    assert.deepEqual(fake.recebidas[0], {
      operacao: '128',
      nsuCTF: '37384',
      dataTransacao: '220615',
      valorTransacao: '2500',
    });
  } finally {
    await fake.parar();
  }
});

test('cancelamento sem nsu vira desfazimento', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const resultado = await driver.cancelarPagamento({ correlationId: 'ped-1' });

    assert.equal(resultado.status, 'CANCELADO');
    assert.equal(fake.recebidas[0].operacao, '191');
  } finally {
    await fake.parar();
  }
});

test('valor invalido e meio desconhecido nao chegam ao CTFClient', async () => {
  const { fake, driver } = await criarAmbiente();

  try {
    const semValor = await driver.iniciarPagamento({ ...PAGAMENTO, valorCentavos: 0 });
    assert.equal(semValor.status, 'ERRO');

    const meioEstranho = await driver.iniciarPagamento({ ...PAGAMENTO, meio: 'DINHEIRO' });
    assert.equal(meioEstranho.status, 'ERRO');

    assert.equal(fake.recebidas.length, 0);
  } finally {
    await fake.parar();
  }
});

test('CTFClient fora do ar devolve ERRO de comunicacao', async () => {
  const driver = createAuttarDriver({
    env: { TOTEM_TEF_AUTTAR_URL: 'ws://127.0.0.1:1', TOTEM_TEF_AUTTAR_TIMEOUT_MS: '500' },
  });

  const resultado = await driver.iniciarPagamento(PAGAMENTO);
  assert.equal(resultado.status, 'ERRO');
  assert.equal(resultado.sucesso, false);
});

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { imprimir } = require('../print');

const BASE64 = Buffer.from('CUPOM DE TESTE\n').toString('base64');

test('impressora nao configurada devolve falha em vez de lancar', async () => {
  const resultado = await imprimir({ dadosBase64: BASE64, tipoImpressora: 'POS_80' });

  assert.equal(resultado.sucesso, false);
  assert.match(resultado.mensagem, /nao configurada/i);
});

test('payload sem dados nao vai para a impressora', async () => {
  assert.equal((await imprimir({ devicePath: 'USB001' })).sucesso, false);
  assert.equal((await imprimir({ dadosBase64: '   ', devicePath: 'USB001' })).sucesso, false);
});

test('payload vazio nao quebra o processo principal', async () => {
  assert.equal((await imprimir()).sucesso, false);
});

/** O renderer carrega pagina remota: devicePath dele nao pode virar caminho arbitrario no SO. */
test('devicePath com travessia de diretorio e recusado antes de tocar o SO', async () => {
  for (const devicePath of ['../../etc/passwd', '/etc/passwd', 'C:/temp/../../x']) {
    const resultado = await imprimir({ dadosBase64: BASE64, devicePath });
    assert.equal(resultado.sucesso, false, devicePath);
    assert.match(resultado.mensagem, /invalido/i, devicePath);
  }
});

const test = require('node:test');
const assert = require('node:assert/strict');
const execUtils = require('../utils/exec-utils');

test('detecta Epson pelo spooler quando a consulta WMI falha', async (t) => {
  let wmi = null;
  t.mock.method(execUtils, 'executarComando', async (comando) => {
    if (comando.includes('Get-Printer')) {
      return { stdout: JSON.stringify({ Name: 'EPSON TM-T20X Receipt (Copy 1)', PortName: 'USB002', PrinterStatus: 'Normal' }) };
    }
    if (wmi === null) throw new Error('WMI indisponível');
    return { stdout: JSON.stringify(wmi) };
  });
  const { listarImpressorasWindows } = require('../core/printer/platforms/windows-detector');
  const lista = await listarImpressorasWindows();
  assert.equal(lista.length, 1);
  assert.equal(lista[0].name, 'EPSON TM-T20X Receipt (Copy 1)');
  assert.equal(lista[0].devicePath, 'USB002');
  // Uma lista WMI não vazia também pode omitir a Epson.
  wmi = [{ Name: 'DARUMA DR800', PortName: 'USB003', Default: true }];
  const completa = await listarImpressorasWindows();
  assert.equal(completa.length, 2);
  assert.equal(completa.find(p => p.name === 'DARUMA DR800').padrao, true);
  assert.ok(completa.some(p => p.name === 'EPSON TM-T20X Receipt (Copy 1)'));
});

const { converterParaEscPos } = require('../core/print/escpos-converter');
const conteudo = Buffer.from([0x1b, 0x61, 1, 0x43, 0x55, 0x50, 0x4f, 0x4d, 0x0a]);

test('mantém os bytes atuais da Diebold e do perfil genérico', () => {
  for (const tipo of ['DIEBOLD_IM693H', 'GENERICA_ESCPOS']) {
    assert.equal(converterParaEscPos(conteudo.toString('base64'), tipo).toString('hex'),
      '1b401b74024355504f4d0a0a0a1d564200');
  }
});

test('Epson TM-T20 e TM-T20X preservam o alinhamento ESC/POS', () => {
  for (const tipo of ['EPSON_TM_T20', 'EPSON_TM_T20X']) {
    const bytes = converterParaEscPos(conteudo.toString('base64'), tipo);
    assert.equal(bytes.subarray(5, 8).toString('hex'), '1b6101');
    assert.equal(bytes.subarray(-4).toString('hex'), '1d564200');
  }
});

test('Daruma DR800 usa alinhamento e corte do protocolo Daruma', () => {
  const bytes = converterParaEscPos(conteudo.toString('base64'), 'DARUMA_800');
  assert.equal(bytes.subarray(5, 8).toString('hex'), '1b6a01');
  assert.equal(bytes.subarray(-2).toString('hex'), '1b6d');
  const formatado = converterParaEscPos(Buffer.from([0x1b, 0x21, 0x28, 0x41, 0x1b, 0x21, 0]).toString('base64'), 'DARUMA_800');
  assert.equal(formatado.subarray(5, -4).toString('hex'), '1b77011b0e001b45411b77001b0e001b46');
});

test('ordinais e graus ocupam uma coluna e mantêm os centavos na linha do item', () => {
  for (const tipo of ['DIEBOLD_IM693H', 'GENERICA_ESCPOS', 'EPSON_TM_T20', 'EPSON_TM_T20X', 'DARUMA_800']) {
    for (const [simbolo, cp850] of [['º', 0xA7], ['ª', 0xA6], ['°', 0xF8]]) {
      for (const [nome, preco] of [[`N${simbolo}10 KIDS`, 'R$10.00'], [`N${simbolo}4`, 'R$13.00']]) {
        // Layout real do backend: descrição (34), espaço, quantidade (3), espaço, valor (9).
        const linha = nome.padEnd(34) + '   1 ' + preco.padStart(9);
        assert.equal(linha.length, 48);
        const bytes = converterParaEscPos(Buffer.from(linha + '\n').toString('base64'), tipo);
        const conteudo = bytes.subarray(5, -(tipo === 'DARUMA_800' ? 4 : 6));
        assert.equal(conteudo[1], cp850, `${tipo}: ${simbolo}`);
        assert.equal(conteudo.length, 49, `${tipo}: 48 colunas e uma quebra de linha`);
        assert.equal(conteudo.subarray(-8).toString('ascii'), preco + '\n');
      }
    }
  }
});

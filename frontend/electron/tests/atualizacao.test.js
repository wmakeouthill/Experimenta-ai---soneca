const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { criarAtualizador, avisarVersaoAtualizada } = require('../atualizacao');

test('confirma a versão nova somente após uma troca de versão instalada', async () => {
  const pasta = fs.mkdtempSync(path.join(os.tmpdir(), 'atualizacao-electron-'));
  const mensagens = [];
  const dialog = { showMessageBox: async options => { mensagens.push(options); } };
  const app = versao => ({ isPackaged: true, getVersion: () => versao, getPath: () => pasta });
  try {
    avisarVersaoAtualizada(app('1.0.0'), dialog);
    assert.equal(mensagens.length, 0);
    avisarVersaoAtualizada(app('1.0.1'), dialog);
    assert.match(mensagens[0].message, /1\.0\.0 → 1\.0\.1/);
    avisarVersaoAtualizada(app('1.0.1'), dialog);
    assert.equal(mensagens.length, 1);
  } finally {
    fs.rmSync(pasta, { recursive: true, force: true });
  }
});

test('entrega a versão instalada ao aviso visual sem abrir diálogo nativo', () => {
  const pasta = fs.mkdtempSync(path.join(os.tmpdir(), 'atualizacao-electron-'));
  const avisos = [];
  const dialog = { showMessageBox: () => { throw new Error('Diálogo nativo inesperado'); } };
  const app = versao => ({ isPackaged: true, getVersion: () => versao, getPath: () => pasta });
  try {
    avisarVersaoAtualizada(app('1.0.2'), dialog, (anterior, atual) => avisos.push({ anterior, atual }));
    avisarVersaoAtualizada(app('1.0.3'), dialog, (anterior, atual) => avisos.push({ anterior, atual }));
    assert.deepEqual(avisos, [{ anterior: '1.0.2', atual: '1.0.3' }]);
  } finally {
    fs.rmSync(pasta, { recursive: true, force: true });
  }
});

function criarCenario({ resposta = 1, checar = async () => {}, instalar = () => {} } = {}) {
  const updater = new EventEmitter();
  const mensagens = [];
  const ordem = [];
  const labels = [];
  const progresso = [];
  const avisos = [];
  updater.checkForUpdates = checar;
  updater.quitAndInstall = (...args) => {
    ordem.push(['instalar', ...args]);
    instalar();
  };
  const dialog = {
    showMessageBox: async options => {
      mensagens.push(options);
      return { response: resposta };
    },
  };
  const atualizador = criarAtualizador({
    app: { isPackaged: true, getVersion: () => '1.0.0' },
    updater,
    dialog,
    platform: 'win32',
    portable: false,
    atualizarMenu: label => labels.push(label),
    atualizarProgresso: valor => progresso.push(valor),
    avisarAtualizacaoPronta: versao => avisos.push(versao),
    antesDeInstalar: async () => ordem.push(['parar-impressao']),
    restaurarAposFalha: async () => ordem.push(['restaurar-impressao']),
  });
  return { atualizador, updater, mensagens, ordem, labels, progresso, avisos };
}

test('mostra versão encontrada e porcentagem durante download manual', async () => {
  const cenario = criarCenario({
    checar: async () => {
      cenario.updater.emit('update-available', { version: '1.0.2' });
      cenario.updater.emit('download-progress', { percent: 42.7 });
    },
  });
  await cenario.atualizador.verificar(true);
  assert.match(cenario.mensagens[0].message, /1\.0\.2/);
  assert.match(cenario.labels.at(-1), /43%/);
  assert.equal(cenario.progresso.at(-1), 0.43);

  cenario.updater.emit('update-downloaded', { version: '1.0.2' });
  assert.equal(cenario.progresso.at(-1), -1);
});

test('release baixada instala ao sair ou após confirmação manual', async () => {
  let verificacoes = 0;
  const cenario = criarCenario({
    checar: async () => { verificacoes += 1; },
  });

  assert.equal(cenario.updater.autoInstallOnAppQuit, true);
  await cenario.atualizador.verificar(false);
  cenario.updater.emit('update-downloaded', { version: '1.0.1' });
  assert.deepEqual(cenario.avisos, ['1.0.1']);
  assert.equal(cenario.mensagens.length, 0);
  assert.match(cenario.labels.at(-1), /Instalar atualização/);
  assert.equal(cenario.atualizador.estaInstalando(), false);

  await cenario.atualizador.verificar(true);
  assert.equal(verificacoes, 1);
  assert.deepEqual(cenario.ordem, []);

  const comConfirmacao = criarCenario({ resposta: 0 });
  comConfirmacao.updater.emit('update-downloaded', { version: '1.0.1' });
  await comConfirmacao.atualizador.verificar(true);
  assert.deepEqual(comConfirmacao.ordem, [['parar-impressao'], ['instalar', true, true]]);
  assert.equal(comConfirmacao.atualizador.estaInstalando(), true);
});

test('falha de rede na checagem automática não interrompe o balcão', async () => {
  const cenario = criarCenario({ checar: async () => { throw new Error('sem rede'); } });
  await cenario.atualizador.verificar(false);
  assert.equal(cenario.mensagens.length, 0);
  assert.deepEqual(cenario.ordem, []);

  await cenario.atualizador.verificar(true);
  assert.equal(cenario.mensagens.length, 1);
  assert.match(cenario.mensagens[0].message, /Não foi possível verificar/);
});

test('checagem manual sem release informa a versão instalada', async () => {
  const cenario = criarCenario();
  cenario.updater.checkForUpdates = async () => {
    cenario.updater.emit('update-not-available');
  };
  await cenario.atualizador.verificar(true);
  assert.equal(cenario.mensagens.length, 1);
  assert.match(cenario.mensagens[0].message, /1\.0\.0/);
});

test('checagens simultâneas não iniciam dois downloads', async () => {
  let resolver;
  let verificacoes = 0;
  const cenario = criarCenario({
    checar: () => {
      verificacoes += 1;
      return new Promise(resolve => { resolver = resolve; });
    },
  });
  const primeira = cenario.atualizador.verificar(false);
  await cenario.atualizador.verificar(true);
  assert.equal(verificacoes, 1);
  assert.match(cenario.mensagens[0].message, /em andamento/);
  resolver();
  await primeira;
});

test('portable não consulta o feed de atualização', async () => {
  let verificacoes = 0;
  const cenario = criarCenario({ checar: async () => { verificacoes += 1; } });
  const portable = criarAtualizador({
    app: { isPackaged: true },
    updater: cenario.updater,
    dialog: { showMessageBox: async () => ({ response: 0 }) },
    platform: 'win32',
    portable: true,
    atualizarMenu: () => {},
    antesDeInstalar: async () => {},
  });
  await portable.verificar(false);
  assert.equal(verificacoes, 0);
});

test('falha ao instalar restaura o servidor de impressão', async () => {
  const cenario = criarCenario({
    resposta: 0,
    instalar: () => { throw new Error('instalador indisponível'); },
  });
  cenario.updater.emit('update-downloaded', { version: '1.0.1' });
  await cenario.atualizador.verificar(true);
  assert.deepEqual(cenario.ordem, [
    ['parar-impressao'],
    ['instalar', true, true],
    ['restaurar-impressao'],
  ]);
  assert.equal(cenario.atualizador.estaInstalando(), false);
});

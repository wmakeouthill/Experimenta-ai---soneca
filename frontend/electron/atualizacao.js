const fs = require('node:fs');
const path = require('node:path');

function avisarVersaoAtualizada(app, dialog) {
  if (!app.isPackaged) return;
  const arquivo = path.join(app.getPath('userData'), 'versao-electron.txt');
  const versaoAtual = app.getVersion();
  try {
    const anterior = fs.existsSync(arquivo) ? fs.readFileSync(arquivo, 'utf8').trim() : null;
    if (anterior !== versaoAtual) fs.writeFileSync(arquivo, versaoAtual, 'utf8');
    if (anterior && anterior !== versaoAtual) {
      void dialog.showMessageBox({
        type: 'info',
        title: 'Aplicativo atualizado',
        message: `Atualização concluída: versão ${anterior} → ${versaoAtual}.`,
        buttons: ['OK'],
      });
    }
  } catch (error) {
    console.error('Erro ao registrar versão do aplicativo:', error);
  }
}

function criarAtualizador({
  app,
  updater,
  dialog,
  atualizarMenu,
  atualizarProgresso = () => {},
  antesDeInstalar,
  restaurarAposFalha = async () => {},
  platform = process.platform,
  portable = Boolean(process.env.PORTABLE_EXECUTABLE_FILE),
}) {
  const habilitado = platform === 'win32' && app.isPackaged && !portable;
  let verificando = false;
  let baixando = false;
  let checagemManual = false;
  let erroDaChecagem = false;
  let versaoPronta = null;
  let instalando = false;
  let novaVersao = null;
  let percentual = null;

  async function informar(type, message) {
    await dialog.showMessageBox({
      type, title: 'Atualizações', message,
      detail: `Versão instalada: ${app.getVersion()}`,
      buttons: ['OK'],
    });
  }

  async function oferecerInstalacao() {
    if (!versaoPronta || instalando) return;
    const { response } = await dialog.showMessageBox({
      type: 'info',
      title: 'Atualização pronta',
      message: `A versão ${versaoPronta.version} está pronta para instalar.`,
      detail: 'Reinicie apenas depois de concluir as vendas e impressões em andamento.',
      buttons: ['Reiniciar e instalar', 'Depois'],
      defaultId: 1,
      cancelId: 1,
    });
    if (response !== 0) return;

    instalando = true;
    try {
      await antesDeInstalar();
      updater.quitAndInstall(true, true);
    } catch (error) {
      instalando = false;
      console.error('Erro ao instalar atualização:', error);
      try {
        await restaurarAposFalha();
      } catch (erroRestauracao) {
        console.error('Erro ao restaurar servidor de impressão:', erroRestauracao);
      }
      await informar('error', 'Não foi possível iniciar a instalação. O aplicativo continua aberto.');
    }
  }

  function tratarErro(error) {
    if (erroDaChecagem) return;
    erroDaChecagem = true;
    verificando = false;
    baixando = false;
    versaoPronta = null;
    percentual = null;
    atualizarProgresso(-1);
    atualizarMenu('Verificar atualizações');
    console.error('Erro ao verificar atualizações:', error);
    if (checagemManual) {
      checagemManual = false;
      void informar('error', 'Não foi possível verificar atualizações. Tente novamente mais tarde.');
    }
  }

  if (habilitado) {
    updater.autoDownload = true;
    updater.autoInstallOnAppQuit = true;

    updater.on('update-available', info => {
      baixando = true;
      novaVersao = info.version;
      percentual = 0;
      atualizarMenu(`Baixando versão ${novaVersao} (0%)...`);
      atualizarProgresso(0);
      if (checagemManual) {
        void informar('info', `Nova versão ${novaVersao} encontrada. O download começou.`);
      }
    });

    updater.on('download-progress', info => {
      percentual = Math.max(0, Math.min(100, Math.round(info.percent)));
      atualizarMenu(`Baixando versão ${novaVersao} (${percentual}%)...`);
      atualizarProgresso(percentual / 100);
    });

    updater.on('update-not-available', () => {
      verificando = false;
      baixando = false;
      percentual = null;
      atualizarProgresso(-1);
      atualizarMenu('Verificar atualizações');
      if (checagemManual) {
        checagemManual = false;
        void informar('info', `Você já está na versão mais recente (${app.getVersion()}).`);
      }
    });

    updater.on('update-downloaded', info => {
      versaoPronta = info;
      verificando = false;
      baixando = false;
      percentual = null;
      atualizarProgresso(-1);
      atualizarMenu(`Instalar atualização ${info.version}...`);
      if (checagemManual) {
        checagemManual = false;
        void oferecerInstalacao();
      }
    });

    updater.on('error', tratarErro);
  }

  async function verificar(manual = false) {
    if (!habilitado) {
      if (manual) await informar('info', 'Atualizações automáticas estão disponíveis apenas no aplicativo Windows instalado.');
      return;
    }
    if (versaoPronta) {
      if (manual) await oferecerInstalacao();
      return;
    }
    if (verificando || baixando) {
      if (manual) {
        const estado = baixando
          ? `Download da versão ${novaVersao} em andamento (${percentual ?? 0}%).`
          : 'A verificação de atualizações já está em andamento.';
        await informar('info', estado);
      }
      return;
    }

    verificando = true;
    checagemManual = manual;
    erroDaChecagem = false;
    try {
      await updater.checkForUpdates();
      if (!baixando) verificando = false;
    } catch (error) {
      tratarErro(error);
    }
  }

  return {
    verificar,
    estaInstalando: () => instalando,
  };
}

module.exports = { criarAtualizador, avisarVersaoAtualizada };

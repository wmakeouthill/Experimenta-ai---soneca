const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const { autoUpdater } = require('electron-updater');
const fs = require('fs');
const path = require('path');
const { resolveTefDriver } = require('./tef');
const { criarResultadoErro } = require('./tef/tef-driver');
const { criarStoreConfirmacoes } = require('./tef/confirmacoes-pendentes');
const { imprimir } = require('./print');
const { criarAtualizador, avisarVersaoAtualizada } = require(app.isPackaged
  ? path.join(process.resourcesPath, 'atualizacao.js')
  : '../electron/atualizacao');

let mainWindow = null;
let atualizador;

function loadLocalEnv() {
  const envPath = app.isPackaged
    ? path.join(app.getPath('userData'), '.env')
    : path.join(__dirname, '.env');
  if (!fs.existsSync(envPath)) {
    return;
  }

  const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) {
      continue;
    }

    const separatorIndex = line.indexOf('=');
    if (separatorIndex <= 0) {
      continue;
    }

    const key = line.slice(0, separatorIndex).trim();
    const value = line.slice(separatorIndex + 1).trim().replace(/^["']|["']$/g, '');
    if (key && process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

function isDevelopmentMode() {
  return process.env.NODE_ENV === 'development' || process.argv.includes('--dev') || !app.isPackaged;
}

function readBoolean(name, defaultValue) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }
  return ['1', 'true', 'sim', 'yes', 'on'].includes(value.toLowerCase());
}

function readNumber(name, defaultValue) {
  const value = Number(process.env[name]);
  return Number.isFinite(value) && value > 0 ? value : defaultValue;
}

function getTotemUrl(isDevelopment) {
  if (process.env.TOTEM_URL) {
    return process.env.TOTEM_URL;
  }

  if (isDevelopment) {
    return process.env.TOTEM_DEV_URL || 'http://localhost:4200/autoatendimento';
  }

  return process.env.TOTEM_PRODUCTION_URL || 'https://experimentaaisoneca.app/autoatendimento';
}

function getMobileUserAgent() {
  return (
    process.env.TOTEM_USER_AGENT ||
    'Mozilla/5.0 (Linux; Android 13; Totem) AppleWebKit/537.36 ' +
      '(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
  );
}

function configureChromiumFlags() {
  app.commandLine.appendSwitch('touch-events', 'enabled');
  app.commandLine.appendSwitch('overscroll-history-navigation', '0');
  app.commandLine.appendSwitch('disable-pinch');
  app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

  if (process.platform === 'win32') {
    app.commandLine.appendSwitch('high-dpi-support', '1');
    app.commandLine.appendSwitch('force-device-scale-factor', process.env.TOTEM_SCALE_FACTOR || '1');
  }
}

function createWindow() {
  const isDevelopment = isDevelopmentMode();
  const fullscreen = readBoolean('TOTEM_FULLSCREEN_ENABLED', true);
  const kiosk = readBoolean('TOTEM_KIOSK_ENABLED', !isDevelopment);
  const showMenu = readBoolean('TOTEM_SHOW_MENU', isDevelopment);
  const mobileEmulation = readBoolean('TOTEM_MOBILE_EMULATION_ENABLED', true);
  const width = readNumber('TOTEM_WINDOW_WIDTH', 430);
  const height = readNumber('TOTEM_WINDOW_HEIGHT', 932);
  const url = getTotemUrl(isDevelopment);

  mainWindow = new BrowserWindow({
    width,
    height,
    minWidth: 360,
    minHeight: 640,
    fullscreen,
    kiosk,
    title: 'Experimenta ai - Totem',
    backgroundColor: '#ffffff',
    autoHideMenuBar: !showMenu,
    show: false,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
      preload: path.join(__dirname, 'preload.js'),
      devTools: readBoolean('TOTEM_DEVTOOLS_ENABLED', isDevelopment),
    },
  });

  if (mobileEmulation) {
    mainWindow.webContents.setUserAgent(getMobileUserAgent());
  }

  if (!showMenu) {
    Menu.setApplicationMenu(null);
    mainWindow.setMenuBarVisibility(false);
  }

  mainWindow.loadURL(url);

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    mainWindow.focus();
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
    console.error('Erro ao carregar totem:', errorCode, errorDescription, validatedURL);
  });

  mainWindow.webContents.on('before-input-event', (event, input) => {
    const isUpdateShortcut =
      input.key.toLowerCase() === 'u' && input.control && input.shift && input.type === 'keyDown';
    const isDevToolsShortcut =
      input.key.toLowerCase() === 'i' && input.control && input.shift && input.type === 'keyDown';
    const isReloadShortcut = input.key.toLowerCase() === 'r' && input.control && input.type === 'keyDown';
    const isFullscreenShortcut = input.key === 'F11' && input.type === 'keyDown';

    if (isUpdateShortcut) {
      event.preventDefault();
      void atualizador.verificar(true);
      return;
    }

    if (isDevToolsShortcut && readBoolean('TOTEM_DEVTOOLS_ENABLED', isDevelopment)) {
      mainWindow.webContents.toggleDevTools();
      event.preventDefault();
      return;
    }

    if (isReloadShortcut && isDevelopment) {
      mainWindow.reload();
      event.preventDefault();
      return;
    }

    if (isFullscreenShortcut && !kiosk) {
      mainWindow.setFullScreen(!mainWindow.isFullScreen());
      event.preventDefault();
    }
  });

  if (readBoolean('TOTEM_DEVTOOLS_OPEN', false)) {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  }
}

function registerIpcHandlers() {
  const tefDriver = resolveTefDriver(process.env);
  const confirmacoes = criarStoreConfirmacoes({
    diretorio: path.join(app.getPath('userData'), 'tef-confirmacoes-pendentes'),
  });
  console.log(`Driver TEF ativo: ${tefDriver.nome}`);

  ipcMain.handle('totem:tef:iniciar-pagamento', async (_event, payload) => {
    try {
      const resultado = await tefDriver.iniciarPagamento(payload);
      // Aprovado = dinheiro ja capturado. A pendencia so sai do disco quando o renderer
      // avisar que o backend gravou.
      confirmacoes.registrar(resultado);
      return resultado;
    } catch (error) {
      console.error('Erro no driver TEF (iniciar):', error);
      return criarResultadoErro('Falha inesperada no TEF do totem.');
    }
  });

  ipcMain.handle('totem:tef:confirmacoes-pendentes', () => confirmacoes.listar());

  ipcMain.handle('totem:tef:confirmacao-registrada', (_event, correlationId) => {
    confirmacoes.concluir(correlationId);
  });

  // Impressao nao e caminho de dinheiro: o pedido ja esta pago e gravado quando chega aqui,
  // entao erro de impressora volta como { sucesso: false } e nunca derruba a venda.
  ipcMain.handle('totem:imprimir', (_event, payload) => imprimir(payload || {}));

  ipcMain.handle('totem:tef:cancelar-pagamento', async (_event, payload) => {
    try {
      return await tefDriver.cancelarPagamento(payload || {});
    } catch (error) {
      console.error('Erro no driver TEF (cancelar):', error);
      return criarResultadoErro('Falha inesperada no TEF do totem.');
    }
  });
}

loadLocalEnv();
configureChromiumFlags();
registerIpcHandlers();

app.whenReady().then(() => {
  atualizador = criarAtualizador({
    app,
    updater: autoUpdater,
    dialog,
    atualizarMenu: () => {},
    atualizarProgresso: valor => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.setProgressBar(valor);
        mainWindow.webContents.send('atualizacao:progresso', valor);
      }
    },
    antesDeInstalar: async () => {},
  });
  createWindow();
  avisarVersaoAtualizada(app, dialog);
  void atualizador.verificar(false);
  setInterval(() => { void atualizador.verificar(false); }, 6 * 60 * 60 * 1000).unref();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

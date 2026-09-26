/**
 * Electron Main Process
 * Este arquivo roda no processo principal do Electron (Node.js)
 * Tem acesso completo ao sistema operacional
 */

const { app, BrowserWindow, ipcMain, screen, Menu, dialog } = require('electron');
const { autoUpdater } = require('electron-updater');
const { criarAtualizador, avisarVersaoAtualizada } = require('./atualizacao');
const path = require('path');

// Biblioteca para detectar impressoras (usa APIs nativas do sistema)
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

// Servidor HTTP local para receber comandos de impressão do backend online
const printServer = require('./infrastructure/http/print-server');

let mainWindow;
let printServerPort = null;
let atualizador;
let avisoAtualizacao = null;

function publicarAvisoAtualizacao(aviso) {
  avisoAtualizacao = aviso;
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('atualizacao:aviso', aviso);
  }
}

ipcMain.handle('atualizacao:estado', () => avisoAtualizacao);
ipcMain.handle('atualizacao:instalar', event => {
  if (!mainWindow || event.sender !== mainWindow.webContents) return;
  return atualizador?.verificar(true);
});

function configureChromiumFlags() {
  if (process.platform !== 'win32') {
    return;
  }

  app.commandLine.appendSwitch('disable-lcd-text');
  app.commandLine.appendSwitch('enable-font-antialiasing');
  app.commandLine.appendSwitch('high-dpi-support', '1');
  app.commandLine.appendSwitch('force-device-scale-factor', '1');
  app.commandLine.appendSwitch('enable-font-hinting');
  app.commandLine.appendSwitch('enable-font-subpixel-positioning');
  app.commandLine.appendSwitch('enable-native-gpu-memory-buffers');
  app.commandLine.appendSwitch(
    'enable-features',
    'VaapiIgnoreDriverChecks,CanvasOopRasterization,UseChromeOSDirectVideoDecoder,SkiaRenderer'
  );
  app.commandLine.appendSwitch(
    'disable-features',
    'VizDisplayCompositor,BlockInsecurePrivateNetworkRequests'
  );
  app.commandLine.appendSwitch(
    'unsafely-treat-insecure-origin-as-secure',
    'https://experimentaaisoneca.app'
  );
}

configureChromiumFlags();

function createWindow() {
  // Define o ícone do aplicativo (Windows usa .ico, Linux/Mac usa .png)
  const iconPath =
    process.platform === 'win32'
      ? path.join(__dirname, 'icon.ico')
      : path.join(__dirname, '../src/assets/experimenta_ai_banner_circular.png');

  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1024,
    minHeight: 768,
    title: 'Experimenta aí do Soneca',
    icon: iconPath, // Ícone do aplicativo (Windows 10 suporta múltiplos tamanhos no ICO)
    backgroundColor: '#f5f5f5',
    webPreferences: {
      nodeIntegration: false, // Segurança: não expor Node.js no renderer
      contextIsolation: true, // Segurança: isolar contexto
      preload: path.join(__dirname, 'preload.js'), // Script de ponte
      // Melhora renderização de texto e gráficos
      enableBlinkFeatures: 'CSSColorSchemeUARendering',
      disableBlinkFeatures: 'Auxclick',
    },
    // Configurações de DPI para melhorar aparência no Windows 10
    titleBarStyle: 'default',
    autoHideMenuBar: false,
    show: false, // Não mostra até estar pronto (melhor UX)
  });

  // Configurações de DPI/Scaling para melhorar aparência no Windows 10
  // Força renderização similar ao Windows 11 (texto e emojis mais finos e nítidos)
  if (process.platform === 'win32') {
    // Abre DevTools automaticamente em desenvolvimento para ver logs
    if (process.env.NODE_ENV === 'development' || process.argv.includes('--dev')) {
      mainWindow.webContents.openDevTools();
    }

    // Quando a página carregar, ajusta configurações visuais
    mainWindow.webContents.on('did-finish-load', () => {
      try {
        const primaryDisplay = screen.getPrimaryDisplay();
        const scaleFactor = primaryDisplay.scaleFactor;

        // Em desenvolvimento, registra o DPI detectado para facilitar diagnóstico
        if (process.env.NODE_ENV === 'development' || process.argv.includes('--dev')) {
          console.log('Fator de escala (DPI) do monitor principal:', scaleFactor);
        }

        // Windows 11 geralmente tem melhor renderização nativa
        // No Windows 10, ajustamos para ficar similar
        // Força zoom de 100% e deixa o sistema gerenciar DPI
        mainWindow.webContents.setZoomFactor(1.0);

        // Configura limites de zoom visual
        mainWindow.webContents.setVisualZoomLevelLimits(1, 3);

        // Injeta CSS para melhorar renderização de texto E emojis
        mainWindow.webContents.insertCSS(`
          * {
            -webkit-font-smoothing: antialiased !important;
            -moz-osx-font-smoothing: grayscale !important;
            text-rendering: optimizeLegibility !important;
          }

          /* Melhora renderização de emojis - especialmente no Windows 10 */
          body, * {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI Emoji", "Segoe UI Symbol",
                         "Segoe UI", "Noto Color Emoji", "Apple Color Emoji", "EmojiOne Color",
                         "Segoe UI Emoji", "Segoe UI", Roboto, Helvetica, Arial, sans-serif !important;
          }

          /* Força renderização nativa de emojis (fina e nítida - igual Windows 11) */
          emoji, [data-emoji], * {
            font-family: "Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji" !important;
            font-style: normal !important;
            font-weight: 400 !important; /* 400 = normal (não bold) - evita emojis grossos */
            -webkit-font-smoothing: auto !important;
            text-rendering: optimizeLegibility !important; /* Melhor que 'auto' para emojis */
          }

          /* Remove qualquer peso extra de emojis (evita traços grossos) */
          emoji, [data-emoji] {
            font-weight: 400 !important;
            text-shadow: none !important;
            -webkit-text-stroke: 0 !important;
          }

          /* Emojis em texto - força renderização suave */
          *:not(script):not(style) {
            font-variant-emoji: emoji;
          }
        `);
      } catch (error) {
        console.error('Erro ao configurar DPI:', error);
      }
    });
  }

  // Carrega a aplicação Angular
  // Em desenvolvimento, aponta para localhost
  // Em produção, carrega do domínio oficial
  const PRODUCTION_URL = 'https://experimentaaisoneca.app';
  const DEV_URL = 'http://localhost:8080';

  // Detecta desenvolvimento: NODE_ENV, flag --dev, ou se não estiver buildado (packaged)
  const isDevelopment =
    process.env.NODE_ENV === 'development' || process.argv.includes('--dev') || !app.isPackaged;

  console.log('🔍 Modo:', isDevelopment ? 'DESENVOLVIMENTO' : 'PRODUÇÃO');
  console.log('🌐 URL:', isDevelopment ? DEV_URL : PRODUCTION_URL);

  if (isDevelopment) {
    mainWindow.loadURL(DEV_URL);
    mainWindow.webContents.openDevTools();

    // Tratamento de erros de conexão em desenvolvimento
    mainWindow.webContents.on(
      'did-fail-load',
      (event, errorCode, errorDescription, validatedURL) => {
        console.error('❌ Erro ao carregar (dev):', errorCode, errorDescription, validatedURL);
        console.log('💡 Verifique se o servidor está rodando em', DEV_URL);
      }
    );
  } else {
    // SEMPRE carrega da URL online servida pelo backend
    mainWindow.loadURL(PRODUCTION_URL);

    // Tratamento de erros de conexão
    mainWindow.webContents.on(
      'did-fail-load',
      (_event, errorCode, errorDescription, validatedURL, isMainFrame) => {
        console.error('❌ Erro ao carregar (prod):', errorCode, errorDescription, validatedURL);
        if (!isMainFrame || errorCode === -3 || mainWindow.isDestroyed()) return;

        mainWindow.show();
        void dialog.showMessageBox(mainWindow, {
          type: 'error',
          title: 'Falha ao abrir o Balcão',
          message: 'Não foi possível carregar o sistema online.',
          detail: `Verifique a conexão e o certificado HTTPS de ${PRODUCTION_URL}.\nErro: ${errorDescription} (${errorCode}).`,
          buttons: ['OK'],
        });
      }
    );

    // Permite navegação externa em novas janelas (se necessário)
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
      require('electron').shell.openExternal(url);
      return { action: 'deny' };
    });
  }

  // Mostra janela apenas quando estiver pronta (melhor UX)
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();

    // Foca na janela
    if (process.platform === 'win32') {
      mainWindow.focus();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Previne fechamento prematuro durante operações importantes
  mainWindow.on('close', event => {
    // Se houver alguma operação crítica em andamento, pode prevenir fechamento aqui
    // Por enquanto, permite fechamento normal
    // Futuramente, pode adicionar lógica para salvar estado ou confirmar fechamento
  });
}

// Função para criar o menu da aplicação
function criarMenu() {
  const PRODUCTION_URL = 'https://experimentaaisoneca.app';
  const DEV_URL = 'http://localhost:8080';
  const isDevelopment =
    process.env.NODE_ENV === 'development' || process.argv.includes('--dev') || !app.isPackaged;
  const URL_INICIAL = isDevelopment ? DEV_URL : PRODUCTION_URL;

  const template = [
    {
      label: 'Opções',
      submenu: [
        {
          label: '🏠 Voltar para Tela Inicial',
          accelerator: 'Ctrl+H',
          click: () => {
            if (mainWindow) {
              mainWindow.loadURL(URL_INICIAL);
            }
          },
        },
        { type: 'separator' },
        {
          label: '🔄 Recarregar',
          accelerator: 'Ctrl+R',
          click: () => {
            if (mainWindow) {
              mainWindow.reload();
            }
          },
        },
        {
          label: '🔍 Abrir DevTools',
          accelerator: 'F12',
          click: () => {
            if (mainWindow) {
              mainWindow.webContents.toggleDevTools();
            }
          },
        },
        {
          label: '🗑️ Limpar cache',
          accelerator: 'Ctrl+Shift+Delete',
          click: async () => {
            if (mainWindow) {
              try {
                const { session } = mainWindow.webContents;
                await session.clearCache();
                await session.clearStorageData({ storages: ['cookies', 'localstorage', 'indexdb'] });
                mainWindow.reload();
                console.log('✅ Cache limpo e página recarregada');
              } catch (error) {
                console.error('Erro ao limpar cache:', error);
              }
            }
          },
        },
        { type: 'separator' },
        {
          id: 'verificar-atualizacoes',
          label: 'Verificar atualizações',
          click: () => { void atualizador.verificar(true); },
        },
        { label: `Versão instalada: ${app.getVersion()}`, enabled: false },
        { type: 'separator' },
        {
          label: 'Sair',
          accelerator: process.platform === 'darwin' ? 'Cmd+Q' : 'Ctrl+Q',
          click: () => {
            app.quit();
          },
        },
      ],
    },
  ];

  // No macOS, adiciona menu padrão
  if (process.platform === 'darwin') {
    template.unshift({
      label: app.getName(),
      submenu: [
        { role: 'about', label: 'Sobre' },
        { type: 'separator' },
        { role: 'services', label: 'Serviços' },
        { type: 'separator' },
        { role: 'hide', label: 'Ocultar' },
        { role: 'hideOthers', label: 'Ocultar Outros' },
        { role: 'unhide', label: 'Mostrar Tudo' },
        { type: 'separator' },
        { role: 'quit', label: 'Sair' },
      ],
    });
  }

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

// Configurações globais de DPI antes de inicializar
app.whenReady().then(async () => {
  // Força DPI awareness no Windows para melhor renderização
  if (process.platform === 'win32') {
    // Informa ao Windows que o app está ciente de DPI
    // Isso melhora a renderização especialmente no Windows 10
    app.commandLine.appendSwitch(
      'disable-features',
      'VizDisplayCompositor,BlockInsecurePrivateNetworkRequests'
    );
  }

  atualizador = criarAtualizador({
    app,
    updater: autoUpdater,
    dialog,
    atualizarMenu: label => {
      const item = Menu.getApplicationMenu()?.getMenuItemById('verificar-atualizacoes');
      if (item) item.label = label;
    },
    atualizarProgresso: valor => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.setProgressBar(valor);
    },
    avisarAtualizacaoPronta: versao => publicarAvisoAtualizacao(
      versao ? { tipo: 'pronta', versao } : null
    ),
    antesDeInstalar: limparRecursos,
    restaurarAposFalha: async () => {
      printServerPort = await printServer.iniciarServidor(3001);
    },
  });

  // Cria o menu da aplicação
  criarMenu();

  // Inicia o servidor HTTP local para receber comandos de impressão
  try {
    printServerPort = await printServer.iniciarServidor(3001);
    console.log(`✅ Servidor de impressão local rodando na porta ${printServerPort}`);
  } catch (error) {
    console.error('❌ Erro ao iniciar servidor de impressão:', error);
  }

  createWindow();
  avisarVersaoAtualizada(app, dialog, (_anterior, versao) => {
    publicarAvisoAtualizacao({ tipo: 'concluida', versao });
  });

  // A checagem é silenciosa; a versão baixada instala no encerramento normal.
  void atualizador.verificar(false);
  const intervaloAtualizacao = setInterval(() => {
    void atualizador.verificar(false);
  }, 6 * 60 * 60 * 1000);
  intervaloAtualizacao.unref();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

// Função para limpar recursos ao encerrar
async function limparRecursos() {
  try {
    // Para o servidor HTTP (agora retorna Promise)
    await printServer.pararServidor();
  } catch (error) {
    console.error('Erro ao limpar recursos (parar servidor de impressão):', error);
  }
}

// Flag para evitar múltiplas execuções de limpeza
let estaLimpando = false;

// Evento quando todas as janelas são fechadas
app.on('window-all-closed', async () => {
  if (atualizador?.estaInstalando()) return;
  if (estaLimpando) return;
  estaLimpando = true;

  await limparRecursos();

  // No macOS, é comum manter o app rodando mesmo sem janelas
  if (process.platform !== 'darwin') {
    app.quit();
  } else {
    estaLimpando = false;
  }
});

// Evento antes do app ser encerrado
app.on('before-quit', event => {
  if (estaLimpando) return;
  event.preventDefault();
  estaLimpando = true;

  void limparRecursos().then(() => app.quit());
});

// Evento quando o app realmente vai encerrar (último evento)
app.on('will-quit', () => {
  // Garante que o servidor seja solicitado a encerrar (caso ainda não tenha sido parado)
  printServer.pararServidor();
});

// Encerramento quando recebe sinais do sistema
process.on('SIGTERM', async () => {
  await limparRecursos();
  app.exit(0);
  process.exit(0);
});

process.on('SIGINT', async () => {
  await limparRecursos();
  app.exit(0);
  process.exit(0);
});

// Garante que o processo seja encerrado completamente
process.on('exit', () => {
  limparRecursos();
});

// Tratamento de erros não capturados antes do encerramento
process.on('uncaughtException', error => {
  console.error('Erro não capturado:', error);
  printServer.pararServidor();
  // Em produção, pode querer salvar logs ou notificar o usuário
});

// Tratamento de rejeições de Promise não tratadas
process.on('unhandledRejection', (reason, promise) => {
  console.error('Promise rejeitada não tratada:', reason);
  // Não encerra o app por isso, mas registra o erro
});

// ============================================
// IPC HANDLERS - Comunicação com o Renderer
// ============================================

/**
 * Lista todas as impressoras disponíveis no sistema
 * Retorna: [{ name: string, devicePath: string, status: string }]
 */
ipcMain.handle('listar-impressoras', async () => {
  try {
    const impressoras = await listarImpressorasSistema();
    return { sucesso: true, impressoras };
  } catch (error) {
    console.error('Erro ao listar impressoras:', error);
    return { sucesso: false, erro: error.message, impressoras: [] };
  }
});

/**
 * Obtém a impressora padrão do sistema
 */
ipcMain.handle('obter-impressora-padrao', async () => {
  try {
    const impressoras = await listarImpressorasSistema();
    const padrao = impressoras.find(p => p.padrao) || impressoras[0];
    return { sucesso: true, impressora: padrao };
  } catch (error) {
    console.error('Erro ao obter impressora padrão:', error);
    return { sucesso: false, erro: error.message };
  }
});

/**
 * Verifica se uma impressora está disponível
 */
ipcMain.handle('verificar-impressora', async (event, devicePath) => {
  try {
    const impressoras = await listarImpressorasSistema();
    const encontrada = impressoras.find(p => p.devicePath === devicePath || p.name === devicePath);
    return { sucesso: true, disponivel: !!encontrada, impressora: encontrada };
  } catch (error) {
    return { sucesso: false, erro: error.message };
  }
});

/**
 * Obtém a porta do servidor de impressão local
 */
ipcMain.handle('obter-porta-servidor-impressao', async () => {
  return { sucesso: true, porta: printServerPort || printServer.obterPorta() };
});

// ============================================
// FUNÇÕES AUXILIARES
// ============================================

/**
 * Lista impressoras usando APIs do sistema operacional
 * Suporta Windows, Linux e macOS
 */
async function listarImpressorasSistema() {
  const plataforma = process.platform;

  if (plataforma === 'win32') {
    return await listarImpressorasWindows();
  } else if (plataforma === 'linux') {
    return await listarImpressorasLinux();
  } else if (plataforma === 'darwin') {
    return await listarImpressorasMacOS();
  } else {
    throw new Error(`Plataforma não suportada: ${plataforma}`);
  }
}

/**
 * Lista impressoras no Windows usando PowerShell
 */
async function listarImpressorasWindows() {
  try {
    // Usa WMI (Windows Management Instrumentation) via PowerShell
    const comando = `powershell -Command "Get-WmiObject -Class Win32_Printer | Select-Object Name, PortName, Default, Status | ConvertTo-Json"`;
    const { stdout } = await execPromise(comando, { timeout: 5000, maxBuffer: 1024 * 1024 });
    const impressoras = JSON.parse(stdout);

    // Normaliza para array
    const lista = Array.isArray(impressoras) ? impressoras : [impressoras];

    return lista.map(imp => ({
      name: imp.Name || '',
      devicePath: imp.PortName || `COM${lista.indexOf(imp)}`,
      status: imp.Status || 'Desconhecido',
      padrao: imp.Default || false,
      tipo: 'windows',
    }));
  } catch (error) {
    console.error('Erro ao listar impressoras Windows:', error);
    // Fallback: tenta usar lpstat se estiver disponível
    return [];
  }
}

/**
 * Lista impressoras no Linux usando lpstat
 */
async function listarImpressorasLinux() {
  try {
    // Usa lpstat (CUPS - Common UNIX Printing System)
    const { stdout } = await execPromise('lpstat -p -d', { timeout: 5000 });
    const linhas = stdout.split('\n').filter(l => l.trim());

    const impressoras = [];
    let impressoraPadrao = '';

    // Extrai impressora padrão
    const padraoMatch = stdout.match(/system default destination:\s*(.+)/);
    if (padraoMatch) {
      impressoraPadrao = padraoMatch[1].trim();
    }

    // Extrai lista de impressoras
    for (const linha of linhas) {
      const match = linha.match(/printer\s+(\S+)/);
      if (match) {
        const nome = match[1];
        impressoras.push({
          name: nome,
          devicePath: `/dev/usb/lp${impressoras.length}`, // Aproximação - pode precisar ajuste
          status: linha.includes('idle') ? 'Disponível' : 'Desconhecido',
          padrao: nome === impressoraPadrao,
          tipo: 'linux',
        });
      }
    }

    // Tenta também listar dispositivos USB diretos
    try {
      const { stdout: usbOut } = await execPromise('ls -la /dev/usb/lp* 2>/dev/null || true', {
        timeout: 3000,
      });
      if (usbOut.trim()) {
        const usbDevices = usbOut.trim().split('\n');
        usbDevices.forEach(device => {
          const match = device.match(/lp(\d+)/);
          if (match) {
            impressoras.push({
              name: `USB Printer ${match[1]}`,
              devicePath: `/dev/usb/lp${match[1]}`,
              status: 'Disponível',
              padrao: false,
              tipo: 'linux-usb',
            });
          }
        });
      }
    } catch {
      // Ignora erros de USB
    }

    return impressoras;
  } catch (error) {
    console.error('Erro ao listar impressoras Linux:', error);
    return [];
  }
}

/**
 * Lista impressoras no macOS usando lpstat
 */
async function listarImpressorasMacOS() {
  try {
    // macOS também usa CUPS
    const { stdout } = await execPromise('lpstat -p -d', { timeout: 5000 });
    const linhas = stdout.split('\n').filter(l => l.trim());

    const impressoras = [];
    let impressoraPadrao = '';

    const padraoMatch = stdout.match(/system default destination:\s*(.+)/);
    if (padraoMatch) {
      impressoraPadrao = padraoMatch[1].trim();
    }

    for (const linha of linhas) {
      const match = linha.match(/printer\s+(\S+)/);
      if (match) {
        const nome = match[1];
        impressoras.push({
          name: nome,
          devicePath: nome, // No macOS, geralmente usa o nome
          status: 'Disponível',
          padrao: nome === impressoraPadrao,
          tipo: 'macos',
        });
      }
    }

    return impressoras;
  } catch (error) {
    console.error('Erro ao listar impressoras macOS:', error);
    return [];
  }
}

module.exports = { createWindow };

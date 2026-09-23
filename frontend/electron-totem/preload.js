const { contextBridge, ipcRenderer } = require('electron');
const { installTouchMode } = require('./touch-mode');
const { installVirtualKeyboard } = require('./virtual-keyboard');

function readBoolean(name, defaultValue) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }
  return ['1', 'true', 'sim', 'yes', 'on'].includes(value.toLowerCase());
}

contextBridge.exposeInMainWorld('totemAPI', {
  modoTotem: true,
  plataforma: process.platform,
  versaoElectron: process.versions.electron,
  iniciarPagamentoTef: payload => ipcRenderer.invoke('totem:tef:iniciar-pagamento', payload),
  // Sem nsu/dataTransacao/valorCentavos o driver so consegue desfazimento (191); o estorno de
  // venda ja capturada (128) precisa do payload inteiro.
  cancelarPagamentoTef: payload => ipcRenderer.invoke('totem:tef:cancelar-pagamento', payload),
  confirmacoesTefPendentes: () => ipcRenderer.invoke('totem:tef:confirmacoes-pendentes'),
  marcarConfirmacaoTefRegistrada: correlationId =>
    ipcRenderer.invoke('totem:tef:confirmacao-registrada', correlationId),
  imprimir: payload => ipcRenderer.invoke('totem:imprimir', payload),
});

let progressoAtualizacao = -1;
function mostrarProgressoAtualizacao() {
  const anterior = document.getElementById('progresso-atualizacao-electron');
  if (progressoAtualizacao < 0) {
    anterior?.remove();
    return;
  }
  if (!document.body) return;
  const aviso = anterior || document.createElement('div');
  aviso.id = 'progresso-atualizacao-electron';
  aviso.style.cssText = 'position:fixed;top:12px;right:12px;z-index:2147483647;padding:8px 12px;border-radius:8px;background:#18212ee6;color:#fff;font:600 14px system-ui;pointer-events:none;';
  aviso.textContent = `Atualizando aplicativo: ${Math.round(progressoAtualizacao * 100)}%`;
  if (!anterior) document.body.appendChild(aviso);
}
ipcRenderer.on('atualizacao:progresso', (_event, valor) => {
  if (!Number.isFinite(valor)) return;
  progressoAtualizacao = valor;
  mostrarProgressoAtualizacao();
});
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mostrarProgressoAtualizacao);
}

if (readBoolean('TOTEM_MOBILE_EMULATION_ENABLED', true)) {
  installTouchMode();
}

if (readBoolean('TOTEM_VIRTUAL_KEYBOARD_ENABLED', true)) {
  installVirtualKeyboard();
}

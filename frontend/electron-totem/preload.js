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
  cancelarPagamentoTef: correlationId =>
    ipcRenderer.invoke('totem:tef:cancelar-pagamento', { correlationId }),
});

if (readBoolean('TOTEM_MOBILE_EMULATION_ENABLED', true)) {
  installTouchMode();
}

if (readBoolean('TOTEM_VIRTUAL_KEYBOARD_ENABLED', true)) {
  installVirtualKeyboard();
}

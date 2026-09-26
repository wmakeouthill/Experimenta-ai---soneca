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

let avisoAtualizacao = null;
let tempoAvisoAtualizacao;
let avisoRecebido = false;
function mostrarAvisoAtualizacao() {
  if (!document.body) return;
  document.getElementById('aviso-versao-totem')?.remove();
  clearTimeout(tempoAvisoAtualizacao);
  if (!avisoAtualizacao || !['pronta', 'concluida'].includes(avisoAtualizacao.tipo)) return;

  const pronta = avisoAtualizacao.tipo === 'pronta';
  const cartao = document.createElement('section');
  cartao.id = 'aviso-versao-totem';
  cartao.setAttribute('role', 'status');
  cartao.setAttribute('aria-live', 'polite');
  cartao.style.cssText = 'position:fixed;top:12px;left:12px;right:12px;z-index:2147483647;box-sizing:border-box;padding:13px 16px;background:var(--delivery-surface,#fff);color:var(--delivery-text,#241c15);border:1px solid var(--delivery-border,#f0e6de);border-left:5px solid var(--delivery-primary,#ff6b35);border-radius:var(--delivery-radius-sm,14px);box-shadow:0 8px 24px rgba(36,20,10,.16);font-family:var(--delivery-font,"Plus Jakarta Sans",system-ui,sans-serif);pointer-events:none;';

  const titulo = document.createElement('strong');
  titulo.style.cssText = 'display:block;font-size:15px;line-height:1.3;';
  titulo.textContent = pronta ? 'Tem novidade no Totem! 🎉' : 'Totem atualizado! 🎉';

  const descricao = document.createElement('span');
  descricao.style.cssText = 'display:block;margin-top:4px;color:var(--delivery-text-secondary,#6b5d53);font-size:13px;line-height:1.4;';
  descricao.textContent = pronta
    ? `Versão ${avisoAtualizacao.versao} pronta. Instala ao encerrar o Totem.`
    : `A versão ${avisoAtualizacao.versao} já está em uso.`;

  cartao.append(titulo, descricao);
  document.body.append(cartao);
  tempoAvisoAtualizacao = setTimeout(() => cartao.remove(), 9000);
}
ipcRenderer.on('atualizacao:aviso', (_event, aviso) => {
  avisoRecebido = true;
  avisoAtualizacao = aviso;
  mostrarAvisoAtualizacao();
});
void ipcRenderer.invoke('atualizacao:estado').then(aviso => {
  if (avisoRecebido) return;
  avisoAtualizacao = aviso;
  mostrarAvisoAtualizacao();
});
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mostrarAvisoAtualizacao);
}

if (readBoolean('TOTEM_MOBILE_EMULATION_ENABLED', true)) {
  installTouchMode();
}

if (readBoolean('TOTEM_VIRTUAL_KEYBOARD_ENABLED', true)) {
  installVirtualKeyboard();
}

/**
 * Preload Script - Ponte segura entre Main e Renderer
 * Expõe APIs do Electron para o Angular de forma segura
 */

const { contextBridge, ipcRenderer } = require('electron');

// Expõe APIs seguras para o renderer (Angular)
contextBridge.exposeInMainWorld('electronAPI', {
  /**
   * Lista todas as impressoras disponíveis
   */
  listarImpressoras: () => ipcRenderer.invoke('listar-impressoras'),
  
  /**
   * Obtém a impressora padrão
   */
  obterImpressoraPadrao: () => ipcRenderer.invoke('obter-impressora-padrao'),
  
  /**
   * Verifica se uma impressora está disponível
   */
  verificarImpressora: (devicePath) => ipcRenderer.invoke('verificar-impressora', devicePath),
  
  /**
   * Obtém a porta do servidor de impressão local
   */
  obterPortaServidorImpressao: () => ipcRenderer.invoke('obter-porta-servidor-impressao'),
  
  /**
   * Informações sobre a plataforma
   */
  plataforma: process.platform,
  
  /**
   * Versão do Electron
   */
  versao: process.versions.electron
});

let avisoAtualizacao = null;
let tempoAvisoAtualizacao;
let avisoRecebido = false;

function mostrarAvisoAtualizacao() {
  if (!document.body) return;
  document.getElementById('aviso-atualizacao-electron')?.remove();
  clearTimeout(tempoAvisoAtualizacao);
  if (!avisoAtualizacao || !['pronta', 'concluida'].includes(avisoAtualizacao.tipo)) return;

  const pronta = avisoAtualizacao.tipo === 'pronta';
  const cartao = document.createElement('section');
  cartao.id = 'aviso-atualizacao-electron';
  cartao.setAttribute('role', 'status');
  cartao.setAttribute('aria-live', 'polite');
  cartao.style.cssText = 'position:fixed;top:16px;right:16px;z-index:2147483647;box-sizing:border-box;width:min(380px,calc(100vw - 32px));padding:18px 42px 18px 20px;background:var(--delivery-surface,#fff);color:var(--delivery-text,#241c15);border:1px solid var(--delivery-border,#f0e6de);border-top:4px solid var(--delivery-primary,#ff6b35);border-radius:var(--delivery-radius-md,16px);box-shadow:0 16px 42px rgba(36,20,10,.18);font-family:var(--delivery-font,"Plus Jakarta Sans",system-ui,sans-serif);';

  const fechar = document.createElement('button');
  fechar.type = 'button';
  fechar.setAttribute('aria-label', 'Fechar aviso de atualização');
  fechar.textContent = '×';
  fechar.style.cssText = 'position:absolute;top:9px;right:11px;border:0;background:transparent;color:var(--delivery-text-secondary,#6b5d53);font:26px system-ui;line-height:1;cursor:pointer;outline-offset:2px;';
  fechar.addEventListener('click', () => cartao.remove());

  const titulo = document.createElement('strong');
  titulo.style.cssText = 'display:block;margin:0 0 6px;font-size:18px;line-height:1.3;';
  titulo.textContent = pronta ? 'Tem novidade chegando! 🎉' : 'Balcão atualizado! 🎉';

  const descricao = document.createElement('p');
  descricao.style.cssText = 'margin:0;color:var(--delivery-text-secondary,#6b5d53);font-size:14px;line-height:1.5;';
  descricao.textContent = pronta
    ? `Versão ${avisoAtualizacao.versao} pronta. Continue atendendo; ela instala ao fechar o Balcão.`
    : `A versão ${avisoAtualizacao.versao} já está em uso.`;

  cartao.append(fechar, titulo, descricao);
  if (pronta) {
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.textContent = 'Reiniciar e instalar';
    botao.style.cssText = 'margin-top:14px;padding:10px 14px;border:0;border-radius:10px;background:var(--delivery-primary,#ff6b35);color:#241c15;font:700 14px var(--delivery-font,"Plus Jakarta Sans",system-ui,sans-serif);cursor:pointer;outline-offset:2px;';
    botao.addEventListener('click', () => { void ipcRenderer.invoke('atualizacao:instalar'); });
    cartao.append(botao);
  }

  document.body.append(cartao);
  tempoAvisoAtualizacao = setTimeout(() => cartao.remove(), 16000);
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


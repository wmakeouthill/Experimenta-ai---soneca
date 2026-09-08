/**
 * Impressao do totem.
 *
 * Ao contrario do TEF, impressao NAO e caminho de dinheiro: a venda ja foi capturada e
 * gravada antes de qualquer byte sair daqui. Por isso nada neste modulo lanca — falha de
 * impressora vira `{ sucesso: false }` e o pedido segue aprovado.
 */

const path = require('path');

// ponytail: o core de impressao (ESC/POS + spooler do Windows) vive no Electron da
// lanchonete e e Node puro, sem dependencia npm. Requerido daqui por caminho relativo para
// nao duplicar ~1.5k linhas de tratamento de impressora que ja custaram caro uma vez.
// Ceiling: quando o totem ganhar electron-builder proprio, o `files` nao alcanca fora da
// pasta do app — ai mover core/print, core/printer, infrastructure/os e utils para um
// pacote compartilhado (file:../print-core) e trocar apenas o RAIZ_CORE abaixo.
// Atencao: `frontend/electron/electron/` e uma copia antiga de core/ e infrastructure/ sem o
// utils/. A raiz viva do app da lanchonete e `frontend/electron/`, que tem main.js e utils/.
const RAIZ_CORE = path.join(__dirname, '..', '..', 'electron');

const { converterParaEscPos } = require(path.join(RAIZ_CORE, 'core', 'print', 'escpos-converter'));
const { imprimirLocalmente } = require(path.join(RAIZ_CORE, 'core', 'print', 'print-executor'));
const { validarEMapearDevicePath } = require(path.join(RAIZ_CORE, 'core', 'printer', 'printer-validator'));

/**
 * O renderer carrega uma pagina remota: devicePath que chega dele e entrada nao confiavel
 * e vai virar caminho de arquivo/comando no SO.
 */
function devicePathSuspeito(devicePath) {
  return devicePath.includes('..') || (devicePath.startsWith('/') && !devicePath.startsWith('/dev/'));
}

function falha(mensagem) {
  return { sucesso: false, mensagem };
}

/**
 * @param {{ dadosBase64: string, tipoImpressora?: string, devicePath?: string }} payload
 * @returns {Promise<{ sucesso: boolean, mensagem?: string }>}
 */
async function imprimir(payload = {}) {
  const { dadosBase64, tipoImpressora } = payload;
  const devicePath = (payload.devicePath || '').trim();

  if (typeof dadosBase64 !== 'string' || dadosBase64.trim().length === 0) {
    return falha('Nada para imprimir.');
  }
  if (!devicePath) {
    return falha('Impressora nao configurada no totem.');
  }
  if (devicePathSuspeito(devicePath)) {
    return falha('devicePath invalido.');
  }

  try {
    const impressora = await validarEMapearDevicePath(devicePath);
    if (!impressora) {
      return falha(`Impressora nao encontrada: "${devicePath}".`);
    }

    const ehObjeto = typeof impressora === 'object';
    const caminhoReal = ehObjeto ? impressora.devicePath || impressora.nome : impressora;
    const nome = ehObjeto ? impressora.nome : null;

    const bytes = converterParaEscPos(dadosBase64, tipoImpressora);
    const resultado = await imprimirLocalmente(bytes, caminhoReal, tipoImpressora, nome);

    return resultado.sucesso ? { sucesso: true } : falha(resultado.erro || 'Falha ao imprimir.');
  } catch (error) {
    console.error('Erro ao imprimir no totem:', error);
    return falha(error.message || 'Falha inesperada ao imprimir.');
  }
}

module.exports = { imprimir };

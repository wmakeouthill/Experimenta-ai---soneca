/**
 * Impressao do totem.
 *
 * Ao contrario do TEF, impressao NAO e caminho de dinheiro: a venda ja foi capturada e
 * gravada antes de qualquer byte sair daqui. Por isso nada neste modulo lanca — falha de
 * impressora vira `{ sucesso: false }` e o pedido segue aprovado.
 */

const path = require('path');

// ponytail: o build copia o core vivo do balcao para resources/print-core;
// em desenvolvimento usamos os mesmos arquivos direto da pasta irma.
const RAIZ_CORE = process.versions.electron && require('electron').app.isPackaged
  ? path.join(process.resourcesPath, 'print-core')
  : path.join(__dirname, '..', '..', 'electron');

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

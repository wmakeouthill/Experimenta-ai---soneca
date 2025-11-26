/**
 * Impressora Windows
 * Responsabilidade: Imprimir no Windows usando apenas spooler do Windows
 */

const { enviarParaRede } = require('./windows-network-printer');
const { enviarParaSpooler } = require('./windows-spooler-printer');

/**
 * Imprime no Windows usando apenas spooler do Windows
 * @param {Buffer} dados - Dados ESC/POS para imprimir
 * @param {string} devicePath - Caminho do dispositivo (IP:PORTA ou nome da impressora)
 * @param {string|null} nomeImpressora - Nome real da impressora (opcional)
 * @returns {Promise<{sucesso: boolean, erro?: string}>}
 */
async function imprimirWindows(dados, devicePath, nomeImpressora = null) {
  try {
    const mensagemImpressao = nomeImpressora
      ? `🪟 Windows: Tentando imprimir em "${devicePath}" (nome: "${nomeImpressora}")`
      : `🪟 Windows: Tentando imprimir em "${devicePath}"`;
    console.log(mensagemImpressao);

    // Se for IP:PORTA (rede), usa rede
    if (devicePath.includes(':') && /^\d+\.\d+\.\d+\.\d+:\d+$/.test(devicePath)) {
      console.log(`🌐 Enviando para impressora de rede: ${devicePath}`);
      return await enviarParaRede(dados, devicePath);
    }

    // Para todos os outros casos, usa spooler do Windows
    console.log(`🖨️ Usando spooler do Windows para enviar dados RAW.`);
    return await enviarParaSpooler(dados, devicePath, nomeImpressora);
  } catch (error) {
    console.error(`❌ Erro em imprimirWindows:`, error);
    return { sucesso: false, erro: error.message };
  }
}

module.exports = {
  imprimirWindows
};

/**
 * Conversor ESC/POS
 * Responsabilidade: Converter dados do cupom para formato ESC/POS binário
 */

/**
 * Converte dados do cupom para formato ESC/POS
 * @param {string} dadosCupom - Dados do cupom em base64
 * @param {string} tipoImpressora - Tipo da impressora (EPSON_TM_T20, DARUMA_800, GENERICA_ESCPOS)
 * @returns {Buffer} - Dados ESC/POS binários
 */
function converterParaEscPos(dadosCupom, tipoImpressora) {
  // Por enquanto, retorna os dados como estão (já vêm em ESC/POS do backend)
  // Futuramente, pode converter para ESC/POS específico baseado no tipoImpressora
  return Buffer.from(dadosCupom, 'base64');
}

module.exports = {
  converterParaEscPos
};


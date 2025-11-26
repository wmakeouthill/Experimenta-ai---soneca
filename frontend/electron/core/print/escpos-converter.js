/**
 * Conversor ESC/POS
 * Responsabilidade: Converter dados do cupom para formato ESC/POS binário
 * e adicionar comandos de impressora (inicialização e finalização)
 */

const { inicializar, cortarPapel, linhaEmBranco } = require('./escpos-commands');

/**
 * Converte dados do cupom para formato ESC/POS completo
 * 
 * O backend envia apenas o CONTEÚDO (bitmap centralizado + dados do pedido).
 * O Electron adiciona comandos de impressora (reset, buffer flush, corte, feeds).
 * 
 * @param {string} dadosCupom - Dados do cupom em base64 (apenas conteúdo do backend)
 * @param {string} tipoImpressora - Tipo da impressora (EPSON_TM_T20, DARUMA_800, GENERICA_ESCPOS)
 * @returns {Buffer} - Dados ESC/POS binários completos (com comandos de impressora)
 */
/**
 * Remove comandos que sabidamente causam problemas em algumas impressoras
 * @param {Buffer} buffer - Buffer original
 * @returns {Buffer} - Buffer sanitizado
 */
function sanitizarComandosProblematicos(buffer) {
  const listaBytes = [];

  for (let i = 0; i < buffer.length; i++) {
    // Detecta ESC a (0x1B 0x61 n) - Alinhamento
    // A impressora Diebold rejeita este comando e trava
    if (i + 2 < buffer.length &&
      buffer[i] === 0x1B &&
      buffer[i + 1] === 0x61) {

      console.log(`⚠️ Removendo comando problemático: ESC a ${buffer[i + 2]} (Alinhamento) na posição ${i}`);
      i += 2; // Pula os 3 bytes (1B 61 n)
      continue;
    }

    listaBytes.push(buffer[i]);
  }

  return Buffer.from(listaBytes);
}

function converterParaEscPos(dadosCupom, tipoImpressora) {
  // 1. Decodifica conteúdo do backend
  let conteudo = Buffer.from(dadosCupom, 'base64');

  console.log(`📦 Conteúdo recebido do backend: ${conteudo.length} bytes`);

  // 1.1 Sanitiza comandos problemáticos (CRÍTICO para Diebold)
  conteudo = sanitizarComandosProblematicos(conteudo);
  console.log(`🧹 Conteúdo sanitizado: ${conteudo.length} bytes`);

  console.log(`🔍 Primeiros 20 bytes (hex): ${conteudo.slice(0, 20).toString('hex')}`);
  console.log(`🔍 Últimos 20 bytes (hex): ${conteudo.slice(-20).toString('hex')}`);

  // 2. Adiciona inicialização (reset da impressora) ANTES do conteúdo
  const init = inicializar();
  console.log(`🔄 Adicionando inicialização: ${init.toString('hex')} (${init.length} bytes)`);
  const comInicializacao = Buffer.concat([init, conteudo]);

  // 3. Adiciona finalização APÓS o conteúdo
  // IMPORTANTE: Usar EXATAMENTE a mesma sequência do teste simples que funcionou:
  // Reset → Conteúdo → 2x LF → Corte
  const linhas = Buffer.from([0x0A, 0x0A]); // 2x LF
  const corte = Buffer.from([0x1D, 0x56, 66, 0]); // GS V 66 0 - Corte completo

  console.log(`🔄 Adicionando finalização: ${linhas.length + corte.length} bytes (2 LF + corte)`);

  const completo = Buffer.concat([comInicializacao, linhas, corte]);

  console.log(`✅ Dados completos gerados: ${completo.length} bytes`);
  console.log(`🔍 Primeiros 5 bytes finais (hex): ${completo.slice(0, 5).toString('hex')}`);
  console.log(`🔍 Últimos 10 bytes finais (hex): ${completo.slice(-10).toString('hex')}`);
  console.log(`   Sequência: Reset → Conteúdo(Sanitizado) → Linhas → Corte`);

  return completo;
}

module.exports = {
  converterParaEscPos
};

/**
 * Conversor ESC/POS
 * Responsabilidade: Converter dados do cupom para formato ESC/POS binário
 * e adicionar comandos de impressora (inicialização e finalização)
 */

const { inicializar, cortarPapel, linhaEmBranco, setCodePage850 } = require('./escpos-commands');

/**
 * Remove comandos que sabidamente causam problemas em algumas impressoras
 * @param {Buffer} buffer - Buffer original
 * @returns {Buffer} - Buffer sanitizado
 */
function sanitizarComandosProblematicos(buffer) {
  const listaBytes = [];
  let i = 0;

  while (i < buffer.length) {
    // 1. Detecta ESC a (0x1B 0x61 n) - Alinhamento
    // A impressora Diebold rejeita este comando e trava
    if (i + 2 < buffer.length &&
      buffer[i] === 0x1B &&
      buffer[i + 1] === 0x61) {

      console.log(`⚠️ Removendo comando problemático: ESC a ${buffer[i + 2]} (Alinhamento) na posição ${i}`);
      i += 3; // Pula os 3 bytes (1B 61 n)
      continue;
    }

    // 2. Detecta GS v 0 (0x1D 0x76 0x30) - Bitmap Raster
    // Causa impressão de lixo se não suportado
    if (i + 2 < buffer.length &&
      buffer[i] === 0x1D &&
      buffer[i + 1] === 0x76 &&
      buffer[i + 2] === 0x30) {

      console.log(`⚠️ Removendo comando de BITMAP (GS v 0) na posição ${i} - Causa lixo na impressão`);

      // O comando GS v 0 tem formato: GS v 0 m xL xH yL yH d1...dk
      // Precisamos pular todo o bloco de dados do bitmap
      // m = buffer[i+3]
      // xL = buffer[i+4], xH = buffer[i+5]
      // yL = buffer[i+6], yH = buffer[i+7]

      if (i + 7 < buffer.length) {
        const xL = buffer[i + 4];
        const xH = buffer[i + 5];
        const yL = buffer[i + 6];
        const yH = buffer[i + 7];

        // Calcula tamanho dos dados do bitmap
        const widthBytes = Math.floor((xL + xH * 256 + 7) / 8);
        const height = yL + yH * 256;
        const dataSize = widthBytes * height;

        console.log(`   Dimensões bitmap: ${xL + xH * 256}x${height} (${dataSize} bytes de dados)`);

        i += 8 + dataSize; // Pula cabeçalho (8 bytes) + dados
        continue;
      }
    }

    listaBytes.push(buffer[i]);
    i++;
  }

  return Buffer.from(listaBytes);
}

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
function converterParaEscPos(dadosCupom, tipoImpressora) {
  // 1. Decodifica conteúdo do backend
  let conteudo = Buffer.from(dadosCupom, 'base64');

  console.log(`📦 Conteúdo recebido do backend: ${conteudo.length} bytes`);

  // 1.1 Sanitiza comandos problemáticos (CRÍTICO para Diebold)
  conteudo = sanitizarComandosProblematicos(conteudo);
  console.log(`🧹 Conteúdo sanitizado: ${conteudo.length} bytes`);

  // 1.2 Correção cirúrgica de UTF-8 para CP850 no buffer
  const listaBytes = [];
  let substituicoes = 0;

  for (let i = 0; i < conteudo.length; i++) {
    // Detecta sequência UTF-8 de 2 bytes (C2 xx ou C3 xx)
    if (i + 1 < conteudo.length && (conteudo[i] === 0xC2 || conteudo[i] === 0xC3)) {
      const b1 = conteudo[i];
      const b2 = conteudo[i + 1];
      let cp850 = null;

      // Mapeamento manual das sequências UTF-8 mais comuns para CP850
      if (b1 === 0xC3) {
        if (b2 === 0xA1) cp850 = 0xA0; // á
        else if (b2 === 0xA9) cp850 = 0x82; // é
        else if (b2 === 0xAD) cp850 = 0xA1; // í
        else if (b2 === 0xB3) cp850 = 0xA2; // ó
        else if (b2 === 0xBA) cp850 = 0xA3; // ú
        else if (b2 === 0xA3) cp850 = 0xC6; // ã
        else if (b2 === 0xB5) cp850 = 0xE4; // õ
        else if (b2 === 0xA7) cp850 = 0x87; // ç
        else if (b2 === 0x81) cp850 = 0xB5; // Á
        else if (b2 === 0x89) cp850 = 0x90; // É
        else if (b2 === 0x8D) cp850 = 0xD6; // Í
        else if (b2 === 0x93) cp850 = 0xE0; // Ó
        else if (b2 === 0x9A) cp850 = 0xE9; // Ú
        else if (b2 === 0x83) cp850 = 0xC7; // Ã
        else if (b2 === 0x95) cp850 = 0xE5; // Õ
        else if (b2 === 0x87) cp850 = 0x80; // Ç
        else if (b2 === 0xAA) cp850 = 0x88; // ê
        else if (b2 === 0xB4) cp850 = 0x93; // ô
      }

      if (cp850 !== null) {
        // console.log(`🔤 Substituindo UTF-8 ${b1.toString(16)} ${b2.toString(16)} por CP850 ${cp850.toString(16)}`);
        listaBytes.push(cp850);
        i++; // Pula o segundo byte
        substituicoes++;
        continue;
      }
    }
    listaBytes.push(conteudo[i]);
  }
  conteudo = Buffer.from(listaBytes);
  console.log(`🔤 Conteúdo com encoding corrigido: ${conteudo.length} bytes (${substituicoes} substituições)`);

  console.log(`🔍 Primeiros 20 bytes (hex): ${conteudo.slice(0, 20).toString('hex')}`);
  console.log(`🔍 Últimos 20 bytes (hex): ${conteudo.slice(-20).toString('hex')}`);

  // 2. Adiciona inicialização (reset + code page) ANTES do conteúdo
  const init = inicializar();
  const cpCommand = setCodePage850(); // ESC t 2
  console.log(`🔄 Adicionando inicialização: Reset + CP850 (${init.length + cpCommand.length} bytes)`);
  const comInicializacao = Buffer.concat([init, cpCommand, conteudo]);

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
  console.log(`   Sequência: Reset → CP850 → Conteúdo(Sanitizado) → Linhas → Corte`);

  return completo;
}

module.exports = {
  converterParaEscPos
};

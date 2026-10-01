/**
 * Conversor ESC/POS
 * Responsabilidade: Converter dados do cupom para formato ESC/POS binário
 * e adicionar comandos de impressora (inicialização e finalização)
 */

const { inicializar, cortarPapel, linhaEmBranco, setCodePage850 } = require('./escpos-commands');

/**
 * Sanitiza comandos que podem causar problemas em algumas impressoras
 * 
 * NOTA: O filtro de bitmap (GS v 0) foi REMOVIDO conforme solicitação.
 * A impressão de imagens deve funcionar em qualquer impressora com
 * configuração correta. Use node-thermal-printer para garantir
 * compatibilidade com diferentes modelos.
 * 
 * @param {Buffer} buffer - Buffer original
 * @param {string} tipoImpressora - Tipo da impressora
 * @returns {Buffer} - Buffer sanitizado
 */
function sanitizarComandosProblematicos(buffer, tipoImpressora) {
  const listaBytes = [];
  let i = 0;

  // Mantém o tratamento já usado pela Diebold e pelo perfil genérico.
  const tipo = (tipoImpressora || '').toUpperCase();
  if (tipo === 'EPSON_TM_T20' || tipo === 'EPSON_TM_T20X') return buffer;

  while (i < buffer.length) {
    if (tipo === 'DARUMA_800' && i + 2 < buffer.length && buffer[i] === 0x1B && buffer[i + 1] === 0x21) {
      const modo = buffer[i + 2];
      // O backend envia ESC !; a Daruma usa comandos separados para altura, largura e negrito.
      listaBytes.push(0x1B, 0x77, modo & 0x20 ? 1 : 0,
        0x1B, 0x0E, modo & 0x10 ? 1 : 0, 0x1B, modo & 0x08 ? 0x45 : 0x46);
      i += 3;
      continue;
    }
    // Detecta ESC a (0x1B 0x61 n) - Alinhamento
    // Algumas impressoras (ex: Diebold) podem ter problemas com este comando
    // Mantemos o filtro apenas para o comando de alinhamento
    if (i + 2 < buffer.length &&
      buffer[i] === 0x1B &&
      buffer[i + 1] === 0x61) {

      if (tipo === 'DARUMA_800') {
        // ESC j n: alinhamento Daruma (mesmo comando usado por node-thermal-printer).
        listaBytes.push(0x1B, 0x6A, buffer[i + 2]);
        i += 3;
        continue;
      }

      console.log(`⚠️ Removendo comando ESC a ${buffer[i + 2]} (Alinhamento) na posição ${i}`);
      i += 3; // Pula os 3 bytes (1B 61 n)
      continue;
    }

    // NOTA: Filtro de bitmap (GS v 0) foi REMOVIDO
    // Imagens agora são passadas diretamente para a impressora
    // Use node-thermal-printer para melhor compatibilidade

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
  console.log(`🖨️ Tipo de Impressora: ${tipoImpressora}`);

  // 1.1 Sanitiza comandos problemáticos (CRÍTICO para Diebold)
  conteudo = sanitizarComandosProblematicos(conteudo, tipoImpressora);
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
      if (b1 === 0xC2) {
        if (b2 === 0xBA) cp850 = 0xA7; // º
        else if (b2 === 0xAA) cp850 = 0xA6; // ª
        else if (b2 === 0xB0) cp850 = 0xF8; // °
      } else if (b1 === 0xC3) {
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
  const corte = (tipoImpressora || '').toUpperCase() === 'DARUMA_800'
    ? Buffer.from([0x1B, 0x6D]) // ESC m: corte Daruma, conforme node-thermal-printer.
    : Buffer.from([0x1D, 0x56, 66, 0]); // GS V 66 0 - mantém Diebold/Epson

  console.log(`🔄 Adicionando finalização: ${linhas.length + corte.length} bytes (2 LF + corte)`);

  const completo = Buffer.concat([comInicializacao, linhas, corte]);

  console.log(`✅ Dados completos gerados: ${completo.length} bytes`);
  console.log(`🔍 Primeiros 5 bytes finais (hex): ${completo.slice(0, 5).toString('hex')}`);
  console.log(`🔍 Últimos 10 bytes finais (hex): ${completo.slice(-10).toString('hex')}`);
  console.log(`   Sequência: Reset → CP850 → Conteúdo(Sanitizado) → Linhas → Corte`);

  return completo;
}

module.exports = {
  converterParaEscPos,
  sanitizarComandosProblematicos
};

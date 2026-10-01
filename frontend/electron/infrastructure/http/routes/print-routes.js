/**
 * Rotas de Impressão
 * Responsabilidade: Definir endpoints HTTP para impressão
 */

const express = require('express');
const { validarEMapearDevicePath } = require('../../../core/printer/printer-validator');
const { converterParaEscPos, sanitizarComandosProblematicos } = require('../../../core/print/escpos-converter');
const { imprimirLocalmente } = require('../../../core/print/print-executor');
const { converterLogoParaBuffer } = require('../../../core/print/thermal-printer-service');

const router = express.Router();

/**
 * Valida request de impressão
 * @param {object} body - Body da requisição
 * @returns {{valido: boolean, erro?: string}}
 */
function validarRequest(body) {
  if (!body.pedidoId || typeof body.pedidoId !== 'string' || body.pedidoId.trim().length === 0) {
    return { valido: false, erro: 'pedidoId é obrigatório e deve ser uma string não vazia' };
  }

  if (!body.tipoImpressora || typeof body.tipoImpressora !== 'string') {
    return { valido: false, erro: 'tipoImpressora é obrigatório' };
  }

  if (!body.devicePath || typeof body.devicePath !== 'string' || body.devicePath.trim().length === 0) {
    return { valido: false, erro: 'devicePath é obrigatório' };
  }

  if (!body.dadosCupom || typeof body.dadosCupom !== 'string' || body.dadosCupom.trim().length === 0) {
    return { valido: false, erro: 'dadosCupom é obrigatório e deve ser uma string base64 válida' };
  }

  // Valida formato básico do devicePath (segurança)
  const devicePathSanitizado = body.devicePath.trim();
  if (devicePathSanitizado.includes('..') ||
    (devicePathSanitizado.startsWith('/') && !devicePathSanitizado.startsWith('/dev/'))) {
    return { valido: false, erro: 'devicePath inválido' };
  }

  return { valido: true };
}

/**
 * POST /imprimir/cupom-fiscal
 * Endpoint para imprimir cupom fiscal
 */
router.post('/imprimir/cupom-fiscal', async (req, res) => {
  try {
    console.log('📥 POST recebido em /imprimir/cupom-fiscal');
    console.log('📦 Body recebido:', JSON.stringify({
      pedidoId: req.body?.pedidoId,
      tipoImpressora: req.body?.tipoImpressora,
      devicePath: req.body?.devicePath,
      dadosCupomLength: req.body?.dadosCupom?.length || 0,
      logoBase64Length: req.body?.logoBase64?.length || 0
    }));

    // Validação
    const validacao = validarRequest(req.body);
    if (!validacao.valido) {
      return res.status(400).json({
        sucesso: false,
        mensagem: validacao.erro
      });
    }

    const { pedidoId, tipoImpressora, devicePath, dadosCupom } = req.body;
    const devicePathSanitizado = devicePath.trim();

    console.log('📄 Recebendo comando de impressão:', { pedidoId, tipoImpressora, devicePath: devicePathSanitizado });

    // Valida e mapeia devicePath
    console.log(`🔍 Validando devicePath: "${devicePathSanitizado}"`);
    const impressoraInfo = await validarEMapearDevicePath(devicePathSanitizado);

    if (!impressoraInfo) {
      console.error(`❌ Impressora não encontrada: "${devicePathSanitizado}"`);
      return res.status(400).json({
        sucesso: false,
        mensagem: `Impressora não encontrada: "${devicePathSanitizado}". Verifique se a impressora está instalada e disponível.`
      });
    }

    // Extrai devicePath real e nome da impressora
    const devicePathReal = typeof impressoraInfo === 'object'
      ? (impressoraInfo.devicePath || impressoraInfo.nome)
      : impressoraInfo;
    const nomeImpressora = typeof impressoraInfo === 'object'
      ? impressoraInfo.nome
      : null;

    console.log(`✅ Impressora validada: "${devicePathSanitizado}" → "${devicePathReal}"${nomeImpressora ? ` (nome: "${nomeImpressora}")` : ''}`);

    // Processa logo se presente (usa node-thermal-printer para conversão confiável)
    let logoEscPos = null;
    const logoBase64 = req.body.logoBase64;
    if (logoBase64 && logoBase64.length > 0) {
      console.log(`🖼️ Processando logo para impressão (${logoBase64.length} chars)...`);
      const logoResult = await converterLogoParaBuffer(logoBase64, tipoImpressora);
      if (logoResult.success && logoResult.buffer) {
        // Centraliza o raster no papel
        // Detecta largura IMPRIMÍVEL do papel (não largura total)
        // 80mm papel = ~72mm área imprimível = 576px = 72 bytes
        let paperWidthBytes;
        if (tipoImpressora && tipoImpressora.includes('DIEBOLD')) {
          paperWidthBytes = 72; // 80mm papel, ~72mm imprimível = 576px
        } else if (tipoImpressora && tipoImpressora.includes('58')) {
          paperWidthBytes = 48; // 58mm = 384px = 48 bytes
        } else {
          paperWidthBytes = 72; // 80mm padrão = 576px = 72 bytes
        }
        console.log(`📄 Papel detectado: ${paperWidthBytes} bytes (${paperWidthBytes * 8}px)`);
        logoEscPos = centralizarRasterBuffer(logoResult.buffer, paperWidthBytes);
        console.log(`✅ Logo processado e centralizado: ${logoEscPos.length} bytes`);
      } else {
        console.warn(`⚠️ Falha ao processar logo: ${logoResult.error}`);
      }
    } else {
      console.log('ℹ️ Sem logo para imprimir (logoBase64 vazio ou não enviado)');
    }

    /**
     * Centraliza um buffer GS v 0 adicionando bytes brancos à esquerda de cada linha
     * @param {Buffer} buffer - Buffer com ESC a 1 + GS v 0 + dados
     * @param {number} paperWidthBytes - Largura do papel em bytes (48 para 48mm = 384px)
     * @returns {Buffer} - Buffer modificado com imagem centralizada
     */
    function centralizarRasterBuffer(buffer, paperWidthBytes) {
      // Procura o comando GS v 0 (1D 76 30)
      let gsv0Index = -1;
      for (let i = 0; i < buffer.length - 7; i++) {
        if (buffer[i] === 0x1D && buffer[i + 1] === 0x76 && buffer[i + 2] === 0x30) {
          gsv0Index = i;
          break;
        }
      }

      if (gsv0Index === -1) {
        console.log('⚠️ GS v 0 não encontrado no buffer');
        return buffer;
      }

      // Extrai parâmetros do GS v 0
      // Formato: 1D 76 30 m xL xH yL yH [dados]
      const m = buffer[gsv0Index + 3];
      const xL = buffer[gsv0Index + 4];
      const xH = buffer[gsv0Index + 5];
      const yL = buffer[gsv0Index + 6];
      const yH = buffer[gsv0Index + 7];

      const widthBytes = xL + (xH * 256);
      const height = yL + (yH * 256);
      const dataStart = gsv0Index + 8;

      console.log(`📊 Raster original: ${widthBytes} bytes/linha x ${height} linhas (${widthBytes * 8}px)`);

      // Calcula padding para centralizar (igual dos dois lados)
      const leftPaddingBytes = Math.floor((paperWidthBytes - widthBytes) / 2);
      const rightPaddingBytes = paperWidthBytes - widthBytes - leftPaddingBytes;

      if (leftPaddingBytes <= 0) {
        console.log('ℹ️ Imagem já está na largura máxima, sem padding');
        return buffer;
      }

      console.log(`🎯 Centralizando: ${leftPaddingBytes} bytes esquerda + ${widthBytes} imagem + ${rightPaddingBytes} bytes direita = ${paperWidthBytes} bytes total`);

      // Novo buffer terá a largura total do papel
      const newWidthBytes = paperWidthBytes;
      const headerBefore = buffer.slice(0, gsv0Index); // ESC a 1 e outros
      const leftPadding = Buffer.alloc(leftPaddingBytes, 0x00); // Zeros = pixels brancos
      const rightPadding = Buffer.alloc(rightPaddingBytes, 0x00);

      // Novo cabeçalho GS v 0 com largura = papel
      const newGsv0 = Buffer.from([
        0x1D, 0x76, 0x30, m,
        newWidthBytes & 0xFF,
        (newWidthBytes >> 8) & 0xFF,
        yL, yH
      ]);

      // Reconstrói os dados linha por linha: padding esquerdo + imagem + padding direito
      const newDataParts = [];
      for (let y = 0; y < height; y++) {
        const lineStart = dataStart + (y * widthBytes);
        const lineEnd = lineStart + widthBytes;
        if (lineEnd <= buffer.length) {
          const lineData = buffer.slice(lineStart, lineEnd);
          newDataParts.push(leftPadding);
          newDataParts.push(lineData);
          newDataParts.push(rightPadding);
        }
      }

      // Parte final do buffer (após os dados da imagem)
      const dataEnd = dataStart + (widthBytes * height);
      const footer = buffer.slice(dataEnd);

      const result = Buffer.concat([headerBefore, newGsv0, ...newDataParts, footer]);

      console.log(`✅ Raster centralizado: largura=${newWidthBytes} bytes (${leftPaddingBytes} + ${widthBytes} + ${rightPaddingBytes})`);

      return result;
    }

    // Converte dados do cupom para ESC/POS
    console.log('🔄 Convertendo dados para ESC/POS...');
    const dadosEscPos = converterParaEscPos(dadosCupom, tipoImpressora);
    console.log(`✅ Dados convertidos: ${dadosEscPos.length} bytes`);

    // Monta buffer final: Logo (se houver) + Dados do cupom
    let bufferFinal;
    if (logoEscPos) {
      // Comandos iniciais
      const resetCmd = Buffer.from([0x1B, 0x40]); // ESC @ - Reset
      const centerCmd = Buffer.from([0x1B, 0x61, 0x01]); // ESC a 1 - Centralizar (teste)

      // Remove comandos de inicialização do início dos dados do cupom
      // A sequência típica é: ESC @ (reset) + ESC t 02 (code page 850)
      // Precisamos remover ambos para não conflitar com o logo já impresso
      let dadosLimpos = dadosEscPos;

      // Remove Reset (ESC @ = 1B 40) se presente
      if (dadosLimpos.length >= 2 && dadosLimpos[0] === 0x1B && dadosLimpos[1] === 0x40) {
        dadosLimpos = dadosLimpos.slice(2);
        console.log('🔧 Removido Reset (ESC @) do início dos dados');
      }

      // Remove Code Page (ESC t n = 1B 74 xx) se presente
      if (dadosLimpos.length >= 3 && dadosLimpos[0] === 0x1B && dadosLimpos[1] === 0x74) {
        dadosLimpos = dadosLimpos.slice(3);
        console.log('🔧 Removido Code Page (ESC t) do início dos dados');
      }

      // Comandos de transição após o logo:
      // Garantem que a impressora saia do modo gráfico e esteja pronta para texto
      const transicao = Buffer.from([
        0x1B, 0x21, 0x00,  // ESC ! 0 - Reset modo de texto (cancela double height/width)
        0x1B, 0x61, 0x00,  // ESC a 0 - Alinhamento à esquerda
        0x1B, 0x74, 0x02,  // ESC t 2 - Code page CP850
        0x0A               // LF - Nova linha
      ]);

      // Reset → Center → Logo → Transição → Dados do cupom (limpos)
      const transicaoModelo = tipoImpressora === 'DARUMA_800'
        ? sanitizarComandosProblematicos(transicao, tipoImpressora) : transicao;
      bufferFinal = Buffer.concat([resetCmd, centerCmd, logoEscPos, transicaoModelo, dadosLimpos]);
      console.log(`✅ Buffer final com logo: ${bufferFinal.length} bytes (logo: ${logoEscPos.length}, transição: ${transicao.length}, dados: ${dadosLimpos.length})`);
    } else {
      bufferFinal = dadosEscPos;
    }

    // Imprime
    console.log(`🖨️ Iniciando impressão em: "${devicePathReal}"${nomeImpressora ? ` (nome: "${nomeImpressora}")` : ''}`);

    let resultado;
    try {
      resultado = await imprimirLocalmente(bufferFinal, devicePathReal, tipoImpressora, nomeImpressora);
    } catch (error) {
      console.error('❌ Exceção ao imprimir:', error);
      console.error('❌ Stack trace:', error.stack);
      return res.status(500).json({
        sucesso: false,
        mensagem: `Erro ao imprimir: ${error.message || 'Erro desconhecido'}`
      });
    }

    if (resultado.sucesso) {
      console.log('✅ Impressão concluída com sucesso');
      res.json({
        sucesso: true,
        mensagem: 'Cupom impresso com sucesso',
        pedidoId,
        dataImpressao: new Date().toISOString()
      });
    } else {
      console.error('❌ Erro na impressão:', resultado.erro);
      res.status(500).json({
        sucesso: false,
        mensagem: resultado.erro || 'Erro desconhecido ao imprimir'
      });
    }
  } catch (error) {
    console.error('❌ Erro ao processar impressão:', error);
    res.status(500).json({
      sucesso: false,
      mensagem: error.message || 'Erro ao processar impressão'
    });
  }
});

/**
 * GET /health
 * Endpoint de saúde/status
 */
router.get('/health', (req, res) => {
  res.json({
    status: 'online',
    plataforma: process.platform,
    timestamp: new Date().toISOString()
  });
});

/**
 * POST /teste/imprimir-simples
 * Endpoint para testar impressão com dados ESC/POS básicos
 */
router.post('/teste/imprimir-simples', async (req, res) => {
  try {
    const { devicePath, nomeImpressora } = req.body;

    // Se nomeImpressora foi fornecido, usa diretamente (mais rápido e confiável)
    // Caso contrário, usa devicePath
    const nomeParaUsar = nomeImpressora || devicePath;

    if (!nomeParaUsar) {
      return res.status(400).json({
        sucesso: false,
        mensagem: 'devicePath ou nomeImpressora é obrigatório'
      });
    }

    const devicePathParaUsar = devicePath || 'USB001';

    console.log(`🧪 TESTE: Enviando para impressora "${nomeParaUsar}" (devicePath: ${devicePathParaUsar})`);

    const path = require('path');
    const { testarImpressaoSimples } = require(path.join(__dirname, '../../os/teste-impressao-simples'));
    const resultado = await testarImpressaoSimples(nomeParaUsar, devicePathParaUsar);

    res.json({
      sucesso: resultado.sucesso,
      mensagem: resultado.sucesso ? 'Teste enviado com sucesso' : resultado.erro,
      nomeImpressoraUsado: nomeParaUsar,
      devicePathUsado: devicePathParaUsar
    });
  } catch (error) {
    console.error('❌ Erro no teste:', error);
    res.status(500).json({
      sucesso: false,
      mensagem: error.message || 'Erro ao testar impressão'
    });
  }
});

/**
 * POST /teste/cupom-completo
 * Endpoint para testar impressão com cupom completo simulado
 */
router.post('/teste/cupom-completo', async (req, res) => {
  try {
    const { devicePath, nomeImpressora } = req.body;

    const nomeParaUsar = nomeImpressora || devicePath || 'DIABO';
    const devicePathParaUsar = devicePath || 'USB001';

    console.log(`🧪 TESTE CUPOM COMPLETO: Enviando para impressora "${nomeParaUsar}" (devicePath: ${devicePathParaUsar})`);

    const path = require('path');
    const { testarCupomCompleto } = require(path.join(__dirname, '../../os/teste-cupom-completo'));
    const resultado = await testarCupomCompleto(nomeParaUsar, devicePathParaUsar);

    res.json({
      sucesso: resultado.sucesso,
      mensagem: resultado.sucesso ? 'Cupom completo simulado enviado com sucesso' : resultado.erro,
      nomeImpressoraUsado: nomeParaUsar,
      devicePathUsado: devicePathParaUsar,
      tamanho: resultado.tamanho
    });
  } catch (error) {
    console.error('❌ Erro no teste cupom completo:', error);
    res.status(500).json({
      sucesso: false,
      mensagem: error.message || 'Erro ao testar impressão'
    });
  }
});

module.exports = router;


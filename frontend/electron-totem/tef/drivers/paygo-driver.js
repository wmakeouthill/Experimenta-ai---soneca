'use strict';

const {
  normalizarResultadoTef,
  criarResultadoErro,
  criarResultadoNaoConfigurado,
} = require('../tef-driver');

const MENSAGEM_NAO_CONFIGURADO =
  'Driver PayGo sem endpoint ou cliente configurado. ' +
  'Pendente: spike PayGo Web (Fase 0 da spec 2026-07-08).';

const RESULTADOS_VALIDOS = Object.freeze(['APROVADO', 'NEGADO', 'TIMEOUT', 'ERRO', 'CANCELADO']);

function lerConfigPayGo(env) {
  const endpoint = (env.TOTEM_TEF_PAYGO_ENDPOINT || '').trim();
  if (!endpoint) {
    return null;
  }

  const timeoutMs = Number(env.TOTEM_TEF_PAYGO_TIMEOUT_MS);
  return {
    endpoint,
    pontoCaptura: (env.TOTEM_TEF_PAYGO_PONTO_CAPTURA || '').trim(),
    timeoutMs: Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 90000,
    adquirente: (env.TOTEM_TEF_PAYGO_ADQUIRENTE || '').trim() || 'PAYGO',
  };
}

function traduzirResposta(resposta, { correlationId, valorCentavos }, config, operacao) {
  if (!resposta || typeof resposta !== 'object') {
    return criarResultadoErro('Cliente PayGo devolveu resposta invalida.');
  }

  const status = RESULTADOS_VALIDOS.includes(resposta.resultado) ? resposta.resultado : 'ERRO';

  return normalizarResultadoTef(
    {
      status,
      correlationId,
      nsu: resposta.nsu,
      bandeira: resposta.bandeira,
      autorizacao: resposta.autorizacao,
      ultimosDigitos: resposta.ultimosDigitos,
      adquirente: config.adquirente,
      valorCentavos,
      codigoResposta: resposta.codigoResposta,
      mensagem: resposta.mensagem,
      dataHora: resposta.dataHora,
    },
    { operacao }
  );
}

function createPayGoDriver({ env = process.env, client = null } = {}) {
  const config = lerConfigPayGo(env);

  function naoConfigurado() {
    return criarResultadoNaoConfigurado(MENSAGEM_NAO_CONFIGURADO);
  }

  return {
    nome: 'paygo',

    async iniciarPagamento(payload) {
      if (!config || !client) {
        return naoConfigurado();
      }

      const valorCentavos = Number(payload?.valorCentavos);
      if (!Number.isInteger(valorCentavos) || valorCentavos <= 0) {
        return criarResultadoErro('Valor invalido para pagamento TEF.');
      }

      const correlationId = String(payload?.correlationId || '');

      try {
        const resposta = await client.iniciarTransacao({
          correlationId,
          valorCentavos,
          meio: payload.meio,
          pontoCaptura: config.pontoCaptura,
          timeoutMs: config.timeoutMs,
        });
        return traduzirResposta(resposta, { correlationId, valorCentavos }, config, 'iniciar');
      } catch (error) {
        return criarResultadoErro(`Falha na comunicacao com o PayGo: ${error.message}`);
      }
    },

    async cancelarPagamento(payload) {
      if (!config || !client) {
        return naoConfigurado();
      }

      const correlationId = String(payload?.correlationId || '');

      try {
        const resposta = await client.cancelarTransacao({
          correlationId,
          pontoCaptura: config.pontoCaptura,
          timeoutMs: config.timeoutMs,
        });
        return traduzirResposta(resposta, { correlationId }, config, 'cancelar');
      } catch (error) {
        return criarResultadoErro(`Falha na comunicacao com o PayGo: ${error.message}`);
      }
    },
  };
}

module.exports = { createPayGoDriver, MENSAGEM_NAO_CONFIGURADO };

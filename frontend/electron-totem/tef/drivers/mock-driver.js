'use strict';

const { normalizarResultadoTef } = require('../tef-driver');

function readNumber(env, name, defaultValue) {
  const value = Number(env[name]);
  return Number.isFinite(value) && value > 0 ? value : defaultValue;
}

function cryptoLikeId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const random = Math.floor(Math.random() * 16);
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function sleep(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function createMockTefResponse(env, payload) {
  const valorCentavos = Number(payload?.valorCentavos);
  if (!Number.isInteger(valorCentavos) || valorCentavos <= 0) {
    return {
      sucesso: false,
      status: 'ERRO',
      mensagem: 'Valor invalido para pagamento TEF.',
    };
  }

  const result = (env.TOTEM_TEF_MOCK_RESULT || 'APROVADO').toUpperCase();
  const correlationId = String(payload?.correlationId || cryptoLikeId());
  const now = new Date();

  if (result === 'NEGADO') {
    return {
      sucesso: false,
      status: 'NEGADO',
      correlationId,
      codigoResposta: '51',
      mensagem: 'Pagamento negado pelo TEF mock.',
      dataHora: now.toISOString(),
    };
  }

  if (result === 'TIMEOUT') {
    return {
      sucesso: false,
      status: 'TIMEOUT',
      correlationId,
      codigoResposta: 'TIMEOUT',
      mensagem: 'Tempo esgotado no TEF mock.',
      dataHora: now.toISOString(),
    };
  }

  if (result === 'ERRO') {
    return {
      sucesso: false,
      status: 'ERRO',
      correlationId,
      codigoResposta: 'ERRO_TECNICO',
      mensagem: 'Erro tecnico simulado no TEF mock.',
      dataHora: now.toISOString(),
    };
  }

  return {
    sucesso: true,
    status: 'APROVADO',
    correlationId,
    autorizacao: `AUT${String(now.getTime()).slice(-6)}`,
    nsu: `NSU${String(now.getTime()).slice(-8)}`,
    bandeira: env.TOTEM_TEF_MOCK_BANDEIRA || 'VISA',
    ultimosDigitos: env.TOTEM_TEF_MOCK_ULTIMOS_DIGITOS || '1234',
    adquirente: 'STONE_TEF_MOCK',
    valorCentavos,
    mensagem: 'Pagamento aprovado pelo TEF mock.',
    dataHora: now.toISOString(),
  };
}

function createMockDriver(env = process.env) {
  return {
    nome: 'mock',
    async iniciarPagamento(payload) {
      await sleep(readNumber(env, 'TOTEM_TEF_MOCK_DELAY_MS', 1200));
      return normalizarResultadoTef(createMockTefResponse(env, payload), { operacao: 'iniciar' });
    },
    async cancelarPagamento(payload) {
      return normalizarResultadoTef(
        {
          status: 'CANCELADO',
          correlationId: String(payload?.correlationId || ''),
          mensagem: 'Pagamento cancelado no TEF mock.',
          dataHora: new Date().toISOString(),
        },
        { operacao: 'cancelar' }
      );
    },
  };
}

module.exports = { createMockDriver };

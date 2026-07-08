'use strict';

const { criarResultadoNaoConfigurado } = require('./tef-driver');
const { createMockDriver } = require('./drivers/mock-driver');
const { createPayGoDriver } = require('./drivers/paygo-driver');
const { createAuttarDriver } = require('./drivers/auttar-driver');

const MENSAGEM_LEGADO = 'TEF real ainda nao foi configurado neste Electron do totem.';

function readBoolean(env, name, defaultValue) {
  const value = env[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }
  return ['1', 'true', 'sim', 'yes', 'on'].includes(value.toLowerCase());
}

function createDriverNaoConfigurado(mensagem) {
  return {
    nome: 'nao-configurado',
    async iniciarPagamento() {
      return criarResultadoNaoConfigurado(mensagem);
    },
    async cancelarPagamento() {
      return criarResultadoNaoConfigurado(mensagem);
    },
  };
}

function resolveTefDriver(env = process.env) {
  const nome = (env.TOTEM_TEF_DRIVER || '').trim().toLowerCase();

  if (nome === 'mock') {
    return createMockDriver(env);
  }
  if (nome === 'paygo') {
    return createPayGoDriver({ env });
  }
  if (nome === 'auttar') {
    return createAuttarDriver();
  }
  if (nome) {
    return createDriverNaoConfigurado(
      `Driver TEF desconhecido: ${nome}. Use mock, paygo ou auttar em TOTEM_TEF_DRIVER.`
    );
  }

  // Retrocompatibilidade com o comportamento anterior a TOTEM_TEF_DRIVER.
  if (!readBoolean(env, 'TOTEM_TEF_MOCK_ENABLED', true)) {
    return createDriverNaoConfigurado(MENSAGEM_LEGADO);
  }

  return createMockDriver(env);
}

module.exports = { resolveTefDriver };

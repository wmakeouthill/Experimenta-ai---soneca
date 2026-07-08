'use strict';

const { criarResultadoNaoConfigurado } = require('../tef-driver');

const MENSAGEM_PENDENCIA_AUTTAR =
  'Driver Auttar/Getnet ainda nao implementado. ' +
  'Pendente: aprovacao da proposta TEF WSGE com a Getnet e kit CTFClient da Auttar ' +
  '(Fase 0 da spec 2026-07-08).';

function createAuttarDriver() {
  return {
    nome: 'auttar',
    async iniciarPagamento() {
      return criarResultadoNaoConfigurado(MENSAGEM_PENDENCIA_AUTTAR);
    },
    async cancelarPagamento() {
      return criarResultadoNaoConfigurado(MENSAGEM_PENDENCIA_AUTTAR);
    },
  };
}

module.exports = { createAuttarDriver, MENSAGEM_PENDENCIA_AUTTAR };

'use strict';

const STATUS_TEF = Object.freeze([
  'APROVADO',
  'NEGADO',
  'TIMEOUT',
  'ERRO',
  'CANCELADO',
  'NAO_CONFIGURADO',
]);

function statusDeSucesso(operacao) {
  return operacao === 'cancelar' ? 'CANCELADO' : 'APROVADO';
}

function normalizarResultadoTef(resultado, { operacao = 'iniciar' } = {}) {
  if (!resultado || typeof resultado !== 'object') {
    return criarResultadoErro('Driver TEF devolveu resposta invalida.');
  }

  const status = STATUS_TEF.includes(resultado.status) ? resultado.status : 'ERRO';
  const sucesso = status === statusDeSucesso(operacao);

  if (
    operacao === 'iniciar' &&
    sucesso &&
    (!resultado.nsu || !resultado.bandeira || !resultado.autorizacao)
  ) {
    return criarResultadoErro('Resultado aprovado sem nsu, bandeira ou autorizacao.');
  }

  return {
    ...resultado,
    status,
    sucesso,
    dataHora: resultado.dataHora || new Date().toISOString(),
  };
}

function criarResultadoErro(mensagem) {
  return {
    sucesso: false,
    status: 'ERRO',
    codigoResposta: 'ERRO_TECNICO',
    mensagem,
    dataHora: new Date().toISOString(),
  };
}

function criarResultadoNaoConfigurado(mensagem) {
  return {
    sucesso: false,
    status: 'NAO_CONFIGURADO',
    mensagem,
    dataHora: new Date().toISOString(),
  };
}

module.exports = {
  STATUS_TEF,
  normalizarResultadoTef,
  criarResultadoErro,
  criarResultadoNaoConfigurado,
};

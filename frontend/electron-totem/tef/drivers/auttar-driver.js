'use strict';

const {
  normalizarResultadoTef,
  criarResultadoErro,
  criarResultadoNaoConfigurado,
} = require('../tef-driver');

const MENSAGEM_NAO_CONFIGURADO =
  'Driver Auttar sem TOTEM_TEF_AUTTAR_URL. Aponte para o WebSocket do CTFClient ' +
  '(configCTFClient.xml: tipointegracao=websocket).';

// Codigos de operacao e de retorno conferidos no "Guia Rapido de Integracao do CTFClient —
// Integracao WebSocket v01.17" (Auttar/Getnet, 02/12/2025), secoes "Codigos de operacao" e
// "Codigos de retorno". O manual esta em auttar/Manual-integracao-WebSocket-v1.17.pdf.
const OPERACAO_POR_MEIO = Object.freeze({
  CARTAO_CREDITO: '112',
  CARTAO_DEBITO: '101',
  CARTAO_VOUCHER: '106',
  PIX: '422',
});

const OPERACAO_CONFIRMACAO = '6';
const OPERACAO_DESFAZIMENTO = '191';
const OPERACAO_CANCELAMENTO = '128';

const RETORNO_APROVADO = 0;
const STATUS_POR_RETORNO = Object.freeze({
  0: 'APROVADO',
  1: 'TIMEOUT',
  5: 'NEGADO',
  6: 'CANCELADO',
  11: 'NEGADO',
});

function lerConfigAuttar(env) {
  const url = (env.TOTEM_TEF_AUTTAR_URL || '').trim();
  if (!url) {
    return null;
  }

  const timeoutMs = Number(env.TOTEM_TEF_AUTTAR_TIMEOUT_MS);
  return {
    url,
    timeoutMs: Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 90000,
    versaoAC: (env.TOTEM_TEF_AUTTAR_VERSAO_AC || '').trim(),
  };
}

function erroDeTimeout(mensagem) {
  const erro = new Error(mensagem);
  erro.timeout = true;
  return erro;
}

function comTimeout(promessa, timeoutMs, mensagem) {
  let timer;
  const limite = new Promise((_resolve, reject) => {
    timer = setTimeout(() => reject(erroDeTimeout(mensagem)), timeoutMs);
  });
  return Promise.race([promessa, limite]).finally(() => clearTimeout(timer));
}

// O protocolo e sincrono (uma resposta por requisicao), entao basta uma fila FIFO de pendentes.
function conectar(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const pendentes = [];
    let encerrada = null;

    const timer = setTimeout(() => {
      socket.close();
      reject(erroDeTimeout(`Timeout ao conectar no CTFClient em ${url}.`));
    }, timeoutMs);

    function falhar(erro) {
      encerrada = erro;
      while (pendentes.length) {
        pendentes.shift().reject(erro);
      }
    }

    socket.addEventListener('open', () => {
      clearTimeout(timer);
      resolve({
        enviar(mensagem) {
          if (encerrada) {
            return Promise.reject(encerrada);
          }
          return new Promise((resolveEnvio, rejectEnvio) => {
            pendentes.push({ resolve: resolveEnvio, reject: rejectEnvio });
            socket.send(JSON.stringify(mensagem));
          });
        },
        fechar() {
          socket.close();
        },
      });
    });

    socket.addEventListener('message', evento => {
      const pendente = pendentes.shift();
      if (pendente) {
        pendente.resolve(String(evento.data));
      }
    });

    socket.addEventListener('error', () => {
      clearTimeout(timer);
      const erro = new Error(`Falha na conexao WebSocket com o CTFClient em ${url}.`);
      falhar(erro);
      reject(erro);
    });

    socket.addEventListener('close', () => {
      clearTimeout(timer);
      falhar(new Error('Conexao com o CTFClient encerrada antes da resposta.'));
    });
  });
}

function parseResposta(texto) {
  try {
    const resposta = JSON.parse(texto);
    return resposta && typeof resposta === 'object' ? resposta : null;
  } catch {
    return null;
  }
}

function primeiraMensagem(display) {
  return Array.isArray(display) && display.length ? display[0].mensagem : undefined;
}

// cupomCliente / cupomReduzido chegam como lista de { linha }. O reduzido e o fallback quando
// o TEF nao devolve a via completa (manual WebSocket v1.17, tabela de parametros de resposta).
function juntarCupom(...listas) {
  for (const linhas of listas) {
    if (Array.isArray(linhas) && linhas.length) {
      return linhas.map(item => String(item?.linha ?? '')).join('\n');
    }
  }
  return undefined;
}

function traduzirResposta(resposta, { correlationId, valorCentavos, operacao }) {
  if (!resposta) {
    return criarResultadoErro('CTFClient devolveu resposta invalida.');
  }

  const retorno = Number(resposta.retorno);
  // Sucesso de cancelamento/desfazimento tambem vem como retorno 0; o contrato do totem
  // espera CANCELADO nessa operacao.
  const status =
    retorno === RETORNO_APROVADO && operacao === 'cancelar'
      ? 'CANCELADO'
      : STATUS_POR_RETORNO[retorno] || 'ERRO';
  // O cartao ja chega mascarado do CTFClient; guardamos so os 4 ultimos digitos.
  const cartao = resposta.cartao ? String(resposta.cartao) : '';

  return normalizarResultadoTef(
    {
      status,
      correlationId,
      nsu: resposta.nsuCTF,
      bandeira: resposta.bandeira,
      autorizacao: resposta.codigoAprovacao,
      ultimosDigitos: cartao ? cartao.slice(-4) : undefined,
      adquirente: resposta.redeAdquirente || 'GETNET_AUTTAR',
      valorCentavos,
      dataTransacao: resposta.dataTransacao,
      codigoResposta: resposta.codigoErro,
      mensagem: primeiraMensagem(resposta.display),
      // Via do cliente: a adquirente exige que a AC entregue o comprovante de toda transacao
      // aprovada. So a via do cliente e capturada — o totem nao tem impressora nem operador
      // para a via do estabelecimento.
      comprovanteCliente: juntarCupom(resposta.cupomCliente, resposta.cupomReduzido),
    },
    { operacao }
  );
}

function traduzirFalha(error, { correlationId, valorCentavos, operacao = 'iniciar' }) {
  if (!error.timeout) {
    return criarResultadoErro(`Falha na comunicacao com o CTFClient: ${error.message}`);
  }
  return normalizarResultadoTef(
    { status: 'TIMEOUT', correlationId, valorCentavos, mensagem: error.message },
    { operacao }
  );
}

function createAuttarDriver({ env = process.env } = {}) {
  const config = lerConfigAuttar(env);

  async function trocarMensagens(usar) {
    const conexao = await comTimeout(
      conectar(config.url, config.timeoutMs),
      config.timeoutMs,
      `Timeout ao conectar no CTFClient em ${config.url}.`
    );

    async function pedir(mensagem) {
      const texto = await comTimeout(
        conexao.enviar(config.versaoAC ? { versaoAC: config.versaoAC, ...mensagem } : mensagem),
        config.timeoutMs,
        'CTFClient nao respondeu dentro do tempo limite.'
      );
      return parseResposta(texto);
    }

    try {
      return await usar(pedir);
    } finally {
      conexao.fechar();
    }
  }

  async function desfazerSilenciosamente() {
    try {
      await trocarMensagens(pedir => pedir({ operacao: OPERACAO_DESFAZIMENTO }));
    } catch {
      // Sem conexao nao ha o que desfazer daqui: o CTF derruba sozinho o que nao foi confirmado.
    }
  }

  return {
    nome: 'auttar',

    async iniciarPagamento(payload) {
      if (!config) {
        return criarResultadoNaoConfigurado(MENSAGEM_NAO_CONFIGURADO);
      }

      const valorCentavos = Number(payload?.valorCentavos);
      if (!Number.isInteger(valorCentavos) || valorCentavos <= 0) {
        return criarResultadoErro('Valor invalido para pagamento TEF.');
      }

      const operacao = OPERACAO_POR_MEIO[payload?.meio];
      if (!operacao) {
        return criarResultadoErro(`Meio de pagamento sem operacao Auttar: ${payload?.meio}.`);
      }

      const correlationId = String(payload?.correlationId || '');

      try {
        return await trocarMensagens(async pedir => {
          const resposta = await pedir({
            operacao,
            valorTransacao: String(valorCentavos),
            documento: correlationId,
            numeroTransacao: '1',
          });

          const resultado = traduzirResposta(resposta, {
            correlationId,
            valorCentavos,
            operacao: 'iniciar',
          });

          if (!resultado.sucesso) {
            return resultado;
          }

          // O CTF desfaz transacao aprovada e nao confirmada. Se a confirmacao falhar,
          // desfazemos explicitamente para nao cobrar uma venda que o totem deu por perdida.
          const confirmacao = await pedir({
            operacao: OPERACAO_CONFIRMACAO,
            numeroTransacao: '1',
          });

          if (Number(confirmacao?.retorno) === RETORNO_APROVADO) {
            return resultado;
          }

          await pedir({ operacao: OPERACAO_DESFAZIMENTO }).catch(() => null);
          return criarResultadoErro(
            'Pagamento aprovado mas nao confirmado no CTF; desfazimento solicitado.'
          );
        });
      } catch (error) {
        // Falha depois do envio pode ter deixado uma transacao aprovada no CTF. O desfazimento
        // total derruba as transacoes nao confirmadas desta fase de recebimento.
        await desfazerSilenciosamente();
        return traduzirFalha(error, { correlationId, valorCentavos });
      }
    },

    async cancelarPagamento(payload) {
      if (!config) {
        return criarResultadoNaoConfigurado(MENSAGEM_NAO_CONFIGURADO);
      }

      const correlationId = String(payload?.correlationId || '');
      const nsu = payload?.nsu ? String(payload.nsu) : '';

      // Sem NSU nao ha venda concluida para estornar: e o desfazimento da transacao em curso.
      const mensagem = nsu
        ? {
            operacao: OPERACAO_CANCELAMENTO,
            nsuCTF: nsu,
            dataTransacao: payload?.dataTransacao,
            valorTransacao: String(payload?.valorCentavos ?? ''),
          }
        : { operacao: OPERACAO_DESFAZIMENTO };

      try {
        return await trocarMensagens(async pedir =>
          traduzirResposta(await pedir(mensagem), {
            correlationId,
            valorCentavos: payload?.valorCentavos,
            operacao: 'cancelar',
          })
        );
      } catch (error) {
        return traduzirFalha(error, {
          correlationId,
          valorCentavos: payload?.valorCentavos,
          operacao: 'cancelar',
        });
      }
    },
  };
}

module.exports = {
  createAuttarDriver,
  MENSAGEM_NAO_CONFIGURADO,
  OPERACAO_POR_MEIO,
  OPERACAO_CONFIRMACAO,
  OPERACAO_DESFAZIMENTO,
  OPERACAO_CANCELAMENTO,
  RETORNO_APROVADO,
};

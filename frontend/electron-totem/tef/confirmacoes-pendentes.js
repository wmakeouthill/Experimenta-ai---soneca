'use strict';

const fs = require('node:fs');
const path = require('node:path');

// O correlationId vem do renderer e vira nome de arquivo: so aceitamos o que ja e seguro
// como caminho (UUID passa; qualquer coisa com barra ou ponto-ponto, nao).
const ID_VALIDO = /^[A-Za-z0-9_-]{1,100}$/;

/**
 * Guarda em disco a confirmacao de uma venda ja aprovada na maquininha, ate o backend
 * registra-la. Entre a aprovacao no CTF e o POST do totem o dinheiro ja saiu do cartao e o
 * backend ainda nao sabe de nada; se o totem cair nesse intervalo, o arquivo sobrevive e o
 * renderer reenvia na proxima abertura.
 *
 * ponytail: fs sincrono, um arquivo por venda, sem lock — o totem faz uma venda por vez.
 * Se um dia houver concorrencia real aqui, troque por append em log unico.
 */
function criarStoreConfirmacoes({ diretorio }) {
  function arquivo(correlationId) {
    return ID_VALIDO.test(String(correlationId || ''))
      ? path.join(diretorio, `${correlationId}.json`)
      : null;
  }

  return {
    /**
     * `confirmacao` e exatamente o corpo do POST; `estorno` sao os dados da operacao 128, o
     * unico caminho de volta se o backend recusar a venda em definitivo.
     */
    registrar(resultado) {
      const destino = resultado?.sucesso ? arquivo(resultado.correlationId) : null;
      if (!destino) {
        return;
      }

      fs.mkdirSync(diretorio, { recursive: true });
      fs.writeFileSync(
        destino,
        JSON.stringify({
          confirmacao: {
            correlationId: resultado.correlationId,
            aprovado: true,
            nsuTef: resultado.nsu,
            bandeira: resultado.bandeira,
            codigoAutorizacao: resultado.autorizacao,
            codigoAdquirente: resultado.adquirente,
            comprovanteCliente: resultado.comprovanteCliente,
          },
          estorno: {
            correlationId: resultado.correlationId,
            nsu: resultado.nsu,
            dataTransacao: resultado.dataTransacao,
            valorCentavos: resultado.valorCentavos,
          },
        })
      );
    },

    concluir(correlationId) {
      const destino = arquivo(correlationId);
      if (destino) {
        fs.rmSync(destino, { force: true });
      }
    },

    listar() {
      if (!fs.existsSync(diretorio)) {
        return [];
      }

      return fs
        .readdirSync(diretorio)
        .filter(nome => nome.endsWith('.json'))
        .map(nome => {
          try {
            return JSON.parse(fs.readFileSync(path.join(diretorio, nome), 'utf8'));
          } catch {
            return null;
          }
        })
        .filter(Boolean);
    },
  };
}

module.exports = { criarStoreConfirmacoes };

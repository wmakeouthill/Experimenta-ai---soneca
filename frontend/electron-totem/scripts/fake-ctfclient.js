'use strict';

// CTFClient falso: servidor WebSocket que fala o mesmo JSON do CTFClient real
// (tipointegracao=websocket). Serve para desenvolver e testar o driver auttar sem o
// kit/credenciais da Auttar.
//
// Uso manual (dois terminais):
//   node scripts/fake-ctfclient.js --porta 2500 --resultado APROVADO
//   TOTEM_TEF_DRIVER=auttar TOTEM_TEF_AUTTAR_URL=ws://127.0.0.1:2500 npm run dev

const http = require('node:http');
const crypto = require('node:crypto');

const {
  OPERACAO_CONFIRMACAO,
  OPERACAO_DESFAZIMENTO,
  OPERACAO_CANCELAMENTO,
} = require('../tef/drivers/auttar-driver');

const GUID_WEBSOCKET = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

// ponytail: quadro unico de texto, sem fragmentacao nem ping/pong. Cobre o protocolo do
// CTFClient (JSON pequeno, request/response). Se um dia precisar de mais, troque por `ws`.
function lerQuadro(buffer) {
  if (buffer.length < 2) return null;

  const opcode = buffer[0] & 0x0f;
  const mascarado = (buffer[1] & 0x80) !== 0;
  let tamanho = buffer[1] & 0x7f;
  let offset = 2;

  if (tamanho === 126) {
    if (buffer.length < 4) return null;
    tamanho = buffer.readUInt16BE(2);
    offset = 4;
  } else if (tamanho === 127) {
    if (buffer.length < 10) return null;
    tamanho = Number(buffer.readBigUInt64BE(2));
    offset = 10;
  }

  const mascara = mascarado ? buffer.subarray(offset, offset + 4) : null;
  if (mascarado) offset += 4;
  if (buffer.length < offset + tamanho) return null;

  const dados = Buffer.from(buffer.subarray(offset, offset + tamanho));
  if (mascara) {
    for (let i = 0; i < dados.length; i += 1) {
      dados[i] ^= mascara[i % 4];
    }
  }

  return { opcode, texto: dados.toString('utf8'), resto: buffer.subarray(offset + tamanho) };
}

function montarQuadro(texto) {
  const dados = Buffer.from(texto, 'utf8');
  let cabecalho;

  if (dados.length < 126) {
    cabecalho = Buffer.from([0x81, dados.length]);
  } else if (dados.length < 65536) {
    cabecalho = Buffer.alloc(4);
    cabecalho.writeUInt8(0x81, 0);
    cabecalho.writeUInt8(126, 1);
    cabecalho.writeUInt16BE(dados.length, 2);
  } else {
    cabecalho = Buffer.alloc(10);
    cabecalho.writeUInt8(0x81, 0);
    cabecalho.writeUInt8(127, 1);
    cabecalho.writeBigUInt64BE(BigInt(dados.length), 2);
  }

  return Buffer.concat([cabecalho, dados]);
}

function sleep(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function respostaPagamento(requisicao, resultado) {
  const base = { operacao: requisicao.operacao, valorTransacao: Number(requisicao.valorTransacao) };

  if (resultado === 'NEGADO') {
    return {
      ...base,
      retorno: 5,
      codigoErro: '0051',
      codigoRespAutorizadora: '51',
      display: [{ mensagem: 'TRANSAC NAO AUTORIZADA' }],
    };
  }

  if (resultado === 'CANCELADO') {
    return {
      ...base,
      retorno: 6,
      codigoErro: '0000',
      display: [{ mensagem: 'CANCELADA PELO OPERADOR' }],
    };
  }

  const sufixo = String(Date.now()).slice(-6);
  const pix = String(requisicao.operacao) === '422';

  return {
    ...base,
    retorno: 0,
    codigoErro: '0000',
    display: [{ mensagem: 'TRANSAC APROVADA' }],
    codigoRespAutorizadora: pix ? 'YN' : 'G1',
    codigoAprovacao: pix ? `E9040088${sufixo}` : sufixo,
    nsuCTF: sufixo,
    nsuAutorizadora: `1000${sufixo}`,
    bandeira: pix ? '**' : 'MASTERCARD',
    redeAdquirente: 'GETNET',
    dataTransacao: '220615',
    // Layout copiado do exemplo do manual (v1.17, p.13): cartao ja mascarado na via.
    cupomCliente: [
      { linha: '              REDE GETNET' },
      { linha: '22/06/15 17:55:38 AUT:006299' },
      { linha: 'CARTAO              ************6479' },
      { linha: '            CREDITO A VISTA' },
      { linha: `NSU_CTF: ${sufixo}  LOJA: 0001  PDV: 300` },
    ],
    cupomReduzido: [{ linha: 'MASTERCARD        C************6479' }],
    ...(pix
      ? { pix: { transactionId: `00100000000427779103466${sufixo}`, receiverPsp: '0033' } }
      : { cartao: '515590******6479' }),
  };
}

function iniciarFakeCtfClient({
  porta = 0,
  resultado = 'APROVADO',
  // 'NEGADO' na confirmacao exercita o desfazimento automatico do driver.
  confirmacao = 'APROVADO',
  atrasoMs = 0,
  onErro = erro => console.error('[fake-ctfclient]', erro.message),
} = {}) {
  const recebidas = [];
  const conexoes = new Set();

  function responder(requisicao) {
    const operacao = String(requisicao.operacao);

    if (operacao === OPERACAO_CONFIRMACAO) {
      return confirmacao === 'APROVADO'
        ? { operacao, retorno: 0, codigoErro: '0000' }
        : { operacao, retorno: 20, codigoErro: '0020' };
    }

    if (operacao === OPERACAO_DESFAZIMENTO) {
      return { operacao, retorno: 0, codigoErro: '0000' };
    }

    if (operacao === OPERACAO_CANCELAMENTO) {
      return resultado === 'NEGADO'
        ? { operacao, retorno: 5, codigoErro: '0051' }
        : {
            operacao,
            retorno: 0,
            codigoErro: '0000',
            nsuCTF: requisicao.nsuCTF,
            bandeira: 'MASTERCARD',
            codigoAprovacao: '000001',
            redeAdquirente: 'GETNET',
            display: [{ mensagem: 'CANCELAMENTO OK' }],
          };
    }

    return resultado === 'TIMEOUT' ? null : respostaPagamento(requisicao, resultado);
  }

  const servidor = http.createServer((_req, res) => {
    res.writeHead(426).end('Somente WebSocket.');
  });

  servidor.on('upgrade', (req, socket) => {
    const chave = req.headers['sec-websocket-key'] || '';
    // SHA-1 aqui e o handshake do RFC 6455, nao criptografia: o algoritmo e fixado pelo
    // protocolo e o valor derivado e publico.
    const aceite = crypto
      .createHash('sha1')
      .update(chave + GUID_WEBSOCKET)
      .digest('base64');

    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\n' +
        'Upgrade: websocket\r\n' +
        'Connection: Upgrade\r\n' +
        `Sec-WebSocket-Accept: ${aceite}\r\n\r\n`
    );

    conexoes.add(socket);
    socket.on('close', () => conexoes.delete(socket));
    socket.on('error', () => conexoes.delete(socket));

    let pendente = Buffer.alloc(0);

    socket.on('data', async dados => {
      pendente = Buffer.concat([pendente, dados]);

      for (let quadro = lerQuadro(pendente); quadro; quadro = lerQuadro(pendente)) {
        pendente = quadro.resto;

        if (quadro.opcode === 0x8) {
          socket.end();
          return;
        }
        if (quadro.opcode !== 0x1) {
          continue;
        }

        try {
          const requisicao = JSON.parse(quadro.texto);
          recebidas.push(requisicao);

          const resposta = responder(requisicao);
          if (!resposta) {
            continue;
          }
          if (atrasoMs > 0) {
            await sleep(atrasoMs);
          }
          if (!socket.destroyed) {
            socket.write(montarQuadro(JSON.stringify(resposta)));
          }
        } catch (error) {
          onErro(error);
        }
      }
    });
  });

  const ouvindo = new Promise(resolve => servidor.listen(porta, '127.0.0.1', resolve));

  return {
    recebidas,
    async url() {
      await ouvindo;
      return `ws://127.0.0.1:${servidor.address().port}`;
    },
    async parar() {
      await ouvindo;
      for (const socket of conexoes) {
        socket.destroy();
      }
      await new Promise(resolve => servidor.close(resolve));
    },
  };
}

function lerArgumentos(argv, env) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i].startsWith('--')) {
      args[argv[i].slice(2)] = argv[i + 1];
    }
  }
  return {
    porta: Number(args.porta) || Number(env.TOTEM_TEF_AUTTAR_PORTA) || 2500,
    resultado: (args.resultado || 'APROVADO').toUpperCase(),
    atrasoMs: Number(args.atraso) || 0,
  };
}

if (require.main === module) {
  const opcoes = lerArgumentos(process.argv.slice(2), process.env);
  const fake = iniciarFakeCtfClient({ ...opcoes, onErro: error => console.error(error.message) });

  fake.url().then(url => {
    console.log(
      `CTFClient falso ouvindo ${url} ` +
        `(resultado=${opcoes.resultado}, atraso=${opcoes.atrasoMs}ms). Ctrl+C para sair.`
    );
  });
}

module.exports = { iniciarFakeCtfClient, lerQuadro, montarQuadro };

import { encode } from 'uqr';

/**
 * Cartão de mesa para impressão: 100 × 140 mm, 4 por folha A4, com marcas de corte.
 * Tudo em SVG com unidades em mm para sair no tamanho real. O QR é gerado aqui,
 * sem serviço externo, com correção H para aguentar o logo no centro.
 */
export interface CartaoMesa {
  numero: number;
  nome: string;
  url: string;
}

const LOGO = '/assets/experimenta_ai_banner_circular.png';
const LOGO_PROPORCAO = 505 / 351;
const TINTA = '#171312';
const PAPEL = '#FFFFFF';
const AMARELO = '#FFC41F';
const CINZA = '#645C59';
const POR_FOLHA = 4;

const doisDigitos = (n: number): string => String(n).padStart(2, '0');
const escapar = (s: string): string => s.replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);

/** Caminho SVG do QR (1 unidade = 1 módulo), unindo módulos escuros vizinhos na mesma linha. */
export function qrCaminho(texto: string): { modulos: number; d: string } {
  const { size, data } = encode(texto, { ecc: 'H', border: 0 });
  let d = '';
  data.forEach((linha, r) => {
    for (let c = 0; c < size; c++) {
      if (!linha[c]) continue;
      let len = 1;
      while (linha[c + len]) len++;
      // 1.03 de altura evita frestas brancas entre linhas no antialiasing
      d += `M${c} ${r}h${len}v1.03h-${len}z`;
      c += len - 1;
    }
  });
  return { modulos: size, d };
}

/** QR simples (sem logo) como data URL, para a prévia na tela. */
export function qrDataUrl(texto: string): string {
  const { modulos, d } = qrCaminho(texto);
  const lado = modulos + 8;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-4 -4 ${lado} ${lado}">` +
    `<rect x="-4" y="-4" width="${lado}" height="${lado}" fill="${PAPEL}"/>` +
    `<path d="${d}" fill="${TINTA}"/></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

function qrComLogo(url: string, x: number, y: number, lado: number): string {
  const { modulos, d } = qrCaminho(url);
  // Logo com até 30% da largura: a correção H recupera ~30% dos módulos
  const lw = lado * 0.3;
  const lh = lw / LOGO_PROPORCAO;
  const folga = lado * 0.025;
  const lx = x + (lado - lw) / 2;
  const ly = y + (lado - lh) / 2;
  return (
    `<g transform="translate(${x} ${y}) scale(${lado / modulos})"><path d="${d}" fill="${TINTA}"/></g>` +
    `<rect x="${lx - folga}" y="${ly - folga}" width="${lw + 2 * folga}" height="${lh + 2 * folga}" rx="${folga * 1.5}" fill="${PAPEL}"/>` +
    `<image href="${LOGO}" x="${lx}" y="${ly}" width="${lw}" height="${lh}"/>`
  );
}

// data-fit = largura máxima em mm; ajustarTextos() encolhe o que passar (nome longo, mesa com 3 dígitos)
function cartao({ numero, nome, url }: CartaoMesa): string {
  return `
  <rect width="100" height="140" fill="${AMARELO}"/>
  <text x="8" y="15" class="d" fill="${TINTA}" font-size="6.4" letter-spacing="1.1">MESA</text>
  <text x="6.5" y="45" class="d" fill="${TINTA}" font-size="36" data-fit="43">${doisDigitos(numero)}</text>
  <image href="${LOGO}" x="50" y="5" width="46" height="33" preserveAspectRatio="xMidYMid meet"/>
  <text x="8" y="53.5" class="b" fill="${TINTA}" font-size="4.4" font-weight="800" data-fit="84">${escapar(nome)}</text>
  <rect x="8" y="59" width="84" height="73" rx="5" fill="${PAPEL}" stroke="${TINTA}" stroke-width="1.2"/>
  ${qrComLogo(url, 25, 64, 50)}
  <text x="50" y="121.6" text-anchor="middle" class="b" fill="${TINTA}" font-size="4" font-weight="800" data-fit="76">Aponte a câmera e faça o pedido</text>
  <text x="50" y="127" text-anchor="middle" class="b" fill="${CINZA}" font-size="3" font-weight="600" data-fit="76">O pedido vai direto para a cozinha, sem fila.</text>`;
}

function marcasDeCorte(x0: number, y0: number, cols: number, linhas: number): string {
  const afasta = 1.5;
  const tam = 3.5;
  const largura = cols * 100;
  const altura = linhas * 140;
  let d = '';
  for (let i = 0; i <= cols; i++) {
    const x = x0 + i * 100;
    d += `M${x} ${y0 - afasta}v${-tam}M${x} ${y0 + altura + afasta}v${tam}`;
  }
  for (let j = 0; j <= linhas; j++) {
    const y = y0 + j * 140;
    d += `M${x0 - afasta} ${y}h${-tam}M${x0 + largura + afasta} ${y}h${tam}`;
  }
  return `<path d="${d}" fill="none" stroke="${TINTA}" stroke-width="0.25"/>`;
}

function folha(cartoes: CartaoMesa[]): string {
  const pecas = cartoes
    .map((c, i) => `<g transform="translate(${5 + (i % 2) * 100} ${8.5 + Math.floor(i / 2) * 140})">${cartao(c)}</g>`)
    .join('');
  const marcas = marcasDeCorte(5, 8.5, Math.min(cartoes.length, 2), Math.ceil(cartoes.length / 2));
  // Altura 296 mm com "slice" (escala 1:1 pela largura): 297 mm exatos às vezes gera folha em branco no fim
  return `<svg class="folha" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 210 297" preserveAspectRatio="xMidYMin slice">${pecas}${marcas}</svg>`;
}

/** Documento HTML completo, uma folha A4 a cada 4 cartões. */
export function htmlCartoesMesa(cartoes: CartaoMesa[]): string {
  const folhas: string[] = [];
  for (let i = 0; i < cartoes.length; i += POR_FOLHA) {
    folhas.push(folha(cartoes.slice(i, i + POR_FOLHA)));
  }
  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>QR Code das mesas</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Lilita+One&family=Plus+Jakarta+Sans:wght@600;800&display=block">
<style>
@page { size: A4; margin: 0; }
html, body { margin: 0; background: ${PAPEL}; }
body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.folha { display: block; width: 210mm; height: 296mm; break-after: page; }
.folha:last-child { break-after: auto; }
.d { font-family: 'Lilita One', 'Arial Black', Impact, sans-serif; }
.b { font-family: 'Plus Jakarta Sans', 'Segoe UI', Arial, sans-serif; }
</style>
</head>
<body>${folhas.join('')}</body>
</html>`;
}

/** Encolhe na horizontal os textos mais largos que o data-fit. Precisa das fontes já carregadas. */
export function ajustarTextos(doc: Document): void {
  doc.querySelectorAll<SVGTextElement>('text[data-fit]').forEach(t => {
    const max = Number(t.dataset['fit']);
    const largura = t.getComputedTextLength();
    if (largura <= max) return;
    const x = Number(t.getAttribute('x'));
    const y = Number(t.getAttribute('y'));
    t.setAttribute('transform', `translate(${x} ${y}) scale(${max / largura} 1) translate(${-x} ${-y})`);
  });
}

/**
 * Imprime por um iframe oculto: não depende de pop-up (bloqueado no navegador
 * e negado pelo setWindowOpenHandler do Electron).
 */
export function imprimirCartoesMesa(cartoes: CartaoMesa[]): void {
  if (cartoes.length === 0) return;
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  iframe.style.cssText = 'position:absolute;width:0;height:0;border:0;visibility:hidden';
  iframe.onload = async () => {
    const win = iframe.contentWindow;
    if (!win) return;
    const fontes = win.document.fonts;
    // Sem internet as fontes falham e o cartão sai com as alternativas do sistema
    await Promise.all(
      ['36px "Lilita One"', '800 10px "Plus Jakarta Sans"', '600 10px "Plus Jakarta Sans"'].map(f =>
        fontes.load(f).catch(() => [])
      )
    );
    ajustarTextos(win.document);
    win.addEventListener('afterprint', () => iframe.remove());
    win.focus();
    win.print();
  };
  iframe.srcdoc = htmlCartoesMesa(cartoes);
  document.body.appendChild(iframe);
}

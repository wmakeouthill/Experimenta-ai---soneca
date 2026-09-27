/**
 * Markdown mínimo das respostas do chat IA convertido em HTML.
 * Escapa o texto inteiro antes e só gera <p>, <br>, <ul>, <ol>, <li>, <strong> e <em>,
 * então o resultado é seguro para [innerHTML] (que ainda passa pelo sanitizer do Angular).
 */

interface Lista {
  tag: 'ul' | 'ol';
  inicio: number;
  itens: string[];
}

// "- item", "* item", "• item", "1. item" ou "1) item"
const ITEM_LISTA = /^\s*(?:[-*•]|(\d+)[.)])\s+(.*)$/;
const TITULO = /^\s*#{1,6}\s+(.*)$/;
const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function markdownChatParaHtml(texto: string): string {
  const html: string[] = [];
  let paragrafo: string[] = [];
  let lista: Lista | null = null;

  for (const linha of escaparHtml(texto).split(/\r?\n/)) {
    const item = ITEM_LISTA.exec(linha);
    if (item) {
      html.push(paragrafoHtml(paragrafo));
      paragrafo = [];
      const tag = item[1] ? 'ol' : 'ul';
      if (!lista || lista.tag !== tag) {
        html.push(listaHtml(lista));
        lista = { tag, inicio: Number(item[1] ?? 1), itens: [] };
      }
      lista.itens.push(formatarInline(item[2]));
    } else if (linha.trim()) {
      html.push(listaHtml(lista));
      lista = null;
      const titulo = TITULO.exec(linha);
      paragrafo.push(titulo ? `<strong>${formatarInline(titulo[1])}</strong>` : formatarInline(linha.trim()));
    } else {
      // Linha em branco fecha o parágrafo, mas não a lista: "1. a\n\n2. b" continua uma lista só
      html.push(paragrafoHtml(paragrafo));
      paragrafo = [];
    }
  }

  html.push(paragrafoHtml(paragrafo), listaHtml(lista));
  return html.join('');
}

function escaparHtml(texto: string): string {
  return texto.replace(/[&<>"']/g, c => ESCAPES[c] ?? c);
}

// Sem lookbehind: quebra o bundle inteiro no Safari do iOS anterior ao 16.4
function formatarInline(texto: string): string {
  return texto
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*\s](?:[^*]*[^*\s])?)\*/g, '<em>$1</em>');
}

function paragrafoHtml(linhas: string[]): string {
  return linhas.length ? `<p>${linhas.join('<br>')}</p>` : '';
}

function listaHtml(lista: Lista | null): string {
  if (!lista) return '';
  const inicio = lista.tag === 'ol' && lista.inicio !== 1 ? ` start="${lista.inicio}"` : '';
  return `<${lista.tag}${inicio}>${lista.itens.map(i => `<li>${i}</li>`).join('')}</${lista.tag}>`;
}

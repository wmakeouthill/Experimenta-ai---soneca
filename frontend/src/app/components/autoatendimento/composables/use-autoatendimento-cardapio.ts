import { signal, computed, inject } from '@angular/core';
import { ProdutoService, Produto } from '../../../services/produto.service';
import { CategoriaService, Categoria } from '../../../services/categoria.service';
import { firstValueFrom } from 'rxjs';

export interface GrupoCategoriaTotem {
  id: string;
  nome: string;
  produtos: Produto[];
}

/**
 * Composable para gerenciar o cardápio no auto atendimento.
 * Responsabilidade única: carregar e filtrar categorias/produtos.
 * Diferente do pedido-mesa, usa os services autenticados diretamente.
 */
export function useAutoAtendimentoCardapio() {
  const produtoService = inject(ProdutoService);
  const categoriaService = inject(CategoriaService);

  // Estado
  const categorias = signal<Categoria[]>([]);
  const produtos = signal<Produto[]>([]);
  const carregando = signal(false);
  const categoriaSelecionada = signal<string | null>(null);

  // Computed
  const produtosFiltrados = computed(() => {
    const categoria = categoriaSelecionada();
    const todosProdutos = produtos();

    if (!categoria) return todosProdutos;
    return todosProdutos.filter(p => p.categoria === categoria);
  });

  const produtosAgrupadosPorCategoria = computed((): GrupoCategoriaTotem[] => {
    const produtosFiltro = produtosFiltrados();
    const categoriasAtivas = categorias();

    const grupos: GrupoCategoriaTotem[] = [];

    for (const cat of categoriasAtivas) {
      const produtosDaCategoria = produtosFiltro.filter(p => p.categoria === cat.nome);
      if (produtosDaCategoria.length > 0) {
        grupos.push({
          id: cat.id,
          nome: cat.nome,
          produtos: produtosDaCategoria
        });
      }
    }

    return grupos;
  });

  // Ações
  /** Falha propaga: quem chama mostra a tela de erro com "Tentar Novamente". */
  async function carregar(): Promise<void> {
    carregando.set(true);

    try {
      // Usa os services autenticados (operador logado)
      const [categoriasResp, produtosResp] = await Promise.all([
        firstValueFrom(categoriaService.listar(true)), // Apenas ativas
        firstValueFrom(produtoService.listar({ disponivel: true })) // Apenas disponíveis
      ]);

      categorias.set(categoriasResp);
      produtos.set(produtosResp);
    } finally {
      carregando.set(false);
    }
  }

  function selecionarCategoria(categoriaId: string | null): void {
    categoriaSelecionada.set(categoriaId);
  }

  function limparFiltros(): void {
    categoriaSelecionada.set(null);
  }

  return {
    // Estado
    categorias: categorias.asReadonly(),
    produtos: produtos.asReadonly(),
    carregando: carregando.asReadonly(),
    categoriaSelecionada: categoriaSelecionada.asReadonly(),

    // Computed
    produtosFiltrados,
    produtosAgrupadosPorCategoria,

    // Ações
    carregar,
    selecionarCategoria,
    limparFiltros
  };
}

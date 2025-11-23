import { Component, inject, PLATFORM_ID, OnInit, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { useProdutos } from './composables/use-produtos';
import { ProdutoModalComponent } from './modals/produto-modal/produto-modal.component';
import { CategoriaModalComponent } from './modals/categoria-modal/categoria-modal.component';
import { ProdutoService } from '../../services/produto.service';

@Component({
  selector: 'app-cardapio',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ProdutoModalComponent,
    CategoriaModalComponent
  ],
  templateUrl: './cardapio.component.html',
  styleUrl: './cardapio.component.css'
})
export class CardapioComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly produtoService = inject(ProdutoService);

  // Composable com toda a lógica de produtos
  readonly produtosComposable = useProdutos();

  // Expor propriedades necessárias
  readonly produtosPaginados = this.produtosComposable.produtosPaginados;
  readonly categoriasAtivas = this.produtosComposable.categoriasAtivas;
  readonly categoriaSelecionada = this.produtosComposable.categoriaSelecionada;
  readonly estado = this.produtosComposable.estado;
  readonly erro = this.produtosComposable.erro;
  readonly estaCarregando = this.produtosComposable.estaCarregando;
  readonly temProdutos = this.produtosComposable.temProdutos;
  readonly pesquisaTexto = this.produtosComposable.pesquisaTexto;

  // Modal states
  readonly mostrarModalProduto = signal(false);
  readonly mostrarModalCategoria = signal(false);
  readonly produtoEditando = signal<any>(null);

  ngOnInit(): void {
    if (this.isBrowser && this.estado() === 'idle') {
      this.carregarDados();
    }
  }

  private carregarDados(): void {
    this.produtosComposable.carregarProdutos();
    this.produtosComposable.carregarCategorias();
  }

  filtrarPorCategoria(categoriaId: string | null): void {
    this.produtosComposable.filtrarPorCategoria(categoriaId);
  }

  pesquisar(texto: string): void {
    this.produtosComposable.pesquisar(texto);
  }

  irParaPagina(pagina: number): void {
    this.produtosComposable.irParaPagina(pagina);
  }

  limparFiltros(): void {
    this.produtosComposable.limparFiltros();
  }

  recarregar(): void {
    this.carregarDados();
  }

  abrirModalProduto(produto?: any): void {
    this.produtoEditando.set(produto || null);
    this.mostrarModalProduto.set(true);
  }

  fecharModalProduto(): void {
    this.mostrarModalProduto.set(false);
    this.produtoEditando.set(null);
  }

  abrirModalCategoria(): void {
    this.mostrarModalCategoria.set(true);
  }

  fecharModalCategoria(): void {
    this.mostrarModalCategoria.set(false);
  }

  editarProduto(produto: any): void {
    this.abrirModalProduto(produto);
  }

  excluirProduto(id: string): void {
    if (!this.isBrowser) {
      return;
    }

    if (!confirm('Tem certeza que deseja excluir este produto?')) {
      return;
    }

    this.produtoService.excluir(id)
      .subscribe({
        next: () => {
          this.carregarDados();
        },
        error: (error) => {
          console.error('Erro ao excluir produto:', error);
          if (this.isBrowser) {
            alert('Erro ao excluir produto. Tente novamente.');
          }
        }
      });
  }

  alternarDisponibilidade(produto: any): void {
    if (!this.isBrowser) {
      return;
    }

    const novaDisponibilidade = !produto.disponivel;
    
    this.produtoService.alternarDisponibilidade(produto.id, novaDisponibilidade)
      .subscribe({
        next: () => {
          this.carregarDados();
        },
        error: (error) => {
          console.error('Erro ao alterar disponibilidade do produto:', error);
          if (this.isBrowser) {
            alert('Erro ao alterar disponibilidade do produto. Tente novamente.');
          }
        }
      });
  }

  onProdutoSalvo(): void {
    this.carregarDados();
    this.produtosComposable.carregarCategorias();
  }

  onCategoriaSalva(): void {
    this.produtosComposable.carregarCategorias();
  }

  gerarNumerosPagina(): (number | string)[] {
    const total = this.produtosPaginados().totalPaginas;
    const atual = this.produtosPaginados().paginaAtual;
    const numeros: (number | string)[] = [];

    if (total <= 7) {
      // Se há 7 ou menos páginas, mostrar todas
      for (let i = 1; i <= total; i++) {
        numeros.push(i);
      }
    } else {
      // Sempre mostrar primeira página
      numeros.push(1);

      if (atual > 3) {
        numeros.push('...');
      }

      // Páginas ao redor da atual
      const inicio = Math.max(2, atual - 1);
      const fim = Math.min(total - 1, atual + 1);

      for (let i = inicio; i <= fim; i++) {
        numeros.push(i);
      }

      if (atual < total - 2) {
        numeros.push('...');
      }

      // Sempre mostrar última página
      if (total > 1) {
        numeros.push(total);
      }
    }

    return numeros;
  }
}

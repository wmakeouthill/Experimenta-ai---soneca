import { Component, inject, PLATFORM_ID, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { useSessoes } from './composables/use-sessoes';
import { SessaoTrabalhoService, SessaoTrabalho, StatusSessao } from '../../services/sessao-trabalho.service';

@Component({
  selector: 'app-sessoes',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule
  ],
  templateUrl: './sessoes.component.html',
  styleUrl: './sessoes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessoesComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly sessaoService = inject(SessaoTrabalhoService);

  readonly sessoesComposable = useSessoes();

  readonly StatusSessao = StatusSessao;

  readonly sessoesPaginadas = this.sessoesComposable.sessoesPaginadas;
  readonly estado = this.sessoesComposable.estado;
  readonly erro = this.sessoesComposable.erro;
  readonly estaCarregando = this.sessoesComposable.estaCarregando;
  readonly temSessoes = this.sessoesComposable.temSessoes;
  readonly pesquisaTexto = this.sessoesComposable.pesquisaTexto;
  readonly dataFiltro = this.sessoesComposable.dataFiltro;

  ngOnInit(): void {
    if (this.isBrowser) {
      this.carregarDados();
    }
  }

  private carregarDados(): void {
    this.sessoesComposable.carregarSessoes();
  }

  filtrarPorData(data: string | null): void {
    this.sessoesComposable.filtrarPorData(data);
  }

  pesquisar(texto: string): void {
    this.sessoesComposable.pesquisar(texto);
  }

  limparFiltros(): void {
    this.sessoesComposable.limparFiltros();
  }

  recarregar(): void {
    this.carregarDados();
  }

  irParaPagina(pagina: number): void {
    this.sessoesComposable.irParaPagina(pagina);
  }

  iniciarSessao(): void {
    const usuarioId = 'usuario-temporario'; // TODO: obter do serviço de autenticação
    this.sessaoService.iniciar(usuarioId).subscribe({
      next: () => {
        this.carregarDados();
      },
      error: (error) => {
        if (this.isBrowser) {
          alert(error.error?.message || 'Erro ao iniciar sessão');
        }
      }
    });
  }

  pausarSessao(sessao: SessaoTrabalho): void {
    this.sessaoService.pausar(sessao.id).subscribe({
      next: () => {
        this.carregarDados();
      },
      error: (error) => {
        if (this.isBrowser) {
          alert(error.error?.message || 'Erro ao pausar sessão');
        }
      }
    });
  }

  retomarSessao(sessao: SessaoTrabalho): void {
    this.sessaoService.retomar(sessao.id).subscribe({
      next: () => {
        this.carregarDados();
      },
      error: (error) => {
        if (this.isBrowser) {
          alert(error.error?.message || 'Erro ao retomar sessão');
        }
      }
    });
  }

  finalizarSessao(sessao: SessaoTrabalho): void {
    if (!this.isBrowser || !confirm('Tem certeza que deseja finalizar esta sessão?')) {
      return;
    }

    this.sessaoService.finalizar(sessao.id).subscribe({
      next: () => {
        this.carregarDados();
      },
      error: (error) => {
        if (this.isBrowser) {
          alert(error.error?.message || 'Erro ao finalizar sessão');
        }
      }
    });
  }

  formatarData(data: string): string {
    return new Date(data).toLocaleDateString('pt-BR');
  }

  formatarDataHora(data: string): string {
    return new Date(data).toLocaleString('pt-BR');
  }

  gerarNumerosPagina(): (number | string)[] {
    const total = this.sessoesPaginadas().totalPaginas;
    const atual = this.sessoesPaginadas().paginaAtual;
    const numeros: (number | string)[] = [];

    if (total <= 7) {
      for (let i = 1; i <= total; i++) {
        numeros.push(i);
      }
    } else {
      numeros.push(1);

      if (atual > 3) {
        numeros.push('...');
      }

      const inicio = Math.max(2, atual - 1);
      const fim = Math.min(total - 1, atual + 1);

      for (let i = inicio; i <= fim; i++) {
        numeros.push(i);
      }

      if (atual < total - 2) {
        numeros.push('...');
      }

      if (total > 1) {
        numeros.push(total);
      }
    }

    return numeros;
  }
}


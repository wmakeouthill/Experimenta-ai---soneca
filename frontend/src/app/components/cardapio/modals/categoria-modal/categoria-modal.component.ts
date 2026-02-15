import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  effect,
  inject,
  input,
  output,
  PLATFORM_ID,
} from '@angular/core';
import { ReactiveFormsModule, Validators } from '@angular/forms';
import { of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { CategoriaService } from '../../../../services/categoria.service';
import { useFormulario } from '../../composables/use-formulario';
import { BaseModalComponent } from '../base-modal/base-modal.component';

export interface CategoriaFormData {
  nome: string;
  descricao: string;
}

/**
 * Componente de modal para criar/editar categoria.
 * Pequeno, focado e reutilizável.
 */
@Component({
  selector: 'app-categoria-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, BaseModalComponent],
  templateUrl: './categoria-modal.component.html',
  styleUrl: './categoria-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriaModalComponent {
  private readonly categoriaService = inject(CategoriaService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly aberto = input<boolean>(false);
  readonly categoria = input<any>(null); // Categoria sendo editada

  readonly onFechar = output<void>();
  readonly onSucesso = output<void>();

  // Composable para formulário
  readonly formulario = useFormulario<CategoriaFormData>(
    { nome: '', descricao: '' },
    {
      nome: [Validators.required, Validators.minLength(2)],
    }
  );

  constructor() {
    // Effects devem estar no injection context (constructor)
    if (this.isBrowser) {
      // Effect para inicializar formulário quando categoria ou aberto mudar
      effect(() => {
        const estaAberto = this.aberto();
        const categoriaAtual = this.categoria();

        if (estaAberto) {
          // Usar setTimeout para garantir que executa após a renderização
          setTimeout(() => {
            this.inicializarFormulario();
            this.cdr.markForCheck();
          }, 0);
        }
      });
    }
  }

  private inicializarFormulario(): void {
    const categoriaEdit = this.categoria();

    if (categoriaEdit && categoriaEdit.id) {
      // Edição: preencher com dados da categoria
      this.formulario.definirValores({
        nome: categoriaEdit.nome || '',
        descricao: categoriaEdit.descricao || '',
      });
    } else {
      // Criação: resetar formulário
      this.formulario.resetar();
    }
  }

  fechar(): void {
    this.formulario.resetar();
    this.onFechar.emit();
  }

  salvar(): void {
    if (!this.formulario.valido()) {
      this.formulario.marcarTodosComoTocados();
      return;
    }

    this.formulario.enviando.set(true);
    this.formulario.limparErros();

    const valores = this.formulario.obterValores();
    const categoria = this.categoria();

    const request$ =
      categoria && categoria.id
        ? this.categoriaService.atualizar(categoria.id, {
            nome: valores.nome.trim(),
            descricao: valores.descricao?.trim() || '',
          })
        : this.categoriaService.criar({
            nome: valores.nome.trim(),
            descricao: valores.descricao?.trim() || '',
          });

    request$
      .pipe(
        catchError(error => {
          const mensagem = error.error?.message || 'Erro ao salvar categoria';
          this.formulario.adicionarErro('nome', mensagem);
          return of(null);
        }),
        finalize(() => {
          this.formulario.enviando.set(false);
        })
      )
      .subscribe(resultado => {
        if (resultado) {
          this.formulario.sucesso.set(true);
          setTimeout(() => {
            this.onSucesso.emit();
            this.fechar();
          }, 500);
        }
      });
  }

  obterTitulo(): string {
    return this.categoria() ? '✏️ Editar Categoria' : '➕ Nova Categoria';
  }
}

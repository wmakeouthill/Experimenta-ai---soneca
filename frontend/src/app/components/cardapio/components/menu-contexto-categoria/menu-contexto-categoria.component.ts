import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Categoria } from '../../../../services/categoria.service';

@Component({
  selector: 'app-menu-contexto-categoria',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './menu-contexto-categoria.component.html',
  styleUrl: './menu-contexto-categoria.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuContextoCategoriaComponent {
  readonly aberto = input.required<boolean>();
  readonly posicao = input<{ x: number; y: number }>({ x: 0, y: 0 });
  readonly categoria = input<Categoria | null>(null);

  readonly onFechar = output<void>();
  readonly onEditar = output<Categoria>();
  readonly onRemover = output<Categoria>();

  editar(): void {
    const cat = this.categoria();
    if (cat) {
      this.onEditar.emit(cat);
    }
    this.fechar();
  }

  remover(): void {
    const cat = this.categoria();
    if (cat) {
      this.onRemover.emit(cat);
    }
    this.fechar();
  }

  fechar(): void {
    this.onFechar.emit();
  }
}

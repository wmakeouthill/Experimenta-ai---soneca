import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LobbyPiso, LobbyTema } from '../../models/lobby-ui.types';

@Component({
  selector: 'app-lobby-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyHeaderComponent {
  readonly isModoGestor = input<boolean>(false);
  readonly podeConfigurarAnimacao = input<boolean>(true);
  readonly horaAtual = input<string>('00:00');
  readonly tema = input<LobbyTema>('escuro');
  readonly piso = input<LobbyPiso>('terreo');
  readonly onTrocarModo = output<void>();
  readonly onAnimacaoManual = output<void>();
  readonly onAbrirConfig = output<void>();
  readonly onToggleTema = output<void>();
  readonly onTogglePiso = output<void>();

  readonly menuAberto = signal(false);

  readonly rotuloPiso = computed(() => (this.piso() === 'terreo' ? 'Térreo' : '1º Andar'));

  readonly rotuloToggleTema = computed(() =>
    this.tema() === 'claro' ? 'Modo escuro' : 'Modo claro'
  );

  toggleMenu(): void {
    this.menuAberto.update((v) => !v);
  }

  fecharMenu(): void {
    this.menuAberto.set(false);
  }

  handleTrocarModo(): void {
    this.onTrocarModo.emit();
  }

  handleAnimacaoManual(): void {
    this.onAnimacaoManual.emit();
  }

  handleAbrirConfig(): void {
    this.onAbrirConfig.emit();
  }

  handleToggleTema(): void {
    this.onToggleTema.emit();
  }

  handleTogglePiso(): void {
    this.onTogglePiso.emit();
  }
}

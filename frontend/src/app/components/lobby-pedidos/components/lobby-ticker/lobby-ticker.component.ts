import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-lobby-ticker',
  standalone: true,
  templateUrl: './lobby-ticker.component.html',
  styleUrl: './lobby-ticker.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyTickerComponent {
  readonly texto = input<string>(
    'Experimenta aí do Soneca — Peça pelo app, acompanhe aqui e retire com praticidade!'
  );
}

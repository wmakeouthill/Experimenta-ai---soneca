import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-titulo-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './titulo-home.component.html',
  styleUrl: './titulo-home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TituloHomeComponent {
}


import { Component, input, output, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UploadUtil } from '../../../../utils/upload.util';
import { LobbyReelItem } from '../../models/lobby-ui.types';
import {
  MAX_LOBBY_REELS,
  MIN_LOBBY_REELS,
  REELS_PADRAO,
  criarReelVazio,
  normalizarReels,
} from '../../utils/lobby-promocoes.util';

export interface ConfigAnimacao {
  animacaoAtivada: boolean;
  intervaloAnimacao: number;
  duracaoAnimacao: number;
  reels: LobbyReelItem[];
  video1Url?: string | null;
  video2Url?: string | null;
}

@Component({
  selector: 'app-config-animacao-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './config-animacao-modal.component.html',
  styleUrl: './config-animacao-modal.component.css',
})
export class ConfigAnimacaoModalComponent {
  readonly aberto = input<boolean>(false);
  readonly configInicial = input<ConfigAnimacao>({
    animacaoAtivada: true,
    intervaloAnimacao: 30,
    duracaoAnimacao: 4,
    reels: REELS_PADRAO,
  });

  readonly onSalvar = output<ConfigAnimacao>();
  readonly onFechar = output<void>();

  readonly animacaoAtivada = signal<boolean>(true);
  readonly intervaloAnimacao = signal<number>(30);
  readonly duracaoAnimacao = signal<number>(4);
  readonly reels = signal<LobbyReelItem[]>([...REELS_PADRAO]);
  readonly carregandoVideoId = signal<string | null>(null);

  readonly maxReels = MAX_LOBBY_REELS;
  readonly minReels = MIN_LOBBY_REELS;

  readonly configAtual = computed(() => ({
    animacaoAtivada: this.animacaoAtivada(),
    intervaloAnimacao: this.intervaloAnimacao(),
    duracaoAnimacao: this.duracaoAnimacao(),
    reels: normalizarReels(this.reels()),
  }));

  readonly duracaoInterludioTotal = computed(() => {
    const qtd = this.reels().filter((r) => r.titulo.trim() || r.videoUrl).length || this.reels().length;
    return qtd * this.duracaoAnimacao();
  });

  readonly podeAdicionarReel = computed(() => this.reels().length < MAX_LOBBY_REELS);

  readonly podeRemoverReel = computed(() => this.reels().length > MIN_LOBBY_REELS);

  constructor() {
    effect(() => {
      const config = this.configInicial();
      this.animacaoAtivada.set(config.animacaoAtivada);
      this.intervaloAnimacao.set(config.intervaloAnimacao);
      this.duracaoAnimacao.set(config.duracaoAnimacao);
      this.reels.set(
        config.reels?.length
          ? config.reels.map((r) => ({ ...r }))
          : [...REELS_PADRAO]
      );
    }, { allowSignalWrites: true });
  }

  atualizarTitulo(id: string, titulo: string): void {
    this.reels.update((lista) =>
      lista.map((r) => (r.id === id ? { ...r, titulo } : r))
    );
  }

  onVideoSelected(id: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('video/')) {
      alert('Por favor, selecione um arquivo de vídeo válido');
      return;
    }

    if (file.size > 100 * 1024 * 1024) {
      alert('Vídeo muito grande. Tamanho máximo: 100MB');
      return;
    }

    this.carregandoVideoId.set(id);
    UploadUtil.fileParaBase64(file)
      .then((base64) => {
        this.reels.update((lista) =>
          lista.map((r) => (r.id === id ? { ...r, videoUrl: base64 } : r))
        );
        this.carregandoVideoId.set(null);
      })
      .catch((error: Error) => {
        alert('Erro ao processar vídeo: ' + error.message);
        this.carregandoVideoId.set(null);
      });

    input.value = '';
  }

  removerVideo(id: string): void {
    this.reels.update((lista) =>
      lista.map((r) => (r.id === id ? { ...r, videoUrl: null } : r))
    );
  }

  adicionarReel(): void {
    if (!this.podeAdicionarReel()) return;
    this.reels.update((lista) => [...lista, criarReelVazio()]);
  }

  removerReel(id: string): void {
    if (!this.podeRemoverReel()) return;
    this.reels.update((lista) => lista.filter((r) => r.id !== id));
  }

  estaCarregando(id: string): boolean {
    return this.carregandoVideoId() === id;
  }

  salvar(): void {
    const reelsValidos = this.reels().map((r) => ({
      ...r,
      titulo: r.titulo.trim(),
    }));

    const semConteudo = reelsValidos.every((r) => !r.titulo && !r.videoUrl);
    if (semConteudo) {
      alert('Adicione pelo menos um título ou vídeo em algum reel.');
      return;
    }

    this.onSalvar.emit({
      ...this.configAtual(),
      reels: reelsValidos,
    });
  }

  fechar(): void {
    this.onFechar.emit();
  }

  fecharOverlay(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fechar();
    }
  }
}

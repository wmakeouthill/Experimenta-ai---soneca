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
  estimarDuracaoInterludioSegundos,
  normalizarReels,
  tipoMidiaReel,
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

  readonly duracaoInterludioResumo = computed(() => {
    const est = estimarDuracaoInterludioSegundos(this.reels(), this.duracaoAnimacao());
    if (est.temVideo) {
      return `mín. ${est.minimo}s (vídeos usam a duração real de cada arquivo)`;
    }
    return `${est.minimo}s`;
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

  onMidiaSelected(id: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const eVideo = file.type.startsWith('video/');
    const eImagem = UploadUtil.eImagem(file);

    if (!eVideo && !eImagem) {
      alert('Selecione um vídeo ou imagem válido');
      return;
    }

    const limiteMb = eVideo ? 100 : 10;
    if (!UploadUtil.validarTamanho(file, limiteMb)) {
      alert(`Arquivo muito grande. Máximo: ${limiteMb}MB`);
      return;
    }

    this.carregandoVideoId.set(id);

    const processar = eImagem
      ? UploadUtil.redimensionarImagem(file, 1080, 1920, 0.85).then((f) => UploadUtil.fileParaBase64(f))
      : UploadUtil.fileParaBase64(file);

    processar
      .then((base64) => {
        this.reels.update((lista) =>
          lista.map((r) =>
            r.id === id
              ? eVideo
                ? { ...r, videoUrl: base64, imagemUrl: null }
                : { ...r, imagemUrl: base64, videoUrl: null }
              : r
          )
        );
        this.carregandoVideoId.set(null);
      })
      .catch((error: Error) => {
        alert('Erro ao processar arquivo: ' + error.message);
        this.carregandoVideoId.set(null);
      });

    input.value = '';
  }

  removerMidia(id: string): void {
    this.reels.update((lista) =>
      lista.map((r) => (r.id === id ? { ...r, videoUrl: null, imagemUrl: null } : r))
    );
  }

  tipoMidia(reel: LobbyReelItem): string {
    return tipoMidiaReel(reel);
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

    const semConteudo = reelsValidos.every((r) => !r.titulo && !r.videoUrl && !r.imagemUrl);
    if (semConteudo) {
      alert('Adicione pelo menos um título, imagem ou vídeo em algum reel.');
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

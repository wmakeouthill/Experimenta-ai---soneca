import { LobbyReelItem } from '../models/lobby-ui.types';

export const LOBBY_REELS_STORAGE_KEY = 'lobby-reels';
export const MAX_LOBBY_REELS = 5;
export const MIN_LOBBY_REELS = 1;

export const REELS_PADRAO: LobbyReelItem[] = [
  { id: 'padrao-1', titulo: 'Combo Duplo Bacon — só hoje', videoUrl: null },
  { id: 'padrao-2', titulo: 'Sobremesa grátis acima de R$ 60', videoUrl: null },
  { id: 'padrao-3', titulo: 'Novo: Milkshake de Paçoca', videoUrl: null },
];

export function criarIdReel(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `reel-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export function criarReelVazio(): LobbyReelItem {
  return { id: criarIdReel(), titulo: '', videoUrl: null };
}

export function normalizarReels(reels: LobbyReelItem[]): LobbyReelItem[] {
  const validos = reels.filter((r) => r.titulo.trim() || r.videoUrl);
  if (validos.length > 0) return validos.slice(0, MAX_LOBBY_REELS);
  return [...REELS_PADRAO];
}

export function migrarReelsDeApi(
  video1Url?: string | null,
  video2Url?: string | null,
  reels?: LobbyReelItem[] | null
): LobbyReelItem[] {
  if (reels && reels.length > 0) {
    return normalizarReels(reels);
  }

  const migrados: LobbyReelItem[] = [];
  if (video1Url) {
    migrados.push({ id: criarIdReel(), titulo: REELS_PADRAO[0].titulo, videoUrl: video1Url });
  }
  if (video2Url) {
    migrados.push({
      id: criarIdReel(),
      titulo: REELS_PADRAO[1]?.titulo ?? 'Promoção',
      videoUrl: video2Url,
    });
  }

  if (migrados.length > 0) return migrados;
  return [...REELS_PADRAO];
}

export function carregarReelsLocalStorage(): LobbyReelItem[] | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(LOBBY_REELS_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LobbyReelItem[];
    if (!Array.isArray(parsed) || parsed.length === 0) return null;
    return parsed.map((r) => ({
      id: r.id || criarIdReel(),
      titulo: r.titulo ?? '',
      videoUrl: r.videoUrl ?? null,
    }));
  } catch {
    return null;
  }
}

export function salvarReelsLocalStorage(reels: LobbyReelItem[]): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(LOBBY_REELS_STORAGE_KEY, JSON.stringify(reels));
}

export function reelsParaApiUrls(reels: LobbyReelItem[]): {
  video1Url: string | null;
  video2Url: string | null;
} {
  return {
    video1Url: reels[0]?.videoUrl ?? null,
    video2Url: reels[1]?.videoUrl ?? null,
  };
}

export function montarTickerTexto(reels: LobbyReelItem[]): string {
  const titulos = normalizarReels(reels)
    .map((r) => r.titulo.trim())
    .filter(Boolean);

  if (titulos.length === 0) {
    return 'Experimenta aí do Soneca — Peça pelo app, acompanhe aqui e retire com praticidade!';
  }

  return titulos.join('★');
}

export function reelsParaSlides(reels: LobbyReelItem[]): { titulo: string }[] {
  return normalizarReels(reels).map((r) => ({ titulo: r.titulo.trim() || 'Promoção' }));
}

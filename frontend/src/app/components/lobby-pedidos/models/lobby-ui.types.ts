export type LobbyTema = 'escuro' | 'claro';
export type LobbyPiso = 'terreo' | 'andar';

export interface LobbySlidePromo {
  titulo: string;
}

export interface LobbyReelItem {
  id: string;
  titulo: string;
  videoUrl: string | null;
}

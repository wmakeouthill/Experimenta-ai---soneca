export type LobbyTema = 'escuro' | 'claro';
export type LobbyPiso = 'terreo' | 'andar';

export interface LobbySlidePromo {
  titulo: string;
}

export type LobbyReelMidiaTipo = 'video' | 'imagem' | 'texto';

export interface LobbyReelItem {
  id: string;
  titulo: string;
  videoUrl: string | null;
  imagemUrl: string | null;
}

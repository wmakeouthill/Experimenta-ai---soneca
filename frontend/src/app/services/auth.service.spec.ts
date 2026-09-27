import { AuthService } from './auth.service';

/** JWT só com o payload que importa; assinatura irrelevante (quem valida é o backend). */
function jwt(payload: object): string {
  const base64url = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `cabecalho.${base64url}.assinatura`;
}

const agoraEmSegundos = () => Math.floor(Date.now() / 1000);

describe('AuthService.tokenExpirado', () => {
  it('token dentro da validade não está expirado', () => {
    expect(AuthService.tokenExpirado(jwt({ exp: agoraEmSegundos() + 3600, nome: 'Totem Balcão' }))).toBeFalse();
  });

  it('token com exp no passado está expirado', () => {
    expect(AuthService.tokenExpirado(jwt({ exp: agoraEmSegundos() - 1 }))).toBeTrue();
  });

  it('token sem exp ou ilegível conta como expirado', () => {
    expect(AuthService.tokenExpirado(jwt({ id: 'usr-1' }))).toBeTrue();
    expect(AuthService.tokenExpirado('nao-e-um-jwt')).toBeTrue();
  });
});

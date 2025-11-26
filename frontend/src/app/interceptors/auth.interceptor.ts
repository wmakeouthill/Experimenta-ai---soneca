import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor que adiciona o token JWT em todas as requisições HTTP
 * O token é obtido do localStorage e adicionado no header Authorization
 * 
 * NOTA: Não adiciona token em requisições para localhost (servidor local do Electron)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Não adiciona token em requisições locais (servidor Electron)
  // O servidor local não precisa de autenticação
  if (req.url.startsWith('http://localhost:') || req.url.startsWith('http://127.0.0.1:')) {
    return next(req);
  }

  const token = localStorage.getItem('token');

  if (token) {
    // Remove espaços e quebras de linha do token (caso tenha sido salvo incorretamente)
    const tokenLimpo = token.trim();
    
    if (tokenLimpo) {
      const cloned = req.clone({
        setHeaders: {
          Authorization: `Bearer ${tokenLimpo}`
        }
      });
      
      // Log apenas em desenvolvimento para debug
      if (process.env['NODE_ENV'] !== 'production') {
        console.debug('Token JWT adicionado à requisição:', req.url);
      }
      
      return next(cloned);
    } else {
      console.warn('Token encontrado no localStorage mas está vazio');
    }
  } else {
    // Log apenas em desenvolvimento
    if (process.env['NODE_ENV'] !== 'production') {
      console.debug('Nenhum token encontrado no localStorage para:', req.url);
    }
  }

  return next(req);
};


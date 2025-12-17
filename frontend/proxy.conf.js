// Proxy dinâmico que funciona tanto dentro do Docker quanto localmente
const PROXY_CONFIG = {
  "/api": {
    // Tenta usar o nome do serviço Docker primeiro, depois localhost
    // O Angular proxy tentará backend-dev primeiro, e se falhar, pode usar localhost
    // Mas na verdade, precisamos de uma lógica mais inteligente
    "target": process.env.BACKEND_URL || "http://backend-dev:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info",
    // Permite que o proxy funcione mesmo se o host não for resolvido imediatamente
    "timeout": 30000
  }
};

// Se estiver rodando localmente (não dentro do Docker), usa localhost
// Detecta se está dentro do Docker verificando se o hostname backend-dev é acessível
// Mas isso é assíncrono, então vamos usar uma variável de ambiente
if (process.env.NODE_ENV === 'development' && !process.env.DOCKER_ENV) {
  // Se não estiver no Docker, tenta localhost
  PROXY_CONFIG["/api"].target = "http://localhost:8080";
}

module.exports = PROXY_CONFIG;

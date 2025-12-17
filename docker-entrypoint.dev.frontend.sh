#!/bin/bash
set -e

echo "🚀 Iniciando frontend em modo desenvolvimento..."

# Garantir que estamos no diretório correto
cd /app/frontend

# Verificar se node_modules existe e tem conteúdo básico
if [ ! -d "node_modules" ] || [ ! -d "node_modules/@angular" ]; then
    echo "📦 Instalando dependências do npm..."
    npm install --legacy-peer-deps
else
    echo "✅ Dependências já instaladas"
fi

echo "🔥 Iniciando Angular Dev Server com hot-reload..."
echo "🌐 Frontend disponível em: http://localhost:4200"
echo "📝 Mudanças serão refletidas automaticamente no navegador"

# Executar ng serve
exec npm start

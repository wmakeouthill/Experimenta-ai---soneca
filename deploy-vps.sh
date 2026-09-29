#!/bin/bash
# ============================================================
# Script de Deploy Completo - VPS (KingHost / Hostinger / etc.)
# Snackbar System (Experimenta aí - Soneca)
# ============================================================
# Uso: ./deploy-vps.sh [primeiro-deploy|atualizar|ssl]
# ============================================================

set -euo pipefail

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date +'%H:%M:%S')]${NC} $1"; }
warn() { echo -e "${YELLOW}[$(date +'%H:%M:%S')] ⚠️  $1${NC}"; }
error() { echo -e "${RED}[$(date +'%H:%M:%S')] ❌ $1${NC}"; exit 1; }

# ==================== VALIDAÇÕES ====================
check_env() {
    if [ ! -f .env.prod ]; then
        error "Arquivo .env.prod não encontrado! Copie de .env.prod.example e configure."
    fi
    
    source .env.prod
    
    if [ "${DB_ROOT_PASSWORD}" = "TROCAR_SENHA_ROOT_FORTE_AQUI" ] || \
       [ "${DB_PASSWORD}" = "TROCAR_SENHA_USER_FORTE_AQUI" ] || \
       [ "${JWT_SECRET}" = "TROCAR_JWT_SECRET_MINIMO_256_BITS" ]; then
        error "Configure as senhas no .env.prod! Valores padrão detectados."
    fi
    
    log "✅ Variáveis de ambiente validadas"
}

preparar_frontend() {
    mkdir -p releases/electron/balcao releases/electron/totem
    if [ -n "${DOMAIN:-}" ] && [ -f "config/certbot/conf/live/${DOMAIN}/fullchain.pem" ]; then
        export DOMAIN
        envsubst '${DOMAIN}' < config/nginx/default.conf.template > config/nginx/default.conf
    fi
}

# O preparar_frontend regenera o default.conf e a VPS dá chmod +x neste script: sem descartar, o pull aborta
puxar_main() {
    git checkout -- config/nginx/default.conf deploy-vps.sh
    git pull origin main
}

# ==================== LOGIN GHCR ====================
login_ghcr() {
    source .env.prod
    local ghcr_user="${GHCR_USER:-wmakeouthill}"
    
    # Verificar se já está logado
    if docker pull "ghcr.io/${ghcr_user}/snackbar-backend:latest" > /dev/null 2>&1; then
        log "✅ Registry acessível"
        return 0
    fi
    
    # Se o repositório for público, não precisa de login
    # Se for privado, tenta login
    if [ -n "${GHCR_TOKEN:-}" ]; then
        echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${ghcr_user}" --password-stdin
        log "✅ Login no GHCR realizado"
    else
        warn "Se as imagens forem privadas, defina GHCR_TOKEN no .env.prod"
        warn "Ou faça login manual: docker login ghcr.io -u ${ghcr_user}"
    fi
}

# ==================== SETUP INICIAL DA VPS ====================
setup_vps() {
    log "🔧 Configurando VPS..."
    
    # Atualizar sistema
    sudo apt update && sudo apt upgrade -y
    
    # Instalar Docker
    if ! command -v docker &> /dev/null; then
        log "📦 Instalando Docker..."
        curl -fsSL https://get.docker.com | sh
        sudo usermod -aG docker $USER
        sudo systemctl enable docker
        sudo systemctl start docker
        log "✅ Docker instalado"
    else
        log "✅ Docker já instalado: $(docker --version)"
    fi
    
    # Instalar Docker Compose
    if ! command -v docker compose &> /dev/null; then
        log "📦 Instalando Docker Compose plugin..."
        sudo apt install -y docker-compose-plugin
        log "✅ Docker Compose instalado"
    else
        log "✅ Docker Compose já instalado"
    fi
    
    # Instalar ferramentas necessárias
    sudo apt install -y git ufw fail2ban curl wget unzip
    
    # ==================== FIREWALL ====================
    log "🔒 Configurando firewall..."
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    sudo ufw allow ssh
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    # NÃO abrir porta 3306 (MySQL só local via Docker)
    sudo ufw --force enable
    log "✅ Firewall configurado (SSH, HTTP, HTTPS)"
    
    # ==================== FAIL2BAN ====================
    log "🔒 Configurando Fail2Ban..."
    sudo tee /etc/fail2ban/jail.local > /dev/null <<'JAIL'
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5

[sshd]
enabled = true
port = ssh
filter = sshd
logpath = /var/log/auth.log
maxretry = 3
bantime = 7200
JAIL
    sudo systemctl enable fail2ban
    sudo systemctl restart fail2ban
    log "✅ Fail2Ban configurado"
    
    # ==================== SWAP (importante para VPS com pouca RAM) ====================
    if [ ! -f /swapfile ]; then
        log "💾 Criando swap de 2GB..."
        sudo fallocate -l 2G /swapfile
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
        sudo swapon /swapfile
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
        # Otimizar swap
        sudo sysctl vm.swappiness=10
        echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
        log "✅ Swap de 2GB criado"
    else
        log "✅ Swap já configurado"
    fi
    
    # ==================== SEGURANÇA SSH ====================
    log "🔒 Endurecendo SSH..."
    sudo sed -i 's/#PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
    sudo sed -i 's/PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
    sudo sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config
    sudo systemctl restart sshd
    log "✅ SSH endurecido"
    
    # ==================== LIMITES DO SISTEMA ====================
    log "⚙️  Otimizando limites do sistema..."
    sudo tee /etc/sysctl.d/99-docker-tune.conf > /dev/null <<'SYSCTL'
# Rede
net.core.somaxconn = 1024
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 300
net.ipv4.ip_forward = 1

# Memória
vm.overcommit_memory = 1
vm.swappiness = 10
SYSCTL
    sudo sysctl --system > /dev/null 2>&1
    log "✅ Sistema otimizado"
    
    # Criar diretórios necessários
    mkdir -p config/certbot/conf config/certbot/www
    preparar_frontend
    
    log "🎉 Setup da VPS completo! Faça logout e login novamente para o grupo docker funcionar."
}

# ==================== PRIMEIRO DEPLOY ====================
primeiro_deploy() {
    check_env
    
    log "🚀 Iniciando primeiro deploy..."
    
    # Criar diretórios
    mkdir -p config/certbot/conf config/certbot/www
    preparar_frontend
    
    # Criar link simbólico para .env
    ln -sf .env.prod .env
    
    # Login no GitHub Container Registry (se necessário)
    login_ghcr
    
    # Baixar imagens pré-buildadas
    log "📥 Baixando imagens do registry..."
    docker compose -f docker-compose.prod.yml pull
    
    log "🚀 Subindo containers..."
    docker compose -f docker-compose.prod.yml up -d mysql
    
    # Aguardar MySQL
    log "⏳ Aguardando MySQL ficar saudável..."
    sleep 30
    
    # Subir backend
    docker compose -f docker-compose.prod.yml up -d backend
    log "⏳ Aguardando backend inicializar..."
    sleep 60
    
    # Subir frontend
    docker compose -f docker-compose.prod.yml up -d frontend
    
    log "✅ Deploy inicial completo!"
    log "📋 Verifique os logs: docker compose -f docker-compose.prod.yml logs -f"
    log ""
    log "🔐 Para configurar SSL, execute: ./deploy-vps.sh ssl"
}

# ==================== CONFIGURAR SSL ====================
configurar_ssl() {
    source .env.prod
    
    if [ -z "${DOMAIN:-}" ] || [ "${DOMAIN}" = "seu-dominio.com.br" ]; then
        error "Configure a variável DOMAIN no .env.prod com seu domínio real!"
    fi
    
    log "🔐 Configurando SSL para ${DOMAIN}..."
    
    # Certificado Let's Encrypt
    docker compose -f docker-compose.prod.yml run --rm certbot certonly \
        --webroot \
        --webroot-path=/var/www/certbot \
        -d "${DOMAIN}" \
        -d "www.${DOMAIN}" \
        --email "admin@${DOMAIN}" \
        --agree-tos \
        --no-eff-email
    
    # Substituir config Nginx para HTTPS
    log "📝 Ativando configuração HTTPS no Nginx..."
    preparar_frontend
    
    # Reiniciar Nginx
    docker compose -f docker-compose.prod.yml restart frontend
    
    # Subir certbot para renovação automática
    docker compose -f docker-compose.prod.yml up -d certbot
    
    log "✅ SSL configurado com sucesso!"
    log "🌐 Acesse: https://${DOMAIN}"
}

# ==================== ATUALIZAR DEPLOY ====================
atualizar() {
    check_env
    ln -sf .env.prod .env
    
    log "🔄 Atualizando aplicação..."
    
    # Pull do código mais recente (configs, nginx, etc.)
    puxar_main
    preparar_frontend
    
    # Login no registry
    login_ghcr
    
    # Baixar imagens novas
    log "📥 Baixando imagens atualizadas..."
    docker compose -f docker-compose.prod.yml pull backend frontend
    
    # Restart com imagens novas (sem downtime)
    log "🚀 Reiniciando com imagens novas..."
    docker compose -f docker-compose.prod.yml up -d --no-deps backend
    docker compose -f docker-compose.prod.yml up -d --no-deps frontend
    
    # Limpar imagens antigas
    docker image prune -f
    
    log "✅ Atualização completa!"
}

# ==================== ATUALIZAR APENAS BACKEND ====================
atualizar_backend() {
    check_env
    ln -sf .env.prod .env
    
    log "🔄 Atualizando apenas o BACKEND..."
    
    puxar_main
    login_ghcr
    
    log "📥 Baixando imagem do backend..."
    docker compose -f docker-compose.prod.yml pull backend
    
    log "🚀 Reiniciando backend (MySQL e Frontend não serão afetados)..."
    docker compose -f docker-compose.prod.yml up -d --no-deps backend
    
    docker image prune -f
    
    log "✅ Backend atualizado!"
    log "📋 Verifique: docker compose -f docker-compose.prod.yml logs -f backend"
}

# ==================== ATUALIZAR APENAS FRONTEND ====================
atualizar_frontend() {
    check_env
    ln -sf .env.prod .env
    
    log "🔄 Atualizando apenas o FRONTEND..."
    
    puxar_main
    preparar_frontend
    login_ghcr
    
    log "📥 Baixando imagem do frontend..."
    docker compose -f docker-compose.prod.yml pull frontend
    
    log "🚀 Reiniciando frontend (MySQL e Backend não serão afetados)..."
    docker compose -f docker-compose.prod.yml up -d --no-deps frontend
    
    docker image prune -f
    
    log "✅ Frontend atualizado!"
    log "📋 Verifique: docker compose -f docker-compose.prod.yml logs -f frontend"
}

# ==================== BACKUP ====================
backup() {
    source .env.prod
    BACKUP_DIR="./backups/$(date +'%Y-%m-%d_%H%M%S')"
    mkdir -p "$BACKUP_DIR"
    
    log "💾 Criando backup..."
    docker compose -f docker-compose.prod.yml exec -T mysql \
        mysqldump -u root -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" \
        --single-transaction --routines --triggers \
        > "${BACKUP_DIR}/${DB_NAME}.sql"
    
    # Comprimir
    gzip "${BACKUP_DIR}/${DB_NAME}.sql"
    
    # Manter apenas últimos 7 backups
    ls -dt ./backups/*/ | tail -n +8 | xargs rm -rf 2>/dev/null || true
    
    log "✅ Backup salvo em ${BACKUP_DIR}"
}

# ==================== STATUS ====================
status() {
    log "📋 Status dos containers:"
    docker compose -f docker-compose.prod.yml ps
    echo ""
    log "📊 Uso de recursos:"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"
}

# ==================== LOGS ====================
logs() {
    docker compose -f docker-compose.prod.yml logs -f --tail=100
}

# ==================== MENU ====================
case "${1:-}" in
    setup)
        setup_vps
        ;;
    primeiro-deploy)
        primeiro_deploy
        ;;
    ssl)
        configurar_ssl
        ;;
    atualizar)
        atualizar
        ;;
    atualizar-backend)
        atualizar_backend
        ;;
    atualizar-frontend)
        atualizar_frontend
        ;;
    backup)
        backup
        ;;
    status)
        status
        ;;
    logs)
        logs
        ;;
    stop)
        docker compose -f docker-compose.prod.yml down
        log "✅ Containers parados"
        ;;
    restart)
        docker compose -f docker-compose.prod.yml restart
        log "✅ Containers reiniciados"
        ;;
    *)
        echo ""
        echo "============================================"
        echo "  Deploy VPS - Snackbar System"
        echo "============================================"
        echo ""
        echo "Uso: $0 <comando>"
        echo ""
        echo "Comandos:"
        echo "  setup              Configurar VPS (primeira vez)"
        echo "  primeiro-deploy    Fazer o primeiro deploy"
        echo "  ssl                Configurar SSL (Let's Encrypt)"
        echo "  atualizar          Atualizar backend + frontend"
        echo "  atualizar-backend  Atualizar APENAS o backend"
        echo "  atualizar-frontend Atualizar APENAS o frontend"
        echo "  backup             Fazer backup do banco"
        echo "  status             Ver status dos containers"
        echo "  logs               Ver logs em tempo real"
        echo "  stop               Parar todos os containers"
        echo "  restart            Reiniciar containers"
        echo ""
        ;;
esac

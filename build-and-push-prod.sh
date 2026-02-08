#!/bin/bash
# ============================================================
# Build Local + Push para GitHub Container Registry
# Roda na sua máquina de desenvolvimento (Linux/Mac)
# ============================================================
# Uso: ./build-and-push-prod.sh [TAG]
# ============================================================

set -euo pipefail

TAG="${1:-latest}"
GHCR_USER="${GHCR_USER:-wmakeouthill}"
REGISTRY="ghcr.io/${GHCR_USER}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  Build & Push - Snackbar System${NC}"
echo -e "${CYAN}  Registry: ${REGISTRY}${NC}"
echo -e "${CYAN}  Tag: ${TAG}${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# ==================== BUILD BACKEND ====================
echo -e "${YELLOW}[1/4] Building Backend...${NC}"
BACKEND_IMAGE="${REGISTRY}/snackbar-backend"

docker build \
    -f Dockerfile.prod.backend \
    -t "${BACKEND_IMAGE}:${TAG}" \
    -t "${BACKEND_IMAGE}:latest" \
    .

echo -e "${GREEN}  Backend OK${NC}"

# ==================== BUILD FRONTEND ====================
echo -e "${YELLOW}[2/4] Building Frontend...${NC}"
FRONTEND_IMAGE="${REGISTRY}/snackbar-frontend"

docker build \
    -f Dockerfile.prod.frontend \
    -t "${FRONTEND_IMAGE}:${TAG}" \
    -t "${FRONTEND_IMAGE}:latest" \
    .

echo -e "${GREEN}  Frontend OK${NC}"

# ==================== PUSH ====================
echo -e "${YELLOW}[3/4] Pushing para ${REGISTRY}...${NC}"

docker push "${BACKEND_IMAGE}:${TAG}"
docker push "${BACKEND_IMAGE}:latest"
docker push "${FRONTEND_IMAGE}:${TAG}"
docker push "${FRONTEND_IMAGE}:latest"

echo -e "${GREEN}  Push OK${NC}"

# ==================== RESUMO ====================
echo ""
echo -e "${GREEN}[4/4] Concluído!${NC}"
echo -e "${CYAN}============================================${NC}"
echo -e "  Imagens: ${BACKEND_IMAGE}:${TAG}"
echo -e "           ${FRONTEND_IMAGE}:${TAG}"
echo -e ""
echo -e "  Na VPS: ./deploy-vps.sh atualizar"
echo -e "${CYAN}============================================${NC}"

docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | grep snackbar

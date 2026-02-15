# ============================================================
# Build Local + Push para GitHub Container Registry
# Roda na sua máquina de desenvolvimento (Windows)
# ============================================================
# Uso: .\build-and-push-prod.ps1 [-Tag "v1.0.0"]
# ============================================================

param(
    [string]$Tag = "latest",
    [string]$GhcrUser = "wmakeouthill"
)

$ErrorActionPreference = "Stop"
$Registry = "ghcr.io/$GhcrUser"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Build & Push - Snackbar System" -ForegroundColor Cyan
Write-Host "  Registry: $Registry" -ForegroundColor Cyan
Write-Host "  Tag: $Tag" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ==================== VERIFICAÇÕES ====================
Write-Host "[1/6] Verificando Docker..." -ForegroundColor Yellow
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker nao encontrado! Instale o Docker Desktop." -ForegroundColor Red
    exit 1
}
Write-Host "  Docker OK" -ForegroundColor Green

# ==================== LOGIN NO GHCR ====================
Write-Host "[2/6] Verificando login no GitHub Container Registry..." -ForegroundColor Yellow
Write-Host "  Se nao estiver logado, execute:" -ForegroundColor Gray
Write-Host "  echo SEU_TOKEN | docker login ghcr.io -u $GhcrUser --password-stdin" -ForegroundColor Gray
Write-Host ""

# Testar se esta logado verificando o config do Docker
$dockerConfig = "$env:USERPROFILE\.docker\config.json"
$loggedIn = $false
if (Test-Path $dockerConfig) {
    $configContent = Get-Content $dockerConfig -Raw
    if ($configContent -match "ghcr\.io") {
        $loggedIn = $true
    }
}
if (-not $loggedIn) {
    Write-Host "  Nao esta logado! Faca login primeiro:" -ForegroundColor Red
    Write-Host "  1. Crie um Personal Access Token em: https://github.com/settings/tokens" -ForegroundColor Yellow
    Write-Host "     Permissoes: write:packages, read:packages, delete:packages" -ForegroundColor Yellow
    Write-Host "  2. Execute: echo SEU_TOKEN | docker login ghcr.io -u $GhcrUser --password-stdin" -ForegroundColor Yellow
    exit 1
}
Write-Host "  Login OK" -ForegroundColor Green

# ==================== BUILD BACKEND ====================
Write-Host "[3/6] Building Backend (Maven + JRE)..." -ForegroundColor Yellow
$backendImage = "$Registry/snackbar-backend"

docker build `
    -f Dockerfile.prod.backend `
    -t "${backendImage}:${Tag}" `
    -t "${backendImage}:latest" `
    .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha no build do backend!" -ForegroundColor Red
    exit 1
}
Write-Host "  Backend build OK" -ForegroundColor Green

# ==================== BUILD FRONTEND ====================
Write-Host "[4/6] Building Frontend (Angular + Nginx)..." -ForegroundColor Yellow
$frontendImage = "$Registry/snackbar-frontend"

docker build `
    -f Dockerfile.prod.frontend `
    -t "${frontendImage}:${Tag}" `
    -t "${frontendImage}:latest" `
    .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha no build do frontend!" -ForegroundColor Red
    exit 1
}
Write-Host "  Frontend build OK" -ForegroundColor Green

# ==================== PUSH ====================
Write-Host "[5/6] Pushing imagens para $Registry..." -ForegroundColor Yellow

docker push "${backendImage}:${Tag}"
docker push "${backendImage}:latest"
docker push "${frontendImage}:${Tag}"
docker push "${frontendImage}:latest"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha no push!" -ForegroundColor Red
    exit 1
}
Write-Host "  Push OK" -ForegroundColor Green

# ==================== RESUMO ====================
Write-Host ""
Write-Host "[6/6] Concluido!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Imagens publicadas:" -ForegroundColor Cyan
Write-Host "    ${backendImage}:${Tag}" -ForegroundColor White
Write-Host "    ${frontendImage}:${Tag}" -ForegroundColor White
Write-Host "" -ForegroundColor Cyan
Write-Host "  Na VPS, execute:" -ForegroundColor Cyan
Write-Host "    ./deploy-vps.sh atualizar" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan

# Mostrar tamanho das imagens
Write-Host ""
Write-Host "Tamanho das imagens:" -ForegroundColor Yellow
docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | Select-String "snackbar"

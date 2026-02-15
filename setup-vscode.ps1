# Script para instalar dependencias de linting do frontend
# Execute este script no diretorio raiz do projeto

Write-Host "Configurando ambiente de desenvolvimento..." -ForegroundColor Cyan
Write-Host ""

# Verifica se o Node.js esta instalado
try {
    $nodeVersion = node --version
    Write-Host "Node.js encontrado: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "Node.js nao encontrado. Por favor, instale o Node.js primeiro." -ForegroundColor Red
    exit 1
}

# Navega para o diretorio frontend
$frontendPath = Join-Path $PSScriptRoot "frontend"
if (-not (Test-Path $frontendPath)) {
    Write-Host "Diretorio 'frontend' nao encontrado." -ForegroundColor Red
    exit 1
}

Write-Host "Navegando para: $frontendPath" -ForegroundColor Yellow
Set-Location $frontendPath

# Instala dependencias principais se necessario
if (-not (Test-Path "node_modules")) {
    Write-Host "Instalando dependencias principais..." -ForegroundColor Yellow
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Erro ao instalar dependencias principais." -ForegroundColor Red
        exit 1
    }
}

# Instala dependencias de linting
Write-Host ""
Write-Host "Instalando ferramentas de linting..." -ForegroundColor Yellow

npm install --save-dev eslint `
    @typescript-eslint/parser `
    @typescript-eslint/eslint-plugin `
    @angular-eslint/eslint-plugin `
    @angular-eslint/eslint-plugin-template `
    @angular-eslint/template-parser `
    prettier `
    eslint-config-prettier

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Dependencias de linting instaladas com sucesso!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Erro ao instalar dependencias de linting." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Configuracao concluida!" -ForegroundColor Cyan
Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor Yellow
Write-Host "   1. Instale as extensoes recomendadas do VS Code" -ForegroundColor White
Write-Host "   2. Recarregue o VS Code (Ctrl+Shift+P -> 'Reload Window')" -ForegroundColor White
Write-Host "   3. Execute 'npm run lint' para verificar o codigo" -ForegroundColor White
Write-Host ""

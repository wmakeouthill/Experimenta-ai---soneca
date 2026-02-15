# Script para limpar cache do Maven e revalidar projeto
Write-Host "Limpando cache do Maven..." -ForegroundColor Yellow

# Remove cache corrompido do Spring Boot
$m2Path = "$env:USERPROFILE\.m2\repository\org\springframework\boot"
if (Test-Path $m2Path) {
    Write-Host "Removendo cache do Spring Boot..." -ForegroundColor Yellow
    Remove-Item -Path $m2Path -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Cache removido!" -ForegroundColor Green
}

Write-Host ""
Write-Host "Atualizando dependencias Maven..." -ForegroundColor Yellow
cd "D:\Experimenta-ai-soneca-apps\Experimenta ai - soneca"
mvn clean dependency:purge-local-repository -U

Write-Host ""
Write-Host "Validando projeto..." -ForegroundColor Yellow
mvn validate

Write-Host ""
Write-Host "Concluido! Recarregue o VS Code (Ctrl+Shift+P -> 'Reload Window')" -ForegroundColor Green

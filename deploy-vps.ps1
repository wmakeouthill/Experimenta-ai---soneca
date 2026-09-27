<#
.SYNOPSIS
    Deploy de produção num comando: builda as imagens aqui, publica no GHCR e atualiza a VPS.

.DESCRIPTION
    O GitHub Actions não builda no push (economia de minutos). Este script:
      1. confere que o código local é exatamente o origin/main (a VPS faz git pull de main);
      2. builda e publica backend e frontend no GHCR com as tags <commit> e latest (build-and-push-prod.ps1);
      3. na VPS: marca as imagens em uso como :anterior, faz backup do banco e roda deploy-vps.sh atualizar;
      4. espera backend e frontend ficarem healthy.
    A VPS baixa do GHCR só as camadas que mudaram.

.EXAMPLE
    .\deploy-vps.ps1 -Vps deploy@<ip da VPS>
#>
param(
    [Parameter(Mandatory)] [string]$Vps,
    [string]$PastaDeploy = '~/snackbar',
    [string]$ChaveSsh = "$HOME\.ssh\kinghost_deploy_ed25519"
)
$ErrorActionPreference = 'Stop'

function Invocar([string]$Programa, [string[]]$Argumentos) {
    & $Programa @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "$Programa falhou (código $LASTEXITCODE)" }
}

Push-Location $PSScriptRoot
try {
    # 1. A imagem tem de corresponder ao main que a VPS vai puxar (compose, nginx, scripts)
    Invocar git @('fetch', '--quiet', 'origin', 'main')
    $pendentes = git status --porcelain
    if ($pendentes) { throw "Há mudanças não commitadas (o build usa o disco, a VPS usa o main):`n$($pendentes -join "`n")" }
    $commit = git rev-parse --short HEAD
    if ((git rev-parse HEAD) -ne (git rev-parse origin/main)) {
        throw "HEAD ($commit) não é o origin/main: faça checkout do main atualizado (e push) antes do deploy."
    }

    # 2. Build e push
    & "$PSScriptRoot\build-and-push-prod.ps1" -Tag $commit
    if ($LASTEXITCODE -ne 0) { throw 'Build ou push das imagens falhou.' }

    # 3. VPS. Sem aspas duplas nos comandos remotos: o PowerShell 5.1 não as escapa ao chamar programas nativos
    $opcoesSsh = @('-i', $ChaveSsh, '-o', 'IdentitiesOnly=yes')
    $atualizar = @(
        'set -e'
        "cd $PastaDeploy"
        # Rollback: TAG=anterior docker compose -f docker-compose.prod.yml up -d --no-deps backend frontend
        'for s in backend frontend; do docker tag $(docker inspect -f ''{{.Image}}'' snackbar-$s) ghcr.io/wmakeouthill/snackbar-${s}:anterior; done'
        'bash ./deploy-vps.sh backup'
        'bash ./deploy-vps.sh atualizar'
    ) -join '; '
    Invocar ssh ($opcoesSsh + @($Vps, $atualizar))

    # 4. Healthcheck do compose: backend leva ~2 min para subir; desiste em 5 min ou no primeiro unhealthy
    $esperar = 'for i in $(seq 1 30); do b=$(docker inspect -f ''{{.State.Health.Status}}'' snackbar-backend); f=$(docker inspect -f ''{{.State.Health.Status}}'' snackbar-frontend); echo backend=$b frontend=$f; [ $b = healthy ] && [ $f = healthy ] && exit 0; [ $b = unhealthy ] || [ $f = unhealthy ] && exit 1; sleep 10; done; exit 1'
    $argumentos = $opcoesSsh + @($Vps, $esperar)
    & ssh @argumentos
    if ($LASTEXITCODE -ne 0) {
        throw "Containers não ficaram healthy. Logs: docker logs --tail 100 snackbar-backend. Rollback na VPS ($PastaDeploy): TAG=anterior docker compose -f docker-compose.prod.yml up -d --no-deps backend frontend (dump em backups/)."
    }
    Write-Host "Deploy de $commit concluído." -ForegroundColor Green
}
finally {
    Pop-Location
}

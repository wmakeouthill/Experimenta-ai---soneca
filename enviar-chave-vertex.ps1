<#
.SYNOPSIS
    Envia a chave da conta de serviço do Vertex AI para a VPS, pronta para o backend.

.DESCRIPTION
    Copia secrets/vertex-sa.json por SSH para <PastaDeploy>/secrets/vertex-sa.json na VPS, com dono
    uid 1001 (appuser do container) e modo 400, e acrescenta GEMINI_PROJECT_ID ao .env.prod se faltar.
    A chave só trafega pelo SSH: não passa por git, .env nem log. Rode de novo para trocar a chave.
    Guia: VERTEX-AI-CONTA-DE-SERVICO.md

.EXAMPLE
    .\enviar-chave-vertex.ps1 -Vps deploy@<ip da VPS>
#>
param(
    [Parameter(Mandatory)] [string]$Vps,
    [string]$PastaDeploy = '~/snackbar',
    [string]$ChaveSsh = "$HOME\.ssh\kinghost_deploy_ed25519",
    [string]$Chave = "$PSScriptRoot\secrets\vertex-sa.json"
)
$ErrorActionPreference = 'Stop'

function Invocar([string]$Programa, [string[]]$Argumentos) {
    & $Programa @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "$Programa falhou (código $LASTEXITCODE)" }
}

if (-not (Test-Path $Chave)) { throw "Chave não encontrada: $Chave" }
$conta = Get-Content -Raw $Chave | ConvertFrom-Json
if ($conta.type -ne 'service_account') { throw "$Chave não é uma chave de conta de serviço" }
# O ID vai para um comando remoto: aceite só o formato de ID de projeto do GCP
if ($conta.project_id -notmatch '^[a-z][a-z0-9-]{4,28}[a-z0-9]$') { throw 'project_id inválido na chave' }

$opcoesSsh = @('-i', $ChaveSsh, '-o', 'IdentitiesOnly=yes')

# Pasta 700 na VPS: durante o envio, nenhum outro usuário lê a chave
Invocar ssh ($opcoesSsh + @($Vps, 'umask 077 && mkdir -p ~/.chave-vertex'))
Invocar scp ($opcoesSsh + @($Chave, "${Vps}:.chave-vertex/vertex-sa.json"))

# Dar o arquivo ao uid 1001 exige root. O sudo da VPS pede senha, mas o usuário está no grupo docker:
# um container descartável faz o install. Sem aspas duplas: o PowerShell 5.1 não as escapa ao chamar programas nativos
$instalar = @(
    'set -e'
    "trap 'rm -rf ~/.chave-vertex' EXIT"
    "cd $PastaDeploy"
    'test -f .env.prod || { echo ERRO: .env.prod ausente em $(pwd); exit 1; }'
    'mkdir -p secrets'
    'docker run --rm -v $HOME/.chave-vertex:/origem:ro -v $(pwd)/secrets:/destino alpine sh -c ''install -o 1001 -g 1001 -m 400 /origem/vertex-sa.json /destino/vertex-sa.json && chown 1001:1001 /destino && chmod 700 /destino'''
    "grep -q '^GEMINI_PROJECT_ID=' .env.prod || printf '\nGEMINI_PROJECT_ID=%s\n' $($conta.project_id) >> .env.prod"
) -join '; '
Invocar ssh ($opcoesSsh + @($Vps, $instalar))

Write-Host "Chave instalada em $PastaDeploy/secrets/vertex-sa.json (projeto $($conta.project_id))."
Write-Host 'O backend lê a chave ao subir: no primeiro uso siga o deploy (runbook §5);'
Write-Host 'numa troca de chave, rode na VPS: docker compose -f docker-compose.prod.yml restart backend'

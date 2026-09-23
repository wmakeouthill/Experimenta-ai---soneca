param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('balcao', 'totem')]
    [string]$App,
    [string]$CertificadoPfx = (Join-Path $env:LOCALAPPDATA 'ExperimentaAiSoneca\assinatura\experimenta-ai-soneca-code-signing.pfx')
)

$ErrorActionPreference = 'Stop'
$pfx = (Resolve-Path -LiteralPath $CertificadoPfx -ErrorAction Stop).Path
$cer = [IO.Path]::ChangeExtension($pfx, '.cer')
$senhaArquivo = [IO.Path]::ChangeExtension($pfx, '.senha.txt')
if (-not (Test-Path -LiteralPath $cer)) { throw 'Certificado público CER não encontrado ao lado do PFX.' }
$appDir = if ($App -eq 'totem') { Join-Path $PSScriptRoot '..\electron-totem' } else { $PSScriptRoot }
$senhaTexto = if (Test-Path -LiteralPath $senhaArquivo) {
    [IO.File]::ReadAllText($senhaArquivo)
} else {
    $senha = Read-Host 'Senha do certificado PFX' -AsSecureString
    [System.Net.NetworkCredential]::new('', $senha).Password
}
if (-not $senhaTexto) { throw 'A senha não pode ficar vazia.' }

$linkAnterior = $env:WIN_CSC_LINK
$senhaAnterior = $env:WIN_CSC_KEY_PASSWORD
try {
    $env:WIN_CSC_LINK = $pfx
    $env:WIN_CSC_KEY_PASSWORD = $senhaTexto
    Push-Location $appDir
    try {
        & npm.cmd run build:release:win
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o instalador assinado.' }
    } finally {
        Pop-Location
    }
} finally {
    if ($null -eq $linkAnterior) { Remove-Item Env:WIN_CSC_LINK -ErrorAction SilentlyContinue }
    else { $env:WIN_CSC_LINK = $linkAnterior }
    if ($null -eq $senhaAnterior) { Remove-Item Env:WIN_CSC_KEY_PASSWORD -ErrorAction SilentlyContinue }
    else { $env:WIN_CSC_KEY_PASSWORD = $senhaAnterior }
    $senhaTexto = $null
}

$dist = Join-Path $appDir 'dist-update'
$linha = Select-String -LiteralPath (Join-Path $dist 'latest.yml') -Pattern '^  - url: (.+\.exe)$'
if (-not $linha) { throw 'latest.yml não indica um instalador NSIS.' }
$nome = $linha.Matches.Groups[1].Value
if ($nome -notmatch '^[a-zA-Z0-9 ._-]+\.exe$') { throw 'Nome inválido no latest.yml.' }
$instalador = Join-Path $dist $nome
$assinatura = Get-AuthenticodeSignature -LiteralPath $instalador
if ($assinatura.Status -ne 'Valid') {
    throw "O instalador gerado não passou na verificação Windows: $($assinatura.Status)."
}
$certificadoPublico = [Security.Cryptography.X509Certificates.X509Certificate2]::new($cer)
if (-not $assinatura.SignerCertificate -or
    $assinatura.SignerCertificate.Thumbprint -ne $certificadoPublico.Thumbprint) {
    throw 'O instalador não foi assinado pelo certificado público esperado.'
}

$nomeBase = [IO.Path]::GetFileNameWithoutExtension($nome)
$kit = Join-Path $dist (Join-Path 'kit-cliente' $nomeBase)
New-Item -ItemType Directory -Path $kit -Force | Out-Null
$cerKit = "$nomeBase.cer"
$leiaMeKit = "$nomeBase-LEIA-ME.txt"
Copy-Item -LiteralPath $instalador -Destination (Join-Path $kit $nome) -Force
Copy-Item -LiteralPath $cer -Destination (Join-Path $kit $cerKit) -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'confiar-certificado-local.ps1') -Destination $kit -Force
@"
INSTALAÇÃO DO $App - $nomeBase

1. Confira a impressão digital do certificado com a anotada na máquina de build:
   $($certificadoPublico.Thumbprint)
2. Abra PowerShell como Administrador nesta pasta e execute:
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\confiar-certificado-local.ps1 -CertificadoCer '.\$cerKit' -Thumbprint '$($certificadoPublico.Thumbprint)'
3. Execute .\$nome para instalar ou atualizar o aplicativo.
4. Após abrir, confira a versão instalada no menu Opções (balcão) ou com Ctrl+Shift+U (totem).

O CER público não tem senha. O PFX privado e seu arquivo .senha.txt ficam apenas na máquina de build; nunca copie esses dois arquivos para o PC cliente.
"@ | Set-Content -LiteralPath (Join-Path $kit $leiaMeKit) -Encoding UTF8

Write-Host "Instalador assinado: $instalador"
Write-Host "Kit para o PC cliente: $kit"

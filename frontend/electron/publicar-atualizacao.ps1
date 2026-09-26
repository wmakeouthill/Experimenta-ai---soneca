param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9_.-]+(@[a-zA-Z0-9_.-]+)?$')]
    [string]$SshTarget,
    [ValidateSet('balcao', 'totem')]
    [string]$App = 'balcao',
    [string]$IdentityFile,
    [ValidatePattern('^/[a-zA-Z0-9/_-]+$')]
    [string]$RemoteProjectDir = '/home/deploy/snackbar'
)

$ErrorActionPreference = 'Stop'
$appDir = if ($App -eq 'totem') { Join-Path $PSScriptRoot '..\electron-totem' } else { $PSScriptRoot }
$dist = Join-Path $appDir 'dist-update'
$metadata = Join-Path $dist 'latest.yml'
$remoteDir = "$RemoteProjectDir/releases/electron/$App"
$sshOptions = @()
if ($IdentityFile) {
    $sshOptions = @('-i', (Resolve-Path -LiteralPath $IdentityFile -ErrorAction Stop).Path, '-o', 'IdentitiesOnly=yes')
}

if (-not (Test-Path -LiteralPath $metadata -PathType Leaf)) {
    throw 'latest.yml não encontrado. Rode npm.cmd run build:release:win antes de publicar.'
}

$package = Get-Content -LiteralPath (Join-Path $appDir 'package.json') -Raw | ConvertFrom-Json
$packageVersion = $package.version
$versionLine = Select-String -LiteralPath $metadata -Pattern '^version: (.+)$'
$installerLine = Select-String -LiteralPath $metadata -Pattern '^  - url: (.+\.exe)$'
if (-not $versionLine -or -not $installerLine) {
    throw 'latest.yml não contém versão e instalador NSIS válidos.'
}
$metadataVersion = $versionLine.Matches.Groups[1].Value
$metadataInstaller = $installerLine.Matches.Groups[1].Value
if ($metadataInstaller -notmatch '^[a-zA-Z0-9 ._-]+\.exe$') {
    throw 'Nome de instalador inválido no latest.yml.'
}
$installerPath = Join-Path $dist $metadataInstaller
if (-not (Test-Path -LiteralPath $installerPath -PathType Leaf)) {
    throw 'Instalador indicado no latest.yml não encontrado.'
}
$installer = Get-Item -LiteralPath $installerPath
$blockmap = "$($installer.FullName).blockmap"
if (-not (Test-Path -LiteralPath $blockmap -PathType Leaf)) {
    throw 'Blockmap do instalador não encontrado.'
}
if ($packageVersion -ne $metadataVersion) {
    throw "Versão do pacote ($packageVersion) diferente da versão do feed ($metadataVersion)."
}
$signature = Get-AuthenticodeSignature -LiteralPath $installer.FullName
if ($signature.Status -ne 'Valid') {
    throw 'Instalador sem assinatura Windows válida. Configure a assinatura antes de publicar.'
}
$signerName = $signature.SignerCertificate.GetNameInfo(
    [System.Security.Cryptography.X509Certificates.X509NameType]::SimpleName,
    $false
)
$publisherName = if ($package.build.win.signtoolOptions) {
    $package.build.win.signtoolOptions.publisherName
} else {
    $package.build.win.publisherName
}
if ($signerName -ne $publisherName) {
    throw "O publicador do certificado ($signerName) difere de package.json ($publisherName)."
}

& ssh @sshOptions $SshTarget "mkdir -p $remoteDir"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar diretório de releases na VPS.' }

$publishedVersion = & ssh @sshOptions $SshTarget "if [ -f $remoteDir/latest.yml ]; then sed -n 's/^version: //p' $remoteDir/latest.yml; fi"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao consultar a versão publicada na VPS.' }
if ($publishedVersion -and [version]$packageVersion -le [version]$publishedVersion) {
    throw "A versão publicada ($publishedVersion) deve ser menor que a nova ($packageVersion)."
}

# O feed só muda depois que os arquivos citados nele chegaram completos.
& scp @sshOptions $installer.FullName $blockmap "${SshTarget}:${remoteDir}/"
if ($LASTEXITCODE -ne 0) { throw 'Falha no upload do instalador ou blockmap.' }

& scp @sshOptions $metadata "${SshTarget}:${remoteDir}/latest.yml.next"
if ($LASTEXITCODE -ne 0) { throw 'Falha no upload do feed.' }

& ssh @sshOptions $SshTarget "mv -f $remoteDir/latest.yml.next $remoteDir/latest.yml"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao ativar o feed.' }

Write-Host "Versão $packageVersion publicada em https://experimentaaisoneca.app/updates/$App/"

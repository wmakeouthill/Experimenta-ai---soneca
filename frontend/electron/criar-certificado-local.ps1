param(
    [string]$Destino = (Join-Path $env:LOCALAPPDATA 'ExperimentaAiSoneca\assinatura')
)

$ErrorActionPreference = 'Stop'
$repositorio = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$diretorio = [IO.Path]::GetFullPath($Destino)
if ($diretorio -eq $repositorio -or
    $diretorio.StartsWith($repositorio + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Guarde a chave privada fora do repositório.'
}

$pfx = Join-Path $diretorio 'experimenta-ai-soneca-code-signing.pfx'
$cer = Join-Path $diretorio 'experimenta-ai-soneca-code-signing.cer'
$senhaArquivo = Join-Path $diretorio 'experimenta-ai-soneca-code-signing.senha.txt'
$existentes = @(@($pfx, $cer, $senhaArquivo) | Where-Object { Test-Path -LiteralPath $_ })
if ($existentes.Count -ne 0 -and $existentes.Count -ne 3) {
    throw 'Certificado incompleto nesse destino. Preserve os arquivos existentes e confira o backup.'
}

New-Item -ItemType Directory -Path $diretorio -Force | Out-Null
$acl = Get-Acl -LiteralPath $diretorio
$identidades = @(
    [Security.Principal.WindowsIdentity]::GetCurrent().User,
    [Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
    [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
)
if ($acl.AreAccessRulesProtected) {
    $permitidos = @($identidades | ForEach-Object { $_.Value })
    $extras = @($acl.Access | Where-Object {
        $_.AccessControlType -eq 'Allow' -and
        $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $permitidos
    })
    if ($extras) { throw 'A pasta do certificado já existe com permissões adicionais. Corrija a ACL antes de prosseguir.' }
} else {
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($identidade in $identidades) {
        $regra = [Security.AccessControl.FileSystemAccessRule]::new(
            $identidade, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'
        )
        $acl.SetAccessRule($regra)
    }
    Set-Acl -LiteralPath $diretorio -AclObject $acl
}

if ($existentes.Count -eq 0) {
    $bytes = New-Object byte[] 32
    $gerador = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $gerador.GetBytes($bytes) } finally { $gerador.Dispose() }
    $senhaTexto = [Convert]::ToBase64String($bytes)
    $senha = ConvertTo-SecureString -String $senhaTexto -AsPlainText -Force
    $certificado = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject 'CN=Experimenta aí do Soneca Delivery' `
        -CertStoreLocation 'Cert:\CurrentUser\My' `
        -KeyAlgorithm RSA `
        -KeyLength 3072 `
        -KeyExportPolicy Exportable `
        -HashAlgorithm SHA256 `
        -NotAfter (Get-Date).AddYears(3)
    Export-Certificate -Cert $certificado -FilePath $cer | Out-Null
    Export-PfxCertificate -Cert $certificado -FilePath $pfx -Password $senha | Out-Null
    Set-Content -LiteralPath $senhaArquivo -Value $senhaTexto -Encoding Ascii -NoNewline
    $senhaTexto = $null
} else {
    $certificado = [Security.Cryptography.X509Certificates.X509Certificate2]::new($cer)
}
Import-Certificate -FilePath $cer -CertStoreLocation 'Cert:\CurrentUser\Root' | Out-Null
Import-Certificate -FilePath $cer -CertStoreLocation 'Cert:\CurrentUser\TrustedPublisher' | Out-Null
Write-Host "Certificado público: $cer"
Write-Host "Chave privada protegida: $pfx"
Write-Host "Senha privada: $senhaArquivo"
Write-Host "Impressão digital: $($certificado.Thumbprint)"
Write-Host 'Faça backup seguro do PFX e da senha. Copie somente o CER para os PCs clientes.'

param(
    [Parameter(Mandatory = $true)]
    [string]$CertificadoCer,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{40}$')]
    [string]$Thumbprint
)

$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Abra o PowerShell como Administrador para instalar a confiança nesta máquina.'
}

$cer = (Resolve-Path -LiteralPath $CertificadoCer -ErrorAction Stop).Path
$certificado = [Security.Cryptography.X509Certificates.X509Certificate2]::new($cer)
$codigoAssinatura = '1.3.6.1.5.5.7.3.3'
$usos = @($certificado.Extensions | Where-Object {
    $_ -is [Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]
} | ForEach-Object { $_.EnhancedKeyUsages | ForEach-Object { $_.Value } })
if ($certificado.Thumbprint -ne $Thumbprint.ToUpperInvariant() -or
    $certificado.Subject -ne 'CN=Experimenta aí do Soneca Delivery' -or
    $certificado.Subject -ne $certificado.Issuer -or
    $codigoAssinatura -notin $usos -or
    $certificado.HasPrivateKey) {
    throw 'Certificado público, impressão digital ou uso de assinatura inválido.'
}

Import-Certificate -FilePath $cer -CertStoreLocation 'Cert:\LocalMachine\Root' | Out-Null
Import-Certificate -FilePath $cer -CertStoreLocation 'Cert:\LocalMachine\TrustedPublisher' | Out-Null
Write-Host "Certificado $($certificado.Thumbprint) confiável nesta máquina."

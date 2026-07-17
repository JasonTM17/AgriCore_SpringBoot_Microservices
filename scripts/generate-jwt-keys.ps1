# Generate PKCS#8 RSA keypair for identity JWT signing (local/dev).
# Private key is gitignored (*.pem). Never commit real production keys.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Dir = Join-Path $Root "infrastructure\jwt"
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
$priv = Join-Path $Dir "private.pem"
$pub = Join-Path $Dir "public.pem"
if ((Test-Path $priv) -and (Test-Path $pub)) {
    Write-Host "Keys already exist at $Dir (delete to regenerate)"
    exit 0
}
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $priv
openssl pkey -in $priv -pubout -out $pub
Write-Host "Wrote $priv and $pub"
Write-Host "Compose identity mounts ./infrastructure/jwt as /keys"

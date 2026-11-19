<#
.SYNOPSIS
    Descarga una copia de seguridad completa de la base de datos de producción (Neon).

.DESCRIPTION
    Usa pg_dump dentro de Docker (no hace falta instalar PostgreSQL). Pide la cadena de conexión de Neon
    al momento: nunca se guarda en el repositorio ni en el script. La copia queda en formato comprimido
    de PostgreSQL (.dump), con fecha y hora en el nombre.

    Guarda las copias en un lugar privado (por ejemplo, una carpeta de Google Drive o OneDrive):
    contienen todos los datos reales del negocio.

.PARAMETER Destino
    Carpeta donde se guarda la copia. Por defecto: Documentos\Copias inventario.

.EXAMPLE
    .\scripts\copia-de-seguridad.ps1
    .\scripts\copia-de-seguridad.ps1 -Destino "G:\Mi unidad\Copias inventario"
#>
param(
    [string]$Destino = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Copias inventario')
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'Hace falta Docker Desktop abierto para hacer la copia.' -ForegroundColor Red
    exit 1
}

Write-Host 'Pega la cadena de conexión de Neon (empieza con postgresql://) y presiona Enter.'
Write-Host 'En Neon: Dashboard > Connect > Connection string (sin "Connection pooling").'
$conexion = Read-Host 'Cadena de conexión'
if ($conexion -notmatch '^postgres(ql)?://') {
    Write-Host 'Eso no parece una cadena de conexión de Neon.' -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$nombre = 'inventario-' + (Get-Date -Format 'yyyy-MM-dd_HHmm') + '.dump'

Write-Host "Descargando la copia en $Destino ..."
# El cliente de PostgreSQL 18 puede copiar bases de la versión 17 o anteriores
docker run --rm -v "${Destino}:/copias" postgres:18 `
    pg_dump --format=custom --no-owner --no-privileges --file "/copias/$nombre" "$conexion"

if ($LASTEXITCODE -ne 0) {
    Write-Host 'No se pudo hacer la copia. Revisa la cadena de conexión y que Docker esté abierto.' -ForegroundColor Red
    exit 1
}
$tamano = [math]::Round((Get-Item (Join-Path $Destino $nombre)).Length / 1KB)
Write-Host "Copia lista: $nombre ($tamano KB)" -ForegroundColor Green

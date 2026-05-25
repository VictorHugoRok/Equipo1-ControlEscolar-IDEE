$ErrorActionPreference = "Stop"

$port = 8080

Write-Host "[run-dev] Buscando proceso escuchando en puerto $port..."

$conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($conns) {
    $owningPids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($owningPid in $owningPids) {
        if (-not $owningPid) { continue }
        try {
            $proc = Get-Process -Id $owningPid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "[run-dev] Deteniendo PID $owningPid ($($proc.ProcessName)) que usa $port..."
            } else {
                Write-Host "[run-dev] Deteniendo PID $owningPid que usa $port..."
            }
            Stop-Process -Id $owningPid -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host ("[run-dev] No se pudo detener PID {0}: {1}" -f $owningPid, $PSItem.Exception.Message)
        }
    }
    Start-Sleep -Seconds 1
} else {
    Write-Host "[run-dev] Puerto $port libre."
}

Write-Host "[run-dev] Iniciando backend (mvn spring-boot:run)..."
Set-Location -Path $PSScriptRoot
mvn spring-boot:run


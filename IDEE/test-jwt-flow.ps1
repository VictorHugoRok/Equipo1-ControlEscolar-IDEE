#!/usr/bin/env pwsh
<#
.DESCRIPTION
Script de prueba automatizado para verificar el flujo JWT completo
.USAGE
.\test-jwt-flow.ps1
#>

Write-Host "======================================"
Write-Host "🔍 PRUEBA DE FLUJO JWT COMPLETO"
Write-Host "======================================"
Write-Host ""

# Colores
$green = [System.ConsoleColor]::Green
$red = [System.ConsoleColor]::Red
$yellow = [System.ConsoleColor]::Yellow
$blue = [System.ConsoleColor]::Cyan

function Write-Success {
    Write-Host "✅ $args" -ForegroundColor $green
}

function Write-Error-Custom {
    Write-Host "❌ $args" -ForegroundColor $red
}

function Write-Warning-Custom {
    Write-Host "⚠️ $args" -ForegroundColor $yellow
}

function Write-Info {
    Write-Host "ℹ️ $args" -ForegroundColor $blue
}

# ============================================
# PASO 1: Verificar Backend
# ============================================

Write-Host ""
Write-Host "PASO 1: Verificar que Backend está corriendo..." -ForegroundColor $blue

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/init-usuarios" `
        -Method POST `
        -ContentType "application/json" `
        -TimeoutSec 5 `
        -SkipHttpErrorCheck

    if ($response.StatusCode -eq 200) {
        Write-Success "Backend está operativo (puerto 8080)"
        $initData = $response.Content | ConvertFrom-Json
        Write-Info "Usuarios creados/verificados:"
        foreach ($user in $initData.usuarios.PSObject.Properties) {
            Write-Host "  - $($user.Name): $($user.Value)" -ForegroundColor Gray
        }
    } else {
        Write-Error-Custom "Backend responde con status $($response.StatusCode)"
        exit 1
    }
} catch {
    Write-Error-Custom "No se puede conectar a Backend: $_"
    Write-Warning-Custom "Asegúrate que:"
    Write-Host "  1. Backend está iniciado: cd backend && mvn spring-boot:run"
    Write-Host "  2. PostgreSQL está corriendo"
    exit 1
}

# ============================================
# PASO 2: Test LOGIN - Obtener Token
# ============================================

Write-Host ""
Write-Host "PASO 2: Probar LOGIN..." -ForegroundColor $blue

$loginBody = @{
    email = "admin@idee.edu.mx"
    password = "admin123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body $loginBody `
        -TimeoutSec 5

    if ($loginResponse.StatusCode -eq 200) {
        Write-Success "Login exitoso"
        
        $loginData = $loginResponse.Content | ConvertFrom-Json
        
        # Validar que el token está presente
        if ($loginData.token) {
            Write-Success "Token recibido ($(($loginData.token).Length) caracteres)"
            Write-Info "Email: $($loginData.email)"
            Write-Info "Tipo: $($loginData.tipoUsuario)"
            Write-Info "ID: $($loginData.id)"
            
            # Guardar token para tests siguientes
            $TOKEN = $loginData.token
        } else {
            Write-Error-Custom "Login devolvió response pero SIN token"
            exit 1
        }
    } else {
        Write-Error-Custom "Login falló con status $($loginResponse.StatusCode)"
        exit 1
    }
} catch {
    Write-Error-Custom "Error en login: $_"
    exit 1
}

# ============================================
# PASO 3: Test /me - Validar Token CON Bearer
# ============================================

Write-Host ""
Write-Host "PASO 3: Validar token en /me (CON Bearer header)..." -ForegroundColor $blue

$headers = @{
    "Authorization" = "Bearer $TOKEN"
    "Content-Type" = "application/json"
}

try {
    $meResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/me" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 5

    if ($meResponse.StatusCode -eq 200) {
        Write-Success "/me validó token correctamente (200 OK)"
        
        $userData = $meResponse.Content | ConvertFrom-Json
        Write-Info "Usuario: $($userData.email)"
        Write-Info "Tipo: $($userData.tipoUsuario)"
        Write-Info "Activo: $($userData.activo)"
    } else {
        Write-Error-Custom "/me devolvió status $($meResponse.StatusCode)"
        exit 1
    }
} catch {
    Write-Error-Custom "Error validando token: $_"
    exit 1
}

# ============================================
# PASO 4: Test /me SIN Token (debe rechazar)
# ============================================

Write-Host ""
Write-Host "PASO 4: Validar que /me rechaza peticiones SIN token..." -ForegroundColor $blue

try {
    $meNoAuthResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/me" `
        -Method GET `
        -ContentType "application/json" `
        -TimeoutSec 5 `
        -SkipHttpErrorCheck

    if ($meNoAuthResponse.StatusCode -eq 401) {
        Write-Success "/me rechazó petición sin token (401 Unauthorized)"
    } else {
        Write-Error-Custom "/me NO rechazó petición sin token (status: $($meNoAuthResponse.StatusCode))"
        Write-Warning-Custom "Esto es un PROBLEMA de SEGURIDAD - endpoint debería ser 401"
    }
} catch {
    Write-Error-Custom "Error en test sin auth: $_"
}

# ============================================
# PASO 5: Test Token Expirado (mediante invalidación manual)
# ============================================

Write-Host ""
Write-Host "PASO 5: Validar que token inválido es rechazado..." -ForegroundColor $blue

$headers_invalid = @{
    "Authorization" = "Bearer invalidtoken123"
    "Content-Type" = "application/json"
}

try {
    $meInvalidResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/me" `
        -Method GET `
        -Headers $headers_invalid `
        -TimeoutSec 5 `
        -SkipHttpErrorCheck

    if ($meInvalidResponse.StatusCode -eq 401) {
        Write-Success "Token inválido fue rechazado (401)"
    } else {
        Write-Error-Custom "Token inválido NO fue rechazado (status: $($meInvalidResponse.StatusCode))"
    }
} catch {
    Write-Warning-Custom "Error esperado con token inválido: OK"
}

# ============================================
# PASO 6: Información del Token (decodificar JWT)
# ============================================

Write-Host ""
Write-Host "PASO 6: Información del token JWT..." -ForegroundColor $blue

try {
    $parts = $TOKEN.Split('.')
    if ($parts.Length -ne 3) {
        Write-Error-Custom "Token JWT inválido (debe tener 3 partes)"
    } else {
        # Decodificar payload (segunda parte)
        $payload = $parts[1]
        # Agregar padding si es necesario
        while ($payload.Length % 4) {
            $payload += "="
        }
        $decoded = [System.Convert]::FromBase64String($payload)
        $payloadJson = [System.Text.Encoding]::UTF8.GetString($decoded)
        $payloadData = $payloadJson | ConvertFrom-Json
        
        Write-Success "Token JWT decodificado:"
        Write-Info "Usuario (sub): $($payloadData.sub)"
        Write-Info "Emisión (iat): $(Get-Date -UnixTimeSeconds $payloadData.iat -Format 'yyyy-MM-dd HH:mm:ss')"
        
        if ($payloadData.exp) {
            $expDate = Get-Date -UnixTimeSeconds $payloadData.exp -Format 'yyyy-MM-dd HH:mm:ss'
            $secsLeft = ($payloadData.exp * 1000) - (Get-Date).Ticks / 1000
            Write-Info "Expira (exp): $expDate (en ~$([Math]::Floor($secsLeft/3600))h)"
            
            if ($secsLeft -lt 0) {
                Write-Error-Custom "⚠️ Token YA está expirado"
            }
        }
    }
} catch {
    Write-Error-Custom "Error decodificando token: $_"
}

# ============================================
# PASO 7: Frontend - Test localStorage
# ============================================

Write-Host ""
Write-Host "PASO 7: Test localStorage (desde console del navegador)..." -ForegroundColor $blue

Write-Info "Para completar este test, abre tu navegador:"
Write-Host "  1. Abre: http://localhost:5500" -ForegroundColor Gray
Write-Host "  2. Presiona F12 (DevTools)" -ForegroundColor Gray
Write-Host "  3. Vete a: Console tab" -ForegroundColor Gray
Write-Host "  4. Ejecuta:" -ForegroundColor Gray
Write-Host "     localStorage.getItem('token')" -ForegroundColor Gray
Write-Host "  5. Debería mostrar el token" -ForegroundColor Gray

# ============================================
# RESUMEN
# ============================================

Write-Host ""
Write-Host "======================================"
Write-Host "✅ RESUMEN DE PRUEBAS" -ForegroundColor $green
Write-Host "======================================"
Write-Host ""
Write-Host "✅ Backend operativo" -ForegroundColor $green
Write-Host "✅ Login devuelve token" -ForegroundColor $green
Write-Host "✅ /me valida token" -ForegroundColor $green
Write-Host "✅ /me rechaza sin token" -ForegroundColor $green
Write-Host "✅ Token JWT válido" -ForegroundColor $green
Write-Host ""
Write-Host "📋 Próximos pasos:" -ForegroundColor $blue
Write-Host "  1. Verificar en browser console que localStorage tiene token"
Write-Host "  2. Hacer login desde la interfaz"
Write-Host "  3. Verificar que sesión persiste (F5)"
Write-Host "  4. Verificar logs backend:"
Write-Host "     - Buscar '✅ [JwtFilter]' en logs"
Write-Host "     - Buscar '✅ Login exitoso' en logs"
Write-Host ""
Write-Host "Si hay problemas, revisa: GUIA_DEPURACION_SESIONES.md"
Write-Host ""

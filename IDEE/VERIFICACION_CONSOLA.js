// VERIFICACIÓN RÁPIDA EN CONSOLA DEL NAVEGADOR
// Copia y pega esto en la consola de DevTools (F12)

console.log("=== VERIFICACIÓN DEL SISTEMA DE ROLES ===\n");

// 1. Verificar que roles.js se cargó
console.log("1. ¿ROLES definido?", typeof ROLES !== 'undefined' ? "✅ SÍ" : "❌ NO");
console.log("2. ¿PERMISOS_POR_ROL definido?", typeof PERMISOS_POR_ROL !== 'undefined' ? "✅ SÍ" : "❌ NO");

// 2. Verificar localStorage
console.log("\n=== localStorage ===");
console.log("token:", localStorage.getItem('token') ? "✅ Existe" : "❌ No existe");
console.log("userType:", localStorage.getItem('userTipo') || "❌ No existe");
console.log("userEmail:", localStorage.getItem('userEmail') || "❌ No existe");

// 3. Verificar window.currentUser
console.log("\n=== window.currentUser ===");
if (window.currentUser) {
    console.log("✅ Existe:");
    console.log("  - email:", window.currentUser.email);
    console.log("  - tipoUsuario:", window.currentUser.tipoUsuario);
    console.log("  - id:", window.currentUser.id);
} else {
    console.log("❌ No existe");
}

// 4. Verificar obtenerRolActual()
console.log("\n=== obtenerRolActual() ===");
if (typeof obtenerRolActual === 'function') {
    const rolActual = obtenerRolActual();
    console.log("Rol actual:", rolActual || "null");
    console.log("¿Es válido?", rolActual && typeof esRolValido === 'function' && esRolValido(rolActual) ? "✅ SÍ" : "❌ NO");
} else {
    console.log("❌ Función no encontrada");
}

// 5. Verificar permisos
console.log("\n=== Permisos del rol actual ===");
if (typeof obtenerRolActual === 'function' && typeof obtenerPermisosDelRol === 'function') {
    const rol = obtenerRolActual();
    if (rol) {
        const permisos = obtenerPermisosDelRol(rol);
        if (permisos) {
            console.log("✅ Permisos encontrados:");
            console.log(permisos);
        } else {
            console.log("❌ Permisos no encontrados para rol:", rol);
        }
    }
}

// 6. Verificar isAuthenticated()
console.log("\n=== isAuthenticated() ===");
if (typeof isAuthenticated === 'function') {
    console.log("¿Autenticado?", isAuthenticated() ? "✅ SÍ" : "❌ NO");
} else {
    console.log("❌ Función no encontrada");
}

console.log("\n=== FIN DE VERIFICACIÓN ===");

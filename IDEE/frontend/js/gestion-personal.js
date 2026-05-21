/**
 * Gestión de Personal Administrativo
 * Controlado por Secretario Administrativo
 */

let personalData = [];
let personalEditando = null;

function getHeadersPersonal(includeContentType = true) {
    const headers = {};
    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }

    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

function escapeHtmlPersonal(value) {
    if (typeof escapeHtml === 'function') {
        return escapeHtml(value);
    }

    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ==================== CARGAR DATOS ====================

/**
 * Cargar todo el personal
 */
async function cargarPersonal() {
    try {
        const response = await fetch(`${API_URL}/personal`, {
            method: 'GET',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error('Error al cargar personal');
        }

        personalData = await response.json();
        renderizarTablaPersonal(personalData);
    } catch (error) {
        console.error('Error al cargar personal:', error);
        mostrarErrorTablaPersonal('Error al cargar la lista de personal');
    }
}

// ==================== RENDERIZAR TABLA ====================

/**
 * Renderizar tabla de personal
 */
function renderizarTablaPersonal(lista) {
    const tbody = document.getElementById('personalTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted py-4">
                    <i class="bi bi-inbox"></i> No hay personal registrado
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = lista.map(persona => `
        <tr>
            <td><strong>${persona.apellidos}, ${persona.nombres}</strong></td>
            <td>${persona.puesto || 'N/A'}</td>
            <td><small>${persona.correoInstitucional || 'N/A'}</small></td>
            <td>${persona.telefono || 'N/A'}</td>
            <td>${getBadgeActivoPersonal(persona.activo)}</td>
            <td>
                <button class="btn btn-sm btn-outline-secondary me-1" onclick="editarPersonal(${persona.id}); event.stopPropagation();">
                    Editar
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="confirmarEliminarPersonal(${persona.id}, '${escapeHtmlPersonal(persona.nombres + ' ' + persona.apellidos)}'); event.stopPropagation();">
                    Eliminar
                </button>
            </td>
        </tr>
    `).join('');
}

function mostrarErrorTablaPersonal(mensaje) {
    const tbody = document.getElementById('personalTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="7" class="text-center text-danger py-4">
                <i class="bi bi-exclamation-triangle"></i> ${mensaje}
            </td>
        </tr>
    `;
}

function getBadgeActivoPersonal(activo) {
    return activo
        ? '<span class="badge bg-success-subtle text-success">Activo</span>'
        : '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>';
}

// ==================== GUARDAR PERSONAL ====================

/**
 * Guardar o actualizar personal
 */
async function guardarPersonal(evento) {
    evento.preventDefault();

    const formulario = evento.target;
    const personalId = document.getElementById('personalId')?.value;

    const datos = {
        curp: document.getElementById('personalClave')?.value || '',
        nombre: document.getElementById('personalNombres').value,
        apellidoPaterno: document.getElementById('personalApellidos').value,
        apellidoMaterno: '', // Campo simplificado
        puesto: document.getElementById('personalPuesto').value,
        correoInstitucional: document.getElementById('personalCorreo').value,
        telefono: document.getElementById('personalTelefono').value,
        activo: document.getElementById('personalActivo')?.checked || true
    };

    // Validar campos requeridos
    if (!datos.nombre || !datos.apellidoPaterno || !datos.puesto || !datos.correoInstitucional) {
        alert('Por favor completa todos los campos requeridos');
        return;
    }

    try {
        const metodo = personalId ? 'PUT' : 'POST';
        const url = personalId ? `${API_URL}/personal/${personalId}` : `${API_URL}/personal`;

        const response = await fetch(url, {
            method: metodo,
            headers: getHeadersPersonal(),
            body: JSON.stringify(datos)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Error al guardar personal: ${response.status}`);
        }

        const resultado = await response.json();

        alert(personalId ? 'Personal actualizado correctamente' : 'Personal registrado correctamente');
        
        // Limpiar formulario
        formulario.reset();
        document.getElementById('personalId').value = '';
        
        // Recargar tabla
        await cargarPersonal();
    } catch (error) {
        console.error('Error al guardar personal:', error);
        alert('Error al guardar personal: ' + error.message);
    }
}

// ==================== EDITAR PERSONAL ====================

/**
 * Editar personal existente
 */
async function editarPersonal(id) {
    try {
        const response = await fetch(`${API_URL}/personal/${id}`, {
            method: 'GET',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error('Error al cargar datos del personal');
        }

        const persona = await response.json();

        // Llenar formulario
        document.getElementById('personalId').value = persona.id || '';
        document.getElementById('personalNombres').value = persona.nombre || '';
        // Combinamos apellidoPaterno y apellidoMaterno en un solo campo
        const apellidos = (persona.apellidoPaterno || '') + ' ' + (persona.apellidoMaterno || '');
        document.getElementById('personalApellidos').value = apellidos.trim();
        document.getElementById('personalPuesto').value = persona.puesto || '';
        document.getElementById('personalCorreo').value = persona.correoInstitucional || '';
        document.getElementById('personalTelefono').value = persona.telefono || '';
        document.getElementById('personalClave').value = persona.curp || '';
        document.getElementById('personalActivo').checked = persona.activo !== false;

        // Scroll al formulario
        const formulario = document.getElementById('personalForm');
        if (formulario) {
            formulario.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    } catch (error) {
        console.error('Error al editar personal:', error);
        alert('Error al cargar datos del personal');
    }
}

/**
 * Limpiar formulario de personal
 */
function limpiarFormularioPersonal() {
    const formulario = document.getElementById('personalForm');
    if (formulario) {
        formulario.reset();
    }
    document.getElementById('personalId').value = '';
}

// ==================== ELIMINAR PERSONAL ====================

/**
 * Confirmar y eliminar personal
 */
async function confirmarEliminarPersonal(id, nombre) {
    if (confirm(`¿Está seguro de eliminar a ${nombre}?\n\nEsta acción no se puede deshacer.`)) {
        await eliminarPersonal(id);
    }
}

/**
 * Eliminar personal
 */
async function eliminarPersonal(id) {
    try {
        const response = await fetch(`${API_URL}/personal/${id}`, {
            method: 'DELETE',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error(`Error al eliminar personal: ${response.status}`);
        }

        alert('Personal eliminado correctamente');
        
        // Recargar tabla
        await cargarPersonal();
    } catch (error) {
        console.error('Error al eliminar personal:', error);
        alert('Error al eliminar personal: ' + error.message);
    }
}

// ==================== BÚSQUEDA Y FILTROS ====================

/**
 * Buscar personal por filtros
 */
function buscarPersonal() {
    const busqueda = document.getElementById('personalBusqueda')?.value.toLowerCase() || '';
    const puesto = document.getElementById('personalFiltroP uesto')?.value || '';
    const activo = document.getElementById('personalFiltroActivo')?.value || '';

    const filtrados = personalData.filter(persona => {
        const coincideNombre = `${persona.nombres} ${persona.apellidos}`.toLowerCase().includes(busqueda);
        const coincidePuesto = !puesto || persona.puesto === puesto;
        const coincideActivo = !activo || persona.activo.toString() === activo;

        return coincideNombre && coincidePuesto && coincideActivo;
    });

    renderizarTablaPersonal(filtrados);
}

/**
 * Limpiar filtros de búsqueda
 */
function limpiarBusquedaPersonal() {
    document.getElementById('personalBusqueda').value = '';
    document.getElementById('personalFiltroP uesto').value = '';
    document.getElementById('personalFiltroActivo').value = '';
    renderizarTablaPersonal(personalData);
}

// ==================== EXPORTAR DATOS ====================

/**
 * Exportar lista de personal a Excel (demo)
 */
function exportarPersonalExcel() {
    alert('Funcionalidad de exportación a Excel en desarrollo.\nSe generará un archivo .xlsx con la lista de personal.');
}

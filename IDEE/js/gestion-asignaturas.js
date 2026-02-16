// Gestion de asignaturas por programa educativo

let asignaturas = [];
let asignaturaEditando = null;

function getHeadersAsignaturas(includeContentType = true) {
    if (typeof getHeaders === 'function') {
        return getHeaders(includeContentType);
    }

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

function escapeHtmlAsignaturas(value) {
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

function formatTipoAsignatura(tipo) {
    const labels = {
        OBLIGATORIA: 'Obligatoria',
        OPTATIVA: 'Optativa',
        LIBRE: 'Libre',
        EXTRACURRICULAR: 'Extracurricular'
    };
    return labels[tipo] || 'N/A';
}

function getBadgeEstatusAsignatura(estatus) {
    const badges = {
        ACTIVA: '<span class="badge bg-success-subtle text-success">Activa</span>',
        INACTIVA: '<span class="badge bg-secondary-subtle text-secondary">Inactiva</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

function renderizarTablaAsignaturas(lista, mensajeVacio) {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage(mensajeVacio || 'No hay asignaturas registradas');
        } else {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center text-muted py-4">${escapeHtmlAsignaturas(mensajeVacio || 'No hay asignaturas registradas')}</td></tr>`;
        }
        return;
    }

    tbody.innerHTML = lista.map(asignatura => {
        const horas = [asignatura.horasAula, asignatura.horasPractica, asignatura.horasIndependientes]
            .map(valor => (valor !== null && valor !== undefined && valor !== '') ? valor : '0')
            .join(' / ');

        return `
            <tr data-asignatura-id="${asignatura.id}">
                <td><strong>${escapeHtmlAsignaturas(asignatura.clave || 'N/A')}</strong></td>
                <td>${escapeHtmlAsignaturas(asignatura.nombre || 'N/A')}</td>
                <td>${formatTipoAsignatura(asignatura.tipo)}</td>
                <td>${escapeHtmlAsignaturas(asignatura.periodo || 'N/A')}</td>
                <td>${escapeHtmlAsignaturas(asignatura.creditos || 'N/A')}</td>
                <td>${escapeHtmlAsignaturas(horas)}</td>
                <td>${getBadgeEstatusAsignatura(asignatura.estatus)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-secondary me-1" data-action="edit">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete">Eliminar</button>
                </td>
            </tr>
        `;
    }).join('');
}

async function cargarAsignaturas(programaId) {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    if (!programaId) {
        asignaturas = [];
        renderizarTablaAsignaturas([], 'Selecciona un programa para ver sus asignaturas');
        limpiarFormularioAsignatura();
        return;
    }

    try {
        const response = await fetch(`${API_URL}/asignaturas?programaId=${programaId}`, {
            method: 'GET',
            headers: getHeadersAsignaturas()
        });

        if (!response.ok) {
            throw new Error('Error al cargar asignaturas');
        }

        asignaturas = await response.json();
        renderizarTablaAsignaturas(asignaturas);
        limpiarFormularioAsignatura();
    } catch (error) {
        console.error('Error al cargar asignaturas:', error);
        renderizarTablaAsignaturas([], 'Error al cargar las asignaturas');
    }
}

function prepararFormularioAsignatura(asignatura) {
    const form = document.getElementById('asignaturaForm');
    if (!form) return;

    document.getElementById('asignaturaId').value = asignatura ? asignatura.id || '' : '';
    document.getElementById('asignaturaClave').value = asignatura ? (asignatura.clave || '') : '';
    document.getElementById('asignaturaNombre').value = asignatura ? (asignatura.nombre || '') : '';
    document.getElementById('asignaturaTipo').value = asignatura ? (asignatura.tipo || '') : '';
    document.getElementById('asignaturaPeriodo').value = asignatura ? (asignatura.periodo || '') : '';
    document.getElementById('asignaturaCreditos').value = asignatura ? (asignatura.creditos || '') : '';
    document.getElementById('asignaturaHorasAula').value = asignatura ? (asignatura.horasAula || '') : '';
    document.getElementById('asignaturaHorasPractica').value = asignatura ? (asignatura.horasPractica || '') : '';
    document.getElementById('asignaturaHorasIndependientes').value = asignatura ? (asignatura.horasIndependientes || '') : '';
    document.getElementById('asignaturaEstatus').value = asignatura ? (asignatura.estatus || 'ACTIVA') : 'ACTIVA';

    const boton = document.getElementById('btnGuardarAsignatura');
    if (boton) {
        boton.textContent = asignatura ? 'Actualizar asignatura' : 'Guardar asignatura';
    }
}

function limpiarFormularioAsignatura() {
    asignaturaEditando = null;
    prepararFormularioAsignatura(null);
}

function editarAsignatura(id) {
    const asignatura = asignaturas.find(item => item.id === id);
    if (!asignatura) return;

    asignaturaEditando = asignatura;
    prepararFormularioAsignatura(asignatura);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function guardarAsignatura() {
    const form = document.getElementById('asignaturaForm');
    if (!form) return;

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    if (!programaSeleccionadoId) {
        alert('Selecciona un programa para registrar la asignatura.');
        return;
    }

    const periodoValue = document.getElementById('asignaturaPeriodo').value;
    const creditosValue = document.getElementById('asignaturaCreditos').value;
    const horasAulaValue = document.getElementById('asignaturaHorasAula').value;
    const horasPracticaValue = document.getElementById('asignaturaHorasPractica').value;
    const horasIndependientesValue = document.getElementById('asignaturaHorasIndependientes').value;

    const asignaturaData = {
        clave: document.getElementById('asignaturaClave').value.trim(),
        nombre: document.getElementById('asignaturaNombre').value.trim(),
        tipo: document.getElementById('asignaturaTipo').value,
        periodo: periodoValue ? parseInt(periodoValue, 10) : null,
        creditos: creditosValue ? parseInt(creditosValue, 10) : null,
        horasAula: horasAulaValue ? parseInt(horasAulaValue, 10) : null,
        horasPractica: horasPracticaValue ? parseInt(horasPracticaValue, 10) : null,
        horasIndependientes: horasIndependientesValue ? parseInt(horasIndependientesValue, 10) : null,
        estatus: document.getElementById('asignaturaEstatus').value || 'ACTIVA',
        programa: { id: programaSeleccionadoId }
    };

    const asignaturaId = document.getElementById('asignaturaId').value;
    const url = asignaturaId ? `${API_URL}/asignaturas/${asignaturaId}` : `${API_URL}/asignaturas`;
    const method = asignaturaId ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method,
            headers: getHeadersAsignaturas(),
            body: JSON.stringify(asignaturaData)
        });

        let data = null;
        try {
            data = await response.json();
        } catch (parseError) {
            data = null;
        }

        if (!response.ok) {
            const message = data && (data.error || data.message) ? (data.error || data.message) : 'Error al guardar asignatura';
            throw new Error(message);
        }

        await cargarAsignaturas(programaSeleccionadoId);
        alert(asignaturaId ? 'Asignatura actualizada exitosamente' : 'Asignatura creada exitosamente');
    } catch (error) {
        console.error('Error al guardar asignatura:', error);
        alert(error.message || 'Error al guardar asignatura');
    }
}

async function eliminarAsignatura(id) {
    try {
        const response = await fetch(`${API_URL}/asignaturas/${id}`, {
            method: 'DELETE',
            headers: getHeadersAsignaturas(false)
        });

        if (!response.ok) {
            let message = 'Error al eliminar asignatura';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar asignatura';
            }
            throw new Error(message);
        }

        await cargarAsignaturas(programaSeleccionadoId);
        alert('Asignatura eliminada exitosamente');
    } catch (error) {
        console.error('Error al eliminar asignatura:', error);
        alert(error.message || 'Error al eliminar asignatura');
    }
}

function inicializarTablaAsignaturas() {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-asignatura-id]');
        if (!row) return;

        const asignaturaId = parseInt(row.dataset.asignaturaId, 10);
        if (!asignaturaId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarAsignatura(asignaturaId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar esta asignatura? Esta accion no se puede deshacer.')) {
                    eliminarAsignatura(asignaturaId);
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('programasSection')) return;

    // ✅ ESPERAR A QUE SESIÓN ESTÉ VALIDADA ANTES DE CARGAR DATOS
    function initAsignaturas() {
        inicializarTablaAsignaturas();

        const btnGuardar = document.getElementById('btnGuardarAsignatura');
        if (btnGuardar) {
            btnGuardar.addEventListener('click', guardarAsignatura);
        }

        if (!programaSeleccionadoId) {
            renderizarTablaAsignaturas([], 'Selecciona un programa para ver sus asignaturas');
        }
    }

    // Si sesión ya está validada, cargar datos ahora
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        console.log('✅ [Asignaturas] Sesión ya validada, cargando datos...');
        initAsignaturas();
    } else {
        // Si no, esperar al evento de validación
        console.log('⏳ [Asignaturas] Esperando validación de sesión...');
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            console.log('✅ [Asignaturas] Sesión validada (evento), cargando datos...');
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initAsignaturas();
        }, { once: true });
    }
});

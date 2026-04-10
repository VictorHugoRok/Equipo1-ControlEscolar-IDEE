// Gestion de programas educativos

let programasEducativos = [];
let programaEditando = null;
let programaSeleccionadoId = null;
let tablaProgramasInicializada = false;

function getHeaders(includeContentType = true) {
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

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatTipoPrograma(tipo) {
    const labels = {
        LICENCIATURA: 'Licenciatura',
        MAESTRIA: 'Maestria',
        ESPECIALIDAD: 'Especialidad',
        DOCTORADO: 'Doctorado',
        EXTRACURRICULAR: 'Extracurricular'
    };
    return labels[tipo] || 'N/A';
}

function formatTipoPeriodo(tipo, cantidad) {
    const labels = {
        SEMESTRE: 'semestre',
        TRIMESTRE: 'trimestre',
        CUATRIMESTRE: 'cuatrimestre'
    };
    const label = labels[tipo] || '';
    if (!label) {
        return 'N/A';
    }
    if (!cantidad) {
        return label;
    }
    return cantidad === 1 ? label : `${label}s`;
}

function getBadgeEstatusPrograma(estatus) {
    const badges = {
        ACTIVO: '<span class="badge bg-success-subtle text-success">Activo</span>',
        INACTIVO: '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

async function cargarProgramas() {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_URL}/programas-educativos`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Error al cargar programas');
        }

        programasEducativos = await response.json();
        renderizarTablaProgramas(programasEducativos);
    } catch (error) {
        console.error('Error al cargar programas:', error);
        mostrarErrorTablaProgramas('Error al cargar la lista de programas');
    }
}

function renderizarTablaProgramas(lista) {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage('No hay programas registrados');
        } else {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No hay programas registrados</td></tr>';
        }
        return;
    }

    tbody.innerHTML = lista.map(programa => {
        const periodo = programa.duracionPeriodos
            ? (programa.tipoPeriodo
                ? `${programa.duracionPeriodos} ${formatTipoPeriodo(programa.tipoPeriodo, programa.duracionPeriodos)}`
                : `${programa.duracionPeriodos}`)
            : (programa.tipoPeriodo ? formatTipoPeriodo(programa.tipoPeriodo) : 'N/A');

        return `
            <tr data-programa-id="${programa.id}">
                <td><strong>${escapeHtml(programa.clave || 'N/A')}</strong></td>
                <td>${escapeHtml(programa.claveDgp || 'N/A')}</td>
                <td>${escapeHtml(programa.nombre || 'N/A')}</td>
                <td>${formatTipoPrograma(programa.tipoPrograma)}</td>
                <td>${escapeHtml(periodo)}</td>
                <td>${escapeHtml(programa.rvoe || 'N/A')}</td>
                <td>${getBadgeEstatusPrograma(programa.estatus)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-secondary me-1" data-action="edit">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete">Eliminar</button>
                </td>
            </tr>
        `;
    }).join('');
}

function mostrarErrorTablaProgramas(mensaje) {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center text-danger py-4">
                ${escapeHtml(mensaje)}
            </td>
        </tr>
    `;
}

function seleccionarProgramaPorId(id) {
    const programa = programasEducativos.find(item => item.id === id);
    if (!programa) return;

    programaSeleccionadoId = id;
    if (typeof seleccionarPrograma === 'function') {
        seleccionarPrograma(programa.nombre || 'Programa');
    }
    if (typeof cargarAsignaturas === 'function') {
        cargarAsignaturas(id);
    }
}

function prepararFormularioPrograma(programa) {
    const form = document.getElementById('programaForm');
    if (!form) return;

    document.getElementById('programaId').value = programa ? programa.id || '' : '';
    document.getElementById('programaClave').value = programa ? (programa.clave || '') : '';
    document.getElementById('programaClaveDgp').value = programa ? (programa.claveDgp || '') : '';
    document.getElementById('programaNombre').value = programa ? (programa.nombre || '') : '';
    document.getElementById('programaTipo').value = programa ? (programa.tipoPrograma || '') : '';
    document.getElementById('programaDuracion').value = programa ? (programa.duracionPeriodos || '') : '';
    document.getElementById('programaTipoPeriodo').value = programa ? (programa.tipoPeriodo || '') : '';
    document.getElementById('programaModalidad').value = programa ? (programa.modalidad || '') : '';
    document.getElementById('programaCreditos').value = programa ? (programa.creditosTotales || '') : '';
    document.getElementById('programaRvoe').value = programa ? (programa.rvoe || '') : '';
    document.getElementById('programaFechaRvoe').value = programa ? (programa.fechaRvoe || '') : '';
    document.getElementById('programaEstatus').value = programa ? (programa.estatus || 'ACTIVO') : 'ACTIVO';

    const boton = document.getElementById('btnGuardarPrograma');
    if (boton) {
        boton.textContent = programa ? 'Actualizar programa' : 'Guardar programa';
    }
}

function limpiarFormularioPrograma() {
    programaEditando = null;
    prepararFormularioPrograma(null);
}

function editarPrograma(id) {
    const programa = programasEducativos.find(item => item.id === id);
    if (!programa) return;

    programaEditando = programa;
    prepararFormularioPrograma(programa);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function guardarPrograma() {
    const form = document.getElementById('programaForm');
    if (!form) return;

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const duracionValue = document.getElementById('programaDuracion').value;
    const creditosValue = document.getElementById('programaCreditos').value;

    const programaData = {
        clave: document.getElementById('programaClave').value.trim(),
        claveDgp: document.getElementById('programaClaveDgp').value.trim() || null,
        nombre: document.getElementById('programaNombre').value.trim(),
        tipoPrograma: document.getElementById('programaTipo').value,
        duracionPeriodos: duracionValue ? parseInt(duracionValue, 10) : null,
        tipoPeriodo: document.getElementById('programaTipoPeriodo').value || null,
        modalidad: document.getElementById('programaModalidad').value || null,
        creditosTotales: creditosValue ? parseInt(creditosValue, 10) : null,
        rvoe: document.getElementById('programaRvoe').value.trim() || null,
        fechaRvoe: document.getElementById('programaFechaRvoe').value || null,
        estatus: document.getElementById('programaEstatus').value || 'ACTIVO'
    };

    const programaId = document.getElementById('programaId').value;
    const url = programaId ? `${API_URL}/programas-educativos/${programaId}` : `${API_URL}/programas-educativos`;
    const method = programaId ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method,
            headers: getHeaders(),
            body: JSON.stringify(programaData)
        });

        let data = null;
        try {
            data = await response.json();
        } catch (parseError) {
            data = null;
        }

        if (!response.ok) {
            const message = data && (data.error || data.message) ? (data.error || data.message) : 'Error al guardar programa';
            throw new Error(message);
        }

        limpiarFormularioPrograma();
        await cargarProgramas();
        alert(programaId ? 'Programa actualizado exitosamente' : 'Programa creado exitosamente');
    } catch (error) {
        console.error('Error al guardar programa:', error);
        alert(error.message || 'Error al guardar programa');
    }
}

async function eliminarPrograma(id) {
    try {
        const response = await fetch(`${API_URL}/programas-educativos/${id}`, {
            method: 'DELETE',
            headers: getHeaders(false)
        });

        if (!response.ok) {
            let message = 'Error al eliminar programa';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar programa';
            }
            throw new Error(message);
        }

        if (programaSeleccionadoId === id) {
            programaSeleccionadoId = null;
            if (typeof seleccionarPrograma === 'function') {
                seleccionarPrograma('Seleccione un programa...');
            }
        }

        await cargarProgramas();
        alert('Programa eliminado exitosamente');
    } catch (error) {
        console.error('Error al eliminar programa:', error);
        alert(error.message || 'Error al eliminar programa');
    }
}

function buscarPrograma() {
    const input = document.getElementById('buscarProgramaInput');
    if (!input) return;

    const termino = input.value.toLowerCase().trim();

    if (!termino) {
        renderizarTablaProgramas(programasEducativos);
        return;
    }

    const filtrados = programasEducativos.filter(programa => {
        return (
            (programa.clave && programa.clave.toLowerCase().includes(termino)) ||
            (programa.nombre && programa.nombre.toLowerCase().includes(termino)) ||
            (programa.claveDgp && programa.claveDgp.toLowerCase().includes(termino))
        );
    });

    renderizarTablaProgramas(filtrados);
}

function inicializarTablaProgramas() {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody || tablaProgramasInicializada) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-programa-id]');
        if (!row) return;

        const programaId = parseInt(row.dataset.programaId, 10);
        if (!programaId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarPrograma(programaId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar este programa? Esta accion no se puede deshacer.')) {
                    eliminarPrograma(programaId);
                }
            }
            return;
        }

        seleccionarProgramaPorId(programaId);
    });

    tablaProgramasInicializada = true;
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('programasSection')) return;

    // ✅ ESPERAR A QUE SESIÓN ESTÉ VALIDADA ANTES DE CARGAR DATOS
    function initProgramas() {
        inicializarTablaProgramas();
        cargarProgramas();

        const btnGuardar = document.getElementById('btnGuardarPrograma');
        if (btnGuardar) {
            btnGuardar.addEventListener('click', guardarPrograma);
        }

        const inputBuscar = document.getElementById('buscarProgramaInput');
        if (inputBuscar) {
            inputBuscar.addEventListener('input', buscarPrograma);
        }
    }

    // Si sesión ya está validada, cargar datos ahora
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        console.log('✅ [Programas] Sesión ya validada, cargando datos...');
        initProgramas();
    } else {
        // Si no, esperar al evento de validación
        console.log('⏳ [Programas] Esperando validación de sesión...');
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            console.log('✅ [Programas] Sesión validada (evento), cargando datos...');
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initProgramas();
        }, { once: true });
    }
});

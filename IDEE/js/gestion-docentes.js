// Gestion de docentes (maestros)

let docentes = [];
let docenteEditando = null;

function getHeadersDocentes(includeContentType = true) {
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

function escapeHtmlDocente(value) {
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

function formatGradoAcademico(grado) {
    const labels = {
        LICENCIATURA: 'Licenciatura',
        ESPECIALIDAD: 'Especialidad',
        MAESTRIA: 'Maestria',
        DOCTORADO: 'Doctorado'
    };
    return labels[grado] || 'N/A';
}

function getBadgeActivo(activo) {
    return activo
        ? '<span class="badge bg-success-subtle text-success">Activo</span>'
        : '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>';
}

async function cargarDocentes() {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_URL}/maestros`, {
            method: 'GET',
            headers: getHeadersDocentes()
        });

        if (!response.ok) {
            throw new Error('Error al cargar docentes');
        }

        docentes = await response.json();
        renderizarTablaDocentes(docentes);
    } catch (error) {
        console.error('Error al cargar docentes:', error);
        mostrarErrorTablaDocentes('Error al cargar la lista de docentes');
    }
}

function renderizarTablaDocentes(lista) {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage('No hay docentes registrados');
        } else {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No hay docentes registrados</td></tr>';
        }
        return;
    }

    tbody.innerHTML = lista.map(docente => {
        const nombreCompleto = [docente.nombre, docente.apellidoPaterno, docente.apellidoMaterno]
            .filter(Boolean)
            .join(' ');

        return `
            <tr data-docente-id="${docente.id}">
                <td><strong>${escapeHtmlDocente(docente.curp || 'N/A')}</strong></td>
                <td>${escapeHtmlDocente(nombreCompleto || 'N/A')}</td>
                <td>${escapeHtmlDocente(docente.etiqueta || 'N/A')}</td>
                <td>${formatGradoAcademico(docente.gradoAcademico)}</td>
                <td>${escapeHtmlDocente(docente.area || 'N/A')}</td>
                <td>${getBadgeActivo(docente.activo)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-secondary me-1" data-action="edit">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete">Eliminar</button>
                </td>
            </tr>
        `;
    }).join('');
}

function mostrarErrorTablaDocentes(mensaje) {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="7" class="text-center text-danger py-4">
                ${escapeHtmlDocente(mensaje)}
            </td>
        </tr>
    `;
}

function prepararFormularioDocente(docente) {
    const form = document.getElementById('maestroForm');
    if (!form) return;

    document.getElementById('maestroId').value = docente ? docente.id || '' : '';
    document.getElementById('maestroCurp').value = docente ? (docente.curp || '') : '';
    document.getElementById('maestroNombre').value = docente ? (docente.nombre || '') : '';
    document.getElementById('maestroApellidoPaterno').value = docente ? (docente.apellidoPaterno || '') : '';
    document.getElementById('maestroApellidoMaterno').value = docente ? (docente.apellidoMaterno || '') : '';
    document.getElementById('maestroGrado').value = docente ? (docente.gradoAcademico || '') : '';
    document.getElementById('maestroCedula').value = docente ? (docente.cedulaProfesional || '') : '';
    document.getElementById('maestroArea').value = docente ? (docente.area || '') : '';
    document.getElementById('maestroCorreoInstitucional').value = docente ? (docente.correoInstitucional || '') : '';
    document.getElementById('maestroCorreoPersonal').value = docente ? (docente.correoPersonal || '') : '';
    document.getElementById('maestroTelefono').value = docente ? (docente.telefono || '') : '';
    document.getElementById('maestroCodigoPostal').value = docente ? (docente.codigoPostal || '') : '';
    document.getElementById('maestroContactoNombre').value = docente ? (docente.nombreContactoEmergencia || '') : '';
    document.getElementById('maestroContactoTelefono').value = docente ? (docente.telefonoContactoEmergencia || '') : '';
    document.getElementById('maestroRfc').value = docente ? (docente.rfc || '') : '';
    document.getElementById('maestroRegimen').value = docente ? (docente.regimenFiscal || '') : '';
    document.getElementById('maestroTipo').value = docente ? (docente.tipoMaestro || '') : '';
    document.getElementById('maestroFechaAlta').value = docente ? (docente.fechaAlta || '') : '';
    document.getElementById('maestroActivo').value = docente ? String(docente.activo) : 'true';
    document.getElementById('maestroObservaciones').value = docente ? (docente.observaciones || '') : '';

    const etiquetaSelect = document.getElementById('etiquetaSelect');
    const etiquetaOtro = document.getElementById('etiquetaOtro');
    const campoOtro = document.getElementById('campoEtiquetaOtro');
    if (etiquetaSelect && etiquetaOtro && campoOtro) {
        const etiquetasFijas = ['Dr.', 'Dra.', 'Mtro.', 'Mtra.', 'Lic.', 'CDEO', 'CDEE', 'CDEP', 'LOEO'];
        if (docente && docente.etiqueta && !etiquetasFijas.includes(docente.etiqueta)) {
            etiquetaSelect.value = 'otro';
            etiquetaOtro.value = docente.etiqueta;
            campoOtro.classList.remove('d-none');
        } else {
            etiquetaSelect.value = docente ? (docente.etiqueta || '') : '';
            etiquetaOtro.value = '';
            campoOtro.classList.add('d-none');
        }
    }

    const archivos = document.getElementById('maestroAntecedentes');
    if (archivos) {
        archivos.value = '';
    }

    const boton = document.getElementById('btnGuardarMaestro');
    if (boton) {
        boton.textContent = docente ? 'Actualizar maestro' : 'Guardar maestro';
    }
}

function limpiarFormularioDocente() {
    docenteEditando = null;
    prepararFormularioDocente(null);
}

function editarDocente(id) {
    const docente = docentes.find(item => item.id === id);
    if (!docente) return;

    docenteEditando = docente;
    prepararFormularioDocente(docente);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function obtenerEtiquetaSeleccionada() {
    const etiquetaSelect = document.getElementById('etiquetaSelect');
    const etiquetaOtro = document.getElementById('etiquetaOtro');
    if (!etiquetaSelect) return null;

    if (etiquetaSelect.value === 'otro') {
        return etiquetaOtro ? etiquetaOtro.value.trim() || null : null;
    }
    return etiquetaSelect.value || null;
}

async function guardarDocente() {
    const form = document.getElementById('maestroForm');
    if (!form) return;

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const docenteData = {
        curp: document.getElementById('maestroCurp').value.trim(),
        nombre: document.getElementById('maestroNombre').value.trim(),
        apellidoPaterno: document.getElementById('maestroApellidoPaterno').value.trim(),
        apellidoMaterno: document.getElementById('maestroApellidoMaterno').value.trim(),
        etiqueta: obtenerEtiquetaSeleccionada(),
        gradoAcademico: document.getElementById('maestroGrado').value || null,
        cedulaProfesional: document.getElementById('maestroCedula').value.trim() || null,
        area: document.getElementById('maestroArea').value || null,
        correoInstitucional: document.getElementById('maestroCorreoInstitucional').value.trim(),
        correoPersonal: document.getElementById('maestroCorreoPersonal').value.trim() || null,
        telefono: document.getElementById('maestroTelefono').value.trim() || null,
        codigoPostal: document.getElementById('maestroCodigoPostal').value.trim() || null,
        nombreContactoEmergencia: document.getElementById('maestroContactoNombre').value.trim() || null,
        telefonoContactoEmergencia: document.getElementById('maestroContactoTelefono').value.trim() || null,
        rfc: document.getElementById('maestroRfc').value.trim() || null,
        regimenFiscal: document.getElementById('maestroRegimen').value || null,
        tipoMaestro: document.getElementById('maestroTipo').value || null,
        fechaAlta: document.getElementById('maestroFechaAlta').value || null,
        activo: document.getElementById('maestroActivo').value === 'true',
        observaciones: document.getElementById('maestroObservaciones').value.trim() || null
    };

    const docenteId = document.getElementById('maestroId').value;
    const url = docenteId ? `${API_URL}/maestros/${docenteId}` : `${API_URL}/maestros`;
    const method = docenteId ? 'PUT' : 'POST';

    const formData = new FormData();
    formData.append('maestro', new Blob([JSON.stringify(docenteData)], { type: 'application/json' }));

    const archivos = document.getElementById('maestroAntecedentes');
    if (archivos && archivos.files && archivos.files.length > 0) {
        Array.from(archivos.files).forEach(file => {
            formData.append('antecedentes', file);
        });
    }

    try {
        const response = await fetch(url, {
            method,
            headers: getHeadersDocentes(false),
            body: formData
        });

        let data = null;
        try {
            data = await response.json();
        } catch (parseError) {
            data = null;
        }

        if (!response.ok) {
            const message = data && (data.error || data.message) ? (data.error || data.message) : 'Error al guardar docente';
            throw new Error(message);
        }

        limpiarFormularioDocente();
        await cargarDocentes();
        alert(docenteId ? 'Docente actualizado exitosamente' : 'Docente creado exitosamente');
    } catch (error) {
        console.error('Error al guardar docente:', error);
        alert(error.message || 'Error al guardar docente');
    }
}

async function eliminarDocente(id) {
    try {
        const response = await fetch(`${API_URL}/maestros/${id}`, {
            method: 'DELETE',
            headers: getHeadersDocentes()
        });

        if (!response.ok) {
            let message = 'Error al eliminar docente';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar docente';
            }
            throw new Error(message);
        }

        await cargarDocentes();
        alert('Docente eliminado exitosamente');
    } catch (error) {
        console.error('Error al eliminar docente:', error);
        alert(error.message || 'Error al eliminar docente');
    }
}

function buscarDocente() {
    const input = document.getElementById('buscarDocenteInput');
    if (!input) return;

    const termino = input.value.toLowerCase().trim();

    if (!termino) {
        renderizarTablaDocentes(docentes);
        return;
    }

    const filtrados = docentes.filter(docente => {
        const nombreCompleto = [docente.nombre, docente.apellidoPaterno, docente.apellidoMaterno]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();

        return (
            (docente.curp && docente.curp.toLowerCase().includes(termino)) ||
            (nombreCompleto && nombreCompleto.includes(termino)) ||
            (docente.correoInstitucional && docente.correoInstitucional.toLowerCase().includes(termino)) ||
            (docente.correoPersonal && docente.correoPersonal.toLowerCase().includes(termino))
        );
    });

    renderizarTablaDocentes(filtrados);
}

function inicializarTablaDocentes() {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-docente-id]');
        if (!row) return;

        const docenteId = parseInt(row.dataset.docenteId, 10);
        if (!docenteId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarDocente(docenteId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar este docente? Esta accion no se puede deshacer.')) {
                    eliminarDocente(docenteId);
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('docentesSection')) return;

    // ✅ ESPERAR A QUE SESIÓN ESTÉ VALIDADA ANTES DE CARGAR DATOS
    function initDocentes() {
        inicializarTablaDocentes();
        cargarDocentes();

        const btnGuardar = document.getElementById('btnGuardarMaestro');
        if (btnGuardar) {
            btnGuardar.addEventListener('click', guardarDocente);
        }

        const inputBuscar = document.getElementById('buscarDocenteInput');
        if (inputBuscar) {
            inputBuscar.addEventListener('input', buscarDocente);
        }
    }

    // Si sesión ya está validada, cargar datos ahora
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        console.log('✅ [Docentes] Sesión ya validada, cargando datos...');
        initDocentes();
    } else {
        // Si no, esperar al evento de validación
        console.log('⏳ [Docentes] Esperando validación de sesión...');
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            console.log('✅ [Docentes] Sesión validada (evento), cargando datos...');
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initDocentes();
        }, { once: true });
    }
});

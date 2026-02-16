// Gestion de alumnos (admin)

let alumnosAdmin = [];

function getHeadersAlumnos(includeContentType = true) {
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

function escapeHtmlAlumno(value) {
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

function getBadgeEstatusAlumno(estatus) {
    const badges = {
        ACTIVA: '<span class="badge bg-success-subtle text-success">Activa</span>',
        INACTIVA: '<span class="badge bg-secondary-subtle text-secondary">Inactiva</span>',
        BAJA_TEMPORAL: '<span class="badge bg-warning-subtle text-warning">Baja temporal</span>',
        BAJA_DEFINITIVA: '<span class="badge bg-danger-subtle text-danger">Baja definitiva</span>',
        EGRESADO: '<span class="badge bg-info-subtle text-info">Egresado</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

function renderizarTablaAlumnosAdmin(lista) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage('No hay alumnos registrados');
        } else {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay alumnos registrados</td></tr>';
        }
        return;
    }

    tbody.innerHTML = lista.map(alumno => {
        const nombreCompleto = [alumno.nombre, alumno.apellidoPaterno, alumno.apellidoMaterno]
            .filter(Boolean)
            .join(' ');
        const programaNombre = alumno.programa ? alumno.programa.nombre : 'Sin programa';
        const cacheKey = alumno.fechaActualizacion || alumno.fechaCreacion || Date.now();
        const fotoUrl = alumno.fotoUrl ? `${API_URL}/alumnos/${alumno.id}/foto?v=${encodeURIComponent(cacheKey)}` : '';

        return `
            <tr data-alumno-id="${alumno.id}">
                <td>
                    ${fotoUrl ? `<img src="${escapeHtmlAlumno(fotoUrl)}" alt="Foto alumno" class="foto-alumno">` : ''}
                </td>
                <td><strong>${escapeHtmlAlumno(alumno.matricula || 'N/A')}</strong></td>
                <td>${escapeHtmlAlumno(nombreCompleto || 'N/A')}</td>
                <td>${escapeHtmlAlumno(programaNombre)}</td>
                <td>${getBadgeEstatusAlumno(alumno.estatusMatricula)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-secondary me-1" data-action="edit">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete">Eliminar</button>
                </td>
            </tr>
        `;
    }).join('');
}

function mostrarErrorTablaAlumnos(mensaje) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center text-danger py-4">
                ${escapeHtmlAlumno(mensaje)}
            </td>
        </tr>
    `;
}

async function cargarProgramasAlumnos() {
    try {
        const response = await fetch(`${API_URL}/programas-educativos`, {
            method: 'GET',
            headers: getHeadersAlumnos()
        });

        if (!response.ok) {
            return;
        }

        const programas = await response.json();
        llenarSelectProgramaAlumno(programas);
        llenarFiltroProgramaAlumno(programas);
    } catch (error) {
        console.error('Error al cargar programas:', error);
    }
}

function llenarSelectProgramaAlumno(programas) {
    const select = document.getElementById('alumnoProgramaId');
    if (!select) return;

    select.innerHTML = '<option value="">Selecciona...</option>';
    programas.forEach(programa => {
        const option = document.createElement('option');
        option.value = programa.id;
        option.textContent = `${programa.nombre} (${programa.tipoPrograma || ''})`;
        select.appendChild(option);
    });
}

function llenarFiltroProgramaAlumno(programas) {
    const select = document.getElementById('alumnoFiltroPrograma');
    if (!select) return;

    select.innerHTML = '<option value="">Todos</option>';
    programas.forEach(programa => {
        const option = document.createElement('option');
        option.value = programa.id;
        option.textContent = programa.nombre;
        select.appendChild(option);
    });
}

async function cargarAlumnosAdmin() {
    try {
        const response = await fetch(`${API_URL}/alumnos`, {
            method: 'GET',
            headers: getHeadersAlumnos()
        });

        if (!response.ok) {
            throw new Error('Error al cargar alumnos');
        }

        alumnosAdmin = await response.json();
        renderizarTablaAlumnosAdmin(alumnosAdmin);
    } catch (error) {
        console.error('Error al cargar alumnos:', error);
        mostrarErrorTablaAlumnos('Error al cargar la lista de alumnos');
    }
}

function limpiarFormularioAlumnoAdmin() {
    document.getElementById('alumnoForm').reset();
    document.getElementById('alumnoId').value = '';
    const archivos = document.querySelectorAll('#alumnoForm input[type="file"]');
    archivos.forEach(input => {
        input.value = '';
    });
}

function editarAlumnoAdmin(id) {
    const alumno = alumnosAdmin.find(item => item.id === id);
    if (!alumno) return;

    document.getElementById('alumnoId').value = alumno.id || '';
    document.getElementById('alumnoMatricula').value = alumno.matricula || '';
    document.getElementById('alumnoProgramaId').value = alumno.programa ? alumno.programa.id : '';
    document.getElementById('alumnoCiclo').value = alumno.cicloEscolar || '';
    document.getElementById('alumnoTurno').value = alumno.turno || '';
    document.getElementById('alumnoEstatus').value = alumno.estatusMatricula || 'ACTIVA';
    document.getElementById('alumnoNombre').value = alumno.nombre || '';
    document.getElementById('alumnoApellidoPaterno').value = alumno.apellidoPaterno || '';
    document.getElementById('alumnoApellidoMaterno').value = alumno.apellidoMaterno || '';
    document.getElementById('alumnoCurp').value = alumno.curp || '';
    document.getElementById('alumnoCorreoInstitucional').value = alumno.correoInstitucional || '';
    document.getElementById('alumnoCorreoPersonal').value = alumno.correoPersonal || '';
    document.getElementById('alumnoTelefono').value = alumno.telefono || '';
    document.getElementById('alumnoCodigoPostal').value = alumno.codigoPostal || '';
    document.getElementById('alumnoContactoNombre').value = alumno.nombreContactoEmergencia || '';
    document.getElementById('alumnoContactoTelefono').value = alumno.telefonoContactoEmergencia || '';
    document.getElementById('alumnoObservaciones').value = alumno.observaciones || '';

    const archivos = document.querySelectorAll('#alumnoForm input[type="file"]');
    archivos.forEach(input => {
        input.value = '';
    });

    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function obtenerArchivo(inputId) {
    const input = document.getElementById(inputId);
    if (!input || !input.files || input.files.length === 0) return null;
    return input.files[0];
}

async function guardarAlumnoAdmin() {
    const form = document.getElementById('alumnoForm');
    if (!form) return;

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const alumnoData = {
        matricula: document.getElementById('alumnoMatricula').value.trim(),
        nombre: document.getElementById('alumnoNombre').value.trim(),
        apellidoPaterno: document.getElementById('alumnoApellidoPaterno').value.trim(),
        apellidoMaterno: document.getElementById('alumnoApellidoMaterno').value.trim(),
        curp: document.getElementById('alumnoCurp').value.trim().toUpperCase(),
        correoInstitucional: document.getElementById('alumnoCorreoInstitucional').value.trim() || null,
        correoPersonal: document.getElementById('alumnoCorreoPersonal').value.trim() || null,
        telefono: document.getElementById('alumnoTelefono').value.trim() || null,
        codigoPostal: document.getElementById('alumnoCodigoPostal').value.trim() || null,
        cicloEscolar: document.getElementById('alumnoCiclo').value || null,
        turno: document.getElementById('alumnoTurno').value || null,
        estatusMatricula: document.getElementById('alumnoEstatus').value || 'ACTIVA',
        nombreContactoEmergencia: document.getElementById('alumnoContactoNombre').value.trim() || null,
        telefonoContactoEmergencia: document.getElementById('alumnoContactoTelefono').value.trim() || null,
        observaciones: document.getElementById('alumnoObservaciones').value.trim() || null,
        programa: document.getElementById('alumnoProgramaId').value
            ? { id: parseInt(document.getElementById('alumnoProgramaId').value, 10) }
            : null
    };

    const alumnoId = document.getElementById('alumnoId').value;
    const url = alumnoId ? `${API_URL}/alumnos/${alumnoId}` : `${API_URL}/alumnos`;
    const method = alumnoId ? 'PUT' : 'POST';

    const formData = new FormData();
    formData.append('alumno', new Blob([JSON.stringify(alumnoData)], { type: 'application/json' }));

    const foto = obtenerArchivo('alumnoFoto');
    if (foto) {
        formData.append('foto', foto);
    }

    const documentos = [
        { id: 'alumnoDocActa', tipo: 'ACTA_NACIMIENTO' },
        { id: 'alumnoDocCertificado', tipo: 'CERTIFICADO_BACHILLERATO' },
        { id: 'alumnoDocTitulo', tipo: 'TITULO_PROFESIONAL' },
        { id: 'alumnoDocIne', tipo: 'INE' },
        { id: 'alumnoDocConstanciaFiscal', tipo: 'CONSTANCIA_SITUACION_FISCAL' },
        { id: 'alumnoDocCurp', tipo: 'CURP' }
    ];

    documentos.forEach(doc => {
        const file = obtenerArchivo(doc.id);
        if (file) {
            formData.append('documentos', file);
            formData.append('documentosTipos', doc.tipo);
        }
    });

    try {
        const response = await fetch(url, {
            method,
            headers: getHeadersAlumnos(false),
            body: formData
        });

        let data = null;
        try {
            data = await response.json();
        } catch (parseError) {
            data = null;
        }

        if (!response.ok) {
            const message = data && (data.error || data.message) ? (data.error || data.message) : 'Error al guardar alumno';
            throw new Error(message);
        }

        limpiarFormularioAlumnoAdmin();
        await cargarAlumnosAdmin();
        alert(alumnoId ? 'Alumno actualizado exitosamente' : 'Alumno creado exitosamente');
    } catch (error) {
        console.error('Error al guardar alumno:', error);
        alert(error.message || 'Error al guardar alumno');
    }
}

async function eliminarAlumnoAdmin(id) {
    try {
        const response = await fetch(`${API_URL}/alumnos/${id}`, {
            method: 'DELETE',
            headers: getHeadersAlumnos()
        });

        if (!response.ok) {
            let message = 'Error al eliminar alumno';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar alumno';
            }
            throw new Error(message);
        }

        await cargarAlumnosAdmin();
        alert('Alumno eliminado exitosamente');
    } catch (error) {
        console.error('Error al eliminar alumno:', error);
        alert(error.message || 'Error al eliminar alumno');
    }
}

async function buscarAlumnosAdmin() {
    const matricula = document.getElementById('alumnoFiltroMatricula').value.trim();
    const curp = document.getElementById('alumnoFiltroCurp').value.trim();

    if (matricula) {
        try {
            const response = await fetch(`${API_URL}/alumnos/matricula/${encodeURIComponent(matricula)}`, {
                method: 'GET',
                headers: getHeadersAlumnos()
            });
            if (!response.ok) {
                throw new Error('No se encontró el alumno');
            }
            const alumno = await response.json();
            renderizarTablaAlumnosAdmin([alumno]);
            return;
        } catch (error) {
            console.error('Error al buscar por matrícula:', error);
            renderizarTablaAlumnosAdmin([]);
            return;
        }
    }

    if (curp) {
        try {
            const response = await fetch(`${API_URL}/alumnos/curp/${encodeURIComponent(curp)}`, {
                method: 'GET',
                headers: getHeadersAlumnos()
            });
            if (!response.ok) {
                throw new Error('No se encontró el alumno');
            }
            const alumno = await response.json();
            renderizarTablaAlumnosAdmin([alumno]);
            return;
        } catch (error) {
            console.error('Error al buscar por CURP:', error);
            renderizarTablaAlumnosAdmin([]);
            return;
        }
    }

    const filtroPrograma = document.getElementById('alumnoFiltroPrograma').value;
    const filtroCiclo = document.getElementById('alumnoFiltroCiclo').value;
    const filtroSexo = document.getElementById('alumnoFiltroSexo').value;
    const filtroNombre = document.getElementById('alumnoFiltroNombre').value.toLowerCase().trim();
    const filtroApellidoPaterno = document.getElementById('alumnoFiltroApellidoPaterno').value.toLowerCase().trim();
    const filtroApellidoMaterno = document.getElementById('alumnoFiltroApellidoMaterno').value.toLowerCase().trim();
    const filtroEstatus = document.getElementById('alumnoFiltroEstatus').value;

    const filtrados = alumnosAdmin.filter(alumno => {
        if (filtroPrograma && (!alumno.programa || String(alumno.programa.id) !== filtroPrograma)) {
            return false;
        }
        if (filtroCiclo && alumno.cicloEscolar !== filtroCiclo) {
            return false;
        }
        if (filtroSexo && alumno.sexo !== filtroSexo) {
            return false;
        }
        if (filtroEstatus && alumno.estatusMatricula !== filtroEstatus) {
            return false;
        }
        if (filtroNombre && (!alumno.nombre || !alumno.nombre.toLowerCase().includes(filtroNombre))) {
            return false;
        }
        if (filtroApellidoPaterno && (!alumno.apellidoPaterno || !alumno.apellidoPaterno.toLowerCase().includes(filtroApellidoPaterno))) {
            return false;
        }
        if (filtroApellidoMaterno && (!alumno.apellidoMaterno || !alumno.apellidoMaterno.toLowerCase().includes(filtroApellidoMaterno))) {
            return false;
        }
        return true;
    });

    renderizarTablaAlumnosAdmin(filtrados);
}

function limpiarFiltrosAdmin() {
    document.getElementById('alumnoSearchForm').reset();
    renderizarTablaAlumnosAdmin(alumnosAdmin);
}

function inicializarTablaAlumnosAdmin() {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-alumno-id]');
        if (!row) return;

        const alumnoId = parseInt(row.dataset.alumnoId, 10);
        if (!alumnoId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarAlumnoAdmin(alumnoId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar este alumno? Esta accion no se puede deshacer.')) {
                    eliminarAlumnoAdmin(alumnoId);
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('alumnosSection')) return;

    // ✅ ESPERAR A QUE SESIÓN ESTÉ VALIDADA ANTES DE CARGAR DATOS
    function initAlumnos() {
        inicializarTablaAlumnosAdmin();
        cargarProgramasAlumnos();
        cargarAlumnosAdmin();

        const btnGuardar = document.getElementById('btnGuardarAlumnoAdmin');
        if (btnGuardar) {
            btnGuardar.addEventListener('click', guardarAlumnoAdmin);
        }

        const btnBuscar = document.getElementById('btnBuscarAlumnos');
        if (btnBuscar) {
            btnBuscar.addEventListener('click', buscarAlumnosAdmin);
        }

        const btnLimpiar = document.getElementById('btnLimpiarFiltros');
        if (btnLimpiar) {
            btnLimpiar.addEventListener('click', limpiarFiltrosAdmin);
        }

        const btnNuevo = document.getElementById('btnNuevoAlumno');
        if (btnNuevo) {
            btnNuevo.addEventListener('click', limpiarFormularioAlumnoAdmin);
        }
    }

    // Si sesión ya está validada, cargar datos ahora
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        console.log('✅ [Alumnos Admin] Sesión ya validada, cargando datos...');
        initAlumnos();
    } else {
        // Si no, esperar al evento de validación
        console.log('⏳ [Alumnos Admin] Esperando validación de sesión...');
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            console.log('✅ [Alumnos Admin] Sesión validada (evento), cargando datos...');
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initAlumnos();
        }, { once: true });
    }
});

```

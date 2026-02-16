// Variables globales
let alumnos = [];
let programasEducativos = [];
let alumnoEditando = null;

// Función para obtener headers con autenticación opcional
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

// ==================== CARGAR DATOS ====================

/**
 * Cargar todos los alumnos
 */
async function cargarAlumnos() {
    try {
        alumnos = await authFetch('/alumnos');
        renderizarTablaAlumnos(alumnos);
    } catch (error) {
        console.error('Error al cargar alumnos:', error);
        mostrarErrorTabla('Error al cargar la lista de alumnos');
    }
}

/**
 * Cargar programas educativos para el select
 */
async function cargarProgramasEducativos() {
    try {
        programasEducativos = await authFetch('/programas-educativos');
        llenarSelectProgramas();
    } catch (error) {
        console.error('Error al cargar programas:', error);
    }
}

/**
 * Llenar el select de programas educativos
 */
function llenarSelectProgramas() {
    const select = document.getElementById('alumnoProgramaId');
    if (!select) return;

    select.innerHTML = '<option value="">Seleccionar...</option>';

    programasEducativos.forEach(programa => {
        const option = document.createElement('option');
        option.value = programa.id;
        option.textContent = `${programa.nombre} (${programa.tipoPrograma || ''})`;
        select.appendChild(option);
    });
}

// ==================== RENDERIZAR TABLA ====================

/**
 * Renderizar la tabla de alumnos
 */
function renderizarTablaAlumnos(lista) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    if (lista.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-4">
                    <i class="bi bi-inbox"></i> No hay alumnos registrados
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = lista.map(alumno => `
        <tr>
            <td><strong>${alumno.matricula || 'N/A'}</strong></td>
            <td>${alumno.nombre} ${alumno.apellidoPaterno} ${alumno.apellidoMaterno}</td>
            <td><small>${alumno.curp || 'N/A'}</small></td>
            <td><small>${alumno.programa ? alumno.programa.nombre : 'Sin programa'}</small></td>
            <td>${getBadgeEstatus(alumno.estatusMatricula)}</td>
            <td>
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editarAlumno(${alumno.id})"
                        title="Editar">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="confirmarEliminarAlumno(${alumno.id}, '${alumno.nombre} ${alumno.apellidoPaterno}')"
                        title="Eliminar">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

/**
 * Obtener badge de estatus
 */
function getBadgeEstatus(estatus) {
    const badges = {
        'ACTIVA': '<span class="badge bg-success">Activa</span>',
        'INACTIVA': '<span class="badge bg-secondary">Inactiva</span>',
        'BAJA_TEMPORAL': '<span class="badge bg-warning">Baja Temporal</span>',
        'BAJA_DEFINITIVA': '<span class="badge bg-danger">Baja Definitiva</span>',
        'EGRESADO': '<span class="badge bg-info">Egresado</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary">N/A</span>';
}

/**
 * Mostrar error en la tabla
 */
function mostrarErrorTabla(mensaje) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center text-danger py-4">
                <i class="bi bi-exclamation-triangle"></i> ${mensaje}
            </td>
        </tr>
    `;
}

// ==================== CREAR/EDITAR ALUMNO ====================

/**
 * Abrir modal para nuevo alumno
 */
function nuevoAlumno() {
    alumnoEditando = null;
    document.getElementById('modalAlumnoTitle').innerHTML = '<i class="bi bi-person-plus"></i> Nuevo Alumno';
    limpiarFormularioAlumno();
    ocultarAlerta('alertaAlumno');
}

/**
 * Editar alumno existente
 */
async function editarAlumno(id) {
    try {
        const alumno = await authFetch(`/alumnos/${id}`);
        alumnoEditando = alumno;

        // Cambiar título del modal
        document.getElementById('modalAlumnoTitle').innerHTML = '<i class="bi bi-pencil"></i> Editar Alumno';

        // Llenar formulario
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
        document.getElementById('alumnoSexo').value = alumno.sexo || '';
        document.getElementById('alumnoFechaNacimiento').value = alumno.fechaNacimiento || '';
        document.getElementById('alumnoCodigoPostal').value = alumno.codigoPostal || '';

        document.getElementById('alumnoCorreoInstitucional').value = alumno.correoInstitucional || '';
        document.getElementById('alumnoCorreoPersonal').value = alumno.correoPersonal || '';
        document.getElementById('alumnoTelefono').value = alumno.telefono || '';

        document.getElementById('alumnoNombreContacto').value = alumno.nombreContactoEmergencia || '';
        document.getElementById('alumnoTelefonoContacto').value = alumno.telefonoContactoEmergencia || '';

        document.getElementById('alumnoObservaciones').value = alumno.observaciones || '';

        ocultarAlerta('alertaAlumno');

        // Abrir modal
        const modal = new bootstrap.Modal(document.getElementById('modalAlumno'));
        modal.show();

    } catch (error) {
        console.error('Error al cargar alumno:', error);
        alert('Error al cargar los datos del alumno');
    }
}

/**
 * Guardar alumno (crear o actualizar)
 */
async function guardarAlumno() {
    try {
        // Validar formulario
        const form = document.getElementById('formAlumno');
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        // Recopilar datos del formulario
        const programaId = document.getElementById('alumnoProgramaId').value;
        const alumnoData = {
            matricula: document.getElementById('alumnoMatricula').value.trim(),
            nombre: document.getElementById('alumnoNombre').value.trim(),
            apellidoPaterno: document.getElementById('alumnoApellidoPaterno').value.trim(),
            apellidoMaterno: document.getElementById('alumnoApellidoMaterno').value.trim(),
            curp: document.getElementById('alumnoCurp').value.trim().toUpperCase(),
            sexo: document.getElementById('alumnoSexo').value || null,
            fechaNacimiento: document.getElementById('alumnoFechaNacimiento').value || null,
            codigoPostal: document.getElementById('alumnoCodigoPostal').value.trim() || null,
            correoInstitucional: document.getElementById('alumnoCorreoInstitucional').value.trim() || null,
            correoPersonal: document.getElementById('alumnoCorreoPersonal').value.trim() || null,
            telefono: document.getElementById('alumnoTelefono').value.trim() || null,
            cicloEscolar: document.getElementById('alumnoCiclo').value.trim() || null,
            turno: document.getElementById('alumnoTurno').value || null,
            estatusMatricula: document.getElementById('alumnoEstatus').value,
            nombreContactoEmergencia: document.getElementById('alumnoNombreContacto').value.trim() || null,
            telefonoContactoEmergencia: document.getElementById('alumnoTelefonoContacto').value.trim() || null,
            observaciones: document.getElementById('alumnoObservaciones').value.trim() || null,
            programa: programaId ? { id: parseInt(programaId) } : null
        };

        const alumnoId = document.getElementById('alumnoId').value;
        const path = alumnoId ? `/alumnos/${alumnoId}` : '/alumnos';
        const method = alumnoId ? 'PUT' : 'POST';

        const data = await authFetch(path, {
            method: method,
            body: JSON.stringify(alumnoData)
        });

        // Cerrar modal
        const modal = bootstrap.Modal.getInstance(document.getElementById('modalAlumno'));
        modal.hide();

        // Recargar lista
        await cargarAlumnos();

        // Mostrar mensaje de éxito
        alert(alumnoId ? 'Alumno actualizado exitosamente' : 'Alumno creado exitosamente');

    } catch (error) {
        console.error('Error al guardar alumno:', error);
        mostrarAlerta('alertaAlumno', error.message, 'danger');
    }
}

/**
 * Limpiar formulario de alumno
 */
function limpiarFormularioAlumno() {
    document.getElementById('formAlumno').reset();
    document.getElementById('alumnoId').value = '';
    document.getElementById('alumnoEstatus').value = 'ACTIVA';
    ocultarAlerta('alertaAlumno');
}

// ==================== ELIMINAR ALUMNO ====================

/**
 * Confirmar eliminación de alumno
 */
function confirmarEliminarAlumno(id, nombre) {
    if (confirm(`¿Está seguro de eliminar al alumno ${nombre}?\n\nEsta acción no se puede deshacer.`)) {
        eliminarAlumno(id);
    }
}

/**
 * Eliminar alumno
 */
async function eliminarAlumno(id) {
    try {
        await authFetch(`/alumnos/${id}`, { method: 'DELETE' });
        await cargarAlumnos();
        alert('Alumno eliminado exitosamente');

    } catch (error) {
        console.error('Error al eliminar alumno:', error);
        alert('Error al eliminar alumno: ' + error.message);
    }
}

// ==================== BÚSQUEDA ====================

/**
 * Buscar alumnos (filtrado local)
 */
function buscarAlumno() {
    const input = document.getElementById('buscarAlumnoInput');
    if (!input) return;

    const termino = input.value.toLowerCase().trim();

    if (!termino) {
        renderizarTablaAlumnos(alumnos);
        return;
    }

    const filtrados = alumnos.filter(alumno => {
        return (
            (alumno.matricula && alumno.matricula.toLowerCase().includes(termino)) ||
            (alumno.nombre && alumno.nombre.toLowerCase().includes(termino)) ||
            (alumno.apellidoPaterno && alumno.apellidoPaterno.toLowerCase().includes(termino)) ||
            (alumno.apellidoMaterno && alumno.apellidoMaterno.toLowerCase().includes(termino)) ||
            (alumno.curp && alumno.curp.toLowerCase().includes(termino))
        );
    });

    renderizarTablaAlumnos(filtrados);
}

// ==================== UTILIDADES ====================

/**
 * Mostrar alerta
 */
function mostrarAlerta(elementoId, mensaje, tipo) {
    const alerta = document.getElementById(elementoId);
    if (!alerta) return;

    alerta.className = `alert alert-${tipo}`;
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
}

/**
 * Ocultar alerta
 */
function ocultarAlerta(elementoId) {
    const alerta = document.getElementById(elementoId);
    if (!alerta) return;

    alerta.classList.add('d-none');
}

// ==================== EVENT LISTENERS ====================

// Esperar a que el DOM esté cargado
document.addEventListener('DOMContentLoaded', function() {
    // Cargar datos iniciales si estamos en la página de secretaría académica
    if (document.getElementById('alumnosSection')) {
        cargarProgramasEducativos();
        cargarAlumnos();

        // Event listener para botón guardar
        const btnGuardar = document.getElementById('btnGuardarAlumno');
        if (btnGuardar) {
            btnGuardar.addEventListener('click', guardarAlumno);
        }

        // Event listener para búsqueda
        const inputBuscar = document.getElementById('buscarAlumnoInput');
        if (inputBuscar) {
            inputBuscar.addEventListener('input', buscarAlumno);
        }

        // Event listener para limpiar formulario al cerrar modal
        const modalAlumno = document.getElementById('modalAlumno');
        if (modalAlumno) {
            modalAlumno.addEventListener('hidden.bs.modal', function() {
                limpiarFormularioAlumno();
            });
        }
    }
});
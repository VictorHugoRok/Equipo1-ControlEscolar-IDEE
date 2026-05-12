// Módulo de gestión de certificados electrónicos

let alumnoCertificado = null;
let kardexAlumno = [];
let certificadosListados = [];
let busquedaCertController = null;

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('certificadosSection')) return;

    function initCertificados() {
        inicializarEventosCertificados();
    }

    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        initCertificados();
    } else {
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initCertificados();
        }, { once: true });
    }
});

function inicializarEventosCertificados() {
    const fechaInput = document.getElementById('certFechaExpedicion');
    if (fechaInput && !fechaInput.value) {
        fechaInput.value = new Date().toISOString().split('T')[0];
    }

    const btnBuscar = document.getElementById('btnBuscarAlumnoCert');
    if (btnBuscar) {
        btnBuscar.addEventListener('click', buscarAlumnoCertificado);
    }

    const inputBuscar = document.getElementById('buscarAlumnoCertInput');
    if (inputBuscar) {
        inputBuscar.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') buscarAlumnoCertificado();
        });
    }

    const formGenerar = document.getElementById('formGenerarCertificado');
    if (formGenerar) {
        formGenerar.addEventListener('submit', function (e) {
            e.preventDefault();
            generarCertificado();
        });
    }

    const btnNuevaBusqueda = document.getElementById('btnNuevaBusquedaCert');
    if (btnNuevaBusqueda) {
        btnNuevaBusqueda.addEventListener('click', limpiarBusquedaCertificado);
    }

    // Delegación de eventos en tabla de certificados
    const tablaCerts = document.getElementById('tablaCertificadosBody');
    if (tablaCerts) {
        tablaCerts.addEventListener('click', function (event) {
            const btn = event.target.closest('button[data-action]');
            if (!btn) return;
            const fila = btn.closest('tr[data-cert-id]');
            if (!fila) return;
            const certId = parseInt(fila.dataset.certId, 10);
            const action = btn.dataset.action;

            if (action === 'descargar') {
                descargarCertificado(certId);
            } else if (action === 'eliminar') {
                if (confirm('¿Estás seguro de eliminar este certificado? Esta acción no se puede deshacer.')) {
                    eliminarCertificado(certId);
                }
            }
        });
    }
}

// ==================== BÚSQUEDA DE ALUMNO ====================

async function buscarAlumnoCertificado() {
    const input = document.getElementById('buscarAlumnoCertInput');
    if (!input) return;

    const criterio = input.value.trim();
    if (!criterio) {
        showError('Ingresa una matrícula o CURP para buscar');
        return;
    }

    if (busquedaCertController) busquedaCertController.abort();
    busquedaCertController = new AbortController();
    const { signal } = busquedaCertController;

    const btnBuscar = document.getElementById('btnBuscarAlumnoCert');
    if (btnBuscar) btnBuscar.disabled = true;

    mostrarCargandoAlumnoCert();

    try {
        const headers = getHeaders();
        let url = `${API_URL}/alumnos/matricula/${encodeURIComponent(criterio)}`;
        let response = await fetch(url, { headers, signal });

        if (!response.ok && criterio.length === 18) {
            url = `${API_URL}/alumnos/curp/${encodeURIComponent(criterio)}`;
            response = await fetch(url, { headers, signal });
        }

        if (response.ok) {
            const alumno = await response.json();
            alumnoCertificado = alumno;
            mostrarInfoAlumnoCert(alumno);
            await cargarKardexAlumno(alumno.id);
            await cargarCertificadosAlumno(alumno.id);
        } else {
            mostrarAlumnoCertNoEncontrado();
        }
    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('[Certificados] Error en búsqueda:', error);
        mostrarAlumnoCertNoEncontrado();
    } finally {
        if (btnBuscar) btnBuscar.disabled = false;
        busquedaCertController = null;
    }
}

function mostrarCargandoAlumnoCert() {
    const panel = document.getElementById('certAlumnoPanel');
    if (panel) panel.classList.add('d-none');
    const noEncontrado = document.getElementById('certAlumnoNoEncontrado');
    if (noEncontrado) noEncontrado.classList.add('d-none');
}

function mostrarAlumnoCertNoEncontrado() {
    const panel = document.getElementById('certAlumnoPanel');
    if (panel) panel.classList.add('d-none');
    const noEncontrado = document.getElementById('certAlumnoNoEncontrado');
    if (noEncontrado) noEncontrado.classList.remove('d-none');
    alumnoCertificado = null;
    kardexAlumno = [];
    renderizarKardex([]);
    renderizarCertificadosAlumno([]);
}

function mostrarInfoAlumnoCert(alumno) {
    document.getElementById('certAlumnoNoEncontrado').classList.add('d-none');
    document.getElementById('certAlumnoPanel').classList.remove('d-none');

    const nombre = alumno.nombreCompleto ||
        [alumno.nombre, alumno.apellidoPaterno, alumno.apellidoMaterno].filter(Boolean).join(' ');

    const nombreEl = document.getElementById('certNombreAlumno');
    if (nombreEl) nombreEl.textContent = nombre || 'N/A';

    const matriculaEl = document.getElementById('certMatriculaAlumno');
    if (matriculaEl) matriculaEl.textContent = alumno.matricula || 'N/A';

    const curpEl = document.getElementById('certCurpAlumno');
    if (curpEl) curpEl.textContent = alumno.curp || 'N/A';

    const programaEl = document.getElementById('certProgramaAlumno');
    if (programaEl) programaEl.textContent = alumno.programa ? alumno.programa.nombre : 'N/A';

    const estatusEl = document.getElementById('certEstatusAlumno');
    if (estatusEl) {
        estatusEl.textContent = alumno.estatusMatricula || 'N/A';
        estatusEl.className = `badge ${
            alumno.estatusMatricula === 'ACTIVA' ? 'bg-success' :
            alumno.estatusMatricula === 'EGRESADO' ? 'bg-info' : 'bg-secondary'
        }`;
    }

    const hiddenId = document.getElementById('certAlumnoIdHidden');
    if (hiddenId) hiddenId.value = alumno.id || '';
}

function limpiarBusquedaCertificado() {
    alumnoCertificado = null;
    kardexAlumno = [];

    const input = document.getElementById('buscarAlumnoCertInput');
    if (input) input.value = '';

    document.getElementById('certAlumnoPanel').classList.add('d-none');
    document.getElementById('certAlumnoNoEncontrado').classList.add('d-none');
    renderizarKardex([]);
    renderizarCertificadosAlumno([]);
}

// ==================== KARDEX / CALIFICACIONES ====================

async function cargarKardexAlumno(alumnoId) {
    const seccionKardex = document.getElementById('certKardexSection');
    if (seccionKardex) seccionKardex.classList.add('d-none');

    try {
        const response = await fetch(`${API_URL}/alumnos/${alumnoId}/kardex`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            console.warn('[Certificados] No se pudo cargar el kardex:', response.status);
            return;
        }

        kardexAlumno = await response.json();
        renderizarKardex(kardexAlumno);

        if (seccionKardex) seccionKardex.classList.remove('d-none');
    } catch (error) {
        console.error('[Certificados] Error al cargar kardex:', error);
    }
}

function renderizarKardex(periodos) {
    const contenedor = document.getElementById('certKardexContenedor');
    if (!contenedor) return;

    if (!Array.isArray(periodos) || periodos.length === 0) {
        contenedor.innerHTML = '<p class="text-muted text-center py-3">No hay calificaciones registradas para este alumno.</p>';
        return;
    }

    const fragment = document.createDocumentFragment();

    periodos.forEach(periodo => {
        const card = document.createElement('div');
        card.className = 'card mb-3';

        const header = document.createElement('div');
        header.className = 'card-header py-2';

        const titulo = document.createElement('h6');
        titulo.className = 'mb-0 fw-semibold';
        titulo.textContent = periodo.nombre || periodo.periodo || `Período ${periodo.numero || ''}`;
        header.appendChild(titulo);
        card.appendChild(header);

        const body = document.createElement('div');
        body.className = 'card-body p-0';

        const table = document.createElement('table');
        table.className = 'table table-sm table-hover mb-0';

        const thead = document.createElement('thead');
        thead.className = 'table-light';
        thead.innerHTML = `
            <tr>
                <th>Asignatura</th>
                <th class="text-center">Créditos</th>
                <th class="text-center">Calificación</th>
                <th class="text-center">Estatus</th>
            </tr>
        `;
        table.appendChild(thead);

        const tbody = document.createElement('tbody');
        const calificaciones = periodo.calificaciones || periodo.asignaturas || [];

        calificaciones.forEach(cal => {
            const tr = document.createElement('tr');

            const tdNombre = document.createElement('td');
            tdNombre.textContent = cal.asignatura?.nombre || cal.nombreAsignatura || cal.nombre || 'N/A';

            const tdCreditos = document.createElement('td');
            tdCreditos.className = 'text-center';
            tdCreditos.textContent = cal.asignatura?.creditos ?? cal.creditos ?? '-';

            const tdCalif = document.createElement('td');
            tdCalif.className = 'text-center fw-semibold';
            const calif = cal.calificacionFinal ?? cal.calificacion ?? cal.promedio;
            tdCalif.textContent = calif != null ? calif : '-';
            if (calif != null && calif < 6) tdCalif.classList.add('text-danger');

            const tdEstatus = document.createElement('td');
            tdEstatus.className = 'text-center';
            const estatus = cal.estatus || (calif != null ? (calif >= 6 ? 'APROBADA' : 'REPROBADA') : 'PENDIENTE');
            const badgeClass = estatus === 'APROBADA' ? 'bg-success-subtle text-success' :
                               estatus === 'REPROBADA' ? 'bg-danger-subtle text-danger' :
                               'bg-warning-subtle text-warning';
            const badge = document.createElement('span');
            badge.className = `badge ${badgeClass}`;
            badge.textContent = estatus;
            tdEstatus.appendChild(badge);

            tr.appendChild(tdNombre);
            tr.appendChild(tdCreditos);
            tr.appendChild(tdCalif);
            tr.appendChild(tdEstatus);
            tbody.appendChild(tr);
        });

        if (calificaciones.length === 0) {
            const trVacio = document.createElement('tr');
            const tdVacio = document.createElement('td');
            tdVacio.colSpan = 4;
            tdVacio.className = 'text-center text-muted py-2';
            tdVacio.textContent = 'Sin asignaturas registradas en este período';
            trVacio.appendChild(tdVacio);
            tbody.appendChild(trVacio);
        }

        table.appendChild(tbody);
        body.appendChild(table);
        card.appendChild(body);
        fragment.appendChild(card);
    });

    contenedor.replaceChildren(fragment);
}

// ==================== GENERACIÓN DE CERTIFICADO ====================

async function generarCertificado() {
    const alumnoId = document.getElementById('certAlumnoIdHidden')?.value;
    if (!alumnoId) {
        showError('Primero busca y selecciona un alumno');
        return;
    }

    const tipoCertificado = document.getElementById('certTipo')?.value;
    if (!tipoCertificado) {
        showError('Selecciona el tipo de certificado');
        return;
    }

    const fechaExpedicion = document.getElementById('certFechaExpedicion')?.value;
    if (!fechaExpedicion) {
        showError('Indica la fecha de expedición');
        return;
    }

    const btnGenerar = document.getElementById('btnGenerarCertificado');
    if (btnGenerar) {
        btnGenerar.disabled = true;
        btnGenerar.textContent = 'Generando...';
    }

    try {
        const body = {
            alumnoId: parseInt(alumnoId, 10),
            tipoCertificado: tipoCertificado,
            fechaExpedicion: fechaExpedicion,
            observaciones: document.getElementById('certObservaciones')?.value?.trim() || null
        };

        const response = await fetch(`${API_URL}/certificados`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(body)
        });

        let data = null;
        try { data = await response.json(); } catch (_) { data = null; }

        if (!response.ok) {
            const mensaje = data?.error || data?.message || 'Error al generar el certificado';
            throw new Error(mensaje);
        }

        showSuccess('Certificado generado exitosamente');
        document.getElementById('certObservaciones').value = '';
        await cargarCertificadosAlumno(parseInt(alumnoId, 10));
    } catch (error) {
        console.error('[Certificados] Error al generar:', error);
        showError(error.message || 'Error al generar el certificado');
    } finally {
        if (btnGenerar) {
            btnGenerar.disabled = false;
            btnGenerar.textContent = 'Generar certificado';
        }
    }
}

// ==================== LISTADO DE CERTIFICADOS ====================

async function cargarCertificadosAlumno(alumnoId) {
    const seccion = document.getElementById('certListadoSection');
    if (seccion) seccion.classList.add('d-none');

    try {
        const response = await fetch(`${API_URL}/certificados?alumnoId=${alumnoId}`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            console.warn('[Certificados] No se pudo cargar la lista:', response.status);
            return;
        }

        certificadosListados = await response.json();
        renderizarCertificadosAlumno(certificadosListados);

        if (seccion) seccion.classList.remove('d-none');
    } catch (error) {
        console.error('[Certificados] Error al cargar lista:', error);
    }
}

function renderizarCertificadosAlumno(lista) {
    const tbody = document.getElementById('tablaCertificadosBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No hay certificados generados para este alumno</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    lista.forEach(cert => {
        const tr = document.createElement('tr');
        tr.dataset.certId = cert.id;

        const tdFolio = document.createElement('td');
        tdFolio.innerHTML = '<strong></strong>';
        tdFolio.querySelector('strong').textContent = cert.folio || cert.id || 'N/A';

        const tdTipo = document.createElement('td');
        tdTipo.textContent = formatTipoCertificado(cert.tipoCertificado || cert.tipo);

        const tdFecha = document.createElement('td');
        tdFecha.textContent = cert.fechaExpedicion ? formatFechaCert(cert.fechaExpedicion) : 'N/A';

        const tdEstatus = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `badge ${getBadgeClaseCertificado(cert.estatus)}`;
        badge.textContent = cert.estatus || 'GENERADO';
        tdEstatus.appendChild(badge);

        const tdAcciones = document.createElement('td');
        const btnDescargar = document.createElement('button');
        btnDescargar.className = 'btn btn-sm btn-outline-primary me-1';
        btnDescargar.dataset.action = 'descargar';
        btnDescargar.innerHTML = '<i class="bi bi-download"></i> Descargar';

        const btnEliminar = document.createElement('button');
        btnEliminar.className = 'btn btn-sm btn-outline-danger';
        btnEliminar.dataset.action = 'eliminar';
        btnEliminar.textContent = 'Eliminar';

        tdAcciones.appendChild(btnDescargar);
        tdAcciones.appendChild(btnEliminar);

        tr.appendChild(tdFolio);
        tr.appendChild(tdTipo);
        tr.appendChild(tdFecha);
        tr.appendChild(tdEstatus);
        tr.appendChild(tdAcciones);
        tbody.appendChild(tr);
    });
}

// ==================== DESCARGA Y ELIMINACIÓN ====================

async function descargarCertificado(certId) {
    try {
        const response = await fetch(`${API_URL}/certificados/${certId}/descargar`, {
            method: 'GET',
            headers: getHeaders(false)
        });

        if (!response.ok) {
            const data = await response.json().catch(() => null);
            throw new Error(data?.error || data?.message || 'Error al descargar el certificado');
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `certificado_${certId}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (error) {
        console.error('[Certificados] Error al descargar:', error);
        showError(error.message || 'Error al descargar el certificado');
    }
}

async function eliminarCertificado(certId) {
    try {
        const response = await fetch(`${API_URL}/certificados/${certId}`, {
            method: 'DELETE',
            headers: getHeaders(false)
        });

        if (!response.ok) {
            let message = 'Error al eliminar el certificado';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (_) {}
            throw new Error(message);
        }

        certificadosListados = certificadosListados.filter(c => c.id !== certId);
        renderizarCertificadosAlumno(certificadosListados);
        showSuccess('Certificado eliminado exitosamente');
    } catch (error) {
        console.error('[Certificados] Error al eliminar:', error);
        showError(error.message || 'Error al eliminar el certificado');
    }
}

// ==================== UTILIDADES ====================

function formatTipoCertificado(tipo) {
    const labels = {
        PARCIAL: 'Parcial',
        FINAL: 'Final / Terminación de estudios',
        EXTRACURRICULAR: 'Extracurricular'
    };
    return labels[tipo] || tipo || 'N/A';
}

function getBadgeClaseCertificado(estatus) {
    const clases = {
        GENERADO: 'bg-primary-subtle text-primary',
        FIRMADO: 'bg-success-subtle text-success',
        CANCELADO: 'bg-danger-subtle text-danger',
        PENDIENTE: 'bg-warning-subtle text-warning'
    };
    return clases[estatus] || 'bg-secondary-subtle text-secondary';
}

function formatFechaCert(fechaStr) {
    try {
        const fecha = new Date(fechaStr);
        return fecha.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch (_) {
        return fechaStr;
    }
}

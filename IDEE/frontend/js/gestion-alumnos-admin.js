// Gestion de alumnos (admin)

let alumnosAdmin = [];
let alumnosMostrados = [];
let programasAlumnos = [];
let periodosAcademicosCachePorTipo = {}; // { SEMESTRE: [ {codigo,...} ], CUATRIMESTRE: [...] }

function setEstadoBotonExcel(btnId, generando, textoNormal, iconNormal) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    const iconEl = btn.querySelector('i');
    const textEl = btn.querySelector('.btn-excel-text');

    btn.disabled = !!generando;
    if (generando) {
        if (iconEl) iconEl.className = 'spinner-border spinner-border-sm';
        if (textEl) textEl.textContent = 'Procesando…';
        return;
    }
    if (iconEl && iconNormal) iconEl.className = iconNormal;
    if (textEl && textoNormal) textEl.textContent = textoNormal;
}

function getApiBaseAlumnos() {
    return (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
}

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

/**
 * Fecha de nacimiento sugerida en registro: hoy en el calendario local menos 18 años (mismo día y mes).
 * Formato YYYY-MM-DD para Flatpickr / backend.
 */
function fechaNacimientoDefecto18AniosYmd() {
    const d = new Date();
    d.setFullYear(d.getFullYear() - 18);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

/**
 * Normaliza texto para búsqueda insensible a tildes/acentos.
 * Ej: "MÓNICA" y "monica" ambos devuelven "monica".
 */
function normalizarParaBusqueda(str) {
    if (!str || typeof str !== 'string') return '';
    return str
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
}

function formatEstatusExcel(estatus) {
    const labels = {
        ACTIVA: 'Activa',
        BAJA_TEMPORAL: 'Baja temporal',
        BAJA_DEFINITIVA: 'Baja definitiva',
        EGRESADO: 'Egresado'
    };
    return labels[estatus] || (estatus || '—');
}

function formatTurnoExcel(turno) {
    const labels = { MATUTINO: 'Matutino', VESPERTINO: 'Vespertino', MIXTO: 'Mixto' };
    return labels[turno] || (turno || '—');
}

function formatSexoExcel(sexo) {
    const labels = { MASCULINO: 'Masculino', FEMENINO: 'Femenino' };
    return labels[sexo] || (sexo || '—');
}

function getBadgeEstatusAlumno(estatus) {
    const badges = {
        ACTIVA: '<span class="badge bg-success-subtle text-success">Activa</span>',
        BAJA_TEMPORAL: '<span class="badge bg-warning-subtle text-warning">Baja temporal</span>',
        BAJA_DEFINITIVA: '<span class="badge bg-danger-subtle text-danger">Baja definitiva</span>',
        EGRESADO: '<span class="badge bg-info-subtle text-info">Egresado</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

function renderizarTablaAlumnosAdmin(lista) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    alumnosMostrados = Array.isArray(lista) ? [...lista] : [];

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage('No hay alumnos registrados');
        } else {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No hay alumnos registrados</td></tr>';
        }
        sincronizarCheckboxSeleccionarTodos();
        actualizarBarraEliminarSeleccionados();
        return;
    }

    tbody.innerHTML = lista.map(alumno => {
        const nombreCompleto = [alumno.nombre, alumno.apellidoPaterno, alumno.apellidoMaterno]
            .filter(Boolean)
            .join(' ');
        const programaNombre = alumno.programa ? alumno.programa.nombre : 'Sin programa';
        const periodoInfo = alumno.periodoCursando != null
            ? (alumno.programa && alumno.programa.duracionPeriodos != null
                ? `${alumno.periodoCursando}/${alumno.programa.duracionPeriodos}`
                : String(alumno.periodoCursando))
            : '—';
        const tieneFoto = alumno.fotoUrl && String(alumno.fotoUrl).trim() !== '';

        const celdaFoto = tieneFoto
            ? `<td class="align-middle" style="width: 100px;"><img data-alumno-foto-id="${alumno.id}" alt="Foto alumno" class="foto-alumno foto-alumno-clickeable" style="width: 48px; height: 48px; object-fit: cover; border-radius: 50%; cursor: pointer;" title="Clic para ampliar"><span class="d-none">Cargando...</span></td>`
            : `<td class="align-middle" style="width: 100px;"><span class="d-inline-flex align-items-center justify-content-center text-muted" style="width: 48px; height: 48px;"><i class="bi bi-person-circle" style="font-size: 48px;"></i></span></td>`;

        return `
            <tr data-alumno-id="${alumno.id}" class="cursor-pointer">
                <td class="text-center align-middle" onclick="event.stopPropagation();">
                    <input type="checkbox" class="form-check-input chk-alumno-seleccion" value="${alumno.id}" aria-label="Seleccionar ${escapeHtmlAlumno(alumno.matricula || '')}" />
                </td>
                ${celdaFoto}
                <td><strong>${escapeHtmlAlumno(alumno.matricula || 'N/A')}</strong></td>
                <td>${escapeHtmlAlumno(nombreCompleto || 'N/A')}</td>
                <td>${escapeHtmlAlumno(programaNombre)}</td>
                <td><small>${escapeHtmlAlumno(periodoInfo)}</small></td>
                <td>${getBadgeEstatusAlumno(alumno.estatusMatricula)}</td>
                <td class="text-nowrap">
                    <div class="btn-group btn-group-sm" role="group">
                        <button type="button" class="btn btn-outline-secondary" data-action="edit" title="Editar"><i class="bi bi-pencil"></i></button>
                        <button type="button" class="btn btn-outline-danger" data-action="delete" title="Eliminar"><i class="bi bi-trash"></i></button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');

    cargarFotosAlumnosEnTabla();
    configurarClicFotoAlumno();
    sincronizarCheckboxSeleccionarTodos();
    actualizarBarraEliminarSeleccionados();
}

function configurarClicFotoAlumno() {
    document.querySelectorAll('.foto-alumno-clickeable').forEach(img => {
        img.onclick = function (e) {
            e.stopPropagation();
            const src = this.src || '';
            if (src && (src.startsWith('blob:') || src.startsWith('http'))) {
                const modal = document.getElementById('modalFotoAlumno');
                const modalImg = document.getElementById('modalFotoAlumnoImg');
                if (modal && modalImg) {
                    modalImg.src = src;
                    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                        new bootstrap.Modal(modal).show();
                    } else {
                        modal.classList.add('show');
                        modal.style.display = 'block';
                    }
                }
            }
        };
    });
}

async function cargarFotosAlumnosEnTabla() {
    const imgs = document.querySelectorAll('img[data-alumno-foto-id]');
    for (const img of imgs) {
        const id = img.getAttribute('data-alumno-foto-id');
        if (!id) continue;
        try {
            const res = await fetch(`${getApiBaseAlumnos()}/alumnos/${id}/foto`, { headers: getHeadersAlumnos(false) });
            if (res.ok) {
                const blob = await res.blob();
                img.src = URL.createObjectURL(blob);
                img.onerror = function () { this.style.display = 'none'; this.parentNode.innerHTML = '<span class="d-inline-flex align-items-center justify-content-center text-muted" style="width: 48px; height: 48px;"><i class="bi bi-person-circle" style="font-size: 48px;"></i></span>'; };
            } else {
                img.parentNode.innerHTML = '<span class="d-inline-flex align-items-center justify-content-center text-muted" style="width: 48px; height: 48px;"><i class="bi bi-person-circle" style="font-size: 48px;"></i></span>';
            }
        } catch (e) {
            img.parentNode.innerHTML = '<span class="d-inline-flex align-items-center justify-content-center text-muted" style="width: 48px; height: 48px;"><i class="bi bi-person-circle" style="font-size: 48px;"></i></span>';
        }
    }
}

function mostrarErrorTablaAlumnos(mensaje) {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

        tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center text-danger py-4">
                ${escapeHtmlAlumno(mensaje)}
            </td>
        </tr>
    `;
}

async function cargarProgramasAlumnos() {
    try {
        const response = await fetch(`${getApiBaseAlumnos()}/programas-educativos`, {
            method: 'GET',
            headers: getHeadersAlumnos()
        });

        if (!response.ok) {
            return;
        }

        const programas = await response.json();
        programasAlumnos = Array.isArray(programas) ? programas : [];
        llenarSelectProgramaAlumno(programas);
        llenarFiltroProgramaAlumno(programas);
    } catch (error) {
        console.error('Error al cargar programas:', error);
    }
}

/**
 * Tipo de periodo del programa seleccionado (o SEMESTRE por defecto).
 */
function tipoPeriodoProgramaPorId(programaId) {
    const pid = programaId != null ? String(programaId) : '';
    if (!pid) return 'SEMESTRE';
    const p = (programasAlumnos || []).find(x => String(x.id) === pid);
    return (p && p.tipoPeriodo) ? String(p.tipoPeriodo).toUpperCase() : 'SEMESTRE';
}

async function cargarPeriodosAcademicosPorTipo(tipoPeriodo) {
    const tipo = (tipoPeriodo != null ? String(tipoPeriodo) : 'SEMESTRE').trim().toUpperCase() || 'SEMESTRE';
    if (periodosAcademicosCachePorTipo[tipo] && Array.isArray(periodosAcademicosCachePorTipo[tipo])) {
        return periodosAcademicosCachePorTipo[tipo];
    }
    const base = getApiBaseAlumnos();
    const res = await fetch(`${base}/periodos-academicos?tipoPeriodo=${encodeURIComponent(tipo)}`, { method: 'GET', headers: getHeadersAlumnos() });
    if (!res.ok) {
        const t = await res.text().catch(() => '');
        throw new Error(t || 'No se pudieron cargar periodos académicos.');
    }
    const json = await res.json().catch(() => []);
    const lista = Array.isArray(json) ? json : [];
    periodosAcademicosCachePorTipo[tipo] = lista;
    return lista;
}

async function cargarPeriodosAcademicosPorPrograma(programaId) {
    const pid = (programaId != null ? String(programaId) : '').trim();
    if (!pid) {
        return await cargarPeriodosAcademicosPorTipo('SEMESTRE');
    }
    const cacheKey = 'PROGRAMA_' + pid;
    if (periodosAcademicosCachePorTipo[cacheKey] && Array.isArray(periodosAcademicosCachePorTipo[cacheKey])) {
        return periodosAcademicosCachePorTipo[cacheKey];
    }
    const base = getApiBaseAlumnos();
    const res = await fetch(`${base}/periodos-academicos?programaId=${encodeURIComponent(pid)}`, { method: 'GET', headers: getHeadersAlumnos() });
    if (!res.ok) {
        const t = await res.text().catch(() => '');
        throw new Error(t || 'No se pudieron cargar periodos académicos.');
    }
    const json = await res.json().catch(() => []);
    const lista = Array.isArray(json) ? json : [];
    periodosAcademicosCachePorTipo[cacheKey] = lista;
    return lista;
}

function textoOpcionPeriodoAlumno(p) {
    if (!p || p.id == null) return '';
    const cod = (p.codigo || '').trim();
    const nom = (p.nombre || '').trim();
    if (nom && cod) return `${nom} (${cod})`;
    return cod || nom || String(p.id);
}

async function cargarPeriodosIngresoAlumnosPorPrograma(programaId) {
    const lista = await cargarPeriodosAcademicosPorPrograma(programaId);
    llenarSelectPeriodoIngresoAlumno(lista);
    aplicarAutoDetectPeriodoIngreso();
}

async function cargarPeriodosIngresoFiltroPorPrograma(programaId) {
    try {
        // Si no hay programa seleccionado, dejar el filtro vacío (evita mezclar tipos)
        const lista = programaId ? await cargarPeriodosAcademicosPorPrograma(programaId) : [];
        llenarFiltroPeriodoIngresoAlumno(lista);
    } catch (e) {
        console.error('Error al cargar periodos para filtro:', e);
        llenarFiltroPeriodoIngresoAlumno([]);
    }
}

function llenarSelectPeriodoIngresoAlumno(periodos) {
    const select = document.getElementById('alumnoPeriodoIngreso');
    if (!select) return;
    const val = select.value;
    select.innerHTML = '<option value="">Selecciona...</option>';
    (periodos || []).forEach(p => {
        if (!p || p.id == null) return;
        const opt = document.createElement('option');
        opt.value = String(p.id);
        opt.textContent = textoOpcionPeriodoAlumno(p);
        select.appendChild(opt);
    });
    if (val) select.value = val;
}

function llenarFiltroPeriodoIngresoAlumno(periodos) {
    const select = document.getElementById('alumnoFiltroPeriodoIngreso');
    if (!select) return;
    const val = select.value;
    select.innerHTML = '<option value="">Periodo de ingreso</option>';
    (periodos || []).forEach(p => {
        if (!p || p.id == null) return;
        const opt = document.createElement('option');
        opt.value = String(p.id);
        opt.textContent = textoOpcionPeriodoAlumno(p);
        select.appendChild(opt);
    });
    if (val) select.value = val;
}

function aplicarAutoDetectPeriodoIngreso() {
    const estatus = document.getElementById('alumnoEstatus') ? document.getElementById('alumnoEstatus').value : '';
    const alumnoId = document.getElementById('alumnoId') ? document.getElementById('alumnoId').value : '';
    const sel = document.getElementById('alumnoPeriodoIngreso');
    if (!sel) return;
    if (estatus === 'EGRESADO') return;
    const progId = document.getElementById('alumnoProgramaId') ? document.getElementById('alumnoProgramaId').value : '';
    const tipo = tipoPeriodoProgramaPorId(progId);
    // Intentar usar el backend para obtener el código actual del tipo
    (async function () {
        try {
            const base = getApiBaseAlumnos();
            const res = await fetch(`${base}/periodos-academicos/activo?tipoPeriodo=${encodeURIComponent(tipo)}`, { method: 'GET', headers: getHeadersAlumnos() });
            const data = res.ok ? await res.json().catch(() => ({})) : {};
            const idActivo = data && data.id != null ? String(data.id) : '';
            if (!idActivo) return;
            const opts = Array.from(sel.options).map(o => o.value);
            if (opts.indexOf(idActivo) !== -1) {
                if (!alumnoId) sel.value = idActivo;
            }
        } catch (e) {}
    })();
}

function actualizarPeriodoCursandoPorEstatus() {
    const estatus = document.getElementById('alumnoEstatus') ? document.getElementById('alumnoEstatus').value : '';
    const inpPeriodo = document.getElementById('alumnoPeriodoCursando');
    if (!inpPeriodo) return;
    if (estatus === 'EGRESADO') {
        inpPeriodo.disabled = true;
        inpPeriodo.readOnly = true;
        inpPeriodo.value = '';
    } else {
        inpPeriodo.disabled = false;
        inpPeriodo.readOnly = false;
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

    select.innerHTML = '<option value="">Programa de estudios</option>';
    programas.forEach(programa => {
        const option = document.createElement('option');
        option.value = programa.id;
        option.textContent = programa.nombre;
        select.appendChild(option);
    });
}

async function cargarAlumnosAdmin() {
    const tbody = document.getElementById('alumnosTableBody');
    if (tbody) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm"></span> Cargando alumnos...</td></tr>';
    }
    try {
        if (typeof authFetch === 'function') {
            // Sin caché HTTP: tras altas/ediciones el navegador podría devolver la lista anterior.
            alumnosAdmin = await authFetch('/alumnos', { cache: 'no-store' });
        } else {
            const base = getApiBaseAlumnos();
            const url = base.replace(/\/$/, '') + '/alumnos';
            const response = await fetch(url, {
                method: 'GET',
                headers: getHeadersAlumnos(),
                cache: 'no-store'
            });
            if (!response.ok) {
                const msg = response.status === 401 ? 'Sesión expirada. Inicia sesión de nuevo.' : (response.status === 403 ? 'No tienes permiso para ver alumnos.' : 'Error al cargar alumnos (código ' + response.status + ')');
                throw new Error(msg);
            }
            alumnosAdmin = await response.json();
        }
        if (!Array.isArray(alumnosAdmin)) alumnosAdmin = [];
        renderizarTablaAlumnosAdmin(alumnosAdmin);
    } catch (error) {
        console.error('Error al cargar alumnos:', error);
        mostrarErrorTablaAlumnos(error.message || 'Error al cargar la lista de alumnos. Verifica que el backend esté en marcha.');
    }
}

function limpiarFormularioAlumnoAdmin() {
    document.getElementById('alumnoForm').reset();
    document.getElementById('alumnoId').value = '';
    const defectoNac = fechaNacimientoDefecto18AniosYmd();
    if (window.alumnoFechaNacimientoPicker) {
        window.alumnoFechaNacimientoPicker.setDate(defectoNac, false);
    } else {
        const elFn = document.getElementById('alumnoFechaNacimiento');
        if (elFn) elFn.value = defectoNac;
    }
    const archivos = document.querySelectorAll('#alumnoForm input[type="file"]');
    archivos.forEach(input => {
        input.value = '';
    });
    actualizarPreviewFotoAlumno(null);
    actualizarContadorCurp();
    actualizarContadorTelefono('alumnoTelefono', 'alumnoTelefonoCounter');
    actualizarContadorTelefono('alumnoContactoTelefono', 'alumnoContactoTelefonoCounter');
}

function editarAlumnoAdmin(id) {
    const alumno = alumnosAdmin.find(item => item.id === id);
    if (!alumno) return;

    // Ir al tab de registro/edición automáticamente
    try {
        const tabBtn = document.querySelector('[data-bs-target="#paneRegistrarAlumno"]');
        if (tabBtn && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
            bootstrap.Tab.getOrCreateInstance(tabBtn).show();
        }
    } catch (e) {}

    document.getElementById('alumnoId').value = alumno.id || '';
    document.getElementById('alumnoMatricula').value = alumno.matricula || '';
    document.getElementById('alumnoTurno').value = alumno.turno || '';
    document.getElementById('alumnoEstatus').value = alumno.estatusMatricula || 'ACTIVA';
    const progSel = document.getElementById('alumnoProgramaId');
    if (progSel) progSel.value = alumno.programa ? String(alumno.programa.id) : '';
    const perSel = document.getElementById('alumnoPeriodoIngreso');
    const periodoIdAlumno = (alumno.periodoAcademico && alumno.periodoAcademico.id != null) ? String(alumno.periodoAcademico.id) : '';
    if (perSel) perSel.value = periodoIdAlumno || '';
    // Asegurar que el select de periodos corresponda al tipo del programa del alumno
    if (progSel && progSel.value) {
        (async function () {
            try {
                if (perSel) {
                    perSel.disabled = false;
                    perSel.innerHTML = '<option value="">Cargando…</option>';
                }
                await cargarPeriodosIngresoAlumnosPorPrograma(progSel.value);
                if (perSel && periodoIdAlumno) perSel.value = periodoIdAlumno;
            } catch (e) {
                console.error(e);
            }
        })();
    }
    document.getElementById('alumnoNombre').value = alumno.nombre || '';
    document.getElementById('alumnoApellidoPaterno').value = alumno.apellidoPaterno || '';
    document.getElementById('alumnoApellidoMaterno').value = alumno.apellidoMaterno || '';
    document.getElementById('alumnoCurp').value = (alumno.curp || '').toUpperCase();
    actualizarContadorCurp();
    document.getElementById('alumnoSexo').value = alumno.sexo || '';
    var fechaVal = alumno.fechaNacimiento ? alumno.fechaNacimiento.substring(0, 10) : '';
    if (window.alumnoFechaNacimientoPicker) {
        window.alumnoFechaNacimientoPicker.setDate(fechaVal || null, false);
    } else {
        document.getElementById('alumnoFechaNacimiento').value = fechaVal;
    }
    document.getElementById('alumnoCorreoInstitucional').value = alumno.correoInstitucional || '';
    document.getElementById('alumnoCorreoPersonal').value = alumno.correoPersonal || '';
    var tel = alumno.telefono || '';
    document.getElementById('alumnoTelefono').value = filtrarSoloDigitos(tel, 10);
    actualizarContadorTelefono('alumnoTelefono', 'alumnoTelefonoCounter');
    var telEmerg = alumno.telefonoContactoEmergencia || '';
    document.getElementById('alumnoContactoTelefono').value = filtrarSoloDigitos(telEmerg, 10);
    actualizarContadorTelefono('alumnoContactoTelefono', 'alumnoContactoTelefonoCounter');
    const elEstado = document.getElementById('alumnoEstado');
    if (elEstado) elEstado.value = alumno.estado || '';
    const elCp = document.getElementById('alumnoCodigoPostal');
    if (elCp) elCp.value = alumno.codigoPostal || '';
    document.getElementById('alumnoContactoNombre').value = alumno.nombreContactoEmergencia || '';
    document.getElementById('alumnoObservaciones').value = alumno.observaciones || '';

    const archivos = document.querySelectorAll('#alumnoForm input[type="file"]');
    archivos.forEach(input => {
        input.value = '';
    });

    if (alumno.fotoUrl && alumno.id) {
        fetch(`${getApiBaseAlumnos()}/alumnos/${alumno.id}/foto`, { headers: getHeadersAlumnos(false) })
            .then(res => (res.ok ? res.blob() : null))
            .then(blob => {
                if (blob) actualizarPreviewFotoAlumno(URL.createObjectURL(blob));
                else actualizarPreviewFotoAlumno(null);
            })
            .catch(() => actualizarPreviewFotoAlumno(null));
    } else {
        actualizarPreviewFotoAlumno(null);
    }

    // Scroll al formulario ya cargado
    try {
        const form = document.getElementById('alumnoForm');
        if (form) form.scrollIntoView({ behavior: 'smooth', block: 'start' });
        else window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}

function actualizarContadorCurp() {
    const input = document.getElementById('alumnoCurp');
    const counter = document.getElementById('alumnoCurpCounter');
    if (!input || !counter) return;
    const len = (input.value || '').length;
    counter.textContent = len + '/18';
}

function actualizarContadorTelefono(inputId, counterId) {
    const input = document.getElementById(inputId);
    const counter = document.getElementById(counterId);
    if (!input || !counter) return;
    const len = (input.value || '').length;
    counter.textContent = len + '/10';
}

function filtrarSoloDigitos(valor, maxLen) {
    const digitos = (valor || '').replace(/\D/g, '');
    return maxLen ? digitos.slice(0, maxLen) : digitos;
}

function actualizarPreviewFotoAlumno(urlOArchivo) {
    const preview = document.getElementById('alumnoFotoPreview');
    if (!preview) return;
    if (!urlOArchivo) {
        preview.innerHTML = '<small class="text-muted">Vista previa de la foto</small>';
        return;
    }
    if (urlOArchivo instanceof File) {
        const reader = new FileReader();
        reader.onload = function (e) {
            preview.innerHTML = `<img src="${e.target.result}" alt="Vista previa" style="max-width: 100%; max-height: 150px; object-fit: contain; border-radius: 4px;">`;
        };
        reader.readAsDataURL(urlOArchivo);
    } else {
        preview.innerHTML = `<img src="${urlOArchivo}" alt="Vista previa" style="max-width: 100%; max-height: 150px; object-fit: contain; border-radius: 4px;" onerror="this.parentElement.innerHTML='<small class=\\'text-muted\\'>No se pudo cargar la foto</small>'">`;
    }
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

    // Reglas adicionales para campos obligatorios (teléfonos de 10 dígitos)
    const tel = (document.getElementById('alumnoTelefono')?.value || '').trim();
    const telEmerg = (document.getElementById('alumnoContactoTelefono')?.value || '').trim();
    if (!/^\d{10}$/.test(tel)) {
        alert('Indica un teléfono de 10 dígitos.');
        document.getElementById('alumnoTelefono')?.focus();
        return;
    }
    if (!/^\d{10}$/.test(telEmerg)) {
        alert('Indica un teléfono de emergencia de 10 dígitos.');
        document.getElementById('alumnoContactoTelefono')?.focus();
        return;
    }

    const correoInst = document.getElementById('alumnoCorreoInstitucional').value.trim();
    if (!correoInst) {
        alert('El correo institucional es obligatorio. Será el acceso al sistema del alumno (contraseña por defecto: idee1234).');
        return;
    }
    const alumnoIdVal = document.getElementById('alumnoId').value;
    const alumnoExistente = alumnoIdVal ? alumnosAdmin.find(a => String(a.id) === alumnoIdVal) : null;

    const progId = document.getElementById('alumnoProgramaId')?.value?.trim();
    const periodoSelVal = document.getElementById('alumnoPeriodoIngreso')?.value?.trim();
    const periodoIdNum = periodoSelVal ? parseInt(periodoSelVal, 10) : NaN;

    const alumnoData = {
        matricula: document.getElementById('alumnoMatricula').value.trim(),
        nombre: (document.getElementById('alumnoNombre').value.trim() || '').toUpperCase(),
        apellidoPaterno: (document.getElementById('alumnoApellidoPaterno').value.trim() || '').toUpperCase(),
        apellidoMaterno: (document.getElementById('alumnoApellidoMaterno').value.trim() || '').toUpperCase(),
        curp: document.getElementById('alumnoCurp').value.trim().toUpperCase(),
        sexo: document.getElementById('alumnoSexo').value || null,
        fechaNacimiento: document.getElementById('alumnoFechaNacimiento').value || null,
        correoInstitucional: correoInst,
        correoPersonal: document.getElementById('alumnoCorreoPersonal').value.trim() || null,
        telefono: document.getElementById('alumnoTelefono').value.trim() || null,
        estado: document.getElementById('alumnoEstado')?.value?.trim() || null,
        codigoPostal: document.getElementById('alumnoCodigoPostal')?.value?.trim() || null,
        turno: document.getElementById('alumnoTurno').value || null,
        estatusMatricula: document.getElementById('alumnoEstatus').value || 'ACTIVA',
        nombreContactoEmergencia: document.getElementById('alumnoContactoNombre').value.trim() || null,
        telefonoContactoEmergencia: document.getElementById('alumnoContactoTelefono').value.trim() || null,
        observaciones: document.getElementById('alumnoObservaciones').value.trim() || null
    };
    if (progId) alumnoData.programa = { id: parseInt(progId, 10) };
    if (!Number.isNaN(periodoIdNum)) alumnoData.periodoAcademicoId = periodoIdNum;
    if (alumnoExistente) {
        if (alumnoExistente.periodoCursando != null) alumnoData.periodoCursando = alumnoExistente.periodoCursando;
    } else {
        alumnoData.periodoCursando = 1;
    }

    const alumnoId = document.getElementById('alumnoId').value;
    const url = alumnoId ? `${getApiBaseAlumnos()}/alumnos/${alumnoId}` : `${getApiBaseAlumnos()}/alumnos`;
    const method = alumnoId ? 'PUT' : 'POST';

    const formData = new FormData();
    formData.append('alumno', new Blob([JSON.stringify(alumnoData)], { type: 'application/json' }));

    const foto = obtenerArchivo('alumnoFoto');
    if (foto) {
        formData.append('foto', foto);
    }

    const documentos = [
        { id: 'alumnoDocActa', tipo: 'ACTA_NACIMIENTO' },
        { id: 'alumnoDocCertificadoEstudios', tipo: 'CONSTANCIA_ESTUDIOS' },
        { id: 'alumnoDocTitulo', tipo: 'TITULO_CEDULA' },
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
        // Tras alta: quitar filtros para que el nuevo alumno aparezca al instante (antes solo se veía al recargar).
        // Tras edición: mantener filtros activos como en eliminación por lote.
        if (alumnoId) {
            buscarAlumnosAdmin();
        } else {
            limpiarFiltrosAdmin();
        }
        // Mostrar lista antes del alert para que, al cerrar el mensaje, ya esté el tab con datos frescos.
        const tabConsulta = document.querySelector('[data-bs-target="#paneConsultaAlumnos"]');
        if (tabConsulta) {
            const tab = new bootstrap.Tab(tabConsulta);
            tab.show();
        }
        alert(alumnoId ? 'Alumno actualizado exitosamente' : 'Alumno creado exitosamente');
    } catch (error) {
        console.error('Error al guardar alumno:', error);
        alert(error.message || 'Error al guardar alumno');
    }
}

function obtenerIdsAlumnosSeleccionados() {
    const cbs = document.querySelectorAll('#alumnosTableBody .chk-alumno-seleccion:checked');
    const ids = [];
    cbs.forEach(cb => {
        const v = parseInt(cb.value, 10);
        if (!isNaN(v)) ids.push(v);
    });
    return ids;
}

function sincronizarCheckboxSeleccionarTodos() {
    const master = document.getElementById('chkSeleccionarTodosAlumnos');
    if (!master) return;
    const all = document.querySelectorAll('#alumnosTableBody .chk-alumno-seleccion');
    if (all.length === 0) {
        master.checked = false;
        master.indeterminate = false;
        return;
    }
    let n = 0;
    all.forEach(cb => { if (cb.checked) n++; });
    master.checked = n === all.length;
    master.indeterminate = n > 0 && n < all.length;
}

function actualizarBarraEliminarSeleccionados() {
    const ids = obtenerIdsAlumnosSeleccionados();
    const btn = document.getElementById('btnEliminarAlumnosSeleccionados');
    const lbl = document.getElementById('lblEliminarSeleccionados');
    if (btn) btn.disabled = ids.length === 0;
    if (lbl) {
        lbl.textContent = ids.length > 0
            ? `Eliminar seleccionados (${ids.length})`
            : 'Eliminar seleccionados';
    }
}

async function eliminarAlumnosSeleccionadosAdmin() {
    const ids = obtenerIdsAlumnosSeleccionados();
    if (ids.length === 0) {
        alert('Marca al menos un alumno con la casilla de la primera columna.');
        return;
    }
    const msg = `¿Eliminar ${ids.length} alumno(s)? Esta acción no se puede deshacer.\n\nLos que tengan títulos electrónicos asociados no se eliminarán y se mostrarán en el resumen.`;
    if (!confirm(msg)) return;

    const btn = document.getElementById('btnEliminarAlumnosSeleccionados');
    if (btn) {
        btn.disabled = true;
    }
    try {
        const response = await fetch(`${getApiBaseAlumnos()}/alumnos/eliminar-lote`, {
            method: 'POST',
            headers: getHeadersAlumnos(),
            body: JSON.stringify({ ids })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.error || data.message || 'Error al eliminar el lote');
        }
        const eliminados = data.eliminados != null ? data.eliminados : 0;
        const errores = Array.isArray(data.errores) ? data.errores : [];
        let mensaje = `Eliminados correctamente: ${eliminados}.`;
        if (errores.length > 0) {
            mensaje += '\n\nNo eliminados (' + errores.length + '):\n';
            errores.slice(0, 8).forEach(e => {
                mensaje += `  · ID ${e.id}: ${e.mensaje || '—'}\n`;
            });
            if (errores.length > 8) mensaje += `  … y ${errores.length - 8} más\n`;
        }
        alert(mensaje);
        const master = document.getElementById('chkSeleccionarTodosAlumnos');
        if (master) {
            master.checked = false;
            master.indeterminate = false;
        }
        await cargarAlumnosAdmin();
        buscarAlumnosAdmin();
    } catch (error) {
        console.error('Error eliminar lote:', error);
        alert(error.message || 'Error al eliminar alumnos');
    } finally {
        actualizarBarraEliminarSeleccionados();
    }
}

async function eliminarAlumnoAdmin(id) {
    try {
        const response = await fetch(`${getApiBaseAlumnos()}/alumnos/${id}`, {
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
    const inputBusqueda = document.getElementById('alumnoFiltroBusqueda');
    const busqueda = (inputBusqueda ? inputBusqueda.value : '').trim();

    const filtroPrograma = (document.getElementById('alumnoFiltroPrograma') || {}).value || '';
    const filtroPeriodoIngreso = (document.getElementById('alumnoFiltroPeriodoIngreso') || {}).value || '';
    const filtroEstatus = (document.getElementById('alumnoFiltroEstatus') || {}).value || '';

    const filtrados = alumnosAdmin.filter(alumno => {
        if (filtroPrograma && (!alumno.programa || String(alumno.programa.id) !== filtroPrograma)) return false;
        if (filtroPeriodoIngreso) {
            const idAl = alumno.periodoAcademico && alumno.periodoAcademico.id != null ? String(alumno.periodoAcademico.id) : '';
            const leg = (alumno.periodoIngreso || alumno.cicloEscolar || '');
            if (idAl !== filtroPeriodoIngreso && leg !== filtroPeriodoIngreso) return false;
        }
        if (filtroEstatus && alumno.estatusMatricula !== filtroEstatus) return false;

        if (busqueda) {
            const termUpper = busqueda.toUpperCase();
            const termNorm = normalizarParaBusqueda(busqueda);

            const matriculaMatch = alumno.matricula && alumno.matricula.toUpperCase().includes(termUpper);
            const curpMatch = alumno.curp && alumno.curp.toUpperCase().includes(termUpper);

            const nombreCompleto = [alumno.nombre, alumno.apellidoPaterno, alumno.apellidoMaterno].filter(Boolean).join(' ');
            const nombreNorm = normalizarParaBusqueda(nombreCompleto);
            const terminos = termNorm.split(/\s+/).filter(Boolean);
            const nombreMatch = terminos.length > 0 && terminos.every(t => nombreNorm.includes(t));

            if (!nombreMatch && !matriculaMatch && !curpMatch) return false;
        }

        return true;
    });

    renderizarTablaAlumnosAdmin(filtrados);
}

function limpiarFiltrosAdmin() {
    document.getElementById('alumnoSearchForm').reset();
    const master = document.getElementById('chkSeleccionarTodosAlumnos');
    if (master) {
        master.checked = false;
        master.indeterminate = false;
    }
    renderizarTablaAlumnosAdmin(alumnosAdmin);
}

async function descargarExcelAlumnos() {
    if (typeof XLSX === 'undefined') {
        alert('No se pudo cargar la librería de Excel. Recarga la página e intenta de nuevo.');
        return;
    }

    setEstadoBotonExcel('btnDescargarExcelAlumnos', true, 'Exportar alumnos', 'bi bi-file-earmark-excel');

    try {
        let lista = alumnosMostrados && alumnosMostrados.length > 0 ? alumnosMostrados : [];
        if (lista.length === 0) {
            if (typeof authFetch === 'function') {
                lista = await authFetch('/alumnos') || [];
            } else {
                const res = await fetch(getApiBaseAlumnos() + '/alumnos', { method: 'GET', headers: getHeadersAlumnos() });
                if (res.ok) lista = await res.json() || [];
            }
        }

        const headers = [
            'Matrícula', 'Nombre', 'Apellido paterno', 'Apellido materno', 'CURP',
            'Género', 'Fecha nacimiento', 'Programa', 'Periodo cursando', 'Periodo de ingreso', 'Turno',
            'Estatus matrícula', 'Correo institucional', 'Correo personal',
            'Teléfono', 'Código postal',
            'Contacto emergencia', 'Tel. emergencia',
            'Estado', 'Observaciones'
        ];
        const filas = [headers];

        lista.forEach(a => {
            const nombreCompleto = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ');
            const programaNombre = a.programa ? a.programa.nombre : 'Sin programa';
            const fechaNac = a.fechaNacimiento ? (typeof a.fechaNacimiento === 'string' ? a.fechaNacimiento.substring(0, 10) : a.fechaNacimiento) : '—';
            filas.push([
                a.matricula || '—',
                a.nombre || '—',
                a.apellidoPaterno || '—',
                a.apellidoMaterno || '—',
                a.curp || '—',
                formatSexoExcel(a.sexo),
                fechaNac,
                programaNombre,
                a.periodoCursando != null ? String(a.periodoCursando) : '—',
                (a.periodoIngreso || a.cicloEscolar) || '—',
                formatTurnoExcel(a.turno),
                formatEstatusExcel(a.estatusMatricula),
                a.correoInstitucional || '—',
                a.correoPersonal || '—',
                a.telefono || '—',
                a.codigoPostal || '—',
                a.nombreContactoEmergencia || '—',
                a.telefonoContactoEmergencia || '—',
                a.estado || '—',
                a.observaciones || '—'
            ]);
        });

        const ws = XLSX.utils.aoa_to_sheet(filas);
        ws['!cols'] = [
            { wch: 14 }, { wch: 18 }, { wch: 18 }, { wch: 18 }, { wch: 20 },
            { wch: 10 }, { wch: 12 }, { wch: 30 }, { wch: 10 }, { wch: 12 }, { wch: 12 },
            { wch: 14 }, { wch: 28 }, { wch: 28 },
            { wch: 14 }, { wch: 10 },
            { wch: 22 }, { wch: 14 },
            { wch: 16 }, { wch: 32 }
        ];
        ws['!freeze'] = { xSplit: 0, ySplit: 1, topLeftCell: 'A2' };

        const wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, 'Alumnos');

        const nombreArchivo = 'Alumnos_inscritos_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        XLSX.writeFile(wb, nombreArchivo);
    } catch (err) {
        console.error('Error al generar Excel:', err);
        alert('No se pudo generar el archivo Excel. Verifica la conexión e intenta de nuevo.');
    } finally {
        setEstadoBotonExcel('btnDescargarExcelAlumnos', false, 'Exportar alumnos', 'bi bi-file-earmark-excel');
    }
}

async function cargarExcelAlumnos(archivo) {
    if (!archivo || (!archivo.name.toLowerCase().endsWith('.xlsx') && !archivo.name.toLowerCase().endsWith('.xls'))) {
        alert('Seleccione un archivo Excel (.xlsx o .xls). Puede usar el formato del Excel descargado.');
        return;
    }

    setEstadoBotonExcel('btnCargarExcelAlumnos', true, 'Importar Excel', 'bi bi-upload');

    try {
        var antesCount = Array.isArray(alumnosAdmin) ? alumnosAdmin.length : 0;
        var formData = new FormData();
        formData.append('archivo', archivo);

        var base = typeof getApiBaseAlumnos === 'function' ? getApiBaseAlumnos() : (typeof API_URL !== 'undefined' ? API_URL : 'http://localhost:8080/api');
        var headers = {};
        var token = typeof getToken === 'function' ? getToken() : localStorage.getItem('token');
        if (token && token !== 'null') headers['Authorization'] = 'Bearer ' + token;

        var res = await fetch(base + '/alumnos/carga-masiva', {
            method: 'POST',
            headers: headers,
            body: formData
        });

        var data = await res.json().catch(function () { return {}; });

        if (!res.ok) {
            alert(data.error || 'Error al cargar el archivo. Verifique el formato del Excel.');
            return;
        }

        if (data.error) {
            alert(data.error);
            return;
        }

        var creados = data.creados || 0;
        var actualizados = data.actualizados || 0;
        var omitidos = data.omitidos || 0;
        var errores = data.errores || [];

        var msg = 'Carga completada:\n' +
            '- Creados: ' + creados + '\n' +
            '- Actualizados: ' + actualizados + '\n' +
            '- Omitidos: ' + omitidos;
        if (errores.length > 0) {
            msg += '\n\nErrores (' + errores.length + '):\n';
            errores.slice(0, 5).forEach(function (e) {
                msg += '  Fila ' + (e.fila || '?') + ' (' + (e.matricula || '') + '): ' + (e.mensaje || '') + '\n';
            });
            if (errores.length > 5) msg += '  ... y ' + (errores.length - 5) + ' más';
        }

        // Refrescar tabla inmediatamente (sin recargar página) y forzar vista de lista
        if (creados > 0 || actualizados > 0) {
            await cargarAlumnosAdmin();
            // Algunos backends confirman la transacción milisegundos después;
            // si no cambia el conteo, reintentar una vez.
            var despuesCount = Array.isArray(alumnosAdmin) ? alumnosAdmin.length : 0;
            if (despuesCount === antesCount) {
                await new Promise(function (r) { setTimeout(r, 800); });
                await cargarAlumnosAdmin();
            }
            limpiarFiltrosAdmin();
        }

        // Ir a la pestaña de lista para que se vea el cambio
        try {
            var tabBtn = document.getElementById('tab-consulta-alumnos');
            if (tabBtn && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
                bootstrap.Tab.getOrCreateInstance(tabBtn).show();
            } else if (tabBtn) {
                tabBtn.click();
            }
        } catch (_) { }

        // Modal de confirmación con conteos
        try {
            var modalEl = document.getElementById('modalCargaExcelAlumnos');
            var msgEl = document.getElementById('modalCargaExcelAlumnosMsg');
            var detEl = document.getElementById('modalCargaExcelAlumnosDetalle');
            var totalAfectados = (Number(creados) || 0) + (Number(actualizados) || 0);
            if (msgEl) {
                msgEl.innerHTML = '<strong>Se registraron ' + totalAfectados + ' alumno(s).</strong>';
            }
            if (detEl) {
                var det = 'Creados: ' + creados + ' · Actualizados: ' + actualizados + ' · Omitidos: ' + omitidos;
                if (errores && errores.length) det += ' · Errores: ' + errores.length;
                detEl.textContent = det;
            }
            if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                bootstrap.Modal.getOrCreateInstance(modalEl).show();
            } else if (modalEl) {
                alert(msg);
            }
        } catch (_) {
            alert(msg);
        }
    } catch (err) {
        console.error('Error al cargar Excel:', err);
        alert('Error al cargar el archivo: ' + (err.message || ''));
    } finally {
        setEstadoBotonExcel('btnCargarExcelAlumnos', false, 'Importar Excel', 'bi bi-upload');
    }
}

function celdaDatosAlumno(label, value) {
    return '<div class="datos-alumno-celda"><small class="text-muted d-block">' + escapeHtmlAlumno(label) + '</small><span class="fw-semibold">' + escapeHtmlAlumno(String(value ?? '—')) + '</span></div>';
}

function fotoPlaceholderDataUriAlumno() {
    // SVG inline (no requiere assets). Fondo gris claro y silueta.
    const svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">',
        '<rect width="160" height="160" rx="18" fill="#f1f3f5"/>',
        '<circle cx="80" cy="62" r="28" fill="#ced4da"/>',
        '<path d="M32 140c8-28 32-42 48-42s40 14 48 42" fill="#ced4da"/>',
        '</svg>'
    ].join('');
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

function setFotoAlumnoEnModalDatos(alumnoId) {
    const img = document.getElementById('modalDatosAlumnoFotoImg');
    if (!img) return;
    const placeholder = fotoPlaceholderDataUriAlumno();
    // Limpiar URL anterior (blob) si existe
    try {
        const prev = img.getAttribute('data-blob-url');
        if (prev && prev.startsWith('blob:')) URL.revokeObjectURL(prev);
    } catch (_) {}
    img.removeAttribute('data-blob-url');
    if (!alumnoId) {
        img.src = placeholder;
        return;
    }
    // Cargar con token (Authorization) igual que en la tabla
    fetch(`${getApiBaseAlumnos()}/alumnos/${encodeURIComponent(String(alumnoId))}/foto`, {
        method: 'GET',
        headers: getHeadersAlumnos(false),
        cache: 'no-store'
    })
        .then(res => (res.ok ? res.blob() : null))
        .then(blob => {
            if (!blob || blob.size === 0) {
                img.src = placeholder;
                return;
            }
            const url = URL.createObjectURL(blob);
            img.setAttribute('data-blob-url', url);
            img.src = url;
        })
        .catch(() => {
            img.src = placeholder;
        });
}

function renderizarDatosAlumnoEnModal(k, contenedor) {
    const periodoActual = k.periodoActual || k.periodoActualNum || '—';
    const porcentajeAprobado = k.porcentajeAprobado != null ? k.porcentajeAprobado + '%' : '—';

    let html = '<div class="kardex-resumen-academico">';
    html += '<div class="kardex-alumno-foto-wrap">' +
        '<img id="modalDatosAlumnoFotoImg" class="kardex-alumno-foto-img" alt="Foto del alumno" src="' + fotoPlaceholderDataUriAlumno() + '" loading="lazy" />' +
        '</div>';
    html += '<div class="datos-alumno-grid">';
    html += '<div class="datos-alumno-fila datos-alumno-fila-3cols">' + celdaDatosAlumno('Matrícula', k.matricula) + celdaDatosAlumno('Nombre', k.nombre) + celdaDatosAlumno('CURP', k.curp) + '</div>';
    html += '<div class="datos-alumno-fila datos-alumno-fila-3cols">' + celdaDatosAlumno('Programa de estudios', k.programaEstudios) + celdaDatosAlumno('Plan de estudios', k.planEstudios) + celdaDatosAlumno('Modalidad', k.modalidad) + '</div>';
    html += '<div class="datos-alumno-fila datos-alumno-fila-4cols">' + celdaDatosAlumno('Periodo de ingreso', k.periodoIngreso) + celdaDatosAlumno('Periodo actual', periodoActual) + celdaDatosAlumno('Periodo de egreso', k.periodoEgreso) + celdaDatosAlumno('Situación / Estatus', k.situacionEstatus) + '</div>';
    html += '<div class="datos-alumno-fila datos-alumno-fila-3cols">' + celdaDatosAlumno('Créditos del plan', k.creditosPlan) + celdaDatosAlumno('Créditos aprobados', k.creditosAprobados) + celdaDatosAlumno('Porcentaje aprobado', porcentajeAprobado) + '</div>';
    html += '<div class="datos-alumno-fila datos-alumno-fila-4cols">' + celdaDatosAlumno('Materias totales', k.materiasTotales) + celdaDatosAlumno('Materias aprobadas', k.materiasAprobadas) + celdaDatosAlumno('Promedio general', k.promedioGeneral) + celdaDatosAlumno('Promedio del periodo', k.promedioPeriodo) + '</div>';
    html += '</div></div>';

    contenedor.innerHTML = html;
}

async function mostrarModalDatosAlumno(alumnoId, alumnoNombre) {
    const modal = document.getElementById('modalDatosAlumno');
    const titleSpan = document.getElementById('modalDatosAlumnoNombre');
    const contenido = document.getElementById('modalDatosAlumnoContenido');
    if (!modal || !titleSpan || !contenido) return;

    titleSpan.textContent = alumnoNombre || '';

    contenido.innerHTML = '<div class="text-center py-4"><span class="spinner-border spinner-border-sm"></span> Cargando datos...</div>';

    const bsModal = typeof bootstrap !== 'undefined' ? new bootstrap.Modal(modal) : null;
    if (bsModal) bsModal.show();

    try {
        const api = getApiBaseAlumnos();
        const response = await fetch(`${api}/kardex/${alumnoId}`, {
            method: 'GET',
            headers: getHeadersAlumnos()
        });
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.error || 'Error al cargar datos del alumno');
        }
        const k = await response.json();
        renderizarDatosAlumnoEnModal(k, contenido);
        setFotoAlumnoEnModalDatos(alumnoId);
    } catch (error) {
        console.error('Error al cargar datos del alumno:', error);
        contenido.innerHTML = '<div class="alert alert-danger">' + escapeHtmlAlumno(error.message || 'Error al cargar los datos') + '</div>';
    }
}

function inicializarTablaAlumnosAdmin() {
    const tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;

    tbody.addEventListener('change', (event) => {
        if (event.target.classList && event.target.classList.contains('chk-alumno-seleccion')) {
            event.stopPropagation();
            sincronizarCheckboxSeleccionarTodos();
            actualizarBarraEliminarSeleccionados();
        }
    });

    tbody.addEventListener('click', (event) => {
        if (event.target.closest('.chk-alumno-seleccion') || event.target.closest('input[type="checkbox"]')) {
            return;
        }
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
            return;
        }

        const alumno = (alumnosMostrados || []).find(a => a.id === alumnoId);
        const nombre = alumno ? [alumno.nombre, alumno.apellidoPaterno, alumno.apellidoMaterno].filter(Boolean).join(' ') : '';
        mostrarModalDatosAlumno(alumnoId, nombre);
    });
}

document.addEventListener('DOMContentLoaded', function () {
    var tbody = document.getElementById('alumnosTableBody');
    if (!tbody) return;
    if (window._initAlumnosAdminDone) return;
    window._initAlumnosAdminDone = true;

    function initAlumnos() {
        inicializarTablaAlumnosAdmin();
        cargarProgramasAlumnos();
        // Periodos de ingreso: dependerán del programa (formulario y filtro).
        // Default para filtro (sin programa seleccionado): SEMESTRE.
        cargarPeriodosIngresoFiltroPorPrograma('');

        const selProgForm = document.getElementById('alumnoProgramaId');
        const selPeriodoForm = document.getElementById('alumnoPeriodoIngreso');
        if (selPeriodoForm) {
            selPeriodoForm.innerHTML = '<option value="">Selecciona programa…</option>';
            selPeriodoForm.disabled = true;
        }
        if (selProgForm) {
            selProgForm.addEventListener('change', async function () {
                const pid = this.value || '';
                if (selPeriodoForm) {
                    selPeriodoForm.disabled = !pid;
                    selPeriodoForm.innerHTML = pid ? '<option value="">Cargando…</option>' : '<option value="">Selecciona programa…</option>';
                }
                if (pid) {
                    try {
                        await cargarPeriodosIngresoAlumnosPorPrograma(pid);
                    } catch (e) {
                        console.error(e);
                        if (selPeriodoForm) selPeriodoForm.innerHTML = '<option value="">No se pudieron cargar periodos</option>';
                    }
                }
            });
        }

        const selProgFiltro = document.getElementById('alumnoFiltroPrograma');
        if (selProgFiltro) {
            selProgFiltro.addEventListener('change', async function () {
                try {
                    await cargarPeriodosIngresoFiltroPorPrograma(this.value || '');
                } catch (e) {}
            });
        }
        cargarAlumnosAdmin();

        var btnGuardar = document.getElementById('btnGuardarAlumnoAdmin');
        if (btnGuardar) btnGuardar.addEventListener('click', guardarAlumnoAdmin);

        var btnBuscar = document.getElementById('btnBuscarAlumnos');
        if (btnBuscar) btnBuscar.addEventListener('click', buscarAlumnosAdmin);

        var searchForm = document.getElementById('alumnoSearchForm');
        if (searchForm) {
            searchForm.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    buscarAlumnosAdmin();
                }
            });
        }

        var btnLimpiar = document.getElementById('btnLimpiarFiltros');
        if (btnLimpiar) btnLimpiar.addEventListener('click', limpiarFiltrosAdmin);

        var chkTodos = document.getElementById('chkSeleccionarTodosAlumnos');
        if (chkTodos) {
            chkTodos.addEventListener('change', function () {
                const marcar = this.checked;
                document.querySelectorAll('#alumnosTableBody .chk-alumno-seleccion').forEach(cb => {
                    cb.checked = marcar;
                });
                this.indeterminate = false;
                actualizarBarraEliminarSeleccionados();
            });
        }

        var btnEliminarSel = document.getElementById('btnEliminarAlumnosSeleccionados');
        if (btnEliminarSel) btnEliminarSel.addEventListener('click', eliminarAlumnosSeleccionadosAdmin);

        var btnNuevo = document.getElementById('btnNuevoAlumno');
        if (btnNuevo) btnNuevo.addEventListener('click', function () {
            limpiarFormularioAlumnoAdmin();
            const tabRegistrar = document.querySelector('[data-bs-target="#paneRegistrarAlumno"]');
            if (tabRegistrar) {
                const tab = new bootstrap.Tab(tabRegistrar);
                tab.show();
            }
        });

        var btnExcel = document.getElementById('btnDescargarExcelAlumnos');
        if (btnExcel) btnExcel.addEventListener('click', descargarExcelAlumnos);

        var btnCargarExcel = document.getElementById('btnCargarExcelAlumnos');
        var inputCargaExcel = document.getElementById('inputCargaExcelAlumnos');
        if (btnCargarExcel && inputCargaExcel) {
            btnCargarExcel.addEventListener('click', function () { inputCargaExcel.click(); });
            inputCargaExcel.addEventListener('change', function () {
                if (this.files && this.files[0]) cargarExcelAlumnos(this.files[0]);
                this.value = '';
            });
        }

        var inputFoto = document.getElementById('alumnoFoto');
        if (inputFoto) {
            inputFoto.addEventListener('change', function () {
                const file = this.files && this.files[0];
                actualizarPreviewFotoAlumno(file || null);
            });
        }

        var inputCurp = document.getElementById('alumnoCurp');
        if (inputCurp) {
            inputCurp.addEventListener('input', function () {
                this.value = this.value.toUpperCase();
                actualizarContadorCurp();
            });
            inputCurp.addEventListener('paste', function () {
                setTimeout(function () {
                    inputCurp.value = inputCurp.value.toUpperCase();
                    actualizarContadorCurp();
                }, 0);
            });
            actualizarContadorCurp();
        }

        document.querySelectorAll('.uppercase-input').forEach(function (input) {
            input.addEventListener('input', function () {
                this.value = this.value.toUpperCase();
            });
            input.addEventListener('paste', function () {
                var el = this;
                setTimeout(function () {
                    el.value = el.value.toUpperCase();
                }, 0);
            });
        });

        function setupPhoneInput(inputId, counterId) {
            var input = document.getElementById(inputId);
            if (!input) return;
            input.addEventListener('input', function () {
                this.value = filtrarSoloDigitos(this.value, 10);
                actualizarContadorTelefono(inputId, counterId);
            });
            input.addEventListener('paste', function () {
                var el = this;
                setTimeout(function () {
                    el.value = filtrarSoloDigitos(el.value, 10);
                    actualizarContadorTelefono(inputId, counterId);
                }, 0);
            });
            actualizarContadorTelefono(inputId, counterId);
        }
        setupPhoneInput('alumnoTelefono', 'alumnoTelefonoCounter');
        setupPhoneInput('alumnoContactoTelefono', 'alumnoContactoTelefonoCounter');

    }

    // Página MPA (alumnos.html): no hay alumnosSection; cargar datos ya (sesión validada en la página)
    if (!document.getElementById('alumnosSection')) {
        initAlumnos();
        return;
    }
    // Dashboard (sección alumnos): esperar evento de sesión validada
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        initAlumnos();
    } else {
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initAlumnos();
        }, { once: true });
    }
});

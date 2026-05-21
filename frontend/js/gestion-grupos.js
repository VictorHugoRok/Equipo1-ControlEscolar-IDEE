// Gestión de grupos - secretaría académica
// Creación: solo programa, número de periodo (nivel del plan), nombre y estatus.
// Asignatura / periodo académico no forman parte del alta; los horarios se cargan después por grupo.

let programasEducativos = [];
let grupos = [];
let periodosPlanPorPrograma = {}; // programaId -> lista /periodos (para etiquetas en tabla)
let grupoIdModalActual = null;
let modalAlumnosGrupoInstance = null;

function getApiBase() {
  return (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
}

async function apiFetch(path, options) {
  var base = getApiBase();
  var url = (path.indexOf('/') === 0) ? base + path : base + '/' + path;
  var headers = options && options.headers ? { ...options.headers } : {};
  if (!headers['Content-Type'] && options && options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  var token = localStorage.getItem('token');
  if (token && token !== 'null' && token !== 'undefined') {
    headers['Authorization'] = 'Bearer ' + token;
  }
  return fetch(url, { ...options, headers });
}

async function cargarProgramas() {
  try {
    const r = await apiFetch('/programas-educativos');
    if (!r.ok) throw new Error('Error al cargar programas');
    programasEducativos = await r.json();
    const sel = document.getElementById('grupoPrograma');
    if (sel) {
      sel.innerHTML = '<option value="">Selecciona programa…</option>' +
        programasEducativos.map(p => '<option value="' + p.id + '">' + (p.nombre || p.clave) + '</option>').join('');
    }
    var filtroProg = document.getElementById('filtroPrograma');
    if (filtroProg) {
      filtroProg.innerHTML = '<option value="">Todos los programas</option>' +
        programasEducativos.map(p => '<option value="' + p.id + '">' + (p.nombre || p.clave) + '</option>').join('');
    }
  } catch (e) {
    console.error('Error cargar programas:', e);
  }
}

/** Etiqueta visible de un periodo del plan (nombre del backend o fallback). */
function etiquetaPeriodoPlanOption(p) {
  if (p.nombre && String(p.nombre).trim()) return String(p.nombre).trim();
  if (p.nombreDisplay && String(p.nombreDisplay).trim()) return String(p.nombreDisplay).trim();
  if (p.numero != null) return 'Periodo ' + p.numero;
  return '—';
}

/**
 * Carga los periodos del plan del programa y llena el select (semestres, cuatrimestres, etc.).
 */
async function cargarPeriodosPlan(programaId) {
  var sel = document.getElementById('grupoNumeroPeriodo');
  if (!sel) return;
  if (!programaId) {
    sel.innerHTML = '';
    var o0 = document.createElement('option');
    o0.value = '';
    o0.textContent = 'Selecciona programa primero…';
    sel.appendChild(o0);
    sel.disabled = true;
    sel.value = '';
    return;
  }
  sel.disabled = false;
  sel.innerHTML = '';
  var loading = document.createElement('option');
  loading.value = '';
  loading.textContent = 'Cargando…';
  sel.appendChild(loading);
  try {
    var r = await apiFetch('/periodos?programaId=' + encodeURIComponent(programaId));
    if (!r.ok) throw new Error('Error al cargar periodos del plan');
    var list = await r.json();
    periodosPlanPorPrograma[programaId] = Array.isArray(list) ? list : [];
    sel.innerHTML = '';
    if (!periodosPlanPorPrograma[programaId].length) {
      var empty = document.createElement('option');
      empty.value = '';
      empty.textContent = 'No hay periodos en el plan';
      sel.appendChild(empty);
      sel.disabled = true;
      return;
    }
    var ph = document.createElement('option');
    ph.value = '';
    ph.textContent = 'Selecciona periodo…';
    sel.appendChild(ph);
    periodosPlanPorPrograma[programaId].forEach(function (p) {
      if (p.numero == null) return;
      var opt = document.createElement('option');
      opt.value = String(p.numero);
      opt.textContent = etiquetaPeriodoPlanOption(p);
      sel.appendChild(opt);
    });
  } catch (e) {
    console.error(e);
    sel.innerHTML = '';
    var err = document.createElement('option');
    err.value = '';
    err.textContent = 'Error al cargar periodos';
    sel.appendChild(err);
    sel.disabled = true;
  }
}

function etiquetaPeriodoPlanGrupo(g) {
  if (g.asignatura && g.asignatura.periodo) {
    var ap = g.asignatura.periodo;
    return (ap.nombre || ('N.º ' + ap.numero)) + ' <span class="text-muted small">(desde asignatura)</span>';
  }
  if (g.numeroPeriodo == null) return '<span class="text-muted">—</span>';
  var prog = g.programa || ((g.asignatura || {}).programa) || {};
  var pid = prog.id;
  var list = pid ? periodosPlanPorPrograma[String(pid)] : null;
  if (list && list.length) {
    var found = list.find(function (p) { return p.numero === g.numeroPeriodo; });
    if (found) return escHtmlGrupo(found.nombre || ('N.º ' + g.numeroPeriodo));
  }
  return 'N.º ' + escHtmlGrupo(g.numeroPeriodo);
}

async function asegurarPeriodosPlanParaListado(listado) {
  var ids = {};
  (listado || []).forEach(function (g) {
    var prog = (g.asignatura || {}).programa || g.programa;
    if (prog && prog.id && g.numeroPeriodo != null) ids[String(prog.id)] = true;
  });
  await Promise.all(Object.keys(ids).map(function (pid) {
    if (periodosPlanPorPrograma[pid]) return Promise.resolve();
    return apiFetch('/periodos?programaId=' + encodeURIComponent(pid)).then(function (r) {
      if (!r.ok) return;
      return r.json().then(function (list) {
        periodosPlanPorPrograma[pid] = Array.isArray(list) ? list : [];
      });
    }).catch(function () { periodosPlanPorPrograma[pid] = []; });
  }));
}

function getBadgeEstatusGrupo(estatus) {
  var badges = {
    ACTIVO: '<span class="badge bg-success-subtle text-success">Activo</span>',
    INACTIVO: '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>',
    FINALIZADO: '<span class="badge bg-info-subtle text-info">Finalizado</span>',
    CANCELADO: '<span class="badge bg-danger-subtle text-danger">Cancelado</span>'
  };
  return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">' + (estatus || '—') + '</span>';
}

function getBadgeEstatusGrupoClickable(grupoId, estatus) {
  var clases = {
    ACTIVO: 'badge bg-success-subtle text-success',
    INACTIVO: 'badge bg-secondary-subtle text-secondary',
    FINALIZADO: 'badge bg-info-subtle text-info',
    CANCELADO: 'badge bg-danger-subtle text-danger'
  };
  var labels = { ACTIVO: 'Activo', INACTIVO: 'Inactivo', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado' };
  var cls = clases[estatus] || 'badge bg-secondary-subtle text-secondary';
  var label = labels[estatus] || (estatus || '—');
  var nuevoEstatus = (estatus === 'ACTIVO') ? 'INACTIVO' : 'ACTIVO';
  return '<button type="button" class="' + cls + ' border-0 py-1 px-2 hv-badge-estatus" style="cursor:pointer" data-grupo-id="' + grupoId + '" data-estatus="' + estatus + '" data-nuevo="' + nuevoEstatus + '" title="Clic para cambiar a ' + (nuevoEstatus === 'ACTIVO' ? 'Activo' : 'Inactivo') + '">' + label + '</button>';
}

function escHtmlGrupo(s) {
  if (s == null || s === '') return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escJsStringGrupo(s) {
  if (s == null) return '';
  return String(s)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n');
}

function getGruposTableColumnCount(tbody) {
  if (!tbody) return 6;
  var table = tbody.closest('table');
  var tr = table && table.querySelector('thead tr');
  var n = tr ? tr.querySelectorAll('th').length : 6;
  return n > 0 ? n : 6;
}

async function cargarGrupos() {
  const tbody = document.getElementById('gruposTableBody');
  if (!tbody) return;
  var nCol = getGruposTableColumnCount(tbody);
  try {
    const r = await apiFetch('/grupos');
    if (!r.ok) throw new Error('Error al cargar grupos');
    grupos = await r.json();
    renderizarGruposTabla();
  } catch (e) {
    console.error('Error cargar grupos:', e);
    tbody.innerHTML = '<tr><td colspan="' + nCol + '" class="text-center text-danger py-4">No se pudieron cargar los grupos. ' + escHtmlGrupo(e.message || '') + '</td></tr>';
  }
}

function renderizarGruposTabla() {
  const tbody = document.getElementById('gruposTableBody');
  if (!tbody) return;
  var nCol = getGruposTableColumnCount(tbody);
  var filtroProg = document.getElementById('filtroPrograma');
  var progVal = filtroProg ? filtroProg.value : '';
  var listado = (grupos || []).filter(function (g) {
    if (progVal) {
      var prog = (g.asignatura || {}).programa || g.programa;
      var progId = prog && prog.id;
      if (!progId || String(progId) !== progVal) return false;
    }
    return true;
  });
  asegurarPeriodosPlanParaListado(listado).then(function () {
    if (listado.length === 0) {
      var vacio = (grupos.length === 0)
        ? 'No hay grupos registrados. Crea uno arriba.'
        : 'No hay grupos que coincidan con los filtros.';
      tbody.innerHTML = '<tr><td colspan="' + nCol + '" class="text-center text-muted py-4">' + vacio + '</td></tr>';
      return;
    }
    tbody.innerHTML = listado.map(function (g) {
      var prog = (g.asignatura || {}).programa || g.programa || {};
      var numAlumnos = (g.alumnos || []).length;
      var btnAlumnos = '<button type="button" class="btn btn-outline-primary btn-sm" onclick="abrirModalAlumnos(' + g.id + ')"><i class="bi bi-people me-1"></i>Ver (' + numAlumnos + ')</button>';
      var estatusHtml = (g.estatus === 'ACTIVO' || g.estatus === 'INACTIVO')
        ? getBadgeEstatusGrupoClickable(g.id, g.estatus)
        : getBadgeEstatusGrupo(g.estatus);
      var acciones = '<button type="button" class="btn btn-outline-secondary btn-sm me-1" onclick="editarGrupo(' + g.id + ')" title="Editar"><i class="bi bi-pencil"></i></button>' +
        '<button type="button" class="btn btn-outline-danger btn-sm" onclick="eliminarGrupo(' + g.id + ')" title="Eliminar"><i class="bi bi-trash"></i></button>';
      return '<tr>' +
        '<td><span class="fw-medium text-break">' + escHtmlGrupo(g.nombre || '—') + '</span></td>' +
        '<td class="small">' + escHtmlGrupo(prog.nombre || prog.clave || '—') + '</td>' +
        '<td class="small">' + etiquetaPeriodoPlanGrupo(g) + '</td>' +
        '<td>' + estatusHtml + '</td>' +
        '<td class="td-grupos-nowrap">' + btnAlumnos + '</td>' +
        '<td class="td-grupos-nowrap">' + acciones + '</td>' +
        '</tr>';
    }).join('');
    tbody.querySelectorAll('.hv-badge-estatus').forEach(function (btn) {
      btn.addEventListener('click', function () {
        cambiarEstatusGrupo(parseInt(this.dataset.grupoId, 10), this.dataset.nuevo);
      });
    });
  });
}

async function cambiarEstatusGrupo(grupoId, nuevoEstatus) {
  var g = grupos.find(function (x) { return x.id === grupoId; });
  if (!g) return;
  try {
    var progId = (g.programa && g.programa.id) || (g.asignatura && g.asignatura.programa && g.asignatura.programa.id);
    var asigId = g.asignatura ? g.asignatura.id : null;
    var payload = {
      nombre: g.nombre,
      estatus: nuevoEstatus,
      programaId: asigId ? null : progId,
      asignaturaId: asigId || null,
      alumnoIds: (g.alumnos || []).map(function (a) { return a.id; })
    };
    if (g.periodoAcademico && g.periodoAcademico.id) payload.periodoAcademicoId = g.periodoAcademico.id;
    if (!asigId && g.numeroPeriodo != null) payload.numeroPeriodo = g.numeroPeriodo;
    var r = await apiFetch('/grupos/' + grupoId, { method: 'PUT', body: JSON.stringify(payload) });
    if (!r.ok) throw new Error('Error al actualizar');
    g.estatus = nuevoEstatus;
    renderizarGruposTabla();
  } catch (e) {
    console.error('Error cambiar estatus:', e);
    alert('No se pudo cambiar el estatus.');
  }
}

function ensureModalAlumnosGrupo() {
  var el = document.getElementById('modalAlumnosGrupo');
  if (!el || typeof bootstrap === 'undefined' || !bootstrap.Modal) return null;
  if (!modalAlumnosGrupoInstance) {
    modalAlumnosGrupoInstance = bootstrap.Modal.getOrCreateInstance(el);
    // Failsafe: si por algún bug queda un backdrop pegado, limpiarlo al cerrar.
    el.addEventListener('hidden.bs.modal', function () {
      try {
        document.querySelectorAll('.modal-backdrop').forEach(function (b) { b.remove(); });
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
      } catch (_) {}
    });
  }
  return modalAlumnosGrupoInstance;
}

function renderModalAlumnos(grupoId) {
  grupoIdModalActual = grupoId;
  var g = grupos.find(function (x) { return x.id === grupoId; });
  if (!g) return;
  var sub = (g.asignatura || {}).nombre || (g.programa || {}).nombre || (g.programa || {}).clave || '';
  document.getElementById('modalAlumnosGrupoNombre').textContent = (g.nombre || '') + (sub ? ' - ' + sub : '');
  var tbody = document.getElementById('modalAlumnosGrupoBody');
  var alumnosList = g.alumnos || [];
  if (alumnosList.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">No hay alumnos inscritos en este grupo.</td></tr>';
  } else {
    tbody.innerHTML = alumnosList.map(function (a) {
      var nombreCompleto = ((a.nombre || '') + ' ' + (a.apellidoPaterno || '') + ' ' + (a.apellidoMaterno || '')).trim() || '—';
      var nombreJs = escJsStringGrupo(nombreCompleto);
      return '<tr>' +
        '<td>' + (a.matricula || '—') + '</td>' +
        '<td>' + nombreCompleto + '</td>' +
        '<td>' + (a.correoInstitucional || a.correoPersonal || '—') + '</td>' +
        '<td><button type="button" class="btn btn-outline-danger btn-sm" onclick="quitarAlumnoDelGrupo(' + g.id + ',' + a.id + ',\'' + nombreJs + '\')"><i class="bi bi-person-dash me-1"></i>Quitar del grupo</button></td>' +
      '</tr>';
    }).join('');
  }
}

function abrirModalAlumnos(grupoId) {
  renderModalAlumnos(grupoId);
  var modal = ensureModalAlumnosGrupo();
  if (modal) modal.show();
}

function descargarExcelAlumnosGrupo() {
  if (typeof XLSX === 'undefined') {
    alert('No se pudo cargar la librería de Excel. Recarga la página e intenta de nuevo.');
    return;
  }
  if (!grupoIdModalActual) return;
  var g = grupos.find(function (x) { return x.id === grupoIdModalActual; });
  if (!g) return;
  var alumnosList = g.alumnos || [];
  if (alumnosList.length === 0) {
    alert('No hay alumnos inscritos en este grupo para exportar.');
    return;
  }
  var grupoNombre = ((g.nombre || '') + '_' + ((g.asignatura || {}).nombre || '')).replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 30);
  var filas = [
    ['Matrícula', 'Nombre', 'Apellido Paterno', 'Apellido Materno', 'Correo Institucional', 'Correo Personal', 'Teléfono']
  ];
  alumnosList.forEach(function (a) {
    filas.push([
      a.matricula || '',
      a.nombre || '',
      a.apellidoPaterno || '',
      a.apellidoMaterno || '',
      a.correoInstitucional || '',
      a.correoPersonal || '',
      a.telefono || ''
    ]);
  });
  var ws = XLSX.utils.aoa_to_sheet(filas);
  var wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Alumnos');
  var nombreArchivo = 'Alumnos_Grupo_' + grupoNombre + '_' + new Date().toISOString().slice(0, 10) + '.xlsx';
  XLSX.writeFile(wb, nombreArchivo);
}

async function quitarAlumnoDelGrupo(grupoId, alumnoId, alumnoNombre) {
  var nombre = (alumnoNombre || '').trim();
  var msg = nombre
    ? ('¿Quitar a "' + nombre + '" del grupo?\n\nEsto lo desinscribe del grupo, pero no elimina al alumno del sistema.')
    : '¿Quitar a este alumno del grupo?\n\nEsto lo desinscribe del grupo, pero no elimina al alumno del sistema.';

  if (typeof window.uiConfirm === 'function') {
    var ok = await window.uiConfirm(msg, {
      title: 'Quitar alumno del grupo',
      subtitle: 'Confirmación requerida',
      okText: 'Quitar del grupo',
      cancelText: 'Cancelar'
    });
    if (!ok) return;
  } else {
    // Fallback (idealmente no): confirm nativo si uiConfirm no está disponible en esta página
    if (!confirm(msg)) return;
  }
  try {
    const r = await apiFetch('/grupos/' + grupoId + '/alumnos/' + alumnoId, { method: 'DELETE' });
    if (!r.ok) {
      var t = await r.text().catch(function () { return ''; });
      throw new Error(t || 'Error al quitar alumno');
    }
    var g = grupos.find(function (x) { return x.id === grupoId; });
    if (g && g.alumnos) g.alumnos = g.alumnos.filter(function (a) { return a.id !== alumnoId; });
    // Solo re-renderiza el contenido; NO recrear/rehacer show() (evita backdrops acumulados).
    renderModalAlumnos(grupoId);
    cargarGrupos();
  } catch (e) {
    alert('Error: ' + (e.message || 'No se pudo quitar al alumno.'));
  }
}

async function editarGrupo(id) {
  var g = grupos.find(function (x) { return x.id === id; });
  if (!g) return;
  document.getElementById('grupoIdEditar').value = id;
  document.getElementById('grupoNombre').value = g.nombre || '';
  document.getElementById('grupoEstatus').value = g.estatus || 'ACTIVO';

  var prog = g.programa || (g.asignatura && g.asignatura.programa);
  var progId = prog && prog.id;
  if (progId) {
    document.getElementById('grupoPrograma').value = String(progId);
    await cargarPeriodosPlan(progId);
  } else {
    document.getElementById('grupoPrograma').value = '';
    await cargarPeriodosPlan('');
  }

  var num = g.numeroPeriodo;
  if (g.asignatura && g.asignatura.periodo && g.asignatura.periodo.numero != null) {
    num = g.asignatura.periodo.numero;
  }
  var numEl = document.getElementById('grupoNumeroPeriodo');
  if (numEl && num != null && num !== '' && !numEl.disabled) {
    numEl.value = String(num);
  }

  document.getElementById('btnGuardarGrupo').textContent = 'Actualizar grupo';
  document.getElementById('btnCancelarEdicion').classList.remove('d-none');
  document.getElementById('formGrupo').scrollIntoView({ behavior: 'smooth' });
}

async function guardarGrupo(ev) {
  ev.preventDefault();
  var idEditar = document.getElementById('grupoIdEditar').value.trim();
  var nombre = document.getElementById('grupoNombre').value.trim();
  var programaId = document.getElementById('grupoPrograma').value;
  var estatus = document.getElementById('grupoEstatus').value || 'ACTIVO';
  var numRaw = document.getElementById('grupoNumeroPeriodo') && document.getElementById('grupoNumeroPeriodo').value;
  var numeroPeriodo = parseInt(String(numRaw || '').trim(), 10);

  if (!nombre) {
    alert('El nombre del grupo es obligatorio.');
    return;
  }
  if (!programaId) {
    alert('Selecciona un programa.');
    return;
  }
  if (!numRaw || !numeroPeriodo || numeroPeriodo < 1 || isNaN(numeroPeriodo)) {
    alert('Selecciona el periodo del plan (según el programa).');
    return;
  }

  var payload = {
    nombre: nombre,
    estatus: estatus,
    programaId: parseInt(programaId, 10),
    numeroPeriodo: numeroPeriodo
  };

  try {
    var url = '/grupos';
    var method = 'POST';
    if (idEditar) {
      url = '/grupos/' + idEditar;
      method = 'PUT';
    }
    const r = await apiFetch(url, {
      method: method,
      body: JSON.stringify(payload)
    });
    if (!r.ok) {
      var errText = await r.text().catch(function () { return ''; });
      var errMsg = 'Error al guardar';
      try {
        var err = errText ? JSON.parse(errText) : {};
        errMsg = (err && (err.message || err.error || err.mensaje)) || (typeof err === 'string' ? err : errMsg);
      } catch (_) {
        if (errText && errText.trim()) errMsg = errText.trim();
      }
      throw new Error(errMsg);
    }
    alert(idEditar ? 'Grupo actualizado correctamente.' : 'Grupo guardado correctamente.');
    cancelarEdicion();
    cargarGrupos();
  } catch (e) {
    var msg = e.message || 'No se pudo guardar el grupo.';
    if (msg.toLowerCase().indexOf('failed to fetch') !== -1) {
      msg = 'No se pudo conectar con el servidor. Verifica que el backend esté ejecutándose y que hayas iniciado sesión.';
    }
    alert('Error: ' + msg);
  }
}

function cancelarEdicion() {
  document.getElementById('formGrupo').reset();
  document.getElementById('grupoIdEditar').value = '';
  document.getElementById('grupoEstatus').value = 'ACTIVO';
  cargarPeriodosPlan('');
  document.getElementById('btnGuardarGrupo').textContent = 'Guardar grupo';
  document.getElementById('btnCancelarEdicion').classList.add('d-none');
}

async function eliminarGrupo(id) {
  if (!confirm('¿Eliminar este grupo?')) return;
  try {
    const r = await apiFetch('/grupos/' + id, { method: 'DELETE' });
    let data = null;
    try { data = await r.json(); } catch (_) {}
    if (!r.ok) {
      var msgErr = (data && (data.message || data.error || data.mensaje)) || 'Error al eliminar';
      throw new Error(msgErr);
    }
    if (data && data.message) {
      alert(data.message);
    }
    cargarGrupos();
  } catch (e) {
    alert('Error: ' + (e.message || 'No se pudo eliminar.'));
  }
}

document.addEventListener('DOMContentLoaded', function () {
  cargarProgramas();

  var progSel = document.getElementById('grupoPrograma');
  if (progSel) {
    progSel.addEventListener('change', function () {
      cargarPeriodosPlan(this.value);
    });
  }

  var form = document.getElementById('formGrupo');
  if (form) {
    form.addEventListener('submit', guardarGrupo);
  }

  var btnCancelar = document.getElementById('btnCancelarEdicion');
  if (btnCancelar) {
    btnCancelar.addEventListener('click', cancelarEdicion);
  }

  var btnExcel = document.getElementById('btnDescargarExcelAlumnosGrupo');
  if (btnExcel) {
    btnExcel.addEventListener('click', descargarExcelAlumnosGrupo);
  }

  var filtroProg = document.getElementById('filtroPrograma');
  if (filtroProg) filtroProg.addEventListener('change', renderizarGruposTabla);
});

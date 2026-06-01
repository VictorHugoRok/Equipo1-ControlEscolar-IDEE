/**
 * titulos-electronicos.js
 * Gestión de generación y consulta de títulos profesionales electrónicos.
 * Muestra lista completa de alumnos para selección.
 */

(function () {
  'use strict';

  var API = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
  var alumnos = [];
  var alumnoSeleccionado = null;
  var titulosCache = [];

  // ─── helpers ────────────────────────────────────────────────────────────────

  function headersJson() {
    var h = { 'Content-Type': 'application/json' };
    var token = localStorage.getItem('token');
    if (token && token !== 'null') h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  function headersNoJson() {
    var h = {};
    var token = localStorage.getItem('token');
    if (token && token !== 'null') h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  async function apiFetch(path, options) {
    var url = path.indexOf('/') === 0 ? (API + path) : (API + '/' + path);
    var opts = Object.assign({}, options || {});
    opts.headers = Object.assign({}, headersJson(), (opts.headers || {}));
    return fetch(url, opts);
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function nombreCompleto(a) {
    if (!a) return '';
    return [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ').trim();
  }

  function mostrarAlerta(elementId, tipo, mensaje) {
    var el = document.getElementById(elementId);
    if (!el) return;
    el.className = 'alert alert-' + tipo;
    el.innerHTML = mensaje;
    el.classList.remove('d-none');
    if (tipo === 'success') {
      setTimeout(function () { el.classList.add('d-none'); }, 8000);
    }
  }

  function ocultarAlerta(elementId) {
    var el = document.getElementById(elementId);
    if (el) el.classList.add('d-none');
  }

  // ─── lista de alumnos ────────────────────────────────────────────────────────

  async function cargarAlumnos() {
    var lista = document.getElementById('titListaAlumnos');
    if (lista) lista.innerHTML = '<div class="list-group-item border-0 text-muted small py-4 text-center"><span class="spinner-border spinner-border-sm me-1"></span>Cargando alumnos…</div>';
    try {
      var res = await apiFetch('/alumnos', { method: 'GET' });
      if (!res.ok) throw new Error('Error ' + res.status + ' al cargar alumnos');
      alumnos = await res.json();
      if (!Array.isArray(alumnos)) alumnos = [];
      poblarFiltroPrograma();
      renderListaAlumnos();
    } catch (e) {
      console.error(e);
      if (lista) lista.innerHTML = '<div class="list-group-item border-0 text-danger small py-4 text-center">No se pudieron cargar los alumnos.</div>';
    }
  }

  function poblarFiltroPrograma() {
    var sel = document.getElementById('titFiltroPrograma');
    if (!sel) return;
    var map = {};
    (alumnos || []).forEach(function (a) {
      if (a && a.programa && a.programa.id) map[a.programa.id] = a.programa.nombre || ('Programa ' + a.programa.id);
    });
    var cur = sel.value;
    sel.innerHTML = '<option value="">Todos los programas</option>';
    Object.keys(map).sort(function (x, y) { return String(map[x]).localeCompare(String(map[y]), 'es'); }).forEach(function (id) {
      var opt = document.createElement('option');
      opt.value = id;
      opt.textContent = map[id];
      sel.appendChild(opt);
    });
    if (cur) sel.value = cur;
  }

  function renderListaAlumnos() {
    var lista = document.getElementById('titListaAlumnos');
    if (!lista) return;

    var q = String((document.getElementById('titFiltroAlumno') || {}).value || '').trim().toLowerCase();
    var progId = String((document.getElementById('titFiltroPrograma') || {}).value || '');
    var est = String((document.getElementById('titFiltroEstatus') || {}).value || '');

    var filtrados = (alumnos || []).filter(function (a) {
      if (!a) return false;
      if (progId && (!a.programa || String(a.programa.id) !== progId)) return false;
      if (est && String(a.estatusMatricula || '') !== est) return false;
      if (q) {
        var texto = [a.matricula, a.curp, nombreCompleto(a), a.programa && a.programa.nombre ? a.programa.nombre : ''].join(' ').toLowerCase();
        return texto.indexOf(q) !== -1;
      }
      return true;
    });

    var countEl = document.getElementById('titAlumnosCount');
    if (countEl) countEl.textContent = String(filtrados.length);

    if (filtrados.length === 0) {
      lista.innerHTML = '<div class="list-group-item border-0 text-muted small py-4 text-center">Sin resultados.</div>';
      return;
    }

    lista.innerHTML = filtrados.map(function (a) {
      var id = a.id;
      var nom = nombreCompleto(a) || '—';
      var mat = a.matricula || '—';
      var prog = (a.programa && a.programa.nombre) ? a.programa.nombre : 'Sin programa';
      var checked = alumnoSeleccionado && String(alumnoSeleccionado.id) === String(id);
      return (
        '<label class="list-group-item kardex-alumno-item d-flex gap-2 align-items-start">' +
          '<input class="form-check-input mt-1 kardex-alumno-item-radio" type="radio" name="titAlumnoRadio" value="' + escapeHtml(String(id)) + '" ' + (checked ? 'checked' : '') + ' />' +
          '<div class="kardex-alumno-item-body">' +
            '<div class="kardex-alumno-nombre">' + escapeHtml(nom) + '</div>' +
            '<div class="kardex-alumno-matricula-estado">' +
              '<span class="kardex-alumno-matricula-chip">' + escapeHtml(mat) + '</span>' +
            '</div>' +
            '<div class="text-muted small mt-1">' + escapeHtml(prog) + '</div>' +
          '</div>' +
        '</label>'
      );
    }).join('');

    lista.querySelectorAll('input[name="titAlumnoRadio"]').forEach(function (r) {
      r.addEventListener('change', function () {
        var id = this.value;
        alumnoSeleccionado = (alumnos || []).find(function (x) { return String(x.id) === String(id); }) || null;
        onAlumnoSeleccionado();
      });
    });
  }

  function onAlumnoSeleccionado() {
    var a = alumnoSeleccionado;
    var hint = document.getElementById('titAlumnoSeleccionadoHint');
    if (hint) hint.textContent = a ? ('Alumno seleccionado: ' + nombreCompleto(a)) : 'Selecciona un alumno para continuar.';

    if (!a) {
      mostrarDatosAlumnoPanel(null);
      deshabilitarFormulario(true);
      return;
    }

    mostrarDatosAlumnoPanel(a);

    // Rellenar hidden inputs del formulario
    var hidId = document.getElementById('alumnoIdTitulo');
    var hidProg = document.getElementById('programaIdTitulo');
    if (hidId) hidId.value = a.id || '';
    if (hidProg) hidProg.value = (a.programa && a.programa.id) ? a.programa.id : '';

    validarRequisitosAlumno(a.id);
  }

  function mostrarDatosAlumnoPanel(a) {
    var panel = document.getElementById('titAlumnoInfoPanel');
    if (!panel) return;
    if (!a) {
      panel.innerHTML = '<p class="text-muted small mb-0">Selecciona un alumno de la lista para ver sus datos.</p>';
      return;
    }
    var prog = (a.programa && a.programa.nombre) ? a.programa.nombre : 'Sin programa';
    var estBadge = {
      ACTIVA: 'bg-success-subtle text-success',
      BAJA_TEMPORAL: 'bg-warning-subtle text-warning',
      BAJA_DEFINITIVA: 'bg-danger-subtle text-danger',
      EGRESADO: 'bg-info-subtle text-info'
    }[a.estatusMatricula || ''] || 'bg-secondary-subtle text-secondary';
    var estLabel = { ACTIVA: 'Activa', BAJA_TEMPORAL: 'Baja temporal', BAJA_DEFINITIVA: 'Baja definitiva', EGRESADO: 'Egresado' }[a.estatusMatricula || ''] || (a.estatusMatricula || '—');
    panel.innerHTML = (
      '<div class="row g-2 small">' +
        '<div class="col-6"><span class="text-muted d-block">Nombre</span><strong>' + escapeHtml(nombreCompleto(a)) + '</strong></div>' +
        '<div class="col-6"><span class="text-muted d-block">Matrícula</span><strong>' + escapeHtml(a.matricula || '—') + '</strong></div>' +
        '<div class="col-6"><span class="text-muted d-block">CURP</span><span>' + escapeHtml(a.curp || '—') + '</span></div>' +
        '<div class="col-6"><span class="text-muted d-block">Estatus</span><span class="badge ' + estBadge + '">' + escapeHtml(estLabel) + '</span></div>' +
        '<div class="col-12"><span class="text-muted d-block">Programa</span><span>' + escapeHtml(prog) + '</span></div>' +
      '</div>'
    );
  }

  // ─── validación de requisitos ────────────────────────────────────────────────

  async function validarRequisitosAlumno(alumnoId) {
    var contenedor = document.getElementById('requisitosValidacion');
    var btnGenerar = document.getElementById('btnGenerarTitulo');
    if (!contenedor) return;
    contenedor.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Verificando requisitos…';
    deshabilitarFormulario(true);
    try {
      var res = await apiFetch('/titulos-electronicos/validar-requisitos/' + encodeURIComponent(String(alumnoId)), { method: 'GET' });
      if (!res.ok) throw new Error('No se pudo validar');
      var validacion = await res.json();
      if (validacion.cumpleRequisitos) {
        contenedor.innerHTML = (
          '<div class="alert alert-success alert-sm py-2 mb-0">' +
            '<i class="bi bi-check-circle-fill me-1"></i><strong>Requisitos cumplidos</strong>' +
            (validacion.mensaje ? '<br><small>' + escapeHtml(validacion.mensaje) + '</small>' : '') +
          '</div>'
        );
        deshabilitarFormulario(false);
      } else {
        contenedor.innerHTML = (
          '<div class="alert alert-danger alert-sm py-2 mb-0">' +
            '<i class="bi bi-exclamation-triangle-fill me-1"></i><strong>No cumple requisitos</strong>' +
            (validacion.mensaje ? '<br><small>' + escapeHtml(validacion.mensaje) + '</small>' : '') +
          '</div>'
        );
        deshabilitarFormulario(true);
      }
    } catch (e) {
      contenedor.innerHTML = '<div class="alert alert-warning alert-sm py-2 mb-0"><small>No se pudieron verificar los requisitos.</small></div>';
      deshabilitarFormulario(false); // dejar generar de todos modos
    }
  }

  function deshabilitarFormulario(deshabilitar) {
    var btn = document.getElementById('btnGenerarTitulo');
    if (btn) btn.disabled = !!deshabilitar;
  }

  // ─── generación ──────────────────────────────────────────────────────────────

  async function generarTitulo(e) {
    if (e) e.preventDefault();
    if (!alumnoSeleccionado) {
      mostrarAlerta('alertaGeneracion', 'warning', 'Primero selecciona un alumno de la lista.');
      return;
    }
    var progId = parseInt((document.getElementById('programaIdTitulo') || {}).value || '0', 10);
    if (!progId || isNaN(progId)) {
      mostrarAlerta('alertaGeneracion', 'danger', 'El alumno seleccionado no tiene un programa educativo asignado.');
      return;
    }
    var datos = {
      alumnoId: parseInt((document.getElementById('alumnoIdTitulo') || {}).value || '0', 10),
      programaId: progId,
      fechaExpedicion: (document.getElementById('fechaExpedicion') || {}).value || '',
      idModalidadTitulacion: (document.getElementById('idModalidadTitulacion') || {}).value || '',
      modalidadTitulacion: (document.getElementById('modalidadTitulacion') || {}).value || '',
      fechaExamenProfesional: (document.getElementById('fechaExamenProfesional') || {}).value || '',
      cumplioServicioSocial: !!(document.getElementById('cumplioServicioSocial') || {}).checked,
      idFundamentoLegalServicioSocial: (document.getElementById('idFundamentoLegalServicioSocial') || {}).value || '',
      fundamentoLegalServicioSocial: (document.getElementById('fundamentoLegalServicioSocial') || {}).value || '',
      institucionProcedencia: (document.getElementById('institucionProcedencia') || {}).value || '',
      idTipoEstudioAntecedente: (document.getElementById('idTipoEstudioAntecedente') || {}).value || '',
      tipoEstudioAntecedente: (document.getElementById('tipoEstudioAntecedente') || {}).value || '',
      idEntidadFederativaAntecedente: (document.getElementById('idEntidadFederativaAntecedente') || {}).value || '',
      entidadFederativaAntecedente: (document.getElementById('entidadFederativaAntecedente') || {}).value || '',
      fechaTerminacionAntecedente: (document.getElementById('fechaTerminacionAntecedente') || {}).value || ''
    };
    var btn = document.getElementById('btnGenerarTitulo');
    if (btn) { btn.disabled = true; btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Generando…'; }
    try {
      var res = await apiFetch('/titulos-electronicos', { method: 'POST', body: JSON.stringify(datos) });
      if (res.ok) {
        var resultado = await res.json();
        mostrarAlerta('alertaGeneracion', 'success',
          '<strong>¡Título generado exitosamente!</strong><br>' +
          '<strong>Folio:</strong> ' + escapeHtml(resultado.folioControl || '') + '<br>' +
          '<strong>Estatus:</strong> ' + escapeHtml(resultado.estatus || '') + '<br>' +
          '<a href="' + API + '/titulos-electronicos/' + encodeURIComponent(String(resultado.id)) + '/descargar-xml" ' +
          'class="btn btn-sm btn-primary mt-2" target="_blank"><i class="bi bi-download me-1"></i>Descargar XML</a>'
        );
        limpiarFormulario();
        setTimeout(function () {
          var tabBtn = document.getElementById('consultar-tab');
          if (tabBtn && typeof bootstrap !== 'undefined') bootstrap.Tab.getOrCreateInstance(tabBtn).show();
          cargarTodosLosTitulos();
        }, 3000);
      } else {
        var err = await res.json().catch(function () { return {}; });
        throw new Error(err.mensaje || err.message || err.error || 'Error al generar título');
      }
    } catch (error) {
      console.error(error);
      mostrarAlerta('alertaGeneracion', 'danger', 'Error al generar título: ' + escapeHtml(error.message || ''));
    } finally {
      if (btn) { btn.disabled = !alumnoSeleccionado; btn.innerHTML = '<i class="bi bi-file-earmark-check me-1"></i>Generar Título'; }
    }
  }

  function limpiarFormulario() {
    var form = document.getElementById('formGenerarTitulo');
    if (form) form.reset();
    var hidId = document.getElementById('alumnoIdTitulo');
    var hidProg = document.getElementById('programaIdTitulo');
    if (hidId) hidId.value = '';
    if (hidProg) hidProg.value = '';
    var req = document.getElementById('requisitosValidacion');
    if (req) req.innerHTML = '';
    ocultarAlerta('alertaGeneracion');
    configurarFechaActual();
  }

  function configurarFechaActual() {
    var el = document.getElementById('fechaExpedicion');
    if (el && !el.value) el.value = new Date().toISOString().split('T')[0];
  }

  // ─── consulta de títulos ──────────────────────────────────────────────────────

  async function cargarTodosLosTitulos() {
    var tbody = document.getElementById('tablaTitulos');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm me-1"></span>Cargando…</td></tr>';
    try {
      var res = await apiFetch('/titulos-electronicos', { method: 'GET' });
      if (!res.ok) throw new Error('Error ' + res.status);
      titulosCache = await res.json();
      if (!Array.isArray(titulosCache)) titulosCache = [];
      filtrarYRenderizarTitulos();
    } catch (e) {
      console.error(e);
      if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-3">No se pudieron cargar los títulos.</td></tr>';
    }
  }

  function filtrarYRenderizarTitulos() {
    var criterio = String((document.getElementById('buscarTitulo') || {}).value || '').trim().toLowerCase();
    var estatus = String((document.getElementById('filtroEstatus') || {}).value || '');
    var lista = (titulosCache || []).filter(function (t) {
      if (estatus && t.estatus !== estatus) return false;
      if (criterio) {
        var texto = [t.folioControl, t.alumnoNombre, t.alumnoMatricula, t.programaNombre].join(' ').toLowerCase();
        return texto.indexOf(criterio) !== -1;
      }
      return true;
    });
    mostrarTitulosEnTabla(lista);
  }

  function mostrarTitulosEnTabla(titulos) {
    var tbody = document.getElementById('tablaTitulos');
    if (!tbody) return;
    if (!titulos || titulos.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No se encontraron títulos.</td></tr>';
      return;
    }
    tbody.innerHTML = titulos.map(function (t) {
      var badge = {
        GENERADO: '<span class="badge bg-info-subtle text-info">Generado</span>',
        FIRMADO: '<span class="badge bg-primary-subtle text-primary">Firmado</span>',
        ENVIADO_SEP: '<span class="badge bg-warning-subtle text-warning">Enviado SEP</span>',
        VALIDADO_SEP: '<span class="badge bg-success-subtle text-success">Validado SEP</span>',
        RECHAZADO_SEP: '<span class="badge bg-danger-subtle text-danger">Rechazado SEP</span>',
        ENTREGADO: '<span class="badge bg-dark">Entregado</span>'
      }[t.estatus] || ('<span class="badge bg-secondary">' + escapeHtml(t.estatus || '') + '</span>');
      var fecha = t.fechaExpedicion ? (function () {
        try { return new Date(t.fechaExpedicion + 'T00:00:00').toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: 'numeric' }); } catch (_) { return t.fechaExpedicion; }
      })() : '—';
      return (
        '<tr>' +
          '<td><strong>' + escapeHtml(t.folioControl || '—') + '</strong></td>' +
          '<td>' + escapeHtml(t.alumnoNombre || '—') + '</td>' +
          '<td>' + escapeHtml(t.alumnoMatricula || '—') + '</td>' +
          '<td><small>' + escapeHtml(t.programaNombre || '—') + '</small></td>' +
          '<td>' + escapeHtml(fecha) + '</td>' +
          '<td>' + badge + '</td>' +
          '<td class="text-nowrap">' +
            '<div class="btn-group btn-group-sm">' +
              '<a href="' + API + '/titulos-electronicos/' + encodeURIComponent(String(t.id)) + '/descargar-xml" ' +
              'class="btn btn-outline-primary" title="Descargar XML" target="_blank"><i class="bi bi-download"></i></a>' +
              '<button type="button" class="btn btn-outline-warning" onclick="__titElecCambioEstatus(' + t.id + ',\'' + escapeHtml(t.estatus || '') + '\')" title="Cambiar estatus"><i class="bi bi-arrow-repeat"></i></button>' +
            '</div>' +
          '</td>' +
        '</tr>'
      );
    }).join('');
  }

  // ─── cambio de estatus ────────────────────────────────────────────────────────

  window.__titElecCambioEstatus = function (tituloId, estatusActual) {
    var hidId = document.getElementById('tituloIdCambioEstatus');
    var selEst = document.getElementById('nuevoEstatus');
    if (hidId) hidId.value = tituloId;
    if (selEst) selEst.value = estatusActual;
    ocultarAlerta('alertaEstatus');
    var modal = document.getElementById('modalCambiarEstatus');
    if (modal && typeof bootstrap !== 'undefined') bootstrap.Modal.getOrCreateInstance(modal).show();
  };

  async function confirmarCambioEstatus() {
    var tituloId = (document.getElementById('tituloIdCambioEstatus') || {}).value || '';
    var nuevoEstatus = (document.getElementById('nuevoEstatus') || {}).value || '';
    if (!nuevoEstatus) { mostrarAlerta('alertaEstatus', 'warning', 'Selecciona un estatus.'); return; }
    try {
      var res = await apiFetch('/titulos-electronicos/' + encodeURIComponent(String(tituloId)) + '/estatus', {
        method: 'PUT',
        body: JSON.stringify({ estatus: nuevoEstatus })
      });
      if (res.ok) {
        var modal = document.getElementById('modalCambiarEstatus');
        if (modal && typeof bootstrap !== 'undefined') bootstrap.Modal.getInstance(modal).hide();
        await cargarTodosLosTitulos();
      } else {
        throw new Error('Error al actualizar estatus');
      }
    } catch (e) {
      mostrarAlerta('alertaEstatus', 'danger', 'Error: ' + escapeHtml(e.message || ''));
    }
  }

  // ─── init ─────────────────────────────────────────────────────────────────────

  document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('titulosElectronicosSection')) return;
    if (window._titulosElectronicosInitDone) return;
    window._titulosElectronicosInitDone = true;

    configurarFechaActual();

    // Filtros de la lista de alumnos
    ['titFiltroAlumno', 'titFiltroPrograma', 'titFiltroEstatus'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.addEventListener(id === 'titFiltroAlumno' ? 'input' : 'change', renderListaAlumnos);
    });

    // Formulario de generación
    var form = document.getElementById('formGenerarTitulo');
    if (form) form.addEventListener('submit', generarTitulo);

    var btnLimpiar = document.getElementById('btnLimpiarForm');
    if (btnLimpiar) btnLimpiar.addEventListener('click', limpiarFormulario);

    // Consulta de títulos
    var btnBuscarTitulo = document.getElementById('btnBuscarTitulo');
    if (btnBuscarTitulo) btnBuscarTitulo.addEventListener('click', filtrarYRenderizarTitulos);

    var inputBuscarTitulo = document.getElementById('buscarTitulo');
    if (inputBuscarTitulo) inputBuscarTitulo.addEventListener('input', filtrarYRenderizarTitulos);

    var filtroEstatus = document.getElementById('filtroEstatus');
    if (filtroEstatus) filtroEstatus.addEventListener('change', filtrarYRenderizarTitulos);

    var btnRecargarTitulos = document.getElementById('btnRecargarTitulos');
    if (btnRecargarTitulos) btnRecargarTitulos.addEventListener('click', cargarTodosLosTitulos);

    // Modal cambio estatus
    var btnConfirmar = document.getElementById('btnConfirmarCambioEstatus');
    if (btnConfirmar) btnConfirmar.addEventListener('click', confirmarCambioEstatus);

    // Cargar datos al entrar a la pestaña "Consultar"
    var tabConsultar = document.getElementById('consultar-tab');
    if (tabConsultar) {
      tabConsultar.addEventListener('shown.bs.tab', function () {
        if (!titulosCache.length) cargarTodosLosTitulos();
      });
    }

    // Cargar alumnos al init
    cargarAlumnos();
  });

})();

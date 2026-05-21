(function () {
  'use strict';

  var API = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
  var ciclos = [];
  var periodosCicloActual = [];
  var cicloEditandoId = null;
  /** Ciclo cuya tabla de periodos se muestra debajo (null = ninguno). */
  var cicloIdSeleccionadoPeriodos = null;

  function headersJson() {
    var h = { 'Content-Type': 'application/json' };
    var token = localStorage.getItem('token');
    if (token) h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  function headersGet() {
    var h = {};
    var token = localStorage.getItem('token');
    if (token) h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function labelTipoPeriodo(t) {
    var m = { SEMESTRE: 'Semestre', CUATRIMESTRE: 'Cuatrimestre', TETRAMESTRE: 'Tetramestre', TRIMESTRE: 'Trimestre', SEMANAL: 'Semanal' };
    return m[String(t || '').toUpperCase()] || String(t || '—');
  }

  function labelEstadoGestion(e) {
    var m = { INACTIVO: 'Planeacion', ACTIVO: 'Activo', CERRADO: 'Historico' };
    return m[String(e || '').toUpperCase()] || String(e || '—');
  }

  function labelEstadoCiclo(e) {
    var m = { ACTIVO: 'Activo', INACTIVO: 'Inactivo' };
    return m[String(e || '').toUpperCase()] || String(e || '—');
  }

  function ordenTipoPeriodo(t) {
    var o = { SEMANAL: 0, SEMESTRE: 1, CUATRIMESTRE: 2, TETRAMESTRE: 3, TRIMESTRE: 4 };
    var k = String(t || '').toUpperCase();
    return o.hasOwnProperty(k) ? o[k] : 99;
  }

  function badgeClassTipoPeriodo(t) {
    var k = String(t || '').toUpperCase();
    var m = {
      SEMESTRE: 'text-bg-primary',
      CUATRIMESTRE: 'text-bg-info',
      TETRAMESTRE: 'text-bg-success',
      TRIMESTRE: 'text-bg-warning text-dark',
      SEMANAL: 'text-bg-secondary'
    };
    return m[k] || 'text-bg-light text-dark border';
  }

  function bordeEstadoPeriodo(est) {
    var e = String(est || '').toUpperCase();
    if (e === 'ACTIVO') return 'border-success';
    if (e === 'CERRADO') return 'border-dark';
    return 'border-secondary';
  }

  function ordenarPeriodosCiclo(lista) {
    if (!Array.isArray(lista)) return [];
    return lista.slice().sort(function (a, b) {
      var ta = ordenTipoPeriodo(a.tipoPeriodo);
      var tb = ordenTipoPeriodo(b.tipoPeriodo);
      if (ta !== tb) return ta - tb;
      var na = a.numero != null ? Number(a.numero) : 0;
      var nb = b.numero != null ? Number(b.numero) : 0;
      if (na !== nb) return na - nb;
      var fa = a.fechaInicio ? String(a.fechaInicio) : '';
      var fb = b.fechaInicio ? String(b.fechaInicio) : '';
      return fa.localeCompare(fb);
    });
  }

  function fmtFecha(iso) {
    if (!iso) return '—';
    var s = String(iso).substring(0, 10);
    return s;
  }

  function fmtFechaLarga(iso) {
    if (!iso) return '—';
    var s = String(iso).substring(0, 10);
    var d = new Date(s + 'T12:00:00');
    if (isNaN(d.getTime())) return s;
    return d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  async function apiJson(path, opts) {
    var res = await fetch(API + path, Object.assign({}, opts || {}, { headers: Object.assign(headersJson(), (opts && opts.headers) || {}) }));
    if (!res.ok) {
      var msg = 'Error';
      try {
        var d = await res.json();
        msg = d.error || d.message || msg;
      } catch (_) {
        try { msg = await res.text(); } catch (__) {}
      }
      throw new Error(msg || 'Error de servidor');
    }
    if (res.status === 204) return null;
    return res.json();
  }

  async function apiGet(path) {
    var res = await fetch(API + path, { headers: headersGet() });
    if (!res.ok) {
      var msg = 'Error';
      try {
        var d = await res.json();
        msg = d.error || d.message || msg;
      } catch (_) {}
      throw new Error(msg || 'Error de servidor');
    }
    return res.json();
  }

  function actualizarEncabezadoPanelPeriodos() {
    var tit = document.getElementById('tituloPanelPeriodos');
    var sub = document.getElementById('subtituloPanelPeriodos');
    if (!tit || !sub) return;
    tit.textContent = 'Periodos del ciclo';
    if (!cicloIdSeleccionadoPeriodos) {
      sub.textContent = 'Seleccione un ciclo en el desplegable superior para ver sus periodos.';
      return;
    }
    var c = ciclos.find(function (x) { return String(x.id) === String(cicloIdSeleccionadoPeriodos); });
    if (!c) {
      sub.textContent = 'Ciclo no encontrado en el listado actual.';
      return;
    }
    sub.textContent = (c.nombre || 'Ciclo') + ' · Vigencia ' + fmtFecha(c.fechaInicio) + ' — ' + fmtFecha(c.fechaFin)
      + ' · ' + labelEstadoCiclo(c.estado);
  }

  function actualizarBotonesToolbarCiclo() {
    var sel = document.getElementById('selCicloRegistrado');
    var has = !!(sel && sel.value && ciclos.length);
    ['btnEditarCicloSeleccionado', 'btnEliminarCicloSeleccionado'].forEach(function (id) {
      var b = document.getElementById(id);
      if (b) b.disabled = !has;
    });
  }

  function llenarSelectCiclos() {
    var sel = document.getElementById('selCicloRegistrado');
    if (!sel) return;
    var conservarId = cicloIdSeleccionadoPeriodos;
    sel.innerHTML = '';
    if (!ciclos.length) {
      var o0 = document.createElement('option');
      o0.value = '';
      o0.textContent = 'No hay ciclos. Regístrelos en la pestaña «Registro de ciclo escolar».';
      sel.appendChild(o0);
      sel.disabled = true;
      cicloIdSeleccionadoPeriodos = null;
      periodosCicloActual = [];
      actualizarEncabezadoPanelPeriodos();
      renderTablaPeriodos();
      actualizarBotonesToolbarCiclo();
      return;
    }
    sel.disabled = false;
    var ph = document.createElement('option');
    ph.value = '';
    ph.textContent = 'Seleccione un ciclo…';
    sel.appendChild(ph);
    ciclos.forEach(function (c) {
      var o = document.createElement('option');
      o.value = String(c.id);
      o.textContent = (c.nombre || 'Ciclo') + ' · ' + fmtFecha(c.fechaInicio) + ' — ' + fmtFecha(c.fechaFin)
        + ' · ' + labelEstadoCiclo(c.estado);
      sel.appendChild(o);
    });
    var tieneConservar = conservarId != null && ciclos.some(function (c) { return String(c.id) === String(conservarId); });
    if (tieneConservar) {
      sel.value = String(conservarId);
      cicloIdSeleccionadoPeriodos = conservarId;
    } else {
      sel.value = '';
      cicloIdSeleccionadoPeriodos = null;
      periodosCicloActual = [];
      renderTablaPeriodos();
    }
    actualizarBotonesToolbarCiclo();
  }

  function obtenerIdCicloDesdeSelect() {
    var sel = document.getElementById('selCicloRegistrado');
    if (!sel || !sel.value) return null;
    var id = parseInt(sel.value, 10);
    return isNaN(id) ? null : id;
  }

  function onCambioSelectCiclo() {
    var id = obtenerIdCicloDesdeSelect();
    actualizarBotonesToolbarCiclo();
    if (!id) {
      cicloIdSeleccionadoPeriodos = null;
      periodosCicloActual = [];
      actualizarEncabezadoPanelPeriodos();
      renderTablaPeriodos();
      return;
    }
    seleccionarCicloYcargarPeriodos(id);
  }

  async function seleccionarCicloYcargarPeriodos(id) {
    if (!id) return;
    cicloIdSeleccionadoPeriodos = id;
    var sel = document.getElementById('selCicloRegistrado');
    if (sel && !sel.disabled) {
      sel.value = String(id);
    }
    actualizarBotonesToolbarCiclo();
    actualizarEncabezadoPanelPeriodos();
    var panel = document.getElementById('panelPeriodosCiclo');
    if (panel && panel.scrollIntoView) {
      panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    try {
      await cargarPeriodosDelCiclo(id);
    } catch (e) {
      alert(e.message || 'No se pudieron cargar los periodos');
    }
  }

  async function eliminarCiclo(id) {
    if (!id || !window.confirm('¿Eliminar este ciclo escolar?\n\nSe eliminarán también todos los periodos académicos del ciclo. En alumnos, calificaciones, grupos y horarios, las referencias a esos periodos quedarán sin asignar.')) {
      return;
    }
    try {
      var res = await fetch(API + '/ciclos-escolares/' + encodeURIComponent(id), {
        method: 'DELETE',
        headers: headersGet()
      });
      var data = null;
      try {
        data = await res.json();
      } catch (_) {}
      if (!res.ok) {
        var msg = (data && (data.error || data.message)) ? (data.error || data.message) : 'No se pudo eliminar el ciclo.';
        throw new Error(msg);
      }
      if (cicloEditandoId === id) {
        limpiarFormCiclo();
      }
      if (cicloIdSeleccionadoPeriodos != null && String(cicloIdSeleccionadoPeriodos) === String(id)) {
        cicloIdSeleccionadoPeriodos = null;
        periodosCicloActual = [];
        actualizarEncabezadoPanelPeriodos();
        renderTablaPeriodos();
      }
      await cargarCiclos();
    } catch (e) {
      alert(e.message || 'Error al eliminar');
    }
  }

  async function cargarCiclos() {
    ciclos = await apiGet('/ciclos-escolares');
    if (!Array.isArray(ciclos)) ciclos = [];
    llenarSelectCiclos();
    actualizarEncabezadoPanelPeriodos();
    if (cicloIdSeleccionadoPeriodos != null) {
      try {
        await cargarPeriodosDelCiclo(cicloIdSeleccionadoPeriodos);
      } catch (_) {}
    }
  }

  function renderTablaPeriodos() {
    var tbody = document.getElementById('periodosTableBody');
    if (!tbody) return;
    if (cicloIdSeleccionadoPeriodos == null) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Ningún ciclo seleccionado. Elija uno en el desplegable superior.</td></tr>';
      return;
    }
    if (!periodosCicloActual.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay periodos en este ciclo. Si acaba de crear el ciclo, actualice la página; en caso contrario contacte al administrador.</td></tr>';
      return;
    }
    var prevTipo = null;
    tbody.innerHTML = periodosCicloActual.map(function (p) {
      var tipoUp = String(p.tipoPeriodo || '').toUpperCase();
      var grupoNuevo = prevTipo !== null && tipoUp !== prevTipo;
      prevTipo = tipoUp;
      var selEstado = '<div class="rounded-start border-start border-4 ps-2 ' + escapeHtml(bordeEstadoPeriodo(p.estadoGestion)) + '">' +
        '<select class="form-select form-select-sm sel-estado-periodo w-100" style="min-width:0" data-id="' + p.id + '" title="Cambiar estado">' +
        ['INACTIVO', 'ACTIVO', 'CERRADO'].map(function (e) {
          return '<option value="' + e + '"' + (String(p.estadoGestion || '').toUpperCase() === e ? ' selected' : '') + '>' + labelEstadoGestion(e) + '</option>';
        }).join('') +
        '</select></div>';
      var trClass = 'periodo-ciclo-fila' + (grupoNuevo ? ' periodo-ciclo-nuevo-grupo' : '');
      var badgeTipo = '<span class="badge rounded-pill ' + badgeClassTipoPeriodo(p.tipoPeriodo) + ' fw-semibold">' + escapeHtml(labelTipoPeriodo(p.tipoPeriodo)) + '</span>';
      var numCell = p.numero != null
        ? '<span class="badge bg-body-secondary text-body border fw-bold px-2 py-1">' + escapeHtml(String(p.numero)) + '</span>'
        : '<span class="text-muted">—</span>';
      return '<tr class="' + trClass + '">' +
        '<td><code class="small text-body-secondary user-select-all">' + escapeHtml(p.codigo || '—') + '</code></td>' +
        '<td><span class="fw-semibold text-body">' + escapeHtml(p.nombre || '—') + '</span></td>' +
        '<td>' + badgeTipo + '</td>' +
        '<td class="text-center">' + numCell + '</td>' +
        '<td class="small"><span class="text-body">' + escapeHtml(fmtFechaLarga(p.fechaInicio)) + '</span> <span class="text-muted">→</span> <span class="text-body">' + escapeHtml(fmtFechaLarga(p.fechaFin)) + '</span></td>' +
        '<td>' + selEstado + '</td>' +
        '</tr>';
    }).join('');

    tbody.querySelectorAll('.sel-estado-periodo').forEach(function (sel) {
      sel.addEventListener('change', function () {
        var id = parseInt(sel.getAttribute('data-id'), 10);
        cambiarEstadoPeriodo(id, sel.value, sel);
      });
    });
  }

  async function cargarPeriodosDelCiclo(cicloId) {
    if (!cicloId) {
      periodosCicloActual = [];
      renderTablaPeriodos();
      return;
    }
    var raw = await apiGet('/periodos-academicos/por-ciclo/' + encodeURIComponent(cicloId));
    periodosCicloActual = Array.isArray(raw) ? ordenarPeriodosCiclo(raw) : [];
    renderTablaPeriodos();
  }

  async function cambiarEstadoPeriodo(id, estado, selectEl) {
    var prev = selectEl.getAttribute('data-prev');
    if (prev == null) {
      prev = selectEl.value;
      selectEl.setAttribute('data-prev', prev);
    }
    try {
      await apiJson('/periodos-academicos/' + id + '/estado-gestion', {
        method: 'PATCH',
        body: JSON.stringify({ estado: estado })
      });
      selectEl.setAttribute('data-prev', estado);
      if (cicloIdSeleccionadoPeriodos) {
        await cargarPeriodosDelCiclo(cicloIdSeleccionadoPeriodos);
      }
    } catch (e) {
      alert(e.message || 'No se pudo cambiar el estado');
      selectEl.value = prev;
    }
  }

  function limpiarFormCiclo() {
    cicloEditandoId = null;
    var f = document.getElementById('formCiclo');
    if (f) f.reset();
    var btn = document.getElementById('btnGuardarCiclo');
    if (btn) btn.textContent = 'Guardar ciclo';
    var tit = document.getElementById('tituloFormCiclo');
    if (tit) tit.textContent = 'Nuevo ciclo escolar';
  }

  function abrirEdicionCiclo(id) {
    var c = ciclos.find(function (x) { return x.id === id; });
    if (!c) return;
    cicloEditandoId = id;
    document.getElementById('cicloNombre').value = c.nombre || '';
    document.getElementById('cicloFechaInicio').value = fmtFecha(c.fechaInicio);
    document.getElementById('cicloFechaFin').value = fmtFecha(c.fechaFin);
    document.getElementById('cicloEstado').value = (c.estado || 'ACTIVO').toUpperCase();
    var btn = document.getElementById('btnGuardarCiclo');
    if (btn) btn.textContent = 'Actualizar ciclo';
    var tit = document.getElementById('tituloFormCiclo');
    if (tit) tit.textContent = 'Editar ciclo escolar';
    var tab = document.querySelector('[data-bs-target="#tabAltaCiclo"]');
    if (tab && typeof bootstrap !== 'undefined') bootstrap.Tab.getOrCreateInstance(tab).show();
  }

  async function guardarCiclo() {
    var nombre = (document.getElementById('cicloNombre').value || '').trim();
    var fi = document.getElementById('cicloFechaInicio').value;
    var ff = document.getElementById('cicloFechaFin').value;
    var est = (document.getElementById('cicloEstado').value || 'ACTIVO').toUpperCase();
    if (!nombre || !fi || !ff) {
      alert('Complete nombre y fechas del ciclo.');
      return;
    }
    var eraEdicion = !!cicloEditandoId;
    var created = null;
    try {
      if (cicloEditandoId) {
        await apiJson('/ciclos-escolares/' + cicloEditandoId, {
          method: 'PUT',
          body: JSON.stringify({ nombre: nombre, fechaInicio: fi, fechaFin: ff, estado: est })
        });
      } else {
        created = await apiJson('/ciclos-escolares', {
          method: 'POST',
          body: JSON.stringify({ nombre: nombre, fechaInicio: fi, fechaFin: ff, estado: est })
        });
      }
      limpiarFormCiclo();
      await cargarCiclos();
      var tabList = document.querySelector('[data-bs-target="#tabListadoCiclos"]');
      if (!eraEdicion && tabList && typeof bootstrap !== 'undefined') {
        bootstrap.Tab.getOrCreateInstance(tabList).show();
      }
      if (!eraEdicion && created && created.id) {
        await seleccionarCicloYcargarPeriodos(created.id);
      } else if (cicloIdSeleccionadoPeriodos) {
        await cargarPeriodosDelCiclo(cicloIdSeleccionadoPeriodos);
      }
    } catch (e) {
      alert(e.message || 'Error al guardar ciclo');
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var root = document.getElementById('ciclosPeriodosSection');
    if (!root) return;

    actualizarEncabezadoPanelPeriodos();
    renderTablaPeriodos();

    cargarCiclos().catch(function (e) {
      console.error(e);
      alert(e.message || 'No se pudieron cargar los ciclos');
    });

    var btnCiclo = document.getElementById('btnGuardarCiclo');
    if (btnCiclo) btnCiclo.addEventListener('click', function () { guardarCiclo(); });

    var btnLimpiarCiclo = document.getElementById('btnLimpiarFormCiclo');
    if (btnLimpiarCiclo) btnLimpiarCiclo.addEventListener('click', limpiarFormCiclo);

    var tabList = document.querySelector('[data-bs-target="#tabListadoCiclos"]');
    if (tabList) {
      tabList.addEventListener('shown.bs.tab', function () {
        cargarCiclos().catch(function () {});
      });
    }

    var selCiclo = document.getElementById('selCicloRegistrado');
    if (selCiclo) {
      selCiclo.addEventListener('change', function () { onCambioSelectCiclo(); });
    }
    var btnEd = document.getElementById('btnEditarCicloSeleccionado');
    if (btnEd) {
      btnEd.addEventListener('click', function () {
        var id = obtenerIdCicloDesdeSelect();
        if (id) abrirEdicionCiclo(id);
      });
    }
    var btnEl = document.getElementById('btnEliminarCicloSeleccionado');
    if (btnEl) {
      btnEl.addEventListener('click', function () {
        var id = obtenerIdCicloDesdeSelect();
        if (id) eliminarCiclo(id);
      });
    }
  });
})();

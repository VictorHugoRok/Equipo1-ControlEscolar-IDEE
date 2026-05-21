/**
 * Captura/confirmación masiva de calificaciones para certificados totales.
 * Requiere permisos de secretaría/admin (EDITAR_CALIFICACIONES + CONFIRMAR_CALIFICACIONES).
 *
 * Flujo:
 * - Carga filas alumno+programa desde /alumnos/resumen-programas
 * - Selecciona una fila (alumnoId+programaId)
 * - Carga asignaturas del plan (programa) + calificaciones existentes del alumno
 * - Permite capturar calificación (0..10) por asignatura
 * - Guarda en /calificaciones/bulk-confirmar-programa (upsert + CONFIRMADA)
 * - Excel: plantilla e importación por columna matricula (+ programaId, asignaturaId, calificacionFinal)
 */
(function () {
  'use strict';

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function norm(s) {
    return String(s || '').trim().toLowerCase();
  }

  async function api(path, opts) {
    // Solo para respuestas binarias u otros casos que necesiten el Response crudo.
    const base = (typeof getApiBaseUrl === 'function')
      ? getApiBaseUrl()
      : ((typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api');
    const token = (typeof getToken === 'function') ? getToken() : localStorage.getItem('token');
    const headers = { ...(opts && opts.headers ? opts.headers : {}) };
    if (token && token !== 'null' && token !== 'undefined') headers['Authorization'] = 'Bearer ' + token;
    return fetch(base + path, { ...(opts || {}), headers });
  }

  /**
   * Petición JSON autenticada. authFetch ya parsea el cuerpo y lanza en HTTP de error
   * (no devuelve Response con .ok / .json()).
   */
  async function fetchJsonAuthed(path, opts) {
    if (typeof authFetch === 'function') {
      return await authFetch(path, opts || { method: 'GET' });
    }
    const res = await api(path, opts || { method: 'GET' });
    if (!res.ok) throw new Error('Error en petición');
    return await res.json().catch(function () { return null; });
  }

  async function fetchBlobAuthed(path) {
    var apiBase = (typeof getApiBaseUrl === 'function')
      ? getApiBaseUrl()
      : ((typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api');
    var token = (typeof getToken === 'function') ? getToken() : localStorage.getItem('token');
    async function doReq(tok) {
      var headers = {};
      if (tok) headers['Authorization'] = 'Bearer ' + tok;
      return fetch(apiBase + path, { method: 'GET', headers: headers });
    }
    var r = await doReq(token);
    if (r.status === 401 && typeof refreshAccessToken === 'function') {
      await refreshAccessToken();
      token = (typeof getToken === 'function') ? getToken() : localStorage.getItem('token');
      r = await doReq(token);
    }
    if (!r.ok) {
      var msg = 'No se pudo descargar la plantilla.';
      try {
        var ct = r.headers.get('Content-Type') || '';
        if (ct.indexOf('application/json') !== -1) {
          var ej = await r.json();
          msg = (ej && (ej.mensaje || ej.message || ej.error)) || msg;
        }
      } catch (_) {}
      throw new Error(msg);
    }
    return await r.blob();
  }

  function toast(msg, type) {
    if (typeof showSystemToast === 'function') {
      showSystemToast(msg, { type: type || 'info', durationMs: 4200 });
      return;
    }
    alert(msg);
  }

  let rows = [];
  let selected = null; // { alumnoId, programaId, ... }
  let asignaturas = [];
  let calificaciones = [];

  async function descargarPlantillaExcel() {
    if (!selected) return;
    const mat = String(selected.matricula || '').trim();
    if (!mat) {
      toast('El alumno seleccionado no tiene matrícula; no se puede generar la plantilla.', 'warning');
      return;
    }
    try {
      const url = '/calificaciones/excel/plantilla-certificados?matricula='
        + encodeURIComponent(mat)
        + '&programaId=' + encodeURIComponent(String(selected.programaId));
      const blob = await fetchBlobAuthed(url);
      const a = document.createElement('a');
      const objUrl = URL.createObjectURL(blob);
      a.href = objUrl;
      a.download = 'plantilla_calificaciones.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(objUrl);
    } catch (e) {
      console.error(e);
      toast(e && e.message ? e.message : 'Error al descargar plantilla.', 'error');
    }
  }

  async function cargarExcelYConfirmar(file) {
    if (!selected) return;
    if (!file) return;
    try {
      const periodo = String((document.getElementById('capCalifPeriodo') || {}).value || '').trim();
      const fd = new FormData();
      fd.append('archivo', file);
      const url = '/calificaciones/excel/importar-certificados' + (periodo ? ('?periodo=' + encodeURIComponent(periodo)) : '');
      await fetchJsonAuthed(url, { method: 'POST', body: fd });
      toast('Excel importado. Calificaciones guardadas y confirmadas.', 'success');
      await cargarDatosSeleccion();
    } catch (e) {
      console.error(e);
      toast(e && e.message ? e.message : 'Error al importar Excel.', 'error');
    }
  }

  function buildProgramasMap(list) {
    const map = {};
    (list || []).forEach(r => {
      if (r && r.programaId != null) {
        map[String(r.programaId)] = r.programaNombre || r.programaClave || ('Programa ' + r.programaId);
      }
    });
    return map;
  }

  function renderProgramasFilter() {
    const sel = document.getElementById('capCalifFiltroPrograma');
    if (!sel) return;
    const prev = sel.value || '';
    const map = buildProgramasMap(rows);
    sel.innerHTML = '<option value=\"\">Todos los programas</option>';
    Object.keys(map).sort((a, b) => String(map[a]).localeCompare(String(map[b]), 'es')).forEach(id => {
      const opt = document.createElement('option');
      opt.value = id;
      opt.textContent = map[id];
      sel.appendChild(opt);
    });
    if (prev) sel.value = prev;
  }

  function rowMatches(r) {
    const q = norm((document.getElementById('capCalifFiltro') || {}).value || '');
    const prog = (document.getElementById('capCalifFiltroPrograma') || {}).value || '';
    const est = (document.getElementById('capCalifFiltroEstatus') || {}).value || '';
    if (prog && String(r.programaId) !== String(prog)) return false;
    if (est && String(r.estatusMatricula || '').toUpperCase() !== String(est).toUpperCase()) return false;
    if (!q) return true;
    const nombre = [r.nombre, r.apellidoPaterno, r.apellidoMaterno].filter(Boolean).join(' ');
    const blob = norm((r.matricula || '') + ' ' + nombre + ' ' + (r.curp || '') + ' ' + (r.programaNombre || r.programaClave || ''));
    return q.split(/\s+/).filter(Boolean).every(t => blob.indexOf(t) !== -1);
  }

  function renderLista() {
    const cont = document.getElementById('capCalifLista');
    const count = document.getElementById('capCalifCount');
    if (!cont) return;
    const list = (rows || []).filter(rowMatches).slice().sort((a, b) => {
      const na = (a.apellidoPaterno || '') + (a.apellidoMaterno || '') + (a.nombre || '');
      const nb = (b.apellidoPaterno || '') + (b.apellidoMaterno || '') + (b.nombre || '');
      return na.localeCompare(nb, 'es');
    });
    if (count) count.textContent = list.length;
    if (!list.length) {
      cont.innerHTML = '<div class=\"list-group-item text-center text-muted py-4\">Sin resultados.</div>';
      return;
    }
    cont.innerHTML = list.map(r => {
      const nombre = [r.nombre, r.apellidoPaterno, r.apellidoMaterno].filter(Boolean).join(' ').trim() || '—';
      const programa = r.programaNombre || r.programaClave || '—';
      const est = (r.estatusMatricula || '').toUpperCase();
      const badge = est ? ('<span class=\"badge bg-secondary\">' + escapeHtml(est) + '</span>') : '';
      const key = String(r.alumnoId) + '|' + String(r.programaId);
      const checked = selected && String(selected.alumnoId) === String(r.alumnoId) && String(selected.programaId) === String(r.programaId);
      return (
        '<label class=\"list-group-item list-group-item-action d-flex gap-2 align-items-start\">' +
        '<input class=\"form-check-input flex-shrink-0\" type=\"radio\" name=\"capCalifRadio\" ' +
        'value=\"' + escapeHtml(key) + '\" ' + (checked ? 'checked' : '') + '>' +
        '<div class=\"flex-grow-1 min-w-0\">' +
        '<div class=\"fw-semibold text-truncate\" title=\"' + escapeHtml(nombre) + '\">' + escapeHtml(nombre) + '</div>' +
        '<div class=\"small text-muted d-flex flex-wrap gap-2 align-items-center\">' +
        '<span class=\"text-nowrap\"><i class=\"bi bi-person-vcard\" aria-hidden=\"true\"></i> ' + escapeHtml(r.matricula || '—') + '</span>' +
        badge +
        '</div>' +
        '<div class=\"small text-muted text-truncate\" title=\"' + escapeHtml(programa) + '\"><i class=\"bi bi-mortarboard\" aria-hidden=\"true\"></i> ' + escapeHtml(programa) + '</div>' +
        '</div>' +
        '</label>'
      );
    }).join('');
  }

  function califMapByAsignaturaId(list, programaId) {
    const map = {};
    (list || []).forEach(c => {
      if (!c || !c.asignatura || c.asignatura.id == null) return;
      const pid = c.asignatura && c.asignatura.programa && c.asignatura.programa.id != null ? String(c.asignatura.programa.id) : '';
      if (programaId != null && pid && String(pid) !== String(programaId)) return;
      // Tomar la más reciente (la lista suele venir ya ordenada, pero no dependemos)
      map[String(c.asignatura.id)] = c;
    });
    return map;
  }

  function renderTabla() {
    const tbody = document.getElementById('capCalifTablaBody');
    const subt = document.getElementById('capCalifSubtitulo');
    const btn = document.getElementById('capCalifBtnGuardar');
    if (!tbody) return;

    if (!selected) {
      if (subt) subt.textContent = 'Selecciona un alumno a la izquierda.';
      tbody.innerHTML = '<tr><td colspan=\"4\" class=\"text-center text-muted py-4\">Selecciona un alumno.</td></tr>';
      if (btn) btn.disabled = true;
      const btnPlantilla0 = document.getElementById('capCalifBtnPlantilla');
      if (btnPlantilla0) btnPlantilla0.disabled = true;
      return;
    }
    const nombre = [selected.nombre, selected.apellidoPaterno, selected.apellidoMaterno].filter(Boolean).join(' ').trim();
    const programa = selected.programaNombre || selected.programaClave || '';
    if (subt) subt.textContent = (nombre ? (nombre + ' — ') : '') + (programa || '');

    const map = califMapByAsignaturaId(calificaciones, selected.programaId);
    const rowsHtml = (asignaturas || []).map(a => {
      const c = map[String(a.id)] || null;
      const estado = c && c.estadoAprobacion ? String(c.estadoAprobacion) : '—';
      const val = c && c.calificacionFinal != null ? String(c.calificacionFinal) : '';
      const perVal = c && c.periodo != null ? String(c.periodo) : '';
      const disabled = false;
      return (
        '<tr>' +
        '<td>' + escapeHtml((a.clave ? (a.clave + ' - ') : '') + (a.nombre || 'Asignatura')) + '</td>' +
        '<td>' +
        '<input class=\"form-control form-control-sm cap-calif-periodo\" type=\"text\" inputmode=\"text\" ' +
        'placeholder=\"ej. 2024-2\" title=\"Ciclo o periodo escolar en que cursó la materia\" ' +
        'data-asignatura-id=\"' + escapeHtml(a.id) + '\" value=\"' + escapeHtml(perVal) + '\" ' + (disabled ? 'disabled' : '') + ' />' +
        '</td>' +
        '<td>' +
        '<input class=\"form-control form-control-sm cap-calif-input\" type=\"number\" min=\"0\" max=\"10\" step=\"0.01\" ' +
        'data-asignatura-id=\"' + escapeHtml(a.id) + '\" value=\"' + escapeHtml(val) + '\" ' + (disabled ? 'disabled' : '') + ' />' +
        '</td>' +
        '<td class=\"small\">' + escapeHtml(estado) + '</td>' +
        '</tr>'
      );
    }).join('');
    tbody.innerHTML = rowsHtml || '<tr><td colspan=\"4\" class=\"text-center text-muted py-4\">No hay asignaturas en este programa.</td></tr>';
    if (btn) btn.disabled = !asignaturas || !asignaturas.length;
    const btnPlantilla = document.getElementById('capCalifBtnPlantilla');
    if (btnPlantilla) btnPlantilla.disabled = false;
  }

  async function cargarDatosSeleccion() {
    if (!selected) return;
    try {
      asignaturas = [];
      calificaciones = [];
      renderTabla();

      const asigJson = await fetchJsonAuthed(
        '/asignaturas?programaId=' + encodeURIComponent(String(selected.programaId)),
        { method: 'GET' }
      );
      asignaturas = Array.isArray(asigJson) ? asigJson : [];

      var calJson = [];
      try {
        calJson = await fetchJsonAuthed(
          '/calificaciones?alumnoId=' + encodeURIComponent(String(selected.alumnoId)),
          { method: 'GET' }
        );
      } catch (_) {
        calJson = [];
      }
      var rawCal = Array.isArray(calJson) ? calJson : [];
      var aidSel = String(selected.alumnoId);
      calificaciones = rawCal.filter(function (c) {
        if (!c || !c.alumno || c.alumno.id == null) return false;
        return String(c.alumno.id) === aidSel;
      });

      renderTabla();
    } catch (e) {
      console.error(e);
      toast(e && e.message ? e.message : 'Error al cargar datos.', 'error');
      renderTabla();
    }
  }

  function leerItemsTabla() {
    const items = [];
    const periodoDefecto = String((document.getElementById('capCalifPeriodo') || {}).value || '').trim();
    document.querySelectorAll('.cap-calif-input').forEach(inp => {
      const aid = inp.getAttribute('data-asignatura-id');
      const raw = String(inp.value || '').trim();
      if (!aid) return;
      if (!raw) return;
      const num = Number(raw);
      if (!Number.isFinite(num)) return;
      const tr = inp.closest ? inp.closest('tr') : null;
      const perInp = tr ? tr.querySelector('.cap-calif-periodo') : null;
      let per = perInp ? String(perInp.value || '').trim() : '';
      if (!per && periodoDefecto) per = periodoDefecto;
      const item = { asignaturaId: Number(aid), calificacionFinal: num };
      if (per) item.periodo = per;
      items.push(item);
    });
    return items;
  }

  async function guardarYConfirmar() {
    if (!selected) return;
    const btn = document.getElementById('capCalifBtnGuardar');
    const periodo = String((document.getElementById('capCalifPeriodo') || {}).value || '').trim();
    const items = leerItemsTabla();
    if (!items.length) {
      toast('No hay calificaciones capturadas para guardar.', 'warning');
      return;
    }
    if (btn) btn.disabled = true;
    try {
      await fetchJsonAuthed('/calificaciones/bulk-confirmar-programa', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          alumnoId: selected.alumnoId,
          programaId: selected.programaId,
          periodo: periodo || '',
          items: items
        })
      });
      toast('Calificaciones guardadas y confirmadas.', 'success');
      await cargarDatosSeleccion();
    } catch (e) {
      console.error(e);
      toast(e && e.message ? e.message : 'Error al guardar.', 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  async function cargarRows() {
    const cont = document.getElementById('capCalifLista');
    if (!cont) return;
    try {
      const json = await fetchJsonAuthed('/alumnos/resumen-programas', { method: 'GET' });
      rows = Array.isArray(json) ? json : [];
      renderProgramasFilter();
      renderLista();
    } catch (e) {
      console.error(e);
      cont.innerHTML = '<div class=\"list-group-item text-center text-danger py-4\">No se pudo cargar.</div>';
    }
  }

  function wire() {
    const filtro = document.getElementById('capCalifFiltro');
    const selProg = document.getElementById('capCalifFiltroPrograma');
    const selEst = document.getElementById('capCalifFiltroEstatus');
    const lista = document.getElementById('capCalifLista');
    const btnGuardar = document.getElementById('capCalifBtnGuardar');
    const btnPlantilla = document.getElementById('capCalifBtnPlantilla');
    const excelInput = document.getElementById('capCalifExcelInput');

    if (filtro) filtro.addEventListener('input', renderLista);
    if (selProg) selProg.addEventListener('change', renderLista);
    if (selEst) selEst.addEventListener('change', renderLista);

    if (lista) {
      lista.addEventListener('change', function (ev) {
        const t = ev && ev.target ? ev.target : null;
        if (!t || !t.matches || !t.matches('input[type=\"radio\"][name=\"capCalifRadio\"]')) return;
        const parts = String(t.value || '').split('|');
        const aid = parts[0] || '';
        const pid = parts[1] || '';
        selected = (rows || []).find(r => String(r.alumnoId) === String(aid) && String(r.programaId) === String(pid)) || null;
        cargarDatosSeleccion();
      });
    }

    if (btnGuardar) btnGuardar.addEventListener('click', function (ev) {
      ev.preventDefault();
      guardarYConfirmar();
    });
    if (btnPlantilla) btnPlantilla.addEventListener('click', function (ev) {
      ev.preventDefault();
      descargarPlantillaExcel();
    });
    if (excelInput) {
      excelInput.addEventListener('change', function () {
        const f = this.files && this.files[0] ? this.files[0] : null;
        if (!f) return;
        cargarExcelYConfirmar(f).finally(() => { try { excelInput.value = ''; } catch (_) {} });
      });
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('capCalifCertSection')) return;
    wire();
    cargarRows();
  });
})();


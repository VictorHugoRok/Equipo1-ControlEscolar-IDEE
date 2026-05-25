/**
 * Calificaciones (refactor completo)
 *
 * Reglas:
 * - ALUMNO: solo consulta (read-only)
 * - MAESTRO: captura (CAPTURADA) y envía (EN_REVISION)
 * - SECRETARÍA: consulta, asigna, edita y confirma (CONFIRMADA). Puede modificar manteniendo CONFIRMADA.
 *
 * Backend existente:
 * - /api/alumnos/me/calificaciones
 * - /api/maestros/me/clases
 * - /api/maestros/me/grupos/{grupoId}
 * - /api/maestros/me/calificaciones
 * - /api/calificaciones (POST/PUT)
 * - /api/calificaciones/{id}/enviar-revision
 * - /api/calificaciones/{id}/confirmar
 * - /api/calificaciones/{id}/modificar
 * - /api/calificaciones?alumnoId=&periodo= (calificaciones del alumno en un periodo; lista vacía si no hay)
 * - /api/calificaciones/por-grupo-asignatura
 * - /api/observaciones-calificacion
 */

(function () {
  'use strict';

  let catalogoObservaciones = [];
  let programasParaFiltro = [];
  let ciclosEscolares = [];
  const ciclosEscolaresPorTipo = {};
  let asignaturasPorPrograma = {};
  let gruposPorPrograma = {};

  function getApiBase() {
    return (typeof API_URL !== 'undefined' && API_URL)
      ? API_URL
      : (typeof API_BASE_URL !== 'undefined' ? API_BASE_URL : 'http://localhost:8080/api');
  }

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function errorToText(err) {
    if (!err) return 'Error desconocido';
    if (typeof err === 'string') return err;
    const parts = [];
    if (err.name) parts.push(String(err.name));
    if (err.message) parts.push(String(err.message));
    const base = parts.length ? parts.join(': ') : String(err);
    const stack = err.stack ? String(err.stack) : '';
    return stack ? (base + '\n\n' + stack) : base;
  }

  function mostrarErrorCopiable(titulo, err) {
    try {
      const modalEl = document.getElementById('modalCalifError');
      const ta = document.getElementById('modalCalifErrorText');
      const lbl = document.getElementById('modalCalifErrorLabel');
      const status = document.getElementById('modalCalifErrorCopyStatus');
      const btnCopy = document.getElementById('btnCalifErrorCopy');
      if (!modalEl || !ta) {
        alert((titulo ? (titulo + ': ') : '') + (err && err.message ? err.message : 'Error'));
        return;
      }
      if (lbl && titulo) lbl.textContent = titulo;
      const text = errorToText(err);
      ta.value = text;
      ta.scrollTop = 0;
      if (status) status.textContent = '';

      if (btnCopy) {
        btnCopy.onclick = async function () {
          try {
            await navigator.clipboard.writeText(text);
            if (status) status.textContent = 'Copiado al portapapeles.';
          } catch (_) {
            ta.focus();
            ta.select();
            if (status) status.textContent = 'No se pudo copiar automáticamente. Selecciona y copia manualmente.';
          }
        };
      }

      // Mostrar modal (bootstrap si existe, si no, fallback básico)
      if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
      } else {
        modalEl.classList.add('show');
        modalEl.style.display = 'block';
        modalEl.removeAttribute('aria-hidden');
        modalEl.setAttribute('aria-modal', 'true');
        modalEl.setAttribute('role', 'dialog');
        // Backdrop simple
        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop fade show';
        backdrop.dataset.califBackdrop = '1';
        document.body.appendChild(backdrop);
        document.body.classList.add('modal-open');
        // Cerrar manual al hacer click en cerrar
        modalEl.querySelectorAll('[data-bs-dismiss="modal"]').forEach(function (btn) {
          btn.onclick = function () {
            modalEl.classList.remove('show');
            modalEl.style.display = 'none';
            modalEl.setAttribute('aria-hidden', 'true');
            document.querySelectorAll('.modal-backdrop[data-calif-backdrop="1"], .modal-backdrop[data-califBackdrop="1"]').forEach(function (b) { b.remove(); });
            document.body.classList.remove('modal-open');
          };
        });
      }
    } catch (e) {
      alert((err && err.message) ? err.message : 'Error');
    }
  }

  // Captura global para poder copiar cualquier error en Calificaciones
  if (!window.__califGlobalErrorHookInstalled) {
    window.__califGlobalErrorHookInstalled = true;
    window.addEventListener('error', function (ev) {
      try {
        const err = ev && ev.error ? ev.error : new Error(ev && ev.message ? ev.message : 'Error');
        mostrarErrorCopiable('Error en Calificaciones', err);
      } catch (_) { }
    });
    window.addEventListener('unhandledrejection', function (ev) {
      try {
        const reason = ev && ev.reason ? ev.reason : new Error('Promise rejection');
        mostrarErrorCopiable('Error en Calificaciones', reason);
      } catch (_) { }
    });
  }

  function tipoUsuarioActual() {
    if (window.currentUser && window.currentUser.tipoUsuario) return String(window.currentUser.tipoUsuario);
    const t = localStorage.getItem('userTipo');
    return t ? String(t) : '';
  }

  function badgeEstado(estado) {
    const e = (estado || '').toUpperCase();
    if (e === 'PENDIENTE') return '<span class="badge bg-secondary">Pendiente</span>';
    if (e === 'CAPTURADA') return '<span class="badge bg-warning text-dark">Capturada</span>';
    if (e === 'EN_REVISION') return '<span class="badge bg-info">En revisión</span>';
    if (e === 'CONFIRMADA') return '<span class="badge bg-success">Confirmada</span>';
    return '<span class="badge bg-secondary">—</span>';
  }

  function badgeEstatusMatricula(estatus) {
    const e = (estatus || '').toUpperCase();
    const cls = e === 'ACTIVA' ? 'bg-success-subtle text-success'
      : e === 'BAJA_TEMPORAL' ? 'bg-warning-subtle text-warning'
        : e === 'BAJA_DEFINITIVA' ? 'bg-danger-subtle text-danger'
          : e === 'EGRESADO' ? 'bg-info-subtle text-info'
            : 'bg-secondary-subtle text-secondary';
    const label = e === 'ACTIVA' ? 'Activa'
      : e === 'BAJA_TEMPORAL' ? 'Baja temporal'
        : e === 'BAJA_DEFINITIVA' ? 'Baja definitiva'
          : e === 'EGRESADO' ? 'Egresado'
            : (estatus || '—');
    return '<span class="badge ' + cls + '">' + escapeHtml(label) + '</span>';
  }

  /** Valor numérico para guardar: acepta cualquier decimal razonable; máx. 2 decimales. */
  function parsearCalificacionFinal(val) {
    const n = Number(val);
    if (Number.isNaN(n)) return null;
    return Math.round(n * 100) / 100;
  }

  function formatoCalificacionDisplay(n) {
    if (n == null || Number.isNaN(Number(n))) return '';
    const v = Math.round(Number(n) * 100) / 100;
    // Siempre 2 decimales (9.00), no 9.0
    return v.toFixed(2);
  }

  /** Total de puntos (0..100) -> calificación final (0.00..10.00), 2 decimales. */
  function calificacionFinalDesdePuntos(totalPuntos) {
    const t = Number(totalPuntos);
    if (Number.isNaN(t)) return null;
    const clamped = Math.max(0, Math.min(100, t));
    const final = clamped / 10;
    return Math.round(final * 100) / 100;
  }

  /**
   * Flechas ↑/↓: paso de 0.5 (comportamiento auxiliar). La captura manual puede usar cualquier decimal válido (step="any").
   */
  function vincularPasoCalificacionFlechas05(container) {
    const root = container && container.querySelectorAll ? container : document;
    root.querySelectorAll('.inpCal').forEach(function (inp) {
      if (inp.dataset.califFlechas05 === '1') return;
      inp.dataset.califFlechas05 = '1';
      inp.addEventListener('keydown', function (e) {
        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
        e.preventDefault();
        const min = parseFloat(inp.getAttribute('min') || '0');
        const max = parseFloat(inp.getAttribute('max') || '100');
        const delta = max > 10 ? 1 : 0.5;
        let v = inp.value === '' || inp.value === '.' ? NaN : parseFloat(inp.value);
        if (Number.isNaN(v)) v = min;
        let nv = Math.round((v + (e.key === 'ArrowUp' ? delta : -delta)) * 100) / 100;
        if (nv < min) nv = min;
        if (nv > max) nv = max;
        inp.value = max > 10 ? String(nv) : formatoCalificacionDisplay(nv);
      });
    });
  }

  /** Calificación guardada 0–10 → porcentaje 0–100 para mostrar/editar sin criterios */
  function porcentajeUiDesdeCalificacionFinal10(cf10) {
    if (cf10 == null || Number.isNaN(Number(cf10))) return '';
    const n = Math.round(Number(cf10) * 10 * 100) / 100;
    return String(n);
  }

  function apiFetch(path, options) {
    const base = getApiBase();
    const url = (path.indexOf('/') === 0) ? base + path : base + '/' + path;

    // Preferir authFetch si existe (refresh token y 401 retry)
    if (typeof authFetch === 'function') {
      // authFetch (en auth.js) retorna JSON (o null) y lanza Error en HTTP !ok.
      // Aquí lo envolvemos con una interfaz tipo Response para reutilizar el código existente.
      return Promise.resolve()
        .then(() => authFetch(path, options || {}))
        .then((data) => {
          const isNoContent = data == null;
          return {
            ok: true,
            status: isNoContent ? 204 : 200,
            headers: { get: () => 'application/json' },
            json: async () => data,
            text: async () => (isNoContent ? '' : JSON.stringify(data))
          };
        });
    }

    const token = localStorage.getItem('token');
    const headers = { 'Content-Type': 'application/json' };
    if (token && token !== 'null' && token !== 'undefined') headers['Authorization'] = 'Bearer ' + token;
    return fetch(url, { ...(options || {}), headers: { ...headers, ...((options || {}).headers || {}) } });
  }

  async function cargarObservaciones() {
    try {
      const r = await apiFetch('/observaciones-calificacion', { method: 'GET' });
      if (r.ok) {
        const json = await r.json().catch(() => []);
        catalogoObservaciones = Array.isArray(json) ? json : [];
        return catalogoObservaciones;
      }
    } catch (_) {}
    catalogoObservaciones = [];
    return [];
  }

  function optionsObservaciones(selectedId) {
    const def = selectedId != null ? selectedId : 100;
    if (!catalogoObservaciones.length) {
      return '<option value="100"' + (def === 100 ? ' selected' : '') + '>100 - Ordinario</option>';
    }
    return catalogoObservaciones.map(function (o) {
      const sel = (o.id === def || (def == null && o.id === 100)) ? ' selected' : '';
      return '<option value="' + o.id + '"' + sel + '>' + o.id + ' - ' + toTitleCaseEs(o.observacion || '') + '</option>';
    }).join('');
  }

  function toTitleCaseEs(s) {
    s = String(s || '').trim();
    if (!s) return '';
    var lower = s.toLocaleLowerCase('es-MX');
    return lower.split(/\s+/g).map(function (w, idx) {
      if (!w) return w;
      if (idx > 0 && (w === 'de' || w === 'del' || w === 'la' || w === 'las' || w === 'los' || w === 'y' || w === 'e' || w === 'en' || w === 'al')) {
        return w;
      }
      return w.charAt(0).toLocaleUpperCase('es-MX') + w.slice(1);
    }).join(' ');
  }

  function nombreCompletoAlumno(a) {
    const n = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ').trim();
    return n || a.matricula || '—';
  }

  function indexByAlumnoId(calificaciones) {
    const m = {};
    (calificaciones || []).forEach(c => {
      const aid = c.alumno && c.alumno.id != null ? String(c.alumno.id) : '';
      if (aid) m[aid] = c;
    });
    return m;
  }

  function indexByAsignaturaId(calificaciones) {
    const m = {};
    (calificaciones || []).forEach(c => {
      const sid = c.asignatura && c.asignatura.id != null ? String(c.asignatura.id) : '';
      if (sid && !m[sid]) m[sid] = c;
    });
    return m;
  }

  async function cargarGrupo(grupoId, maestroMode) {
    const path = maestroMode
      ? ('/maestros/me/grupos/' + encodeURIComponent(grupoId))
      : ('/grupos/' + encodeURIComponent(grupoId));
    const r = await apiFetch(path, { method: 'GET' });
    if (!r.ok) throw new Error('No se pudo cargar el grupo.');
    return await r.json();
  }

  async function cargarCalificacionesGrupoAsignatura(grupoId, asignaturaId, maestroMode) {
    if (maestroMode) {
      const r = await apiFetch('/maestros/me/calificaciones', { method: 'GET' });
      const all = r.ok ? await r.json().catch(() => []) : [];
      const rows = Array.isArray(all) ? all : [];
      return rows.filter(c =>
        c.grupo && String(c.grupo.id) === String(grupoId) &&
        c.asignatura && String(c.asignatura.id) === String(asignaturaId)
      );
    }
    const r = await apiFetch('/calificaciones/por-grupo-asignatura?grupoId=' + encodeURIComponent(grupoId) + '&asignaturaId=' + encodeURIComponent(asignaturaId), { method: 'GET' });
    const rows = r.ok ? await r.json().catch(() => []) : [];
    return Array.isArray(rows) ? rows : [];
  }

  async function cargarCalificacionesAlumnoPeriodo({ alumnoId, periodo, grupoId, asignaturaId, estado }) {
    const qs = new URLSearchParams();
    qs.set('alumnoId', String(alumnoId));
    qs.set('periodo', String(periodo || ''));
    if (grupoId) qs.set('grupoId', String(grupoId));
    if (asignaturaId) qs.set('asignaturaId', String(asignaturaId));
    if (estado) qs.set('estado', String(estado));
    const r = await apiFetch('/calificaciones?' + qs.toString(), { method: 'GET' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || 'No se pudieron cargar calificaciones del alumno.');
    }
    const rows = await r.json().catch(() => []);
    return Array.isArray(rows) ? rows : [];
  }

  async function cargarAlumnosPorPrograma(programaId) {
    if (!programaId) return [];
    const r = await apiFetch('/alumnos?programaId=' + encodeURIComponent(programaId), { method: 'GET', cache: 'no-store' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || 'No se pudieron cargar alumnos del programa.');
    }
    const rows = await r.json().catch(() => []);
    return Array.isArray(rows) ? rows : [];
  }

  async function cargarAlumnosTodos() {
    const r = await apiFetch('/alumnos', { method: 'GET', cache: 'no-store' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || 'No se pudieron cargar alumnos.');
    }
    const rows = await r.json().catch(() => []);
    return Array.isArray(rows) ? rows : [];
  }

  async function upsertCalificacion({ calId, alumnoId, asignaturaId, grupoId, periodo, calificacionFinal, asistenciaPorcentaje, idObservaciones, criterios, capturaComoDocente }) {
    const body = calId
      ? { calificacionFinal, asistenciaPorcentaje, idObservaciones, criterios }
      : { alumnoId, asignaturaId, grupoId, periodo, calificacionFinal, asistenciaPorcentaje, idObservaciones, criterios };
    const path = calId ? ('/calificaciones/' + encodeURIComponent(calId)) : '/calificaciones';
    const method = calId ? 'PUT' : 'POST';
    const headers = {};
    if (capturaComoDocente) headers['X-Captura-Como'] = 'DOCENTE';
    const r = await apiFetch(path, { method, headers, body: JSON.stringify(body) });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('Error ' + r.status));
    }
    return await r.json();
  }

  async function cargarCriteriosMaestro(grupoId, asignaturaId) {
    if (!grupoId || !asignaturaId) return [];
    const r = await apiFetch('/maestros/me/criterios?grupoId=' + encodeURIComponent(String(grupoId)) + '&asignaturaId=' + encodeURIComponent(String(asignaturaId)), { method: 'GET' });
    if (!r.ok) return [];
    const json = await r.json().catch(() => []);
    return Array.isArray(json) ? json : [];
  }

  async function enviarCalificacion(calId) {
    const r = await apiFetch('/calificaciones/' + encodeURIComponent(calId) + '/enviar-revision', { method: 'POST' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('Error ' + r.status));
    }
    return await r.json();
  }

  async function confirmarCalificacion(calId) {
    const r = await apiFetch('/calificaciones/' + encodeURIComponent(calId) + '/confirmar', { method: 'POST' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('Error ' + r.status));
    }
    return await r.json();
  }

  async function editarCalificacionEnRevision(calId, payload) {
    const r = await apiFetch('/calificaciones/' + encodeURIComponent(calId) + '/editar-revision', { method: 'PUT', body: JSON.stringify(payload || {}) });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('Error ' + r.status));
    }
    return await r.json();
  }

  async function modificarCalificacion(calId, payload) {
    const r = await apiFetch('/calificaciones/' + encodeURIComponent(calId) + '/modificar', { method: 'PUT', body: JSON.stringify(payload || {}) });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('Error ' + r.status));
    }
    return await r.json();
  }

  // -------------------- VISTA ALUMNO --------------------
  async function initVistaAlumno() {
    const desc = document.getElementById('calificacionesDesc');
    if (desc) desc.textContent = 'Solo se muestran calificaciones ya confirmadas por la Secretaría Académica (oficiales).';
    document.getElementById('calificacionesVistaAlumno')?.classList.remove('d-none');
    document.getElementById('calificacionesFiltrosCard')?.classList.add('d-none');
    document.getElementById('calificacionesTablaCard')?.classList.remove('d-none');
    document.getElementById('calificacionesTableTitle').textContent = 'Mis calificaciones';

    const thead = document.querySelector('#calificacionesTabla thead');
    if (thead) {
      thead.innerHTML = '<tr><th>Periodo</th><th>Asignatura</th><th>Calificación</th><th>Resultado</th></tr>';
    }

    const tbody = document.getElementById('calificacionesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm"></span> Cargando…</td></tr>';

    try {
      const r = await apiFetch('/alumnos/me/calificaciones', { method: 'GET' });
      const list = r.ok ? await r.json().catch(() => []) : [];
      const rows = Array.isArray(list) ? list : [];
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">Aún no hay calificaciones registradas.</td></tr>';
        return;
      }
      rows.sort((a, b) => String(a.periodo || '').localeCompare(String(b.periodo || ''), 'es'));
      tbody.innerHTML = rows.map(c => {
        const asig = c.asignatura ? (c.asignatura.nombre || c.asignatura.clave) : '—';
        const bloqueada = !!c.bloqueadaPorEvaluacion;
        const cal = bloqueada
          ? ('<span class="badge text-bg-warning">Bloqueada</span>' +
            '<div class="small text-muted mt-1">' + escapeHtml(c.mensajeBloqueo || 'Responde tu Evaluación Docente para ver tu calificación.') + '</div>' +
            '<a class="btn btn-sm btn-ide mt-2" href="evaluacion-docente.html">Ir a Evaluación Docente</a>')
          : (c.calificacionFinal != null ? String(c.calificacionFinal) : '—');
        const res = c.estatus ? String(c.estatus) : '—';
        const resHtml = res === 'APROBADO'
          ? '<span class="badge bg-success-subtle text-success">Aprobado</span>'
          : res === 'REPROBADO'
            ? '<span class="badge bg-danger-subtle text-danger">Reprobado</span>'
            : escapeHtml(res);
        return '<tr>' +
          '<td>' + escapeHtml(c.periodo || (c.periodoAcademico && c.periodoAcademico.codigo) || '—') + '</td>' +
          '<td>' + escapeHtml(asig || '—') + '</td>' +
          '<td>' + (bloqueada ? cal : escapeHtml(cal)) + '</td>' +
          '<td>' + resHtml + '</td>' +
          '</tr>';
      }).join('');
    } catch (_) {
      tbody.innerHTML = '<tr><td colspan="4" class="text-center text-danger py-4">No se pudieron cargar las calificaciones.</td></tr>';
    }
  }

  // -------------------- VISTA MAESTRO --------------------
  async function initVistaMaestro() {
    const desc = document.getElementById('calificacionesDesc');
    if (desc) {
      desc.textContent = 'Las calificaciones que capturas son preliminares (estado Capturada). Al enviar a revisión pasan a la Secretaría Académica; solo tras confirmar son oficiales (Aprobado/Reprobado) y aparecen al alumno en consultas.';
    }
    const foot = document.getElementById('calificacionesFooter');
    if (foot) {
      foot.textContent = 'La Secretaría Académica revisa, puede ajustar y confirma la calificación definitiva. Hasta entonces el alumno no ve esas calificaciones como oficiales.';
    }
    document.getElementById('calificacionesVistaMaestro')?.classList.remove('d-none');
    document.getElementById('calificacionesFiltrosCard')?.classList.add('d-none');
    document.getElementById('calificacionesTablaCard')?.classList.remove('d-none');
    document.getElementById('calificacionesTableTitle').textContent = 'Captura de calificaciones';

    const wrap = document.getElementById('calificacionesVistaMaestro');
    if (!wrap) return;
    wrap.innerHTML = `
      <div class="card mb-4">
        <div class="card-header bg-soft-primary">Selecciona una clase</div>
        <div class="card-body">
          <div class="row g-2 align-items-end">
            <div class="col-md-8">
              <label class="form-label">Clase</label>
              <select id="califMaestroClase" class="form-select">
                <option value="">Cargando…</option>
              </select>
            </div>
            <div class="col-md-4 d-flex gap-2">
              <button class="btn btn-outline-secondary w-100" id="btnMaestroGuardarTodo" disabled title="Guarda todas las calificaciones completas que hayas capturado (puedes dejar pendientes).">Guardar</button>
              <button class="btn btn-ide w-100" id="btnMaestroEnviarTodo" disabled title="Guarda (si hace falta) y envía a revisión todas las calificaciones completas capturadas de esta clase.">Enviar</button>
            </div>
          </div>
        </div>
      </div>
    `;

    const tbody = document.getElementById('calificacionesTableBody');
    const thead = document.querySelector('#calificacionesTabla thead');
    if (thead) {
      thead.innerHTML = '<tr><th>Matrícula</th><th>Alumno</th><th>Estado</th></tr>';
    }
    if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Selecciona una clase para comenzar.</td></tr>';

    const sel = document.getElementById('califMaestroClase');
    const btnGuardarTodo = document.getElementById('btnMaestroGuardarTodo');
    const btnEnviar = document.getElementById('btnMaestroEnviarTodo');

    // Cargar clases del maestro
    try {
      const r = await apiFetch('/maestros/me/clases', { method: 'GET' });
      const clases = r.ok ? await r.json().catch(() => []) : [];
      const list = Array.isArray(clases) ? clases : [];
      if (sel) {
        sel.innerHTML = '<option value="">Selecciona…</option>' + list.map(c => {
          const label = (c.grupoNombre || 'Grupo') + ' — ' + (c.asignaturaNombre || 'Asignatura') + (c.periodo ? (' (' + c.periodo + ')') : '');
          const val = encodeURIComponent(JSON.stringify({
            grupoId: c.grupoId,
            asignaturaId: c.asignaturaId,
            periodo: c.periodo || '',
            grupoNombre: c.grupoNombre || '',
            asignaturaNombre: c.asignaturaNombre || ''
          }));
          return '<option value="' + val + '">' + escapeHtml(label) + '</option>';
        }).join('');
      }
    } catch (e) {
      if (sel) sel.innerHTML = '<option value="">No se pudieron cargar clases</option>';
    }

    async function guardarClaseCompleta({ enviarLuego }) {
      if (!sel || !tbody) return;
      const raw = sel.value;
      if (!raw) return;
      const clase = JSON.parse(decodeURIComponent(raw));
      const filas = Array.from(tbody.querySelectorAll('tr[data-alumno-id]'));
      if (!filas.length) return;

      const bloqueadas = ['EN_REVISION', 'CONFIRMADA'];
      const guardarIds = [];
      try {
        for (const tr of filas) {
          const alumnoId = tr.getAttribute('data-alumno-id');
          const estado = (tr.dataset.estado || 'PENDIENTE').toUpperCase();
          if (bloqueadas.includes(estado)) continue;
          const calId = tr.dataset.calId || '';

          // Determinar si esta fila tiene captura completa
          let criteriosPayload = null;
          let calVal = null;
          let asisVal = null;

          const inpCrits = Array.from(tr.querySelectorAll('.inpCrit'));
          if (inpCrits.length) {
            criteriosPayload = [];
            let total = 0;
            for (const inp of inpCrits) {
              const cid = inp.getAttribute('data-criterio-id') || '';
              const max = Number(inp.getAttribute('max') || '0');
              const v = inp.value !== '' ? Number(inp.value) : NaN;
              if (Number.isNaN(v)) { criteriosPayload = null; break; } // incompleto -> saltar
              if (v < 0 || v > max) {
                if (typeof window.showSystemToast === 'function') {
                  window.showSystemToast('Hay un valor fuera de rango en un criterio (0–' + max + ').', { type: 'error', durationMs: 5200 });
                } else {
                  alert('Hay un valor fuera de rango en un criterio (0–' + max + ').');
                }
                inp.focus();
                return;
              }
              criteriosPayload.push({ criterioId: Number(cid), puntos: v });
              total += v;
            }
            if (!criteriosPayload) continue; // no completo, no guardamos
            calVal = calificacionFinalDesdePuntos(total);
            if (calVal == null) continue;
          } else {
            const inpCal = tr.querySelector('.inpCal');
            const rawCal = inpCal && inpCal.value !== '' ? parseFloat(inpCal.value) : null;
            if (rawCal == null) continue; // no capturada, no guardamos
            const pct = parsearCalificacionFinal(rawCal);
            if (pct == null || isNaN(pct) || pct < 0 || pct > 100) {
              if (typeof window.showSystemToast === 'function') {
                window.showSystemToast('Calificación inválida. Sin criterios usa un valor entre 0 y 100 (%).', { type: 'error', durationMs: 5200 });
              } else {
                alert('Hay una calificación inválida. Sin criterios, usa un valor entre 0 y 100 (%); el sistema lo convierte a escala de 10.');
              }
              inpCal?.focus();
              return;
            }
            // Guardamos en escala 0..10 (backend), pero el input se mantiene en % (0..100)
            calVal = calificacionFinalDesdePuntos(pct);
            asisVal = null;
          }

          // Guardar (parcial por alumno)
          const saved = await upsertCalificacion({
            calId: calId || null,
            alumnoId: parseInt(alumnoId, 10),
            asignaturaId: clase.asignaturaId,
            grupoId: clase.grupoId,
            periodo: clase.periodo || '',
            calificacionFinal: calVal,
            asistenciaPorcentaje: asisVal,
            idObservaciones: 100,
            criterios: criteriosPayload,
            capturaComoDocente: true
          });
          if (saved && saved.id) {
            tr.dataset.calId = String(saved.id);
            tr.dataset.estado = String(saved.estadoAprobacion || 'CAPTURADA');
            guardarIds.push(String(saved.id));
          }
        }

        if (enviarLuego) {
          // Validar: para enviar se requiere que TODOS los alumnos tengan captura completa (ya guardada o capturada en pantalla).
          for (const tr of filas) {
            const st = String(tr.dataset.estado || 'PENDIENTE').toUpperCase();
            if (bloqueadas.includes(st)) continue;
            const inpCrits = Array.from(tr.querySelectorAll('.inpCrit'));
            if (inpCrits.length) {
              // completo si todos tienen valor
              const completos = inpCrits.every(inp => inp.value !== '');
              if (!completos) {
                if (typeof window.showSystemToast === 'function') {
                  window.showSystemToast('Faltan calificaciones por capturar. Para enviar a revisión debes tener a todos los alumnos calificados.', { type: 'error', durationMs: 6200 });
                } else {
                  alert('Para enviar a revisión debes capturar las calificaciones de todos los alumnos (todos los criterios).');
                }
                return;
              }
            } else {
              const inpCal = tr.querySelector('.inpCal');
              if (!inpCal || inpCal.value === '') {
                if (typeof window.showSystemToast === 'function') {
                  window.showSystemToast('Faltan calificaciones por capturar. Para enviar a revisión debes tener a todos los alumnos calificados.', { type: 'error', durationMs: 6200 });
                } else {
                  alert('Para enviar a revisión debes capturar las calificaciones de todos los alumnos.');
                }
                return;
              }
            }
          }
          // Enviar todo lo capturado (incluye lo recién guardado)
          const idsEnviar = new Set();
          // 1) recién guardados
          guardarIds.forEach(id => idsEnviar.add(id));
          // 2) los que ya estaban CAPTURADA
          filas.forEach(tr => {
            const st = (tr.dataset.estado || '').toUpperCase();
            const id = tr.dataset.calId || '';
            if (id && st === 'CAPTURADA') idsEnviar.add(id);
          });
          for (const id of Array.from(idsEnviar)) {
            await enviarCalificacion(id);
          }
        }
      } catch (e) {
        mostrarErrorCopiable(enviarLuego ? 'Error al enviar calificaciones' : 'Error al guardar calificaciones', e);
      } finally {
        if (sel && sel.value) {
          try {
            await renderClase();
          } catch (_) { /* ignorar */ }
        }
      }
    }

    async function renderClase() {
      if (!sel || !tbody) return;
      const raw = sel.value;
      if (!raw) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Selecciona una clase.</td></tr>';
        if (btnEnviar) btnEnviar.disabled = true;
        if (btnGuardarTodo) btnGuardarTodo.disabled = true;
        return;
      }
      const clase = JSON.parse(decodeURIComponent(raw));
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm"></span> Cargando…</td></tr>';
      try {
        const grupo = await cargarGrupo(clase.grupoId, true);
        const alumnos = Array.isArray(grupo.alumnos) ? grupo.alumnos.slice() : [];
        alumnos.sort((a, b) => nombreCompletoAlumno(a).localeCompare(nombreCompletoAlumno(b), 'es'));

        const criterios = await cargarCriteriosMaestro(clase.grupoId, clase.asignaturaId);
        const criteriosList = (criterios || []).slice().sort((a, b) => String(a.nombre || '').localeCompare(String(b.nombre || ''), 'es'));
        const suma = criteriosList.reduce((acc, c) => acc + (c && c.porcentaje != null ? Number(c.porcentaje) : 0), 0);
        const criteriosOk = criteriosList.length > 0 && suma === 100;

        const cals = await cargarCalificacionesGrupoAsignatura(clase.grupoId, clase.asignaturaId, true);
        const byAlumno = indexByAlumnoId(cals);

        // Encabezado dinámico por criterios
        if (thead) {
          if (!criteriosList.length) {
            thead.innerHTML = '<tr><th>Matrícula</th><th>Alumno</th><th class="text-nowrap">Calificación (0–100%)</th><th class="text-nowrap">Final</th><th>Estado</th></tr>';
          } else {
            const thCrit = criteriosList.map(c => {
              const max = c && c.porcentaje != null ? Number(c.porcentaje) : 0;
              const nm = toTitleCaseEs(c && c.nombre ? c.nombre : 'Criterio');
              return '<th class="text-nowrap" title="' + escapeHtml(nm + ' (máx ' + max + ')') + '">' + escapeHtml(nm) + '<div class="small text-muted">0–' + escapeHtml(String(max)) + '</div></th>';
            }).join('');
            thead.innerHTML = '<tr><th>Matrícula</th><th>Alumno</th>' + thCrit + '<th class="text-nowrap">Final</th><th>Estado</th></tr>';
          }
        }

        const html = alumnos.map(a => {
          const cal = byAlumno[String(a.id)] || null;
          const estado = cal ? (cal.estadoAprobacion || 'PENDIENTE') : 'PENDIENTE';
          const locked = ['EN_REVISION', 'CONFIRMADA'].includes(String(estado || '').toUpperCase());
          const valFinal10 = cal && cal.calificacionFinal != null ? String(cal.calificacionFinal) : '';
          const valPctSimple = cal && cal.calificacionFinal != null ? porcentajeUiDesdeCalificacionFinal10(cal.calificacionFinal) : '';
          const asis = cal && cal.asistenciaPorcentaje != null ? String(Math.round(Number(cal.asistenciaPorcentaje))) : '';
          // Prefill por criterios si existen
          const items = (cal && (cal.criterios || cal.criteriosItems)) ? (cal.criterios || cal.criteriosItems) : [];
          const byCrit = {};
          (items || []).forEach(it => {
            const cid = it && it.criterio && it.criterio.id != null ? String(it.criterio.id) : (it && it.criterioId != null ? String(it.criterioId) : '');
            if (cid) byCrit[cid] = it.puntos != null ? Number(it.puntos) : null;
          });

          if (!criteriosList.length) {
            const calIdAttr = cal && cal.id != null ? String(cal.id) : '';
            const finalDisplay = valFinal10 ? formatoCalificacionDisplay(parseFloat(valFinal10)) : '';
            return '<tr data-alumno-id="' + a.id + '" data-cal-id="' + escapeHtml(calIdAttr) + '" data-estado="' + escapeHtml(String(estado)) + '">' +
              '<td>' + escapeHtml(a.matricula || '—') + '</td>' +
              '<td>' + escapeHtml(nombreCompletoAlumno(a)) + '</td>' +
              '<td><input class="form-control form-control-sm inpCal" type="number" min="0" max="100" step="any" inputmode="decimal" placeholder="0–100" value="' + escapeHtml(valPctSimple) + '" ' + (locked ? 'disabled' : '') + ' title="Porcentaje 0–100; se guarda en escala de 10"></td>' +
              '<td class="text-nowrap"><span class="fw-semibold calFinalSpan">' + escapeHtml(finalDisplay || '—') + '</span></td>' +
              '<td>' + badgeEstado(estado) + '</td>' +
              '</tr>';
          }

          const critTds = criteriosList.map(c => {
            const cid = c && c.id != null ? String(c.id) : '';
            const max = c && c.porcentaje != null ? Number(c.porcentaje) : 0;
            const pv = cid && byCrit[cid] != null ? String(byCrit[cid]) : '';
            return '<td style="min-width: 140px;">' +
              '<div class="input-group input-group-sm">' +
              '<input class="form-control inpCrit" type="number" min="0" max="' + escapeHtml(String(max)) + '" step="any" inputmode="decimal" data-criterio-id="' + escapeHtml(cid) + '" value="' + escapeHtml(pv) + '" ' + (locked ? 'disabled' : '') + '>' +
              '<span class="input-group-text text-muted">/' + escapeHtml(String(max)) + '</span>' +
              '</div>' +
              '</td>';
          }).join('');

          const finalDisplay = valFinal10 ? formatoCalificacionDisplay(parseFloat(valFinal10)) : '';
          const calIdCrit = cal && cal.id != null ? String(cal.id) : '';
          return '<tr data-alumno-id="' + a.id + '" data-cal-id="' + escapeHtml(calIdCrit) + '" data-estado="' + escapeHtml(String(estado)) + '">' +
            '<td>' + escapeHtml(a.matricula || '—') + '</td>' +
            '<td>' + escapeHtml(nombreCompletoAlumno(a)) + '</td>' +
            critTds +
            '<td class="text-nowrap"><span class="fw-semibold calFinalSpan">' + escapeHtml(finalDisplay || '—') + '</span></td>' +
            '<td>' + badgeEstado(estado) + '</td>' +
            '</tr>';
        }).join('');

        if (criteriosList.length && !criteriosOk) {
          // Permitir consultar lo ya capturado, pero bloquear guardado.
          // (Si no hay nada capturado, el mensaje guía al docente.)
          const tieneAlgo = (cals || []).some(c => c && c.calificacionFinal != null);
          if (!tieneAlgo) {
            tbody.innerHTML = '<tr><td colspan="99" class="text-center text-muted py-4">Configura criterios para esta clase (deben sumar 100%) para poder capturar calificaciones.</td></tr>';
            if (btnEnviar) btnEnviar.disabled = true;
            return;
          }
        }

        tbody.innerHTML = html || '<tr><td colspan="99" class="text-center text-muted py-4">El grupo no tiene alumnos.</td></tr>';
        vincularPasoCalificacionFlechas05(tbody);

        // Cálculo en vivo (criterios -> final 0..10)
        if (criteriosList.length) {
          tbody.querySelectorAll('tr[data-alumno-id]').forEach(function (tr) {
            const span = tr.querySelector('.calFinalSpan');
            if (!span) return;
            function recalcular() {
              let total = 0;
              let filled = 0;
              tr.querySelectorAll('.inpCrit').forEach(function (inp) {
                const max = Number(inp.getAttribute('max') || '0');
                let v = inp.value !== '' ? Number(inp.value) : NaN;
                if (!Number.isNaN(v)) {
                  // Clamp en UI: no permitir sumar más de lo permitido por criterio
                  if (!Number.isNaN(max) && max >= 0) {
                    if (v > max) v = max;
                    if (v < 0) v = 0;
                  }
                  total += v;
                  filled++;
                }
              });
              if (filled === criteriosList.length) {
                const final = calificacionFinalDesdePuntos(total);
                span.textContent = final != null ? formatoCalificacionDisplay(final) : '—';
              } else {
                span.textContent = '—';
              }
            }
            tr.querySelectorAll('.inpCrit').forEach(function (inp) {
              // Clamp inmediato al capturar (evita 100/40)
              inp.addEventListener('input', function () {
                const max = Number(inp.getAttribute('max') || '0');
                let v = inp.value !== '' ? Number(inp.value) : NaN;
                if (!Number.isNaN(v)) {
                  if (!Number.isNaN(max) && max >= 0 && v > max) inp.value = String(max);
                  if (v < 0) inp.value = '0';
                }
              });
              inp.addEventListener('input', recalcular);
            });
            recalcular();
          });
        } else {
          // Sin criterios: % (0..100) -> final (0..10)
          tbody.querySelectorAll('tr[data-alumno-id]').forEach(function (tr) {
            const inp = tr.querySelector('.inpCal');
            const span = tr.querySelector('.calFinalSpan');
            if (!inp || !span) return;
            function recalcular() {
              const raw = (inp.value !== '' && inp.value !== '.') ? Number(inp.value) : NaN;
              if (Number.isNaN(raw)) { span.textContent = '—'; return; }
              const clamped = Math.max(0, Math.min(100, raw));
              const final = calificacionFinalDesdePuntos(clamped);
              span.textContent = final != null ? formatoCalificacionDisplay(final) : '—';
            }
            inp.addEventListener('input', function () {
              const raw = (inp.value !== '' && inp.value !== '.') ? Number(inp.value) : NaN;
              if (!Number.isNaN(raw)) {
                if (raw > 100) inp.value = '100';
                if (raw < 0) inp.value = '0';
              }
              recalcular();
            });
            recalcular();
          });
        }

        // Botones:
        // - Guardar: activo si hay al menos una fila editable (no EN_REVISION/CONFIRMADA).
        // - Enviar: NO se bloquea por estado CAPTURADA; valida al hacer click si falta capturar alguien.
        const hayFilaEditable = alumnos.some(function (a) {
          const cal = byAlumno[String(a.id)];
          const st = cal ? String(cal.estadoAprobacion || 'PENDIENTE').toUpperCase() : 'PENDIENTE';
          return st !== 'EN_REVISION' && st !== 'CONFIRMADA';
        });
        if (btnGuardarTodo) btnGuardarTodo.disabled = !hayFilaEditable;
        if (btnEnviar) btnEnviar.disabled = !hayFilaEditable;

        // Sin acciones por alumno: solo botones globales Guardar/Enviar.

        if (btnEnviar) {
          btnEnviar.onclick = async function () {
            const msg = 'Se enviarán las calificaciones de esta clase a revisión. Después de enviar, ya no podrás editarlas.';
            if (typeof window.uiConfirm === 'function') {
              const ok = await window.uiConfirm(msg, {
                title: 'Enviar calificaciones a revisión',
                subtitle: 'Requisito: todos los alumnos deben tener calificación capturada.',
                okText: 'Enviar',
                cancelText: 'Cancelar'
              });
              if (!ok) return;
            } else {
              if (!confirm(msg)) return;
            }
            await guardarClaseCompleta({ enviarLuego: true });
          };
        }
        if (btnGuardarTodo) {
          btnGuardarTodo.onclick = async function () {
            await guardarClaseCompleta({ enviarLuego: false });
          };
        }
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-4">' + escapeHtml(e.message || 'Error') + '</td></tr>';
      }
    }

    // Cargar automáticamente al seleccionar clase
    if (sel) {
      sel.addEventListener('change', function () {
        renderClase();
      });
    }
  }

  // -------------------- VISTA SECRETARÍA --------------------
  async function cargarProgramasParaFiltro() {
    const r = await apiFetch('/programas-educativos', { method: 'GET' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('No se pudieron cargar programas (' + r.status + ').'));
    }
    const json = await r.json().catch(() => []);
    programasParaFiltro = Array.isArray(json) ? json : [];
    return programasParaFiltro;
  }
  async function cargarCiclosEscolares(programaId, tipoPeriodoFallback) {
    const pid = (programaId != null ? String(programaId) : '').trim();
    const tipo = (tipoPeriodoFallback != null ? String(tipoPeriodoFallback) : '').trim().toUpperCase();
    const key = pid ? ('PROGRAMA_' + pid) : (tipo || 'SEMESTRE');
    if (ciclosEscolaresPorTipo[key] && Array.isArray(ciclosEscolaresPorTipo[key])) {
      ciclosEscolares = ciclosEscolaresPorTipo[key];
      return ciclosEscolares;
    }
    const url = pid
      ? ('/periodos-academicos?programaId=' + encodeURIComponent(pid))
      : ('/periodos-academicos?tipoPeriodo=' + encodeURIComponent(tipo || 'SEMESTRE'));
    const r = await apiFetch(url, { method: 'GET' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('No se pudieron cargar periodos (' + r.status + ').'));
    }
    const json = await r.json().catch(() => []);
    ciclosEscolares = Array.isArray(json) ? json : [];
    ciclosEscolaresPorTipo[key] = ciclosEscolares;
    return ciclosEscolares;
  }
  async function cargarAsignaturas(programaId) {
    if (!programaId) return [];
    const r = await apiFetch('/asignaturas?programaId=' + encodeURIComponent(programaId), { method: 'GET' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('No se pudieron cargar asignaturas (' + r.status + ').'));
    }
    const json = await r.json().catch(() => []);
    asignaturasPorPrograma[programaId] = Array.isArray(json) ? json : [];
    return asignaturasPorPrograma[programaId];
  }
  async function cargarGrupos(programaId, periodoAcademicoId) {
    if (!programaId) return [];
    const qs = new URLSearchParams();
    qs.set('programaId', String(programaId));
    if (periodoAcademicoId) qs.set('periodoAcademicoId', String(periodoAcademicoId));
    const r = await apiFetch('/grupos?' + qs.toString(), { method: 'GET' });
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      throw new Error(t || ('No se pudieron cargar grupos (' + r.status + ').'));
    }
    const json = await r.json().catch(() => []);
    gruposPorPrograma[programaId] = Array.isArray(json) ? json : [];
    return gruposPorPrograma[programaId];
  }

  function llenarSelectPrograma() {
    const sel = document.getElementById('calificacionesFiltroPrograma');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona programa...</option>';
    programasParaFiltro.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.nombre || p.clave || 'Programa';
      sel.appendChild(opt);
    });
  }
  function llenarSelectCiclo() {
    const sel = document.getElementById('calificacionesFiltroCiclo');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona ciclo...</option>';
    ciclosEscolares.forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.nombre || c.codigo || '';
      opt.textContent = c.nombre || c.codigo || '';
      sel.appendChild(opt);
    });
  }

  function llenarSelectPeriodo() {
    const sel = document.getElementById('calificacionesFiltroPeriodo');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona periodo...</option>';
    ciclosEscolares.forEach(c => {
      const codigo = c.codigo || c.nombre || '';
      const opt = document.createElement('option');
      opt.value = codigo;
      opt.textContent = codigo;
      sel.appendChild(opt);
    });
  }

  function optionsPeriodos(selectedCodigo) {
    const def = selectedCodigo != null ? String(selectedCodigo) : '';
    const base = '<option value="">Selecciona…</option>';
    const opts = ciclosEscolares.map(function (c) {
      const codigo = c.codigo || c.nombre || '';
      const sel = def && String(codigo) === def ? ' selected' : '';
      return '<option value="' + escapeHtml(codigo) + '"' + sel + '>' + escapeHtml(codigo) + '</option>';
    }).join('');
    return base + opts;
  }

  /** Orden comparable para periodos tipo 2025-2 (año * 100 + número). */
  function ordenPeriodoDesdeCat(pa) {
    if (!pa || typeof pa !== 'object') return null;
    const anio = pa.anio != null ? Number(pa.anio) : NaN;
    const num = pa.numero != null ? Number(pa.numero) : NaN;
    if (!Number.isNaN(anio) && !Number.isNaN(num)) return anio * 100 + num;
    const cod = pa.codigo || pa.nombre || '';
    const m = String(cod).trim().match(/^(\d{4})\s*-\s*(\d+)/);
    if (m) return parseInt(m[1], 10) * 100 + parseInt(m[2], 10);
    return null;
  }

  function ordenPeriodoIngresoAlumno(alumno) {
    if (!alumno) return null;
    if (alumno.periodoAcademico) return ordenPeriodoDesdeCat(alumno.periodoAcademico);
    const pi = alumno.periodoIngreso;
    if (pi) return ordenPeriodoDesdeCat({ codigo: pi });
    return null;
  }

  function periodosPorAnioDesdeTipo(tipoPeriodo) {
    const t = String(tipoPeriodo || '').toUpperCase();
    if (t.includes('SEMAN')) return 2;
    if (t.includes('CUATRIM')) return 3;
    if (t.includes('TETRA')) return 3;
    if (t.includes('TRIM')) return 4;
    if (t.includes('SEM')) return 2;
    return 2;
  }

  function idxPeriodoSecuencial(pa, periodosPorAnio) {
    if (!pa) return null;
    const anio = pa.anio != null ? Number(pa.anio) : NaN;
    const num = pa.numero != null ? Number(pa.numero) : NaN;
    if (!Number.isNaN(anio) && !Number.isNaN(num) && periodosPorAnio) {
      return anio * periodosPorAnio + (num - 1);
    }
    const cod = pa.codigo || pa.nombre || '';
    const m = String(cod).trim().match(/^(\d{4})\s*-\s*(\d+)/);
    if (m && periodosPorAnio) {
      return parseInt(m[1], 10) * periodosPorAnio + (parseInt(m[2], 10) - 1);
    }
    return null;
  }

  function periodoPlanDesdeIngreso(alumno, codigoPeriodoSeleccionado) {
    const ingresoOrd = ordenPeriodoIngresoAlumno(alumno);
    if (ingresoOrd == null) return null;

    const ingresoPa = alumno && alumno.periodoAcademico ? alumno.periodoAcademico : { codigo: alumno.periodoIngreso };
    const selPa = (ciclosEscolares || []).find(function (c) {
      const codigo = c.codigo || c.nombre || '';
      return String(codigo) === String(codigoPeriodoSeleccionado);
    }) || { codigo: codigoPeriodoSeleccionado };

    const tipo = (selPa && selPa.tipoPeriodo) || (ingresoPa && ingresoPa.tipoPeriodo) || '';
    const ppa = periodosPorAnioDesdeTipo(tipo);
    const idxIng = idxPeriodoSecuencial(ingresoPa, ppa);
    const idxSel = idxPeriodoSecuencial(selPa, ppa);
    if (idxIng == null || idxSel == null) return null;
    const diff = idxSel - idxIng;
    return diff >= 0 ? (diff + 1) : null;
  }

  /**
   * Llena el selector rápido junto al nombre del alumno: solo periodos del catálogo
   * desde el periodo de ingreso del alumno en adelante (incluido).
   */
  function llenarSelectPeriodoQuickParaAlumno(alumno, codigoPreferido) {
    const sel = document.getElementById('califSecPeriodoQuick');
    if (!sel) return '';
    const ingresoOrd = ordenPeriodoIngresoAlumno(alumno);
    let lista = (ciclosEscolares || []).slice().filter(function (c) {
      const o = ordenPeriodoDesdeCat(c);
      if (ingresoOrd == null) return true;
      if (o == null) return true;
      return o >= ingresoOrd;
    });
    lista.sort(function (a, b) {
      const oa = ordenPeriodoDesdeCat(a) || 0;
      const ob = ordenPeriodoDesdeCat(b) || 0;
      return oa - ob;
    });
    let html = '<option value="">Selecciona…</option>';
    lista.forEach(function (c) {
      const codigo = c.codigo || c.nombre || '';
      if (!codigo) return;
      html += '<option value="' + escapeHtml(codigo) + '">' + escapeHtml(codigo) + '</option>';
    });
    sel.innerHTML = html;
    let pref = codigoPreferido != null ? String(codigoPreferido).trim() : '';
    if (pref && ingresoOrd != null) {
      const po = ordenPeriodoDesdeCat({ codigo: pref });
      if (po != null && po < ingresoOrd) pref = '';
    }
    if (pref && Array.prototype.some.call(sel.options, function (o) { return o.value === pref; })) {
      sel.value = pref;
    }
    if (!sel.value && lista.length > 0) {
      const firstCod = lista[0].codigo || lista[0].nombre || '';
      if (firstCod) sel.value = firstCod;
    }
    return String(sel.value || '').trim();
  }
  function llenarSelectAsignatura(lista) {
    const sel = document.getElementById('calificacionesFiltroAsignatura');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona asignatura...</option>';
    (lista || []).forEach(a => {
      const opt = document.createElement('option');
      opt.value = a.id;
      opt.textContent = a.nombre || a.clave || 'Asignatura';
      sel.appendChild(opt);
    });
  }
  function llenarSelectGrupo(lista) {
    const sel = document.getElementById('calificacionesFiltroGrupo');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona grupo...</option>';
    (lista || []).forEach(g => {
      const opt = document.createElement('option');
      opt.value = g.id;
      opt.textContent = (g.nombre || '—');
      sel.appendChild(opt);
    });
  }

  function setHeaderSecretaria() {
    const thead = document.querySelector('#calificacionesTabla thead');
    if (thead) {
      thead.innerHTML = '<tr><th>Matrícula</th><th>Alumno</th><th>Periodo</th><th>Calif.</th><th>Asistencia %</th><th>Observación</th><th>Estado</th><th>Acciones</th></tr>';
    }
  }

  function leerFiltrosSecretaria() {
    const selPrograma = document.getElementById('calificacionesFiltroPrograma');
    const selAsignatura = document.getElementById('calificacionesFiltroAsignatura');
    const selGrupo = document.getElementById('calificacionesFiltroGrupo');
    const selPeriodo = document.getElementById('calificacionesFiltroPeriodo');
    const selCiclo = document.getElementById('calificacionesFiltroCiclo');
    return {
      programaId: selPrograma ? selPrograma.value : '',
      asignaturaId: selAsignatura ? selAsignatura.value : '',
      grupoId: selGrupo ? selGrupo.value : '',
      periodo: selPeriodo ? selPeriodo.value : '',
      ciclo: selCiclo ? selCiclo.value : ''
    };
  }

  async function initVistaSecretaria() {
    const desc = document.getElementById('calificacionesDesc');
    if (desc) {
      desc.textContent = 'Elige grupo y alumno inscrito; luego revisa por materia (horario/docente) y confirma calificaciones en revisión (UI admin v2026-05-01-7).';
    }
    const foot = document.getElementById('calificacionesFooter');
    if (foot) {
      foot.textContent = 'Los docentes capturan y envían a revisión por materia. Tras elegir al alumno, ves sus calificaciones en cada materia del grupo y confirmas cuando corresponda.';
    }

    document.getElementById('calificacionesVistaSecretaria')?.classList.remove('d-none');
    document.getElementById('calificacionesFiltrosCard')?.classList.add('d-none');
    document.getElementById('calificacionesTablaCard')?.classList.add('d-none');

    const wrap = document.getElementById('calificacionesVistaSecretaria');
    if (!wrap) return;
    wrap.innerHTML = `
      <div class="card mb-4">
        <div class="card-header bg-soft-primary">Selecciona un grupo</div>
        <div class="card-body">
          <div class="row g-2 align-items-end">
            <div class="col-md-8">
              <label class="form-label">Grupo</label>
              <select id="califAdminClase" class="form-select">
                <option value="">Cargando…</option>
              </select>
            </div>
            <div class="col-md-4 d-flex gap-2">
              <button class="btn btn-outline-secondary w-100" id="btnAdminGuardarTodo" disabled style="visibility:hidden" tabindex="-1" aria-hidden="true">Guardar</button>
              <button class="btn btn-ide w-100" id="btnAdminEnviarTodo" disabled style="visibility:hidden" tabindex="-1" aria-hidden="true">Enviar</button>
            </div>
          </div>
          <div class="text-muted small mt-2">
            Se listan <strong>grupos activos con alumnos</strong> y horario de clase. Después elige <strong>un alumno inscrito</strong> en la tabla siguiente; entonces se cargan sus materias y calificaciones.
          </div>
        </div>
      </div>
      <div id="califAdminAlumnosWrap" class="card mb-4 d-none border shadow-sm">
        <div class="card-header bg-soft-primary d-flex flex-wrap align-items-center justify-content-between gap-2">
          <span class="fw-semibold">Alumnos inscritos en el grupo</span>
          <span class="small text-muted fw-normal text-end">Selecciona un alumno para ver y confirmar sus calificaciones en cada materia.</span>
        </div>
        <div class="card-body p-0">
          <div class="table-responsive border-top calif-admin-alumnos-scroll">
            <table class="table table-hover align-middle mb-0 table-sm calif-admin-alumnos-table">
              <thead class="table-light sticky-top">
                <tr>
                  <th scope="col" class="text-center" style="width:3rem">Elegir</th>
                  <th scope="col">Matrícula</th>
                  <th scope="col">Nombre</th>
                </tr>
              </thead>
              <tbody id="califAdminAlumnosBody">
                <tr><td colspan="3" class="text-center text-muted py-4">Selecciona un grupo arriba.</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div id="califAdminMateriasContainer"></div>
    `;

    const sel = document.getElementById('califAdminClase');
    const materiasContainer = document.getElementById('califAdminMateriasContainer');
    const alumnoWrap = document.getElementById('califAdminAlumnosWrap');
    const alumnoBody = document.getElementById('califAdminAlumnosBody');

    function obtenerAlumnoAdminSeleccionadoId() {
      const r = document.querySelector('input[name="califAdminAlumno"]:checked');
      return r && r.value ? String(r.value) : '';
    }

    function sincronizarSeleccionAlumnoAdmin() {
      if (!alumnoBody) return;
      alumnoBody.querySelectorAll('.calif-admin-alumno-fila').forEach(function (fila) {
        const rad = fila.querySelector('.calif-admin-alumno-radio');
        const activa = !!(rad && rad.checked);
        fila.classList.toggle('is-selected', activa);
        fila.setAttribute('aria-selected', activa ? 'true' : 'false');
      });
    }

    async function onCambioGrupoAdmin() {
      if (!sel || !materiasContainer) return;
      const raw = sel.value;
      if (!raw) {
        if (alumnoWrap) alumnoWrap.classList.add('d-none');
        if (alumnoBody) alumnoBody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">Selecciona un grupo arriba.</td></tr>';
        materiasContainer.innerHTML = '';
        return;
      }
      const meta = JSON.parse(decodeURIComponent(raw));
      const grupoId = meta.grupoId;
      if (alumnoBody) {
        alumnoBody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm me-2"></span>Cargando alumnos…</td></tr>';
      }
      if (alumnoWrap) alumnoWrap.classList.remove('d-none');
      materiasContainer.innerHTML = '<p class="text-muted small mb-0 py-3 px-2 border rounded bg-light">Selecciona un <strong>alumno</strong> en la tabla superior para cargar las materias y calificaciones.</p>';
      try {
        const grupo = await cargarGrupo(grupoId, false);
        const alumnos = Array.isArray(grupo.alumnos) ? grupo.alumnos.slice() : [];
        alumnos.sort((a, b) => nombreCompletoAlumno(a).localeCompare(nombreCompletoAlumno(b), 'es'));
        if (!alumnos.length) {
          if (alumnoBody) {
            alumnoBody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">Este grupo no tiene alumnos inscritos.</td></tr>';
          }
          materiasContainer.innerHTML = '<div class="alert alert-secondary mb-0">No hay alumnos en este grupo.</div>';
          return;
        }
        if (alumnoBody) {
          alumnoBody.innerHTML = alumnos.map(a => {
            return '<tr class="calif-admin-alumno-fila" data-alumno-id="' + a.id + '" role="button" tabindex="0" aria-selected="false">' +
              '<td class="text-center"><input type="radio" name="califAdminAlumno" class="form-check-input calif-admin-alumno-radio" value="' + a.id + '" aria-label="Elegir ' + escapeHtml(nombreCompletoAlumno(a)) + '"></td>' +
              '<td>' + escapeHtml(a.matricula || '—') + '</td>' +
              '<td>' + escapeHtml(nombreCompletoAlumno(a)) + '</td>' +
              '</tr>';
          }).join('');
          alumnoBody.querySelectorAll('.calif-admin-alumno-fila').forEach(function (fila) {
            fila.addEventListener('click', function (ev) {
              if (ev.target && ev.target.classList && ev.target.classList.contains('calif-admin-alumno-radio')) return;
              const rad = fila.querySelector('.calif-admin-alumno-radio');
              if (rad) {
                rad.checked = true;
                rad.dispatchEvent(new Event('change', { bubbles: true }));
              }
            });
            fila.addEventListener('keydown', function (ev) {
              if (ev.key === 'Enter' || ev.key === ' ') {
                ev.preventDefault();
                fila.click();
              }
            });
          });
          sincronizarSeleccionAlumnoAdmin();
        }
      } catch (e) {
        if (alumnoBody) {
          alumnoBody.innerHTML = '<tr><td colspan="3" class="text-center text-danger py-4">' + escapeHtml(e.message || 'Error al cargar alumnos') + '</td></tr>';
        }
      }
    }

    async function cargarCriteriosClaseAdmin(grupoId, asignaturaId) {
      if (!grupoId || !asignaturaId) return [];
      const r = await apiFetch('/criterios-evaluacion?grupoId=' + encodeURIComponent(String(grupoId)) + '&asignaturaId=' + encodeURIComponent(String(asignaturaId)), { method: 'GET' });
      if (!r || !r.ok) return [];
      const json = await r.json().catch(() => []);
      return Array.isArray(json) ? json : [];
    }

    function construirPayloadFilaAdmin(tr, claseMeta, criteriosList) {
      const alumnoId = Number(tr && tr.getAttribute ? (tr.getAttribute('data-alumno-id') || '') : '');
      if (!alumnoId || !claseMeta || !claseMeta.grupoId || !claseMeta.asignaturaId) {
        throw new Error('No se pudo identificar correctamente la clase o el alumno.');
      }
      if (criteriosList.length) {
        const criterios = [];
        let total = 0;
        for (const criterio of criteriosList) {
          const cid = criterio && criterio.id != null ? String(criterio.id) : '';
          const max = criterio && criterio.porcentaje != null ? Number(criterio.porcentaje) : 0;
          const inp = tr.querySelector('.inpCrit[data-criterio-id="' + cid + '"]');
          const val = inp && inp.value !== '' ? Number(inp.value) : NaN;
          if (Number.isNaN(val)) {
            throw new Error('Debes capturar todos los criterios antes de guardar o confirmar.');
          }
          if (val < 0 || val > max) {
            throw new Error('Los puntos de "' + (criterio.nombre || 'criterio') + '" deben estar entre 0 y ' + max + '.');
          }
          criterios.push({ criterioId: Number(cid), puntos: val });
          total += val;
        }
        const final = calificacionFinalDesdePuntos(total);
        if (final == null) {
          throw new Error('No se pudo calcular la calificación final desde los criterios.');
        }
        return {
          alumnoId: alumnoId,
          asignaturaId: Number(claseMeta.asignaturaId),
          grupoId: Number(claseMeta.grupoId),
          periodo: claseMeta.periodo || '',
          calificacionFinal: final,
          asistenciaPorcentaje: null,
          idObservaciones: 100,
          criterios: criterios
        };
      }

      const inpFinal = tr.querySelector('.inpAdminCalFinal');
      const rawFinal = inpFinal && inpFinal.value !== '' && inpFinal.value !== '.' ? Number(inpFinal.value) : NaN;
      if (Number.isNaN(rawFinal)) {
        throw new Error('Captura la calificación final antes de guardar o confirmar.');
      }
      const final = parsearCalificacionFinal(rawFinal);
      if (final == null || Number.isNaN(final) || final < 5 || final > 10) {
        throw new Error('La calificación final debe quedar entre 5.00 y 10.00.');
      }
      return {
        alumnoId: alumnoId,
        asignaturaId: Number(claseMeta.asignaturaId),
        grupoId: Number(claseMeta.grupoId),
        periodo: claseMeta.periodo || '',
        calificacionFinal: final,
        asistenciaPorcentaje: null,
        idObservaciones: 100,
        criterios: []
      };
    }

    async function guardarFilaAdmin(tr, claseMeta, criteriosList) {
      const payload = construirPayloadFilaAdmin(tr, claseMeta, criteriosList);
      const calId = tr && tr.dataset ? (tr.dataset.calId || '') : '';
      const estado = String(tr && tr.dataset ? (tr.dataset.estado || 'PENDIENTE') : 'PENDIENTE').toUpperCase();
      if (calId && estado === 'EN_REVISION') {
        return await editarCalificacionEnRevision(calId, payload);
      }
      return await upsertCalificacion({
        calId: calId || null,
        alumnoId: payload.alumnoId,
        asignaturaId: payload.asignaturaId,
        grupoId: payload.grupoId,
        periodo: payload.periodo,
        calificacionFinal: payload.calificacionFinal,
        asistenciaPorcentaje: payload.asistenciaPorcentaje,
        idObservaciones: payload.idObservaciones,
        criterios: payload.criterios
      });
    }

    /**
     * Arma thead + tbody de una materia (misma lógica que la vista admin de una sola tabla).
     * @returns {{ theadTr: string, tbodyInner: string, criteriosList: Array, bindRecalc: boolean }}
     */
    async function armarTablaAdminUnaMateria(grupoId, asignaturaId, alumnos, claseMeta) {
      const criterios = await cargarCriteriosClaseAdmin(grupoId, asignaturaId);
      const criteriosList = (criterios || []).slice().sort((a, b) => String(a.nombre || '').localeCompare(String(b.nombre || ''), 'es'));
      const suma = criteriosList.reduce((acc, c) => acc + (c && c.porcentaje != null ? Number(c.porcentaje) : 0), 0);
      const criteriosOk = criteriosList.length > 0 && suma === 100;

      const cals = await cargarCalificacionesGrupoAsignatura(grupoId, asignaturaId, false);
      const byAlumno = indexByAlumnoId(cals);

      let theadTr;
      if (!criteriosList.length) {
        theadTr = '<tr><th>Matrícula</th><th>Alumno</th><th class="text-nowrap">Calificación final</th><th>Estado</th><th class="text-end text-nowrap">Acciones</th></tr>';
      } else {
        const thCrit = criteriosList.map(c => {
          const max = c && c.porcentaje != null ? Number(c.porcentaje) : 0;
          const nm = toTitleCaseEs(c && c.nombre ? c.nombre : 'Criterio');
          return '<th class="text-nowrap" title="' + escapeHtml(nm + ' (máx ' + max + ')') + '">' + escapeHtml(nm) + '<div class="small text-muted">0–' + escapeHtml(String(max)) + '</div></th>';
        }).join('');
        theadTr = '<tr><th>Matrícula</th><th>Alumno</th>' + thCrit + '<th class="text-nowrap">Calificación final</th><th>Estado</th><th class="text-end text-nowrap">Acciones</th></tr>';
      }

      if (criteriosList.length && !criteriosOk) {
        const tieneAlgo = (cals || []).some(c => c && c.calificacionFinal != null);
        if (!tieneAlgo) {
          return {
            theadTr: theadTr,
            tbodyInner: '<tr><td colspan="99" class="text-center text-muted py-4">Esta clase aún no tiene criterios válidos (deben sumar 100%) o no hay captura.</td></tr>',
            criteriosList: criteriosList,
            bindRecalc: false
          };
        }
      }

      const html = alumnos.map(a => {
        const cal = byAlumno[String(a.id)] || null;
        const estado = cal ? (cal.estadoAprobacion || 'PENDIENTE') : 'PENDIENTE';
        const st = String(estado || 'PENDIENTE').toUpperCase();
        const calId = cal && cal.id != null ? String(cal.id) : '';
        const val = cal && cal.calificacionFinal != null ? formatoCalificacionDisplay(parseFloat(cal.calificacionFinal)) : '';
        const editable = st !== 'CONFIRMADA';

        const items = (cal && (cal.criterios || cal.criteriosItems)) ? (cal.criterios || cal.criteriosItems) : [];
        const byCrit = {};
        (items || []).forEach(it => {
          const cid = it && it.criterio && it.criterio.id != null ? String(it.criterio.id) : (it && it.criterioId != null ? String(it.criterioId) : '');
          if (cid) byCrit[cid] = it.puntos != null ? Number(it.puntos) : null;
        });

        const acciones = '<div class="d-inline-flex gap-2">' +
          '<button type="button" class="btn btn-outline-secondary btn-sm btnAdminGuardar"' + (editable ? '' : ' disabled') + '><i class="bi bi-save me-1"></i>Guardar</button>' +
          '<button type="button" class="btn btn-ide btn-sm btnAdminConfirmar"' + (editable ? '' : ' disabled') + '><i class="bi bi-check2-circle me-1"></i>Confirmar</button>' +
          '</div>';

        if (!criteriosList.length) {
          return '<tr data-alumno-id="' + a.id + '" data-cal-id="' + escapeHtml(calId) + '" data-estado="' + escapeHtml(st) + '">' +
            '<td>' + escapeHtml(a.matricula || '—') + '</td>' +
            '<td>' + escapeHtml(nombreCompletoAlumno(a)) + '</td>' +
            '<td><input class="form-control form-control-sm inpCal inpAdminCalFinal" type="number" min="5" max="10" step="any" inputmode="decimal" placeholder="5.00–10.00" value="' + escapeHtml(val) + '"' + (editable ? '' : ' disabled') + '></td>' +
            '<td>' + badgeEstado(estado) + '</td>' +
            '<td class="text-end text-nowrap">' + acciones + '</td>' +
            '</tr>';
        }

        const critTds = criteriosList.map(c => {
          const cid = c && c.id != null ? String(c.id) : '';
          const max = c && c.porcentaje != null ? Number(c.porcentaje) : 0;
          const pv = cid && byCrit[cid] != null ? String(byCrit[cid]) : '';
          return '<td style="min-width: 140px;">' +
            '<div class="input-group input-group-sm">' +
            '<input class="form-control inpCrit" type="number" min="0" max="' + escapeHtml(String(max)) + '" step="any" inputmode="decimal" data-criterio-id="' + escapeHtml(cid) + '" value="' + escapeHtml(pv) + '"' + (editable ? '' : ' disabled') + '>' +
            '<span class="input-group-text text-muted">/' + escapeHtml(String(max)) + '</span>' +
            '</div>' +
            '</td>';
        }).join('');

        const finalDisplay = val || '';
        return '<tr data-alumno-id="' + a.id + '" data-cal-id="' + escapeHtml(calId) + '" data-estado="' + escapeHtml(st) + '">' +
          '<td>' + escapeHtml(a.matricula || '—') + '</td>' +
          '<td>' + escapeHtml(nombreCompletoAlumno(a)) + '</td>' +
          critTds +
          '<td class="text-nowrap"><span class="fw-semibold calFinalSpan">' + escapeHtml(finalDisplay || '—') + '</span></td>' +
          '<td>' + badgeEstado(estado) + '</td>' +
          '<td class="text-end text-nowrap">' + acciones + '</td>' +
          '</tr>';
      }).join('');

      return {
        theadTr: theadTr,
        tbodyInner: html || '<tr><td colspan="6" class="text-center text-muted py-4">El grupo no tiene alumnos.</td></tr>',
        criteriosList: criteriosList,
        claseMeta: claseMeta,
        bindRecalc: criteriosList.length > 0
      };
    }

    function enlazarEventosTablaMateria(cardEl, inner, onRefrescar) {
      const criteriosList = inner && Array.isArray(inner.criteriosList) ? inner.criteriosList : [];
      const claseMeta = inner ? inner.claseMeta : null;
      cardEl.querySelectorAll('.btnAdminGuardar').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const tr = this.closest('tr[data-alumno-id]');
          if (!tr || !claseMeta) return;
          try {
            this.disabled = true;
            await guardarFilaAdmin(tr, claseMeta, criteriosList);
            await onRefrescar();
          } catch (e) {
            mostrarErrorCopiable('Error al guardar calificación', e);
          } finally {
            this.disabled = false;
          }
        });
      });
      cardEl.querySelectorAll('.btnAdminConfirmar').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const tr = this.closest('tr[data-alumno-id]');
          if (!tr || !claseMeta) return;
          try {
            this.disabled = true;
            const saved = await guardarFilaAdmin(tr, claseMeta, criteriosList);
            if (!saved || !saved.id) {
              throw new Error('No se pudo guardar la calificación antes de confirmar.');
            }
            await confirmarCalificacion(saved.id);
            await onRefrescar();
          } catch (e) {
            mostrarErrorCopiable('Error al confirmar calificación', e);
          } finally {
            this.disabled = false;
          }
        });
      });
      if (criteriosList.length) {
        cardEl.querySelectorAll('tr[data-alumno-id]').forEach(function (tr) {
          const span = tr.querySelector('.calFinalSpan');
          if (!span) return;
          function recalcular() {
            let total = 0;
            let filled = 0;
            tr.querySelectorAll('.inpCrit').forEach(function (inp) {
              const v = inp.value !== '' ? Number(inp.value) : NaN;
              if (!Number.isNaN(v)) {
                total += v;
                filled++;
              }
            });
            if (filled === criteriosList.length) {
              const final = calificacionFinalDesdePuntos(total);
              span.textContent = final != null ? formatoCalificacionDisplay(final) : '—';
            } else {
              span.textContent = '—';
            }
          }
          tr.querySelectorAll('.inpCrit').forEach(function (inp) {
            inp.addEventListener('input', recalcular);
          });
          recalcular();
        });
      } else {
        cardEl.querySelectorAll('.inpAdminCalFinal').forEach(function (inp) {
          inp.addEventListener('input', function () {
            const raw = (inp.value !== '' && inp.value !== '.') ? Number(inp.value) : NaN;
            if (!Number.isNaN(raw)) {
              if (raw > 10) inp.value = '10';
              if (raw < 5) inp.value = '5';
            }
          });
        });
      }
    }

    async function renderGrupoAdmin() {
      if (!sel || !materiasContainer) return;
      const raw = sel.value;
      if (!raw) {
        materiasContainer.innerHTML = '';
        return;
      }
      const alumnoIdSel = obtenerAlumnoAdminSeleccionadoId();
      if (!alumnoIdSel) {
        materiasContainer.innerHTML = '<p class="text-muted small mb-0 py-3 px-2 border rounded bg-light">Selecciona un <strong>alumno</strong> en la tabla superior para cargar las materias y calificaciones.</p>';
        return;
      }
      const meta = JSON.parse(decodeURIComponent(raw));
      const grupoId = meta.grupoId;
      materiasContainer.innerHTML = '<div class="text-center text-muted py-5"><span class="spinner-border spinner-border-sm me-2"></span>Cargando materias del grupo…</div>';
      try {
        const [grupo, rClases] = await Promise.all([
          cargarGrupo(grupoId, false),
          apiFetch('/horarios/clases-por-grupo?grupoId=' + encodeURIComponent(String(grupoId)), { method: 'GET' })
        ]);
        const clases = rClases && rClases.ok ? await rClases.json().catch(() => []) : [];
        if (!Array.isArray(clases) || clases.length === 0) {
          materiasContainer.innerHTML = '<div class="alert alert-info mb-0">No hay materias con horario activo para este grupo. Revisa la pantalla de <strong>Horarios</strong>.</div>';
          return;
        }
        const alumnosTodos = Array.isArray(grupo.alumnos) ? grupo.alumnos.slice() : [];
        const alumnos = alumnosTodos.filter(a => a && String(a.id) === alumnoIdSel);
        if (!alumnos.length) {
          materiasContainer.innerHTML = '<div class="alert alert-warning mb-0">El alumno seleccionado no está en la lista del grupo. Vuelve a elegir grupo o alumno.</div>';
          return;
        }

        const partes = await Promise.all(clases.map(async (clase) => {
          const inner = await armarTablaAdminUnaMateria(grupoId, clase.asignaturaId, alumnos, {
            grupoId: grupoId,
            asignaturaId: clase.asignaturaId,
            periodo: meta.periodo || ''
          });
          const doc = escapeHtml(clase.maestroNombre || '—');
          const mat = escapeHtml(clase.asignaturaNombre || 'Materia');
          const cardHtml = '<div class="card mb-4 border shadow-sm calif-admin-materia-card" data-asignatura-id="' + clase.asignaturaId + '">' +
            '<div class="card-header bg-white border-bottom py-2 d-flex flex-wrap justify-content-between align-items-center gap-2">' +
            '<span class="fw-semibold">' + mat + '</span>' +
            '<span class="text-muted small">Docente: ' + doc + '</span></div>' +
            '<div class="card-body p-0 table-responsive">' +
            '<table class="table table-hover align-middle mb-0 table-sm">' +
            '<thead class="table-light">' + inner.theadTr + '</thead>' +
            '<tbody>' + inner.tbodyInner + '</tbody>' +
            '</table></div></div>';
          return { html: cardHtml, inner: inner };
        }));

        materiasContainer.innerHTML = partes.map(p => p.html).join('');
        const cards = materiasContainer.querySelectorAll('.calif-admin-materia-card');
        cards.forEach(function (cardEl, idx) {
          const inner = partes[idx] && partes[idx].inner;
          if (inner) enlazarEventosTablaMateria(cardEl, inner, renderGrupoAdmin);
        });
      } catch (e) {
        materiasContainer.innerHTML = '<div class="alert alert-danger mb-0">' + escapeHtml(e.message || 'Error al cargar') + '</div>';
      }
    }

    // Catálogo: un renglón por grupo (todas las materias salen de horarios al elegir)
    try {
      const rGr = await apiFetch('/grupos/activos-con-clases-horario', { method: 'GET' });
      const grupos = rGr && rGr.ok ? await rGr.json().catch(() => []) : [];
      const list = Array.isArray(grupos) ? grupos : [];
      const vistos = new Set();
      const unicos = [];
      list.forEach(g => {
        if (!g || g.id == null || vistos.has(g.id)) return;
        const n = Array.isArray(g.alumnos) ? g.alumnos.length : 0;
        if (n < 1) return;
        vistos.add(g.id);
        unicos.push(g);
      });
      unicos.sort((a, b) => String(a.nombre || '').localeCompare(String(b.nombre || ''), 'es'));
      if (sel) {
        sel.innerHTML = '<option value="">Selecciona…</option>' + unicos.map(g => {
          const pa = g.periodoAcademico;
          const codigoPeriodo = pa && (pa.codigo || pa.nombre) ? String(pa.codigo || pa.nombre) : (g.cicloEscolar ? String(g.cicloEscolar) : '');
          const prog = g.programa || (g.asignatura && g.asignatura.programa);
          const progLabel = prog ? (prog.nombre || prog.clave || '') : '';
          const label = (g.nombre || 'Grupo') + (progLabel ? (' · ' + progLabel) : '') + (codigoPeriodo ? (' (' + codigoPeriodo + ')') : '');
          const val = encodeURIComponent(JSON.stringify({
            grupoId: g.id,
            periodo: codigoPeriodo,
            grupoNombre: g.nombre || ''
          }));
          return '<option value="' + val + '">' + escapeHtml(label) + '</option>';
        }).join('');
        if (!unicos.length) {
          sel.innerHTML = '<option value="">No hay grupos activos con alumnos y horario de clase</option>';
        }
      }
    } catch (e) {
      if (sel) sel.innerHTML = '<option value="">No se pudieron cargar grupos</option>';
    }

    if (sel) {
      sel.addEventListener('change', function () {
        onCambioGrupoAdmin();
      });
    }
    if (alumnoWrap) {
      alumnoWrap.addEventListener('change', function (ev) {
        if (ev.target && ev.target.classList && ev.target.classList.contains('calif-admin-alumno-radio')) {
          sincronizarSeleccionAlumnoAdmin();
          renderGrupoAdmin();
        }
      });
    }
  }

  // -------------------- BOOT --------------------
  async function init() {
    const tbody = document.getElementById('calificacionesTableBody');
    if (!tbody) return;
    await cargarObservaciones();
    const t = tipoUsuarioActual();
    if (t === 'ALUMNO') return initVistaAlumno();
    if (t === 'MAESTRO') return initVistaMaestro();
    return initVistaSecretaria();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();


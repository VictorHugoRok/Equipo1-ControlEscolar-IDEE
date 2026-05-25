(function () {
  'use strict';

  // Usar authFetch central (soporta refresh token). Evita falsos "Sin permiso" por token expirado.
  if (typeof authFetch !== 'function') {
    console.error('authFetch no disponible. Asegura cargar auth.js antes.');
  }

  async function apiJson(method, path, body) {
    return await authFetch(path, {
      method: method,
      body: body ? JSON.stringify(body) : undefined
    });
  }

  function getOrCreateStaticModal(modalEl) {
    try {
      if (!modalEl || !window.bootstrap || !window.bootstrap.Modal) return null;
      // Permitir cerrar por backdrop/ESC; si hay respuestas, el guard se encarga de confirmar.
      return window.bootstrap.Modal.getOrCreateInstance(modalEl, { backdrop: true, keyboard: true, focus: true });
    } catch (_) {
      return null;
    }
  }

  function toastOk(msg) {
    try {
      if (typeof window.showSystemToast === 'function') {
        window.showSystemToast(String(msg || ''), { type: 'success', durationMs: 4200 });
        return true;
      }
    } catch (_) {}
    return false;
  }

  function toastWarn(msg) {
    try {
      if (typeof window.showSystemToast === 'function') {
        window.showSystemToast(String(msg || ''), { type: 'warning', durationMs: 5200 });
        return true;
      }
    } catch (_) {}
    return false;
  }

  function modalTieneRespuestasOComentarios(modalEl) {
    try {
      if (!modalEl) return false;
      var checked = modalEl.querySelector('input[type="radio"]:checked, input[type="checkbox"]:checked');
      if (checked) return true;
      var txt = null;
      modalEl.querySelectorAll('textarea, input[type="text"], input[type="number"]').forEach(function (el) {
        if (txt) return;
        var v = (el && el.value != null) ? String(el.value) : '';
        if (v.trim() !== '') txt = el;
      });
      return !!txt;
    } catch (_) {
      return false;
    }
  }

  function bindModalConfirmarCancelarOnce(opts) {
    var modalEl = opts && opts.modalEl;
    var bsModal = opts && opts.bsModal;
    var cancelBtn = opts && opts.cancelBtn;
    var onConfirmSalir = (opts && opts.onConfirmSalir) ? opts.onConfirmSalir : function () {};
    if (!modalEl || !bsModal) return;
    if (modalEl.dataset && modalEl.dataset.edCancelGuardBound === '1') return;
    if (modalEl.dataset) modalEl.dataset.edCancelGuardBound = '1';

    var allowClose = false;
    async function pedirConfirmacionSiHaceFalta(e) {
      if (allowClose) return;
      if (modalEl.dataset && modalEl.dataset.edForceCloseOk === '1') return;
      if (!modalTieneRespuestasOComentarios(modalEl)) return;
      try { if (e && typeof e.preventDefault === 'function') e.preventDefault(); } catch (_) {}
      var ok = false;
      try {
        ok = (typeof window.uiConfirm === 'function')
          ? await window.uiConfirm('Si sales se perderán las respuestas y comentarios seleccionados.', {
            title: 'Cancelar evaluación',
            subtitle: 'Esta acción no se puede deshacer',
            okText: 'Salir',
            cancelText: 'Seguir respondiendo'
          })
          : false;
      } catch (_) { ok = false; }
      if (!ok) return;
      try { await onConfirmSalir(); } catch (_) {}
      allowClose = true;
      try { bsModal.hide(); } catch (_) {}
    }

    // Interceptar cierre por X / backdrop / ESC (aunque esté desactivado)
    modalEl.addEventListener('hide.bs.modal', function (e) {
      // Si se dispara desde bootstrap, detenemos y lanzamos confirm si hace falta
      pedirConfirmacionSiHaceFalta(e);
    });
    modalEl.addEventListener('hidden.bs.modal', function () {
      allowClose = false;
      if (modalEl.dataset) delete modalEl.dataset.edForceCloseOk;
    });

    // Botón Cancelar (footer)
    if (cancelBtn && !cancelBtn.dataset.edCancelBound) {
      cancelBtn.dataset.edCancelBound = '1';
      cancelBtn.addEventListener('click', function () {
        // Esto dispara hide.bs.modal y ahí se valida el "dirty"
        try { bsModal.hide(); } catch (_) {}
      });
    }
  }

  function rol() {
    return (window.currentUser && window.currentUser.tipoUsuario) || localStorage.getItem('userTipo') || '';
  }

  function puedeAplicarEvaluacionAcademica() {
    var r = String(rol() || '').toUpperCase();
    return r === 'SECRETARIA_ACADEMICA' || r === 'COORDINADOR_ACADEMICO' || r === 'ADMIN';
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /** Meses en español para tarjetas de evaluación (sin ISO/T en pantalla). */
  var ED_MESES_TEXTO = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

  /**
   * Formato: "04 Mayo 2026  8:00pm" (dos espacios entre fecha y hora). Solo fecha si viene como YYYY-MM-DD.
   */
  function parsearFechaEvalDocente(val) {
    if (val == null || val === '') return null;
    var s = String(val).trim();
    if (!s) return null;
    var soloFecha = /^\d{4}-\d{2}-\d{2}$/.test(s);
    var d;
    if (soloFecha) {
      var p = s.split('-');
      var y = parseInt(p[0], 10);
      var mo = parseInt(p[1], 10) - 1;
      var da = parseInt(p[2], 10);
      d = new Date(y, mo, da, 12, 0, 0);
    } else {
      d = new Date(s.indexOf('T') !== -1 ? s : s.replace(/^(\d{4}-\d{2}-\d{2})\s+/, '$1T'));
    }
    if (Number.isNaN(d.getTime())) return null;
    return { date: d, soloFecha: soloFecha, raw: s };
  }

  function formatearFechaHoraEvalDocente(val) {
    if (val == null || val === '') return '';
    var parsed = parsearFechaEvalDocente(val);
    var s = String(val).trim();
    if (!parsed) {
      return escapeHtml(s.replace(/T/g, ' '));
    }
    var d = parsed.date;
    var soloFecha = parsed.soloFecha;
    var dd = String(d.getDate()).padStart(2, '0');
    var mesTxt = ED_MESES_TEXTO[d.getMonth()];
    var yyyy = d.getFullYear();
    if (soloFecha) {
      return escapeHtml(dd + ' ' + mesTxt + ' ' + yyyy);
    }
    var h = d.getHours();
    var m = d.getMinutes();
    var ampm = h >= 12 ? 'pm' : 'am';
    var h12 = h % 12;
    if (h12 === 0) h12 = 12;
    var minStr = String(m).padStart(2, '0');
    var horaStr = h12 + ':' + minStr + ampm;
    return escapeHtml(dd + ' ' + mesTxt + ' ' + yyyy + '  ' + horaStr);
  }

  function formatearFechaLargaEvalDocente(val) {
    if (val == null || val === '') return '';
    var parsed = parsearFechaEvalDocente(val);
    var s = String(val).trim();
    if (!parsed) {
      return escapeHtml(s.replace(/T/g, ' '));
    }
    var d = parsed.date;
    try {
      return escapeHtml(d.toLocaleDateString('es-MX', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }));
    } catch (_) {
      var mesTxt = String(ED_MESES_TEXTO[d.getMonth()] || '').toLowerCase();
      return escapeHtml(String(d.getDate()) + ' de ' + mesTxt + ' de ' + d.getFullYear());
    }
  }

  function limpiarMensajeDisponibilidadEvalDocente(msg) {
    var s = String(msg || '').trim();
    if (!s) return '';
    if (/^La evaluación se habilita al terminar la última sesión de este módulo/i.test(s)) {
      return 'La evaluación se habilita al terminar la última sesión de esta asignatura.';
    }
    if (/^La autoevaluación se habilita al terminar la última sesión de este módulo/i.test(s)) {
      return 'La autoevaluación se habilita al terminar la última sesión de esta asignatura.';
    }
    return s.replace(/\s*\(\d{4}-\d{2}-\d{2}[T ][^)]+\)\.?$/i, '.');
  }

  function errorMsg(err, fallback) {
    try {
      if (!err) return fallback || 'Error del servidor.';
      if (typeof err === 'string') {
        var s0 = String(err || '').trim();
        // Evitar mostrar "Error del servidor: null/undefined"
        var low0 = s0.toLowerCase();
        if (low0 === 'error del servidor: null' || low0 === 'error del servidor: undefined') {
          return fallback || 'Error del servidor.';
        }
        if (!s0 || s0 === 'null' || s0 === 'undefined') return fallback || 'Error del servidor.';
        return s0;
      }
      if (err.message != null) {
        var s1 = String(err.message || '').trim();
        var low1 = s1.toLowerCase();
        if (low1 === 'error del servidor: null' || low1 === 'error del servidor: undefined') {
          return fallback || 'Error del servidor.';
        }
        if (!s1 || s1 === 'null' || s1 === 'undefined') return fallback || 'Error del servidor.';
        return s1;
      }
      if (err.error != null) {
        var s2 = String(err.error || '').trim();
        var low2 = s2.toLowerCase();
        if (low2 === 'error del servidor: null' || low2 === 'error del servidor: undefined') {
          return fallback || 'Error del servidor.';
        }
        if (!s2 || s2 === 'null' || s2 === 'undefined') return fallback || 'Error del servidor.';
        return s2;
      }
      return fallback || 'Error del servidor.';
    } catch (_) {
      return fallback || 'Error del servidor.';
    }
  }

  function mensajeNoHayEvaluaciones() {
    return 'Aún no hay evaluaciones disponibles. Intenta más tarde o consulta con el departamento de Secretaría Académica.';
  }

  function esErrorNoHayEvaluaciones(e) {
    try {
      var m = (e && e.message != null) ? String(e.message) : (typeof e === 'string' ? String(e) : '');
      m = (m || '').trim().toLowerCase();
      if (!m || m === 'null' || m === 'undefined') return true;
      if (m === 'error del servidor: null' || m === 'error del servidor: undefined') return true;
      // authFetch() lanza "HTTP <status>" cuando el backend no devuelve cuerpo.
      if (m === 'http 404' || m === 'http 204') return true;
      return false;
    } catch (_) {
      return true;
    }
  }

  async function authFetchBlob(path, filenameFallback) {
    // authFetch() no sirve para binarios (devuelve JSON o null). Para Excel usamos fetch directo
    // con el token actual + refresh si es necesario.
    var apiBase = (typeof getApiBaseUrl === 'function') ? getApiBaseUrl() : ((typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api');
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
      var msg = 'No se pudo descargar (HTTP ' + r.status + ')';
      try {
        var ct = r.headers.get('Content-Type') || '';
        if (ct.indexOf('application/json') !== -1) {
          var ej = await r.json();
          msg = (ej && (ej.message || ej.error || ej.mensaje)) || msg;
        }
      } catch (_) {}
      throw new Error(msg);
    }
    var blob = await r.blob();
    var cd = r.headers.get('Content-Disposition') || '';
    var fn = filenameFallback || 'plantilla.xlsx';
    var m = cd.match(/filename=\"?([^\";]+)\"?/i);
    if (m && m[1]) fn = m[1];

    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = fn;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { try { URL.revokeObjectURL(url); } catch (_) {} }, 120000);
  }

  // ============================
  // Editor por bloques (UI) + edición
  // ============================
  var edState = { bloques: [] };
  var edEditorInited = false;
  var edEditingId = null; // si no es null, estamos editando un formulario existente

  function tipoBloqueLabel(t) {
    if (t === 'LIKERT_5') return 'Escala Likert (1–5)';
    if (t === 'ABIERTA') return 'Preguntas abiertas';
    if (t === 'OPCION_MULTIPLE') return 'Opción múltiple';
    return t || '—';
  }

  function nuevoBloque() {
    return {
      id: 'b_' + Math.random().toString(36).slice(2),
      // Se muestra como default (“placeholder inteligente”): se limpia al escribir.
      titulo: 'Bloque ' + (edState.bloques.length + 1),
      _autoTitulo: true,
      tipo: 'LIKERT_5',
      preguntas: []
    };
  }

  function nuevaPregunta(tipo) {
    return {
      id: 'q_' + Math.random().toString(36).slice(2),
      texto: '',
      opciones: (tipo === 'OPCION_MULTIPLE') ? [''] : []
    };
  }

  function renderBloques() {
    var wrap = document.getElementById('edBloquesWrap');
    if (!wrap) return;
    if (!edState.bloques.length) {
      wrap.innerHTML = '<div class="text-muted small">Agrega al menos un bloque para comenzar.</div>';
      return;
    }
    wrap.innerHTML = edState.bloques.map(function (b, idx) {
      var defaultTitulo = 'Bloque ' + (idx + 1);
      var nombreBloque = (b && b.titulo != null ? String(b.titulo) : '').trim();
      if (!nombreBloque) nombreBloque = defaultTitulo;
      var preguntasHtml = (b.preguntas || []).map(function (q, qi) {
        var opts = '';
        if (b.tipo === 'OPCION_MULTIPLE') {
          var ops = (q.opciones || []);
          opts =
            '<div class="mt-2">' +
            '<div class="small text-muted mb-1">Opciones</div>' +
            ops.map(function (op, oi) {
              return '<div class="input-group input-group-sm mb-2">' +
                '<span class="input-group-text">' + (oi + 1) + '</span>' +
                '<input type="text" class="form-control ed-opcion" data-bid="' + escapeHtml(b.id) + '" data-qid="' + escapeHtml(q.id) + '" data-oi="' + oi + '" value="' + escapeHtml(op || '') + '" placeholder="Opción" />' +
                '<button type="button" class="btn btn-outline-danger ed-btn-del-op" data-bid="' + escapeHtml(b.id) + '" data-qid="' + escapeHtml(q.id) + '" data-oi="' + oi + '" title="Quitar opción"><i class="bi bi-x-lg"></i></button>' +
                '</div>';
            }).join('') +
            '<button type="button" class="btn btn-outline-secondary btn-sm ed-btn-add-op" data-bid="' + escapeHtml(b.id) + '" data-qid="' + escapeHtml(q.id) + '"><i class="bi bi-plus-lg me-1"></i>Agregar opción</button>' +
            '</div>';
        }
        return '' +
          '<div class="border rounded p-3 mb-2 bg-white">' +
          '<div class="d-flex align-items-start justify-content-between gap-2">' +
          '<div class="flex-grow-1">' +
          '<label class="form-label small mb-1">Pregunta ' + (qi + 1) + '</label>' +
          '<input type="text" class="form-control form-control-sm ed-pregunta-texto" data-bid="' + escapeHtml(b.id) + '" data-qid="' + escapeHtml(q.id) + '" value="' + escapeHtml(q.texto || '') + '" placeholder="Escribe la pregunta" />' +
          opts +
          '</div>' +
          '<button type="button" class="btn btn-outline-danger btn-sm ed-btn-del-preg" data-bid="' + escapeHtml(b.id) + '" data-qid="' + escapeHtml(q.id) + '" title="Eliminar pregunta"><i class="bi bi-trash"></i></button>' +
          '</div>' +
          '</div>';
      }).join('');

      var help = (b.tipo === 'LIKERT_5')
        ? '<div class="text-muted small">Respuestas: 1 a 5</div>'
        : (b.tipo === 'ABIERTA')
          ? '<div class="text-muted small">Respuestas abiertas</div>'
          : '<div class="text-muted small">Selecciona una opción</div>';

      return '' +
        '<div class="card mb-3">' +
        '<div class="card-header bg-soft-primary d-flex align-items-center justify-content-between gap-2">' +
        '<div class="d-flex flex-column">' +
        '<div class="fw-semibold">' + escapeHtml(nombreBloque) + '</div>' +
        '<div class="small text-muted">' + escapeHtml(tipoBloqueLabel(b.tipo)) + '</div>' +
        '</div>' +
        '<button type="button" class="btn btn-outline-danger btn-sm ed-btn-del-bloque" data-bid="' + escapeHtml(b.id) + '"><i class="bi bi-trash me-1"></i>Eliminar bloque</button>' +
        '</div>' +
        '<div class="card-body">' +
        '<div class="row g-3">' +
        '<div class="col-md-6">' +
        '<label class="form-label">Nombre del bloque</label>' +
        '<input type="text" class="form-control ed-bloque-titulo" data-bid="' + escapeHtml(b.id) + '" data-default="' + escapeHtml(defaultTitulo) + '" value="' + escapeHtml((b && b._autoTitulo) ? '' : (b.titulo || '')) + '" placeholder="' + escapeHtml(defaultTitulo) + '" />' +
        '</div>' +
        '<div class="col-md-6">' +
        '<label class="form-label">Tipo de reactivos</label>' +
        '<select class="form-select ed-bloque-tipo" data-bid="' + escapeHtml(b.id) + '">' +
        '<option value="LIKERT_5"' + (b.tipo === 'LIKERT_5' ? ' selected' : '') + '>Escala Likert (1–5)</option>' +
        '<option value="ABIERTA"' + (b.tipo === 'ABIERTA' ? ' selected' : '') + '>Preguntas abiertas</option>' +
        '<option value="OPCION_MULTIPLE"' + (b.tipo === 'OPCION_MULTIPLE' ? ' selected' : '') + '>Opción múltiple</option>' +
        '</select>' +
        help +
        '</div>' +
        '<div class="col-12">' +
        '<div class="d-flex align-items-center justify-content-between">' +
        '<div class="fw-semibold">Preguntas</div>' +
        '<button type="button" class="btn btn-outline-primary btn-sm ed-btn-add-preg" data-bid="' + escapeHtml(b.id) + '"><i class="bi bi-plus-lg me-1"></i>Agregar pregunta</button>' +
        '</div>' +
        '<div class="mt-2">' + (preguntasHtml || '<div class="text-muted small">Aún no hay preguntas en este bloque.</div>') + '</div>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '</div>';
    }).join('');

    wrap.querySelectorAll('.ed-bloque-titulo').forEach(function (inp) {
      inp.addEventListener('focus', function () {
        var bid = inp.getAttribute('data-bid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        // Si es título automático, al enfocarse dejamos el campo vacío para escribir sin borrar manualmente.
        if (b._autoTitulo) {
          inp.value = '';
        }
      });
      inp.addEventListener('input', function () {
        var bid = inp.getAttribute('data-bid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var v = (inp.value || '');
        b.titulo = v;
        b._autoTitulo = v.trim() === '';
      });
      inp.addEventListener('blur', function () {
        var bid = inp.getAttribute('data-bid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        // Si lo dejan vacío, vuelve al default (sin guardarlo como texto “escrito”).
        if ((inp.value || '').trim() === '') {
          b.titulo = (inp.getAttribute('data-default') || '').trim();
          b._autoTitulo = true;
          inp.value = '';
        } else {
          b._autoTitulo = false;
        }
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-bloque-tipo').forEach(function (sel) {
      sel.addEventListener('change', function () {
        var bid = sel.getAttribute('data-bid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var nuevo = sel.value;
        if (nuevo !== b.tipo) {
          b.tipo = nuevo;
          (b.preguntas || []).forEach(function (q) {
            if (nuevo === 'OPCION_MULTIPLE') {
              if (!q.opciones || !q.opciones.length) q.opciones = [''];
            } else {
              q.opciones = [];
            }
          });
        }
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-btn-add-preg').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var bid = btn.getAttribute('data-bid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        b.preguntas.push(nuevaPregunta(b.tipo));
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-btn-del-preg').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var bid = btn.getAttribute('data-bid');
        var qid = btn.getAttribute('data-qid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        b.preguntas = (b.preguntas || []).filter(function (q) { return q.id !== qid; });
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-pregunta-texto').forEach(function (inp) {
      inp.addEventListener('input', function () {
        var bid = inp.getAttribute('data-bid');
        var qid = inp.getAttribute('data-qid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var q = (b.preguntas || []).find(function (x) { return x.id === qid; });
        if (q) q.texto = inp.value || '';
      });
    });
    wrap.querySelectorAll('.ed-btn-del-bloque').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var bid = btn.getAttribute('data-bid');
        edState.bloques = edState.bloques.filter(function (b) { return b.id !== bid; });
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-btn-add-op').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var bid = btn.getAttribute('data-bid');
        var qid = btn.getAttribute('data-qid');
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var q = (b.preguntas || []).find(function (x) { return x.id === qid; });
        if (!q) return;
        if (!q.opciones) q.opciones = [];
        q.opciones.push('');
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-btn-del-op').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var bid = btn.getAttribute('data-bid');
        var qid = btn.getAttribute('data-qid');
        var oi = parseInt(btn.getAttribute('data-oi'), 10);
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var q = (b.preguntas || []).find(function (x) { return x.id === qid; });
        if (!q || !q.opciones) return;
        if (q.opciones.length <= 1) return;
        q.opciones.splice(oi, 1);
        renderBloques();
      });
    });
    wrap.querySelectorAll('.ed-opcion').forEach(function (inp) {
      inp.addEventListener('input', function () {
        var bid = inp.getAttribute('data-bid');
        var qid = inp.getAttribute('data-qid');
        var oi = parseInt(inp.getAttribute('data-oi'), 10);
        var b = edState.bloques.find(function (x) { return x.id === bid; });
        if (!b) return;
        var q = (b.preguntas || []).find(function (x) { return x.id === qid; });
        if (!q) return;
        if (!q.opciones) q.opciones = [''];
        if (!isNaN(oi) && oi >= 0) q.opciones[oi] = inp.value || '';
      });
    });
  }

  function ensureDefaultBloque() {
    if (!edState.bloques.length) {
      var b = nuevoBloque();
      b.tipo = 'LIKERT_5';
      edState.bloques.push(b);
    }
    renderBloques();
  }

  function resetEditorForm() {
    edEditingId = null;
    var btn = document.getElementById('edBtnCrearForm');
    if (btn) btn.textContent = 'Guardar evaluación';
    document.getElementById('edTitulo').value = '';
    document.getElementById('edDesc').value = '';
    document.getElementById('edActivo').checked = true;
    edState.bloques = [];
    ensureDefaultBloque();
  }

  function parseOpcionesToArray(opcionesStr) {
    if (!opcionesStr) return [];
    return String(opcionesStr).split(/\r?\n/g).map(function (s) { return String(s || '').trim(); }).filter(Boolean);
  }

  async function cargarFormularioEnEditor(id) {
    try {
      var f = await authFetch('/evaluaciones-docente/formularios/' + encodeURIComponent(String(id)), { method: 'GET' });
      edEditingId = String(id);
      var btn = document.getElementById('edBtnCrearForm');
      if (btn) btn.textContent = 'Guardar cambios';
      document.getElementById('edTitulo').value = (f && f.titulo) ? String(f.titulo) : '';
      document.getElementById('edDesc').value = (f && f.descripcion) ? String(f.descripcion) : '';
      document.getElementById('edActivo').checked = !!(f && f.activo);

      var preguntas = (f && f.preguntas) ? f.preguntas : [];
      // Agrupar por (bloque + tipo) para respetar el editor (un tipo por bloque)
      var map = new Map();
      preguntas.forEach(function (p) {
        var b = (p && p.bloque != null ? String(p.bloque) : '').trim() || 'Bloque 1';
        var t = (p && p.tipo ? String(p.tipo).toUpperCase() : 'LIKERT_5');
        var key = b + '||' + t;
        if (!map.has(key)) map.set(key, { titulo: b, tipo: t, preguntas: [] });
        map.get(key).preguntas.push(p);
      });
      var bloques = Array.from(map.values());
      if (!bloques.length) {
        edState.bloques = [];
        ensureDefaultBloque();
      } else {
        edState.bloques = bloques.map(function (b, bi) {
          return {
            id: 'b_' + bi + '_' + Math.random().toString(36).slice(2),
            titulo: b.titulo,
            tipo: b.tipo,
            preguntas: (b.preguntas || []).map(function (p) {
              var q = {
                id: 'q_' + Math.random().toString(36).slice(2),
                texto: p.texto || '',
                opciones: []
              };
              if (b.tipo === 'OPCION_MULTIPLE') {
                q.opciones = parseOpcionesToArray(p.opciones);
                if (!q.opciones.length) q.opciones = [''];
              }
              return q;
            })
          };
        });
        renderBloques();
      }

      // Cambiar a tab "Crear"
      var tabBtn = document.getElementById('edTabCrearBtn');
      if (tabBtn) tabBtn.click();
      var top = document.getElementById('evalDocenteRoot');
      if (top) top.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (e) {
      alert(errorMsg(e, 'No se pudo cargar la evaluación.'));
    }
  }

  function initEditorOnce() {
    if (edEditorInited) return;
    edEditorInited = true;

    var btnAddBloque = document.getElementById('edBtnAgregarBloque');
    if (btnAddBloque) {
      btnAddBloque.onclick = function () {
        edState.bloques.push(nuevoBloque());
        renderBloques();
      };
    }

    var btnX = document.getElementById('edBtnExcelPlantilla');
    if (btnX) {
      btnX.onclick = async function () {
        try {
          await authFetchBlob('/evaluaciones-docente/excel/plantilla', 'evaluacion_docente_plantilla.xlsx');
        } catch (e) {
          alert(e.message || 'No se pudo descargar la plantilla');
        }
      };
    }

    var excelInput = document.getElementById('edExcelInput');
    var btnImportarExcel = document.getElementById('edBtnExcelImportar');
    if (btnImportarExcel && excelInput) {
      btnImportarExcel.addEventListener('click', function () {
        excelInput.click();
      });
    }
    if (excelInput) {
      excelInput.addEventListener('change', async function () {
        var f = excelInput.files && excelInput.files[0];
        if (!f) return;
        try {
          var fd = new FormData();
          fd.append('archivo', f);
          await authFetch('/evaluaciones-docente/excel/importar', { method: 'POST', body: fd });
          alert('Excel importado.');
          cargarPanelAdmin();
        } catch (e) {
          alert(e.message || 'No se pudo importar el Excel');
        } finally {
          excelInput.value = '';
        }
      });
    }

    var btnSave = document.getElementById('edBtnCrearForm');
    if (btnSave) {
      btnSave.onclick = async function () {
        var titulo = (document.getElementById('edTitulo').value || '').trim();
        if (!titulo) {
          alert('Indique nombre.');
          return;
        }
        ensureDefaultBloque();
        var bloquesReq = [];
        for (var bi = 0; bi < edState.bloques.length; bi++) {
          var b = edState.bloques[bi];
          if (!b) continue;
          var bt = (b.titulo || '').trim() || ('Bloque ' + (bi + 1));
          var tipoB = b.tipo || 'LIKERT_5';
          var ps = (b.preguntas || []).map(function (q, qi) {
            var texto = (q && q.texto ? String(q.texto) : '').trim();
            if (!texto) return null;
            var out = { tipo: tipoB, texto: texto, orden: qi };
            if (tipoB === 'OPCION_MULTIPLE') {
              var ops = (q.opciones || []).map(function (s) { return String(s || '').trim(); }).filter(Boolean);
              out.opciones = ops;
            }
            return out;
          }).filter(Boolean);
          if (!ps.length) continue;
          bloquesReq.push({ titulo: bt, orden: bi, preguntas: ps });
        }
        if (!bloquesReq.length) {
          alert('Agrega al menos una pregunta.');
          return;
        }
        for (bi = 0; bi < bloquesReq.length; bi++) {
          var bq = bloquesReq[bi];
          for (var pi = 0; pi < (bq.preguntas || []).length; pi++) {
            var pq = bq.preguntas[pi];
            if (pq.tipo === 'OPCION_MULTIPLE') {
              if (!pq.opciones || pq.opciones.length < 2) {
                alert('En opción múltiple, cada pregunta debe tener al menos 2 opciones.');
                return;
              }
            }
          }
        }
        var body = {
          tipo: (document.getElementById('edTipo') && document.getElementById('edTipo').value) || 'POR_ALUMNOS',
          titulo: titulo,
          descripcion: (document.getElementById('edDesc').value || '').trim() || null,
          activo: document.getElementById('edActivo').checked,
          fechaInicio: null,
          fechaFin: null,
          bloques: bloquesReq
        };
        try {
          if (edEditingId) {
            await apiJson('PUT', '/evaluaciones-docente/formularios/' + encodeURIComponent(String(edEditingId)), body);
            alert('Evaluación actualizada.');
          } else {
            await apiJson('POST', '/evaluaciones-docente/formularios', body);
            alert('Formulario registrado.');
          }
          resetEditorForm();
          cargarPanelAdmin();
        } catch (err) {
          alert(errorMsg(err, 'Error al guardar.'));
        }
      };
    }

    ensureDefaultBloque();
  }

  async function cargarPanelAdmin() {
    document.getElementById('panelAdminSecretaria').classList.remove('d-none');
    // Tab "Aplicar" solo para roles permitidos
    try {
      var tabAplicarBtn = document.getElementById('edTabAplicarBtn');
      if (tabAplicarBtn) {
        var li = tabAplicarBtn.closest('li');
        if (li) li.classList.toggle('d-none', !puedeAplicarEvaluacionAcademica());
      }
    } catch (_) {}
    try {
      var lista = await authFetch('/evaluaciones-docente/formularios', { method: 'GET' });
      var tbody = document.getElementById('edTablaFormularios');
      if (!lista || !lista.length) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Ninguno</td></tr>';
      } else {
        tbody.innerHTML = lista.map(function (f) {
          var tipo = f.tipo || 'POR_ALUMNOS';
          var activTxt = (tipo === 'POR_ALUMNOS' || tipo === 'AUTOEVALUACION')
            ? 'Según horario del módulo'
            : (tipo === 'POR_SECRETARIA_ACADEMICA')
              ? 'Según formulario activo (sin vigencia por fechas)'
              : '—';
          var tipoTxt = (tipo === 'POR_ALUMNOS') ? 'Evaluación Docente'
            : (tipo === 'POR_SECRETARIA_ACADEMICA') ? 'Evaluación Académica'
              : (tipo === 'AUTOEVALUACION') ? 'Autoevaluación' : tipo;
          var delBtn = '<button type="button" class="btn btn-sm btn-outline-danger ed-btn-del" data-id="' + escapeHtml(f.id) + '" data-titulo="' + escapeHtml(f.titulo || '') + '">Eliminar</button>';
          var probarBtn = '<button type="button" class="btn btn-sm btn-outline-primary ed-btn-probar" data-id="' + escapeHtml(f.id) + '" data-titulo="' + escapeHtml(f.titulo || '') + '">Probar</button>';
          var editBtn = '<button type="button" class="btn btn-sm btn-outline-secondary ed-btn-edit" data-id="' + escapeHtml(f.id) + '">Editar</button>';
          var excelBtn = '<button type="button" class="btn btn-sm btn-outline-success ed-btn-excel" data-id="' + escapeHtml(f.id) + '" data-titulo="' + escapeHtml(f.titulo || '') + '">Excel</button>';
          return '<tr>' +
            '<td>' + escapeHtml(f.titulo) + '</td>' +
            '<td class="small">' + escapeHtml(tipoTxt) + '</td>' +
            '<td class="small">' + escapeHtml(activTxt) + '</td>' +
            '<td class="d-flex justify-content-between align-items-center gap-2">' +
            (f.activo ? 'Sí' : 'No') +
            '<span class="ms-auto d-flex gap-2">' + excelBtn + editBtn + probarBtn + delBtn + '</span>' +
            '</td>' +
            '</tr>';
        }).join('');
        tbody.querySelectorAll('.ed-btn-del').forEach(function (btn) {
          btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-id');
            var tit = btn.getAttribute('data-titulo') || '';
            var ok = window.confirm('¿Eliminar esta evaluación?\n\n' + tit + '\n\nSe borrarán también las respuestas asociadas.');
            if (!ok) return;
            try {
              await authFetch('/evaluaciones-docente/formularios/' + encodeURIComponent(String(id)), { method: 'DELETE' });
              cargarPanelAdmin();
            } catch (e) {
              alert(e.message || 'Error al eliminar');
            }
          });
        });
        tbody.querySelectorAll('.ed-btn-probar').forEach(function (btn) {
          btn.addEventListener('click', function () {
            var id = btn.getAttribute('data-id');
            if (!id) return;
            cargarPanelProbarFormularioAdmin(id);
          });
        });
        tbody.querySelectorAll('.ed-btn-excel').forEach(function (btn) {
          btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-id');
            var tit = btn.getAttribute('data-titulo') || '';
            if (!id) return;
            try {
              var safe = String(tit || ('evaluacion_' + id)).replace(/[\\\/:*?"<>|]+/g, '').trim();
              var fn = (safe ? safe : ('evaluacion_' + id)) + '.xlsx';
              await authFetchBlob('/evaluaciones-docente/excel/formulario/' + encodeURIComponent(String(id)), fn);
            } catch (e) {
              alert(e.message || 'No se pudo descargar el Excel');
            }
          });
        });
        tbody.querySelectorAll('.ed-btn-edit').forEach(function (btn) {
          btn.addEventListener('click', function () {
            var id = btn.getAttribute('data-id');
            if (!id) return;
            cargarFormularioEnEditor(id);
          });
        });
      }
    } catch (e) {
      document.getElementById('panelAdminSecretaria').classList.add('d-none');
      document.getElementById('evalSinPermiso').classList.remove('d-none');
      document.getElementById('evalSinPermiso').textContent = errorMsg(e, 'No se pudo cargar.');
    }

    initEditorOnce();
    var aplicarTabBtn = document.getElementById('edTabAplicarBtn');
    if (aplicarTabBtn && !aplicarTabBtn.dataset.edSecretariaTabBound) {
      aplicarTabBtn.dataset.edSecretariaTabBound = '1';
      aplicarTabBtn.addEventListener('shown.bs.tab', function () {
        if (puedeAplicarEvaluacionAcademica()) refrescarPanelAplicarSecretaria();
      });
    }
  }

  async function refrescarPanelAplicarSecretaria() {
    var msg = document.getElementById('edSecretariaMensaje');
    var formDiv = document.getElementById('edSecretariaFormulario');
    if (msg) {
      msg.classList.remove('d-none', 'alert-warning', 'alert-success');
      msg.classList.add('alert-info');
      msg.textContent = 'Cargando…';
    }
    if (formDiv) formDiv.classList.add('d-none');
    try {
      var ctx = await authFetch('/evaluaciones-docente/secretaria/contexto', { method: 'GET' });
      await renderPanelSecretariaDesdeContexto(ctx);
    } catch (e) {
      if (msg) {
        msg.classList.remove('d-none', 'alert-info', 'alert-success');
        msg.classList.add('alert-warning');
        msg.textContent = esErrorNoHayEvaluaciones(e) ? mensajeNoHayEvaluaciones() : errorMsg(e, 'No se pudo cargar la evaluación.');
      }
      if (formDiv) formDiv.classList.add('d-none');
    }
  }

  async function renderPanelSecretariaDesdeContexto(ctx) {
    var msg = document.getElementById('edSecretariaMensaje');
    var formDiv = document.getElementById('edSecretariaFormulario');
    var cont = document.getElementById('edSecretariaBloques');

    var form = ctx && ctx.formulario;
    if (!form) {
      if (msg) {
        msg.classList.remove('d-none', 'alert-warning', 'alert-success');
        msg.classList.add('alert-info');
        msg.textContent = 'No hay un formulario de Evaluación Académica vigente.';
      }
      if (formDiv) formDiv.classList.add('d-none');
      return;
    }
    var clases = (ctx && ctx.clases) ? ctx.clases : [];
    if (!clases.length) {
      if (msg) {
        msg.classList.remove('d-none', 'alert-success', 'alert-info');
        msg.classList.add('alert-warning');
        msg.textContent = 'No hay clases disponibles para evaluar (docente y horario) en tu alcance.';
      }
      if (formDiv) formDiv.classList.add('d-none');
      return;
    }

    var pend = (ctx.pendientesEvaluacionDocentes != null) ? Number(ctx.pendientesEvaluacionDocentes) : clases.filter(function (x) { return !x.yaEvaluado; }).length;
    if (pend === 0 && clases.length) {
      if (msg) {
        msg.classList.remove('d-none', 'alert-warning', 'alert-info');
        msg.classList.add('alert-success');
        msg.textContent = 'No hay clases pendientes de evaluación académica en tu alcance para este formulario (si otra persona administrativa ya evaluó a un docente, solo verás el informe).';
      }
    } else if (msg) {
      msg.classList.add('d-none');
    }
    if (formDiv) formDiv.classList.remove('d-none');
    var tit = document.getElementById('edSecretariaTitulo');
    if (tit) tit.textContent = form.titulo || 'Evaluación Académica';

    var preguntas = form.preguntas || [];
    var bloques = groupByBloque(preguntas);
    var totalPreg = (preguntas && preguntas.length) ? preguntas.length : 0;

    var modalEl = document.getElementById('edModalSecretariaResponder');
    var modalTituloEl = document.getElementById('edSecretariaModalTitulo');
    var modalSubEl = document.getElementById('edSecretariaModalSubtitulo');
    var modalDescEl = document.getElementById('edSecretariaModalDesc');
    var modalMsgEl = document.getElementById('edSecretariaModalMsg');
    var modalFormEl = document.getElementById('edSecretariaModalForm');
    var modalBtnEnviar = document.getElementById('edSecretariaModalBtnEnviar');
    var modalBtnCancelar = document.getElementById('edSecretariaModalBtnCancelar');
    var bsModal = getOrCreateStaticModal(modalEl);

    var estadoSecretaria = { formularioId: form.id, clases: clases };

    function draftKey(formularioId, maestroId, horarioBloqueId) {
      return 'edDraftAcademica:v1:' + String(formularioId) + ':' + String(maestroId) + ':' + String(horarioBloqueId);
    }

    function saveDraftForClase(c) {
      try {
        if (!c || !modalFormEl) return;
        var key = draftKey(estadoSecretaria.formularioId, c.maestroId, c.horarioBloqueId);
        var payload = {
          v: 1,
          formularioId: estadoSecretaria.formularioId,
          maestroId: c.maestroId,
          horarioBloqueId: c.horarioBloqueId,
          savedAt: new Date().toISOString(),
          respuestas: [],
          observacionesBloque: []
        };
        (modalFormEl || document).querySelectorAll('.ed-likert[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"]:checked').forEach(function (inp) {
          var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
          var v = parseInt(inp.value, 10);
          if (!isNaN(pr)) payload.respuestas.push({ preguntaId: pr, valor: isNaN(v) ? null : v });
        });
        (modalFormEl || document).querySelectorAll('.ed-abi[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"]').forEach(function (ta) {
          var pr = parseInt(ta.getAttribute('data-pregunta'), 10);
          if (!isNaN(pr)) payload.respuestas.push({ preguntaId: pr, valorTexto: (ta.value || '') });
        });
        (modalFormEl || document).querySelectorAll('.ed-om[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"]:checked').forEach(function (inp) {
          var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
          if (!isNaN(pr)) payload.respuestas.push({ preguntaId: pr, valorTexto: (inp.value || '') });
        });
        (modalFormEl || document).querySelectorAll('.ed-obs-bloque[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"]').forEach(function (ta) {
          payload.observacionesBloque.push({
            bloque: (ta.getAttribute('data-bloque') || ''),
            texto: (ta.value || '')
          });
        });
        localStorage.setItem(key, JSON.stringify(payload));
      } catch (_) {}
    }

    function restoreDraftForClase(c) {
      try {
        if (!c || !modalFormEl) return;
        var key = draftKey(estadoSecretaria.formularioId, c.maestroId, c.horarioBloqueId);
        var raw = localStorage.getItem(key);
        if (!raw) return;
        var d = null;
        try { d = JSON.parse(raw); } catch (_) { d = null; }
        if (!d || !d.respuestas) return;
        (d.respuestas || []).forEach(function (r) {
          if (!r || r.preguntaId == null) return;
          if (r.valor != null) {
            var sel = '.ed-likert[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"][data-pregunta="' + String(r.preguntaId) + '"][value="' + String(r.valor) + '"]';
            var inp = modalFormEl.querySelector(sel);
            if (inp) inp.checked = true;
            return;
          }
          if (r.valorTexto != null && r.valorTexto !== '') {
            // ABIERTA
            var ta = modalFormEl.querySelector('.ed-abi[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"][data-pregunta="' + String(r.preguntaId) + '"]');
            if (ta) ta.value = String(r.valorTexto);
            // OPCION_MULTIPLE (radio)
            var selOm = '.ed-om[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"][data-pregunta="' + String(r.preguntaId) + '"][value="' + String(r.valorTexto).replace(/"/g, '\\"') + '"]';
            var om = modalFormEl.querySelector(selOm);
            if (om) om.checked = true;
          }
        });
        (d.observacionesBloque || []).forEach(function (o) {
          if (!o) return;
          var b = String(o.bloque || '');
          var ta = modalFormEl.querySelector('.ed-obs-bloque[data-maestro="' + c.maestroId + '"][data-horario-bloque="' + c.horarioBloqueId + '"][data-bloque="' + b.replace(/"/g, '\\"') + '"]');
          if (ta) ta.value = String(o.texto || '');
        });
      } catch (_) {}
    }

    var draftSaveTimer = null;
    function scheduleDraftSave() {
      if (draftSaveTimer) clearTimeout(draftSaveTimer);
      draftSaveTimer = setTimeout(function () {
        if (edSecretariaCurrentClase) saveDraftForClase(edSecretariaCurrentClase);
      }, 260);
    }

    bindModalConfirmarCancelarOnce({
      modalEl: modalEl,
      bsModal: bsModal,
      cancelBtn: modalBtnCancelar,
      onConfirmSalir: async function () {
        // Si el usuario confirma salir, se pierden respuestas: limpiar borrador local de esa clase.
        try {
          var cc = edSecretariaCurrentClase;
          if (cc && cc.maestroId != null && cc.horarioBloqueId != null) {
            localStorage.removeItem(draftKey(estadoSecretaria.formularioId, cc.maestroId, cc.horarioBloqueId));
          }
        } catch (_) {}
      }
    });

    function setModalMsg(kind, text) {
      if (!modalMsgEl) return;
      modalMsgEl.classList.remove('d-none', 'alert-danger', 'alert-success', 'alert-info', 'alert-warning');
      modalMsgEl.classList.add(kind || 'alert-info');
      modalMsgEl.textContent = text || '';
    }
    function hideModalMsg() {
      if (!modalMsgEl) return;
      modalMsgEl.classList.add('d-none');
      modalMsgEl.textContent = '';
    }

    function renderFormularioModal(c) {
      if (!modalFormEl || !c) return;
      var mid = c.maestroId;
      var hid = c.horarioBloqueId;
      modalFormEl.innerHTML = bloques.map(function (pair) {
        var bName = pair[0];
        var ps = pair[1] || [];
        var inputs = ps.map(function (p) { return renderPreguntaInput(p, mid, hid); }).join('');
        return '<div class="mb-3">' +
          '<div class="fw-semibold mb-2">' + escapeHtml(bName) + '</div>' +
          inputs +
          '<div class="mt-2">' +
          '<label class="form-label small mb-1">Observaciones / anotaciones (no visible para el docente)</label>' +
          '<textarea class="form-control form-control-sm ed-obs-bloque" rows="2" data-maestro="' + mid + '" data-horario-bloque="' + hid + '" data-bloque="' + escapeHtml(bName) + '" placeholder="Escribe observaciones del bloque..."></textarea>' +
          '</div>' +
          '</div>';
      }).join('');

      // Autosave: guardar mientras responde (borrador local)
      try {
        modalFormEl.oninput = scheduleDraftSave;
        modalFormEl.onchange = scheduleDraftSave;
      } catch (_) {}
      restoreDraftForClase(c);
    }

    async function enviarModalSecretaria() {
      var cc = edSecretariaCurrentClase;
      if (!cc || cc.horarioBloqueId == null || cc.maestroId == null) return;
      var mId = cc.maestroId;
      var hId = cc.horarioBloqueId;
      var valores = [];
      (modalFormEl || document).querySelectorAll('.ed-likert[data-maestro="' + mId + '"][data-horario-bloque="' + hId + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        var v = parseInt(inp.value, 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valor: isNaN(v) ? null : v });
      });
      (modalFormEl || document).querySelectorAll('.ed-abi[data-maestro="' + mId + '"][data-horario-bloque="' + hId + '"]').forEach(function (ta) {
        var pr = parseInt(ta.getAttribute('data-pregunta'), 10);
        var txt = (ta.value || '').trim();
        if (!isNaN(pr) && txt) valores.push({ preguntaId: pr, valorTexto: txt });
      });
      (modalFormEl || document).querySelectorAll('.ed-om[data-maestro="' + mId + '"][data-horario-bloque="' + hId + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (inp.value || '') });
      });
      var observacionesBloque = [];
      (modalFormEl || document).querySelectorAll('.ed-obs-bloque[data-maestro="' + mId + '"][data-horario-bloque="' + hId + '"]').forEach(function (ta) {
        var b = (ta.getAttribute('data-bloque') || '').trim();
        var t = (ta.value || '').trim();
        if (b && t) observacionesBloque.push({ bloque: b, texto: t });
      });
      if (!totalPreg || valores.length !== totalPreg) {
        toastWarn('Responde todos los enunciados antes de guardar la evaluación.');
        return;
      }
      if (!modalBtnEnviar) return;
      modalBtnEnviar.disabled = true;
      hideModalMsg();
      try {
        var payload = { maestroId: mId, horarioBloqueId: hId, valores: valores };
        if (observacionesBloque.length) payload.observacionesBloque = observacionesBloque;
        await apiJson('POST', '/evaluaciones-docente/secretaria/responder', {
          formularioId: estadoSecretaria.formularioId,
          porMaestro: [payload]
        });
        try {
          localStorage.removeItem(draftKey(estadoSecretaria.formularioId, mId, hId));
        } catch (_) {}
        // Guardado OK: cerrar sin confirmación y notificar por toast
        try { if (modalEl && modalEl.dataset) modalEl.dataset.edForceCloseOk = '1'; } catch (_) {}
        toastOk('Respuestas enviadas correctamente');
        try { if (bsModal) bsModal.hide(); } catch (_) {}
        authFetch('/evaluaciones-docente/secretaria/contexto', { method: 'GET' }).then(function (ctx2) {
          return renderPanelSecretariaDesdeContexto(ctx2 && ctx2.formulario && String(ctx2.formulario.id) === String(estadoSecretaria.formularioId) ? ctx2 : ctx);
        }).catch(function () {});
      } catch (err) {
        setModalMsg('alert-danger', errorMsg(err, 'Error al enviar.'));
      } finally {
        modalBtnEnviar.disabled = false;
      }
    }

    if (modalBtnEnviar) {
      modalBtnEnviar.onclick = function () { enviarModalSecretaria(); };
    }
    if (modalEl && !edSecretariaModalHiddenBound) {
      edSecretariaModalHiddenBound = true;
      modalEl.addEventListener('hidden.bs.modal', function () {
        edSecretariaCurrentClase = null;
        var mf = document.getElementById('edSecretariaModalForm');
        var mm = document.getElementById('edSecretariaModalMsg');
        if (mf) mf.innerHTML = '';
        if (mm) {
          mm.classList.add('d-none');
          mm.textContent = '';
        }
      });
    }

    var pendientes = clases.filter(function (c) { return c && !c.yaEvaluado; });
    var completadas = clases.filter(function (c) { return c && c.yaEvaluado; });

    var resumenTxt = (pendientes.length > 0)
      ? ('Tienes ' + String(pendientes.length) + ' clase(s) pendiente(s) de evaluar.')
      : '';

    function renderCard(c, esHistorial) {
      var sub = (c.asignaturaNombre || '—') + ' · Grupo ' + (c.grupoNombre || '—');
      var periodo = c.periodoCodigo ? String(c.periodoCodigo) : '';
      var infoPeriodo = periodo ? ('<div class="small text-muted">' + escapeHtml(periodo) + '</div>') : '';
      var badge = c.yaEvaluado
        ? '<span class="badge bg-success">Registrada</span>'
        : '<span class="badge bg-primary">Pendiente</span>';
      var btn = '';
      if (c.yaEvaluado && esHistorial) {
        btn = '<button type="button" class="btn btn-ide btn-sm ed-btn-informe-academico" data-maestro="' +
          escapeHtml(String(c.maestroId)) + '" data-horario="' + escapeHtml(String(c.horarioBloqueId != null ? c.horarioBloqueId : '')) +
          '" data-nombre="' + escapeHtml(c.nombreCompleto || '') + '">Informe</button>';
      } else if (!c.yaEvaluado && c.horarioBloqueId != null && c.maestroId != null) {
        btn = '<button type="button" class="btn btn-ide btn-sm ed-btn-responder-secretaria" data-horario="' + c.horarioBloqueId + '" data-maestro="' + c.maestroId + '">Responder</button>';
      } else if (!c.yaEvaluado) {
        btn = '<button type="button" class="btn btn-outline-secondary btn-sm" disabled>Responder</button>';
      } else {
        btn = '<button type="button" class="btn btn-outline-secondary btn-sm" disabled>—</button>';
      }
      var fechaObsTxt = '';
      if (c.yaEvaluado && c.fechaVisita) {
        fechaObsTxt = '<div class="text-muted small mt-2"><span class="fw-semibold">Fecha de observación:</span> ' +
          formatearFechaHoraEvalDocente(c.fechaVisita) + '</div>';
      }
      return '' +
        '<div class="col-12 col-md-6 col-xl-4">' +
        '<div class="card h-100">' +
        '<div class="card-header py-2 d-flex flex-column gap-1">' +
        '<div class="d-flex align-items-center justify-content-between gap-2">' +
        '<div class="fw-semibold text-truncate">' + escapeHtml(c.nombreCompleto || '—') + '</div>' +
        '<div>' + badge + '</div>' +
        '</div>' +
        '<div class="small text-muted">' + escapeHtml(sub) + '</div>' +
        infoPeriodo +
        '</div>' +
        '<div class="card-body d-flex flex-column">' +
        fechaObsTxt +
        '<div class="mt-auto pt-2 d-flex justify-content-end">' + btn + '</div>' +
        '</div>' +
        '</div>' +
        '</div>';
    }

    var disponiblesHtml = pendientes.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mb-2">' +
        '<div class="fw-semibold">Pendientes</div>' +
        '<div class="small text-muted">' + escapeHtml(String(pendientes.length) + ' pendiente(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + pendientes.map(function (x) { return renderCard(x, false); }).join('') + '</div>')
      : ('<div class="fw-semibold mb-2">Pendientes</div>' +
        '<div class="alert alert-success py-2 small mb-3">No hay clases pendientes de evaluar.</div>');

    var historialHtml = completadas.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mt-3 mb-2">' +
        '<div class="fw-semibold">Historial</div>' +
        '<div class="small text-muted">' + escapeHtml(String(completadas.length) + ' registrada(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + completadas.map(function (x) { return renderCard(x, true); }).join('') + '</div>')
      : '';

    cont.innerHTML =
      (resumenTxt ? ('<div class="alert alert-info py-2 small mb-3">' + escapeHtml(resumenTxt) + '</div>') : '') +
      disponiblesHtml + historialHtml;

    cont.querySelectorAll('.ed-btn-responder-secretaria').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var hid = btn.getAttribute('data-horario');
        var mid = btn.getAttribute('data-maestro');
        var c = (estadoSecretaria.clases || []).find(function (x) {
          return String(x.horarioBloqueId) === String(hid) && String(x.maestroId) === String(mid);
        });
        if (!c || c.yaEvaluado) return;
        edSecretariaCurrentClase = c;
        hideModalMsg();
        if (modalTituloEl) modalTituloEl.textContent = form.titulo || 'Evaluación Académica';
        if (modalSubEl) {
          modalSubEl.textContent = (c.nombreCompleto || '—') + ' · ' + (c.asignaturaNombre || '—') + ' · Grupo ' + (c.grupoNombre || '—');
        }
        if (modalDescEl) modalDescEl.textContent = (form.descripcion || '') ? String(form.descripcion) : '';
        renderFormularioModal(c);
        try { if (bsModal) bsModal.show(); } catch (_) {}
      });
    });

    cont.querySelectorAll('.ed-btn-informe-academico').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var mid = btn.getAttribute('data-maestro');
        var hid = btn.getAttribute('data-horario');
        var nombre = btn.getAttribute('data-nombre') || '';
        var fid = estadoSecretaria.formularioId;
        if (!mid || !fid || hid == null || String(hid).trim() === '') return;
        var claseRow = (estadoSecretaria.clases || []).find(function (x) {
          return String(x.maestroId) === String(mid) && String(x.horarioBloqueId) === String(hid);
        });
        if (!claseRow || !claseRow.yaEvaluado) {
          alert('Solo puedes elaborar el informe cuando la evaluación académica ya fue registrada para esta clase.');
          return;
        }
        abrirModalInformeAcademico(fid, mid, nombre, hid);
      });
    });
  }

  var estadoAlumnoEval = { formulario: null, maestros: [], preguntas: [] };
  var edSecretariaCurrentClase = null;
  var edSecretariaModalHiddenBound = false;
  /** Estado del modal de informe académico (administrativos). */
  var edInformeModalEstado = { formularioId: null, maestroId: null, horarioBloqueId: null, limite: 8000 };

  /** Escalas Likert 1–5 (única fuente para formularios y visualización de respuestas). */
  var ED_LIKERT_5_ETIQUETAS = [
    'Totalmente en desacuerdo',
    'En desacuerdo',
    'Neutral',
    'De acuerdo',
    'Totalmente de acuerdo'
  ];

  /** Etiqueta con numeración: «1 · Totalmente en desacuerdo» (punto medio como separador). */
  function likert5EtiquetaEnumerada(valorEntero) {
    var n = typeof valorEntero === 'number' ? valorEntero : parseInt(valorEntero, 10);
    if (isNaN(n) || n < 1 || n > 5) return '';
    var t = ED_LIKERT_5_ETIQUETAS[n - 1];
    return String(n) + ' · ' + t;
  }

  /** Texto descriptivo para ítems Likert 1–5 (tablas informe, resultados, etc.). */
  function likert5ValorTexto(val) {
    if (val == null || val === '') return '—';
    var n = typeof val === 'number' ? val : parseInt(val, 10);
    if (isNaN(n) || n < 1 || n > 5) return escapeHtml(String(val));
    return escapeHtml(likert5EtiquetaEnumerada(n));
  }

  function formatoPromedioLikert(p) {
    if (p == null || p === '') return '—';
    var x = typeof p === 'number' ? p : parseFloat(p);
    if (isNaN(x)) return '—';
    return escapeHtml((Math.round(x * 100) / 100).toFixed(2));
  }

  function renderHtmlInformeDetalleAdmin(d) {
    if (!d || d.ok === false) {
      return '<div class="alert alert-warning mb-0 shadow-sm">' + escapeHtml((d && d.mensaje) || 'Sin datos.') + '</div>';
    }
    var html = '';
    var clase = d.claseObservada || {};
    html += '<div class="card border-0 shadow-sm mb-3"><div class="card-body py-3">';
    html += '<div class="mb-2"><span class="fw-semibold">Clase observada:</span> ' +
      escapeHtml(String((clase.asignaturaNombre || '—') + ' · Grupo ' + (clase.grupoNombre || '—'))) + '</div>';
    if (d.fechaObservacion) {
      html += '<div class="mb-2"><span class="fw-semibold">Fecha de observación:</span> ' +
        formatearFechaHoraEvalDocente(d.fechaObservacion) + '</div>';
    }
    html += '<div class="mb-0"><span class="fw-semibold">Evaluador:</span> ' + escapeHtml(d.nombreEvaluador || '—') + '</div>';
    html += '</div></div>';
    if (d.informeParaDocenteActual != null && String(d.informeParaDocenteActual).trim() !== '') {
      html += '<div class="card border-0 shadow-sm mb-3"><div class="card-body py-3">';
      if (d.docenteLeidoInforme && d.informeLeidoEn) {
        html += '<div class="mb-0 small"><span class="fw-semibold text-success">Lectura confirmada por el docente:</span> ' +
          formatearFechaHoraEvalDocente(d.informeLeidoEn) + '</div>';
      } else {
        html += '<div class="mb-0 small"><span class="fw-semibold text-warning">Lectura por el docente:</span> pendiente (aparecerá la fecha al consultar el informe en su portal).</div>';
      }
      html += '</div></div>';
    } else {
      html += '<div class="card border-0 shadow-sm mb-3"><div class="card-body py-3">' +
        '<div class="mb-0 small text-muted">Sin texto de informe publicado aún para el docente.</div>' +
        '</div></div>';
    }

    function bloqueInformeKey(b) {
      var s = (b != null ? String(b) : '').trim();
      return s || '—';
    }

    var resp = d.respuestasEvaluacionAcademica || [];
    if (resp.length) {
      var obsPorBloque = {};
      (d.observacionesPorBloque || []).forEach(function (o) {
        if (!o) return;
        var k = bloqueInformeKey(o.bloque);
        obsPorBloque[k] = o.texto != null ? String(o.texto) : '';
      });

      var respOrd = resp.slice().sort(function (a, b) {
        var ba = bloqueInformeKey(a.bloque);
        var bb = bloqueInformeKey(b.bloque);
        if (ba !== bb) return ba.localeCompare(bb, 'es', { sensitivity: 'base' });
        var oa = a.orden != null ? Number(a.orden) : 0;
        var ob = b.orden != null ? Number(b.orden) : 0;
        if (oa !== ob) return oa - ob;
        return (Number(a.preguntaId) || 0) - (Number(b.preguntaId) || 0);
      });

      html += '<h6 class="h6 text-primary border-bottom pb-2 mb-3 mt-2">Evaluación académica</h6>' +
        '<div class="table-responsive"><table class="table table-sm align-middle"><thead><tr><th>Pregunta</th><th class="text-center">Respuesta</th></tr></thead><tbody>';

      var idx = 0;
      while (idx < respOrd.length) {
        var claveBloque = bloqueInformeKey(respOrd[idx].bloque);
        var sig = idx;
        while (sig < respOrd.length && bloqueInformeKey(respOrd[sig].bloque) === claveBloque) {
          sig++;
        }
        for (var k = idx; k < sig; k++) {
          var row = respOrd[k];
          var tipo = (row.tipo || '').toUpperCase();
          var celdaResp = '—';
          if (tipo === 'LIKERT_5' && row.valor != null) {
            celdaResp = likert5ValorTexto(row.valor);
          } else if (row.valorTexto != null && String(row.valorTexto).trim() !== '') {
            celdaResp = escapeHtml(String(row.valorTexto));
          }
          html += '<tr><td>' + escapeHtml(row.texto || '—') + '</td><td class="text-center">' + celdaResp + '</td></tr>';
        }
        var rawObs = obsPorBloque.hasOwnProperty(claveBloque) ? obsPorBloque[claveBloque] : '';
        var obsTxt = (rawObs != null && String(rawObs).trim() !== '')
          ? escapeHtml(String(rawObs).trim())
          : '<span class="text-muted">Sin observaciones</span>';
        html += '<tr class="table-light">' +
          '<td colspan="2" class="small py-2">' +
          '<span class="fw-semibold text-secondary">Observaciones / anotaciones del bloque</span>' +
          (claveBloque !== '—' ? ' <span class="text-muted">(' + escapeHtml(claveBloque) + ')</span>' : '') +
          '<div class="mt-1">' + obsTxt + '</div>' +
          '</td></tr>';
        idx = sig;
      }

      html += '</tbody></table></div>';
    } else if ((d.observacionesPorBloque || []).length) {
      /* Respuestas vacías pero hubo observaciones guardadas (caso raro): mostrar solo anotaciones */
      html += '<h6 class="h6 text-primary border-bottom pb-2 mb-3 mt-2">Evaluación académica</h6>';
      (d.observacionesPorBloque || []).forEach(function (o) {
        if (!o) return;
        var k = bloqueInformeKey(o.bloque);
        var t = (o.texto != null && String(o.texto).trim() !== '')
          ? escapeHtml(String(o.texto).trim())
          : '<span class="text-muted">Sin observaciones</span>';
        html += '<div class="border rounded p-2 mb-2 bg-light small">' +
          '<div class="fw-semibold">' + escapeHtml(k !== '—' ? k : 'Bloque') + '</div>' +
          '<div class="mt-1">' + t + '</div></div>';
      });
    }

    var pl = d.promediosEstudiantesLikert || [];
    if (pl.length) {
      var numRespEstudiantes = 0;
      pl.forEach(function (x) {
        var ne = x.numeroEvaluaciones != null ? Number(x.numeroEvaluaciones) : 0;
        if (!isNaN(ne) && ne > numRespEstudiantes) numRespEstudiantes = ne;
      });
      html += '<h6 class="h6 text-primary border-bottom pb-2 mb-3 mt-4">Estudiantes <span class="fw-normal text-body-secondary small">' +
        '· Número de respuestas: ' + escapeHtml(String(numRespEstudiantes)) + '</span></h6>' +
        '<div class="table-responsive"><table class="table table-sm align-middle"><thead><tr><th>Pregunta</th><th class="text-center">Promedio</th></tr></thead><tbody>';
      pl.forEach(function (x) {
        html += '<tr><td>' + escapeHtml(x.preguntaTexto || '—') + '</td><td class="text-center">' +
          formatoPromedioLikert(x.promedio) + '</td></tr>';
      });
      html += '</tbody></table></div>';
    }

    var rtx = d.respuestasTextoEstudiantes || [];
    if (rtx.length) {
      html += '<h6 class="h6 text-primary border-bottom pb-2 mb-3 mt-4">Estudiantes — respuestas abiertas (anonimizado)</h6>';
      rtx.forEach(function (b) {
        var rs = Array.isArray(b.respuestas) ? b.respuestas : [];
        html += '<div class="mb-3"><div class="fw-semibold small">' + escapeHtml(b.preguntaTexto || '—') + '</div>';
        if (rs.length) {
          rs.forEach(function (t) {
            html += '<div class="border rounded p-2 mb-1 small">' + escapeHtml(String(t)) + '</div>';
          });
        } else {
          html += '<div class="text-muted small">Sin respuestas.</div>';
        }
        html += '</div>';
      });
    }

    var auto = d.autoevaluacionLikert || [];
    if (auto.length) {
      html += '<h6 class="h6 text-primary border-bottom pb-2 mb-3 mt-4">Autoevaluación del docente' +
        (d.formularioAutoevaluacionTitulo
          ? ' <span class="fw-normal text-body-secondary small"> — ' + escapeHtml(String(d.formularioAutoevaluacionTitulo)) + '</span>'
          : '') + '</h6>' +
        '<div class="table-responsive"><table class="table table-sm align-middle"><thead><tr><th>Pregunta</th><th class="text-center">Valor</th></tr></thead><tbody>';
      auto.forEach(function (x) {
        var celdaVal = (x.valor != null) ? likert5ValorTexto(x.valor) : '—';
        html += '<tr><td>' + escapeHtml(x.preguntaTexto || '—') + '</td><td class="text-center">' + celdaVal + '</td></tr>';
      });
      html += '</tbody></table></div>';
    }

    return html;
  }

  function actualizarContadorInforme(limite) {
    var ta = document.getElementById('edInformeAcademicoTexto');
    var sp = document.getElementById('edInformeAcademicoContador');
    if (!ta || !sp) return;
    var n = (ta.value || '').length;
    var L = limite != null ? limite : 8000;
    sp.textContent = n + ' / ' + L;
    if (n > L) sp.classList.add('text-danger'); else sp.classList.remove('text-danger');
  }

  /** Habilita/deshabilita la redacción del informe (solo si ya existe evaluación académica completada). */
  function aplicarEstadoEdicionInformeModal(puedeEditar, limiteOpt) {
    var ta = document.getElementById('edInformeAcademicoTexto');
    var btn = document.getElementById('edInformeAcademicoBtnGuardar');
    var btnM = document.getElementById('edInformeAcademicoBtnGuardarMobile');
    var ayuda = document.getElementById('edInformeAcademicoAyudaRedaccion');
    if (ta) {
      ta.disabled = !puedeEditar;
      if (!puedeEditar) ta.value = '';
    }
    if (btn) btn.disabled = !puedeEditar;
    if (btnM) btnM.disabled = !puedeEditar;
    if (ayuda) {
      ayuda.textContent = puedeEditar
        ? 'Este texto es el que verá el docente en su portal.'
        : 'El informe solo está disponible después de registrar la evaluación académica (visita y respuestas). No aplica a docentes pendientes de evaluar.';
    }
    actualizarContadorInforme(limiteOpt != null ? limiteOpt : edInformeModalEstado.limite || 8000);
  }

  async function guardarInformeAcademicoModal() {
    var st = edInformeModalEstado;
    if (!st.formularioId || !st.maestroId || st.horarioBloqueId == null || String(st.horarioBloqueId).trim() === '') return;
    var txtEl = document.getElementById('edInformeAcademicoTexto');
    if (txtEl && txtEl.disabled) return;
    var lim = st.limite || 8000;
    var v = txtEl ? (txtEl.value || '') : '';
    if (v.length > lim) {
      alert('El informe no puede superar ' + lim + ' caracteres.');
      return;
    }
    var msgEl = document.getElementById('edInformeAcademicoMsg');
    try {
      await apiJson('PUT', '/evaluaciones-docente/secretaria/informe', {
        formularioId: st.formularioId,
        maestroId: st.maestroId,
        horarioBloqueId: st.horarioBloqueId,
        informeParaDocente: v
      });
      if (msgEl) {
        msgEl.classList.remove('d-none', 'alert-danger', 'alert-warning');
        msgEl.classList.add('alert-success');
        msgEl.textContent = 'Informe guardado.';
      }
      refrescarPanelAplicarSecretaria();
    } catch (err) {
      if (msgEl) {
        msgEl.classList.remove('d-none', 'alert-success', 'alert-warning');
        msgEl.classList.add('alert-danger');
        msgEl.textContent = errorMsg(err, 'No se pudo guardar.');
      } else {
        alert(errorMsg(err, 'No se pudo guardar.'));
      }
    }
  }

  async function abrirModalInformeAcademico(formularioId, maestroId, nombreDocente, horarioBloqueId) {
    var modalEl = document.getElementById('edModalInformeAcademico');
    var cuerpo = document.getElementById('edInformeAcademicoCuerpo');
    var titulo = document.getElementById('edInformeAcademicoTitulo');
    var subt = document.getElementById('edInformeAcademicoSubtitulo');
    var ta = document.getElementById('edInformeAcademicoTexto');
    var msg = document.getElementById('edInformeAcademicoMsg');
    if (!modalEl || !cuerpo) return;
    edInformeModalEstado.formularioId = formularioId;
    edInformeModalEstado.maestroId = maestroId;
    edInformeModalEstado.horarioBloqueId = horarioBloqueId != null ? horarioBloqueId : null;
    aplicarEstadoEdicionInformeModal(false, 8000);
    if (msg) {
      msg.classList.add('d-none');
      msg.textContent = '';
      msg.classList.remove('alert-danger', 'alert-success', 'alert-warning');
    }
    cuerpo.innerHTML = '<p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2" role="status"></span>Cargando datos…</p>';
    if (ta) ta.value = '';
    if (subt) subt.textContent = nombreDocente ? String(nombreDocente) : '';
    try {
      var q = '?formularioId=' + encodeURIComponent(String(formularioId)) + '&maestroId=' + encodeURIComponent(String(maestroId))
        + '&horarioBloqueId=' + encodeURIComponent(String(horarioBloqueId));
      var d = await authFetch('/evaluaciones-docente/secretaria/informe/detalle' + q, { method: 'GET' });
      var lim = (d && d.limiteCaracteresInforme != null) ? Number(d.limiteCaracteresInforme) : 8000;
      edInformeModalEstado.limite = lim;
      var puede = !!(d && d.ok !== false && (d.puedeEditarInforme !== false));
      cuerpo.innerHTML = renderHtmlInformeDetalleAdmin(d);
      if (titulo) {
        titulo.textContent = (d && d.formularioAcademico && d.formularioAcademico.titulo)
          ? String(d.formularioAcademico.titulo)
          : 'Informe de observación';
      }
      if (msg) {
        if (!puede) {
          msg.classList.remove('d-none', 'alert-success', 'alert-danger');
          msg.classList.add('alert-warning');
          msg.textContent = (d && d.mensaje) ? String(d.mensaje) : 'No hay evaluación completada para elaborar informe.';
        } else {
          msg.classList.add('d-none');
        }
      }
      if (ta && puede) {
        ta.value = (d.informeParaDocenteActual != null && d.informeParaDocenteActual !== '') ? String(d.informeParaDocenteActual) : '';
      }
      aplicarEstadoEdicionInformeModal(puede, lim);
      var bsModal = null;
      try {
        if (window.bootstrap && window.bootstrap.Modal) {
          bsModal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
        }
      } catch (_) {}
      if (bsModal) bsModal.show();
    } catch (e) {
      aplicarEstadoEdicionInformeModal(false, 8000);
      cuerpo.innerHTML = '<div class="alert alert-danger mb-0">' + escapeHtml(errorMsg(e, 'No se pudo cargar.')) + '</div>';
      if (msg) {
        msg.classList.remove('d-none', 'alert-success', 'alert-warning');
        msg.classList.add('alert-danger');
        msg.textContent = errorMsg(e, 'Error al cargar el informe.');
      }
      try {
        if (window.bootstrap && window.bootstrap.Modal) {
          window.bootstrap.Modal.getOrCreateInstance(modalEl).show();
        }
      } catch (_) {}
    }
  }

  function bindInformeAcademicoModalOnce() {
    var ta = document.getElementById('edInformeAcademicoTexto');
    if (ta && !ta.dataset.edInformeBound) {
      ta.dataset.edInformeBound = '1';
      ta.addEventListener('input', function () {
        actualizarContadorInforme(edInformeModalEstado.limite || 8000);
      });
    }
    ['edInformeAcademicoBtnGuardar', 'edInformeAcademicoBtnGuardarMobile'].forEach(function (id) {
      var btn = document.getElementById(id);
      if (!btn || btn.dataset.edInformeGuardBound) return;
      btn.dataset.edInformeGuardBound = '1';
      btn.addEventListener('click', function () { guardarInformeAcademicoModal(); });
    });
  }

  var ED_STORAGE_VIO_RESULTADOS = 'ed_eval_docente_maestro_vio_resultados';
  var ED_STORAGE_LAST_SIN_LEER = 'ed_eval_docente_last_sin_leer';

  function bindMaestroResultadosTabBadgeOnce() {
    var tabResBtn = document.getElementById('edMaestroTabResultadosBtn');
    if (!tabResBtn || tabResBtn.dataset.edResBadgeBound) return;
    tabResBtn.dataset.edResBadgeBound = '1';
    tabResBtn.addEventListener('shown.bs.tab', function () {
      try {
        sessionStorage.setItem(ED_STORAGE_VIO_RESULTADOS, '1');
      } catch (_) {}
      var badgeTab = document.getElementById('edMaestroTabResultadosBadge');
      if (badgeTab) badgeTab.classList.add('d-none');
    });
  }

  async function refreshMaestroInformesEvalDocenteUi() {
    var badgeTab = document.getElementById('edMaestroTabResultadosBadge');
    try {
      var r = await authFetch('/evaluaciones-docente/maestro/informes-academicos/resumen', { method: 'GET' });
      var n = r && r.sinLeer != null ? Number(r.sinLeer) : 0;
      var prev = -1;
      try {
        var s = sessionStorage.getItem(ED_STORAGE_LAST_SIN_LEER);
        if (s != null && s !== '') prev = parseInt(s, 10);
      } catch (_) {}
      try {
        if (prev >= 0 && n > prev) {
          sessionStorage.removeItem(ED_STORAGE_VIO_RESULTADOS);
        }
        sessionStorage.setItem(ED_STORAGE_LAST_SIN_LEER, String(n));
      } catch (_) {}
      var vio = false;
      try {
        vio = sessionStorage.getItem(ED_STORAGE_VIO_RESULTADOS) === '1';
      } catch (_) {}
      if (badgeTab) {
        badgeTab.classList.toggle('d-none', n <= 0 || vio);
      }
    } catch (_) {
      if (badgeTab) badgeTab.classList.add('d-none');
    }
    if (typeof window.refreshEvalDocenteSidebarBadgeMaestro === 'function') {
      await window.refreshEvalDocenteSidebarBadgeMaestro();
    }
  }
  window.refreshMaestroInformesEvalDocenteUi = refreshMaestroInformesEvalDocenteUi;

  async function cargarPanelAlumno() {
    document.getElementById('panelAlumno').classList.remove('d-none');
    var msg = document.getElementById('edAlumnoMensaje');
    var formDiv = document.getElementById('edAlumnoFormulario');
    var acciones = document.getElementById('edAlumnoAcciones');
    if (acciones) acciones.classList.add('d-none');
    try {
      var ctx = await authFetch('/evaluaciones-docente/alumno/contexto', { method: 'GET' });
      await renderPanelAlumnoDesdeContexto(ctx, {
        modoPrueba: false,
        postUrl: '/evaluaciones-docente/alumno/responder',
        onSuccess: function () { cargarPanelAlumno(); }
      });
    } catch (e) {
      msg.classList.remove('d-none');
      msg.classList.add('alert-warning');
      msg.textContent = esErrorNoHayEvaluaciones(e) ? mensajeNoHayEvaluaciones() : errorMsg(e, 'No se pudo cargar la evaluación.');
      formDiv.classList.add('d-none');
    }
  }

  function parseOpciones(opcionesStr) {
    if (!opcionesStr) return [];
    return String(opcionesStr)
      .split(/\r?\n/g)
      .map(function (s) { return String(s || '').trim(); })
      .filter(Boolean);
  }

  function groupByBloque(preguntas) {
    var map = new Map();
    (preguntas || []).forEach(function (p) {
      var b = (p && p.bloque != null ? String(p.bloque) : '').trim() || 'Bloque';
      if (!map.has(b)) map.set(b, []);
      map.get(b).push(p);
    });
    // ordenar por orden si viene
    map.forEach(function (arr) {
      arr.sort(function (a, b) {
        var oa = (a && a.orden != null) ? Number(a.orden) : 0;
        var ob = (b && b.orden != null) ? Number(b.orden) : 0;
        if (oa !== ob) return oa - ob;
        return (Number(a.id) || 0) - (Number(b.id) || 0);
      });
    });
    return Array.from(map.entries());
  }

  function renderPreguntaInput(p, maestroId, horarioBloqueIdOpt) {
    var tipo = (p && p.tipo) ? String(p.tipo).toUpperCase() : 'LIKERT_5';
    var pid = p.id;
    var hExtra = (horarioBloqueIdOpt != null && horarioBloqueIdOpt !== '')
      ? (' data-horario-bloque="' + escapeHtml(String(horarioBloqueIdOpt)) + '"')
      : '';
    var hSuffix = (horarioBloqueIdOpt != null && horarioBloqueIdOpt !== '') ? ('_' + String(horarioBloqueIdOpt)) : '';
    var label = '<label class="form-label small mb-1">' + escapeHtml(p.texto) + '</label>';
    if (tipo === 'ABIERTA') {
      return '<div class="mb-3">' + label +
        '<textarea class="form-control form-control-sm ed-abi" rows="2" data-maestro="' + maestroId + '" data-pregunta="' + pid + '"' + hExtra + ' placeholder="Escribe tu respuesta"></textarea>' +
        '</div>';
    }
    if (tipo === 'OPCION_MULTIPLE') {
      var ops = parseOpciones(p.opciones);
      var nameOm = 'ed_om_' + String(maestroId) + hSuffix + '_' + String(pid);
      var radiosOm = ops.map(function (o, idx) {
        var optId = nameOm + '_' + idx;
        return '' +
          '<div class="form-check">' +
          '<input class="form-check-input ed-om" type="radio" name="' + escapeHtml(nameOm) + '" id="' + escapeHtml(optId) + '" value="' + escapeHtml(o) + '" data-maestro="' + maestroId + '" data-pregunta="' + pid + '"' + hExtra + '>' +
          '<label class="form-check-label" for="' + escapeHtml(optId) + '">' + escapeHtml(o) + '</label>' +
          '</div>';
      }).join('');
      return '<div class="mb-3">' + label +
        '<div class="ms-1">' + (radiosOm || '<div class="text-muted small">Sin opciones configuradas.</div>') + '</div>' +
        '</div>';
    }
    // Default: LIKERT_5 (etiquetas «n · texto» alineadas con likert5ValorTexto)
    var nameLik = 'ed_lik_' + String(maestroId) + hSuffix + '_' + String(pid);
    var radiosLik = ED_LIKERT_5_ETIQUETAS.map(function (t, i) {
      var v = i + 1;
      var optId = nameLik + '_' + v;
      var etiqueta = likert5EtiquetaEnumerada(v);
      return '' +
        '<div class="form-check">' +
        '<input class="form-check-input ed-likert" type="radio" name="' + escapeHtml(nameLik) + '" id="' + escapeHtml(optId) + '" value="' + v + '" data-maestro="' + maestroId + '" data-pregunta="' + pid + '"' + hExtra + '>' +
        '<label class="form-check-label" for="' + escapeHtml(optId) + '">' + escapeHtml(etiqueta) + '</label>' +
        '</div>';
    }).join('');
    return '<div class="mb-3">' + label +
      '<div class="ms-1">' + radiosLik + '</div>' +
      '</div>';
  }

  async function renderPanelAlumnoDesdeContexto(ctx, opts) {
    opts = opts || {};
    var postUrl = opts.postUrl;
    var onSuccess = typeof opts.onSuccess === 'function' ? opts.onSuccess : function () {};
    var onCancel = typeof opts.onCancel === 'function' ? opts.onCancel : null;
    var modoPrueba = !!opts.modoPrueba;

    document.getElementById('panelAlumno').classList.remove('d-none');
    var msg = document.getElementById('edAlumnoMensaje');
    var formDiv = document.getElementById('edAlumnoFormulario');
    var acciones = document.getElementById('edAlumnoAcciones');

    var form = ctx && ctx.formulario;
    if (!form) {
      msg.classList.remove('d-none', 'alert-warning', 'alert-success');
      msg.classList.add('alert-info');
      msg.textContent = 'No hay un formulario de evaluación vigente.';
      formDiv.classList.add('d-none');
      if (acciones) acciones.classList.add('d-none');
      return;
    }
    var obligaciones = (ctx && ctx.obligaciones) ? ctx.obligaciones : [];
    if (modoPrueba && !obligaciones.length && ctx && ctx.maestros && ctx.maestros.length) {
      obligaciones = ctx.maestros.map(function (m) {
        return {
          horarioBloqueId: null,
          maestroId: m.id,
          nombreCompleto: m.nombreCompleto || '',
          asignaturaNombre: 'Modo prueba',
          grupoNombre: '—',
          puedeResponder: true,
          yaEvaluado: false,
          mensaje: null
        };
      });
    }
    var pend = (ctx.pendientesEvaluacion != null) ? Number(ctx.pendientesEvaluacion) : 0;

    if (!obligaciones.length) {
      msg.classList.remove('d-none', 'alert-success', 'alert-info');
      msg.classList.add('alert-warning');
      msg.textContent = 'No hay módulos en tu horario para evaluar (revisa que existan bloques activos con docente y asignatura).';
      formDiv.classList.add('d-none');
      if (acciones) acciones.classList.add('d-none');
      return;
    }

    msg.classList.add('d-none');
    formDiv.classList.remove('d-none');
    document.getElementById('edAlumnoTitulo').textContent = form.titulo || 'Evaluación docente';

    if (acciones) {
      if (modoPrueba && onCancel) {
        acciones.classList.remove('d-none');
        acciones.innerHTML = '' +
          '<div class="d-flex flex-wrap gap-2">' +
          '<button type="button" class="btn btn-outline-secondary btn-sm" id="edBtnCancelarPrueba">' +
          '<i class="bi bi-x-lg me-1"></i>Salir de prueba</button>' +
          '<div class="text-muted small align-self-center">Modo prueba: no se guardan respuestas.</div>' +
          '</div>';
        var btnCancel = document.getElementById('edBtnCancelarPrueba');
        if (btnCancel) btnCancel.onclick = onCancel;
      } else {
        acciones.classList.add('d-none');
        acciones.innerHTML = '';
      }
    }

    estadoAlumnoEval = {
      formularioId: form.id,
      obligaciones: obligaciones,
      preguntas: form.preguntas || []
    };

    var cont = document.getElementById('edAlumnoBloques');
    var preguntas = form.preguntas || [];
    var bloques = groupByBloque(preguntas);

    // Modo prueba: conservar flujo anterior inline (no persiste).
    if (modoPrueba) {
      cont.innerHTML = obligaciones.map(function (ob) {
        var puede = !!ob.puedeResponder;
        var sub = (ob.asignaturaNombre || '') + ' · Grupo ' + (ob.grupoNombre || '');
        var estadoTxt = puede ? '<span class="badge bg-primary">Puedes responder</span>' : '<span class="badge bg-secondary">—</span>';
        var bloquesHtml = '';
        if (puede) {
          bloquesHtml = bloques.map(function (pair) {
            var bName = pair[0];
            var ps = pair[1] || [];
            var inputs = ps.map(function (p) { return renderPreguntaInput(p, ob.maestroId); }).join('');
            return '<div class="mb-3">' +
              '<div class="fw-semibold mb-2">' + escapeHtml(bName) + '</div>' +
              inputs +
              '</div>';
          }).join('');
          bloquesHtml += '<p class="small text-muted mb-0">Modo prueba: usa el botón inferior para validar respuestas (primer módulo listado).</p>';
        } else {
          bloquesHtml = '<p class="text-muted small mb-0">' + escapeHtml(ob.mensaje || '—') + '</p>';
        }
        return '<div class="card mb-3">' +
          '<div class="card-header py-2 d-flex flex-column gap-1">' +
          '<div><span class="fw-semibold">' + escapeHtml(ob.nombreCompleto || '') + '</span> ' + estadoTxt + '</div>' +
          '<div class="small text-muted">' + escapeHtml(sub) + '</div>' +
          '</div>' +
          '<div class="card-body">' + bloquesHtml + '</div>' +
          '</div>';
      }).join('');
      // Mantener el botón global de prueba si existe
      var btnGlobalAlumno = document.getElementById('edBtnEnviarEval');
      if (btnGlobalAlumno) {
        btnGlobalAlumno.classList.remove('d-none');
      }
      return;
    }

    // Flujo normal (Alumno): tarjetas para elegir y modal para responder 1x por módulo (horarioBloqueId).
    var modalEl = document.getElementById('edModalAlumnoResponder');
    var modalTituloEl = document.getElementById('edAlumnoModalTitulo');
    var modalSubEl = document.getElementById('edAlumnoModalSubtitulo');
    var modalDescEl = document.getElementById('edAlumnoModalDesc');
    var modalMsgEl = document.getElementById('edAlumnoModalMsg');
    var modalFormEl = document.getElementById('edAlumnoModalForm');
    var modalBtnEnviar = document.getElementById('edAlumnoModalBtnEnviar');
    var modalBtnCancelar = document.getElementById('edAlumnoModalBtnCancelar');
    var bsModal = getOrCreateStaticModal(modalEl);

    bindModalConfirmarCancelarOnce({
      modalEl: modalEl,
      bsModal: bsModal,
      cancelBtn: modalBtnCancelar
    });

    var currentOb = null;
    function setModalMsg(kind, text) {
      if (!modalMsgEl) return;
      modalMsgEl.classList.remove('d-none', 'alert-danger', 'alert-success', 'alert-info', 'alert-warning');
      modalMsgEl.classList.add(kind || 'alert-info');
      modalMsgEl.textContent = text || '';
    }
    function hideModalMsg() {
      if (!modalMsgEl) return;
      modalMsgEl.classList.add('d-none');
      modalMsgEl.textContent = '';
    }

    function renderFormForOb(ob) {
      if (!modalFormEl) return;
      var mid = ob.maestroId;
      modalFormEl.innerHTML = bloques.map(function (pair) {
        var bName = pair[0];
        var ps = pair[1] || [];
        var inputs = ps.map(function (p) { return renderPreguntaInput(p, mid); }).join('');
        return '<div class="mb-3">' +
          '<div class="fw-semibold mb-2">' + escapeHtml(bName) + '</div>' +
          inputs +
          '</div>';
      }).join('');
    }

    async function enviarModal() {
      if (!currentOb || !currentOb.horarioBloqueId || !currentOb.maestroId) return;
      var hid = currentOb.horarioBloqueId;
      var mid = currentOb.maestroId;
      var valores = [];
      // Importante: solo leer inputs dentro del modal
      (modalFormEl || document).querySelectorAll('.ed-likert[data-maestro="' + mid + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        var v = parseInt(inp.value, 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valor: isNaN(v) ? null : v });
      });
      (modalFormEl || document).querySelectorAll('.ed-abi[data-maestro="' + mid + '"]').forEach(function (ta) {
        var pr = parseInt(ta.getAttribute('data-pregunta'), 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (ta.value || '') });
      });
      (modalFormEl || document).querySelectorAll('.ed-om[data-maestro="' + mid + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (inp.value || '') });
      });
      var totalPreg = (preguntas && preguntas.length) ? preguntas.length : 0;
      if (totalPreg && valores.length !== totalPreg) {
        toastWarn('Responde todos los enunciados antes de guardar la evaluación.');
        return;
      }
      if (!modalBtnEnviar) return;
      modalBtnEnviar.disabled = true;
      hideModalMsg();
      try {
        var body = {
          formularioId: estadoAlumnoEval.formularioId,
          horarioBloqueId: parseInt(hid, 10),
          porMaestro: [{ maestroId: parseInt(mid, 10), valores: valores }]
        };
        await apiJson('POST', postUrl, body);
        // Guardado OK: cerrar sin confirmación y notificar por toast
        try { if (modalEl && modalEl.dataset) modalEl.dataset.edForceCloseOk = '1'; } catch (_) {}
        toastOk('Respuestas enviadas correctamente');
        try { if (bsModal) bsModal.hide(); } catch (_) {}
        onSuccess();
      } catch (err) {
        setModalMsg('alert-danger', errorMsg(err, 'Error al enviar.'));
      } finally {
        modalBtnEnviar.disabled = false;
      }
    }

    if (modalBtnEnviar) {
      modalBtnEnviar.onclick = function () { enviarModal(); };
    }
    if (modalEl) {
      modalEl.addEventListener('hidden.bs.modal', function () {
        currentOb = null;
        hideModalMsg();
        if (modalFormEl) modalFormEl.innerHTML = '';
      });
    }

    // Separar: disponibles vs próximas vs historial
    var pendientes = (obligaciones || []).filter(function (ob) { return ob && !ob.yaEvaluado && !!ob.puedeResponder; });
    var noDisponibles = (obligaciones || []).filter(function (ob) { return ob && !ob.yaEvaluado && !ob.puedeResponder; });
    var completadas = (obligaciones || []).filter(function (ob) { return ob && !!ob.yaEvaluado; });

    var resumenTxt = (pendientes.length > 0)
      ? ('Tienes ' + String(pendientes.length) + ' evaluación(es) disponible(s) para responder.')
      : '';

    function renderCard(ob) {
      var puede = !!ob.puedeResponder;
      var sub = (ob.asignaturaNombre || '—') + ' · Grupo ' + (ob.grupoNombre || '—');
      var infoFin = ob.instanteAperturaEvaluacion
        ? '<div class="small text-muted">Disponible desde: ' + (puede ? formatearFechaHoraEvalDocente(ob.instanteAperturaEvaluacion) : formatearFechaLargaEvalDocente(ob.instanteAperturaEvaluacion)) + '</div>'
        : (ob.fechaFinModulo ? '<div class="small text-muted">Fin de módulo en horario: ' + formatearFechaHoraEvalDocente(ob.fechaFinModulo) + '</div>' : '');
      var badge = ob.yaEvaluado
        ? '<span class="badge bg-success">Enviada</span>'
        : puede ? '<span class="badge bg-primary">Disponible</span>' : '<span class="badge bg-secondary">Aún no disponible</span>';
      var btn = '';
      if (ob.yaEvaluado) {
        btn = '<button type="button" class="btn btn-outline-success btn-sm" disabled>Enviada</button>';
      } else if (puede && ob.horarioBloqueId != null) {
        btn = '<button type="button" class="btn btn-ide btn-sm ed-btn-responder" data-horario="' + ob.horarioBloqueId + '" data-maestro="' + ob.maestroId + '">Responder</button>';
      } else {
        btn = '<button type="button" class="btn btn-outline-secondary btn-sm" disabled>Responder</button>';
      }
      var msgNo = (!puede && !ob.yaEvaluado) ? ('<div class="text-muted small mt-2">' + escapeHtml(limpiarMensajeDisponibilidadEvalDocente(ob.mensaje || '')) + '</div>') : '';
      return '' +
        '<div class="col-12 col-md-6 col-xl-4">' +
        '<div class="card h-100">' +
        '<div class="card-header py-2 d-flex flex-column gap-1">' +
        '<div class="d-flex align-items-center justify-content-between gap-2">' +
        '<div class="fw-semibold text-truncate">' + escapeHtml(ob.nombreCompleto || '—') + '</div>' +
        '<div>' + badge + '</div>' +
        '</div>' +
        '<div class="small text-muted">' + escapeHtml(sub) + '</div>' +
        infoFin +
        '</div>' +
        '<div class="card-body d-flex flex-column">' +
        (msgNo || '<div class="text-muted small">.</div>').replace('<div class="text-muted small">.</div>', '') +
        '<div class="mt-auto pt-2 d-flex justify-content-end">' + btn + '</div>' +
        '</div>' +
        '</div>' +
        '</div>';
    }

    var disponiblesHtml = pendientes.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mb-2">' +
        '<div class="fw-semibold">Disponibles</div>' +
        '<div class="small text-muted">' + escapeHtml(String(pendientes.length) + ' pendiente(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + pendientes.map(renderCard).join('') + '</div>')
      : ('<div class="fw-semibold mb-2">Disponibles</div>' +
        '<div class="alert alert-success py-2 small mb-3">Ya completaste tus evaluaciones disponibles.</div>');

    var noDispHtml = noDisponibles.length
      ? ('<div class="fw-semibold mt-3 mb-2">Próximamente</div>' +
        '<div class="row g-3">' + noDisponibles.map(renderCard).join('') + '</div>')
      : '';

    var historialHtml = completadas.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mt-3 mb-2">' +
        '<div class="fw-semibold">Historial</div>' +
        '<div class="small text-muted">' + escapeHtml(String(completadas.length) + ' enviada(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + completadas.map(renderCard).join('') + '</div>')
      : '';

    cont.innerHTML =
      (resumenTxt ? ('<div class="alert alert-info py-2 small mb-3">' + escapeHtml(resumenTxt) + '</div>') : '') +
      disponiblesHtml + noDispHtml + historialHtml;

    cont.querySelectorAll('.ed-btn-responder').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var hid = btn.getAttribute('data-horario');
        var ob = (estadoAlumnoEval.obligaciones || []).find(function (x) { return String(x.horarioBloqueId) === String(hid); });
        if (!ob || !ob.puedeResponder) return;
        currentOb = ob;
        hideModalMsg();
        if (modalTituloEl) modalTituloEl.textContent = form.titulo || 'Evaluación docente';
        if (modalSubEl) modalSubEl.textContent = (ob.nombreCompleto || '—') + ' · ' + (ob.asignaturaNombre || '—') + ' · Grupo ' + (ob.grupoNombre || '—');
        if (modalDescEl) modalDescEl.textContent = (form.descripcion || '') ? String(form.descripcion) : '';
        renderFormForOb(ob);
        try { if (bsModal) bsModal.show(); } catch (_) {}
      });
    });

    var btnGlobalAlumno = document.getElementById('edBtnEnviarEval');
    if (btnGlobalAlumno) {
      if (modoPrueba) btnGlobalAlumno.classList.remove('d-none');
      else btnGlobalAlumno.classList.add('d-none');
    }

    if (modoPrueba) {
      document.getElementById('edBtnEnviarEval').onclick = async function () {
        var primera = obligaciones.find(function (o) { return o.puedeResponder; });
        if (!primera) {
          alert('No hay un módulo disponible para prueba.');
          return;
        }
        var mid = primera.maestroId;
        var valores = [];
        cont.querySelectorAll('.ed-likert[data-maestro="' + mid + '"]:checked').forEach(function (inp) {
          var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
          var v = parseInt(inp.value, 10);
          if (!isNaN(pr)) valores.push({ preguntaId: pr, valor: isNaN(v) ? null : v });
        });
        cont.querySelectorAll('.ed-abi[data-maestro="' + mid + '"]').forEach(function (ta) {
          var pr = parseInt(ta.getAttribute('data-pregunta'), 10);
          if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (ta.value || '') });
        });
        cont.querySelectorAll('.ed-om[data-maestro="' + mid + '"]:checked').forEach(function (inp) {
          var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
          if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (inp.value || '') });
        });
        try {
          var bodyPrueba = {
            formularioId: estadoAlumnoEval.formularioId,
            porMaestro: [{ maestroId: mid, valores: valores }]
          };
          if (primera.horarioBloqueId != null) bodyPrueba.horarioBloqueId = primera.horarioBloqueId;
          await apiJson('POST', postUrl, bodyPrueba);
          alert('Validación de prueba enviada.');
          onSuccess();
        } catch (err) {
          alert(errorMsg(err, 'Error al enviar.'));
        }
      };
    }
  }

  async function cargarPanelProbarFormularioAdmin(formularioId) {
    // Ocultar admin y mostrar panel alumno reutilizado (sin guardar respuestas)
    document.getElementById('panelAdminSecretaria').classList.add('d-none');
    document.getElementById('panelMaestro').classList.add('d-none');
    document.getElementById('evalSinPermiso').classList.add('d-none');
    document.getElementById('panelAlumno').classList.remove('d-none');
    var msg = document.getElementById('edAlumnoMensaje');
    var formDiv = document.getElementById('edAlumnoFormulario');
    var acciones = document.getElementById('edAlumnoAcciones');
    try {
      msg.classList.remove('d-none', 'alert-warning', 'alert-success');
      msg.classList.add('alert-info');
      msg.textContent = 'Cargando…';
      formDiv.classList.add('d-none');
      var ctx = await authFetch('/evaluaciones-docente/prueba/contexto?formularioId=' + encodeURIComponent(String(formularioId)), { method: 'GET' });
      msg.classList.add('d-none');
      var volver = function () {
        document.getElementById('panelAlumno').classList.add('d-none');
        document.getElementById('panelAdminSecretaria').classList.remove('d-none');
        var top = document.getElementById('evalDocenteRoot');
        if (top) top.scrollIntoView({ behavior: 'smooth', block: 'start' });
      };
      await renderPanelAlumnoDesdeContexto(ctx, {
        modoPrueba: true,
        postUrl: '/evaluaciones-docente/prueba/responder',
        onCancel: function () { volver(); },
        onSuccess: function () {
          // Volvemos al listado al terminar
          volver();
          cargarPanelAdmin();
        }
      });
    } catch (e) {
      msg.classList.remove('d-none', 'alert-success', 'alert-info');
      msg.classList.add('alert-warning');
      msg.textContent = errorMsg(e, 'No se pudo cargar.');
      formDiv.classList.add('d-none');
    }
  }

  function renderMaestroResultadosHtml(data) {
    if (!data || !data.formulario) {
      return '<p class="text-muted">' + escapeHtml((data && data.mensaje) || 'Sin datos.') + '</p>';
    }
    if (data.puedeVerResultados === false) {
      return '<div class="alert alert-warning mb-0">' + escapeHtml(data.mensaje || 'No puedes consultar estos resultados aún.') + '</div>';
    }
    var stats = data.estadisticas || [];
    var txt = data.respuestasTexto || [];
    var tieneSemaforo = stats.some(function (s) { return s && s.semaforo; });
    function badgeSemaforo(s) {
      if (!s || !s.semaforo) return '—';
      var interp = s.interpretacionBrecha ? String(s.interpretacionBrecha) : '';
      var cls = s.semaforo === 'VERDE' ? 'bg-success'
        : (s.semaforo === 'AMARILLO' ? 'bg-warning text-dark' : (s.semaforo === 'ROJO' ? 'bg-danger' : 'bg-secondary'));
      var icon = s.semaforo === 'VERDE' ? '🟢' : (s.semaforo === 'AMARILLO' ? '🟡' : (s.semaforo === 'ROJO' ? '🔴' : ''));
      return '<span class="badge ' + cls + '" title="' + escapeHtml(interp) + '">' + icon + ' ' + escapeHtml(interp) + '</span>';
    }
    var theadCols = '<th>Pregunta</th><th class="text-center">Promedio alumnos (1–5)</th>';
    if (tieneSemaforo) {
      theadCols += '<th class="text-center">Autoeval.</th><th class="text-center">Diferencia</th><th class="text-center">Brecha</th>';
    }
    var html =
      '<p class="fw-medium">' + escapeHtml(data.formulario.titulo || '') + '</p>' +
      '<p class="small">Respuestas de estudiantes consideradas: <strong>' + (data.totalRespuestasAlumno != null ? data.totalRespuestasAlumno : '—') + '</strong></p>' +
      '<div class="table-responsive"><table class="table table-sm align-middle"><thead><tr>' + theadCols + '</tr></thead><tbody>' +
      stats.map(function (s) {
        var row = '<td>' + escapeHtml(s.preguntaTexto) + '</td>' +
          '<td class="text-center">' + (s.promedio != null ? escapeHtml(String(s.promedio)) : '—') + '</td>';
        if (tieneSemaforo) {
          row += '<td class="text-center">' + (s.valorAutoevaluacion != null ? likert5ValorTexto(s.valorAutoevaluacion) : '—') + '</td>' +
            '<td class="text-center">' + (s.diferenciaVsAlumnos != null ? escapeHtml(String(s.diferenciaVsAlumnos)) : '—') + '</td>' +
            '<td class="text-center">' + badgeSemaforo(s) + '</td>';
        }
        return '<tr>' + row + '</tr>';
      }).join('') + '</tbody></table></div>';
    if (Array.isArray(txt) && txt.length) {
      html += '<hr class="my-3">' +
        '<h6 class="mb-2">Respuestas abiertas (anónimas)</h6>' +
        txt.map(function (b) {
          var rs = Array.isArray(b.respuestas) ? b.respuestas : [];
          return '<div class="mb-3">' +
            '<div class="fw-semibold mb-1">' + escapeHtml(b.preguntaTexto || '—') + '</div>' +
            (rs.length ? rs.map(function (t) {
              return '<div class="border rounded-3 p-2 mb-2 bg-light">' + escapeHtml(String(t)) + '</div>';
            }).join('') : '<div class="text-muted small">Sin respuestas.</div>') +
            '</div>';
        }).join('');
    }
    return html;
  }

  async function cargarPanelMaestro() {
    document.getElementById('panelMaestro').classList.remove('d-none');
    bindMaestroResultadosTabBadgeOnce();
    if (typeof window.refreshMaestroInformesEvalDocenteUi === 'function') {
      await window.refreshMaestroInformesEvalDocenteUi();
    }
    var box = document.getElementById('edMaestroResultados');
    box.innerHTML = '<p class="text-muted">Cargando…</p>';
    var ctx = null;
    try {
      ctx = await authFetch('/evaluaciones-docente/autoevaluacion/contexto', { method: 'GET' });
    } catch (_) {}
    var obligaciones = (ctx && ctx.obligaciones) ? ctx.obligaciones : [];
    var informesPorHorario = {};
    try {
      var listaInf = await authFetch('/evaluaciones-docente/maestro/informes-academicos', { method: 'GET' });
      (listaInf || []).forEach(function (row) {
        if (row && row.horarioBloqueId != null) {
          informesPorHorario[String(row.horarioBloqueId)] = row;
        }
      });
    } catch (_) {}
    box.innerHTML =
      '<div class="row g-3">' +
      '<div class="col-12 col-lg-5">' +
      '<div class="card h-100">' +
      '<div class="card-header bg-soft-primary">Por grupo / módulo</div>' +
      '<div class="card-body">' +
      '<div class="text-muted small mb-3">Consulta resultados agregados o el informe institucional cuando esté publicado. Los promedios se muestran cuando todos los estudiantes del módulo hayan respondido.</div>' +
      '<div id="edMaestroResultadosCards" class="row g-3"></div>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '<div class="col-12 col-lg-7">' +
      '<div class="card h-100">' +
      '<div class="card-header bg-soft-primary">' +
      '<span id="edMaestroResultadosTitle">Resultados e informe</span>' +
      '</div>' +
      '<div class="card-body" id="edMaestroResultadosInner"><p class="text-muted mb-0">Selecciona un grupo y una opción a la izquierda.</p></div>' +
      '</div>' +
      '</div>' +
      '</div>';

    var cardsWrap = document.getElementById('edMaestroResultadosCards');
    var inner = document.getElementById('edMaestroResultadosInner');
    var title = document.getElementById('edMaestroResultadosTitle');

    function tieneTextoInforme(row) {
      return row && row.informeParaDocente != null && String(row.informeParaDocente).trim() !== '';
    }

    async function mostrarInformeInstitucionalEnColumna(row, tituloPanel) {
      if (!inner || !row || !tieneTextoInforme(row)) return;
      var txt = String(row.informeParaDocente || '');
      if (title) title.textContent = tituloPanel || 'Informe institucional';
      inner.innerHTML =
        '<div class="ed-maestro-informe-vista text-dark">' +
        '<p class="small text-muted mb-2">Texto elaborado por la institución. La lectura queda registrada para fines administrativos.</p>' +
        '<div class="border rounded-3 p-3 bg-light small" style="white-space:pre-wrap;">' + escapeHtml(txt) + '</div>' +
        '</div>';
      if (row.informeSinLeer && row.respuestaAcademicaId != null) {
        try {
          await authFetch(
            '/evaluaciones-docente/maestro/informes-academicos/' + encodeURIComponent(String(row.respuestaAcademicaId)) + '/marcar-leido',
            { method: 'POST' }
          );
          row.informeSinLeer = false;
          if (typeof window.refreshMaestroInformesEvalDocenteUi === 'function') {
            await window.refreshMaestroInformesEvalDocenteUi();
          }
          renderCards();
        } catch (_) {}
      }
    }

    function renderCards() {
      if (!cardsWrap) return;
      if (!obligaciones.length) {
        cardsWrap.innerHTML = '<div class="col-12"><div class="text-muted small">No se encontraron grupos en tu horario.</div></div>';
        return;
      }
      cardsWrap.innerHTML = obligaciones.map(function (o) {
        var id = o.horarioBloqueId;
        var g = o.grupoNombre || '—';
        var a = o.asignaturaNombre || '—';
        var lineaGrupo = 'Grupo ' + g;
        var tituloPanel = a + ' · ' + lineaGrupo;
        var inf = id != null ? informesPorHorario[String(id)] : null;
        var hayInforme = tieneTextoInforme(inf);
        var dotInf = hayInforme && inf.informeSinLeer
          ? '<span class="ed-informe-btn-dot" aria-hidden="true"></span>'
          : '';
        var btnInforme = hayInforme
          ? ('<span class="btn-ed-informe-leer-wrap d-inline-block">' +
            '<button type="button" class="btn btn-ide btn-sm ed-btn-informe-res" data-hid="' + escapeHtml(String(id)) + '" data-title="' + escapeHtml(tituloPanel) + '">Informe</button>' +
            dotInf +
            '</span>')
          : '';
        return '' +
          '<div class="col-12">' +
          '<div class="card">' +
          '<div class="card-body">' +
          '<div class="fw-semibold text-truncate" title="' + escapeHtml(a) + '">' + escapeHtml(a) + '</div>' +
          '<div class="small text-muted fw-normal text-truncate" title="' + escapeHtml(lineaGrupo) + '">' + escapeHtml(lineaGrupo) + '</div>' +
          '<div class="mt-3 d-flex flex-wrap gap-2 justify-content-end align-items-center">' +
          btnInforme +
          '<button type="button" class="btn btn-ide btn-sm ed-btn-ver-res" data-hid="' + escapeHtml(String(id)) + '" data-title="' + escapeHtml(tituloPanel) + '">Ver resultados</button>' +
          '</div>' +
          '</div>' +
          '</div>' +
          '</div>';
      }).join('');
    }

    async function cargarResultadosInner(hid) {
      if (!inner) return;
      inner.innerHTML = '<p class="text-muted small">Cargando…</p>';
      try {
        var q = hid ? ('?horarioBloqueId=' + encodeURIComponent(String(hid))) : '';
        var data = await authFetch('/evaluaciones-docente/maestro/resultados' + q, { method: 'GET' });
        inner.innerHTML = renderMaestroResultadosHtml(data);
      } catch (e) {
        inner.innerHTML = '<p class="text-danger">' + escapeHtml(e.message) + '</p>';
      }
    }

    renderCards();
    if (cardsWrap) {
      cardsWrap.querySelectorAll('.ed-btn-ver-res').forEach(function (b) {
        b.addEventListener('click', function () {
          var hid = b.getAttribute('data-hid');
          var t = b.getAttribute('data-title') || 'Resultados';
          if (title) title.textContent = t;
          cargarResultadosInner(hid ? parseInt(hid, 10) : null);
          try { if (box) box.scrollIntoView({ behavior: 'smooth', block: 'start' }); } catch (_) {}
        });
      });
      cardsWrap.querySelectorAll('.ed-btn-informe-res').forEach(function (b) {
        b.addEventListener('click', function () {
          var hid = b.getAttribute('data-hid');
          var t = b.getAttribute('data-title') || 'Informe';
          var rowInf = hid != null ? informesPorHorario[String(hid)] : null;
          if (!rowInf || !tieneTextoInforme(rowInf)) return;
          if (title) title.textContent = t + ' — Informe';
          mostrarInformeInstitucionalEnColumna(rowInf, t + ' — Informe');
          try { if (box) box.scrollIntoView({ behavior: 'smooth', block: 'start' }); } catch (_) {}
        });
      });
    }

    try {
      await cargarAutoevaluacionMaestro();
    } catch (_) {}
  }

  async function cargarAutoevaluacionMaestro() {
    var card = document.getElementById('edAutoevalCard');
    var msg = document.getElementById('edAutoevalMsg');
    var formDiv = document.getElementById('edAutoevalForm');
    var cont = document.getElementById('edAutoevalBloques');
    var btn = document.getElementById('edBtnEnviarAutoeval');
    var tituloEl = document.getElementById('edAutoevalTitulo');
    if (!card || !msg || !formDiv || !cont || !btn) return;

    // Mostrar estado cargando
    msg.classList.remove('d-none', 'alert-warning', 'alert-success');
    msg.classList.add('alert-info');
    msg.textContent = 'Cargando…';
    formDiv.classList.add('d-none');

    var ctx = await authFetch('/evaluaciones-docente/autoevaluacion/contexto', { method: 'GET' });
    var form = ctx && ctx.formulario;
    if (!form) {
      msg.classList.remove('d-none', 'alert-warning', 'alert-success');
      msg.classList.add('alert-info');
      msg.textContent = (ctx && ctx.mensaje) ? String(ctx.mensaje) : 'No hay una autoevaluación vigente.';
      formDiv.classList.add('d-none');
      return;
    }
    var obligaciones = ctx.obligaciones || [];
    var pendOc = ctx.pendientesEvaluacion != null ? Number(ctx.pendientesEvaluacion) : 0;

    if (!obligaciones.length) {
      msg.classList.remove('d-none', 'alert-success', 'alert-info');
      msg.classList.add('alert-warning');
      msg.textContent = 'No hay módulos en tu horario docente para autoevaluación.';
      formDiv.classList.add('d-none');
      if (btn) btn.classList.add('d-none');
      return;
    }

    msg.classList.add('d-none');
    formDiv.classList.remove('d-none');
    if (btn) btn.classList.add('d-none'); // se envía por modal (por módulo)
    if (tituloEl) tituloEl.textContent = form.titulo || 'Autoevaluación';

    var maestro = ctx && ctx.maestro ? ctx.maestro : null;
    var maestroId = maestro && maestro.id != null ? maestro.id : null;

    var preguntas = form.preguntas || [];
    var bloques = groupByBloque(preguntas);

    // UI (Docente): tarjetas + modal (igual que alumno)
    var modalEl = document.getElementById('edModalMaestroAutoeval');
    var modalTituloEl = document.getElementById('edMaestroAutoevalModalTitulo');
    var modalSubEl = document.getElementById('edMaestroAutoevalModalSubtitulo');
    var modalDescEl = document.getElementById('edMaestroAutoevalModalDesc');
    var modalMsgEl = document.getElementById('edMaestroAutoevalModalMsg');
    var modalFormEl = document.getElementById('edMaestroAutoevalModalForm');
    var modalBtnEnviar = document.getElementById('edMaestroAutoevalModalBtnEnviar');
    var modalBtnCancelar = document.getElementById('edMaestroAutoevalModalBtnCancelar');
    var bsModal = getOrCreateStaticModal(modalEl);

    bindModalConfirmarCancelarOnce({
      modalEl: modalEl,
      bsModal: bsModal,
      cancelBtn: modalBtnCancelar
    });

    function setModalMsg(kind, text) {
      if (!modalMsgEl) return;
      modalMsgEl.classList.remove('d-none', 'alert-danger', 'alert-success', 'alert-info', 'alert-warning');
      modalMsgEl.classList.add(kind || 'alert-info');
      modalMsgEl.textContent = text || '';
    }
    function hideModalMsg() {
      if (!modalMsgEl) return;
      modalMsgEl.classList.add('d-none');
      modalMsgEl.textContent = '';
    }

    var currentOb = null;

    function renderFormForOb(ob) {
      if (!modalFormEl || maestroId == null) return;
      modalFormEl.innerHTML = bloques.map(function (pair) {
        var bName = pair[0];
        var ps = pair[1] || [];
        var inputs = ps.map(function (p) { return renderPreguntaInput(p, maestroId); }).join('');
        return '<div class="mb-3">' +
          '<div class="fw-semibold mb-2">' + escapeHtml(bName) + '</div>' +
          inputs +
          '</div>';
      }).join('');
    }

    async function enviarModal() {
      if (!currentOb || !currentOb.horarioBloqueId || maestroId == null) return;
      if (!modalBtnEnviar) return;
      var hid = currentOb.horarioBloqueId;

      var valores = [];
      (modalFormEl || document).querySelectorAll('.ed-likert[data-maestro="' + maestroId + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        var v = parseInt(inp.value, 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valor: isNaN(v) ? null : v });
      });
      (modalFormEl || document).querySelectorAll('.ed-abi[data-maestro="' + maestroId + '"]').forEach(function (ta) {
        var pr = parseInt(ta.getAttribute('data-pregunta'), 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (ta.value || '') });
      });
      (modalFormEl || document).querySelectorAll('.ed-om[data-maestro="' + maestroId + '"]:checked').forEach(function (inp) {
        var pr = parseInt(inp.getAttribute('data-pregunta'), 10);
        if (!isNaN(pr)) valores.push({ preguntaId: pr, valorTexto: (inp.value || '') });
      });
      var totalPreg = (preguntas && preguntas.length) ? preguntas.length : 0;
      if (totalPreg && valores.length !== totalPreg) {
        toastWarn('Responde todos los enunciados antes de guardar la evaluación.');
        return;
      }

      modalBtnEnviar.disabled = true;
      hideModalMsg();
      try {
        await apiJson('POST', '/evaluaciones-docente/autoevaluacion/responder', {
          formularioId: form.id,
          horarioBloqueId: parseInt(hid, 10),
          porMaestro: [{ maestroId: maestroId, valores: valores }]
        });
        // Guardado OK: cerrar sin confirmación y notificar por toast
        try { if (modalEl && modalEl.dataset) modalEl.dataset.edForceCloseOk = '1'; } catch (_) {}
        toastOk('Respuestas enviadas correctamente');
        try { if (bsModal) bsModal.hide(); } catch (_) {}
        cargarAutoevaluacionMaestro();
      } catch (err) {
        setModalMsg('alert-danger', errorMsg(err, 'Error al enviar.'));
      } finally {
        modalBtnEnviar.disabled = false;
      }
    }

    if (modalBtnEnviar) modalBtnEnviar.onclick = function () { enviarModal(); };
    if (modalEl) {
      modalEl.addEventListener('hidden.bs.modal', function () {
        currentOb = null;
        hideModalMsg();
        if (modalFormEl) modalFormEl.innerHTML = '';
      });
    }

    // Separar por estado: pendientes (disponibles) vs completadas (histórico)
    var pendientes = (obligaciones || []).filter(function (ob) { return ob && !ob.yaEvaluado && !!ob.puedeResponder; });
    var noDisponibles = (obligaciones || []).filter(function (ob) { return ob && !ob.yaEvaluado && !ob.puedeResponder; });
    var completadas = (obligaciones || []).filter(function (ob) { return ob && !!ob.yaEvaluado; });

    var resumenTxt = (pendientes.length > 0)
      ? ('Tienes ' + String(pendientes.length) + ' autoevaluación(es) disponible(s) para responder.')
      : '';

    function renderCard(ob) {
        var puede = !!ob.puedeResponder;
        var sub = (ob.asignaturaNombre || '—') + ' · Grupo ' + (ob.grupoNombre || '—');
        var infoFin = ob.fechaFinModulo
          ? '<div class="small text-muted">Fin de módulo: ' + formatearFechaHoraEvalDocente(ob.fechaFinModulo) + '</div>'
          : (ob.instanteAperturaEvaluacion ? '<div class="small text-muted">Disponible desde: ' + (puede ? formatearFechaHoraEvalDocente(ob.instanteAperturaEvaluacion) : formatearFechaLargaEvalDocente(ob.instanteAperturaEvaluacion)) + '</div>' : '');
        var badge = ob.yaEvaluado
          ? '<span class="badge bg-success">Enviada</span>'
          : puede ? '<span class="badge bg-primary">Disponible</span>' : '<span class="badge bg-secondary">Aún no disponible</span>';

        var btnHtml = '';
        if (ob.yaEvaluado) {
          btnHtml = '<button type="button" class="btn btn-outline-success btn-sm" disabled>Enviada</button>';
        } else if (puede && ob.horarioBloqueId != null && maestroId != null) {
          btnHtml = '<button type="button" class="btn btn-ide btn-sm ed-btn-responder-autoeval" data-horario="' + ob.horarioBloqueId + '">Responder</button>';
        } else {
          btnHtml = '<button type="button" class="btn btn-outline-secondary btn-sm" disabled>Responder</button>';
        }
        var msgNo = (!puede && !ob.yaEvaluado) ? ('<div class="text-muted small mt-2">' + escapeHtml(limpiarMensajeDisponibilidadEvalDocente(ob.mensaje || '')) + '</div>') : '';

        return '' +
          '<div class="col-12 col-md-6 col-xl-4">' +
          '<div class="card h-100">' +
          '<div class="card-header py-2 d-flex flex-column gap-1">' +
          '<div class="d-flex align-items-center justify-content-between gap-2">' +
        '<div class="fw-semibold text-truncate">' + escapeHtml(ob.asignaturaNombre || 'Autoevaluación') + '</div>' +
          '<div>' + badge + '</div>' +
          '</div>' +
        '<div class="small text-muted">' + escapeHtml('Grupo ' + (ob.grupoNombre || '—')) + '</div>' +
          infoFin +
          '</div>' +
          '<div class="card-body d-flex flex-column">' +
          (msgNo || '<div class="text-muted small">.</div>').replace('<div class="text-muted small">.</div>', '') +
          '<div class="mt-auto pt-2 d-flex justify-content-end">' + btnHtml + '</div>' +
          '</div>' +
          '</div>' +
          '</div>';
    }

    var pendientesHtml = pendientes.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mb-2">' +
        '<div class="fw-semibold">Disponibles</div>' +
        '<div class="small text-muted">' + escapeHtml(String(pendientes.length) + ' pendiente(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + pendientes.map(renderCard).join('') + '</div>')
      : ('<div class="fw-semibold mb-2">Disponibles</div>' +
        '<div class="alert alert-success py-2 small mb-3">Ya completaste tus autoevaluaciones disponibles.</div>');

    var noDispHtml = noDisponibles.length
      ? ('<div class="fw-semibold mt-3 mb-2">Próximamente</div>' +
        '<div class="row g-3">' + noDisponibles.map(renderCard).join('') + '</div>')
      : '';

    var completadasHtml = completadas.length
      ? ('<div class="d-flex align-items-baseline justify-content-between mt-3 mb-2">' +
        '<div class="fw-semibold">Historial</div>' +
        '<div class="small text-muted">' + escapeHtml(String(completadas.length) + ' enviada(s)') + '</div>' +
        '</div>' +
        '<div class="row g-3">' + completadas.map(renderCard).join('') + '</div>')
      : '';

    cont.innerHTML =
      (resumenTxt ? ('<div class="alert alert-info py-2 small mb-3">' + escapeHtml(resumenTxt) + '</div>') : '') +
      pendientesHtml + noDispHtml + completadasHtml;

    cont.querySelectorAll('.ed-btn-responder-autoeval').forEach(function (b) {
      b.addEventListener('click', function () {
        var hid = b.getAttribute('data-horario');
        var ob = (obligaciones || []).find(function (x) { return String(x.horarioBloqueId) === String(hid); });
        if (!ob || !ob.puedeResponder || maestroId == null) return;
        currentOb = ob;
        hideModalMsg();
        if (modalTituloEl) modalTituloEl.textContent = form.titulo || 'Autoevaluación';
        if (modalSubEl) modalSubEl.textContent = (ob.asignaturaNombre || '—') + ' · Grupo ' + (ob.grupoNombre || '—');
        if (modalDescEl) modalDescEl.textContent = (form.descripcion || '') ? String(form.descripcion) : '';
        renderFormForOb(ob);
        try { if (bsModal) bsModal.show(); } catch (_) {}
      });
    });
  }

  window.initEvaluacionDocentePage = function () {
    bindInformeAcademicoModalOnce();
    var r = rol();
    document.getElementById('evalSinPermiso').classList.add('d-none');
    if (r === 'ADMIN' || r === 'SECRETARIA_ACADEMICA') {
      cargarPanelAdmin();
    } else if (r === 'COORDINADOR_ACADEMICO') {
      var panel = document.getElementById('panelAdminSecretaria');
      if (panel) panel.classList.remove('d-none');
      var tabsUl = document.getElementById('edAdminTabs');
      if (tabsUl) tabsUl.classList.add('d-none');
      ['edTabLista', 'edTabCrear', 'edTabResultados'].forEach(function (tid) {
        var el = document.getElementById(tid);
        if (el) el.classList.remove('show', 'active');
      });
      var tAplic = document.getElementById('edTabAplicar');
      if (tAplic) tAplic.classList.add('show', 'active');
      refrescarPanelAplicarSecretaria();
    } else if (r === 'ALUMNO') {
      cargarPanelAlumno();
    } else if (r === 'MAESTRO') {
      cargarPanelMaestro();
    } else {
      document.getElementById('evalSinPermiso').classList.remove('d-none');
    }
  };
})();

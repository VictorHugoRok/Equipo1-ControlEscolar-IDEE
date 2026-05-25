(function () {
  'use strict';

  var API = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
  var cohortes = [];
  var programasCache = [];
  var cohorteMiembrosActualId = null;
  var candidatosCache = [];
  var miembrosInicialesIds = [];

  function headers(json) {
    var h = {};
    var token = localStorage.getItem('token');
    if (token) h['Authorization'] = 'Bearer ' + token;
    if (json) h['Content-Type'] = 'application/json';
    return h;
  }

  async function api(path, opts) {
    var res = await fetch(API + path, Object.assign({}, opts || {}, { headers: Object.assign(headers(!(opts && opts.body instanceof FormData)), (opts && opts.headers) || {}) }));
    if (!res.ok) {
      var msg = 'Error de servidor';
      try {
        var d = await res.json();
        msg = d.error || d.message || msg;
      } catch (_) {}
      throw new Error(msg);
    }
    if (res.status === 204) return null;
    return res.json();
  }

  function renderTabla() {
    var tbody = document.getElementById('cohortesTableBody');
    if (!tbody) return;
    if (!cohortes || cohortes.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay cohortes registradas.</td></tr>';
      return;
    }
    tbody.innerHTML = cohortes.map(function (c) {
      return '<tr>' +
        '<td><strong>' + escapeHtml(c.idCohorte || '—') + '</strong></td>' +
        '<td>' + escapeHtml(c.nombre || '—') + '</td>' +
        '<td>' + escapeHtml(c.programaNombre || '—') + '</td>' +
        '<td>' + escapeHtml(c.descripcion || '—') + '</td>' +
        '<td><button type="button" class="btn btn-sm btn-outline-primary" data-action="ver-miembros" data-id="' + c.id + '" title="Ver miembros de la cohorte">' + (c.tamano || 0) + '</button></td>' +
        '<td class="text-nowrap">' +
          '<div class="btn-group btn-group-sm">' +
            '<button class="btn btn-outline-primary" data-action="miembros" data-id="' + c.id + '" title="Agregar miembros"><i class="bi bi-person-plus"></i></button>' +
            '<button class="btn btn-outline-secondary" data-action="editar" data-id="' + c.id + '" title="Editar"><i class="bi bi-pencil"></i></button>' +
            '<button class="btn btn-outline-danger" data-action="eliminar" data-id="' + c.id + '" title="Eliminar"><i class="bi bi-trash"></i></button>' +
          '</div>' +
        '</td>' +
      '</tr>';
    }).join('');
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  async function cargarCohortes() {
    cohortes = await api('/cohortes');
    renderTabla();
  }

  async function cargarProgramas() {
    try {
      programasCache = await api('/programas-educativos');
    } catch (_) {
      programasCache = [];
    }
    if (!Array.isArray(programasCache)) programasCache = [];

    var sel = document.getElementById('cohorteProgramaId');
    if (!sel) return;
    var prev = sel.value;
    sel.innerHTML = '<option value="">Selecciona programa…</option>';
    programasCache.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = (p.nombre || p.clave || ('Programa ' + p.id));
      sel.appendChild(opt);
    });
    if (prev) sel.value = prev;
  }

  function limpiarForm() {
    document.getElementById('cohorteIdInterno').value = '';
    if (document.getElementById('cohorteProgramaId')) document.getElementById('cohorteProgramaId').value = '';
    document.getElementById('cohorteIdCohorte').value = '';
    document.getElementById('cohorteNombre').value = '';
    document.getElementById('cohorteDescripcion').value = '';
    document.getElementById('btnGuardarCohorte').textContent = 'Guardar cohorte';
    document.getElementById('btnCancelarEdicionCohorte').classList.add('d-none');
  }

  async function guardarCohorte() {
    var id = document.getElementById('cohorteIdInterno').value;
    var programaId = (document.getElementById('cohorteProgramaId') && document.getElementById('cohorteProgramaId').value) || '';
    var body = {
      programaId: programaId ? parseInt(programaId, 10) : null,
      idCohorte: (document.getElementById('cohorteIdCohorte').value || '').trim(),
      nombre: (document.getElementById('cohorteNombre').value || '').trim(),
      descripcion: (document.getElementById('cohorteDescripcion').value || '').trim()
    };
    if (!body.programaId || !body.idCohorte || !body.nombre) {
      alert('Programa, ID Cohorte y Nombre son obligatorios.');
      return;
    }
    if (id) {
      await api('/cohortes/' + id, { method: 'PUT', body: JSON.stringify(body) });
      alert('Cohorte actualizada correctamente.');
    } else {
      var resp = await api('/cohortes', { method: 'POST', body: JSON.stringify(body) });
      alert('Cohorte creada correctamente.');
      // Si el backend devuelve el id, ofrecer abrir miembros inmediatamente
      if (resp && resp.id) {
        try {
          await cargarCohortes();
          await abrirMiembros(resp.id);
        } catch (err) {
          console.error(err);
          alert(err.message || 'Cohorte creada, pero no se pudo abrir el modal de miembros.');
        }
        limpiarForm();
        return;
      }
    }
    limpiarForm();
    await cargarCohortes();
  }

  function editarCohorte(id) {
    var c = (cohortes || []).find(function (x) { return String(x.id) === String(id); });
    if (!c) return;
    document.querySelector('[data-bs-target="#tabAltaCohorte"]').click();
    document.getElementById('cohorteIdInterno').value = c.id;
    if (document.getElementById('cohorteProgramaId')) document.getElementById('cohorteProgramaId').value = c.programaId != null ? String(c.programaId) : '';
    document.getElementById('cohorteIdCohorte').value = c.idCohorte || '';
    document.getElementById('cohorteNombre').value = c.nombre || '';
    document.getElementById('cohorteDescripcion').value = c.descripcion || '';
    document.getElementById('btnGuardarCohorte').textContent = 'Actualizar cohorte';
    document.getElementById('btnCancelarEdicionCohorte').classList.remove('d-none');
  }

  async function eliminarCohorte(id) {
    if (!confirm('¿Eliminar cohorte? Se desasignarán sus miembros.')) return;
    await api('/cohortes/' + id, { method: 'DELETE' });
    await cargarCohortes();
  }

  function renderMiembrosTabla() {
    var tbody = document.getElementById('miembrosCohorteBody');
    var txt = document.getElementById('buscarMiembroCohorteInput');
    if (!tbody) return;
    var q = (txt && txt.value ? txt.value : '').toLowerCase().trim();
    var candidatos = (candidatosCache || []).filter(function (a) {
      if (!q) return true;
      var blob = ((a.matricula || '') + ' ' + (a.nombre || '') + ' ' + (a.programaNombre || '')).toLowerCase();
      return blob.indexOf(q) !== -1;
    });
    if (!candidatos.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Sin resultados</td></tr>';
      return;
    }
    tbody.innerHTML = candidatos.map(function (a) {
      var ids = Array.isArray(a.cohorteIds) ? a.cohorteIds.map(String) : [];
      var checked = cohorteMiembrosActualId != null && ids.indexOf(String(cohorteMiembrosActualId)) !== -1 ? 'checked' : '';
      var cohortesTxt = '';
      if (Array.isArray(a.cohortesNombres) && a.cohortesNombres.length) {
        cohortesTxt = a.cohortesNombres.join(', ');
      } else if (Array.isArray(a.cohortes) && a.cohortes.length) {
        cohortesTxt = a.cohortes.map(function (c) { return c && (c.nombre || ('Cohorte ' + c.id)); }).filter(Boolean).join(', ');
      }
      return '<tr>' +
        '<td><input type="checkbox" class="form-check-input cohorte-miembro-check" value="' + a.id + '" ' + checked + '></td>' +
        '<td>' + escapeHtml(a.matricula || '') + '</td>' +
        '<td>' + escapeHtml(a.nombre || '') + '</td>' +
        '<td>' + escapeHtml(a.programaNombre || '—') + '</td>' +
        '<td>' + (cohortesTxt ? '<span class="badge text-bg-secondary">' + escapeHtml(cohortesTxt) + '</span>' : '<span class="text-muted">—</span>') + '</td>' +
      '</tr>';
    }).join('');
    actualizarEstadoCheckAllMiembros();
  }

  function actualizarEstadoCheckAllMiembros() {
    var checkAll = document.getElementById('miembrosCohorteCheckAll');
    var checks = Array.from(document.querySelectorAll('#miembrosCohorteBody .cohorte-miembro-check'));
    if (!checkAll) return;
    if (!checks.length) {
      checkAll.checked = false;
      checkAll.indeterminate = false;
      return;
    }
    var marcados = checks.filter(function (c) { return c.checked; }).length;
    checkAll.checked = marcados > 0 && marcados === checks.length;
    checkAll.indeterminate = marcados > 0 && marcados < checks.length;
  }

  function obtenerBootstrapModal() {
    var root = typeof window !== 'undefined' && window.bootstrap ? window.bootstrap : (typeof bootstrap !== 'undefined' ? bootstrap : null);
    return root && root.Modal ? root.Modal : null;
  }

  async function abrirMiembros(id) {
    try {
      cohorteMiembrosActualId = id != null ? String(id) : null;
      candidatosCache = await api('/cohortes/' + encodeURIComponent(String(id)) + '/candidatos');
      if (!Array.isArray(candidatosCache)) candidatosCache = [];
      // Capturar estado inicial (quiénes ya eran miembros) para enviar cambios explícitos (add/remove).
      miembrosInicialesIds = (candidatosCache || [])
        .filter(function (a) {
          var ids = Array.isArray(a.cohorteIds) ? a.cohorteIds.map(String) : [];
          return cohorteMiembrosActualId != null && ids.indexOf(String(cohorteMiembrosActualId)) !== -1;
        })
        .map(function (a) { return parseInt(a.id, 10); })
        .filter(function (x) { return !isNaN(x); });
      var txt = document.getElementById('buscarMiembroCohorteInput');
      if (txt) txt.value = '';
      renderMiembrosTabla();
      var ModalCtor = obtenerBootstrapModal();
      var elModal = document.getElementById('modalMiembrosCohorte');
      if (!ModalCtor || !elModal) {
        alert('No se pudo abrir el modal de miembros (Bootstrap no está disponible). Recargue la página.');
        return;
      }
      ModalCtor.getOrCreateInstance(elModal).show();
    } catch (e) {
      console.error(e);
      alert(e.message || 'No se pudieron cargar los candidatos para esta cohorte. Revise su sesión y permisos (VER_GRUPOS / VER_ALUMNOS).');
    }
  }

  function nombreCohorteActualParaMensaje() {
    var c = (cohortes || []).find(function (x) { return String(x.id) === String(cohorteMiembrosActualId); });
    return c ? (c.nombre || c.idCohorte || 'esta cohorte') : 'esta cohorte';
  }

  function construirHtmlConfirmacionQuitar(removeIds, nombreCohorte) {
    var n = removeIds.length;
    var etiquetaNum = n === 1 ? '1 estudiante' : (String(n) + ' estudiantes');
    var html = '';
    html += '<div class="modal-cohorte-quitar-miembro__lead mb-3">';
    html += '<p class="mb-2 fs-6 text-body lh-base">Vas a sacar <strong>' + escapeHtml(etiquetaNum) + '</strong> de la cohorte</p>';
    html += '<div class="d-inline-flex align-items-center gap-2 flex-wrap">';
    html += '<span class="badge rounded-pill modal-cohorte-quitar-miembro__badge-cohorte px-3 py-2 fw-semibold">' + escapeHtml(nombreCohorte) + '</span>';
    html += '</div>';
    html += '</div>';

    html += '<div class="alert modal-cohorte-quitar-miembro__alert modal-cohorte-quitar-miembro__alert--compact d-flex align-items-start gap-2 mb-3 border-0 shadow-sm" role="note">';
    html += '<div class="modal-cohorte-quitar-miembro__alert-icon flex-shrink-0"><i class="bi bi-shield-check"></i></div>';
    html += '<p class="small text-body mb-0 lh-sm"><strong>Las demás cohortes no cambian.</strong> Siguen en ellas; únicamente se les retira de esta cohorte.</p>';
    html += '</div>';

    html += '<div class="small text-uppercase text-muted fw-semibold letter-spacing mb-1 modal-cohorte-quitar-miembro__section-label">Usuarios afectados</div>';
    html += '<ul class="list-group list-group-flush modal-cohorte-quitar-miembro__list rounded-3 border">';
    removeIds.forEach(function (rid) {
      var a = (candidatosCache || []).find(function (x) { return String(x.id) === String(rid); });
      var etiqueta = a ? ((a.nombre || '').trim() || a.matricula || ('ID ' + rid)) : ('ID ' + rid);
      var mat = a && a.matricula ? String(a.matricula) : '';
      var cids = a && Array.isArray(a.cohorteIds) ? a.cohorteIds : [];
      var cnoms = a && Array.isArray(a.cohortesNombres) ? a.cohortesNombres : [];
      var otras = [];
      for (var i = 0; i < cids.length; i++) {
        if (String(cids[i]) === String(cohorteMiembrosActualId)) continue;
        otras.push(cnoms[i] ? String(cnoms[i]) : ('Cohorte ' + cids[i]));
      }
      html += '<li class="list-group-item modal-cohorte-quitar-miembro__item px-3 py-3">';
      html += '<div class="d-flex gap-3">';
      html += '<div class="modal-cohorte-quitar-miembro__avatar flex-shrink-0"><i class="bi bi-person-fill"></i></div>';
      html += '<div class="min-w-0 flex-grow-1">';
      html += '<div class="fw-semibold text-body text-break">' + escapeHtml(etiqueta) + '</div>';
      if (mat) {
        html += '<div class="small text-muted font-monospace">' + escapeHtml(mat) + '</div>';
      }
      if (otras.length) {
        html += '<div class="small mt-2 pt-2 border-top border-light-subtle">';
        html += '<span class="text-muted"><i class="bi bi-diagram-3 me-1"></i></span>';
        html += '<span class="text-body-secondary">' + escapeHtml('Seguirá en: ' + otras.join(', ')) + '</span>';
        html += '</div>';
      }
      html += '</div></div></li>';
    });
    html += '</ul>';
    return html;
  }

  async function ejecutarGuardarMiembros(addIds, removeIds) {
    var resp = await api('/cohortes/' + encodeURIComponent(String(cohorteMiembrosActualId)) + '/miembros', {
      method: 'PUT',
      body: JSON.stringify({ alumnoIdsAdd: addIds, alumnoIdsRemove: removeIds })
    });
    if (resp && resp.ignoradosAdd > 0) {
      var adv = resp.advertencia ? ('\n\n' + resp.advertencia) : '';
      alert('Miembros actualizados con advertencias: no se agregaron ' + resp.ignoradosAdd + ' alumno(s). Revise que tengan inscripción al programa de esta cohorte.' + adv);
    } else {
      alert('Miembros actualizados.');
    }
    var ModalCtor = obtenerBootstrapModal();
    var elModal = document.getElementById('modalMiembrosCohorte');
    if (ModalCtor && elModal) ModalCtor.getOrCreateInstance(elModal).hide();
    await cargarCohortes();
  }

  async function guardarMiembros() {
    if (!cohorteMiembrosActualId) return;
    var seleccionados = Array.from(document.querySelectorAll('.cohorte-miembro-check:checked'))
      .map(function (x) { return parseInt(x.value, 10); })
      .filter(function (x) { return !isNaN(x); });

    var inicialSet = {};
    (miembrosInicialesIds || []).forEach(function (id) { inicialSet[String(id)] = true; });
    var selSet = {};
    (seleccionados || []).forEach(function (id) { selSet[String(id)] = true; });

    var addIds = (seleccionados || []).filter(function (id) { return !inicialSet[String(id)]; });
    var removeIds = (miembrosInicialesIds || []).filter(function (id) { return !selSet[String(id)]; });

    if (removeIds.length > 0) {
      var nombreCohorte = nombreCohorteActualParaMensaje();
      var bodyEl = document.getElementById('confirmQuitarMiembrosBody');
      var elConf = document.getElementById('modalConfirmarQuitarMiembrosCohorte');
      var ModalCtor = obtenerBootstrapModal();
      if (!bodyEl || !elConf || !ModalCtor) {
        alert('No se pudo mostrar la confirmación. Recargue la página.');
        return;
      }
      bodyEl.innerHTML = construirHtmlConfirmacionQuitar(removeIds, nombreCohorte);
      elConf.dataset.pendingAdd = JSON.stringify(addIds);
      elConf.dataset.pendingRemove = JSON.stringify(removeIds);
      ModalCtor.getOrCreateInstance(elConf).show();
      return;
    }

    try {
      await ejecutarGuardarMiembros(addIds, removeIds);
    } catch (e) {
      console.error(e);
      alert(e.message || 'No se pudieron guardar los miembros. Revise permisos (ACTUALIZAR_GRUPOS o ACTUALIZAR_ALUMNOS).');
    }
  }

  async function confirmarQuitarMiembrosDesdeModal() {
    var elConf = document.getElementById('modalConfirmarQuitarMiembrosCohorte');
    var ModalCtor = obtenerBootstrapModal();
    if (!elConf || !ModalCtor || !elConf.dataset.pendingAdd) return;
    var addIds = [];
    var removeIds = [];
    try {
      addIds = JSON.parse(elConf.dataset.pendingAdd || '[]');
      removeIds = JSON.parse(elConf.dataset.pendingRemove || '[]');
    } catch (err) {
      console.error(err);
      return;
    }
    delete elConf.dataset.pendingAdd;
    delete elConf.dataset.pendingRemove;
    ModalCtor.getOrCreateInstance(elConf).hide();
    try {
      await ejecutarGuardarMiembros(addIds, removeIds);
    } catch (e) {
      console.error(e);
      alert(e.message || 'No se pudieron guardar los miembros. Revise permisos (ACTUALIZAR_GRUPOS o ACTUALIZAR_ALUMNOS).');
    }
  }

  async function abrirListaMiembros(id) {
    var cohorte = (cohortes || []).find(function (x) { return String(x.id) === String(id); });
    var titulo = document.getElementById('listaMiembrosCohorteNombre');
    if (titulo) titulo.textContent = cohorte ? (cohorte.nombre || cohorte.idCohorte || '—') : '—';
    var tbody = document.getElementById('listaMiembrosCohorteBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">Cargando…</td></tr>';
    var miembros = await api('/cohortes/' + id + '/miembros');
    if (tbody) {
      if (!miembros || !miembros.length) {
        tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">Esta cohorte no tiene miembros.</td></tr>';
      } else {
        tbody.innerHTML = miembros.map(function (m) {
          return '<tr>' +
            '<td>' + escapeHtml(m.matricula || '') + '</td>' +
            '<td>' + escapeHtml(m.nombre || '') + '</td>' +
            '<td>' + escapeHtml(m.periodoCursando || '—') + '</td>' +
            '</tr>';
        }).join('');
      }
    }
    var ModalCtor = obtenerBootstrapModal();
    var elLista = document.getElementById('modalListaMiembrosCohorte');
    if (ModalCtor && elLista) ModalCtor.getOrCreateInstance(elLista).show();
  }

  function bindEventos() {
    var tbody = document.getElementById('cohortesTableBody');
    if (tbody) {
      tbody.addEventListener('click', function (e) {
        var btn = e.target.closest('button[data-action]');
        if (!btn) return;
        var id = btn.getAttribute('data-id');
        var action = btn.getAttribute('data-action');
        if (action === 'editar') editarCohorte(id);
        else if (action === 'eliminar') eliminarCohorte(id);
        else if (action === 'miembros') {
          abrirMiembros(id).catch(function (err) {
            console.error(err);
            alert((err && err.message) ? err.message : 'No se pudo abrir la gestión de miembros.');
          });
        }
        else if (action === 'ver-miembros') abrirListaMiembros(id).catch(function (err) {
          console.error(err);
          alert((err && err.message) ? err.message : 'No se pudo cargar la lista de miembros.');
        });
      });
    }
    var btnGuardar = document.getElementById('btnGuardarCohorte');
    if (btnGuardar) btnGuardar.addEventListener('click', guardarCohorte);
    var btnCancel = document.getElementById('btnCancelarEdicionCohorte');
    if (btnCancel) btnCancel.addEventListener('click', limpiarForm);
    var btnGuardarMiembros = document.getElementById('btnGuardarMiembrosCohorte');
    if (btnGuardarMiembros) btnGuardarMiembros.addEventListener('click', guardarMiembros);
    var txtBuscar = document.getElementById('buscarMiembroCohorteInput');
    if (txtBuscar) txtBuscar.addEventListener('input', renderMiembrosTabla);
    var checkAll = document.getElementById('miembrosCohorteCheckAll');
    if (checkAll) {
      checkAll.addEventListener('change', function () {
        var checks = document.querySelectorAll('#miembrosCohorteBody .cohorte-miembro-check');
        checks.forEach(function (cb) { cb.checked = checkAll.checked; });
        checkAll.indeterminate = false;
      });
    }
    var bodyMiembros = document.getElementById('miembrosCohorteBody');
    if (bodyMiembros) {
      bodyMiembros.addEventListener('change', function (e) {
        if (e.target && e.target.classList.contains('cohorte-miembro-check')) {
          actualizarEstadoCheckAllMiembros();
        }
      });
    }
    var btnConfQuitar = document.getElementById('btnConfirmarQuitarMiembrosCohorte');
    if (btnConfQuitar) {
      btnConfQuitar.addEventListener('click', function () { confirmarQuitarMiembrosDesdeModal(); });
    }
    var modalQuitar = document.getElementById('modalConfirmarQuitarMiembrosCohorte');
    if (modalQuitar) {
      modalQuitar.addEventListener('hidden.bs.modal', function () {
        if (modalQuitar.dataset.pendingAdd !== undefined) {
          delete modalQuitar.dataset.pendingAdd;
          delete modalQuitar.dataset.pendingRemove;
        }
      });
    }
  }

  document.addEventListener('DOMContentLoaded', async function () {
    try {
      bindEventos();
      await cargarProgramas();
      await cargarCohortes();
    } catch (e) {
      console.error(e);
      alert(e.message || 'Error al cargar cohortes.');
    }
  });
})();

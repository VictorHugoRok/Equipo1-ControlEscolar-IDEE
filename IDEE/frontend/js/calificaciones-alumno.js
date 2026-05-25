/**
 * Pantalla de calificaciones (ALUMNO)
 * Multi-programa: periodo actual por cada inscripción (materias del semestre del plan que cursa);
 * historial: tarjetas por (programa + periodo escolar) anteriores al actual en ese programa.
 *
 * Reglas:
 * - CAPTURADA no es visible (backend la filtra).
 * - EN_REVISION/CONFIRMADA con bloqueo por evaluación docente (backend).
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

  function fmt2(n) {
    if (n == null || Number.isNaN(Number(n))) return '';
    return (Math.round(Number(n) * 100) / 100).toFixed(2);
  }

  function badgeEstado(estadoAprobacion) {
    var e = (estadoAprobacion || '').toUpperCase();
    if (e === 'CONFIRMADA') return '<span class="badge bg-success">Confirmada</span>';
    if (e === 'EN_REVISION') {
      return '<span class="badge bg-info">En revisión</span>';
    }
    return '<span class="badge bg-secondary">—</span>';
  }

  function renderCalificacionCell(row) {
    if (!row) return '—';
    if (row.bloqueadaPorEvaluacion) {
      var msg = row.mensajeBloqueo || 'Responde la evaluación docente para ver tu calificación.';
      return (
        '<span class="badge text-bg-warning">Bloqueada</span>' +
        '<div class="small text-muted mt-1">' + escapeHtml(msg) + '</div>' +
        '<a class="btn btn-sm btn-ide mt-2" href="evaluacion-docente.html">Ir a Evaluación Docente</a>'
      );
    }
    if (row.calificacionFinal == null) return '—';
    return '<span class="fw-semibold">' + escapeHtml(fmt2(row.calificacionFinal)) + '</span>';
  }

  function asignaturaLabel(a) {
    if (!a) return '—';
    var clave = a.clave ? String(a.clave).trim() : '';
    var nombre = a.nombre ? String(a.nombre).trim() : '';
    var base = (clave ? (clave + ' - ') : '') + (nombre || 'Asignatura');
    return base;
  }

  function periodoClave(row) {
    if (!row) return '';
    if (row.periodo) return String(row.periodo);
    if (row.periodoAcademico && (row.periodoAcademico.codigo || row.periodoAcademico.nombre)) {
      return String(row.periodoAcademico.codigo || row.periodoAcademico.nombre);
    }
    return '';
  }

  /** { y, n } o null si no reconoce formato tipo 2026-1 */
  function parsePeriodoCodigo(s) {
    var m = String(s || '').trim().match(/^(\d{4})\s*[-–]\s*(\d+)/);
    if (!m) return null;
    return { y: parseInt(m[1], 10), n: parseInt(m[2], 10) };
  }

  /** true si `antes` es estrictamente anterior a `actual` (orden escolar). */
  function periodoEsEstrictamenteAnterior(antes, actual) {
    var A = parsePeriodoCodigo(antes);
    var B = parsePeriodoCodigo(actual);
    if (!A || !B) {
      return String(antes).trim() !== String(actual).trim() && String(antes).trim() !== '';
    }
    if (A.y !== B.y) return A.y < B.y;
    return A.n < B.n;
  }

  function esHistorialVisible(row) {
    if (!row) return false;
    var e = (row.estadoAprobacion || '').toUpperCase();
    return e === 'CONFIRMADA' || e === 'EN_REVISION';
  }

  function programaIdDesdeRow(r) {
    return r && r.asignatura && r.asignatura.programa && r.asignatura.programa.id != null
      ? String(r.asignatura.programa.id)
      : '';
  }

  function inscripcionesDesdeAlumno(alumno) {
    var list = [];
    var asign = alumno && Array.isArray(alumno.programasAsignados) ? alumno.programasAsignados : [];
    asign.forEach(function (ap) {
      if (!ap || !ap.programa || ap.programa.id == null) return;
      var st = ap.estatusMatricula ? String(ap.estatusMatricula).toUpperCase() : '';
      if (st === 'BAJA_DEFINITIVA') return;
      list.push({
        programaId: ap.programa.id,
        programaNombre: ap.programa.nombre || ap.programa.clave || ('Programa ' + ap.programa.id),
        periodoCursando: ap.periodoCursando != null ? Number(ap.periodoCursando) : null,
        periodoEscolarCodigo: ap.periodoEscolarCursandoCodigo ? String(ap.periodoEscolarCursandoCodigo).trim() : '',
        estatusMatricula: ap.estatusMatricula || ''
      });
    });
    if (!list.length && alumno && alumno.programa && alumno.programa.id != null) {
      list.push({
        programaId: alumno.programa.id,
        programaNombre: alumno.programa.nombre || alumno.programa.clave || ('Programa ' + alumno.programa.id),
        periodoCursando: alumno.periodoCursando != null ? Number(alumno.periodoCursando) : null,
        periodoEscolarCodigo: '',
        estatusMatricula: ''
      });
    }
    return list;
  }

  async function cargarAsignaturasPlanPropio(programaId) {
    if (!programaId) return [];
    try {
      var list = await authFetch(
        '/kardex/mi-kardex/asignaturas-plan?programaId=' + encodeURIComponent(String(programaId)),
        { method: 'GET' }
      );
      return Array.isArray(list) ? list : [];
    } catch (_) {
      return [];
    }
  }

  function mejorCalificacionParaAsignatura(rows, asignaturaId, programaId, codigoEscolarPreferido) {
    var candidatas = (rows || []).filter(function (r) {
      if (!r || !r.asignatura || String(r.asignatura.id) !== String(asignaturaId)) return false;
      var pid = programaIdDesdeRow(r);
      return pid === String(programaId);
    });
    if (!candidatas.length) return null;
    if (codigoEscolarPreferido) {
      var exact = candidatas.filter(function (r) { return periodoClave(r) === codigoEscolarPreferido; });
      if (exact.length === 1) return exact[0];
      if (exact.length > 1) return exact[0];
    }
    return candidatas[0];
  }

  function sortRowsByAsignatura(rows) {
    return (rows || []).slice().sort(function (x, y) {
      return asignaturaLabel(x.asignatura).localeCompare(asignaturaLabel(y.asignatura), 'es');
    });
  }

  function tablaMateriasHtml(asignaturasPeriodo, rows, programaId, codigoEscolar) {
    var byAsig = {};
    (asignaturasPeriodo || []).forEach(function (a) {
      if (a && a.id != null) {
        byAsig[String(a.id)] = mejorCalificacionParaAsignatura(rows, a.id, programaId, codigoEscolar || null);
      }
    });
    if (!asignaturasPeriodo || !asignaturasPeriodo.length) {
      return '<p class="text-muted mb-0">No hay materias registradas en el plan para este semestre.</p>';
    }
    var body = asignaturasPeriodo.map(function (a) {
      var r = byAsig[String(a.id)] || null;
      var estado = badgeEstado(r ? r.estadoAprobacion : null);
      var calCell = r ? renderCalificacionCell(r) : '—';
      return (
        '<tr>' +
        '<td>' + escapeHtml(asignaturaLabel(a)) + '</td>' +
        '<td class="text-nowrap">' + calCell + '</td>' +
        '<td>' + estado + '</td>' +
        '</tr>'
      );
    }).join('');
    return (
      '<div class="table-responsive">' +
      '<table class="table table-hover align-middle mb-0">' +
      '<thead class="table-light"><tr><th>Asignatura</th><th class="text-nowrap">Calificación</th><th>Estado</th></tr></thead>' +
      '<tbody>' + body + '</tbody></table></div>'
    );
  }

  /**
   * Tarjetas historial: { key, programaId, programaNombre, periodoCodigo, sortKey }
   */
  function construirTarjetasHistorial(rows, inscripciones) {
    var vis = (rows || []).filter(esHistorialVisible);
    var mapaActual = {};
    var nombresProg = {};
    inscripciones.forEach(function (ins) {
      mapaActual[String(ins.programaId)] = ins.periodoEscolarCodigo || '';
      nombresProg[String(ins.programaId)] = ins.programaNombre;
    });
    var tarjetas = [];
    var seen = {};

    vis.forEach(function (r) {
      var pid = programaIdDesdeRow(r);
      if (!pid) return;
      var pnom = nombresProg[pid]
        || (r.asignatura && r.asignatura.programa && r.asignatura.programa.nombre
          ? String(r.asignatura.programa.nombre)
          : 'Programa');
      var cod = periodoClave(r);
      if (!cod) return;
      var actualCod = mapaActual[pid] || '';
      if (actualCod && cod === actualCod) return;
      if (actualCod && !periodoEsEstrictamenteAnterior(cod, actualCod)) return;
      var key = pid + '|' + cod;
      if (seen[key]) return;
      seen[key] = true;
      var p = parsePeriodoCodigo(cod);
      var sortKey = p ? (p.y * 100 + p.n) : 0;
      tarjetas.push({
        key: key,
        programaId: pid,
        programaNombre: pnom,
        periodoCodigo: cod,
        sortKey: sortKey
      });
    });

    tarjetas.sort(function (a, b) {
      if (b.sortKey !== a.sortKey) return b.sortKey - a.sortKey;
      return String(a.periodoCodigo).localeCompare(String(b.periodoCodigo), 'es');
    });
    return tarjetas;
  }

  async function init() {
    try {
      if (typeof setupLogoutButtons === 'function') setupLogoutButtons();
      if (typeof protectPage === 'function') protectPage();

      var me = (typeof getCurrentUser === 'function') ? await getCurrentUser() : null;
      var tipo = (me && me.tipoUsuario) ? String(me.tipoUsuario) : (localStorage.getItem('userTipo') || '');
      if (tipo !== 'ALUMNO') {
        window.location.href = '../index.html';
        return;
      }

      var alumno = await authFetch('/alumnos/me', { method: 'GET' });
      var inscripciones = inscripcionesDesdeAlumno(alumno);

      var rows = await authFetch('/alumnos/me/calificaciones', { method: 'GET' });
      rows = Array.isArray(rows) ? rows : [];

      // ---------- Periodo actual (un bloque por programa) ----------
      var contActual = document.getElementById('califAlumnoActualContenedor');
      if (contActual) {
        if (!inscripciones.length) {
          contActual.innerHTML = '<p class="text-muted py-4 mb-0">No hay programas asignados en tu expediente.</p>';
        } else {
          var partes = [];
          for (var i = 0; i < inscripciones.length; i++) {
            var ins = inscripciones[i];
            var asignaturasPrograma = await cargarAsignaturasPlanPropio(ins.programaId);
            var asignaturasPeriodo = asignaturasPrograma;
            if (ins.periodoCursando != null && !Number.isNaN(ins.periodoCursando)) {
              asignaturasPeriodo = asignaturasPrograma.filter(function (a) {
                var n = a && a.periodo && a.periodo.numero != null ? Number(a.periodo.numero) : null;
                return n != null && n === ins.periodoCursando;
              });
            }
            asignaturasPeriodo = (asignaturasPeriodo || []).slice().sort(function (a, b) {
              return asignaturaLabel(a).localeCompare(asignaturaLabel(b), 'es');
            });
            var tituloProg = escapeHtml(ins.programaNombre);
            if (ins.periodoCursando != null && !Number.isNaN(ins.periodoCursando)) {
              tituloProg += ' <span class="text-muted fw-normal">· Semestre ' + escapeHtml(String(ins.periodoCursando)) + '</span>';
            }
            partes.push(
              '<div class="card mb-4 border-0 shadow-sm">' +
              '<div class="card-header bg-light py-2">' +
              '<h6 class="mb-0">' + tituloProg + '</h6>' +
              '</div><div class="card-body p-0">' +
              tablaMateriasHtml(asignaturasPeriodo, rows, ins.programaId, ins.periodoEscolarCodigo) +
              '</div></div>'
            );
          }
          contActual.innerHTML = partes.join('');
        }
      }

      // ---------- Historial: tarjetas por programa + periodo escolar pasado ----------
      var wrapTarjetas = document.getElementById('califAlumnoHistorialTarjetas');
      var bodyHist = document.getElementById('califAlumnoHistorialBody');
      if (wrapTarjetas && bodyHist) {
        var tarjetas = construirTarjetasHistorial(rows, inscripciones);
        if (!tarjetas.length) {
          wrapTarjetas.innerHTML = '<div class="col-12 text-muted small">Aún no hay periodos anteriores con calificaciones visibles.</div>';
          bodyHist.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">—</td></tr>';
        } else {
          var selectedKey = tarjetas[0].key;

          function filasHistorialSeleccion() {
            var t = tarjetas.find(function (x) { return x.key === selectedKey; });
            if (!t) return [];
            return sortRowsByAsignatura(rows.filter(function (r) {
              if (!esHistorialVisible(r)) return false;
              if (programaIdDesdeRow(r) !== String(t.programaId)) return false;
              return periodoClave(r) === t.periodoCodigo;
            }));
          }

          function renderTablaHist() {
            var list = filasHistorialSeleccion();
            if (!list.length) {
              bodyHist.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">No hay calificaciones en este periodo.</td></tr>';
              return;
            }
            bodyHist.innerHTML = list.map(function (r) {
              var asig = asignaturaLabel(r.asignatura);
              var estado = badgeEstado(r.estadoAprobacion);
              var calCell = renderCalificacionCell(r);
              return (
                '<tr>' +
                '<td>' + escapeHtml(asig) + '</td>' +
                '<td class="text-nowrap">' + calCell + '</td>' +
                '<td>' + estado + '</td>' +
                '</tr>'
              );
            }).join('');
          }

          function renderTarjetas() {
            wrapTarjetas.innerHTML = tarjetas.map(function (t) {
              var active = t.key === selectedKey;
              return (
                '<div class="col-12 col-sm-6 col-md-4 col-lg-3">' +
                '<button type="button" class="calif-hist-card w-100 btn text-start p-3 h-100 border rounded-3 ' +
                (active ? 'border-primary bg-primary-subtle' : 'border-secondary-subtle bg-white') + '" ' +
                'data-hist-key="' + escapeHtml(t.key) + '">' +
                '<div class="small text-muted mb-1">' + escapeHtml(t.programaNombre) + '</div>' +
                '<div class="fw-semibold"><i class="bi bi-calendar2-week me-1"></i>' + escapeHtml(t.periodoCodigo) + '</div>' +
                '</button></div>'
              );
            }).join('');
            wrapTarjetas.querySelectorAll('.calif-hist-card').forEach(function (btn) {
              btn.addEventListener('click', function () {
                selectedKey = this.getAttribute('data-hist-key') || '';
                renderTarjetas();
                renderTablaHist();
              });
            });
          }

          renderTarjetas();
          renderTablaHist();
        }
      }
    } catch (e) {
      console.error(e);
      if (typeof window.showSystemToast === 'function') {
        window.showSystemToast(e && e.message ? e.message : 'No se pudieron cargar las calificaciones.', { type: 'error', durationMs: 5200 });
      } else {
        alert((e && e.message) ? e.message : 'No se pudieron cargar las calificaciones.');
      }
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();

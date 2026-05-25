/**
 * Constancias de estudios (PDF).
 * - Selección de alumno + tipo de constancia
 * - Generación PDF con html2canvas + jsPDF
 */
(function () {
  'use strict';

  var API = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
  var alumnos = [];
  var planteles = [];
  var alumnoSeleccionado = null;
  var _constanciaPreviewUrl = null;
  var _constanciaPreviewFilename = null;

  function headersJson() {
    var h = { 'Content-Type': 'application/json' };
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

  function mostrarFeedback(msg, tipo) {
    var el = document.getElementById('constFeedback');
    if (!el) return;
    el.className = 'alert alert-' + (tipo || 'info');
    el.textContent = msg || '';
    el.classList.toggle('d-none', !msg);
  }

  function nombreCompletoAlumno(a) {
    if (!a) return '';
    var parts = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean);
    return parts.join(' ').trim();
  }

  function sexoAlumno(a) {
    var s = a && a.sexo ? String(a.sexo).toUpperCase() : '';
    if (s === 'FEMENINO' || s === 'F') return 'F';
    if (s === 'MASCULINO' || s === 'M') return 'M';
    return '';
  }

  function palabrasPorGenero(a) {
    var sx = sexoAlumno(a);
    return {
      aQuien: 'A quien corresponda:',
      interesado: sx === 'F' ? 'la interesada' : 'el interesado',
      elLa: sx === 'F' ? 'la' : 'el',
      alumnaAlumno: sx === 'F' ? 'alumna' : 'alumno',
      inscritaInscrito: sx === 'F' ? 'inscrita' : 'inscrito',
      seHaceConstarQue: sx === 'F' ? 'se hace constar que la' : 'se hace constar que el',
      actualmente: sx === 'F' ? 'Actualmente, la alumna' : 'Actualmente, el alumno'
    };
  }

  function tipoProgramaTexto(programa) {
    var t = programa && programa.tipoPrograma ? String(programa.tipoPrograma).toUpperCase() : '';
    if (t === 'LICENCIATURA') return 'licenciatura';
    if (t === 'MAESTRIA') return 'maestría';
    if (t === 'ESPECIALIDAD') return 'especialidad';
    if (t === 'DOCTORADO') return 'doctorado';
    return 'programa';
  }

  function articuloPrograma(programa) {
    var tp = tipoProgramaTexto(programa);
    // maestría/licenciatura/especialidad -> femenino; doctorado -> masculino
    return (tp === 'doctorado') ? 'del' : 'de la';
  }

  function tipoPeriodoTexto(programa) {
    var t = programa && programa.tipoPeriodo ? String(programa.tipoPeriodo).toUpperCase() : '';
    if (t === 'SEMESTRE') return 'semestre';
    if (t === 'CUATRIMESTRE') return 'cuatrimestre';
    if (t === 'TETRAMESTRE') return 'tetramestre';
    if (t === 'TRIMESTRE') return 'trimestre';
    return 'periodo';
  }

  function ciclosPorAnio(programa) {
    var t = programa && programa.tipoPeriodo ? String(programa.tipoPeriodo).toUpperCase() : '';
    if (t === 'SEMESTRE') return 2;
    if (t === 'CUATRIMESTRE') return 3;
    if (t === 'TETRAMESTRE') return 3; // aproximación (no siempre aplica)
    if (t === 'TRIMESTRE') return 4;
    return 2;
  }

  function ordinalES(n) {
    var map = {
      1: 'primer',
      2: 'segundo',
      3: 'tercer',
      4: 'cuarto',
      5: 'quinto',
      6: 'sexto',
      7: 'séptimo',
      8: 'octavo',
      9: 'noveno',
      10: 'décimo',
      11: 'décimo primero',
      12: 'décimo segundo'
    };
    return map[n] || (String(n) + '°');
  }

  function formatoFechaLargaES(d) {
    var meses = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
    var dia = d.getDate();
    var mes = meses[d.getMonth()];
    var anio = d.getFullYear();
    return { dia: dia, mes: mes, anio: anio };
  }

  function encontrarPlantelPorAlumno(a) {
    var prog = a && a.programa ? a.programa : null;
    var claveDgp = prog && prog.claveDgp ? String(prog.claveDgp).trim().toUpperCase() : '';
    // Mejor esfuerzo: si no hay claveDgp pero solo hay un plantel, usarlo.
    if (!claveDgp) {
      return (planteles && planteles.length === 1) ? planteles[0] : null;
    }
    var found = (planteles || []).find(function (p) {
      return (p && p.claveDgp ? String(p.claveDgp).trim().toUpperCase() : '') === claveDgp;
    }) || null;
    return found || ((planteles && planteles.length === 1) ? planteles[0] : null);
  }

  function renderLista() {
    var lista = document.getElementById('constListaAlumnos');
    if (!lista) return;

    var q = (document.getElementById('constFiltroAlumno') || {}).value || '';
    q = String(q).trim().toLowerCase();
    var progId = (document.getElementById('constFiltroPrograma') || {}).value || '';
    var est = (document.getElementById('constFiltroEstatus') || {}).value || '';

    var filtrados = (alumnos || []).filter(function (a) {
      if (!a || !a.programa || !a.programa.id) return false;
      if (progId && String(a.programa.id) !== String(progId)) return false;
      if (est && String(a.estatusMatricula || '') !== String(est)) return false;
      if (!q) return true;
      var hay = [
        a.matricula,
        a.curp,
        nombreCompletoAlumno(a),
        (a.programa && a.programa.nombre) ? a.programa.nombre : '',
        (a.programa && a.programa.clave) ? a.programa.clave : ''
      ].join(' ').toLowerCase();
      return hay.indexOf(q) !== -1;
    });

    var countEl = document.getElementById('constanciasCount');
    if (countEl) countEl.textContent = String(filtrados.length);

    if (filtrados.length === 0) {
      lista.innerHTML = '<div class="list-group-item border-0 text-muted small py-4 text-center">Sin resultados.</div>';
      return;
    }

    lista.innerHTML = filtrados.map(function (a) {
      var id = a.id;
      var nom = nombreCompletoAlumno(a) || '—';
      var mat = a.matricula || '—';
      var prog = (a.programa && a.programa.nombre) ? a.programa.nombre : '—';
      var checked = alumnoSeleccionado && String(alumnoSeleccionado.id) === String(id);
      return (
        '<label class="list-group-item kardex-alumno-item d-flex gap-2 align-items-start">' +
          '<input class="form-check-input mt-1 kardex-alumno-item-radio" type="radio" name="constAlumnoRadio" value="' + escapeHtml(id) + '" ' + (checked ? 'checked' : '') + ' />' +
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

    lista.querySelectorAll('input[name="constAlumnoRadio"]').forEach(function (r) {
      r.addEventListener('change', function () {
        var id = this.value;
        alumnoSeleccionado = (alumnos || []).find(function (x) { return String(x.id) === String(id); }) || null;
        actualizarUISeleccion();
      });
    });
  }

  function poblarFiltros() {
    var sel = document.getElementById('constFiltroPrograma');
    if (!sel) return;
    var map = {};
    (alumnos || []).forEach(function (a) {
      if (a && a.programa && a.programa.id) map[a.programa.id] = a.programa.nombre || a.programa.clave || ('Programa ' + a.programa.id);
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

  function actualizarUISeleccion() {
    var hint = document.getElementById('constAlumnoSeleccionadoHint');
    var btn = document.getElementById('btnGenerarConstancia');
    if (hint) {
      hint.textContent = alumnoSeleccionado
        ? ('Alumno seleccionado: ' + nombreCompletoAlumno(alumnoSeleccionado))
        : 'Selecciona un alumno para continuar.';
    }
    if (btn) btn.disabled = !alumnoSeleccionado;
  }

  async function cargarDatos() {
    try {
      var resA = await apiFetch('/alumnos', { method: 'GET' });
      if (!resA.ok) throw new Error('No se pudieron cargar alumnos');
      alumnos = await resA.json();
      if (!Array.isArray(alumnos)) alumnos = [];

      // Planteles: opcional. En algunos roles no se permite listar todos los planteles.
      // La constancia resolverá el plantel por programa en el momento de generar.
      planteles = [];

      poblarFiltros();
      renderLista();
      actualizarUISeleccion();
    } catch (e) {
      console.error(e);
      mostrarFeedback('Error al cargar datos para constancias. ' + (e && e.message ? e.message : ''), 'danger');
      var lista = document.getElementById('constListaAlumnos');
      if (lista) lista.innerHTML = '<div class="list-group-item border-0 text-danger small py-4 text-center">No se pudieron cargar alumnos.</div>';
    }
  }

  async function obtenerPeriodoCursando(alumnoId) {
    try {
      var res = await apiFetch('/kardex/' + encodeURIComponent(String(alumnoId)), { method: 'GET' });
      if (!res.ok) return null;
      return await res.json();
    } catch (_) {
      return null;
    }
  }

  function construirNodoConstancia(opts) {
    var alumno = opts.alumno;
    var programa = alumno && alumno.programa ? alumno.programa : null;
    var plantel = opts.plantel;
    var tipo = opts.tipo;
    var kardex = opts.kardex || null;

    var gen = palabrasPorGenero(alumno);
    var hoy = new Date();
    var f = formatoFechaLargaES(hoy);

    var nombrePlantel = plantel && plantel.nombrePlantel ? plantel.nombrePlantel : '—';
    var claveCct = plantel && plantel.claveCct ? plantel.claveCct : '—';

    var tipoProg = tipoProgramaTexto(programa);
    var artProg = articuloPrograma(programa);
    var nombrePrograma = (programa && programa.nombre) ? programa.nombre : '—';
    var periodoTipo = tipoPeriodoTexto(programa);
    var dur = programa && programa.duracionPeriodos != null ? Number(programa.duracionPeriodos) : null;

    var periodoCursando = (kardex && kardex.periodoCursando != null) ? Number(kardex.periodoCursando) : null;
    var periodoTxt = periodoCursando ? (ordinalES(periodoCursando) + ' ' + periodoTipo) : ('— ' + periodoTipo);
    var durTxt = (dur ? (dur + ' ' + (dur === 1 ? periodoTipo : (periodoTipo + 's'))) : ('— ' + periodoTipo + 's'));
    var anios = (dur ? (dur / ciclosPorAnio(programa)) : null);
    var aniosTxt = (anios != null && isFinite(anios))
      ? (Number.isInteger(anios) ? String(anios) : String(Math.round(anios * 10) / 10))
      : '—';

    var asunto = 'Constancia de estudios';
    if (tipo === 'CALIFICACIONES') asunto = 'Constancia de estudios con calificaciones';
    if (tipo === 'EVENTO') asunto = 'Constancia de estudios (evento)';

    var wrap = document.createElement('div');
    wrap.style.background = '#ffffff';
    wrap.style.color = '#111';
    wrap.style.fontFamily = 'system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';
    // Carta (Letter) con márgenes de 2.5cm por lado
    wrap.style.width = '216mm';
    wrap.style.minHeight = '279mm';
    wrap.style.boxSizing = 'border-box';
    wrap.style.padding = '25mm';
    wrap.style.position = 'relative';
    wrap.style.overflow = 'hidden';

    // Imagen inferior (pegada hasta abajo de la hoja)
    var bgBottom = document.createElement('div');
    bgBottom.style.position = 'absolute';
    bgBottom.style.left = '0';
    bgBottom.style.right = '0';
    bgBottom.style.bottom = '0';
    bgBottom.style.height = '95mm';
    bgBottom.style.backgroundImage = 'url(../assets/constancia_watermark.png)';
    bgBottom.style.backgroundRepeat = 'no-repeat';
    bgBottom.style.backgroundPosition = 'center bottom';
    bgBottom.style.backgroundSize = '100% auto';
    bgBottom.style.opacity = '0.22';
    bgBottom.style.pointerEvents = 'none';
    bgBottom.style.zIndex = '0';
    wrap.appendChild(bgBottom);

    // Header: logo izquierda + "A quien corresponda" debajo del logo + bloque institución derecha
    var header = document.createElement('div');
    header.style.display = 'flex';
    header.style.justifyContent = 'space-between';
    header.style.alignItems = 'flex-start';
    header.style.gap = '16px';
    header.style.marginBottom = '28px';

    var hdrLeft = document.createElement('div');
    hdrLeft.style.display = 'flex';
    hdrLeft.style.flexDirection = 'column';
    hdrLeft.style.alignItems = 'flex-start';
    hdrLeft.style.gap = '10px';
    hdrLeft.style.width = '240px';

    var logo = document.createElement('img');
    // Logo del formato (del PDF)
    logo.src = '../assets/constancia_pdf_imgs/x10.png';
    logo.alt = 'IDEE';
    logo.style.width = '180px';
    logo.style.height = 'auto';
    logo.style.objectFit = 'contain';
    hdrLeft.appendChild(logo);

    var aQuienEl = document.createElement('div');
    aQuienEl.style.fontSize = '14px';
    aQuienEl.style.fontWeight = '700';
    aQuienEl.textContent = gen.aQuien;
    hdrLeft.appendChild(aQuienEl);

    header.appendChild(hdrLeft);

    var hdrRight = document.createElement('div');
    hdrRight.style.textAlign = 'right';
    hdrRight.style.fontSize = '12px';
    hdrRight.style.lineHeight = '1.32';
    hdrRight.style.paddingTop = '8px';
    hdrRight.innerHTML =
      '<div style="font-weight:700;font-size:13px;">' + escapeHtml(nombrePlantel) + '</div>' +
      '<div style="margin-top:2px;"><strong>C.C.T.</strong> ' + escapeHtml(claveCct) + '</div>' +
      '<div style="margin-top:6px;">Asunto: <strong>' + escapeHtml(asunto) + '</strong></div>' +
      '<div><strong>Mérida, Yucatán.</strong></div>';
    header.appendChild(hdrRight);
    wrap.appendChild(header);

    // Cuerpo
    var body = document.createElement('div');
    body.style.fontSize = '13px';
    body.style.lineHeight = '1.5';
    body.style.whiteSpace = 'normal';
    body.style.minHeight = '360px';
    body.style.position = 'relative';
    body.style.zIndex = '1';

    var nombreAlumno = nombreCompletoAlumno(alumno);

    body.innerHTML =
      '<div style="margin-bottom:12px;">Por medio de la presente, se hace constar que ' + escapeHtml(gen.elLa) + ' <strong>' + escapeHtml(nombreAlumno) + '</strong> es ' + escapeHtml(gen.alumnaAlumno) + ' regular ' + escapeHtml(artProg) + ' <strong>' + escapeHtml(tipoProg) + '</strong> en <strong>' + escapeHtml(nombrePrograma) + '</strong> del ' + escapeHtml(nombrePlantel) + '.</div>' +
      '<div style="margin-bottom:12px;">' + escapeHtml(gen.actualmente) + ' se encuentra ' + escapeHtml(gen.inscritaInscrito) + ' en el <strong>' + escapeHtml(periodoTxt) + '</strong> de la ' + escapeHtml(tipoProg) + ', la cual tiene una duración total de <strong>' + escapeHtml(durTxt) + '</strong> (' + escapeHtml(aniosTxt) + ' años).</div>' +
      '<div style="margin-bottom:12px;">Se hace constar que ' + escapeHtml(gen.elLa) + ' se encuentra cursando y acreditando los estudios correspondientes al plan académico vigente, y que una vez concluida y acreditada la totalidad de los créditos, estará en posibilidad de obtener el título de la ' + escapeHtml(tipoProg) + ', conforme a la normatividad institucional.</div>' +
      '<div style="margin-bottom:18px;">Se expide la presente constancia a solicitud de ' + escapeHtml(gen.interesado) + ', para los fines que estime pertinentes, en Mérida, Yucatán, a los <strong>' + String(f.dia).padStart(2, '0') + '</strong> días del mes de <strong>' + escapeHtml(f.mes) + '</strong> de <strong>' + String(f.anio) + '</strong>.</div>';

    // Si es con calificaciones, agregar una tabla simple al final
    if (tipo === 'CALIFICACIONES' && opts.historial && Array.isArray(opts.historial) && opts.historial.length) {
      var filas = opts.historial.slice(0, 30); // tope razonable para PDF
      var tbl = '<table style="width:100%;border-collapse:collapse;font-size:11.5px;margin-top:10px;">' +
        '<thead><tr>' +
        '<th style="text-align:left;border-bottom:1px solid #ddd;padding:6px 6px;">Asignatura</th>' +
        '<th style="text-align:center;border-bottom:1px solid #ddd;padding:6px 6px;width:90px;">Calif.</th>' +
        '</tr></thead><tbody>';
      filas.forEach(function (r) {
        var asig = (r && (r.asignaturaNombre || r.nombreAsignatura || r.asignatura)) ? (r.asignaturaNombre || r.nombreAsignatura || r.asignatura) : (r && r.nombre ? r.nombre : '—');
        var cal = (r && r.calificacionFinal != null) ? r.calificacionFinal : (r && r.calificacion != null ? r.calificacion : '—');
        tbl += '<tr>' +
          '<td style="border-bottom:1px solid #f0f0f0;padding:6px 6px;">' + escapeHtml(asig) + '</td>' +
          '<td style="border-bottom:1px solid #f0f0f0;padding:6px 6px;text-align:center;">' + escapeHtml(cal) + '</td>' +
        '</tr>';
      });
      tbl += '</tbody></table>';
      body.innerHTML += tbl;
    }

    wrap.appendChild(body);

    // Sección final con marca de agua detrás
    var firma = document.createElement('div');
    firma.style.position = 'relative';
    firma.style.marginTop = '34px';
    firma.style.paddingTop = '8px';
    firma.style.minHeight = '170px';
    firma.style.zIndex = '1';

    var watermark = document.createElement('div');
    watermark.style.position = 'absolute';
    watermark.style.left = '0';
    watermark.style.right = '0';
    watermark.style.bottom = '0';
    watermark.style.top = '0';
    watermark.style.pointerEvents = 'none';
    watermark.style.zIndex = '0';
    // La imagen grande va pegada al fondo de la hoja (bgBottom). Aquí solo se mantiene un leve apoyo visual.
    watermark.style.background = 'transparent';
    firma.appendChild(watermark);

    var firmaInner = document.createElement('div');
    firmaInner.style.position = 'relative';
    firmaInner.style.zIndex = '1';
    firmaInner.style.display = 'grid';
    firmaInner.style.gridTemplateColumns = '1fr 1fr';
    firmaInner.style.columnGap = '18px';
    firmaInner.style.fontSize = '13px';
    firmaInner.style.lineHeight = '1.6';

    // Izquierda: atentamente + firma + cc.p
    var left = document.createElement('div');
    left.innerHTML =
      '<div><strong>Atentamente,</strong></div>' +
      // Espacio extra (2 saltos) antes de la línea de firma
      '<div style="height:72px;"></div>' +
      '<div>________________________</div>' +
      '<div><strong>C.D.E.O Carlos Conrado Alamilla Bazán</strong></div>' +
      '<div><strong>Director General</strong></div>' +
      '<div style="height:18px;"></div>' +
      '<div>C.c.p Archivo</div>';

    // Derecha: datos contacto en esquina inferior derecha (dentro del margen)
    var right = document.createElement('div');
    right.style.textAlign = 'right';
    right.style.position = 'absolute';
    right.style.right = '0';
    right.style.bottom = '0';
    right.innerHTML = [
      '<div style="display:flex;justify-content:flex-end;gap:8px;align-items:center;">',
      '  <span>(999) 374 19 91</span><i class="bi bi-telephone"></i>',
      '</div>',
      '<div style="display:flex;justify-content:flex-end;gap:8px;align-items:center;">',
      '  <span>academico@idee.edu.mx</span><i class="bi bi-envelope"></i>',
      '</div>',
      '<div style="display:flex;justify-content:flex-end;gap:8px;align-items:center;">',
      '  <span>Calle 65 #638a por 82 y 84 centro</span><i class="bi bi-geo-alt"></i>',
      '</div>'
    ].join('');

    firmaInner.appendChild(left);
    firmaInner.appendChild(right);
    firma.appendChild(firmaInner);
    wrap.appendChild(firma);

    return wrap;
  }

  async function generarPdf() {
    if (!alumnoSeleccionado) {
      mostrarFeedback('Seleccione un alumno.', 'warning');
      return;
    }
    mostrarFeedback('', 'info');

    var btn = document.getElementById('btnGenerarConstancia');
    if (btn) btn.disabled = true;
    try {
      var tipo = (document.getElementById('constTipo') || {}).value || 'REGULAR';
      // Asegurar programa completo
      var programaFull = (alumnoSeleccionado && alumnoSeleccionado.programa) ? alumnoSeleccionado.programa : null;
      try {
        var pid = programaFull && programaFull.id != null ? programaFull.id : null;
        if (pid != null) {
          var resProg = await apiFetch('/programas-educativos/' + encodeURIComponent(String(pid)), { method: 'GET' });
          if (resProg && resProg.ok) {
            var pjson = await resProg.json();
            if (pjson && pjson.id != null) programaFull = pjson;
          }
        }
      } catch (_) {}

      // Resolver plantel por programa (endpoint dedicado con permiso VER_CONSTANCIAS)
      var plantel = null;
      try {
        var pid2 = programaFull && programaFull.id != null ? programaFull.id : null;
        if (pid2 != null) {
          var resPl = await apiFetch('/programas-educativos/' + encodeURIComponent(String(pid2)) + '/plantel', { method: 'GET' });
          if (resPl && resPl.ok) {
            plantel = await resPl.json();
          }
        }
      } catch (_) {}
      // Fallback: si se puede, intentar por catálogo de planteles (cuando está disponible)
      if (!plantel) {
        var alumnoForPlantel = Object.assign({}, alumnoSeleccionado, { programa: programaFull || alumnoSeleccionado.programa });
        plantel = encontrarPlantelPorAlumno(alumnoForPlantel);
      }

      var kardex = await obtenerPeriodoCursando(alumnoSeleccionado.id);

      // Historial opcional para constancia con calificaciones
      var historial = null;
      if (tipo === 'CALIFICACIONES') {
        try {
          var resH = await apiFetch('/kardex/' + encodeURIComponent(String(alumnoSeleccionado.id)) + '/historial-calificaciones', { method: 'GET' });
          if (resH.ok) historial = await resH.json();
        } catch (_) { historial = null; }
      }

      var nodo = construirNodoConstancia({
        alumno: Object.assign({}, alumnoSeleccionado, { programa: programaFull || alumnoSeleccionado.programa }),
        plantel: plantel,
        tipo: tipo,
        kardex: kardex,
        historial: historial
      });

      var host = document.getElementById('constanciaPdfHost');
      if (!host) throw new Error('No existe host de PDF');
      host.innerHTML = '';
      host.classList.remove('d-none');
      host.appendChild(nodo);

      if (typeof html2canvas === 'undefined') throw new Error('No se cargó html2canvas');
      var jsPDF = (window.jspdf && window.jspdf.jsPDF) ? window.jspdf.jsPDF : null;
      if (!jsPDF) throw new Error('No se cargó jsPDF');

      var canvas = await html2canvas(nodo, {
        backgroundColor: '#ffffff',
        scale: 2,
        useCORS: true,
        allowTaint: true,
        logging: false,
        scrollX: 0,
        scrollY: -window.scrollY
      });
      var imgData = canvas.toDataURL('image/png');

      var pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
      var pageW = pdf.internal.pageSize.getWidth();
      var pageH = pdf.internal.pageSize.getHeight();
      // Forzar 1 sola hoja: ajustar contenido a A4 (sin paginado)
      var imgW = pageW;
      var imgH = (canvas.height * imgW) / canvas.width;
      var x = 0;
      var y0 = 0;
      if (imgH > pageH) {
        imgH = pageH;
        imgW = (canvas.width * imgH) / canvas.height;
        x = (pageW - imgW) / 2;
      }
      pdf.addImage(imgData, 'PNG', x, y0, imgW, imgH, undefined, 'FAST');

      var base = ('constancia_' + (alumnoSeleccionado.matricula || alumnoSeleccionado.id || 'alumno')).replace(/[\\\/:*?"<>|]+/g, '-');

      // Vista previa (no descargar de inmediato)
      try {
        if (_constanciaPreviewUrl && _constanciaPreviewUrl.indexOf('blob:') === 0) {
          URL.revokeObjectURL(_constanciaPreviewUrl);
        }
      } catch (_) {}
      var blob = pdf.output('blob');
      _constanciaPreviewUrl = URL.createObjectURL(blob);
      _constanciaPreviewFilename = base + '.pdf';

      var frame = document.getElementById('constanciaPreviewFrame');
      if (frame) frame.src = _constanciaPreviewUrl;
      var hint = document.getElementById('constanciaPreviewHint');
      if (hint) hint.textContent = 'Archivo: ' + _constanciaPreviewFilename;
      var dlBtn = document.getElementById('btnDescargarConstanciaPdf');
      if (dlBtn) dlBtn.disabled = false;

      var modalEl = document.getElementById('modalConstanciaPreview');
      if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
      }

      host.classList.add('d-none');
    } catch (e) {
      console.error(e);
      mostrarFeedback('No se pudo generar la constancia. ' + (e && e.message ? e.message : ''), 'danger');
    } finally {
      if (btn) btn.disabled = !alumnoSeleccionado;
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('constanciasSection')) return;

    var f1 = document.getElementById('constFiltroAlumno');
    var f2 = document.getElementById('constFiltroPrograma');
    var f3 = document.getElementById('constFiltroEstatus');
    if (f1) f1.addEventListener('input', renderLista);
    if (f2) f2.addEventListener('change', renderLista);
    if (f3) f3.addEventListener('change', renderLista);

    var btn = document.getElementById('btnGenerarConstancia');
    if (btn) btn.addEventListener('click', generarPdf);

    // Descargar desde la vista previa
    var btnDl = document.getElementById('btnDescargarConstanciaPdf');
    if (btnDl && !btnDl.__bound) {
      btnDl.__bound = true;
      btnDl.addEventListener('click', function () {
        if (!_constanciaPreviewUrl) return;
        var a = document.createElement('a');
        a.href = _constanciaPreviewUrl;
        a.download = _constanciaPreviewFilename || 'constancia.pdf';
        document.body.appendChild(a);
        a.click();
        a.remove();
      });
    }

    // Limpiar blob al cerrar modal
    var modalEl = document.getElementById('modalConstanciaPreview');
    if (modalEl && !modalEl.__boundCleanup) {
      modalEl.__boundCleanup = true;
      modalEl.addEventListener('hidden.bs.modal', function () {
        var frame = document.getElementById('constanciaPreviewFrame');
        if (frame) frame.src = 'about:blank';
        var dlBtn = document.getElementById('btnDescargarConstanciaPdf');
        if (dlBtn) dlBtn.disabled = true;
        try {
          if (_constanciaPreviewUrl && _constanciaPreviewUrl.indexOf('blob:') === 0) {
            URL.revokeObjectURL(_constanciaPreviewUrl);
          }
        } catch (_) {}
        _constanciaPreviewUrl = null;
        _constanciaPreviewFilename = null;
      });
    }

    cargarDatos();
  });
})();


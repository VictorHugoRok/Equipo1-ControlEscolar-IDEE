/**
 * Kardex académico: resumen e historial de calificaciones (personal o alumno autenticado).
 */
(function () {
  'use strict';

  var API = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
  var alumnosCache = [];
  var TIPOS_PLAN = { OBLIGATORIA: true, OPTATIVA: true, LIBRE: true };
  var lastKardexResumen = null;

  function tipoUsuarioActual() {
    if (window.currentUser && window.currentUser.tipoUsuario) return String(window.currentUser.tipoUsuario);
    var t = localStorage.getItem('userTipo');
    return t ? String(t) : '';
  }

  function esVistaKardexAlumno() {
    return tipoUsuarioActual() === 'ALUMNO';
  }

  function headers() {
    var h = { 'Content-Type': 'application/json' };
    var token = localStorage.getItem('token');
    if (token && token !== 'null') h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  function authHeadersSinContentType() {
    var h = {};
    var token = localStorage.getItem('token');
    if (token && token !== 'null') h['Authorization'] = 'Bearer ' + token;
    return h;
  }

  async function apiFetch(path, options) {
    var url = path.indexOf('/') === 0 ? API + path : API + '/' + path;
    return fetch(url, Object.assign({}, options || {}, { headers: Object.assign(headers(), (options && options.headers) || {}) }));
  }

  function splitQuery(path) {
    var p = String(path || '');
    var q = p.indexOf('?');
    if (q === -1) return { base: p, qs: '' };
    return { base: p.slice(0, q), qs: p.slice(q) };
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function vOrDash(v) {
    return (v == null || v === '') ? '—' : String(v);
  }

  function safeFilename(s) {
    return String(s || '')
      .trim()
      .replace(/[\\\/:*?"<>|]+/g, '-')
      .replace(/\s+/g, ' ')
      .replace(/^\.+/, '')
      .slice(0, 120) || 'kardex';
  }

  async function descargarElementoComoPdf(elemento, filenameBase, opts) {
    if (!elemento) throw new Error('No hay contenido para exportar');
    if (typeof html2canvas === 'undefined') throw new Error('No se cargó html2canvas');
    var jsPDF = (window.jspdf && window.jspdf.jsPDF) ? window.jspdf.jsPDF : null;
    if (!jsPDF) throw new Error('No se cargó jsPDF');
    opts = opts || {};

    // Render a canvas del tamaño real del elemento (mejor calidad en texto/lineas)
    var canvas = await html2canvas(elemento, {
      backgroundColor: '#ffffff',
      scale: 2,
      useCORS: true,
      allowTaint: true,
      logging: false,
      scrollX: 0,
      scrollY: -window.scrollY
    });
    var imgData = canvas.toDataURL('image/png');

    var orientation = opts.orientation || 'landscape';
    var pdf = new jsPDF({ orientation: orientation, unit: 'mm', format: 'a4' });
    var pageW = pdf.internal.pageSize.getWidth();
    var pageH = pdf.internal.pageSize.getHeight();

    // Ajuste a página manteniendo proporción
    var imgW = pageW;
    var imgH = (canvas.height * imgW) / canvas.width;

    // Forzar 1 página: escalar para que quepa completo (sin paginar)
    if (opts.forceSinglePage) {
      var w = imgW;
      var h = imgH;
      if (h > pageH) {
        h = pageH;
        w = (canvas.width * h) / canvas.height;
      }
      var x = Math.max(0, (pageW - w) / 2);
      var y = 0;
      pdf.addImage(imgData, 'PNG', x, y, w, h, undefined, 'FAST');
    }
    // Si excede alto de la hoja, paginar por cortes verticales
    else if (imgH <= pageH) {
      pdf.addImage(imgData, 'PNG', 0, 0, imgW, imgH, undefined, 'FAST');
    } else {
      // Cortes en px por página equivalente
      var pxPerMm = canvas.width / pageW;
      var pagePxH = Math.floor(pageH * pxPerMm);
      var y = 0;
      var page = 0;
      while (y < canvas.height) {
        var sliceH = Math.min(pagePxH, canvas.height - y);
        var slice = document.createElement('canvas');
        slice.width = canvas.width;
        slice.height = sliceH;
        var ctx = slice.getContext('2d');
        ctx.drawImage(canvas, 0, y, canvas.width, sliceH, 0, 0, canvas.width, sliceH);
        var sliceData = slice.toDataURL('image/png');
        var sliceMmH = (sliceH / pxPerMm);
        if (page > 0) pdf.addPage();
        pdf.addImage(sliceData, 'PNG', 0, 0, pageW, sliceMmH, undefined, 'FAST');
        y += sliceH;
        page++;
      }
    }

    var base = safeFilename(filenameBase);
    pdf.save(base + '.pdf');
  }

  function crearNodoExportKardexGrafico(k) {
    var wrap = document.createElement('div');
    wrap.setAttribute('data-kardex-export', 'grafico');
    wrap.style.background = '#ffffff';
    wrap.style.color = '#111';
    wrap.style.fontFamily = 'system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';
    wrap.style.position = 'relative';
    // Compacto para que el resumen quepa en 1 hoja (landscape)
    wrap.style.padding = '10px 12px 8px 12px';
    wrap.style.width = '1120px'; // ancho fijo para buen render a PDF horizontal
    wrap.style.boxSizing = 'border-box';

    // Encabezado: logo+generado no forman parte del flujo vertical (absolute) para que
    // el título y los datos del alumno sigan compactos y no "bajen" con la altura del logo.
    var headSecG = document.createElement('div');
    headSecG.style.position = 'relative';
    headSecG.style.paddingRight = '200px';
    // Altura mínima = bloque del logo+generado; si el texto izq. es más alto, el bloque crece sin añadir hueco extra.
    headSecG.style.minHeight = '102px';
    headSecG.style.boxSizing = 'border-box';
    headSecG.style.marginBottom = '6px';

    var leftH = document.createElement('div');
    leftH.style.fontSize = '15px';
    leftH.style.fontWeight = '700';
    leftH.textContent = 'Kardex Académico';

    var subt = document.createElement('div');
    // Línea de identificación: 3 datos juntos, un poco más grande y legible
    subt.style.fontSize = '13px';
    subt.style.color = '#333';
    subt.style.marginTop = '6px';
    subt.innerHTML =
      '<div><strong>Matrícula:</strong> ' + escapeHtml(vOrDash(k && k.matricula)) +
      ' &nbsp; <strong>Nombre:</strong> ' + escapeHtml(vOrDash(k && k.nombre)) +
      ' &nbsp; <strong>CURP:</strong> ' + escapeHtml(vOrDash(k && k.curp)) + '</div>';

    var rightCol = document.createElement('div');
    rightCol.style.position = 'absolute';
    rightCol.style.top = '0';
    rightCol.style.right = '0';
    rightCol.style.display = 'flex';
    rightCol.style.flexDirection = 'column';
    rightCol.style.alignItems = 'flex-end';
    rightCol.style.width = '200px';
    var logoG = document.createElement('img');
    logoG.src = '../assets/favicon.png';
    logoG.alt = 'Logo';
    logoG.style.width = '72px';
    logoG.style.height = '72px';
    logoG.style.objectFit = 'contain';
    logoG.style.opacity = '0.95';
    logoG.style.display = 'block';
    var genG = document.createElement('div');
    genG.style.fontSize = '10px';
    genG.style.color = '#555';
    genG.style.marginTop = '4px';
    genG.style.lineHeight = '1.2';
    genG.style.textAlign = 'right';
    genG.textContent = 'Generado: ' + new Date().toLocaleString();
    rightCol.appendChild(logoG);
    rightCol.appendChild(genG);
    headSecG.appendChild(leftH);
    headSecG.appendChild(subt);
    headSecG.appendChild(rightCol);
    wrap.appendChild(headSecG);

    var grid = document.createElement('div');
    grid.style.display = 'grid';
    // 4 bloques en una fila para reducir alto
    grid.style.gridTemplateColumns = '1fr 1fr 1fr 1fr';
    grid.style.gap = '6px 10px';
    grid.style.borderTop = '1px solid #e5e7eb';
    grid.style.paddingTop = '6px';
    grid.style.marginBottom = '8px';

    function bloque(tituloTxt, rows) {
      var b = document.createElement('div');
      var h = document.createElement('div');
      h.textContent = tituloTxt;
      h.style.fontWeight = '700';
      h.style.fontSize = '10px';
      h.style.marginBottom = '4px';
      b.appendChild(h);
      var tbl = document.createElement('table');
      tbl.style.width = '100%';
      tbl.style.borderCollapse = 'collapse';
      tbl.style.fontSize = '9.5px';
      rows.forEach(function (r) {
        var tr = document.createElement('tr');
        var td1 = document.createElement('td');
        td1.style.padding = '1px 6px 1px 0';
        td1.style.color = '#555';
        td1.style.width = '46%';
        td1.textContent = r[0];
        var td2 = document.createElement('td');
        td2.style.padding = '1px 0';
        td2.style.fontWeight = '600';
        td2.textContent = vOrDash(r[1]);
        tr.appendChild(td1);
        tr.appendChild(td2);
        tbl.appendChild(tr);
      });
      b.appendChild(tbl);
      return b;
    }

    grid.appendChild(bloque('Programa', [
      ['Programa de estudios', k && k.programaEstudios],
      ['Plan de estudios', k && k.planEstudios],
      ['Modalidad', k && k.modalidad]
    ]));

    grid.appendChild(bloque('Periodos y estatus', [
      ['Periodo de ingreso', k && k.periodoIngreso],
      ['Periodo actual', k && k.periodoActual],
      ['Periodo de egreso', k && k.periodoEgreso],
      ['Situación / Estatus', k && k.situacionEstatus]
    ]));

    grid.appendChild(bloque('Créditos y avance', [
      ['Créditos del plan', k && k.creditosPlan],
      ['Créditos aprobados', k && k.creditosAprobados],
      ['Porcentaje aprobado', (k && k.porcentajeAprobado != null) ? (String(k.porcentajeAprobado) + '%') : null]
    ]));

    grid.appendChild(bloque('Promedios', [
      ['Materias totales', k && k.materiasTotales],
      ['Materias aprobadas', k && k.materiasAprobadas],
      ['Promedio general', k && k.promedioGeneral],
      ['Promedio del periodo', k && k.promedioPeriodo]
    ]));

    wrap.appendChild(grid);

    var sep = document.createElement('div');
    sep.style.borderTop = '1px solid #e5e7eb';
    sep.style.margin = '3px 0 4px 0';
    wrap.appendChild(sep);

    var host = document.createElement('div');
    host.id = 'kardexExportMatrizHost';
    wrap.appendChild(host);

    return wrap;
  }

  function crearNodoExportKardexLista(k) {
    var wrap = document.createElement('div');
    wrap.setAttribute('data-kardex-export', 'lista');
    wrap.style.background = '#ffffff';
    wrap.style.color = '#111';
    wrap.style.fontFamily = 'system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';
    wrap.style.position = 'relative';
    wrap.style.padding = '12px 12px 10px 12px';
    wrap.style.width = '780px'; // ancho cómodo para A4 vertical
    wrap.style.boxSizing = 'border-box';

    var headSecL = document.createElement('div');
    headSecL.style.position = 'relative';
    headSecL.style.paddingRight = '200px';
    headSecL.style.minHeight = '102px';
    headSecL.style.boxSizing = 'border-box';
    headSecL.style.marginBottom = '8px';

    var leftHL = document.createElement('div');
    leftHL.style.fontSize = '14px';
    leftHL.style.fontWeight = '700';
    leftHL.textContent = 'Kardex Académico';

    var ident = document.createElement('div');
    ident.style.fontSize = '11.5px';
    ident.style.color = '#333';
    ident.style.marginTop = '6px';
    ident.innerHTML =
      '<div><strong>Matrícula:</strong> ' + escapeHtml(vOrDash(k && k.matricula)) +
      ' &nbsp; <strong>Nombre:</strong> ' + escapeHtml(vOrDash(k && k.nombre)) + '</div>' +
      '<div><strong>CURP:</strong> ' + escapeHtml(vOrDash(k && k.curp)) + '</div>';

    var rightColL = document.createElement('div');
    rightColL.style.position = 'absolute';
    rightColL.style.top = '0';
    rightColL.style.right = '0';
    rightColL.style.display = 'flex';
    rightColL.style.flexDirection = 'column';
    rightColL.style.alignItems = 'flex-end';
    rightColL.style.width = '200px';
    var logoL = document.createElement('img');
    logoL.src = '../assets/favicon.png';
    logoL.alt = 'Logo';
    logoL.style.width = '72px';
    logoL.style.height = '72px';
    logoL.style.objectFit = 'contain';
    logoL.style.opacity = '0.95';
    logoL.style.display = 'block';
    var genL = document.createElement('div');
    genL.style.fontSize = '10px';
    genL.style.color = '#555';
    genL.style.marginTop = '4px';
    genL.style.lineHeight = '1.2';
    genL.style.textAlign = 'right';
    genL.textContent = 'Generado: ' + new Date().toLocaleString();
    rightColL.appendChild(logoL);
    rightColL.appendChild(genL);
    headSecL.appendChild(leftHL);
    headSecL.appendChild(ident);
    headSecL.appendChild(rightColL);
    wrap.appendChild(headSecL);

    var grid = document.createElement('div');
    grid.style.display = 'grid';
    grid.style.gridTemplateColumns = '1fr 1fr';
    grid.style.gap = '6px 14px';
    grid.style.borderTop = '1px solid #e5e7eb';
    grid.style.paddingTop = '8px';
    grid.style.marginBottom = '10px';

    function bloque(tituloTxt, rows) {
      var b = document.createElement('div');
      var h = document.createElement('div');
      h.textContent = tituloTxt;
      h.style.fontWeight = '700';
      h.style.fontSize = '10.5px';
      h.style.marginBottom = '4px';
      b.appendChild(h);
      var tbl = document.createElement('table');
      tbl.style.width = '100%';
      tbl.style.borderCollapse = 'collapse';
      tbl.style.fontSize = '9.5px';
      rows.forEach(function (r) {
        var tr = document.createElement('tr');
        var td1 = document.createElement('td');
        td1.style.padding = '1px 6px 1px 0';
        td1.style.color = '#555';
        td1.style.width = '46%';
        td1.textContent = r[0];
        var td2 = document.createElement('td');
        td2.style.padding = '1px 0';
        td2.style.fontWeight = '600';
        td2.textContent = vOrDash(r[1]);
        tr.appendChild(td1);
        tr.appendChild(td2);
        tbl.appendChild(tr);
      });
      b.appendChild(tbl);
      return b;
    }

    grid.appendChild(bloque('Programa', [
      ['Programa de estudios', k && k.programaEstudios],
      ['Plan de estudios', k && k.planEstudios],
      ['Modalidad', k && k.modalidad]
    ]));

    grid.appendChild(bloque('Periodos y estatus', [
      ['Periodo de ingreso', k && k.periodoIngreso],
      ['Periodo actual', k && k.periodoActual],
      ['Periodo de egreso', k && k.periodoEgreso],
      ['Situación / Estatus', k && k.situacionEstatus]
    ]));

    grid.appendChild(bloque('Créditos y avance', [
      ['Créditos del plan', k && k.creditosPlan],
      ['Créditos aprobados', k && k.creditosAprobados],
      ['Porcentaje aprobado', (k && k.porcentajeAprobado != null) ? (String(k.porcentajeAprobado) + '%') : null]
    ]));

    grid.appendChild(bloque('Promedios', [
      ['Materias totales', k && k.materiasTotales],
      ['Materias aprobadas', k && k.materiasAprobadas],
      ['Promedio general', k && k.promedioGeneral],
      ['Promedio del periodo', k && k.promedioPeriodo]
    ]));

    wrap.appendChild(grid);

    var sep = document.createElement('div');
    sep.style.borderTop = '1px solid #e5e7eb';
    sep.style.margin = '4px 0 8px 0';
    wrap.appendChild(sep);

    var host = document.createElement('div');
    host.id = 'kardexExportListaHost';
    wrap.appendChild(host);

    return wrap;
  }

  async function descargarKardexListaPdf() {
    var tabla = document.querySelector('#kardexVistaLista table');
    if (!tabla) throw new Error('No hay tabla de historial para exportar. Consulta un alumno.');
    var exportNode = crearNodoExportKardexLista(lastKardexResumen || {});
    var host = exportNode.querySelector('#kardexExportListaHost');
    if (host) {
      var clone = tabla.cloneNode(true);
      clone.style.background = '#fff';
      clone.style.color = '#111';
      // Compactar un poco para PDF
      clone.style.fontSize = '9px';
      host.appendChild(clone);
    }

    var stage = document.createElement('div');
    stage.style.position = 'fixed';
    stage.style.left = '-20000px';
    stage.style.top = '0';
    stage.style.width = exportNode.style.width;
    stage.style.zIndex = '-1';
    stage.appendChild(exportNode);
    document.body.appendChild(stage);
    try {
      var nombre = 'kardex_lista_' + (lastKardexResumen && lastKardexResumen.matricula ? String(lastKardexResumen.matricula) : '');
      await descargarElementoComoPdf(exportNode, nombre, { orientation: 'portrait' });
    } finally {
      stage.remove();
    }
  }

  async function descargarKardexGraficoPdf() {
    var matriz = document.querySelector('#kardexMatrizContenido table');
    if (!matriz) throw new Error('No hay matriz para exportar. Consulta un alumno y abre la vista Gráfico.');
    var exportNode = crearNodoExportKardexGrafico(lastKardexResumen || {});
    var host = exportNode.querySelector('#kardexExportMatrizHost');
    if (host) {
      var clone = matriz.cloneNode(true);
      // Ajustes para PDF (fondo blanco + texto legible)
      clone.style.background = '#fff';
      clone.style.color = '#111';
      host.appendChild(clone);
    }

    // Montar fuera de pantalla para render
    var stage = document.createElement('div');
    stage.style.position = 'fixed';
    stage.style.left = '-20000px';
    stage.style.top = '0';
    stage.style.width = exportNode.style.width;
    stage.style.zIndex = '-1';
    stage.appendChild(exportNode);
    document.body.appendChild(stage);
    try {
      var nombre = 'kardex_grafico_' + (lastKardexResumen && lastKardexResumen.matricula ? String(lastKardexResumen.matricula) : '');
      // Ajustar a 1 hoja: si se pasa "un poco", se escala para que quepa completa.
      await descargarElementoComoPdf(exportNode, nombre, { forceSinglePage: true, orientation: 'landscape' });
    } finally {
      stage.remove();
    }
  }

  function normalizarBusqueda(s) {
    if (s == null) return '';
    try {
      return String(s)
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
    } catch (_) {
      return String(s).toLowerCase().trim();
    }
  }

  function badgeEstatus(estatus) {
    var e = (estatus || '').toUpperCase();
    var cls = e === 'ACTIVA' ? 'bg-success-subtle text-success'
      : e === 'BAJA_TEMPORAL' ? 'bg-warning-subtle text-warning'
        : e === 'BAJA_DEFINITIVA' ? 'bg-danger-subtle text-danger'
          : e === 'EGRESADO' ? 'bg-info-subtle text-info'
            : 'bg-secondary-subtle text-secondary';
    var estatusPlano = formatearEstatusFrontend(estatus);
    var label = e === 'ACTIVA' ? 'Activa'
      : e === 'BAJA_TEMPORAL' ? 'Baja temporal'
          : e === 'BAJA_DEFINITIVA' ? 'Baja definitiva'
            : e === 'EGRESADO' ? 'Egresado'
              : estatusPlano;
    return '<span class="badge ' + cls + '">' + escapeHtml(label) + '</span>';
  }

  function formatearEstatusFrontend(estatus) {
    if (estatus == null || estatus === '') return '—';
    return String(estatus).replace(/_/g, ' ');
  }

  var alumnoSeleccionadoId = null;

  /** Ordinales en palabra + tipo de periodo del programa (misma forma siempre: "Primer semestre", "Segundo cuatrimestre", …). */
  var ORDINAL_PALABRA_PLAN = [
    '',
    'Primer', 'Segundo', 'Tercer', 'Cuarto', 'Quinto', 'Sexto', 'Séptimo', 'Octavo', 'Noveno', 'Décimo',
    'Undécimo', 'Duodécimo', 'Decimotercero', 'Decimocuarto', 'Decimoquinto', 'Decimosexto', 'Decimoséptimo', 'Decimoctavo', 'Decimonoveno', 'Vigésimo',
    'Vigésimo primero', 'Vigésimo segundo', 'Vigésimo tercero', 'Vigésimo cuarto', 'Vigésimo quinto',
    'Vigésimo sexto', 'Vigésimo séptimo', 'Vigésimo octavo', 'Vigésimo noveno', 'Trigésimo'
  ];

  function sufijoTipoPeriodoPlan(tipoPeriodo) {
    var t = (tipoPeriodo != null ? String(tipoPeriodo) : 'SEMESTRE').toUpperCase();
    if (t === 'SEMESTRE') return 'semestre';
    if (t === 'CUATRIMESTRE') return 'cuatrimestre';
    if (t === 'TETRAMESTRE') return 'tetramestre';
    if (t === 'TRIMESTRE') return 'trimestre';
    return 'periodo';
  }

  /** Misma etiqueta corta que en registrar asignaturas (p. ej. "1° Semestre"). */
  function tipoPeriodoLabelCorto(tipoPeriodo) {
    var t = (tipoPeriodo != null ? String(tipoPeriodo) : 'SEMESTRE').toUpperCase();
    if (t === 'SEMESTRE') return 'Semestre';
    if (t === 'CUATRIMESTRE') return 'Cuatrimestre';
    if (t === 'TETRAMESTRE') return 'Tetramestre';
    if (t === 'TRIMESTRE') return 'Trimestre';
    return 'Periodo';
  }

  function ordinalPalabraPlan(n) {
    if (n >= 1 && n < ORDINAL_PALABRA_PLAN.length) return ORDINAL_PALABRA_PLAN[n];
    return String(n) + 'º';
  }

  function etiquetaColumnaPlan(numNivel, tipoPeriodo) {
    return ordinalPalabraPlan(numNivel) + ' ' + sufijoTipoPeriodoPlan(tipoPeriodo);
  }

  function tipoPeriodoParaMatriz(kardex, alumnoSel) {
    if (kardex && kardex.tipoPeriodo != null) return kardex.tipoPeriodo;
    if (alumnoSel && alumnoSel.programa && alumnoSel.programa.tipoPeriodo != null) {
      return alumnoSel.programa.tipoPeriodo;
    }
    return null;
  }

  function celdaDatosAlumno(label, value) {
    return '<div class="datos-alumno-celda"><small class="text-muted d-block">' + escapeHtml(label) + '</small><span class="fw-semibold">' + escapeHtml(String(value != null ? value : '—')) + '</span></div>';
  }

  function fotoPlaceholderDataUri() {
    // SVG inline (no requiere assets). Fondo gris claro y silueta.
    var svg = [
      '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">',
      '<rect width="160" height="160" rx="18" fill="#f1f3f5"/>',
      '<circle cx="80" cy="62" r="28" fill="#ced4da"/>',
      '<path d="M32 140c8-28 32-42 48-42s40 14 48 42" fill="#ced4da"/>',
      '</svg>'
    ].join('');
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }

  /**
   * Carga la foto en el resumen del kardex.
   * @param {string|number|null} alumnoId — id numérico del alumno (vista staff) o null si se usa miFoto
   * @param {{ miFoto?: boolean }} [opts] — si miFoto es true, usa GET /alumnos/me/foto (alumno autenticado)
   */
  function setFotoAlumnoEnResumen(alumnoId, opts) {
    opts = opts || {};
    var usarMe = opts.miFoto === true;
    var img = document.getElementById('kardexAlumnoFotoImg');
    if (!img) return;
    var placeholder = fotoPlaceholderDataUri();
    try {
      var prev = img.getAttribute('data-blob-url');
      if (prev && prev.indexOf('blob:') === 0) URL.revokeObjectURL(prev);
    } catch (_) { }
    img.removeAttribute('data-blob-url');
    if (!alumnoId && !usarMe) {
      img.src = placeholder;
      img.removeAttribute('data-alumno-id');
      return;
    }
    if (alumnoId != null && !usarMe) {
      img.setAttribute('data-alumno-id', String(alumnoId));
    } else {
      img.removeAttribute('data-alumno-id');
    }
    var urlFoto = usarMe
      ? (API + '/alumnos/me/foto')
      : (API + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/foto');
    (async function () {
      try {
        var res = await fetch(urlFoto, {
          method: 'GET',
          headers: authHeadersSinContentType(),
          cache: 'no-store'
        });
        if (!res.ok) throw new Error('no-foto');
        var blob = await res.blob();
        if (!blob || blob.size === 0) throw new Error('no-foto');
        var url = URL.createObjectURL(blob);
        img.setAttribute('data-blob-url', url);
        img.src = url;
      } catch (_) {
        img.src = placeholder;
      }
    })();
  }

  function renderizarDatosAlumno(k, contenedor) {
    var periodoActual = k.periodoActual || k.periodoActualNum || '—';
    var porcentajeAprobado = k.porcentajeAprobado != null ? k.porcentajeAprobado + '%' : '—';
    var html = '<div class="kardex-resumen-academico">';
    html += '<div class="kardex-alumno-foto-wrap">' +
      '<img id="kardexAlumnoFotoImg" class="kardex-alumno-foto-img foto-alumno-clickeable" alt="Foto del alumno" src="' + fotoPlaceholderDataUri() + '" loading="lazy" title="Clic para ampliar" style="cursor:pointer;" />' +
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

  function abrirModalFotoKardexDesdeImg(imgEl) {
    if (!imgEl) return;
    var modal = document.getElementById('modalKardexFotoAlumno');
    var modalImg = document.getElementById('modalKardexFotoAlumnoImg');
    if (!modal || !modalImg) return;

    // Revocar blob previo del modal si aplica
    try {
      var prev = modalImg.getAttribute('data-blob-url');
      if (prev && prev.indexOf('blob:') === 0) URL.revokeObjectURL(prev);
    } catch (_) { }
    modalImg.removeAttribute('data-blob-url');

    var src = imgEl.getAttribute('data-blob-url') || imgEl.getAttribute('src') || '';
    if (!src) return;
    modalImg.src = src;
    if (src.indexOf('blob:') === 0) {
      modalImg.setAttribute('data-blob-url', src);
    }

    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
      bootstrap.Modal.getOrCreateInstance(modal).show();
    }
  }

  function renderHistorial(rows, tbody, tipoPeriodo, kardex, asignaturasPlan, alumnoSel) {
    var historialArr = Array.isArray(rows) ? rows : [];

    // Si tenemos plan, renderizar 1 fila por asignatura del plan (incluye no cursadas)
    if (asignaturasPlan && asignaturasPlan.length) {
      var filasPlan = filasAsignaturasParaMatriz(asignaturasPlan, historialArr);
      if (!filasPlan || !filasPlan.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay asignaturas del plan para mostrar.</td></tr>';
        return;
      }

      var rowsPorClave = agruparHistorialPorClave(historialArr);
      var selCtx = alumnoSel || alumnoStubDesdeKardex(kardex);
      var nivel = nivelAlumnoDesdeContexto(kardex, selCtx);

      function resumenUltimoIntento(grup) {
        if (!grup || !grup.length) return { periodo: '—' };
        var last = grup[grup.length - 1];
        return { periodo: last && last.periodo ? last.periodo : '—' };
      }

      function estatusTextoDesdeTipo(tipoCelda) {
        if (tipoCelda === 'aprobada') return 'APROBADO';
        if (tipoCelda === 'reprobada') return 'REPROBADO';
        if (tipoCelda === 'cursando') return 'CURSANDO';
        if (tipoCelda === 'sin_cursar') return 'PENDIENTE';
        return '—';
      }

      var lista = filasPlan.slice().sort(function (a, b) {
        var pa = a && a.periodoPlan != null ? parseInt(a.periodoPlan, 10) : 9999;
        var pb = b && b.periodoPlan != null ? parseInt(b.periodoPlan, 10) : 9999;
        if (pa !== pb) return pa - pb;
        return String(a.clave || '').localeCompare(String(b.clave || ''), 'es');
      });

      var html = '';
      var currentP = null;
      lista.forEach(function (fila) {
        var pNum = fila && fila.periodoPlan != null ? parseInt(fila.periodoPlan, 10) : null;
        var key = (pNum != null && !isNaN(pNum)) ? String(pNum) : 'sin';
        if (key !== currentP) {
          currentP = key;
          var etiqueta = key === 'sin'
            ? 'Sin periodo del plan'
            : etiquetaColumnaPlan(parseInt(key, 10), tipoPeriodo);
          html += '<tr class="kardex-list-group-header">' +
            '<td colspan="6" class="small fw-semibold text-secondary py-2">' + escapeHtml(etiqueta) + '</td>' +
            '</tr>';
        }

        var colK = pNum != null && !isNaN(pNum) ? pNum : null;
        var info = (colK != null) ? infoCeldaPlan(fila, colK, nivel, rowsPorClave) : { tipo: 'na', grup: [] };
        var ult = resumenUltimoIntento(info.grup);
        var estTxt = estatusTextoDesdeTipo(info.tipo === 'na' ? 'sin_cursar' : info.tipo);
        var calTxt = info.tipo === 'sin_cursar' ? '—' : calificacionTextoParaCelda(info.grup, info.tipo);
        var periodoEscolarTxt = (info.grup && info.grup.length) ? ult.periodo : '—';

        // Pendiente: si ya "debería" cursarse (col <= nivel) pero no hay historial
        if ((!info.grup || !info.grup.length) && colK != null && colK <= nivel) {
          estTxt = 'PENDIENTE';
          calTxt = 'Pendiente';
        }
        // Sin cursar (futuro): col > nivel
        if ((!info.grup || !info.grup.length) && colK != null && colK > nivel) {
          estTxt = 'SIN CURSAR';
          calTxt = '—';
        }

        html += '<tr>' +
          '<td>' + escapeHtml(periodoEscolarTxt) + '</td>' +
          '<td>' + escapeHtml(fila.clave || '—') + '</td>' +
          '<td>' + escapeHtml(fila.nombre || '—') + '</td>' +
          '<td>' + (fila.periodoPlan != null ? escapeHtml(String(fila.periodoPlan)) : '—') + '</td>' +
          '<td>' + escapeHtml(calTxt) + '</td>' +
          '<td>' + escapeHtml(formatearEstatusFrontend(estTxt)) + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = html;
      return;
    }

    // Fallback: historial tal cual (solo cursadas)
    if (!historialArr.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Sin calificaciones registradas hasta el momento.</td></tr>';
      return;
    }

    var listaHist = historialArr.slice().sort(function (a, b) {
      var pa = a && a.periodoAsignatura != null ? parseInt(a.periodoAsignatura, 10) : 9999;
      var pb = b && b.periodoAsignatura != null ? parseInt(b.periodoAsignatura, 10) : 9999;
      if (pa !== pb) return pa - pb;
      var ca = String((a && a.asignaturaClave) || '');
      var cb = String((b && b.asignaturaClave) || '');
      var ccmp = ca.localeCompare(cb, 'es');
      if (ccmp !== 0) return ccmp;
      return String((a && a.periodo) || '').localeCompare(String((b && b.periodo) || ''), 'es');
    });

    var html2 = '';
    var currentP2 = null;
    listaHist.forEach(function (r) {
      var pRaw = r && r.periodoAsignatura != null ? r.periodoAsignatura : null;
      var pNum2 = pRaw != null && pRaw !== '' ? parseInt(pRaw, 10) : null;
      var key2 = (pNum2 != null && !isNaN(pNum2)) ? String(pNum2) : 'sin';
      if (key2 !== currentP2) {
        currentP2 = key2;
        var etiqueta2 = key2 === 'sin'
          ? 'Sin periodo del plan'
          : etiquetaColumnaPlan(parseInt(key2, 10), tipoPeriodo);
        html2 += '<tr class="kardex-list-group-header">' +
          '<td colspan="6" class="small fw-semibold text-secondary py-2">' + escapeHtml(etiqueta2) + '</td>' +
          '</tr>';
      }
      html2 += '<tr>' +
        '<td>' + escapeHtml(r.periodo) + '</td>' +
        '<td>' + escapeHtml(r.asignaturaClave) + '</td>' +
        '<td>' + escapeHtml(r.asignaturaNombre) + '</td>' +
        '<td>' + (r.periodoAsignatura != null ? escapeHtml(String(r.periodoAsignatura)) : '—') + '</td>' +
        '<td>' + (r && r.bloqueadaPorEvaluacion === true
          ? 'Bloqueada'
          : (r.calificacionFinal != null ? escapeHtml(String(r.calificacionFinal)) : '—')) + '</td>' +
        '<td>' + escapeHtml(formatearEstatusFrontend(r.estatus)) + '</td>' +
        '</tr>';
    });
    tbody.innerHTML = html2;
  }

  function cuentaEnPlanFrontend(a) {
    // Compatibilidad: si en datos antiguos no viene "tipo", asumimos que cuenta en el plan.
    if (!a) return false;
    if (!a.tipo) return true;
    if (!TIPOS_PLAN[a.tipo]) return false;
    return true;
  }

  function filasAsignaturasParaMatriz(asignaturasPlan, historial) {
    if (asignaturasPlan && asignaturasPlan.length) {
      var filtradas = asignaturasPlan.filter(cuentaEnPlanFrontend).slice().sort(function (a, b) {
        var na = a.periodo && a.periodo.numero != null ? a.periodo.numero : 999;
        var nb = b.periodo && b.periodo.numero != null ? b.periodo.numero : 999;
        if (na !== nb) return na - nb;
        return String(a.clave || '').localeCompare(String(b.clave || ''), 'es');
      });
      return filtradas.map(function (a) {
        return {
          clave: String(a.clave || '').trim(),
          nombre: a.nombre != null ? a.nombre : '—',
          periodoPlan: a.periodo && a.periodo.numero != null ? a.periodo.numero : null,
          creditos: a.creditos != null ? a.creditos : null
        };
      });
    }
    var map = {};
    (historial || []).forEach(function (r) {
      var c = String(r.asignaturaClave || '').trim();
      if (!c) return;
      var pp = r.periodoAsignatura != null ? r.periodoAsignatura : 999;
      if (!map[c]) {
        map[c] = { clave: c, nombre: r.asignaturaNombre || c, periodoPlan: pp, creditos: null };
      } else {
        map[c].nombre = r.asignaturaNombre || map[c].nombre;
        if (map[c].periodoPlan === 999 && r.periodoAsignatura != null) {
          map[c].periodoPlan = r.periodoAsignatura;
        }
      }
    });
    return Object.keys(map).map(function (k) { return map[k]; }).sort(function (a, b) {
      if (a.periodoPlan !== b.periodoPlan) return a.periodoPlan - b.periodoPlan;
      return a.clave.localeCompare(b.clave, 'es');
    });
  }

  /** Todas las filas de historial por clave de asignatura (todos los periodos escolares). */
  function agruparHistorialPorClave(historial) {
    var g = {};
    (historial || []).forEach(function (r) {
      var c = String(r.asignaturaClave || '').trim();
      if (!c) return;
      if (!g[c]) g[c] = [];
      g[c].push(r);
    });
    return g;
  }

  /** Verde: calificación aún no cerrada por secretaría (PENDIENTE / CAPTURADA / EN_REVISION). */
  function estadoCeldaMatriz(groupRows) {
    if (!groupRows || !groupRows.length) return 'vacia';
    if (groupRows.some(function (r) { return r.estatus === 'APROBADO'; })) return 'aprobada';
    if (groupRows.some(function (r) { return r.estatus === 'REPROBADO'; })) return 'reprobada';
    var abierto = groupRows.some(function (r) {
      var e = r.estadoAprobacion;
      return e === 'PENDIENTE' || e === 'CAPTURADA' || e === 'EN_REVISION';
    });
    if (abierto) return 'cursando';
    return 'vacia';
  }

  function nivelAlumnoDesdeContexto(kardex, alumnoSel) {
    var n = kardex && kardex.periodoCursando != null ? parseInt(kardex.periodoCursando, 10) : NaN;
    if (!isNaN(n) && n > 0) return n;
    if (alumnoSel && alumnoSel.periodoCursando != null) {
      var n2 = parseInt(alumnoSel.periodoCursando, 10);
      if (!isNaN(n2) && n2 > 0) return n2;
    }
    return 1;
  }

  function maxColumnasPlanDesdeContexto(kardex, filas, nivelAlumno) {
    var d = kardex && kardex.duracionPeriodos != null ? parseInt(kardex.duracionPeriodos, 10) : NaN;
    if (!isNaN(d) && d > 0) return d;
    var m = 0;
    filas.forEach(function (f) {
      if (f.periodoPlan != null && f.periodoPlan > m) m = f.periodoPlan;
    });
    if (nivelAlumno != null && nivelAlumno > m) m = nivelAlumno;
    return m > 0 ? m : 10;
  }

  /**
   * Columnas = periodos 1..N del plan. Solo en la columna igual al periodo de la materia hay estado;
   * na = gris. sin_cursar = blanco (pendiente). aprobada / reprobada / cursando según historial y nivel.
   */
  function infoCeldaPlan(fila, colK, nivelAlumno, rowsPorClave) {
    var pPlan = fila.periodoPlan != null ? fila.periodoPlan : null;
    if (pPlan == null || colK !== pPlan) {
      return { tipo: 'na', grup: [] };
    }
    var grup = rowsPorClave[fila.clave] || [];
    if (colK > nivelAlumno) {
      return { tipo: 'sin_cursar', grup: grup };
    }
    var st = estadoCeldaMatriz(grup);
    if (st === 'vacia') {
      return { tipo: 'sin_cursar', grup: grup };
    }
    return { tipo: st, grup: grup };
  }

  function calificacionTextoParaCelda(grup, est) {
    if (!grup || !grup.length) return '—';
    // Bloqueo por Evaluación Docente (alumno): mostrar solo "Bloqueada" sin notas adicionales.
    if (grup.some(function (r) { return r && r.bloqueadaPorEvaluacion === true; })) {
      return 'Bloqueada';
    }
    if (est === 'cursando') {
      var i;
      for (i = grup.length - 1; i >= 0; i--) {
        if (grup[i].calificacionFinal != null) return String(grup[i].calificacionFinal);
      }
      return 'En curso';
    }
    var aprob = null;
    grup.forEach(function (r) {
      if (r.estatus === 'APROBADO') aprob = r;
    });
    if (aprob && aprob.calificacionFinal != null) return String(aprob.calificacionFinal);
    var rep = null;
    grup.forEach(function (r) {
      if (r.estatus === 'REPROBADO') rep = r;
    });
    if (rep && rep.calificacionFinal != null) return String(rep.calificacionFinal);
    var last = grup[grup.length - 1];
    if (last.calificacionFinal != null) return String(last.calificacionFinal);
    return '—';
  }

  function htmlCeldaPendienteKardex(fila) {
    var nom = escapeHtml(fila.nombre || fila.clave || '—');
    var clv = fila.clave ? escapeHtml(fila.clave) : '';
    return (
      '<div class="kardex-celda-pendiente">' +
      '<div class="kardex-celda-pendiente-nombre">' + nom + '</div>' +
      (clv ? '<div class="kardex-celda-pendiente-meta">' + clv + '</div>' : '') +
      '<div class="kardex-celda-pendiente-hint">Pendiente</div>' +
      '</div>'
    );
  }

  function nuevaFilaEmpaqueVacia(maxCol) {
    var r = new Array(maxCol);
    var i;
    for (i = 0; i < maxCol; i++) r[i] = null;
    return r;
  }

  /**
   * Varias materias por fila: cada una solo en su columna (periodo del plan).
   * Orden: por columna y orden del plan; greedy primera fila con hueco libre.
   */
  function empacarMateriasEnFilas(filas, maxCol) {
    var items = [];
    filas.forEach(function (fila, orden) {
      var p = fila.periodoPlan != null ? parseInt(fila.periodoPlan, 10) : NaN;
      if (isNaN(p) || p < 1 || p > maxCol) return;
      items.push({ fila: fila, colK: p, orden: orden });
    });
    items.sort(function (a, b) {
      if (a.colK !== b.colK) return a.colK - b.colK;
      return a.orden - b.orden;
    });
    var filasOut = [];
    items.forEach(function (it) {
      var idx = it.colK - 1;
      var colocada = false;
      var r;
      for (r = 0; r < filasOut.length; r++) {
        if (!filasOut[r][idx]) {
          filasOut[r][idx] = it.fila;
          colocada = true;
          break;
        }
      }
      if (!colocada) {
        var nr = nuevaFilaEmpaqueVacia(maxCol);
        nr[idx] = it.fila;
        filasOut.push(nr);
      }
    });
    return filasOut;
  }

  function htmlCeldaMatrizUnaMateria(fila, colK, nivel, rowsPorClave) {
    var info = infoCeldaPlan(fila, colK, nivel, rowsPorClave);
    if (info.tipo === 'sin_cursar') {
      return '<td class="kardex-cell-vacia">' + htmlCeldaPendienteKardex(fila) + '</td>';
    }
    var cls = info.tipo === 'aprobada' ? 'kardex-cell-aprobada' : info.tipo === 'reprobada' ? 'kardex-cell-reprobada' : 'kardex-cell-cursando';
    var tile = {
      grup: info.grup,
      est: info.tipo,
      nombre: fila.nombre,
      clave: fila.clave,
      creditos: fila.creditos
    };
    return '<td class="' + cls + '">' + htmlInteriorTarjetaKardex(tile) + '</td>';
  }

  function htmlInteriorTarjetaKardex(tile) {
    var cal = calificacionTextoParaCelda(tile.grup, tile.est);
    var tieneCred = tile.creditos != null && tile.creditos !== '';
    var credTxt = tieneCred ? String(tile.creditos) + ' créditos' : '—';
    var nom = escapeHtml(tile.nombre || '—');
    var clv = escapeHtml(tile.clave || '—');
    return (
      '<div class="kardex-tarjeta">' +
      '<div class="kardex-tarjeta-nombre">' + nom + '</div>' +
      '<div class="kardex-tarjeta-calif">' + escapeHtml(cal) + '</div>' +
      '<div class="kardex-tarjeta-meta"><span class="kardex-tarjeta-clave">' + clv + '</span>' +
      '<span class="kardex-tarjeta-sep"> · </span>' +
      '<span class="kardex-tarjeta-cred">' + escapeHtml(credTxt) + '</span></div>' +
      '</div>'
    );
  }

  function renderMatrizKardex(historial, kardex, asignaturasPlan, alumnoSel) {
    var cont = document.getElementById('kardexMatrizContenido');
    if (!cont) return;

    var historialArr = Array.isArray(historial) ? historial : [];
    var filas = filasAsignaturasParaMatriz(asignaturasPlan, historialArr);
    var rowsPorClave = agruparHistorialPorClave(historialArr);

    if (!filas.length) {
      cont.innerHTML = '<p class="text-center text-muted py-4 mb-0 px-3">No hay asignaturas del plan para mostrar (verifica permisos de catálogo o que el alumno tenga calificaciones).</p>';
      return;
    }

    var nivel = nivelAlumnoDesdeContexto(kardex, alumnoSel);
    var maxCol = maxColumnasPlanDesdeContexto(kardex, filas, nivel);
    var cols = [];
    var i;
    for (i = 1; i <= maxCol; i++) cols.push(i);

    var tipoPerMatriz = tipoPeriodoParaMatriz(kardex, alumnoSel);
    var html = '<table class="table table-bordered kardex-matriz kardex-matriz-empacada kardex-matriz-plan mb-0"><thead><tr>';
    cols.forEach(function (num) {
      var etiqueta = etiquetaColumnaPlan(num, tipoPerMatriz);
      html += '<th scope="col" class="kardex-matriz-periodo-th" title="' + escapeHtml(etiqueta) + '">' + escapeHtml(etiqueta) + '</th>';
    });
    html += '</tr></thead><tbody>';

    var filasEmpacadas = empacarMateriasEnFilas(filas, maxCol);
    if (!filasEmpacadas.length) {
      cont.innerHTML = '<p class="text-center text-muted py-4 mb-0 px-3">No hay materias con periodo del plan válido para la matriz.</p>';
      return;
    }

    filasEmpacadas.forEach(function (celdasPorCol) {
      var labelParts = [];
      var j;
      for (j = 0; j < maxCol; j++) {
        var f = celdasPorCol[j];
        if (f) labelParts.push((f.nombre || f.clave || '') + (f.clave ? ' (' + f.clave + ')' : ''));
      }
      html += '<tr aria-label="' + escapeHtml(labelParts.join(' · ') || 'Fila') + '">';
      for (j = 0; j < maxCol; j++) {
        var fila = celdasPorCol[j];
        var colK = j + 1;
        if (!fila) {
          html += '<td class="kardex-cell-slot-vacio"></td>';
        } else {
          html += htmlCeldaMatrizUnaMateria(fila, colK, nivel, rowsPorClave);
        }
      }
      html += '</tr>';
    });
    html += '</tbody></table>';
    cont.innerHTML = html;
  }

  function setVistaKardex(modo) {
    var lista = document.getElementById('kardexVistaLista');
    var graf = document.getElementById('kardexVistaGrafico');
    var btnL = document.getElementById('kardexVistaListaBtn');
    var btnG = document.getElementById('kardexVistaGraficoBtn');
    if (!lista || !graf) return;
    var esLista = modo === 'lista';
    lista.classList.toggle('d-none', !esLista);
    graf.classList.toggle('d-none', esLista);
    if (btnL) {
      btnL.classList.toggle('active', esLista);
      btnL.setAttribute('aria-pressed', esLista ? 'true' : 'false');
    }
    if (btnG) {
      btnG.classList.toggle('active', !esLista);
      btnG.setAttribute('aria-pressed', !esLista ? 'true' : 'false');
    }

    // Botón único de descarga: ajustar tooltip según la vista
    var btnPdf = document.getElementById('btnKardexPdfDescargar');
    if (btnPdf) {
      btnPdf.title = esLista ? 'Descargar PDF (lista)' : 'Descargar PDF (gráfico)';
    }
  }

  function programaPorIdDesdeCache(programaId) {
    var id = String(programaId);
    for (var i = 0; i < alumnosCache.length; i++) {
      var a = alumnosCache[i];
      if (a && a.programa && String(a.programa.id) === id) return a.programa;
    }
    return null;
  }

  /** Cantidad de opciones del filtro: duración del plan o, si falta, el máximo periodoCursando visto en ese programa. */
  function duracionFiltroParaPrograma(programaId) {
    var prog = programaPorIdDesdeCache(programaId);
    var dPla = prog && prog.duracionPeriodos != null ? parseInt(prog.duracionPeriodos, 10) : 0;
    if (isNaN(dPla)) dPla = 0;
    var maxAlum = 0;
    alumnosCache.forEach(function (a) {
      if (!a.programa || String(a.programa.id) !== String(programaId)) return;
      var n = a.periodoCursando != null ? parseInt(a.periodoCursando, 10) : NaN;
      if (!isNaN(n) && n > maxAlum) maxAlum = n;
    });
    var n = Math.max(dPla, maxAlum);
    return n > 0 ? n : 1;
  }

  function textoOpcionPeriodoComoAsignaturas(p, tipoLabel) {
    var num = p && p.numero != null ? String(p.numero) : '';
    if (num) return num + '° ' + tipoLabel;
    return (p && (p.nombre || p.nombreDisplay)) ? String(p.nombre || p.nombreDisplay) : 'Periodo';
  }

  async function poblarFiltroNumeroPeriodo() {
    var selProg = document.getElementById('kardexFiltroPrograma');
    var selPer = document.getElementById('kardexFiltroNumeroPeriodo');
    if (!selPer) return;
    var progVal = selProg ? String(selProg.value || '') : '';
    if (!progVal) {
      selPer.disabled = true;
      selPer.innerHTML = '<option value="">—</option>';
      return;
    }
    var prog = programaPorIdDesdeCache(progVal);
    var tipoPer = prog && prog.tipoPeriodo != null ? prog.tipoPeriodo : 'SEMESTRE';
    var tipoLabel = tipoPeriodoLabelCorto(tipoPer);
    var dur = duracionFiltroParaPrograma(progVal);
    var prev = selPer.value;

    var periodosApi = [];
    try {
      var rp = await apiFetch('/periodos?programaId=' + encodeURIComponent(progVal), { method: 'GET' });
      if (rp.ok) {
        var jsonP = await rp.json().catch(function () { return []; });
        periodosApi = Array.isArray(jsonP) ? jsonP : [];
      }
    } catch (e) {
      periodosApi = [];
    }

    selPer.disabled = false;
    selPer.innerHTML = '';
    var optTodos = document.createElement('option');
    optTodos.value = '';
    optTodos.textContent = 'Todos';
    selPer.appendChild(optTodos);

    var maxNum = 0;
    if (periodosApi.length > 0) {
      periodosApi.slice().sort(function (a, b) {
        return (parseInt(a && a.numero, 10) || 0) - (parseInt(b && b.numero, 10) || 0);
      }).forEach(function (p) {
        var n = p && p.numero != null ? parseInt(p.numero, 10) : NaN;
        if (isNaN(n) || n < 1) return;
        if (n > maxNum) maxNum = n;
        var o = document.createElement('option');
        o.value = String(n);
        o.textContent = textoOpcionPeriodoComoAsignaturas(p, tipoLabel);
        selPer.appendChild(o);
      });
    }
    if (maxNum === 0) {
      var i;
      for (i = 1; i <= dur; i++) {
        var o2 = document.createElement('option');
        o2.value = String(i);
        o2.textContent = String(i) + '° ' + tipoLabel;
        selPer.appendChild(o2);
        maxNum = i;
      }
    }

    if (prev && (prev === '' || (parseInt(prev, 10) >= 1 && parseInt(prev, 10) <= maxNum))) {
      selPer.value = prev;
    } else {
      selPer.value = '';
    }
  }

  async function poblarFiltrosAlumnos() {
    var selProg = document.getElementById('kardexFiltroPrograma');
    if (selProg) {
      var prev = selProg.value;
      selProg.innerHTML = '<option value="">Todos</option>';
      var map = {};
      alumnosCache.forEach(function (a) {
        if (a && a.programaId != null) {
          map[String(a.programaId)] = a.programaNombre || a.programaClave || ('Programa ' + a.programaId);
        }
      });
      Object.keys(map).sort(function (x, y) { return String(map[x]).localeCompare(String(map[y]), 'es'); }).forEach(function (id) {
        var opt = document.createElement('option');
        opt.value = id;
        opt.textContent = map[id];
        selProg.appendChild(opt);
      });
      if (prev) selProg.value = prev;
    }
    await poblarFiltroNumeroPeriodo();
  }

  function alumnoMatchFiltros(a) {
    if (!a) return false;
    var q = normalizarBusqueda((document.getElementById('kardexFiltroAlumno') || {}).value || '');
    var prog = (document.getElementById('kardexFiltroPrograma') || {}).value || '';
    var numPer = (document.getElementById('kardexFiltroNumeroPeriodo') || {}).value || '';
    var est = (document.getElementById('kardexFiltroEstatus') || {}).value || '';
    if (prog && String(a.programaId || '') !== String(prog)) return false;
    if (numPer) {
      var n = a.periodoCursando != null ? parseInt(a.periodoCursando, 10) : NaN;
      if (isNaN(n) || String(n) !== String(numPer)) return false;
    }
    if (est && String(a.estatusMatricula || '').toUpperCase() !== String(est).toUpperCase()) return false;
    if (q) {
      var nombre = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ');
      var blob = normalizarBusqueda((a.matricula || '') + ' ' + nombre + ' ' + (a.curp || '') + ' ' + (a.programaNombre || a.programaClave || ''));
      // match por términos (AND)
      var terms = q.split(/\s+/).filter(Boolean);
      var ok = terms.every(function (t) { return blob.indexOf(t) !== -1; });
      if (!ok) return false;
    }
    return true;
  }

  function renderListaAlumnos() {
    var cont = document.getElementById('kardexListaAlumnos');
    var countEl = document.getElementById('kardexResultadosCount');
    var btn = document.getElementById('btnKardexConsultar');
    if (!cont) return;
    var lista = (alumnosCache || []).filter(alumnoMatchFiltros).slice().sort(function (a, b) {
      var na = (a.apellidoPaterno || '') + (a.apellidoMaterno || '') + (a.nombre || '');
      var nb = (b.apellidoPaterno || '') + (b.apellidoMaterno || '') + (b.nombre || '');
      return na.localeCompare(nb, 'es');
    });
    if (countEl) countEl.textContent = lista.length ? (lista.length + ' alumno(s) encontrados') : 'Sin resultados';
    if (btn) btn.disabled = !alumnoSeleccionadoId;

    if (!lista.length) {
      cont.innerHTML = '<div class="list-group-item border-0 text-muted small py-4 text-center kardex-lista-alumnos-vacia">No hay alumnos que coincidan con los filtros.</div>';
      return;
    }
    cont.innerHTML = lista.map(function (a) {
      var id = a && a.alumnoId != null ? String(a.alumnoId) : '';
      var pid = a && a.programaId != null ? String(a.programaId) : '';
      var rowKey = id && pid ? (id + '|' + pid) : id;
      var nombre = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ').trim();
      var programa = a.programaNombre || a.programaClave || '—';
      var numPc = a.periodoCursando != null ? parseInt(a.periodoCursando, 10) : NaN;
      var tipoP = a.programaTipoPeriodo || null;
      var etiquetaPer = !isNaN(numPc)
        ? (ordinalPalabraPlan(numPc) + ' ' + sufijoTipoPeriodoPlan(tipoP))
        : '—';
      var matricula = a.matricula || '—';
      var curp = a.curp || '—';
      var checked = alumnoSeleccionadoId && String(alumnoSeleccionadoId) === rowKey;
      var nombreTitulo = escapeHtml(nombre || '—');
      var progTitulo = escapeHtml(programa);
      return (
        '<label class="list-group-item list-group-item-action kardex-alumno-item d-flex gap-2 align-items-start">' +
        '<input class="form-check-input kardex-alumno-item-radio flex-shrink-0" type="radio" name="kardexAlumnoRadio" value="' + escapeHtml(rowKey) + '" ' +
        'data-kardex-alumno-id="' + escapeHtml(id) + '" data-kardex-programa-id="' + escapeHtml(pid) + '" ' +
        (checked ? 'checked' : '') + ' aria-label="Seleccionar alumno">' +
        '<div class="kardex-alumno-item-body flex-grow-1 min-w-0">' +
        '<div class="kardex-alumno-nombre text-truncate" title="' + nombreTitulo + '">' + nombreTitulo + '</div>' +
        '<div class="kardex-alumno-matricula-estado d-flex align-items-center gap-2">' +
        '<span class="kardex-alumno-matricula-chip"><i class="bi bi-person-vcard" aria-hidden="true"></i>' + escapeHtml(matricula) + '</span>' +
        badgeEstatus(a.estatusMatricula) +
        '</div>' +
        '<div class="kardex-alumno-meta">' +
        '<div class="kardex-alumno-meta-programa" title="' + progTitulo + '">' +
        '<i class="bi bi-mortarboard kardex-alumno-meta-icon" aria-hidden="true"></i>' +
        '<span class="kardex-alumno-prog-text">' + escapeHtml(programa) + '</span>' +
        '</div>' +
        '<div class="kardex-alumno-meta-extras">' +
        '<span class="kardex-alumno-periodo-chip" title="Periodo cursando"><i class="bi bi-layers" aria-hidden="true"></i>' + escapeHtml(etiquetaPer) + '</span>' +
        '<span class="kardex-alumno-curp"><span class="kardex-alumno-curp-label">CURP</span> ' + escapeHtml(curp) + '</span>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '</label>'
      );
    }).join('');
  }

  async function cargarAlumnos() {
    // Multi-programa (admin): una fila por inscripción Alumno↔Programa.
    var r = await apiFetch('/alumnos/resumen-programas', { method: 'GET' });
    if (!r.ok) throw new Error('No se pudo cargar el listado de alumnos');
    alumnosCache = await r.json();
    if (!Array.isArray(alumnosCache)) alumnosCache = [];
    await poblarFiltrosAlumnos();
    renderListaAlumnos();
  }

  function alumnoStubDesdeKardex(k) {
    return { programa: k && k.tipoPeriodo != null ? { tipoPeriodo: k.tipoPeriodo } : null };
  }

  async function cargarKardexPorRutas(basePath, alumnoSel) {
    var detalle = document.getElementById('kardexDetalleContenido');
    var tbody = document.getElementById('kardexHistorialBody');
    var matriz = document.getElementById('kardexMatrizContenido');
    if (detalle) detalle.innerHTML = '<div class="text-center py-3"><span class="spinner-border spinner-border-sm"></span> Cargando…</div>';
    if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Cargando…</td></tr>';
    if (matriz) matriz.innerHTML = '<p class="text-center text-muted py-3 mb-0">Cargando matriz…</p>';
    try {
      var rk = await apiFetch(basePath, { method: 'GET' });
      if (!rk.ok) {
        var err = await rk.json().catch(function () { return {}; });
        throw new Error(err.error || 'Error al cargar resumen');
      }
      var k = await rk.json();
      if (detalle) renderizarDatosAlumno(k, detalle);
      lastKardexResumen = k || null;

      var sp = splitQuery(basePath);
      var histPath = sp.base.indexOf('/mi-kardex') !== -1
        ? '/kardex/mi-kardex/historial-calificaciones' + sp.qs
        : sp.base + '/historial-calificaciones' + sp.qs;
      var rh = await apiFetch(histPath, { method: 'GET' });
      if (!rh.ok) throw new Error('Error al cargar calificaciones');
      var historial = await rh.json();
      if (!Array.isArray(historial)) historial = [];

      var asignaturasPlan = [];
      if (sp.base === '/kardex/mi-kardex') {
        var ra = await apiFetch('/kardex/mi-kardex/asignaturas-plan' + sp.qs, { method: 'GET' });
        if (ra.ok) {
          var jsonA = await ra.json().catch(function () { return []; });
          asignaturasPlan = Array.isArray(jsonA) ? jsonA : [];
        }
      } else {
        var id = sp.base.replace(/^\/kardex\//, '');
        var alumnoSel2 = alumnoSel || alumnosCache.filter(function (a) { return String(a.id) === String(id); })[0];
        var programaId = alumnoSel2 && alumnoSel2.programa && alumnoSel2.programa.id != null
          ? alumnoSel2.programa.id
          : null;
        if (programaId != null) {
          var ra2 = await apiFetch('/asignaturas?programaId=' + encodeURIComponent(programaId), { method: 'GET' });
          if (ra2.ok) {
            var jsonA2 = await ra2.json().catch(function () { return []; });
            asignaturasPlan = Array.isArray(jsonA2) ? jsonA2 : [];
          }
        }
        alumnoSel = alumnoSel2;
      }

      // Foto: staff usa /alumnos/{id}/foto; alumno autenticado usa /alumnos/me/foto (mismo endpoint que el portal).
      if (sp.base && sp.base.indexOf('/kardex/mi-kardex') !== -1) {
        setFotoAlumnoEnResumen(null, { miFoto: true });
      } else {
        var alumnoIdFoto = null;
        if (sp.base && sp.base.indexOf('/kardex/') === 0) {
          var raw = sp.base.replace(/^\/kardex\//, '').trim();
          if (raw && raw.match(/^\d+$/)) alumnoIdFoto = raw;
        }
        if (alumnoIdFoto) setFotoAlumnoEnResumen(alumnoIdFoto);
        else if (alumnoSel && alumnoSel.id != null) setFotoAlumnoEnResumen(alumnoSel.id);
        else setFotoAlumnoEnResumen(null);
      }

      var selMatriz = alumnoSel || alumnoStubDesdeKardex(k);
      var tipoPerLista = tipoPeriodoParaMatriz(k, selMatriz);
      if (tbody) renderHistorial(historial, tbody, tipoPerLista, k, asignaturasPlan, selMatriz);
      renderMatrizKardex(historial, k, asignaturasPlan, selMatriz);
    } catch (e) {
      console.error(e);
      if (detalle) detalle.innerHTML = '<div class="alert alert-danger">' + escapeHtml(e.message || 'Error') + '</div>';
      if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-3">' + escapeHtml(e.message || 'Error') + '</td></tr>';
      if (matriz) matriz.innerHTML = '<p class="text-center text-danger py-3 mb-0">' + escapeHtml(e.message || 'Error') + '</p>';
    }
  }

  async function consultarKardex() {
    if (!alumnoSeleccionadoId) {
      alert('Selecciona un alumno.');
      return;
    }
    var parts = String(alumnoSeleccionadoId).split('|');
    var aid = parts[0] || '';
    var pid = parts[1] || '';
    var a = (alumnosCache || []).filter(function (x) {
      return String(x.alumnoId) === String(aid) && String(x.programaId) === String(pid);
    })[0] || null;
    var qs = pid ? ('?programaId=' + encodeURIComponent(String(pid))) : '';
    await cargarKardexPorRutas('/kardex/' + encodeURIComponent(String(aid)) + qs, a);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var btnL = document.getElementById('kardexVistaListaBtn');
    var btnG = document.getElementById('kardexVistaGraficoBtn');
    if (btnL) btnL.addEventListener('click', function () { setVistaKardex('lista'); });
    if (btnG) btnG.addEventListener('click', function () { setVistaKardex('grafico'); });

    var btnPdf = document.getElementById('btnKardexPdfDescargar');
    if (btnPdf) {
      // Tooltip inicial: lista por default (es el estado inicial de la UI)
      btnPdf.title = 'Descargar PDF (lista)';
      btnPdf.addEventListener('click', async function () {
        var vistaLista = document.getElementById('kardexVistaLista');
        var esListaActiva = vistaLista && !vistaLista.classList.contains('d-none');
        try {
          if (esListaActiva) {
            await descargarKardexListaPdf();
          } else {
            await descargarKardexGraficoPdf();
          }
        } catch (e3) {
          alert('No se pudo generar el PDF. ' + (e3 && e3.message ? e3.message : ''));
        }
      });
    }

    // Foto clickeable: el resumen se re-renderiza, así que usamos delegación
    document.addEventListener('click', function (ev) {
      var t = ev && ev.target ? ev.target : null;
      if (!t) return;
      if (t.id !== 'kardexAlumnoFotoImg') return;
      abrirModalFotoKardexDesdeImg(t);
    });

    var intro = document.getElementById('kardexIntroTexto');
    var cardSel = document.getElementById('kardexCardSeleccionAlumno');
    var btn = document.getElementById('btnKardexConsultar');

    if (esVistaKardexAlumno()) {
      if (intro) intro.textContent = 'Consulta tu resumen académico y tu historial de calificaciones.';
      if (cardSel) cardSel.classList.add('d-none');
      if (btn) btn.classList.add('d-none');

      (async function () {
        try {
          var rMe = await apiFetch('/alumnos/me', { method: 'GET' });
          if (!rMe.ok) throw new Error('No se pudo cargar tu información');
          var me = await rMe.json();
          var inscs = Array.isArray(me && me.programasAsignados) ? me.programasAsignados.slice() : [];
          inscs = inscs.filter(function (x) { return x && x.programa && x.programa.id != null; });
          inscs.sort(function (a, b) {
            var an = (a.programa && a.programa.nombre) ? String(a.programa.nombre) : '';
            var bn = (b.programa && b.programa.nombre) ? String(b.programa.nombre) : '';
            return an.localeCompare(bn, 'es', { sensitivity: 'base' });
          });

          var cont = document.getElementById('kardexTabsAlumno');
          if (!cont) {
            cont = document.createElement('div');
            cont.id = 'kardexTabsAlumno';
            cont.className = 'mb-3';
            var section = document.getElementById('kardexSection');
            if (section) {
              var ref = (document.querySelector('#kardexSection .card.mb-4') || null);
              section.insertBefore(cont, ref);
            }
          }

          if (inscs.length > 1) {
            var ul = document.createElement('ul');
            ul.className = 'nav nav-tabs';
            ul.setAttribute('role', 'tablist');

            inscs.forEach(function (ap, idx) {
              var pid = ap.programa.id;
              var nombre = ap.programa.nombre || ('Programa ' + pid);
              var li = document.createElement('li');
              li.className = 'nav-item';
              li.setAttribute('role', 'presentation');
              var a = document.createElement('a');
              a.className = 'nav-link' + (idx === 0 ? ' active' : '');
              a.href = '#';
              a.textContent = nombre;
              a.setAttribute('role', 'tab');
              a.setAttribute('data-programa-id', String(pid));
              a.addEventListener('click', function (ev) {
                ev.preventDefault();
                var pid2 = this.getAttribute('data-programa-id');
                if (!pid2) return;
                cont.querySelectorAll('.nav-link').forEach(function (x) { x.classList.remove('active'); });
                this.classList.add('active');
                cargarKardexPorRutas('/kardex/mi-kardex?programaId=' + encodeURIComponent(String(pid2)), null);
              });
              li.appendChild(a);
              ul.appendChild(li);
            });
            cont.innerHTML = '';
            cont.appendChild(ul);
            var firstId = inscs[0].programa.id;
            cargarKardexPorRutas('/kardex/mi-kardex?programaId=' + encodeURIComponent(String(firstId)), null);
          } else if (inscs.length === 1) {
            cont.innerHTML = '';
            cargarKardexPorRutas('/kardex/mi-kardex?programaId=' + encodeURIComponent(String(inscs[0].programa.id)), null);
          } else {
            cont.innerHTML = '';
            cargarKardexPorRutas('/kardex/mi-kardex', null);
          }
        } catch (eMe) {
          console.error(eMe);
          cargarKardexPorRutas('/kardex/mi-kardex', null);
        }
      })();
    } else {
      if (btn) btn.addEventListener('click', consultarKardex);
      var filtro = document.getElementById('kardexFiltroAlumno');
      if (filtro) filtro.addEventListener('input', function () { renderListaAlumnos(); });
      var fProg = document.getElementById('kardexFiltroPrograma');
      if (fProg) fProg.addEventListener('change', function () {
        poblarFiltroNumeroPeriodo().then(function () { renderListaAlumnos(); });
      });
      var fPer = document.getElementById('kardexFiltroNumeroPeriodo');
      if (fPer) fPer.addEventListener('change', function () { renderListaAlumnos(); });
      var fEst = document.getElementById('kardexFiltroEstatus');
      if (fEst) fEst.addEventListener('change', function () { renderListaAlumnos(); });

      // Selección única (radio) + consulta automática al seleccionar
      var listaEl = document.getElementById('kardexListaAlumnos');
      if (listaEl) {
        listaEl.addEventListener('change', function (e) {
          var r = e.target && e.target.matches ? (e.target.matches('input[type="radio"][name="kardexAlumnoRadio"]') ? e.target : null) : null;
          if (!r) return;
          alumnoSeleccionadoId = r.value || null;
          if (btn) btn.disabled = !alumnoSeleccionadoId;
          consultarKardex();
        });
      }
      cargarAlumnos().catch(function (e) {
        console.error(e);
        var detalle = document.getElementById('kardexDetalleContenido');
        if (detalle) detalle.innerHTML = '<div class="alert alert-warning">No se pudieron cargar los alumnos. ' + escapeHtml(e.message || '') + '</div>';
      });
    }
  });
})();

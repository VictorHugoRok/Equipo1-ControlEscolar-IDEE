// Horarios - Gestión de horarios por arrastre visual

const API_BASE = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';

function getHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
        headers['Authorization'] = 'Bearer ' + token;
    }
    return headers;
}

async function apiFetch(path) {
    const url = path.indexOf('/') === 0 ? API_BASE + path : API_BASE + '/' + path;
    const r = await fetch(url, { headers: getHeaders() });
    if (!r.ok) throw new Error('Error en petición: ' + r.status);
    return r.json();
}

let programas = [];
let periodosPrograma = [];
let periodosAcademicos = [];
let grupos = [];
let asignaturasPeriodo = [];
let maestros = [];
let asignaturaMaestroMap = {};
// Mapa "DIA_HH:mm" -> Array<BloqueColocado>
// Un mismo día/hora puede contener varias materias si sus periodos no se traslapan.
let horarioState = {};
let vistaTablaActiva = false;
let hvTarjetaEnDrag = null;
let hvTarjetaAulaEditando = null;
let hvBloqueCopiado = null;

const DIAS_SEMANA = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO'];
const HORAS_DEFAULT = ['07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00', '19:00', '20:00'];

// Paleta de colores muy distinguibles (fondo claro + borde) - sin tonos similares
const HV_COLORES_MATERIAS = [
    { bg: '#d4edff', border: '#0066cc' },   /* Azul */
    { bg: '#c8f0c8', border: '#228b22' },   /* Verde */
    { bg: '#ffe4b5', border: '#e65100' },   /* Naranja */
    { bg: '#e6d5f5', border: '#6a1b9a' },   /* Morado */
    { bg: '#b2ebf2', border: '#00838f' },  /* Cyan */
    { bg: '#f8bbd9', border: '#ad1457' },   /* Rosa */
    { bg: '#fff9c4', border: '#f9a825' },  /* Amarillo */
    { bg: '#c5cae9', border: '#283593' },   /* Índigo */
    { bg: '#d7ccc8', border: '#5d4037' }, /* Marrón */
    { bg: '#ffccbc', border: '#bf360c' }, /* Coral */
    { bg: '#dcedc8', border: '#558b2f' },  /* Lima */
    { bg: '#b2dfdb', border: '#00695c' },  /* Teal */
    { bg: '#d1c4e9', border: '#4527a0' },  /* Violeta */
    { bg: '#ffecb3', border: '#ff8f00' }, /* Ámbar */
];

// Mapa asignaturaId -> índice de color (sin repeticiones dentro del mismo horario)
let hvAsignaturaColorMap = {};

function hvColorIndexForAsignaturaId(asignaturaId) {
    const len = HV_COLORES_MATERIAS.length || 1;
    if (asignaturaId == null) return 0;
    const raw = String(asignaturaId).trim();
    const n = Number(raw);
    if (!isNaN(n) && isFinite(n)) {
        return Math.abs(Math.trunc(n)) % len;
    }
    // Hash simple y estable para IDs no numéricos.
    let h = 0;
    for (let i = 0; i < raw.length; i++) {
        h = ((h << 5) - h) + raw.charCodeAt(i);
        h |= 0;
    }
    return Math.abs(h) % len;
}

function setAsignaturaColorMapFromIds(ids) {
    const uniq = Array.from(new Set((ids || []).map(x => String(x)).filter(Boolean)));
    hvAsignaturaColorMap = {};
    uniq.forEach((id, i) => {
        hvAsignaturaColorMap[String(id)] = hvColorIndexForAsignaturaId(id);
    });
}

function buildAsignaturaColorMap() {
    setAsignaturaColorMapFromIds((asignaturasPeriodo || []).map(a => a && a.id));
}

function getColorForAsignatura(asignaturaId) {
    let idx = hvAsignaturaColorMap[String(asignaturaId)];
    if (idx == null) {
        idx = hvColorIndexForAsignaturaId(asignaturaId);
        hvAsignaturaColorMap[String(asignaturaId)] = idx;
    }
    return HV_COLORES_MATERIAS[idx];
}

async function cargarProgramas() {
    try {
        programas = await apiFetch('/programas-educativos');
        const selCrear = document.getElementById('hvPrograma');
        const selConsulta = document.getElementById('hvConsultaPrograma');
        if (selCrear) {
            selCrear.innerHTML = '<option value="">Selecciona programa...</option>';
            (programas || []).forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.nombre || p.clave || 'Programa ' + p.id;
                selCrear.appendChild(opt);
            });
        }
        if (selConsulta) {
            selConsulta.innerHTML = '<option value="">Todos los programas</option>';
            (programas || []).forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.nombre || p.clave || 'Programa ' + p.id;
                selConsulta.appendChild(opt);
            });
        }
    } catch (e) {
        console.error('Error cargar programas:', e);
        programas = [];
    }
}

/** Carga periodos del plan en memoria (solo para etiquetas en resumen; el periodo efectivo sale del grupo). */
async function cargarPeriodosPlanEnMemoria(programaId) {
    if (!programaId) {
        periodosPrograma = [];
        return;
    }
    try {
        periodosPrograma = await apiFetch('/periodos?programaId=' + programaId);
    } catch (e) {
        console.error('Error cargar periodos del plan:', e);
        periodosPrograma = [];
    }
}

function getGrupoSeleccionado() {
    const sel = document.getElementById('hvGrupo');
    if (!sel || !sel.value) return null;
    return (grupos || []).find(x => String(x.id) === String(sel.value)) || null;
}

function etiquetaPeriodoDesdeGrupo(g) {
    if (!g || g.numeroPeriodo == null) return '—';
    return etiquetaPeriodoResumen(g.numeroPeriodo);
}

/** Periodos académicos institucionales (solo si existe select en la página; el listado de horarios ya no filtra por esto). */
async function cargarPeriodosAcademicos(programaId) {
    try {
        const pid = programaId != null ? String(programaId).trim() : '';
        const url = pid ? ('/periodos-academicos?programaId=' + encodeURIComponent(pid)) : '/periodos-academicos';
        periodosAcademicos = await apiFetch(url);
        const selCrear = document.getElementById('hvPeriodoAcademico');
        if (selCrear) {
            selCrear.innerHTML = '<option value="">Selecciona ciclo...</option>';
            (periodosAcademicos || []).forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.codigo || p.nombre || ('Periodo ' + p.id);
                selCrear.appendChild(opt);
            });
        }
    } catch (e) {
        console.error('Error cargar periodos académicos:', e);
        periodosAcademicos = [];
    }
}

/** Rellena el filtro «periodo del plan» según los periodos del programa educativo (nivel 1…N). */
function llenarSelectPeriodoPlanConsulta() {
    const sel = document.getElementById('hvConsultaPeriodoPlan');
    if (!sel) return;
    const prev = sel.value;
    sel.innerHTML = '<option value="">Todos los niveles</option>';
    const lista = (periodosPrograma || []).slice().sort((a, b) => (a.numero || 0) - (b.numero || 0));
    lista.forEach(p => {
        const opt = document.createElement('option');
        opt.value = String(p.numero != null ? p.numero : '');
        opt.textContent = (p.nombre && String(p.nombre).trim()) ? p.nombre : ('Nivel ' + (p.numero != null ? p.numero : ''));
        sel.appendChild(opt);
    });
    if (prev && [...sel.options].some(o => o.value === prev)) sel.value = prev;
}

async function cargarGrupos(programaId) {
    const sel = document.getElementById('hvGrupo');
    if (!sel) return;
    sel.innerHTML = '<option value="">Selecciona grupo...</option>';
    if (!programaId) {
        sel.innerHTML = '<option value="">Selecciona programa primero…</option>';
        sel.disabled = true;
        grupos = [];
        return;
    }
    try {
        grupos = await apiFetch('/grupos?programaId=' + programaId);
        (grupos || []).forEach(g => {
            const opt = document.createElement('option');
            opt.value = g.id;
            const suf = g.numeroPeriodo != null ? (' — ' + etiquetaPeriodoResumen(g.numeroPeriodo)) : '';
            opt.textContent = (g.nombre || 'Grupo ' + g.id) + suf;
            sel.appendChild(opt);
        });
        sel.disabled = false;
    } catch (e) {
        console.error('Error cargar grupos:', e);
        grupos = [];
        sel.disabled = true;
    }
}

/** Etiqueta corta para opción de grupo (usa periodos ya cargados en memoria si existen). */
function etiquetaPeriodoResumen(numero) {
    if (numero == null) return '';
    const p = (periodosPrograma || []).find(x => x.numero === numero);
    return p ? (p.nombre || ('N.º ' + numero)) : ('N.º ' + numero);
}

/**
 * Grupos para el filtro de lista. Opcionalmente solo los del nivel (numeroPeriodo) indicado.
 * @param {string|number} programaId
 * @param {string|number|null|undefined} numeroPeriodoFiltro — si viene, solo grupos con ese número de periodo del plan
 */
async function cargarGruposConsulta(programaId, numeroPeriodoFiltro) {
    const sel = document.getElementById('hvConsultaGrupo');
    if (!sel) return;
    const firstOpt = '<option value="">Todos los grupos</option>';
    sel.innerHTML = firstOpt;
    sel.disabled = !programaId;
    if (!programaId) return;
    const filtroNivel = numeroPeriodoFiltro != null && String(numeroPeriodoFiltro).trim() !== ''
        ? String(numeroPeriodoFiltro).trim()
        : null;
    try {
        const grps = await apiFetch('/grupos?programaId=' + programaId);
        (grps || []).forEach(g => {
            if (filtroNivel != null && String(g.numeroPeriodo) !== filtroNivel) return;
            const opt = document.createElement('option');
            opt.value = g.id;
            const suf = g.numeroPeriodo != null ? (' — ' + etiquetaPeriodoResumen(g.numeroPeriodo)) : '';
            opt.textContent = (g.nombre || 'Grupo ' + g.id) + suf;
            sel.appendChild(opt);
        });
    } catch (e) {
        console.error('Error cargar grupos consulta:', e);
    }
}

function fmtHoraConsulta(h) {
    if (!h) return '';
    const s = (h + '').trim();
    if (s.length >= 5) return s.substring(0, 5);
    return s;
}

function minutosDesdeMedianocheConsulta(hhmm) {
    const s = fmtHoraConsulta(hhmm);
    const p = s.split(':');
    return (parseInt(p[0], 10) || 0) * 60 + (parseInt(p[1], 10) || 0);
}

function slotOverlapsClaseConsulta(slotIdx, claseIni, claseFin) {
    const slotIni = minutosDesdeMedianocheConsulta(HORAS_DEFAULT[slotIdx]);
    const slotFin = slotIni + 60;
    return claseIni < slotFin && claseFin > slotIni;
}

function extraerAsignaturaNombre(b) {
    return b.asignaturaNombre || (b.asignatura && b.asignatura.nombre) || '—';
}

function extraerAsignaturaId(b) {
    return b.asignaturaId || (b.asignatura && b.asignatura.id) || null;
}

function extraerMaestroNombre(b) {
    const m = b.maestro;
    if (!m) return '';
    return m.nombre || m.etiqueta || (m.apellidoPaterno ? (m.nombre || '') + ' ' + (m.apellidoPaterno || '') : '') || '';
}

function normalizarDiaHorario(d) {
    return (d || '').toString().toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\s+/g, '');
}

function normalizarAulaHorario(a) {
    return (a == null) ? '' : String(a).trim();
}

/**
 * Fusiona bloques contiguos del mismo grupo/día/materia/maestro/aula.
 * Esto permite que la vista de “Horarios creados” se vea igual a como se armó en la tabla (celdas agrupadas).
 */
function fusionarBloquesConsecutivosHorario(bloques) {
    const lista = Array.isArray(bloques) ? bloques.slice() : [];
    // Orden consistente: grupo → día → horaInicio
    lista.sort((a, b) => {
        const ga = (a.grupoEntity && a.grupoEntity.id) || a.grupoId || 0;
        const gb = (b.grupoEntity && b.grupoEntity.id) || b.grupoId || 0;
        if (String(ga) !== String(gb)) return String(ga).localeCompare(String(gb));
        const da = normalizarDiaHorario(a.dia);
        const db = normalizarDiaHorario(b.dia);
        if (da !== db) return da.localeCompare(db);
        return minutosDesdeMedianocheConsulta(fmtHoraConsulta(a.horaInicio)) - minutosDesdeMedianocheConsulta(fmtHoraConsulta(b.horaInicio));
    });

    const out = [];
    for (const b of lista) {
        const d = normalizarDiaHorario(b.dia);
        if (DIAS_SEMANA.indexOf(d) < 0) {
            out.push(b);
            continue;
        }
        const aid = extraerAsignaturaId(b);
        const gid = (b.grupoEntity && b.grupoEntity.id) || b.grupoId || null;
        const hi = fmtHoraConsulta(b.horaInicio);
        const hf = fmtHoraConsulta(b.horaFin);
        if (!hi || !hf) {
            out.push(b);
            continue;
        }

        const prev = out.length ? out[out.length - 1] : null;
        if (!prev) {
            out.push({ ...b, dia: d });
            continue;
        }

        const prevDia = normalizarDiaHorario(prev.dia);
        const prevAid = extraerAsignaturaId(prev);
        const prevGid = (prev.grupoEntity && prev.grupoEntity.id) || prev.grupoId || null;

        const prevFin = fmtHoraConsulta(prev.horaFin);
        const prevFinMin = prevFin ? minutosDesdeMedianocheConsulta(prevFin) : null;
        const hiMin = minutosDesdeMedianocheConsulta(hi);
        // “Contiguo” tolerante: algunos registros viejos guardan horaFin como HH:59 o con segundos.
        // Consideramos contiguo si fin == inicio, o si la diferencia es <= 1 minuto.
        const contiguo = prevFin
            && (prevFin === hi || (prevFinMin != null && Math.abs(prevFinMin - hiMin) <= 1));
        // En la vista de consulta queremos agrupar como en la tabla de creación:
        // misma asignatura en horas contiguas (por grupo y día). Maestro/aula pueden variar en BD por capturas parciales,
        // pero visualmente deben verse como un solo bloque continuo.
        const mismo =
            String(prevDia) === String(d) &&
            String(prevAid || '') === String(aid || '') &&
            String(prevGid || '') === String(gid || '');

        if (contiguo && mismo) {
            // Extiende el bloque anterior
            prev.horaFin = hf;
        } else {
            out.push({ ...b, dia: d });
        }
    }
    return out;
}

function renderizarConsultaHorario(bloques) {
    const cont = document.getElementById('hvConsultaContenido');
    if (!cont) return;
    const lista = fusionarBloquesConsecutivosHorario(bloques || []);
    if (lista.length === 0) {
        cont.innerHTML = '<p class="text-muted small text-center py-4 mb-0">No hay horario guardado para este grupo.</p>';
        return;
    }
    const bloqueStartAt = {};
    const asignaturaIds = {};
    lista.forEach(b => {
        const d = (b.dia || '').toUpperCase();
        if (DIAS_SEMANA.indexOf(d) < 0) return;
        const hi = fmtHoraConsulta(b.horaInicio);
        const hf = fmtHoraConsulta(b.horaFin);
        const claseIni = minutosDesdeMedianocheConsulta(hi);
        const claseFin = minutosDesdeMedianocheConsulta(hf);
        let startIdx = -1;
        let span = 0;
        for (let i = 0; i < HORAS_DEFAULT.length; i++) {
            if (slotOverlapsClaseConsulta(i, claseIni, claseFin)) {
                if (startIdx < 0) startIdx = i;
                span++;
            }
        }
        if (startIdx >= 0 && span > 0) {
            const key = d + '_' + HORAS_DEFAULT[startIdx];
            const aid = extraerAsignaturaId(b);
            bloqueStartAt[key] = {
                dia: d,
                hora: HORAS_DEFAULT[startIdx],
                horaIndex: startIdx,
                span,
                bloque: {
                    asignaturaId: aid,
                    asignaturaNombre: extraerAsignaturaNombre(b),
                    maestroNombre: extraerMaestroNombre(b),
                    aula: b.aula || ''
                }
            };
            if (aid) asignaturaIds[aid] = true;
        }
    });
    const diasLabels = { LUNES: 'LUNES', MARTES: 'MARTES', MIERCOLES: 'MIÉRCOLES', JUEVES: 'JUEVES', VIERNES: 'VIERNES', SABADO: 'SÁBADO' };
    let html = '<table class="table table-bordered table-sm hv-calendario-table hv-solo-vista"><thead><tr><th>HORA</th>';
    DIAS_SEMANA.forEach(d => { html += '<th>' + (diasLabels[d] || d) + '</th>'; });
    html += '</tr></thead><tbody>';
    HORAS_DEFAULT.forEach((h, rowIdx) => {
        const [hh, mm] = h.split(':').map(Number);
        const fin = (hh * 60 + (mm || 0) + 59) % (24 * 60);
        const finH = Math.floor(fin / 60);
        const finM = fin % 60;
        const finStr = (finH < 10 ? '0' : '') + finH + ':' + (finM < 10 ? '0' : '') + finM;
        html += '<tr><td class="text-nowrap bg-light small">' + h + ' - ' + finStr + '</td>';
        DIAS_SEMANA.forEach(dia => {
            const key = dia + '_' + h;
            const blockStart = bloqueStartAt[key];
            if (blockStart) {
                const b = blockStart.bloque;
                const nombreEsc = (b.asignaturaNombre || '—').replace(/</g, '&lt;');
                const maestroEsc = (b.maestroNombre || '').replace(/</g, '&lt;');
                const aulaEsc = (b.aula || '').replace(/</g, '&lt;');
                const col = getColorForAsignatura(b.asignaturaId);
                html += '<td class="hv-celda hv-celda-merged" rowspan="' + blockStart.span + '">';
                html += '<div class="hv-tarjeta-materia hv-tarjeta-solo-vista border rounded p-1 small d-flex flex-column" style="background-color:' + col.bg + ';border-left:4px solid ' + col.border + '">';
                html += '<span class="fw-semibold hv-tarjeta-nombre text-truncate" title="' + nombreEsc + '">' + nombreEsc + '</span>';
                if (maestroEsc) html += '<span class="text-muted hv-tarjeta-maestro text-truncate" title="' + maestroEsc + '">' + maestroEsc + '</span>';
                else html += '<span class="text-muted hv-tarjeta-maestro text-truncate" title="Sin docente asignado">Sin docente asignado</span>';
                if (aulaEsc) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate" title="' + aulaEsc + '">' + aulaEsc + '</span>';
                html += '</div></td>';
            } else {
                const cubierto = Object.keys(bloqueStartAt).some(k => {
                    const bs = bloqueStartAt[k];
                    if (bs.dia !== dia) return false;
                    const si = bs.horaIndex;
                    return rowIdx > si && rowIdx < si + bs.span;
                });
                if (!cubierto) html += '<td class="hv-celda"></td>';
            }
        });
        html += '</tr>';
    });
    html += '</tbody></table>';
    cont.innerHTML = html;
}

const DIAS_LABELS = { LUNES: 'Lunes', MARTES: 'Martes', MIERCOLES: 'Miércoles', JUEVES: 'Jueves', VIERNES: 'Viernes', SABADO: 'Sábado' };

async function cargarListaHorarios() {
    const cont = document.getElementById('hvListaHorariosGraficos');
    if (!cont) return;
    cont.innerHTML = '<div class="text-center py-4"><span class="spinner-border spinner-border-sm"></span> Cargando…</div>';
    const programaId = document.getElementById('hvConsultaPrograma')?.value;
    const grupoId = document.getElementById('hvConsultaGrupo')?.value;
    const periodoPlanVal = document.getElementById('hvConsultaPeriodoPlan')?.value;
    let url = '/horarios';
    const params = [];
    if (grupoId) {
        params.push('grupoId=' + grupoId);
    } else if (programaId) {
        params.push('programaId=' + programaId);
    }
    if (params.length) url += '?' + params.join('&');
    try {
        let bloques = await apiFetch(url);
        bloques = bloques || [];
        /* Filtrar por nivel del plan (grupo.numeroPeriodo): el horario actual no depende del periodo académico institucional. */
        if (periodoPlanVal && !grupoId) {
            const n = parseInt(periodoPlanVal, 10);
            if (!isNaN(n)) {
                bloques = bloques.filter(b => {
                    const ge = b.grupoEntity;
                    return ge && ge.numeroPeriodo != null && Number(ge.numeroPeriodo) === n;
                });
            }
        }
        renderizarListaHorariosGraficos(bloques);
    } catch (e) {
        console.error('Error cargar horarios:', e);
        if (cont) cont.innerHTML = '<div class="text-center text-danger py-4">No se pudieron cargar los horarios.</div>';
    }
}

function buildCalendarioGraficoHtml(bloques) {
    const lista = fusionarBloquesConsecutivosHorario(bloques || []);
    if (lista.length === 0) return '<p class="text-muted small text-center py-3 mb-0">Sin bloques</p>';
    const bloqueStartAt = {};
    lista.forEach(b => {
        const d = normalizarDiaHorario(b.dia);
        if (DIAS_SEMANA.indexOf(d) < 0) return;
        const hi = fmtHoraConsulta(b.horaInicio);
        const hf = fmtHoraConsulta(b.horaFin);
        const claseIni = minutosDesdeMedianocheConsulta(hi);
        const claseFin = minutosDesdeMedianocheConsulta(hf);
        let startIdx = -1, span = 0;
        for (let i = 0; i < HORAS_DEFAULT.length; i++) {
            if (slotOverlapsClaseConsulta(i, claseIni, claseFin)) {
                if (startIdx < 0) startIdx = i;
                span++;
            }
        }
        if (startIdx >= 0 && span > 0) {
            const key = d + '_' + HORAS_DEFAULT[startIdx];
            const aid = extraerAsignaturaId(b);
            bloqueStartAt[key] = {
                dia: d, hora: HORAS_DEFAULT[startIdx], horaIndex: startIdx, span,
                bloque: {
                    asignaturaId: aid,
                    asignaturaNombre: extraerAsignaturaNombre(b),
                    maestroNombre: extraerMaestroNombre(b),
                    aula: b.aula || ''
                }
            };
        }
    });
    const diasLabels = { LUNES: 'LUNES', MARTES: 'MARTES', MIERCOLES: 'MIÉRCOLES', JUEVES: 'JUEVES', VIERNES: 'VIERNES', SABADO: 'SÁBADO' };
    let html = '<table class="table table-bordered table-sm hv-calendario-table hv-solo-vista mb-0"><thead><tr><th>HORA</th>';
    DIAS_SEMANA.forEach(d => { html += '<th>' + (diasLabels[d] || d) + '</th>'; });
    html += '</tr></thead><tbody>';
    HORAS_DEFAULT.forEach((h, rowIdx) => {
        html += '<tr><td class="text-nowrap bg-light small hv-celda-hora">' + horaARango(h) + '</td>';
        DIAS_SEMANA.forEach(dia => {
            const key = dia + '_' + h;
            const blockStart = bloqueStartAt[key];
            if (blockStart) {
                const b = blockStart.bloque;
                const col = getColorForAsignatura(b.asignaturaId);
                const n = (b.asignaturaNombre || '—').replace(/</g, '&lt;');
                const m = (b.maestroNombre || '').replace(/</g, '&lt;');
                const a = (b.aula || '').replace(/</g, '&lt;');
                html += '<td class="hv-celda hv-celda-merged" rowspan="' + blockStart.span + '">';
                html += '<div class="hv-tarjeta-wrapper">';
                html += '<div class="hv-tarjeta-materia hv-tarjeta-solo-vista border rounded p-1 small d-flex flex-column" style="background-color:' + col.bg + ';border-left:4px solid ' + col.border + '">';
                html += '<span class="fw-semibold hv-tarjeta-nombre text-truncate" title="' + n + '">' + n + '</span>';
                if (m) html += '<span class="text-muted hv-tarjeta-maestro text-truncate" title="' + m + '">' + m + '</span>';
                else html += '<span class="text-muted hv-tarjeta-maestro text-truncate" title="Sin docente asignado">Sin docente asignado</span>';
                if (a) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate" title="' + a + '">' + a + '</span>';
                html += '</div></div></td>';
            } else {
                const cubierto = Object.keys(bloqueStartAt).some(k => {
                    const bs = bloqueStartAt[k];
                    return bs.dia === dia && rowIdx > bs.horaIndex && rowIdx < bs.horaIndex + bs.span;
                });
                if (!cubierto) html += '<td class="hv-celda"></td>';
            }
        });
        html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
}

/**
 * Construye el HTML del horario en formato visual (solo lectura) para alumno/maestro.
 * Mismo formato que crear horarios: tabla con celdas fijas y tarjetas de colores.
 * @param {Array} bloques - Lista de bloques de horario (del API alumnos/me/horarios o maestros/me/horarios)
 * @param {Object} opciones - { tipo: 'alumno'|'maestro' }
 * @returns {string} HTML
 */
function buildHorarioConsultaVisual(bloques, opciones) {
    const lista = fusionarBloquesConsecutivosHorario(bloques || []);
    const tipo = (opciones && opciones.tipo) || 'maestro';
    if (lista.length === 0) return '<p class="text-muted small text-center py-3 mb-0">Sin bloques</p>';

    // Colores determinísticos: consistente con Crear Horario y Lista de Horarios.
    setAsignaturaColorMapFromIds((lista || []).map(b => extraerAsignaturaId(b)));

    const bloqueStartAt = {};
    lista.forEach(b => {
        const d = normalizarDiaHorario(b.dia);
        if (DIAS_SEMANA.indexOf(d) < 0) return;
        const hi = fmtHoraConsulta(b.horaInicio);
        const hf = fmtHoraConsulta(b.horaFin);
        const claseIni = minutosDesdeMedianocheConsulta(hi);
        const claseFin = minutosDesdeMedianocheConsulta(hf);
        let startIdx = -1, span = 0;
        for (let i = 0; i < HORAS_DEFAULT.length; i++) {
            if (slotOverlapsClaseConsulta(i, claseIni, claseFin)) {
                if (startIdx < 0) startIdx = i;
                span++;
            }
        }
        if (startIdx >= 0 && span > 0) {
            const key = d + '_' + HORAS_DEFAULT[startIdx];
            const aid = extraerAsignaturaId(b);
            bloqueStartAt[key] = {
                dia: d, hora: HORAS_DEFAULT[startIdx], horaIndex: startIdx, span,
                bloque: {
                    asignaturaId: aid,
                    asignaturaNombre: extraerAsignaturaNombre(b),
                    maestroNombre: (b.maestroNombre != null && b.maestroNombre !== '') ? b.maestroNombre : extraerMaestroNombre(b),
                    grupo: b.grupo || (b.grupoEntity && b.grupoEntity.nombre) || '',
                    aula: b.aula || ''
                }
            };
        }
    });
    const diasLabels = { LUNES: 'LUNES', MARTES: 'MARTES', MIERCOLES: 'MIÉRCOLES', JUEVES: 'JUEVES', VIERNES: 'VIERNES', SABADO: 'SÁBADO' };
    let html = '<table class="table table-bordered table-sm hv-calendario-table hv-solo-vista mb-0"><thead><tr><th>HORA</th>';
    DIAS_SEMANA.forEach(d => { html += '<th>' + (diasLabels[d] || d) + '</th>'; });
    html += '</tr></thead><tbody>';
    HORAS_DEFAULT.forEach((h, rowIdx) => {
        html += '<tr><td class="text-nowrap bg-light small hv-celda-hora">' + horaARango(h) + '</td>';
        DIAS_SEMANA.forEach(dia => {
            const key = dia + '_' + h;
            const blockStart = bloqueStartAt[key];
            if (blockStart) {
                const b = blockStart.bloque;
                const col = getColorForAsignatura(b.asignaturaId);
                const n = (b.asignaturaNombre || '—').replace(/</g, '&lt;');
                const m = (b.maestroNombre || '').replace(/</g, '&lt;');
                const g = (b.grupo || '').replace(/</g, '&lt;');
                const a = (b.aula || '').replace(/</g, '&lt;');
                html += '<td class="hv-celda hv-celda-merged" rowspan="' + blockStart.span + '"><div class="hv-tarjeta-wrapper">';
                html += '<div class="hv-tarjeta-materia hv-tarjeta-solo-vista border rounded p-1 small d-flex flex-column" style="background-color:' + col.bg + ';border-left:4px solid ' + col.border + '">';
                html += '<span class="fw-semibold hv-tarjeta-nombre text-truncate">' + n + '</span>';
                if (g) html += '<span class="text-muted hv-tarjeta-maestro text-truncate">Grupo ' + g + '</span>';
                if (tipo === 'alumno' && m) html += '<span class="text-muted hv-tarjeta-maestro text-truncate">' + m + '</span>';
                if (tipo === 'maestro' && a) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate">' + a + '</span>';
                if (tipo === 'alumno' && a) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate">' + a + '</span>';
                html += '</div></div></td>';
            } else {
                const cubierto = Object.keys(bloqueStartAt).some(k => {
                    const bs = bloqueStartAt[k];
                    return bs.dia === dia && rowIdx > bs.horaIndex && rowIdx < bs.horaIndex + bs.span;
                });
                if (!cubierto) html += '<td class="hv-celda"></td>';
            }
        });
        html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
}

/**
 * Construye el HTML del horario en formato agenda (por fechas) para alumno/maestro.
 * Genera ocurrencias semanales dentro de fechaInicio/fechaFin de cada bloque.
 *
 * @param {Array} bloques - Lista de bloques de horario (DTO)
 * @param {Object} opciones - { tipo: 'alumno'|'maestro', diasHaciaAtras?: number, diasHaciaAdelante?: number }
 * @returns {string} HTML
 */
function buildHorarioAgenda(bloques, opciones) {
    const lista = Array.isArray(bloques) ? bloques : [];
    const tipo = (opciones && opciones.tipo) || 'maestro';
    const backDays = (opciones && opciones.diasHaciaAtras != null) ? parseInt(opciones.diasHaciaAtras, 10) : 7;
    const fwdDays = (opciones && opciones.diasHaciaAdelante != null) ? parseInt(opciones.diasHaciaAdelante, 10) : 60;

    if (!lista || lista.length === 0) {
        return '<div class="text-center text-muted py-4">Sin clases.</div>';
    }

    const today = new Date();
    const rangeStart = new Date(today.getFullYear(), today.getMonth(), today.getDate() - backDays);
    const rangeEnd = new Date(today.getFullYear(), today.getMonth(), today.getDate() + fwdDays);

    const diaToJs = (d) => {
        const s = String(d || '').toUpperCase();
        if (s === 'LUNES') return 1;
        if (s === 'MARTES') return 2;
        if (s === 'MIERCOLES' || s === 'MIÉRCOLES') return 3;
        if (s === 'JUEVES') return 4;
        if (s === 'VIERNES') return 5;
        if (s === 'SABADO' || s === 'SÁBADO') return 6;
        if (s === 'DOMINGO') return 0;
        return null;
    };

    const parseYmd = (s) => {
        if (!s) return null;
        const t = String(s).substring(0, 10);
        const [y, m, d] = t.split('-').map(x => parseInt(x, 10));
        if (!y || !m || !d) return null;
        return new Date(y, m - 1, d);
    };

    const fmtFecha = (dt) => {
        try {
            return dt.toLocaleDateString('es-MX', { weekday: 'long', year: 'numeric', month: 'short', day: '2-digit' });
        } catch (_) {
            return dt.toISOString().substring(0, 10);
        }
    };

    const fmtHora = (h) => {
        if (!h) return '';
        const s = String(h);
        return s.length >= 5 ? s.substring(0, 5) : s;
    };

    // Colores determinísticos por asignatura
    setAsignaturaColorMapFromIds((lista || []).map(b => extraerAsignaturaId(b)));

    const eventos = [];
    const sinFechas = [];

    (lista || []).forEach(b => {
        const diaIdx = diaToJs(b.dia);
        const fi = parseYmd(b.fechaInicio);
        const ff = parseYmd(b.fechaFin);
        if (diaIdx == null || !fi || !ff) {
            sinFechas.push(b);
            return;
        }

        const start = fi > rangeStart ? fi : rangeStart;
        const end = ff < rangeEnd ? ff : rangeEnd;
        if (start > end) return;

        // primera fecha >= start que caiga en el día del bloque
        let cur = new Date(start.getFullYear(), start.getMonth(), start.getDate());
        const delta = (diaIdx - cur.getDay() + 7) % 7;
        cur.setDate(cur.getDate() + delta);

        while (cur <= end) {
            eventos.push({
                date: new Date(cur.getFullYear(), cur.getMonth(), cur.getDate()),
                horaInicio: fmtHora(b.horaInicio),
                horaFin: fmtHora(b.horaFin),
                asignaturaId: extraerAsignaturaId(b),
                asignaturaNombre: extraerAsignaturaNombre(b),
                maestroNombre: (b.maestroNombre != null && b.maestroNombre !== '') ? b.maestroNombre : extraerMaestroNombre(b),
                grupo: b.grupo || (b.grupoEntity && b.grupoEntity.nombre) || '',
                aula: b.aula || '',
                fechaInicio: String(b.fechaInicio || '').substring(0, 10),
                fechaFin: String(b.fechaFin || '').substring(0, 10),
            });
            cur.setDate(cur.getDate() + 7);
        }
    });

    if (eventos.length === 0 && sinFechas.length === 0) {
        return '<div class="text-center text-muted py-4">Sin clases en este rango.</div>';
    }

    eventos.sort((a, b) => {
        const da = a.date.getTime() - b.date.getTime();
        if (da !== 0) return da;
        return String(a.horaInicio || '').localeCompare(String(b.horaInicio || ''));
    });

    const byDateKey = {};
    eventos.forEach(ev => {
        const k = ev.date.toISOString().substring(0, 10);
        byDateKey[k] = byDateKey[k] || { date: ev.date, items: [] };
        byDateKey[k].items.push(ev);
    });

    const dateKeys = Object.keys(byDateKey).sort();

    let html = '';
    html += '<div class="d-flex justify-content-between align-items-center mb-2">';
    html += '<small class="text-muted">Mostrando ' + backDays + ' días atrás y ' + fwdDays + ' días adelante.</small>';
    html += '</div>';

    dateKeys.forEach(k => {
        const grp = byDateKey[k];
        html += '<div class="mb-3">';
        html += '<div class="fw-semibold mb-2">' + fmtFecha(grp.date) + '</div>';
        html += '<div class="list-group">';
        grp.items.forEach(it => {
            const col = getColorForAsignatura(it.asignaturaId);
            const n = (it.asignaturaNombre || '—').replace(/</g, '&lt;');
            const m = (it.maestroNombre || '').replace(/</g, '&lt;');
            const g = (it.grupo || '').replace(/</g, '&lt;');
            const a = (it.aula || '').replace(/</g, '&lt;');
            const rango = (it.fechaInicio && it.fechaFin) ? (it.fechaInicio + ' → ' + it.fechaFin) : '';

            let sub = '';
            if (tipo === 'maestro') {
                sub = (g ? ('Grupo ' + g) : '') + (a ? (' · ' + a) : '');
            } else {
                sub = (m ? m : '') + (a ? (' · ' + a) : '');
                if (g) sub += (sub ? ' · ' : '') + ('Grupo ' + g);
            }

            html += '<div class="list-group-item">';
            html += '<div class="d-flex gap-2">';
            html += '<div style="width:6px;border-radius:6px;background:' + col.border + '"></div>';
            html += '<div class="flex-grow-1">';
            html += '<div class="d-flex justify-content-between gap-2">';
            html += '<div class="fw-semibold text-truncate">' + n + '</div>';
            html += '<div class="text-nowrap small text-muted">' + (it.horaInicio || '—') + ' - ' + (it.horaFin || '—') + '</div>';
            html += '</div>';
            if (sub) html += '<div class="small text-muted text-truncate">' + sub + '</div>';
            if (rango) html += '<div class="small text-muted">Vigencia: ' + rango + '</div>';
            html += '</div></div></div>';
        });
        html += '</div></div>';
    });

    if (sinFechas.length > 0) {
        html += '<div class="alert alert-warning small mt-3 mb-0">';
        html += '<strong>Clases sin fechas</strong>: hay bloques sin fechaInicio/fechaFin y no se pueden mostrar en agenda.';
        html += '</div>';
    }

    return html;
}

/**
 * Renderiza un calendario navegable (semana/mes/lista) a partir de bloques de horario.
 * Requiere FullCalendar cargado (CDN) en la página.
 *
 * @param {HTMLElement|string} targetElOrId - elemento destino o id del contenedor
 * @param {Array} bloques - lista de bloques (API /maestros/me/horarios o /alumnos/me/horarios)
 * @param {Object} opciones - { tipo: 'alumno'|'maestro' }
 * @returns {{calendar: any, missingDates: number}} instancia y conteo de bloques sin fechas
 */
function renderHorarioCalendar(targetElOrId, bloques, opciones) {
    const tipo = (opciones && opciones.tipo) || 'maestro';
    const el = (typeof targetElOrId === 'string') ? document.getElementById(targetElOrId) : targetElOrId;
    if (!el) return { calendar: null, missingDates: 0 };
    if (typeof FullCalendar === 'undefined' || !FullCalendar.Calendar) {
        el.innerHTML = '<div class="text-center text-danger py-4">No se cargó el calendario.</div>';
        return { calendar: null, missingDates: 0 };
    }

    // destruir anterior si existe
    try {
        if (el._hvFullCalendarInstance && typeof el._hvFullCalendarInstance.destroy === 'function') {
            el._hvFullCalendarInstance.destroy();
        }
    } catch (_) {}
    el._hvFullCalendarInstance = null;
    el.innerHTML = '';

    const lista = Array.isArray(bloques) ? bloques : [];
    const diaToFc = (d) => {
        const s = String(d || '').toUpperCase();
        if (s === 'LUNES') return 1;
        if (s === 'MARTES') return 2;
        if (s === 'MIERCOLES' || s === 'MIÉRCOLES') return 3;
        if (s === 'JUEVES') return 4;
        if (s === 'VIERNES') return 5;
        if (s === 'SABADO' || s === 'SÁBADO') return 6;
        if (s === 'DOMINGO') return 0;
        return null;
    };
    const fmtHora = (h) => {
        if (!h) return null;
        const s = String(h).trim();
        return s.length >= 5 ? s.substring(0, 5) : s;
    };
    const ymd = (s) => {
        if (!s) return null;
        return String(s).substring(0, 10);
    };
    const addDaysYmd = (ymdStr, days) => {
        try {
            const [y, m, d] = String(ymdStr).split('-').map(x => parseInt(x, 10));
            if (!y || !m || !d) return null;
            const dt = new Date(y, m - 1, d);
            dt.setDate(dt.getDate() + (days || 0));
            const yy = dt.getFullYear();
            const mm = String(dt.getMonth() + 1).padStart(2, '0');
            const dd = String(dt.getDate()).padStart(2, '0');
            return yy + '-' + mm + '-' + dd;
        } catch (_) {
            return null;
        }
    };

    // Colores consistentes por asignatura (si existe helper actual)
    try { setAsignaturaColorMapFromIds((lista || []).map(b => extraerAsignaturaId(b))); } catch (_) {}

    let missingDates = 0;
    const events = [];
    (lista || []).forEach((b) => {
        const dow = diaToFc(b.dia);
        const hi = fmtHora(b.horaInicio);
        const hf = fmtHora(b.horaFin);
        const fi = ymd(b.fechaInicio);
        const ff = ymd(b.fechaFin);
        if (dow == null || !hi || !hf) return;
        if (!fi || !ff) {
            missingDates++;
            return;
        }
        const endRecur = addDaysYmd(ff, 1); // exclusivo
        const asig = extraerAsignaturaNombre(b);
        const grupo = b.grupo || (b.grupoEntity && b.grupoEntity.nombre) || '';
        const maestroNombre = (b.maestroNombre != null && b.maestroNombre !== '') ? b.maestroNombre : extraerMaestroNombre(b);
        const aula = b.aula || '';

        let title = asig || 'Clase';
        if (tipo === 'maestro') {
            if (grupo) title = title + ' · Grupo ' + grupo;
            if (aula) title = title + ' · ' + aula;
        } else {
            if (maestroNombre) title = title + ' · ' + maestroNombre;
            if (aula) title = title + ' · ' + aula;
            if (grupo) title = title + ' · Grupo ' + grupo;
        }

        const aid = extraerAsignaturaId(b);
        let border = '#0d6efd', bg = '#d4edff';
        try {
            const col = getColorForAsignatura(aid);
            if (col && col.border) border = col.border;
            if (col && col.bg) bg = col.bg;
        } catch (_) {}

        events.push({
            title,
            startTime: hi + ':00',
            endTime: hf + ':00',
            daysOfWeek: [dow],
            startRecur: fi,
            endRecur: endRecur || ff,
            backgroundColor: bg,
            borderColor: border,
            textColor: '#0b1320',
            extendedProps: {
                asignatura: asig,
                grupo,
                aula,
                maestroNombre,
                fechaInicio: fi,
                fechaFin: ff,
                horaInicio: hi,
                horaFin: hf
            }
        });
    });

    const rightToolbar = (tipo === 'maestro')
        ? 'timeGridWeek,dayGridMonth'
        : 'timeGridWeek,dayGridMonth,listWeek';

    const buttonText = (tipo === 'maestro')
        ? { today: 'Hoy', week: 'Semana', month: 'Mes' }
        : { today: 'Hoy', week: 'Semana', month: 'Mes', list: 'Lista' };

    const calendar = new FullCalendar.Calendar(el, {
        locale: 'es',
        initialView: 'timeGridWeek',
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: rightToolbar
        },
        buttonText: buttonText,
        height: 'auto',
        nowIndicator: true,
        firstDay: 1,
        allDaySlot: false,
        slotMinTime: '07:00:00',
        slotMaxTime: '21:00:00',
        slotDuration: '01:00:00',
        expandRows: true,
        navLinks: true,
        eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
        events,
        eventClick: function (info) {
            try {
                const p = (info && info.event && info.event.extendedProps) ? info.event.extendedProps : {};
                const parts = [];
                if (p.asignatura) parts.push('Asignatura: ' + p.asignatura);
                if (tipo === 'maestro' && p.grupo) parts.push('Grupo: ' + p.grupo);
                if (tipo === 'alumno' && p.maestroNombre) parts.push('Docente: ' + p.maestroNombre);
                if (p.aula) parts.push('Aula: ' + p.aula);
                if (p.horaInicio && p.horaFin) parts.push('Hora: ' + p.horaInicio + ' - ' + p.horaFin);
                if (p.fechaInicio && p.fechaFin) parts.push('Vigencia: ' + p.fechaInicio + ' → ' + p.fechaFin);
                alert(parts.join('\n'));
            } catch (_) {}
        }
    });

    calendar.render();
    el._hvFullCalendarInstance = calendar;
    return { calendar, missingDates };
}

// Exponer en window para páginas que lo llaman desde scripts inline
try { window.renderHorarioCalendar = renderHorarioCalendar; } catch (_) {}

function renderizarListaHorariosGraficos(bloques) {
    const cont = document.getElementById('hvListaHorariosGraficos');
    if (!cont) return;
    if (!bloques || bloques.length === 0) {
        cont.innerHTML = '<div class="text-center text-muted py-4">No hay horarios registrados.</div>';
        return;
    }
    const grupos = {};
    bloques.forEach(b => {
        // Agrupar de forma estable: algunos endpoints/devuelven grupoEntity, otros solo grupoId o "grupo" (string).
        // Si no unificamos por grupoId, se fragmenta el mismo horario y parece que “falta” una materia.
        const gId = (b.grupoEntity && b.grupoEntity.id) || b.grupoId || ('str_' + (b.grupo || ''));
        const key = String(gId);
        if (!grupos[key]) {
            const ge = b.grupoEntity;
            const np = ge && ge.numeroPeriodo != null ? ge.numeroPeriodo : null;
            grupos[key] = {
                bloques: [],
                grupoId: ge && ge.id,
                periodoAcadId: b.periodoAcademico && b.periodoAcademico.id,
                programa: b.programa || {},
                grupoNombre: (ge && ge.nombre) || b.grupo || '—',
                numeroPeriodoPlan: np,
                periodoNombre: ''
            };
        }
        grupos[key].bloques.push(b);
    });
    setAsignaturaColorMapFromIds((bloques || []).map(b => extraerAsignaturaId(b)));
    hvGruposHorariosCache = [];
    let html = '';
    Object.keys(grupos).forEach(k => {
        const g = grupos[k];
        hvGruposHorariosCache.push(g);
        const idx = hvGruposHorariosCache.length - 1;
        const nivelTxt = g.numeroPeriodoPlan != null
            ? (' · ' + etiquetaPeriodoResumen(g.numeroPeriodoPlan))
            : '';
        const titulo = (g.programa.nombre || g.programa.clave || '') + ' — ' + g.grupoNombre + nivelTxt;
        const calHtml = buildCalendarioGraficoHtml(g.bloques);
        const puedeEliminar = g.grupoId;
        html += '<div class="card mb-4"><div class="card-header bg-soft-primary d-flex justify-content-between align-items-center py-2">';
        html += '<span class="fw-semibold">' + (titulo || 'Horario') + '</span>';
        html += '<div class="btn-group btn-group-sm">';
        html += '<button type="button" class="btn btn-outline-primary" onclick="editarHorarioCompleto(' + idx + ')" title="Editar todo el horario"><i class="bi bi-pencil me-1"></i>Editar todo</button>';
        if (puedeEliminar) {
            html += '<button type="button" class="btn btn-outline-danger" onclick="eliminarHorarioCompleto(' + g.grupoId + ')" title="Eliminar todo el horario"><i class="bi bi-trash me-1"></i>Eliminar todo</button>';
        } else {
            html += '<button type="button" class="btn btn-outline-secondary" disabled title="Eliminar todo (requiere grupo)"><i class="bi bi-trash me-1"></i>Eliminar todo</button>';
        }
        html += '</div></div><div class="card-body p-2 overflow-auto" style="max-height: 55vh;">' + calHtml + '</div></div>';
    });
    cont.innerHTML = html;
}

async function eliminarHorarioCompleto(grupoId) {
    const msgConfirm = '¿Eliminar todo el horario de este grupo? Se borrarán todas las clases.';
    let ok = false;
    if (typeof window.uiConfirm === 'function') {
        ok = await window.uiConfirm(msgConfirm, {
            title: 'Eliminar horario',
            subtitle: 'Esta acción no se puede deshacer',
            okText: 'Eliminar',
            cancelText: 'Cancelar'
        });
    } else {
        ok = window.confirm(msgConfirm);
    }
    if (!ok) return;
    try {
        const base = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
        const token = localStorage.getItem('token');
        const headers = {};
        if (token && token !== 'null') headers['Authorization'] = 'Bearer ' + token;
        const r = await fetch(base + '/horarios?grupoId=' + encodeURIComponent(grupoId), { method: 'DELETE', headers });
        if (!r.ok) {
            const errBody = await r.json().catch(function () { return {}; });
            const detail = errBody.mensaje || errBody.message || ('Error ' + r.status);
            throw new Error(detail);
        }
        cargarListaHorarios();
        if (typeof showSystemToast === 'function') showSystemToast('Horario eliminado.', { type: 'success' });
    } catch (e) {
        console.error('Error eliminar:', e);
        const text = (e && e.message) ? e.message : 'No se pudo eliminar el horario.';
        if (typeof showSystemToast === 'function') showSystemToast(text, { type: 'error', durationMs: 6200 });
    }
}

let hvGruposHorariosCache = [];

function editarHorarioCompleto(idx) {
    const meta = hvGruposHorariosCache && hvGruposHorariosCache[idx];
    if (!meta) {
        alert('Error al cargar datos del horario.');
        return;
    }
    if (!meta.programa || !meta.programa.id || !meta.grupoId || !meta.bloques || meta.bloques.length === 0) {
        alert('Faltan datos para editar el horario.');
        return;
    }
    document.getElementById('hvPrograma').value = meta.programa.id;
    cargarPeriodosPlanEnMemoria(meta.programa.id).then(() => cargarGrupos(meta.programa.id)).then(() => {
        const selGrupo = document.getElementById('hvGrupo');
        if (selGrupo) {
            selGrupo.value = meta.grupoId;
            selGrupo.disabled = false;
        }
        const grupoSel = grupos.find(g => String(g.id) === String(meta.grupoId));
        if (!grupoSel && meta.grupoId) {
            grupos = [{ id: meta.grupoId, nombre: meta.grupoNombre || 'Grupo', numeroPeriodo: null, periodoAcademico: meta.periodoAcadId ? { id: meta.periodoAcadId } : null }];
            if (selGrupo) {
                const opt = document.createElement('option');
                opt.value = meta.grupoId;
                opt.textContent = meta.grupoNombre || 'Grupo';
                selGrupo.appendChild(opt);
                selGrupo.value = String(meta.grupoId);
            }
        }
        const periodoNum = meta.bloques[0] && meta.bloques[0].asignatura && meta.bloques[0].asignatura.periodo;
        const numBloque = periodoNum && (periodoNum.numero != null ? periodoNum.numero : null);
        const g = grupos.find(x => String(x.id) === String(meta.grupoId));
        const num = (g && g.numeroPeriodo != null) ? g.numeroPeriodo : numBloque;
        return cargarAsignaturasPeriodo(num);
    }).then(() => {
        asignaturaMaestroMap = {};
        meta.bloques.forEach(b => {
            const aid = String(b.asignatura && b.asignatura.id);
            const mid = b.maestro && b.maestro.id;
            const mNom = extraerMaestroNombre(b);
            const fIni = b.fechaInicio ? String(b.fechaInicio).substring(0, 10) : '';
            const fFin = b.fechaFin ? String(b.fechaFin).substring(0, 10) : '';
            if (aid) {
                const midStr = mid != null && mid !== '' ? String(mid) : null;
                asignaturaMaestroMap[aid] = { maestroId: midStr, maestroNombre: mNom, fechaInicio: fIni, fechaFin: fFin };
            }
        });
        horarioState = {};
        meta.bloques.forEach(b => {
            const d = (b.dia || '').toUpperCase();
            const hi = fmtHoraConsulta(b.horaInicio);
            const hf = fmtHoraConsulta(b.horaFin);
            const claseIni = minutosDesdeMedianocheConsulta(hi);
            const claseFin = minutosDesdeMedianocheConsulta(hf);
            const fIni = b.fechaInicio ? String(b.fechaInicio).substring(0, 10) : '';
            const fFin = b.fechaFin ? String(b.fechaFin).substring(0, 10) : '';
            for (let i = 0; i < HORAS_DEFAULT.length; i++) {
                if (slotOverlapsClaseConsulta(i, claseIni, claseFin)) {
                    const key = d + '_' + HORAS_DEFAULT[i];
                    agregarBloqueACelda(key, {
                        asignaturaId: b.asignatura && b.asignatura.id,
                        asignaturaNombre: extraerAsignaturaNombre(b),
                        maestroId: b.maestro && b.maestro.id,
                        maestroNombre: extraerMaestroNombre(b),
                        aula: b.aula || '',
                        fechaInicio: fIni,
                        fechaFin: fFin,
                        cid: (b.id != null ? ('srv_' + b.id) : null)
                    });
                }
            }
        });
        const tabEl = document.getElementById('tab-crear-horario');
        if (tabEl) {
            const tab = new bootstrap.Tab(tabEl);
            tab.show();
        }
        document.getElementById('hvFormularioInicial').classList.add('d-none');
        document.getElementById('hvAsignarMaestros').classList.add('d-none');
        document.getElementById('hvVistaTabla').classList.remove('d-none');
        document.getElementById('hvResumenContainer').classList.remove('d-none');
        vistaTablaActiva = true;
        mostrarResumenSeleccion();
        renderizarMateriasPendientes(asignaturasPeriodo);
        renderizarCalendarioDesdeEstado();
        renderizarNotificaciones();
    });
}

/**
 * Filtra asignaturas por número de periodo del plan (el mismo que tiene el grupo).
 * @param {number|null|undefined} numeroPeriodo - nivel del plan (1, 2, …)
 */
async function cargarAsignaturasPeriodo(numeroPeriodo) {
    const programaId = document.getElementById('hvPrograma').value;
    if (!programaId) {
        asignaturasPeriodo = [];
        return [];
    }
    try {
        const asignaturas = await apiFetch('/asignaturas?programaId=' + programaId);
        let lista = [];
        if (numeroPeriodo == null || numeroPeriodo === '') {
            lista = asignaturas || [];
        } else {
            const n = parseInt(String(numeroPeriodo), 10);
            const num = (a) => a.periodoNumero ?? (a.periodo && a.periodo.numero);
            const filtradas = (asignaturas || []).filter(a => num(a) === n);
            lista = filtradas.length > 0 ? filtradas : (asignaturas || []);
        }
        asignaturasPeriodo = lista;
        return asignaturasPeriodo;
    } catch (e) {
        console.error('Error cargar asignaturas:', e);
        asignaturasPeriodo = [];
        return [];
    }
}

async function cargarMaestros() {
    try {
        // Importante: en el sistema unificado, la lista de "usuarios" es la fuente de verdad.
        // Evitar mostrar maestros legados que aún existan en /maestros pero ya no se gestionan en Usuarios.
        const staff = await apiFetch('/personal/staff');
        const lista = Array.isArray(staff) ? staff : [];
        maestros = lista
            .filter(r => r && r.maestroId != null)
            .filter(r => (r.roles || []).some(x => String(x).toUpperCase() === 'MAESTRO'))
            .filter(r => r.soloMaestroLegacy !== true) // excluir legado antiguo
            .filter(r => r.activo !== false) // solo activos
            .map(r => ({
                id: r.maestroId,
                nombre: r.nombre || '',
                apellidoPaterno: r.apellidoPaterno || '',
                apellidoMaterno: r.apellidoMaterno || '',
                etiqueta: r.etiqueta || ''
            }))
            .sort((a, b) => {
                const na = (a.apellidoPaterno + ' ' + a.apellidoMaterno + ' ' + a.nombre).trim().toLowerCase();
                const nb = (b.apellidoPaterno + ' ' + b.apellidoMaterno + ' ' + b.nombre).trim().toLowerCase();
                return na.localeCompare(nb, 'es');
            });
        return maestros || [];
    } catch (e) {
        console.error('Error cargar maestros:', e);
        maestros = [];
        return [];
    }
}

function renderizarMateriasPendientes(asignaturas) {
    buildAsignaturaColorMap();
    const cont = document.getElementById('hvMateriasPendientes');
    if (!cont) return;
    if (!asignaturas || asignaturas.length === 0) {
        cont.innerHTML = '<p class="text-muted small mb-0">No hay asignaturas para este periodo.</p>';
        configurarDragDrop();
        return;
    }
    // Permitir continuar aunque no haya maestros asignados todavía.
    cont.innerHTML = (asignaturas || []).map(a => {
        const map = asignaturaMaestroMap[String(a.id)];
        const nombre = (a.nombre || a.clave || 'Asignatura ' + a.id).replace(/"/g, '&quot;');
        const label = a.nombre || a.clave || 'Asignatura ' + a.id;
        const col = getColorForAsignatura(a.id);
        const maestroId = map && map.maestroId ? map.maestroId : '';
        const maestroNombre = map && map.maestroNombre ? map.maestroNombre : '';
        const fIni = map && map.fechaInicio ? map.fechaInicio : '';
        const fFin = map && map.fechaFin ? map.fechaFin : '';
        const rango = (fIni && fFin) ? (fIni + ' → ' + fFin) : '';
        const rangoHtml = rango
            ? `<small class="text-muted d-block">${rango}</small>`
            : `<small class="text-danger d-block">Falta periodo (inicio/fin)</small>`;
        const docenteHtml = maestroId
            ? `<small class="text-muted d-block">${(maestroNombre || '—')}</small>`
            : `<button type="button" class="btn btn-link p-0 small text-muted hv-asignar-docente" data-asignatura-id="${a.id}" data-asignatura-nombre="${nombre}">Sin docente asignado</button>`;
        return `
        <div class="border rounded p-2 mb-2 hv-materia-item" data-asignatura-id="${a.id}" data-asignatura-nombre="${nombre}" data-maestro-id="${maestroId || ''}" data-maestro-nombre="${(maestroNombre || '').replace(/"/g, '&quot;')}" draggable="true" style="background-color:${col.bg};border-left:4px solid ${col.border}">
            <small class="fw-semibold">${label}</small>
            ${rangoHtml}
            ${docenteHtml}
        </div>
    `}).join('');
    configurarDragDrop();
}

async function abrirModalAsignarDocentePendiente(asignaturaId, asignaturaNombre) {
    const modalEl = document.getElementById('hvModalDocente');
    if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap.Modal) return;

    if (!maestros || maestros.length === 0) {
        await cargarMaestros();
    }

    const inputAid = document.getElementById('hvModalDocenteAsignaturaId');
    const titulo = document.getElementById('hvModalDocenteAsignaturaNombre');
    const sel = document.getElementById('hvModalDocenteSelect');
    if (!inputAid || !titulo || !sel) return;

    inputAid.value = String(asignaturaId || '');
    titulo.textContent = (asignaturaNombre || '—').replace(/&quot;/g, '"');

    const prev = asignaturaMaestroMap[String(asignaturaId)] || {};
    let opts = '<option value="">Sin docente asignado</option>';
    (maestros || []).forEach(m => {
        const nombreM = ((m.etiqueta ? m.etiqueta + ' ' : '') + (m.nombre || '') + ' ' + (m.apellidoPaterno || '') + ' ' + (m.apellidoMaterno || '')).trim();
        const selAttr = prev.maestroId != null && String(prev.maestroId) === String(m.id) ? ' selected' : '';
        opts += '<option value="' + m.id + '"' + selAttr + '>' + (nombreM || ('Docente ' + m.id)) + '</option>';
    });
    sel.innerHTML = opts;

    new bootstrap.Modal(modalEl).show();
}

function guardarDocentePendienteDesdeModal() {
    const modalEl = document.getElementById('hvModalDocente');
    const inputAid = document.getElementById('hvModalDocenteAsignaturaId');
    const sel = document.getElementById('hvModalDocenteSelect');
    if (!modalEl || !inputAid || !sel) return;

    const aid = String(inputAid.value || '');
    if (!aid) return;

    const maestroId = sel.value ? String(sel.value) : null;
    const maestroNombre = maestroId ? (sel.options[sel.selectedIndex]?.text || '') : '';
    asignaturaMaestroMap[aid] = { ...(asignaturaMaestroMap[aid] || {}), maestroId, maestroNombre };

    // Si la materia ya está colocada en el calendario, actualizarla también.
    Object.keys(horarioState || {}).forEach(k => {
        const arr = estadoGetArr(k);
        let changed = false;
        arr.forEach(b => {
            if (!b) return;
            if (String(b.asignaturaId) === String(aid)) {
                b.maestroId = maestroId;
                b.maestroNombre = maestroNombre || '';
                changed = true;
            }
        });
        if (changed) estadoSetArr(k, arr);
    });

    const inst = bootstrap.Modal.getInstance(modalEl);
    if (inst) inst.hide();
    renderizarCalendarioDesdeEstado();
    refreshMateriasPendientes();

    if (typeof showSystemToast === 'function') {
        if (maestroId) showSystemToast('Docente asignado.', { type: 'success' });
        else showSystemToast('Docente removido.', { type: 'info' });
    }
}

function configurarDragDrop() {
    const materias = document.querySelectorAll('.hv-materia-item');
    const celdas = document.querySelectorAll('.hv-celda');
    materias.forEach(el => {
        el.setAttribute('draggable', 'true');
        el.ondragstart = onDragStartMateria;
        el.ondragend = function () { el.classList.remove('opacity-50'); };
    });
    celdas.forEach(celda => {
        celda.ondragover = onDragOver;
        celda.ondrop = onDrop;
        celda.ondragenter = onDragEnter;
        celda.ondragleave = onDragLeave;
        celda.onclick = function (ev) {
            if (!hvBloqueCopiado) return;
            const dia = celda.dataset.dia;
            const hora = celda.dataset.hora;
            if (!dia || !hora) return;
            if (pegarBloqueCopiado(dia, hora)) {
                renderizarCalendarioDesdeEstado();
                refreshMateriasPendientes();
            } else {
                alert('No hay espacio suficiente en ese horario o la celda está ocupada.');
            }
        };
    });
}

function onDragStartMateria(e) {
    hvTarjetaEnDrag = null;
    const id = e.target.dataset.asignaturaId;
    const nombre = e.target.dataset.asignaturaNombre || e.target.textContent.trim();
    const map = asignaturaMaestroMap[String(id)] || {};
    const maestroId = map.maestroId || e.target.dataset.maestroId || null;
    const maestroNombre = map.maestroNombre || e.target.dataset.maestroNombre || '';
    const fechaInicio = map.fechaInicio || '';
    const fechaFin = map.fechaFin || '';
    const sig = (id && fechaInicio && fechaFin) ? ('asig_' + id + '_' + fechaInicio + '_' + fechaFin) : '';
    e.dataTransfer.setData('application/json', JSON.stringify({ asignaturaId: id, asignaturaNombre: nombre, maestroId, maestroNombre, fechaInicio, fechaFin, sig }));
    e.dataTransfer.effectAllowed = 'copy';
    e.target.classList.add('opacity-50');
}

function onDragStartTarjeta(e) {
    if (e.target.closest('.hv-tarjeta-remove') || e.target.closest('.hv-tarjeta-aula') || e.target.closest('.hv-tarjeta-duplicar')) {
        e.preventDefault();
        return;
    }
    const tarjeta = e.currentTarget;
    hvTarjetaEnDrag = tarjeta;
    const id = tarjeta.dataset.asignaturaId;
    const nombre = tarjeta.dataset.asignaturaNombre || '';
    const maestroId = tarjeta.dataset.maestroId || '';
    const maestroNombre = tarjeta.dataset.maestroNombre || '';
    const aula = tarjeta.dataset.aula || '';
    const fechaInicio = tarjeta.dataset.fechaInicio || '';
    const fechaFin = tarjeta.dataset.fechaFin || '';
    const sig = tarjeta.dataset.sig || '';
    e.dataTransfer.setData('application/json', JSON.stringify({ asignaturaId: id, asignaturaNombre: nombre, maestroId, maestroNombre, aula, fechaInicio, fechaFin, sig }));
    e.dataTransfer.effectAllowed = 'move';
    tarjeta.classList.add('opacity-50');
}

function refreshMateriasPendientes() {
    renderizarMateriasPendientes(asignaturasPeriodo);
    actualizarNotificaciones();
}

function onDragOver(e) {
    e.preventDefault();
    e.dataTransfer.dropEffect = hvTarjetaEnDrag ? 'move' : 'copy';
}

function onDragEnter(e) {
    e.preventDefault();
    const celda = e.currentTarget;
    if (celda.classList.contains('hv-celda')) celda.classList.add('hv-celda-drag-over');
}

function onDragLeave(e) {
    const celda = e.currentTarget;
    if (celda.classList.contains('hv-celda')) celda.classList.remove('hv-celda-drag-over');
}

function obtenerBloqueCompleto(dia, hora, sig) {
    const key = dia + '_' + hora;
    const arr = estadoGetArr(key);
    if (!arr || arr.length === 0) return [];
    const bloque = arr.find(x => x && (!sig || bloqueSigFront(x) === sig));
    if (!bloque) return [];
    const s = sig || bloqueSigFront(bloque);
    const keys = [key];
    const idx = HORAS_DEFAULT.indexOf(hora);
    for (let i = idx + 1; i < HORAS_DEFAULT.length; i++) {
        const k = dia + '_' + HORAS_DEFAULT[i];
        const arr2 = estadoGetArr(k);
        const has = (arr2 || []).some(x => x && bloqueSigFront(x) === s);
        if (!has) break;
        keys.push(k);
    }
    return keys;
}

function copiarBloqueParaDuplicar(dia, hora, sig) {
    const key = dia + '_' + hora;
    const arr = estadoGetArr(key);
    const bloque = (arr || []).find(x => x && (!sig || bloqueSigFront(x) === sig)) || null;
    if (!bloque) return;
    const s = sig || bloqueSigFront(bloque);
    const keys = obtenerBloqueCompleto(dia, hora, s);
    hvBloqueCopiado = {
        asignaturaId: bloque.asignaturaId,
        asignaturaNombre: bloque.asignaturaNombre,
        maestroId: bloque.maestroId || null,
        maestroNombre: bloque.maestroNombre || '',
        aula: bloque.aula || '',
        fechaInicio: bloque.fechaInicio || '',
        fechaFin: bloque.fechaFin || '',
        span: keys.length
    };
    mostrarNotificacionBloqueCopiado();
}

function pegarBloqueCopiado(dia, hora) {
    if (!hvBloqueCopiado) return false;
    const idx = HORAS_DEFAULT.indexOf(hora);
    if (idx < 0) return false;
    const bloque = {
        asignaturaId: hvBloqueCopiado.asignaturaId,
        asignaturaNombre: hvBloqueCopiado.asignaturaNombre,
        maestroId: hvBloqueCopiado.maestroId,
        maestroNombre: hvBloqueCopiado.maestroNombre,
        aula: hvBloqueCopiado.aula || '',
        fechaInicio: hvBloqueCopiado.fechaInicio || '',
        fechaFin: hvBloqueCopiado.fechaFin || ''
    };
    for (let i = 0; i < hvBloqueCopiado.span; i++) {
        const h = HORAS_DEFAULT[idx + i];
        if (!h) return false;
        const k = dia + '_' + h;
        if (!celdaPermiteBloque(k, bloque)) return false;
    }
    for (let i = 0; i < hvBloqueCopiado.span; i++) {
        const h = HORAS_DEFAULT[idx + i];
        if (h) agregarBloqueACelda(dia + '_' + h, bloque);
    }
    return true;
}

function mostrarNotificacionBloqueCopiado() {
    const cont = document.getElementById('hvNotificaciones');
    if (!cont) return;
    const prev = cont.innerHTML;
    cont.innerHTML = '<div class="alert alert-success small mb-2"><i class="bi bi-check-circle me-1"></i>Bloque copiado. Haz clic en una celda vacía del día que quieras para pegarlo.</div>' + prev;
    setTimeout(() => {
        const first = cont.querySelector('.alert-success');
        if (first) first.remove();
    }, 4000);
}

function onDrop(e) {
    e.preventDefault();
    const celda = e.currentTarget;
    if (!celda.classList.contains('hv-celda')) return;
    celda.classList.remove('hv-celda-drag-over');
    const dia = celda.dataset.dia;
    const hora = celda.dataset.hora;
    const key = dia + '_' + hora;
    try {
        if (hvTarjetaEnDrag) {
            const oldDia = hvTarjetaEnDrag.dataset.dia;
            const oldHora = hvTarjetaEnDrag.dataset.hora;
            const sig = hvTarjetaEnDrag.dataset.sig || '';
            const oldKey = oldDia + '_' + oldHora;
            if (oldKey === key) {
                hvTarjetaEnDrag = null;
                return;
            }
            const keysToMove = obtenerBloqueCompleto(oldDia, oldHora, sig);
            const oldArr = estadoGetArr(oldKey);
            const bloque = oldArr.find(x => x && bloqueSigFront(x) === sig) || null;
            if (!bloque || keysToMove.length === 0) {
                hvTarjetaEnDrag = null;
                return;
            }
            const idx = HORAS_DEFAULT.indexOf(hora);
            // Validar destino antes de mover
            for (let i = 0; i < keysToMove.length; i++) {
                const h = HORAS_DEFAULT[idx + i];
                if (!h) { hvTarjetaEnDrag = null; return; }
                const k = dia + '_' + h;
                const v = validarCeldaParaBloque(k, bloque);
                if (!v.ok) { alert(v.reason || 'No se puede colocar ahí.'); hvTarjetaEnDrag = null; return; }
            }
            // Quitar del origen
            keysToMove.forEach(k => {
                const arr = estadoGetArr(k).filter(x => x && bloqueSigFront(x) !== sig);
                estadoSetArr(k, arr);
            });
            keysToMove.forEach((_, i) => {
                const h = HORAS_DEFAULT[idx + i];
                if (h) agregarBloqueACelda(dia + '_' + h, bloque);
            });
            hvTarjetaEnDrag = null;
        } else {
            const data = JSON.parse(e.dataTransfer.getData('application/json'));
            if (!data.asignaturaId || !data.asignaturaNombre) return;
            const bloque = {
                asignaturaId: data.asignaturaId,
                asignaturaNombre: data.asignaturaNombre,
                maestroId: data.maestroId || null,
                maestroNombre: data.maestroNombre || '',
                aula: data.aula || '',
                fechaInicio: data.fechaInicio || '',
                fechaFin: data.fechaFin || '',
                cid: data.sig || null
            };
            if (!bloque.fechaInicio || !bloque.fechaFin) {
                alert('Asigna fechas de inicio y fin a la materia antes de colocarla en el horario.');
                return;
            }
            const v2 = validarCeldaParaBloque(key, bloque);
            if (!v2.ok) { alert(v2.reason || 'No se puede colocar ahí.'); return; }
            agregarBloqueACelda(key, bloque);
        }
        renderizarCalendarioDesdeEstado();
        refreshMateriasPendientes();
    } catch (err) {
        console.warn('Drop fallido:', err);
    }
    document.querySelectorAll('.hv-materia-item').forEach(m => m.classList.remove('opacity-50'));
}

function eliminarBloque(dia, hora, sig) {
    const keys = obtenerBloqueCompleto(dia, hora, sig);
    keys.forEach(k => {
        const arr = estadoGetArr(k).filter(x => x && bloqueSigFront(x) !== sig);
        estadoSetArr(k, arr);
    });
    renderizarCalendarioDesdeEstado();
    refreshMateriasPendientes();
}

function abrirModalAula(dia, hora) {
    const sig = arguments.length >= 3 ? (arguments[2] || '') : '';
    hvTarjetaAulaEditando = { dia, hora, sig };
    const arr = estadoGetArr(dia + '_' + hora);
    const bloque = (arr || []).find(x => x && (!sig || bloqueSigFront(x) === sig)) || null;
    const input = document.getElementById('hvInputAula');
    if (input) input.value = bloque ? (bloque.aula || '') : '';
    const modal = new bootstrap.Modal(document.getElementById('hvModalAula'));
    modal.show();
}

function guardarAula() {
    if (!hvTarjetaAulaEditando) return;
    const keys = obtenerBloqueCompleto(hvTarjetaAulaEditando.dia, hvTarjetaAulaEditando.hora, hvTarjetaAulaEditando.sig || '');
    const input = document.getElementById('hvInputAula');
    const aulaVal = input ? input.value.trim() : '';
    keys.forEach(k => {
        const arr = estadoGetArr(k);
        let changed = false;
        arr.forEach(b => {
            if (!b) return;
            if (bloqueSigFront(b) === (hvTarjetaAulaEditando.sig || '')) {
                b.aula = aulaVal;
                changed = true;
            }
        });
        if (changed) estadoSetArr(k, arr);
    });
    hvTarjetaAulaEditando = null;
    const modalEl = document.getElementById('hvModalAula');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();
    renderizarCalendarioDesdeEstado();
}

function horaARango(h) {
    const [hh, mm] = h.split(':').map(Number);
    const fin = (hh * 60 + mm + 59) % (24 * 60);
    const finH = Math.floor(fin / 60);
    const finM = fin % 60;
    const finStr = (finH < 10 ? '0' : '') + finH + ':' + (finM < 10 ? '0' : '') + finM;
    return h + ' - ' + finStr;
}

function computeBloquesConsecutivos() {
    const bloques = [];
    DIAS_SEMANA.forEach(dia => {
        for (let i = 0; i < HORAS_DEFAULT.length; i++) {
            const h = HORAS_DEFAULT[i];
            const key = dia + '_' + h;
            const arr = estadoGetArr(key);
            if (!arr || arr.length === 0) continue;
            for (const bloque of arr) {
                if (!bloque) continue;
                const sig = bloqueSigFront(bloque);
                const prevKey = i > 0 ? dia + '_' + HORAS_DEFAULT[i - 1] : null;
                const prevArr = prevKey ? estadoGetArr(prevKey) : [];
                const esInicio = !prevKey || !(prevArr || []).some(x => x && bloqueSigFront(x) === sig);
                if (!esInicio) continue;
                let span = 1;
                for (let j = i + 1; j < HORAS_DEFAULT.length; j++) {
                    const nextKey = dia + '_' + HORAS_DEFAULT[j];
                    const nextArr = estadoGetArr(nextKey);
                    const has = (nextArr || []).some(x => x && bloqueSigFront(x) === sig);
                    if (!has) break;
                    span++;
                }
                bloques.push({ dia, hora: h, horaIndex: i, span, bloque });
            }
        }
    });
    return bloques;
}

function renderizarCalendarioDesdeEstado() {
    const cont = document.getElementById('hvCalendarioContenido');
    if (!cont) return;
    const diasLabels = { LUNES: 'LUNES', MARTES: 'MARTES', MIERCOLES: 'MIÉRCOLES', JUEVES: 'JUEVES', VIERNES: 'VIERNES', SABADO: 'SÁBADO' };

    // Re-habilitar "combinación" visual (rowspan) cuando un bloque ocupa varias horas
    // y no hay otros bloques en esas mismas celdas.
    const mergeStartAt = {};
    const covered = {};
    DIAS_SEMANA.forEach(dia => {
        for (let i = 0; i < HORAS_DEFAULT.length; i++) {
            const h = HORAS_DEFAULT[i];
            const key = dia + '_' + h;
            const arr = estadoGetArr(key);
            if (!arr || arr.length !== 1) continue; // solo combinamos cuando hay 1 tarjeta en la celda
            const b = arr[0];
            if (!b) continue;
            const cid = bloqueSigFront(b);
            if (!cid) continue;
            if (covered[key]) continue;

            // Debe ser inicio (la celda anterior no contiene el mismo cid)
            const prevKey = i > 0 ? (dia + '_' + HORAS_DEFAULT[i - 1]) : null;
            const prevArr = prevKey ? estadoGetArr(prevKey) : [];
            const prevHas = (prevArr || []).length === 1 && prevArr[0] && bloqueSigFront(prevArr[0]) === cid;
            if (prevHas) continue;

            // Calcular span hacia abajo mientras cada celda tenga exactamente esa misma tarjeta
            let span = 1;
            for (let j = i + 1; j < HORAS_DEFAULT.length; j++) {
                const k2 = dia + '_' + HORAS_DEFAULT[j];
                const a2 = estadoGetArr(k2);
                const ok = (a2 || []).length === 1 && a2[0] && bloqueSigFront(a2[0]) === cid;
                if (!ok) break;
                span++;
            }
            if (span > 1) {
                mergeStartAt[key] = { span, bloque: b };
                for (let j = i + 1; j < i + span; j++) {
                    covered[dia + '_' + HORAS_DEFAULT[j]] = true;
                }
            }
        }
    });

    let html = '<table class="table table-bordered table-sm hv-calendario-table"><thead><tr><th>HORA</th>';
    DIAS_SEMANA.forEach(d => { html += '<th>' + (diasLabels[d] || d) + '</th>'; });
    html += '</tr></thead><tbody>';

    HORAS_DEFAULT.forEach((h) => {
        html += '<tr><td class="text-nowrap bg-light small hv-celda-hora">' + horaARango(h) + '</td>';
        DIAS_SEMANA.forEach(dia => {
            const key = dia + '_' + h;
            if (covered[key]) return; // celda cubierta por rowspan anterior
            const merge = mergeStartAt[key];
            if (merge) {
                const b = merge.bloque;
                const nombreEsc = (b.asignaturaNombre || '').replace(/</g, '&lt;');
                const maestroEsc = (b.maestroNombre || '').replace(/</g, '&lt;');
                const aulaEsc = (b.aula || '').replace(/</g, '&lt;');
                const col = getColorForAsignatura(b.asignaturaId);
                const sig = bloqueSigFront(b);
                const rango = (b.fechaInicio && b.fechaFin) ? (b.fechaInicio + ' → ' + b.fechaFin) : '';
                html += '<td class="hv-celda hv-celda-merged" rowspan="' + merge.span + '" data-dia="' + dia + '" data-hora="' + h + '">';
                html += '<div class="hv-tarjeta-materia border rounded p-1 small d-flex flex-column" style="background-color:' + col.bg + ';border-left:4px solid ' + col.border + '" data-sig="' + sig + '" data-asignatura-id="' + b.asignaturaId + '" data-asignatura-nombre="' + nombreEsc + '" data-maestro-id="' + (b.maestroId || '') + '" data-maestro-nombre="' + maestroEsc + '" data-aula="' + aulaEsc + '" data-fecha-inicio="' + (b.fechaInicio || '') + '" data-fecha-fin="' + (b.fechaFin || '') + '" data-dia="' + dia + '" data-hora="' + h + '" draggable="true">';
                html += '<span class="fw-semibold hv-tarjeta-nombre text-truncate" title="' + nombreEsc + '">' + nombreEsc + '</span>';
                if (rango) html += '<span class="text-muted small text-truncate" title="' + rango + '">' + rango + '</span>';
                html += (b.maestroNombre ? '<span class="text-muted hv-tarjeta-maestro text-truncate" title="' + maestroEsc + '">' + maestroEsc + '</span>' : '');
                if (aulaEsc) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate" title="' + aulaEsc + '">' + aulaEsc + '</span>';
                html += '<div class="mt-auto pt-1 hv-tarjeta-actions"><button type="button" class="btn btn-sm btn-link p-0 text-secondary hv-tarjeta-duplicar" title="Duplicar en otro día"><i class="bi bi-copy"></i></button>';
                html += ' <button type="button" class="btn btn-sm btn-link p-0 text-secondary hv-tarjeta-aula" title="Asignar aula"><i class="bi bi-geo-alt"></i></button>';
                html += ' <button type="button" class="btn btn-sm btn-link p-0 text-danger hv-tarjeta-remove" title="Quitar"><i class="bi bi-x-lg"></i></button></div>';
                html += '</div>';
                html += '</td>';
                return;
            }
            const arr = estadoGetArr(key);
            html += '<td class="hv-celda" data-dia="' + dia + '" data-hora="' + h + '">';
            (arr || []).forEach(b => {
                if (!b) return;
                const nombreEsc = (b.asignaturaNombre || '').replace(/</g, '&lt;');
                const maestroEsc = (b.maestroNombre || '').replace(/</g, '&lt;');
                const aulaEsc = (b.aula || '').replace(/</g, '&lt;');
                const col = getColorForAsignatura(b.asignaturaId);
                const sig = bloqueSigFront(b);
                const rango = (b.fechaInicio && b.fechaFin) ? (b.fechaInicio + ' → ' + b.fechaFin) : '';
                html += '<div class="hv-tarjeta-materia border rounded p-1 small d-flex flex-column mb-1" style="background-color:' + col.bg + ';border-left:4px solid ' + col.border + '" data-sig="' + sig + '" data-asignatura-id="' + b.asignaturaId + '" data-asignatura-nombre="' + nombreEsc + '" data-maestro-id="' + (b.maestroId || '') + '" data-maestro-nombre="' + maestroEsc + '" data-aula="' + aulaEsc + '" data-fecha-inicio="' + (b.fechaInicio || '') + '" data-fecha-fin="' + (b.fechaFin || '') + '" data-dia="' + dia + '" data-hora="' + h + '" draggable="true">';
                html += '<span class="fw-semibold hv-tarjeta-nombre text-truncate" title="' + nombreEsc + '">' + nombreEsc + '</span>';
                if (rango) html += '<span class="text-muted small text-truncate" title="' + rango + '">' + rango + '</span>';
                html += (b.maestroNombre ? '<span class="text-muted hv-tarjeta-maestro text-truncate" title="' + maestroEsc + '">' + maestroEsc + '</span>' : '');
                if (aulaEsc) html += '<span class="small text-primary hv-tarjeta-aula-text text-truncate" title="' + aulaEsc + '">' + aulaEsc + '</span>';
                html += '<div class="mt-auto pt-1 hv-tarjeta-actions"><button type="button" class="btn btn-sm btn-link p-0 text-secondary hv-tarjeta-duplicar" title="Duplicar en otro día"><i class="bi bi-copy"></i></button>';
                html += ' <button type="button" class="btn btn-sm btn-link p-0 text-secondary hv-tarjeta-aula" title="Asignar aula"><i class="bi bi-geo-alt"></i></button>';
                html += ' <button type="button" class="btn btn-sm btn-link p-0 text-danger hv-tarjeta-remove" title="Quitar"><i class="bi bi-x-lg"></i></button></div>';
                html += '</div>';
            });
            html += '</td>';
        });
        html += '</tr>';
    });
    html += '</tbody></table>';
    cont.innerHTML = html;

    document.querySelectorAll('.hv-tarjeta-materia').forEach(tarjeta => {
        tarjeta.ondragstart = onDragStartTarjeta;
        tarjeta.ondragend = function () {
            tarjeta.classList.remove('opacity-50');
            hvTarjetaEnDrag = null;
        };
        const btnRemove = tarjeta.querySelector('.hv-tarjeta-remove');
        if (btnRemove) {
            btnRemove.addEventListener('click', function (ev) {
                ev.stopPropagation();
                eliminarBloque(tarjeta.dataset.dia, tarjeta.dataset.hora, tarjeta.dataset.sig || '');
            });
        }
        const btnAula = tarjeta.querySelector('.hv-tarjeta-aula');
        if (btnAula) {
            btnAula.addEventListener('click', function (ev) {
                ev.stopPropagation();
                abrirModalAula(tarjeta.dataset.dia, tarjeta.dataset.hora, tarjeta.dataset.sig || '');
            });
        }
        const btnDuplicar = tarjeta.querySelector('.hv-tarjeta-duplicar');
        if (btnDuplicar) {
            btnDuplicar.addEventListener('click', function (ev) {
                ev.stopPropagation();
                copiarBloqueParaDuplicar(tarjeta.dataset.dia, tarjeta.dataset.hora, tarjeta.dataset.sig || '');
            });
        }
        tarjeta.addEventListener('click', function (ev) {
            if (ev.target.closest('.hv-tarjeta-remove') || ev.target.closest('.hv-tarjeta-aula') || ev.target.closest('.hv-tarjeta-duplicar')) return;
            ev.preventDefault();
            abrirModalAula(tarjeta.dataset.dia, tarjeta.dataset.hora, tarjeta.dataset.sig || '');
        });
    });

    configurarDragDrop();
}

function renderizarCalendarioSemanal() {
    horarioState = {};
    renderizarCalendarioDesdeEstado();
}

/**
 * Convierte horarioState a lista de HorarioBloqueRequest y envía al backend.
 * Primero elimina bloques existentes del grupo+periodo, luego crea los nuevos.
 */
async function guardarHorarioVisual() {
    const programaId = document.getElementById('hvPrograma')?.value;
    const grupoId = document.getElementById('hvGrupo')?.value;

    if (!programaId || !grupoId) {
        alert('Selecciona programa y grupo.');
        return;
    }

    const bloquesInfo = computeBloquesConsecutivos();
    if (bloquesInfo.length === 0) {
        alert('No hay bloques en el horario. Arrastra materias al calendario antes de guardar.');
        return;
    }

    const requests = bloquesInfo.map(b => {
        const horaFinIdx = b.horaIndex + b.span;
        const horaFin = HORAS_DEFAULT[horaFinIdx] || '21:00';
        const maestroIdVal = b.bloque.maestroId ? parseInt(b.bloque.maestroId, 10) : null;
        const payload = {
            id: bloqueServidorIdDesdeCid(b.bloque.cid),
            programaId: parseInt(programaId, 10),
            asignaturaId: parseInt(b.bloque.asignaturaId, 10),
            grupoId: parseInt(grupoId, 10),
            dia: b.dia,
            horaInicio: b.hora,
            horaFin: horaFin,
            aula: b.bloque.aula || null,
            fechaInicio: b.bloque.fechaInicio || null,
            fechaFin: b.bloque.fechaFin || null
        };
        payload.maestroId = maestroIdVal;
        return payload;
    });

    const btn = document.getElementById('hvBtnGuardarHorario');
    const txtOrig = btn ? btn.innerHTML : '';
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Guardando...';
    }

    try {
        const base = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
        const token = localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token && token !== 'null' && token !== 'undefined') headers['Authorization'] = 'Bearer ' + token;

        const postRes = await fetch(base + '/horarios/grupo/' + encodeURIComponent(grupoId) + '/reemplazar', {
            method: 'PUT',
            headers,
            body: JSON.stringify(requests)
        });

        if (!postRes.ok) {
            const errBody = await postRes.json().catch(() => ({}));
            const msg = errBody.mensaje || errBody.message || 'Error ' + postRes.status;
            throw new Error(msg);
        }

        await postRes.json();

        const asignados = (requests || []).some(r => r && r.maestroId != null);
        const msg = 'Horario guardado con éxito.';
        if (typeof showSystemToast === 'function') {
            showSystemToast(msg, { type: 'success' });
            if (asignados) showSystemToast('Docentes asignados en el horario.', { type: 'info', durationMs: 3200 });
        }

        // Ir directo a la lista de horarios y recargar.
        try {
            const tabBtn = document.getElementById('tab-consultar-horario');
            if (tabBtn && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
                bootstrap.Tab.getOrCreateInstance(tabBtn).show();
            } else if (tabBtn) {
                tabBtn.click();
            }
        } catch (_) {}
        cargarListaHorarios();
    } catch (err) {
        console.error('Error al guardar horario:', err);
        if (typeof showSystemToast === 'function') {
            showSystemToast(err.message || 'No se pudo guardar el horario. Verifica la conexión y los permisos.', { type: 'error', durationMs: 5200 });
        } else {
            alert(err.message || 'No se pudo guardar el horario. Verifica la conexión y los permisos.');
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = txtOrig || '<i class="bi bi-check-lg"></i> Guardar horario';
        }
    }
}

function actualizarNotificaciones() {
    const cont = document.getElementById('hvNotificaciones');
    if (!cont) return;

    let html = '<div class="alert alert-info small mb-2"><i class="bi bi-info-circle me-1"></i>Arrastra las materias al horario libremente. Las horas de cada asignatura son solo datos informativos.</div>';
    html += '<p class="text-muted small mb-0 mt-2">Clic en tarjeta para asignar aula.</p>';
    cont.innerHTML = html;
}

function renderizarNotificaciones() {
    actualizarNotificaciones();
}

function mostrarResumenSeleccion() {
    const sel = document.getElementById('hvResumenSeleccion');
    if (!sel) return;
    const programa = document.getElementById('hvPrograma');
    const grupo = document.getElementById('hvGrupo');
    if (!programa || !grupo) return;
    const progOpt = programa.options[programa.selectedIndex];
    const g = getGrupoSeleccionado();
    const periodoLabel = etiquetaPeriodoDesdeGrupo(g);
    const nombreGrupo = g ? (g.nombre || '—') : '—';
    sel.innerHTML = `
        <strong>Programa:</strong> ${progOpt ? progOpt.text : '—'} |
        <strong>Periodo (según grupo):</strong> ${periodoLabel} |
        <strong>Grupo:</strong> ${nombreGrupo}
    `;
}

async function mostrarVistaAsignarMaestros() {
    const programaId = document.getElementById('hvPrograma').value;
    const grupoId = document.getElementById('hvGrupo').value;
    const g = getGrupoSeleccionado();
    if (!programaId || !grupoId || !g) {
        alert('Selecciona programa y grupo.');
        return;
    }
    if (g.numeroPeriodo == null) {
        alert('El grupo no tiene número de periodo asignado. Actualízalo en la pantalla Grupos.');
        return;
    }
    const resumen0 = document.getElementById('hvResumenContainer');
    const vista0 = document.getElementById('hvVistaTabla');
    if (resumen0) resumen0.classList.add('d-none');
    if (vista0) vista0.classList.add('d-none');
    vistaTablaActiva = false;

    document.getElementById('hvFormularioInicial').classList.add('d-none');
    document.getElementById('hvAsignarMaestros').classList.remove('d-none');
    await cargarAsignaturasPeriodo(g.numeroPeriodo);
    await cargarMaestros();
    renderizarAsignaturasMaestros();
}

function renderizarAsignaturasMaestros() {
    const cont = document.getElementById('hvAsignaturasMaestrosLista');
    if (!cont) return;
    // No reiniciar el mapa: permite volver desde la tabla sin perder asignaciones
    asignaturaMaestroMap = asignaturaMaestroMap || {};
    (asignaturasPeriodo || []).forEach(a => {
        asignaturaMaestroMap[String(a.id)] = asignaturaMaestroMap[String(a.id)] || {};
    });
    cont.innerHTML = (asignaturasPeriodo || []).map(a => {
        const map = asignaturaMaestroMap[String(a.id)] || {};
        const label = a.nombre || a.clave || 'Asignatura ' + a.id;
        const fIni = map.fechaInicio || '';
        const fFin = map.fechaFin || '';
        let opts = '<option value="">Selecciona maestro...</option>';
        (maestros || []).filter(m => m.activo !== false).forEach(m => {
            const nombreM = (m.etiqueta ? m.etiqueta + ' ' : '') + (m.nombre || '') + ' ' + (m.apellidoPaterno || '') + ' ' + (m.apellidoMaterno || '');
            const sel = String(map.maestroId || '') === String(m.id) ? ' selected' : '';
            opts += '<option value="' + m.id + '"' + sel + '>' + (nombreM.trim() || m.correoInstitucional || 'Maestro ' + m.id) + '</option>';
        });
        return `
        <div class="col-md-6 col-lg-4">
            <label class="form-label small">${label}</label>
            <select class="form-select form-select-sm hv-maestro-select" data-asignatura-id="${a.id}">
                ${opts}
            </select>
            <div class="row g-2 mt-1">
              <div class="col-6">
                <input type="date" class="form-control form-control-sm hv-fecha-inicio" data-asignatura-id="${a.id}" value="${fIni}" title="Fecha inicio" />
              </div>
              <div class="col-6">
                <input type="date" class="form-control form-control-sm hv-fecha-fin" data-asignatura-id="${a.id}" value="${fFin}" title="Fecha fin" />
              </div>
            </div>
        </div>
        `;
    }).join('');

    cont.querySelectorAll('.hv-maestro-select').forEach(sel => {
        sel.addEventListener('change', function () {
            const aid = this.dataset.asignaturaId;
            const opt = this.options[this.selectedIndex];
            asignaturaMaestroMap[aid] = {
                ...(asignaturaMaestroMap[aid] || {}),
                maestroId: this.value || null,
                maestroNombre: opt ? opt.text : ''
            };
        });
    });

    cont.querySelectorAll('.hv-fecha-inicio').forEach(inp => {
        inp.addEventListener('change', function () {
            const aid = this.dataset.asignaturaId;
            asignaturaMaestroMap[aid] = { ...(asignaturaMaestroMap[aid] || {}), fechaInicio: this.value || '' };
        });
    });
    cont.querySelectorAll('.hv-fecha-fin').forEach(inp => {
        inp.addEventListener('change', function () {
            const aid = this.dataset.asignaturaId;
            asignaturaMaestroMap[aid] = { ...(asignaturaMaestroMap[aid] || {}), fechaFin: this.value || '' };
        });
    });
}

function fechasSeSolapanFront(aIni, aFin, bIni, bFin) {
    const ai = aIni ? new Date(aIni + 'T00:00:00') : new Date(-8640000000000000);
    const af = aFin ? new Date(aFin + 'T00:00:00') : new Date(8640000000000000);
    const bi = bIni ? new Date(bIni + 'T00:00:00') : new Date(-8640000000000000);
    const bf = bFin ? new Date(bFin + 'T00:00:00') : new Date(8640000000000000);
    return !(ai > bf) && !(bi > af);
}

function hvDiaToJsDay(dia) {
    // JS: 0=Domingo ... 6=Sábado
    const d = String(dia || '').trim().toUpperCase();
    if (d === 'LUNES') return 1;
    if (d === 'MARTES') return 2;
    if (d === 'MIERCOLES' || d === 'MIÉRCOLES') return 3;
    if (d === 'JUEVES') return 4;
    if (d === 'VIERNES') return 5;
    if (d === 'SABADO' || d === 'SÁBADO') return 6;
    return null;
}

function hvParseYmdLocal(ymd) {
    if (!ymd) return null;
    const s = String(ymd).substring(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
    // Construcción explícita a medianoche local para evitar offsets raros.
    const parts = s.split('-').map(Number);
    return new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0, 0);
}

/**
 * Valida que el día seleccionado (LUNES..SABADO) exista al menos una vez dentro del rango [fechaInicio, fechaFin].
 * Ejemplo: 2026-04-27..2026-04-29 no contiene JUEVES ni VIERNES.
 */
function hvDiaEstaDentroDeRango(fechaInicio, fechaFin, dia) {
    const fi = hvParseYmdLocal(fechaInicio);
    const ff = hvParseYmdLocal(fechaFin);
    const target = hvDiaToJsDay(dia);
    if (!fi || !ff || target == null) return false;
    if (fi > ff) return false;

    const startDow = fi.getDay();
    const delta = (target - startDow + 7) % 7;
    const first = new Date(fi.getFullYear(), fi.getMonth(), fi.getDate(), 0, 0, 0, 0);
    first.setDate(first.getDate() + delta);
    return first <= ff;
}

function bloqueSigFront(b) {
    return String(b && b.cid != null ? b.cid : '');
}

function bloqueServidorIdDesdeCid(cid) {
    const s = String(cid || '');
    if (!s.startsWith('srv_')) return null;
    const n = parseInt(s.substring(4), 10);
    return Number.isFinite(n) ? n : null;
}

function estadoGetArr(key) {
    const v = horarioState[key];
    if (!v) return [];
    return Array.isArray(v) ? v : [v];
}

function estadoSetArr(key, arr) {
    if (!arr || arr.length === 0) {
        delete horarioState[key];
        return;
    }
    horarioState[key] = arr;
}

function validarCeldaParaBloque(key, bloque) {
    // key = "DIA_HH:mm"
    const dia = String(key || '').split('_')[0] || '';
    if (!hvDiaEstaDentroDeRango(bloque && bloque.fechaInicio, bloque && bloque.fechaFin, dia)) {
        return {
            ok: false,
            reason: 'No puedes colocar esta asignatura en ' + dia + ' porque su periodo (' +
                (bloque.fechaInicio || '—') + ' → ' + (bloque.fechaFin || '—') + ') no incluye ese día.'
        };
    }
    const arr = estadoGetArr(key);
    for (const ex of arr) {
        if (!ex) continue;
        if (fechasSeSolapanFront(bloque.fechaInicio, bloque.fechaFin, ex.fechaInicio, ex.fechaFin)) {
            return { ok: false, reason: 'La celda ya tiene una materia en un periodo que se traslapa.' };
        }
    }
    return { ok: true, reason: null };
}

function celdaPermiteBloque(key, bloque) {
    return validarCeldaParaBloque(key, bloque).ok === true;
}

function agregarBloqueACelda(key, bloque) {
    const arr = estadoGetArr(key);
    const b = { ...bloque };
    if (!b.cid) {
        const seed = (Date.now().toString(36) + Math.random().toString(36).slice(2, 8));
        b.cid = 'hv_' + seed;
    }
    arr.push(b);
    estadoSetArr(key, arr);
}

function volverDesdeVistaTablaAAsignarMaestros() {
    vistaTablaActiva = false;
    const vista = document.getElementById('hvVistaTabla');
    const resumen = document.getElementById('hvResumenContainer');
    const asignar = document.getElementById('hvAsignarMaestros');
    if (vista) vista.classList.add('d-none');
    if (resumen) {
        resumen.classList.remove('d-none');
        try { mostrarResumenSeleccion(); } catch (_) {}
    }
    if (asignar) asignar.classList.remove('d-none');
    // horarioState se conserva; docentes pueden no estar cargados si se entró sólo desde "Editar todo"
    (async function () {
        try {
            if (!maestros || maestros.length === 0) await cargarMaestros();
        } catch (_) {}
        try { renderizarAsignaturasMaestros(); } catch (_) {}
    })();
}

function mostrarVistaTabla() {
    // Validar que cada asignatura tenga periodo (inicio/fin) para habilitar horarios solapables por fechas
    const faltantes = (asignaturasPeriodo || []).filter(a => {
        const m = asignaturaMaestroMap[String(a.id)] || {};
        return !m.fechaInicio || !m.fechaFin;
    });
    if (faltantes.length > 0) {
        alert('Antes de crear el horario, asigna fecha inicio y fin a cada materia.');
        return;
    }
    const invalidas = (asignaturasPeriodo || []).filter(a => {
        const m = asignaturaMaestroMap[String(a.id)] || {};
        return m.fechaInicio && m.fechaFin && String(m.fechaInicio) > String(m.fechaFin);
    });
    if (invalidas.length > 0) {
        alert('Hay materias con fecha inicio mayor a fecha fin. Corrígelo antes de continuar.');
        return;
    }
    vistaTablaActiva = true;
    document.getElementById('hvAsignarMaestros').classList.add('d-none');
    document.getElementById('hvVistaTabla').classList.remove('d-none');
    document.getElementById('hvResumenContainer').classList.remove('d-none');
    mostrarResumenSeleccion();
    renderizarMateriasPendientes(asignaturasPeriodo);
    const tieneBloquesPrevios = !!(horarioState && Object.keys(horarioState).length > 0);
    // No vaciar estado si el usuario ya colocó bloques o viene de editar horario completo / volver desde tabla
    if (tieneBloquesPrevios) {
        renderizarCalendarioDesdeEstado();
    } else {
        renderizarCalendarioSemanal();
    }
    renderizarNotificaciones();
}

function mostrarFormularioInicial() {
    vistaTablaActiva = false;
    asignaturaMaestroMap = {};
    horarioState = {};
    document.getElementById('hvFormularioInicial').classList.remove('d-none');
    document.getElementById('hvAsignarMaestros').classList.add('d-none');
    document.getElementById('hvVistaTabla').classList.add('d-none');
    document.getElementById('hvResumenContainer').classList.add('d-none');
}

function volverAsignarMaestros() {
    document.getElementById('hvAsignarMaestros').classList.add('d-none');
    const resumen = document.getElementById('hvResumenContainer');
    const vista = document.getElementById('hvVistaTabla');
    if (resumen) resumen.classList.add('d-none');
    if (vista) vista.classList.add('d-none');
    vistaTablaActiva = false;
    document.getElementById('hvFormularioInicial').classList.remove('d-none');
}

function initHorariosVisual() {
    cargarProgramas();
    cargarListaHorarios();

    const selPrograma = document.getElementById('hvPrograma');
    const selGrupo = document.getElementById('hvGrupo');

    const selConsultaPrograma = document.getElementById('hvConsultaPrograma');
    const selConsultaPeriodoPlan = document.getElementById('hvConsultaPeriodoPlan');
    const selConsultaGrupo = document.getElementById('hvConsultaGrupo');
    const btnConsultar = document.getElementById('hvBtnConsultar');
    const btnVerTodos = document.getElementById('hvBtnVerTodos');
    if (selConsultaPrograma) {
        selConsultaPrograma.addEventListener('change', async function () {
            const pid = this.value;
            const selPlan = document.getElementById('hvConsultaPeriodoPlan');
            if (selPlan) {
                if (!pid) {
                    selPlan.innerHTML = '<option value="">Todos los niveles</option>';
                    selPlan.disabled = true;
                    selPlan.value = '';
                } else {
                    selPlan.disabled = false;
                }
            }
            await cargarPeriodosPlanEnMemoria(pid);
            llenarSelectPeriodoPlanConsulta();
            const nivel = selConsultaPeriodoPlan ? selConsultaPeriodoPlan.value : '';
            await cargarGruposConsulta(pid, nivel);
        });
    }
    if (selConsultaPeriodoPlan) {
        selConsultaPeriodoPlan.addEventListener('change', async function () {
            const pid = selConsultaPrograma ? selConsultaPrograma.value : '';
            await cargarGruposConsulta(pid, this.value);
            cargarListaHorarios();
        });
    }
    if (btnConsultar) btnConsultar.addEventListener('click', cargarListaHorarios);
    if (btnVerTodos) {
        btnVerTodos.addEventListener('click', function () {
            periodosPrograma = [];
            if (selConsultaPrograma) selConsultaPrograma.value = '';
            if (selConsultaPeriodoPlan) {
                selConsultaPeriodoPlan.innerHTML = '<option value="">Todos los niveles</option>';
                selConsultaPeriodoPlan.disabled = true;
                selConsultaPeriodoPlan.value = '';
            }
            if (selConsultaGrupo) selConsultaGrupo.value = '';
            cargarGruposConsulta('');
            cargarListaHorarios();
        });
    }

    if (selPrograma) {
        selPrograma.addEventListener('change', async function () {
            await cargarPeriodosPlanEnMemoria(this.value);
            cargarPeriodosAcademicos(this.value);
            await cargarGrupos(this.value);
        });
    }
    if (selGrupo) {
        selGrupo.addEventListener('change', async function () {
            const panel = document.getElementById('hvAsignarMaestros');
            if (panel && !panel.classList.contains('d-none')) {
                const g = getGrupoSeleccionado();
                await cargarAsignaturasPeriodo(g && g.numeroPeriodo != null ? g.numeroPeriodo : null);
                renderizarAsignaturasMaestros();
            }
        });
    }

    const btnAsignar = document.getElementById('hvBtnAsignarMaestros');
    const btnVolver = document.getElementById('hvBtnVolverAsignar');
    const btnCreate = document.getElementById('hvBtnCreate');
    const btnEdit = document.getElementById('hvBtnEdit');
    const btnEditTop = document.getElementById('hvBtnEditTop');
    const btnVolverPasoAnterior = document.getElementById('hvBtnVolverPasoAnterior');
    if (btnAsignar) btnAsignar.addEventListener('click', mostrarVistaAsignarMaestros);
    if (btnVolver) btnVolver.addEventListener('click', volverAsignarMaestros);
    if (btnCreate) btnCreate.addEventListener('click', mostrarVistaTabla);
    if (btnEdit) btnEdit.addEventListener('click', mostrarFormularioInicial);
    if (btnEditTop) btnEditTop.addEventListener('click', mostrarFormularioInicial);
    if (btnVolverPasoAnterior) btnVolverPasoAnterior.addEventListener('click', volverDesdeVistaTablaAAsignarMaestros);

    const btnGuardarAula = document.getElementById('hvBtnGuardarAula');
    if (btnGuardarAula) btnGuardarAula.addEventListener('click', guardarAula);

    const contPendientes = document.getElementById('hvMateriasPendientes');
    if (contPendientes) {
        contPendientes.addEventListener('click', function (ev) {
            const btn = ev.target && ev.target.closest ? ev.target.closest('.hv-asignar-docente') : null;
            if (!btn) return;
            ev.preventDefault();
            ev.stopPropagation();
            const aid = btn.dataset.asignaturaId;
            const an = btn.dataset.asignaturaNombre || '';
            abrirModalAsignarDocentePendiente(aid, an);
        });
    }

    const btnGuardarDocente = document.getElementById('hvBtnGuardarDocente');
    if (btnGuardarDocente) btnGuardarDocente.addEventListener('click', guardarDocentePendienteDesdeModal);

    const btnGuardarHorario = document.getElementById('hvBtnGuardarHorario');
    if (btnGuardarHorario) btnGuardarHorario.addEventListener('click', guardarHorarioVisual);

    const btnFullscreen = document.getElementById('hvBtnFullscreen');
    const cardHorario = document.getElementById('hvHorarioCard');
    if (btnFullscreen && cardHorario) {
        const reqFs = cardHorario.requestFullscreen || cardHorario.webkitRequestFullscreen;
        const exitFs = document.exitFullscreen || document.webkitExitFullscreen;
        const isFs = () => document.fullscreenElement || document.webkitFullscreenElement;
        btnFullscreen.addEventListener('click', function () {
            if (!isFs()) {
                if (reqFs) {
                    reqFs.call(cardHorario).then(function () {
                        btnFullscreen.innerHTML = '<i class="bi bi-fullscreen-exit"></i>';
                        btnFullscreen.title = 'Salir de pantalla completa';
                    }).catch(function (err) {
                        console.warn('Fullscreen no disponible:', err);
                    });
                }
            } else {
                if (exitFs) {
                    exitFs.call(document).then(function () {
                        btnFullscreen.innerHTML = '<i class="bi bi-fullscreen"></i>';
                        btnFullscreen.title = 'Pantalla completa';
                    });
                }
            }
        });
        document.addEventListener('fullscreenchange', function () {
            if (!isFs() && btnFullscreen) {
                btnFullscreen.innerHTML = '<i class="bi bi-fullscreen"></i>';
                btnFullscreen.title = 'Pantalla completa';
            }
        });
        document.addEventListener('webkitfullscreenchange', function () {
            if (!isFs() && btnFullscreen) {
                btnFullscreen.innerHTML = '<i class="bi bi-fullscreen"></i>';
                btnFullscreen.title = 'Pantalla completa';
            }
        });
    }
}

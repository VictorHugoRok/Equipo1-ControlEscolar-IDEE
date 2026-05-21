/**
 * certificados-electronicos.js
 * Generación y consulta de certificados electrónicos (DEC) - conectado al backend.
 * Rediseño: lista de alumnos con checkboxes para selección múltiple y generación masiva.
 */
(function() {
    'use strict';

    let listaAlumnosCert = [];
    let plantelesCert = [];
    let periodosIngresoMap = {}; // alumnoId -> periodo de ingreso (ej. "2025-1")
    // Certificados generados en esta sesión (para mostrar lista rápida en el formulario)
    let certificadosSesion = [];
    // Cache de la última tabla renderizada (para nombres de archivo en descargas)
    let certificadosCache = [];
    // Mapa id -> erroresXsd para mostrar en modal de certificado no válido
    let certErroresXsdMap = {};

    function getSection() {
        return document.getElementById('certificadosSection');
    }

    var _certificadosInitDone = false;
    function init() {
        if (!getSection()) return;
        if (_certificadosInitDone) return;
        _certificadosInitDone = true;
        const btnLimpiar = document.getElementById('certBtnLimpiar');
        const btnFiltrar = document.getElementById('certBtnFiltrar');
        const filtroAlumno = document.getElementById('certFiltroAlumno');
        const btnLimpiarTabla = document.getElementById('certBtnLimpiarTabla');
        const filtroLista = document.getElementById('certFiltroListaAlumnos');
        const checkSeleccionarTodos = document.getElementById('certSeleccionarTodos');

        if (filtroLista) {
            filtroLista.addEventListener('input', function() { filtrarYRenderizarListaAlumnos(); });
        }
        var filtroPrograma = document.getElementById('certFiltroPrograma');
        var filtroEstatus = document.getElementById('certFiltroEstatus');
        var filtroPeriodoIngreso = document.getElementById('certFiltroPeriodoIngreso');
        if (filtroPrograma) filtroPrograma.addEventListener('change', filtrarYRenderizarListaAlumnos);
        if (filtroEstatus) filtroEstatus.addEventListener('change', filtrarYRenderizarListaAlumnos);
        if (filtroPeriodoIngreso) filtroPeriodoIngreso.addEventListener('change', filtrarYRenderizarListaAlumnos);
        if (checkSeleccionarTodos) {
            checkSeleccionarTodos.addEventListener('change', function() {
                const checked = this.checked;
                document.querySelectorAll('#certListaAlumnos input[data-cert-alumno-id]').forEach(function(cb) {
                    cb.checked = checked;
                });
                actualizarContadorYFormCert();
            });
        }
        var btnGenerar = document.getElementById('certBtnGenerar');
        if (btnGenerar) {
            btnGenerar.addEventListener('mousedown', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                return false;
            }, true);
            btnGenerar.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                if (typeof activarAdminSection === 'function') activarAdminSection('certificadosSection');
                generarCertificado(e);
                return false;
            }, true);
        }
        document.addEventListener('keydown', function(e) {
            if (e.key !== 'Enter') return;
            var formCert = document.getElementById('formGenerarCertificado');
            if (!formCert || !formCert.contains(e.target)) return;
            if (e.target.id === 'certFiltroListaAlumnos') return;
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            if (document.getElementById('certFormCampos') && !document.getElementById('certFormCampos').classList.contains('d-none')) {
                generarCertificado(e);
            }
        }, true);
        if (btnLimpiar) {
            btnLimpiar.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                limpiarFormCert();
                if (typeof activarAdminSection === 'function') activarAdminSection('certificadosSection');
            });
        }
        if (btnFiltrar) {
            btnFiltrar.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                consultarCertificadosPorFiltro();
                if (typeof activarAdminSection === 'function') activarAdminSection('certificadosSection');
                return false;
            });
        }
        var btnVerTodos = document.getElementById('certBtnVerTodos');
        if (btnVerTodos) {
            btnVerTodos.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var input = document.getElementById('certFiltroAlumno');
                if (input) input.value = '';
                consultarTodosCertificados();
                if (typeof activarAdminSection === 'function') activarAdminSection('certificadosSection');
            });
        }
        if (filtroAlumno) filtroAlumno.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                consultarCertificadosPorFiltro();
            }
        });
        if (btnLimpiarTabla) {
            btnLimpiarTabla.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                limpiarTablaCertificados();
            });
        }

        configurarFechaActualCert();
        cargarPlantelesCert();
        cargarListaAlumnosCert();
        consultarTodosCertificados();
    }

    function actualizarContadorYFormCert() {
        const seleccionados = obtenerAlumnosSeleccionadosCert();
        const countEl = document.getElementById('certAlumnosSeleccionadosCount');
        const alertPrimero = document.getElementById('certAlertPrimeroBuscar');
        const formCampos = document.getElementById('certFormCampos');
        const checkTodos = document.getElementById('certSeleccionarTodos');
        const inputs = document.querySelectorAll('#certListaAlumnos input[data-cert-alumno-id]');
        const total = inputs.length;
        const checked = seleccionados.length;
        if (countEl) countEl.textContent = checked;
        if (alertPrimero) alertPrimero.classList.toggle('d-none', checked > 0);
        if (formCampos) formCampos.classList.toggle('d-none', checked === 0);
        if (checkTodos) {
            if (total === 0) {
                checkTodos.checked = false;
                checkTodos.indeterminate = false;
            } else {
                checkTodos.checked = checked === total;
                checkTodos.indeterminate = checked > 0 && checked < total;
            }
        }
    }

    function obtenerAlumnosSeleccionadosCert() {
        const result = [];
        document.querySelectorAll('#certListaAlumnos input[data-cert-alumno-id]:checked').forEach(function(cb) {
            const id = cb.getAttribute('data-cert-alumno-id');
            const programaId = cb.getAttribute('data-cert-programa-id');
            const programaClaveDgp = cb.getAttribute('data-cert-programa-clave-dgp') || '';
            const nombre = cb.getAttribute('data-cert-alumno-nombre') || '';
            const matricula = cb.getAttribute('data-cert-alumno-matricula') || '';
            if (id && programaId) result.push({ id: id, programaId: programaId, programaClaveDgp: programaClaveDgp, nombre: nombre, matricula: matricula });
        });
        return result;
    }

    async function cargarListaAlumnosCert() {
        const wrapper = document.getElementById('certListaAlumnosWrapper');
        const cargando = document.getElementById('certListaAlumnosCargando');
        const lista = document.getElementById('certListaAlumnos');
        const vacia = document.getElementById('certListaAlumnosVacia');
        if (!wrapper || !cargando || !lista) return;
        try {
            const [dataAlumnos, dataPeriodosIngreso, dataFiltros] = await Promise.all([
                authFetch('/alumnos/resumen-programas'),
                authFetch('/alumnos/periodos-ingreso').catch(function() { return {}; }),
                authFetch('/kardex/filtros').catch(function() { return { periodos: [] }; })
            ]);
            listaAlumnosCert = Array.isArray(dataAlumnos) ? dataAlumnos : [];
            // Nuevo contrato: filas aplanadas alumno+programa (programaId/programaNombre/...)
            listaAlumnosCert = listaAlumnosCert.filter(function(a) {
                return a && a.alumnoId && a.programaId;
            });
            periodosIngresoMap = dataPeriodosIngreso && typeof dataPeriodosIngreso === 'object' ? dataPeriodosIngreso : {};
            cargando.classList.add('d-none');
            poblarFiltrosCert(dataFiltros.periodos || []);
            if (listaAlumnosCert.length === 0) {
                vacia.classList.remove('d-none');
                lista.classList.add('d-none');
            } else {
                vacia.classList.add('d-none');
                lista.classList.remove('d-none');
                filtrarYRenderizarListaAlumnos();
            }
        } catch (err) {
            console.error('Error cargando alumnos:', err);
            cargando.classList.add('d-none');
            vacia.classList.remove('d-none');
            vacia.textContent = 'Error al cargar alumnos.';
            lista.classList.add('d-none');
        }
    }

    function poblarFiltrosCert(periodos) {
        var progSel = document.getElementById('certFiltroPrograma');
        var periodoSel = document.getElementById('certFiltroPeriodoIngreso');
        if (!progSel || !periodoSel) return;
        var programasMap = {};
        listaAlumnosCert.forEach(function(a) {
            if (a && a.programaId) {
                programasMap[String(a.programaId)] = a.programaNombre || a.programaClave || ('Programa ' + a.programaId);
            }
        });
        var progVal = progSel.value;
        progSel.innerHTML = '<option value="">Todos los programas</option>';
        Object.keys(programasMap).sort(function(x, y) { return (programasMap[x] || '').localeCompare(programasMap[y] || ''); }).forEach(function(id) {
            var opt = document.createElement('option');
            opt.value = id;
            opt.textContent = programasMap[id];
            progSel.appendChild(opt);
        });
        if (progVal) progSel.value = progVal;
        var periodoVal = periodoSel.value;
        periodoSel.innerHTML = '<option value="">Todos los periodos</option>';
        (periodos || []).forEach(function(p) {
            var nombre = (p && p.nombre) ? p.nombre : (typeof p === 'string' ? p : '');
            if (nombre) {
                var opt = document.createElement('option');
                opt.value = nombre;
                opt.textContent = nombre;
                periodoSel.appendChild(opt);
            }
        });
        if (periodoVal) periodoSel.value = periodoVal;
    }

    function filtrarYRenderizarListaAlumnos() {
        var criterio = (document.getElementById('certFiltroListaAlumnos') || {}).value.trim().toLowerCase();
        var programaId = (document.getElementById('certFiltroPrograma') || {}).value;
        var estatus = (document.getElementById('certFiltroEstatus') || {}).value;
        var periodoIngreso = (document.getElementById('certFiltroPeriodoIngreso') || {}).value.trim();
        const lista = document.getElementById('certListaAlumnos');
        if (!lista) return;
        var filtrados = listaAlumnosCert.filter(function(a) {
            if (programaId && String(a.programaId) !== programaId) return false;
            if (estatus && (a.estatusMatricula || '') !== estatus) return false;
            if (periodoIngreso && (periodosIngresoMap[String(a.alumnoId)] || '') !== periodoIngreso) return false;
            if (criterio) {
                var mat = (a.matricula || '').trim().toLowerCase();
                var curp = (a.curp || '').trim().toLowerCase();
                var nom = ((a.nombre || '') + ' ' + (a.apellidoPaterno || '') + ' ' + (a.apellidoMaterno || '')).toLowerCase();
                var c = criterio.trim();
                if (c.length === 18 && curp === c) return true;
                if (mat === c) return true;
                return nom.indexOf(c) !== -1 || mat.indexOf(c) !== -1 || curp.indexOf(c) !== -1;
            }
            return true;
        });
        if (filtrados.length === 0) {
            lista.innerHTML = '<div class="list-group-item border-0 text-muted small py-4 text-center kardex-lista-alumnos-vacia">Ningún alumno coincide con los filtros o la búsqueda.</div>';
        } else {
            filtrados = filtrados.slice().sort(function(a, b) {
                var na = (a.apellidoPaterno || '') + (a.apellidoMaterno || '') + (a.nombre || '');
                var nb = (b.apellidoPaterno || '') + (b.apellidoMaterno || '') + (b.nombre || '');
                return na.localeCompare(nb, 'es');
            });
            lista.innerHTML = filtrados.map(function(a) {
                var nombreCompleto = ((a.nombre || '') + ' ' + (a.apellidoPaterno || '') + ' ' + (a.apellidoMaterno || '')).trim();
                var programa = a.programaNombre || a.programaClave || '';
                var est = (a.estatusMatricula || '');
                var estBadge = est ? badgeEstatusCert(est) : '';
                var nombreTitulo = escapeHtmlNombreCert(nombreCompleto || '—');
                var progTitulo = escapeHtmlNombreCert(programa || '—');
                var matChip = escapeHtmlNombreCert((a.matricula || '').trim());
                return (
                    '<label class="list-group-item list-group-item-action kardex-alumno-item d-flex gap-2 align-items-start">' +
                    '<input class="form-check-input kardex-alumno-item-radio flex-shrink-0" type="checkbox" ' +
                    'data-cert-alumno-id="' + (a.alumnoId || '') + '" ' +
                    'data-cert-programa-id="' + (a.programaId || '') + '" ' +
                    'data-cert-programa-clave-dgp="' + escapeHtmlNombreCert((a.programaClaveDgp || '') || '') + '" ' +
                    'data-cert-alumno-nombre="' + escapeHtmlNombreCert(nombreCompleto) + '" ' +
                    'data-cert-alumno-matricula="' + escapeHtmlNombreCert(a.matricula || '') + '" ' +
                    'aria-label="Seleccionar ' + escapeHtmlNombreCert(nombreCompleto) + '">' +
                    '<div class="kardex-alumno-item-body flex-grow-1 min-w-0">' +
                    '<div class="kardex-alumno-nombre text-truncate" title="' + nombreTitulo + '">' + nombreTitulo + '</div>' +
                    '<div class="kardex-alumno-matricula-estado d-flex align-items-center gap-2 flex-wrap">' +
                    '<span class="kardex-alumno-matricula-chip"><i class="bi bi-person-vcard" aria-hidden="true"></i>' + matChip + '</span>' +
                    estBadge +
                    '</div>' +
                    '<div class="kardex-alumno-meta">' +
                    '<div class="kardex-alumno-meta-programa" title="' + progTitulo + '">' +
                    '<i class="bi bi-mortarboard kardex-alumno-meta-icon" aria-hidden="true"></i>' +
                    '<span class="kardex-alumno-prog-text">' + escapeHtmlNombreCert(programa) + '</span>' +
                    '</div>' +
                    '</div>' +
                    '</div>' +
                    '</label>'
                );
            }).join('');
            lista.querySelectorAll('input[data-cert-alumno-id]').forEach(function(cb) {
                cb.addEventListener('change', actualizarContadorYFormCert);
            });
        }
        actualizarContadorYFormCert();
    }

    async function cargarPlantelesCert() {
        try {
            plantelesCert = await authFetch('/planteles');
            if (!Array.isArray(plantelesCert)) plantelesCert = [];
        } catch (err) {
            console.error('Error cargando planteles:', err);
            plantelesCert = [];
        }
    }

    function obtenerPlantelIdPorClaveDgp(claveDgp) {
        var c = (claveDgp || '').trim().toUpperCase();
        if (!c) return null;
        var p = (plantelesCert || []).find(function(x) {
            return (x && x.claveDgp ? String(x.claveDgp).trim().toUpperCase() : '') === c;
        });
        return p && p.id != null ? parseInt(p.id, 10) : null;
    }

    function configurarFechaActualCert() {
        const input = document.getElementById('certFechaExpedicion');
        if (input) input.value = new Date().toISOString().split('T')[0];
    }

    async function generarCertificado(e) {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }
        const seleccionados = obtenerAlumnosSeleccionadosCert();
        const fechaExp = document.getElementById('certFechaExpedicion').value;
        const tipoId = document.getElementById('certTipoCertificacion').value;
        const observaciones = document.getElementById('certObservaciones').value.trim();

        if (!seleccionados.length) {
            mostrarFeedbackCert('Seleccione al menos un alumno de la lista.', 'warning');
            return;
        }
        if (!tipoId) {
            mostrarFeedbackCert('Seleccione el tipo de certificado (Parcial o Total).', 'warning');
            return;
        }
        if (!fechaExp) {
            mostrarFeedbackCert('Indique la fecha de expedición.', 'warning');
            return;
        }

        const tipoLabel = tipoId === '79' ? 'Total' : 'Parcial';
        const btn = document.getElementById('certBtnGenerar');
        const feedback = document.getElementById('certFeedback');
        const totalAlumnos = seleccionados.length;
        if (btn) btn.disabled = true;
        feedback.classList.add('d-none');

        // ===== Validación por porcentaje aprobado (regla de negocio) =====
        // Total (79): solo 100%
        // Parcial (80): solo <100%
        const omitidosPorRegla = [];
        const alumnosParaGenerar = [];
        const esTotal = tipoId === '79';
        try {
            const kardexList = await Promise.all(seleccionados.map(function(a) {
                return authFetch('/kardex/' + a.id).then(function(k) {
                    const pct = (k && k.porcentajeAprobado != null) ? Number(k.porcentajeAprobado) : null;
                    return { alumno: a, porcentajeAprobado: (Number.isFinite(pct) ? pct : null) };
                }).catch(function() {
                    // Si no se puede obtener el kardex, no bloqueamos aquí; el backend validará.
                    return { alumno: a, porcentajeAprobado: null };
                });
            }));
            kardexList.forEach(function(r) {
                const a = r.alumno;
                const pct = r.porcentajeAprobado;
                if (pct == null) {
                    alumnosParaGenerar.push(a);
                    return;
                }
                const alumnoNombre = a.nombre || a.matricula || ('ID ' + a.id);
                if (esTotal) {
                    if (pct < 100) {
                        omitidosPorRegla.push({ alumno: alumnoNombre, error: 'Certificado total requiere 100% aprobado. Actual: ' + pct + '%.' });
                        return;
                    }
                } else {
                    if (pct >= 100) {
                        omitidosPorRegla.push({ alumno: alumnoNombre, error: 'Certificado parcial requiere menos del 100% aprobado. Actual: ' + pct + '%.' });
                        return;
                    }
                }
                alumnosParaGenerar.push(a);
            });
        } catch (e2) {
            // En caso extremo, seguir con todos y dejar que backend aplique validación
            alumnosParaGenerar.push.apply(alumnosParaGenerar, seleccionados);
        }

        if (alumnosParaGenerar.length === 0) {
            mostrarModalResumenGeneracionCertificados(0, omitidosPorRegla);
            if (btn) btn.disabled = false;
            return;
        }
        // ========== BATCH: un solo disparo (máximo 50) ==========
        if (alumnosParaGenerar.length > 50) {
            mostrarFeedbackCert('El máximo permitido por generación es 50 certificados. Seleccionaste ' + alumnosParaGenerar.length + '.', 'warning');
            if (btn) btn.disabled = false;
            return;
        }

        // Resolver plantelId por alumno. Si falta, se omite ANTES del batch (para devolver error claro y no romper todo).
        var items = [];
        var omitidosPorPlantel = [];
        alumnosParaGenerar.forEach(function (a) {
            var plantelIdAuto = obtenerPlantelIdPorClaveDgp(a.programaClaveDgp);
            if (!plantelIdAuto) {
                omitidosPorPlantel.push({
                    alumno: a.nombre || a.matricula || ('ID ' + a.id),
                    error: 'No se pudo determinar el plantel del programa (Clave DGP: ' + (a.programaClaveDgp || '—') + '). Verifique el plantel registrado y su Clave DGP.'
                });
                return;
            }
            items.push({
                alumnoId: parseInt(a.id, 10),
                programaId: parseInt(a.programaId, 10),
                plantelId: plantelIdAuto
            });
        });

        if (items.length === 0) {
            var omitidosSolo = (omitidosPorRegla || []).concat(omitidosPorPlantel || []);
            mostrarFeedbackCert('No hay alumnos válidos para generar certificados.', 'warning');
            mostrarModalResumenGeneracionCertificados(0, omitidosSolo);
            if (btn) btn.disabled = false;
            return;
        }

        // Un solo mensaje de estado para evitar "recargas" visuales múltiples.
        mostrarFeedbackCert('Generando certificados... (' + items.length + ' alumno(s))', 'info');

        try {
            var batchReq = {
                fechaExpedicion: fechaExp,
                idTipoCertificacion: tipoId,
                tipoCertificacion: tipoLabel,
                periodo: null,
                cicloEscolar: null,
                observaciones: observaciones || null,
                items: items
            };

            var batchRes = await authFetch('/certificados-electronicos/batch', {
                method: 'POST',
                body: JSON.stringify(batchReq)
            });

            var creados = (batchRes && batchRes.certificadosCreados && Array.isArray(batchRes.certificadosCreados))
                ? batchRes.certificadosCreados
                : [];

            creados.forEach(function (c) { agregarCertificadoAListaSesion(c); });

            var omitidos = [];
            omitidos = omitidos.concat(omitidosPorRegla || []);
            omitidos = omitidos.concat(omitidosPorPlantel || []);
            if (batchRes && Array.isArray(batchRes.errores) && batchRes.errores.length > 0) {
                // Mapear al formato del modal {alumno, error}
                batchRes.errores.forEach(function (er) {
                    omitidos.push({
                        alumno: (er && er.alumnoId != null) ? ('Alumno ID ' + er.alumnoId) : 'Alumno',
                        error: (er && er.mensaje) ? er.mensaje : 'Error al generar'
                    });
                });
            }

            var creadosCount = (batchRes && typeof batchRes.creados === 'number') ? batchRes.creados : creados.length;
            if (creadosCount > 0) {
                var msg = creadosCount === 1
                    ? 'Certificado generado correctamente. Folio: ' + (creados[0] && creados[0].folioControl ? creados[0].folioControl : '')
                    : creadosCount + ' certificados generados correctamente.';
                if (omitidos.length > 0) msg += ' Se omitieron ' + omitidos.length + '.';
                mostrarFeedbackCert(msg, omitidos.length > 0 ? 'warning' : 'success');
            } else {
                mostrarFeedbackCert('No se pudo generar ningún certificado.', 'danger');
            }

            // Evitar refrescos visuales extra: no recargar automáticamente la tabla aquí.
            mostrarModalResumenGeneracionCertificados(creadosCount, omitidos);
        } catch (errBatch) {
            var msgErr = (errBatch && errBatch.message) ? errBatch.message : 'Error al generar certificados.';
            mostrarFeedbackCert(msgErr, 'danger');
            var omitidosErr = (omitidosPorRegla || []).concat(omitidosPorPlantel || []);
            mostrarModalResumenGeneracionCertificados(0, omitidosErr.length ? omitidosErr : [{ alumno: 'Batch', error: msgErr }]);
        } finally {
            if (btn) btn.disabled = false;
        }

        return;
    }

    function mostrarModalResumenGeneracionCertificados(creados, omitidos) {
        const elCount = document.getElementById('resumenCertificadosCreados');
        const wrapper = document.getElementById('resumenCertificadosOmitidosWrapper');
        const list = document.getElementById('resumenCertificadosOmitidosList');
        if (elCount) elCount.textContent = String(creados || 0);
        const arr = Array.isArray(omitidos) ? omitidos : [];
        if (wrapper) wrapper.classList.toggle('d-none', arr.length === 0);
        if (list) {
            list.innerHTML = arr.map(function(x) {
                const a = x && x.alumno ? String(x.alumno) : 'Alumno';
                const r = x && x.error ? String(x.error) : 'No cumple requisitos.';
                return '<li class="list-group-item d-flex flex-column gap-1">' +
                    '<div class="fw-semibold">' + escapeHtmlPlantel(a) + '</div>' +
                    '<div class="text-muted">' + escapeHtmlPlantel(r) + '</div>' +
                '</li>';
            }).join('');
        }
        const modalEl = document.getElementById('modalResumenGeneracionCertificados');
        if (modalEl && typeof bootstrap !== 'undefined') {
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
        }
    }

    /** Mantiene la sección Certificados visible y hace scroll al mensaje (evita volver al inicio del dashboard). */
    function mantenerEnCertificadosYMostrarResultado() {
        function aplicar() {
            if (typeof activarAdminSection === 'function') {
                activarAdminSection('certificadosSection');
            }
            var section = document.getElementById('certificadosSection');
            var feedback = document.getElementById('certFeedback');
            if (section) {
                section.scrollIntoView({ behavior: 'instant', block: 'start' });
            }
            if (feedback && !feedback.classList.contains('d-none')) {
                feedback.scrollIntoView({ behavior: 'instant', block: 'nearest' });
                try { feedback.focus(); } catch (e) { }
            }
        }
        aplicar();
        if (typeof requestAnimationFrame !== 'undefined') {
            requestAnimationFrame(aplicar);
        }
    }

    function mostrarFeedbackCert(mensaje, tipo) {
        const el = document.getElementById('certFeedback');
        if (!el) return;
        el.textContent = mensaje;
        el.className = 'alert alert-' + tipo + ' mt-3';
        el.classList.remove('d-none');
    }

    /**
     * Agrega un certificado recién generado a la lista visual en el formulario,
     * mostrando el nombre de archivo como CERT-XXXX...XML y un botón de descarga directa.
     */
    function agregarCertificadoAListaSesion(data) {
        if (!data || !data.id) return;
        const folio = (data.folioControl || '').toString().trim();
        const nombreArchivo = (folio ? folio : ('CERT-' + data.id)) + '.XML';

        certificadosSesion.push({
            id: data.id,
            folio: folio,
            nombreArchivo: nombreArchivo,
            validoXsd: data.validoXsd !== false
        });

        const wrapper = document.getElementById('certArchivosGeneradosWrapper');
        const lista = document.getElementById('certArchivosGeneradosList');
        if (!wrapper || !lista) return;

        wrapper.classList.remove('d-none');

        // Renderización simple: mostrar todos los certificados de la sesión
        lista.innerHTML = certificadosSesion.map(function(c) {
            var validoXsd = c.validoXsd !== false;
            var btns = validoXsd
                ? '<button type="button" class="btn btn-outline-success" data-cert-id="' + c.id + '" data-action="vista-previa" title="Vista previa PDF"><i class="bi bi-eye"></i> Vista previa</button>' +
                  '<button type="button" class="btn btn-outline-success" data-cert-id="' + c.id + '" data-action="descargar-pdf" title="Descargar PDF"><i class="bi bi-file-pdf"></i> PDF</button>' +
                  '<button type="button" class="btn btn-outline-primary" data-cert-id="' + c.id + '" data-action="descargar" title="Descargar XML"><i class="bi bi-download"></i> XML</button>'
                : '<span class="badge bg-warning text-dark"><i class="bi bi-exclamation-triangle"></i> No pasó XSD</span>';
            return '<li class="list-group-item d-flex justify-content-between align-items-center">' +
                '<span><i class="bi bi-file-earmark-code me-1"></i>' + escapeHtmlNombreCert(c.nombreArchivo) + '</span>' +
                '<span class="btn-group btn-group-sm">' + btns + '</span></li>';
        }).join('');

        lista.querySelectorAll('button[data-cert-id]').forEach(function(btn) {
            btn.addEventListener('click', function(ev) {
                ev.preventDefault();
                ev.stopPropagation();
                var id = btn.getAttribute('data-cert-id');
                var action = btn.getAttribute('data-action');
                if (id && action === 'vista-previa') abrirVistaPreviaCert(id);
                else if (id && action === 'descargar-pdf') descargarPdfCert(id);
                else if (id && action === 'descargar') descargarXmlCert(id);
            });
        });
    }

    function escapeHtmlNombreCert(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** Mismos colores/etiquetas que kardex.js (badgeEstatus). */
    function badgeEstatusCert(estatus) {
        var e = (estatus || '').toUpperCase();
        var cls = e === 'ACTIVA' ? 'bg-success-subtle text-success'
            : e === 'BAJA_TEMPORAL' ? 'bg-warning-subtle text-warning'
                : e === 'BAJA_DEFINITIVA' ? 'bg-danger-subtle text-danger'
                    : e === 'EGRESADO' ? 'bg-info-subtle text-info'
                        : 'bg-secondary-subtle text-secondary';
        var label = e === 'ACTIVA' ? 'Activa'
            : e === 'BAJA_TEMPORAL' ? 'Baja temporal'
                : e === 'BAJA_DEFINITIVA' ? 'Baja definitiva'
                    : e === 'EGRESADO' ? 'Egresado'
                        : (estatus || '—');
        return '<span class="badge ' + cls + '">' + escapeHtmlNombreCert(label) + '</span>';
    }

    function resetFormCert() {
        var f = document.getElementById('certFechaExpedicion');
        var t = document.getElementById('certTipoCertificacion');
        var o = document.getElementById('certObservaciones');
        if (f) f.value = '';
        if (t) t.value = '';
        if (o) o.value = '';
    }

    function limpiarFormCert() {
        resetFormCert();
        configurarFechaActualCert();
        var fb = document.getElementById('certFeedback');
        if (fb) fb.classList.add('d-none');
    }

    async function consultarCertificadosPorFiltro() {
        const input = document.getElementById('certFiltroAlumno');
        if (!input || !input.value.trim()) {
            // Si no hay filtro, mostrar todos
            await consultarTodosCertificados();
            return;
        }
        const criterio = input.value.trim();
        try {
            let url = `${API_URL}/alumnos/matricula/${encodeURIComponent(criterio)}`;
            let res = await fetch(url, { headers: { 'Authorization': 'Bearer ' + (getToken() || ''), 'Content-Type': 'application/json' } });
            if (!res.ok && criterio.length === 18) {
                url = `${API_URL}/alumnos/curp/${encodeURIComponent(criterio)}`;
                res = await fetch(url, { headers: { 'Authorization': 'Bearer ' + (getToken() || ''), 'Content-Type': 'application/json' } });
            }
            if (res.ok) {
                const alumno = await res.json();
                await consultarCertificadosAlumno(alumno.id);
            } else {
                document.getElementById('certificadosTableBody').innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No se encontró el alumno.</td></tr>';
            }
        } catch (err) {
            document.getElementById('certificadosTableBody').innerHTML = '<tr><td colspan="7" class="text-center text-danger py-4">Error al buscar alumno.</td></tr>';
        }
    }

    async function consultarTodosCertificados() {
        const tbody = document.getElementById('certificadosTableBody');
        if (!tbody) return;
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm"></span> Cargando certificados...</td></tr>';
        try {
            const lista = await authFetch('/certificados-electronicos');
            if (!lista || lista.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No hay certificados generados.</td></tr>';
                return;
            }
            renderizarTablaCertificados(lista);
        } catch (err) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-4">Error al cargar certificados.</td></tr>';
        }
    }

    async function consultarCertificadosAlumno(alumnoId) {
        const tbody = document.getElementById('certificadosTableBody');
        if (!tbody) return;
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm"></span> Cargando...</td></tr>';
        try {
            const lista = await authFetch('/certificados-electronicos/alumno/' + alumnoId);
            if (!lista || lista.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No hay certificados para este alumno.</td></tr>';
                return;
            }
            renderizarTablaCertificados(lista);
        } catch (err) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-4">Error al cargar certificados.</td></tr>';
        }
    }

    async function limpiarTablaCertificados() {
        const tbody = document.getElementById('certificadosTableBody');
        if (!tbody) return;
        if (!confirm('Esta acción eliminará TODOS los certificados electrónicos de prueba.\n\n¿Deseas continuar?')) {
            return;
        }
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm"></span> Eliminando certificados...</td></tr>';
        try {
            await authFetch('/certificados-electronicos', {
                method: 'DELETE'
            });
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No hay certificados generados.</td></tr>';
            certificadosSesion = [];
            const wrapper = document.getElementById('certArchivosGeneradosWrapper');
            const lista = document.getElementSubtree ? document.getElementById('certArchivosGeneradosList') : document.getElementById('certArchivosGeneradosList');
            if (wrapper) wrapper.classList.add('d-none');
            if (lista) lista.innerHTML = '';
        } catch (err) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-4">Error al eliminar certificados: ' + (err.message || '') + '</td></tr>';
        }
    }

    function mostrarModalXsdInvalido(erroresXsd) {
        var modal = document.getElementById('modalCertificadoXsdInvalido');
        var wrapper = document.getElementById('modalXsdErroresWrapper');
        var lista = document.getElementById('modalXsdErroresLista');
        if (modal && typeof bootstrap !== 'undefined') {
            if (wrapper && lista) {
                if (erroresXsd && Array.isArray(erroresXsd) && erroresXsd.length > 0) {
                    lista.innerHTML = erroresXsd.map(function(e) {
                        return '<li class="list-group-item list-group-item-danger py-2">' + (e || '').replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</li>';
                    }).join('');
                    wrapper.classList.remove('d-none');
                } else {
                    wrapper.classList.add('d-none');
                    lista.innerHTML = '';
                }
            }
            var bsModal = bootstrap.Modal.getOrCreateInstance(modal);
            bsModal.show();
        }
    }

    function renderizarTablaCertificados(lista) {
        const tbody = document.getElementById('certificadosTableBody');
        if (!tbody) return;
        certErroresXsdMap = {};
        certificadosCache = Array.isArray(lista) ? lista : [];
        tbody.innerHTML = lista.map(function(c) {
            const estatusClass = c.estatus === 'FIRMADO' || c.estatus === 'ENTREGADO' ? 'bg-success' : 'bg-secondary';
            const fecha = c.fechaExpedicion ? c.fechaExpedicion : '';
            const validoXsd = c.validoXsd !== false;
            let accionesHtml;
            if (validoXsd) {
                accionesHtml = '<a href="#" class="btn btn-sm btn-outline-success me-1 vista-previa-cert" data-id="' + c.id + '" title="Vista previa PDF"><i class="bi bi-eye"></i></a>' +
                    '<a href="#" class="btn btn-sm btn-outline-success me-1 descargar-cert-pdf" data-id="' + c.id + '" title="Descargar PDF"><i class="bi bi-file-pdf"></i></a>' +
                    '<a href="#" class="btn btn-sm btn-outline-secondary me-1 descargar-cert-cadena" data-id="' + c.id + '" title="Descargar cadena original (pruebas)"><i class="bi bi-map"></i></a>' +
                    '<a href="#" class="btn btn-sm btn-outline-primary descargar-cert-xml" data-id="' + c.id + '" title="Descargar XML"><i class="bi bi-download"></i></a>';
            } else {
                certErroresXsdMap[c.id] = c.erroresXsd || [];
                accionesHtml = '<span class="badge bg-warning text-dark me-1" title="No pasó validación XSD"><i class="bi bi-exclamation-triangle"></i> No XSD</span>' +
                    '<button type="button" class="btn btn-sm btn-outline-warning btn-xsd-warning" data-id="' + c.id + '" title="Ver detalle"><i class="bi bi-info-circle"></i></button>';
            }
            return '<tr>' +
                '<td><code>' + (c.folioControl || '') + '</code></td>' +
                '<td>' + (c.alumnoNombreCompleto || '') + '</td>' +
                '<td>' + (c.programaNombre || '') + '</td>' +
                '<td>' + (c.tipoCertificacion || '') + '</td>' +
                '<td>' + fecha + '</td>' +
                '<td><span class="badge ' + estatusClass + '">' + (c.estatus || '') + '</span>' + (!validoXsd ? ' <span class="badge bg-danger ms-1">No XSD</span>' : '') + '</td>' +
                '<td>' + accionesHtml + '</td>' +
                '</tr>';
        }).join('');
        tbody.querySelectorAll('.descargar-cert-xml').forEach(function(a) {
            a.addEventListener('click', function(ev) {
                ev.preventDefault();
                descargarXmlCert(a.getAttribute('data-id'));
            });
        });
        tbody.querySelectorAll('.descargar-cert-pdf').forEach(function(a) {
            a.addEventListener('click', function(ev) {
                ev.preventDefault();
                descargarPdfCert(a.getAttribute('data-id'));
            });
        });
        tbody.querySelectorAll('.descargar-cert-cadena').forEach(function(a) {
            a.addEventListener('click', function(ev) {
                ev.preventDefault();
                descargarCadenaOriginalCert(a.getAttribute('data-id'));
            });
        });
        tbody.querySelectorAll('.vista-previa-cert').forEach(function(a) {
            a.addEventListener('click', function(ev) {
                ev.preventDefault();
                abrirVistaPreviaCert(a.getAttribute('data-id'));
            });
        });
        tbody.querySelectorAll('.btn-xsd-warning').forEach(function(btn) {
            btn.addEventListener('click', function(ev) {
                ev.preventDefault();
                var id = btn.getAttribute('data-id');
                var errores = (certErroresXsdMap && certErroresXsdMap[id]) ? certErroresXsdMap[id] : [];
                mostrarModalXsdInvalido(errores);
            });
        });
    }

    function getApiBase() {
        return (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
    }

    async function abrirVistaPreviaCert(id) {
        var token = typeof getToken === 'function' ? getToken() : localStorage.getItem('token');
        if (!token) {
            alert('Sesión expirada. Vuelva a iniciar sesión.');
            return;
        }
        try {
            var base = getApiBase();
            var res = await fetch(base + '/certificados-electronicos/' + id + '/vista-previa', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) {
                if (res.status === 400) {
                    var errData = await res.json().catch(function() { return {}; });
                    alert(errData.error || 'Este certificado no pasó la validación XSD.');
                    return;
                }
                throw new Error('No se pudo cargar la vista previa (HTTP ' + res.status + ')');
            }
            var pdfBlob = await res.blob();
            if (!pdfBlob || pdfBlob.size === 0) {
                alert('No se pudo cargar la vista previa.');
                return;
            }
            var url = URL.createObjectURL(pdfBlob);
            window.open(url, '_blank', 'noopener,noreferrer,width=900,height=700');
            setTimeout(function() { URL.revokeObjectURL(url); }, 10000);
        } catch (err) {
            alert('Error al abrir vista previa: ' + (err.message || ''));
        }
    }

    function descargarPdfCert(id) {
        var token = typeof getToken === 'function' ? getToken() : localStorage.getItem('token');
        if (!token) {
            alert('Sesión expirada. Vuelva a iniciar sesión.');
            return;
        }
        var base = getApiBase();
        var url = base + '/certificados-electronicos/' + id + '/descargar-pdf';
        fetch(url, { headers: { 'Authorization': 'Bearer ' + token } })
            .then(function(r) {
                if (!r.ok) {
                    if (r.status === 400) {
                        return r.json().then(function(data) {
                            throw new Error(data.error || 'Este certificado no pasó la validación XSD.');
                        });
                    }
                    throw new Error('No se pudo descargar el PDF (HTTP ' + r.status + ')');
                }
                return r.blob();
            })
            .then(function(blob) {
                var u = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = u;
                try {
                    var row = (certificadosCache || []).find(function (x) { return String(x.id) === String(id); });
                    var folio = row && row.folioControl ? String(row.folioControl).trim() : ('certificado_' + id);
                    var full = row && row.alumnoNombreCompleto ? String(row.alumnoNombreCompleto).trim() : '';
                    var parts = full ? full.split(/\s+/g) : [];
                    var ap = parts.length >= 2 ? parts[parts.length - 2] : '';
                    var am = parts.length >= 1 ? parts[parts.length - 1] : '';
                    var fn = (folio + '_' + ap + '_' + am).replace(/[\\\/:*?"<>|]+/g, '').trim();
                    a.download = (fn ? fn : ('certificado_' + id)) + '.pdf';
                } catch (_) {
                    a.download = 'certificado_' + id + '.pdf';
                }
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(u);
            })
            .catch(function(e) {
                alert('Error al descargar el PDF: ' + (e.message || ''));
            });
    }

    function descargarCadenaOriginalCert(id) {
        var token = typeof getToken === 'function' ? getToken() : localStorage.getItem('token');
        if (!token) {
            alert('Sesión expirada. Vuelva a iniciar sesión.');
            return;
        }
        var base = getApiBase();
        var url = base + '/certificados-electronicos/' + id + '/cadena-original';
        fetch(url, { headers: { 'Authorization': 'Bearer ' + token } })
            .then(function(r) {
                if (!r.ok) throw new Error('No se pudo descargar (HTTP ' + r.status + ')');
                return r.blob();
            })
            .then(function(blob) {
                var u = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = u;
                try {
                    var row = (certificadosCache || []).find(function (x) { return String(x.id) === String(id); });
                    var folio = row && row.folioControl ? String(row.folioControl).trim() : ('cadena_original_' + id);
                    var full = row && row.alumnoNombreCompleto ? String(row.alumnoNombreCompleto).trim() : '';
                    var parts = full ? full.split(/\s+/g) : [];
                    var ap = parts.length >= 2 ? parts[parts.length - 2] : '';
                    var am = parts.length >= 1 ? parts[parts.length - 1] : '';
                    var fn = ('cadena_original_' + folio + '_' + ap + '_' + am).replace(/[\\\/:*?"<>|]+/g, '').trim();
                    a.download = (fn ? fn : ('cadena_original_' + id)) + '.xml';
                } catch (_) {
                    a.download = 'cadena_original_' + id + '.xml';
                }
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(u);
            })
            .catch(function(e) {
                alert('Error al descargar cadena original: ' + (e.message || ''));
            });
    }

    function descargarXmlCert(id) {
        var token = typeof getToken === 'function' ? getToken() : localStorage.getItem('token');
        if (!token) {
            alert('Sesión expirada. Vuelva a iniciar sesión.');
            return;
        }
        var base = getApiBase();
        var url = base + '/certificados-electronicos/' + id + '/descargar-xml';
        fetch(url, { headers: { 'Authorization': 'Bearer ' + token } })
            .then(function(r) {
                if (!r.ok) {
                    if (r.status === 400) {
                        return r.json().then(function(data) {
                            throw new Error(data.error || 'Este certificado no pasó la validación XSD.');
                        });
                    }
                    throw new Error('No se pudo descargar (HTTP ' + r.status + ')');
                }
                return r.blob();
            })
            .then(function(blob) {
                var u = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = u;
                try {
                    var row = (certificadosCache || []).find(function (x) { return String(x.id) === String(id); });
                    var folio = row && row.folioControl ? String(row.folioControl).trim() : ('certificado_' + id);
                    var full = row && row.alumnoNombreCompleto ? String(row.alumnoNombreCompleto).trim() : '';
                    var parts = full ? full.split(/\s+/g) : [];
                    var ap = parts.length >= 2 ? parts[parts.length - 2] : '';
                    var am = parts.length >= 1 ? parts[parts.length - 1] : '';
                    var fn = (folio + '_' + ap + '_' + am).replace(/[\\\/:*?"<>|]+/g, '').trim();
                    a.download = (fn ? fn : ('certificado_' + id)) + '.xml';
                } catch (_) {
                    a.download = 'certificado_' + id + '.xml';
                }
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(u);
            })
            .catch(function(e) {
                alert('Error al descargar el XML: ' + (e.message || ''));
            });
    }

    document.addEventListener('DOMContentLoaded', init);
})();

/**
 * titulos-electronicos.js
 * Gestión de generación y consulta de títulos profesionales electrónicos
 */
console.log("✅ titulos-electronicos.js CARGADO - versión debug");


let alumnoSeleccionado = null;
let titulosCache = [];
let busquedaAlumnoController = null;

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function() {
    // Solo inicializar si estamos en la página correcta
    if (document.getElementById('titulosElectronicosSection')) {
        inicializarEventos();
        configurarFechaActual();
    }
});

function inicializarEventos() {
  console.log("✅ inicializarEventos() ejecutado");

  const btnBuscarAlumno = document.getElementById('btnBuscarAlumno');
  console.log("btnBuscarAlumno:", btnBuscarAlumno);

  if (btnBuscarAlumno) {
    btnBuscarAlumno.addEventListener('click', () => {
      console.log("✅ click buscar alumno");
      buscarAlumno();
    });
  }

    const buscarAlumnoInput = document.getElementById('buscarAlumno');
    if (buscarAlumnoInput) {
        buscarAlumnoInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                buscarAlumno();
            }
        });
    }

    // Formulario de generación
    const formGenerarTitulo = document.getElementById('formGenerarTitulo');
    if (formGenerarTitulo) {
        formGenerarTitulo.addEventListener('submit', generarTitulo);
    }

    const btnLimpiarForm = document.getElementById('btnLimpiarForm');
    if (btnLimpiarForm) {
        btnLimpiarForm.addEventListener('click', limpiarFormulario);
    }

    // Consulta de títulos
    const btnBuscarTitulo = document.getElementById('btnBuscarTitulo');
    if (btnBuscarTitulo) {
        btnBuscarTitulo.addEventListener('click', buscarTitulos);
    }

    const filtroEstatus = document.getElementById('filtroEstatus');
    if (filtroEstatus) {
        filtroEstatus.addEventListener('change', buscarTitulos);
    }

    // Modal de cambio de estatus
    const btnConfirmarCambioEstatus = document.getElementById('btnConfirmarCambioEstatus');
    if (btnConfirmarCambioEstatus) {
        btnConfirmarCambioEstatus.addEventListener('click', confirmarCambioEstatus);
    }

    // Delegación de eventos en tabla de títulos (evita onclick inline en filas dinámicas)
    const tablaTitulos = document.getElementById('tablaTitulos');
    if (tablaTitulos) {
        tablaTitulos.addEventListener('click', (event) => {
            const btn = event.target.closest('button[data-action="cambiar-estatus"]');
            if (!btn) return;
            const fila = btn.closest('tr[data-titulo-id]');
            if (!fila) return;
            const tituloId = parseInt(fila.dataset.tituloId, 10);
            const titulo = titulosCache.find(t => t.id === tituloId);
            if (titulo) abrirModalCambioEstatus(titulo.id, titulo.estatus);
        });
    }
}

function configurarFechaActual() {
    const fechaExpedicion = document.getElementById('fechaExpedicion');
    if (fechaExpedicion) {
        const hoy = new Date().toISOString().split('T')[0];
        fechaExpedicion.value = hoy;
    }
}

// ==================== BÚSQUEDA DE ALUMNO ====================

async function buscarAlumno() {
    const input = document.getElementById('buscarAlumno');
    if (!input) return;

    const criterio = input.value.trim();
    if (!criterio) {
        showError('Por favor ingrese una matrícula o CURP');
        return;
    }

    // Cancelar búsqueda anterior si sigue en curso
    if (busquedaAlumnoController) busquedaAlumnoController.abort();
    busquedaAlumnoController = new AbortController();
    const { signal } = busquedaAlumnoController;

    const btnBuscar = document.getElementById('btnBuscarAlumno');
    if (btnBuscar) btnBuscar.disabled = true;

    try {
        const headers = getHeaders();
        let url = `${API_URL}/alumnos/matricula/${encodeURIComponent(criterio)}`;
        let response = await fetch(url, { headers, signal });

        // Si no se encuentra por matrícula, intentar por CURP (18 chars)
        if (!response.ok && criterio.length === 18) {
            url = `${API_URL}/alumnos/curp/${encodeURIComponent(criterio)}`;
            response = await fetch(url, { headers, signal });
        }

        if (response.ok) {
            const data = await response.json();
            alumnoSeleccionado = data;
            mostrarDatosAlumno(alumnoSeleccionado);

            if (!alumnoSeleccionado.id) {
                console.warn('El JSON del alumno no trae id. Revisa el DTO del backend.');
                return;
            }

            await validarRequisitosAlumno(alumnoSeleccionado.id);
        } else {
            mostrarAlumnoNoEncontrado();
        }
    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('Error en buscarAlumno():', error);
        mostrarAlumnoNoEncontrado();
    } finally {
        if (btnBuscar) btnBuscar.disabled = false;
        busquedaAlumnoController = null;
    }
}


function mostrarDatosAlumno(alumno) {
  console.log("Alumno recibido:", alumno);

  document.getElementById('alumnoNoEncontrado').classList.add('d-none');
  document.getElementById('alumnoInfo').classList.remove('d-none');

  // Ya lo trae el backend
  document.getElementById('alumnoNombre').textContent = alumno.nombreCompleto || '';
  document.getElementById('alumnoMatricula').textContent = alumno.matricula || '';
  document.getElementById('alumnoCurp').textContent = alumno.curp || 'No registrado';

  const correo = alumno.correoInstitucional || alumno.correoPersonal || 'No registrado';
  document.getElementById('alumnoEmail').textContent = correo;

  // Estatus real que trae tu JSON
  const estatus = alumno.estatusMatricula || '';
  const estatusBadge = document.getElementById('alumnoEstatus');
  estatusBadge.textContent = estatus;
  estatusBadge.className = `badge ${estatus === 'EGRESADO' ? 'bg-success' : 'bg-warning'}`;

  // Hidden IDs necesarios para el POST
  document.getElementById('alumnoIdTitulo').value = alumno.id;
  document.getElementById('programaId').value = alumno.programa?.id || '';
}



function mostrarAlumnoNoEncontrado() {
    document.getElementById('alumnoInfo').classList.add('d-none');
    document.getElementById('alumnoNoEncontrado').classList.remove('d-none');
    document.getElementById('btnGenerarTitulo').disabled = true;
    alumnoSeleccionado = null;
}

async function validarRequisitosAlumno(alumnoId) {
    try {
        const token = localStorage.getItem('token');
        const response = await fetch(`${API_URL}/titulos-electronicos/validar-requisitos/${alumnoId}`, {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const validacion = await response.json();
            mostrarResultadoValidacion(validacion);
        }
    } catch (error) {
        console.error('Error:', error);
    }
}

function mostrarResultadoValidacion(validacion) {
    const contenedor = document.getElementById('requisitosValidacion');
    const btnGenerar = document.getElementById('btnGenerarTitulo');

    const tipo = validacion.cumpleRequisitos ? 'success' : 'danger';
    const icono = validacion.cumpleRequisitos ? 'check-circle-fill' : 'exclamation-triangle-fill';
    const titulo = validacion.cumpleRequisitos ? 'Requisitos cumplidos' : 'No cumple requisitos';

    const alerta = document.createElement('div');
    alerta.className = `alert alert-${tipo}`;
    alerta.innerHTML = `<i class="bi bi-${icono}"></i> <strong>${titulo}</strong><br>`;
    const msg = document.createElement('span');
    msg.textContent = validacion.mensaje || '';
    alerta.appendChild(msg);
    contenedor.replaceChildren(alerta);

    btnGenerar.disabled = !validacion.cumpleRequisitos;
}

// ==================== GENERACIÓN DE TÍTULO ====================

async function generarTitulo(event) {
    console.log("🚀 generarTitulo() disparado", event);
    event.preventDefault();

    if (!alumnoSeleccionado) {
        mostrarAlerta('alertaGeneracion', 'warning', 'Primero debe buscar y seleccionar un alumno');
        return;
    }

    const datos = {
        alumnoId: parseInt(document.getElementById('alumnoIdTitulo').value),
        programaId: parseInt(document.getElementById('programaId').value),
        fechaExpedicion: document.getElementById('fechaExpedicion').value,
        idModalidadTitulacion: document.getElementById('idModalidadTitulacion').value,
        modalidadTitulacion: document.getElementById('modalidadTitulacion').value,
        fechaExamenProfesional: document.getElementById('fechaExamenProfesional').value,
        cumplioServicioSocial: document.getElementById('cumplioServicioSocial').checked,
        idFundamentoLegalServicioSocial: document.getElementById('idFundamentoLegalServicioSocial').value,
        fundamentoLegalServicioSocial: document.getElementById('fundamentoLegalServicioSocial').value,
        institucionProcedencia: document.getElementById('institucionProcedencia').value,
        idTipoEstudioAntecedente: document.getElementById('idTipoEstudioAntecedente').value,
        tipoEstudioAntecedente: document.getElementById('tipoEstudioAntecedente').value,
        idEntidadFederativaAntecedente: document.getElementById('idEntidadFederativaAntecedente').value,
        entidadFederativaAntecedente: document.getElementById('entidadFederativaAntecedente').value,
        fechaTerminacionAntecedente: document.getElementById('fechaTerminacionAntecedente').value
    };

    // Validar que programaId esté presente
    if (!datos.programaId || isNaN(datos.programaId)) {
        mostrarAlerta('alertaGeneracion', 'danger',
            'El alumno seleccionado no tiene un programa educativo asignado');
        return;
    }

    try {
        const token = localStorage.getItem('token');
        const response = await fetch(`${API_URL}/titulos-electronicos`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(datos)
        });

        if (response.ok) {
            const resultado = await response.json();
            mostrarExitoGeneracion(resultado);
            limpiarFormulario();
        } else {
            const error = await response.json();
            throw new Error(error.mensaje || 'Error al generar título');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaGeneracion', 'danger', 'Error al generar título: ' + error.message);
    }
}

function mostrarExitoGeneracion(titulo) {
    const alerta = document.getElementById('alertaGeneracion');
    alerta.className = 'alert alert-success';
    alerta.innerHTML = '<strong>¡Título generado exitosamente!</strong><br>';

    const folio = document.createElement('span');
    folio.innerHTML = '<strong>Folio de Control:</strong> ';
    const folioVal = document.createElement('span');
    folioVal.textContent = titulo.folioControl || '';
    folio.appendChild(folioVal);
    alerta.appendChild(folio);
    alerta.appendChild(document.createElement('br'));

    const estatus = document.createElement('span');
    estatus.innerHTML = '<strong>Estatus:</strong> ';
    const estatusVal = document.createElement('span');
    estatusVal.textContent = titulo.estatus || '';
    estatus.appendChild(estatusVal);
    alerta.appendChild(estatus);
    alerta.appendChild(document.createElement('br'));

    const enlace = document.createElement('a');
    enlace.href = `${API_URL}/titulos-electronicos/${encodeURIComponent(titulo.id)}/descargar-xml`;
    enlace.className = 'btn btn-sm btn-primary mt-2';
    enlace.target = '_blank';
    enlace.rel = 'noopener';
    enlace.innerHTML = '<i class="bi bi-download"></i> Descargar XML';
    alerta.appendChild(enlace);

    alerta.classList.remove('d-none');
}

function limpiarFormulario() {
    document.getElementById('formGenerarTitulo').reset();
    document.getElementById('alumnoIdTitulo').value = '';
    document.getElementById('programaId').value = '';
    document.getElementById('alumnoInfo').classList.add('d-none');
    document.getElementById('btnGenerarTitulo').disabled = true;
    alumnoSeleccionado = null;
    configurarFechaActual();
    ocultarAlerta('alertaGeneracion');
}

// ==================== CONSULTA DE TÍTULOS ====================

async function buscarTitulos() {
    const criterio = document.getElementById('buscarTitulo').value.trim();
    const estatus = document.getElementById('filtroEstatus').value;

    if (!criterio && !estatus) {
        return;
    }

    try {
        const token = localStorage.getItem('token');
        let titulos = [];

        if (criterio) {
            // Buscar por folio de control
            if (criterio.includes('_')) {
                const response = await fetch(`${API_URL}/titulos-electronicos/folio/${criterio}`, {
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (response.ok) {
                    const titulo = await response.json();
                    titulos = [titulo];
                }
            } else {
                // Buscar por matrícula - primero obtener alumno
                const responseAlumno = await fetch(`${API_URL}/alumnos/matricula/${criterio}`, {
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (responseAlumno.ok) {
                    const alumno = await responseAlumno.json();
                    const responseTitulos = await fetch(`${API_URL}/titulos-electronicos/alumno/${alumno.id}`, {
                        headers: {
                            'Authorization': `Bearer ${token}`,
                            'Content-Type': 'application/json'
                        }
                    });

                    if (responseTitulos.ok) {
                        titulos = await responseTitulos.json();
                    }
                }
            }
        }

        // Aplicar filtro de estatus si está seleccionado
        if (estatus && titulos.length > 0) {
            titulos = titulos.filter(t => t.estatus === estatus);
        }

        titulosCache = titulos;
        mostrarTitulosEnTabla(titulos);
    } catch (error) {
        console.error('Error:', error);
        mostrarTitulosEnTabla([]);
    }
}

function mostrarTitulosEnTabla(titulos) {
    const tbody = document.getElementById('tablaTitulos');

    if (titulos.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No se encontraron títulos con los criterios especificados</td></tr>';
        return;
    }

    tbody.replaceChildren();

    titulos.forEach(titulo => {
        const tr = document.createElement('tr');
        tr.dataset.tituloId = titulo.id;

        const tdFolio = document.createElement('td');
        const strong = document.createElement('strong');
        strong.textContent = titulo.folioControl || 'N/A';
        tdFolio.appendChild(strong);

        const tdNombre = document.createElement('td');
        tdNombre.textContent = titulo.alumnoNombre || 'N/A';

        const tdMatricula = document.createElement('td');
        tdMatricula.textContent = titulo.alumnoMatricula || 'N/A';

        const tdPrograma = document.createElement('td');
        tdPrograma.textContent = titulo.programaNombre || 'N/A';

        const tdFecha = document.createElement('td');
        tdFecha.textContent = formatearFecha(titulo.fechaExpedicion);

        const tdEstatus = document.createElement('td');
        tdEstatus.innerHTML = obtenerBadgeEstatus(titulo.estatus);

        const tdAcciones = document.createElement('td');
        const grupo = document.createElement('div');
        grupo.className = 'btn-group btn-group-sm';
        grupo.setAttribute('role', 'group');

        const enlaceXml = document.createElement('a');
        enlaceXml.href = `${API_URL}/titulos-electronicos/${encodeURIComponent(titulo.id)}/descargar-xml`;
        enlaceXml.className = 'btn btn-primary';
        enlaceXml.title = 'Descargar XML';
        enlaceXml.target = '_blank';
        enlaceXml.rel = 'noopener';
        enlaceXml.innerHTML = '<i class="bi bi-download"></i>';

        const btnEstatus = document.createElement('button');
        btnEstatus.className = 'btn btn-warning';
        btnEstatus.title = 'Cambiar estatus';
        btnEstatus.dataset.action = 'cambiar-estatus';
        btnEstatus.innerHTML = '<i class="bi bi-arrow-repeat"></i>';

        grupo.appendChild(enlaceXml);
        grupo.appendChild(btnEstatus);
        tdAcciones.appendChild(grupo);

        tr.appendChild(tdFolio);
        tr.appendChild(tdNombre);
        tr.appendChild(tdMatricula);
        tr.appendChild(tdPrograma);
        tr.appendChild(tdFecha);
        tr.appendChild(tdEstatus);
        tr.appendChild(tdAcciones);
        tbody.appendChild(tr);
    });
}

function obtenerBadgeEstatus(estatus) {
    const badges = {
        'GENERADO': '<span class="badge bg-info">Generado</span>',
        'FIRMADO': '<span class="badge bg-primary">Firmado</span>',
        'ENVIADO_SEP': '<span class="badge bg-warning">Enviado SEP</span>',
        'VALIDADO_SEP': '<span class="badge bg-success">Validado SEP</span>',
        'RECHAZADO_SEP': '<span class="badge bg-danger">Rechazado SEP</span>',
        'ENTREGADO': '<span class="badge bg-dark">Entregado</span>'
    };
    return badges[estatus] || `<span class="badge bg-secondary">${estatus}</span>`;
}

// ==================== CAMBIO DE ESTATUS ====================

function abrirModalCambioEstatus(tituloId, estatusActual) {
    document.getElementById('tituloIdCambioEstatus').value = tituloId;
    document.getElementById('nuevoEstatus').value = estatusActual;

    const modal = new bootstrap.Modal(document.getElementById('modalCambiarEstatus'));
    modal.show();
}

async function confirmarCambioEstatus() {
    const tituloId = document.getElementById('tituloIdCambioEstatus').value;
    const nuevoEstatus = document.getElementById('nuevoEstatus').value;

    if (!nuevoEstatus) {
        mostrarAlerta('alertaEstatus', 'warning', 'Por favor seleccione un estatus');
        return;
    }

    try {
        const token = localStorage.getItem('token');
        const response = await fetch(`${API_URL}/titulos-electronicos/${tituloId}/estatus`, {
            method: 'PUT',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ estatus: nuevoEstatus })
        });

        if (response.ok) {
            // Cerrar modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('modalCambiarEstatus'));
            modal.hide();

            // Recargar tabla
            await buscarTitulos();

            showSuccess('Estatus actualizado exitosamente');
        } else {
            throw new Error('Error al actualizar estatus');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaEstatus', 'danger', 'Error al actualizar estatus: ' + error.message);
    }
}

// ==================== UTILIDADES ====================

function mostrarAlerta(elementId, tipo, mensaje) {
    const alerta = document.getElementById(elementId);
    alerta.className = `alert alert-${tipo}`;
    alerta.innerHTML = mensaje;
    alerta.classList.remove('d-none');

    // Auto-ocultar después de 8 segundos para alertas de éxito
    if (tipo === 'success') {
        setTimeout(() => ocultarAlerta(elementId), 8000);
    }
}

function ocultarAlerta(elementId) {
    const alerta = document.getElementById(elementId);
    alerta.classList.add('d-none');
}

function formatearFecha(fechaStr) {
    if (!fechaStr) return 'N/A';
    const fecha = new Date(fechaStr + 'T00:00:00');
    return fecha.toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' });
}

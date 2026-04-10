/**
 * titulos-electronicos.js
 * Gestión de generación y consulta de títulos profesionales electrónicos
 */
console.log("✅ titulos-electronicos.js CARGADO - versión debug");


let alumnoSeleccionado = null;
let titulosCache = [];

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
   console.log("✅ buscarAlumno() INICIO");

  const input = document.getElementById('buscarAlumno');
  if (!input) {
    console.error("❌ No existe #buscarAlumno en el DOM");
    return;
  }

  const criterio = input.value.trim();
  console.log("criterio:", criterio);

  if (!criterio) {
    alert('Por favor ingrese una matrícula o CURP');
    return;
  }

  try {
    const token = localStorage.getItem('token');
    console.log("API_BASE_URL:", API_BASE_URL);
    console.log("token existe:", !!token);

    let url = `${API_BASE_URL}/alumnos/matricula/${encodeURIComponent(criterio)}`;
    console.log("GET:", url);

    let response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    console.log("respuesta matricula status:", response.status);

    // Si no se encuentra por matrícula, intentar por CURP (18 chars)
    if (!response.ok && criterio.length === 18) {
      url = `${API_BASE_URL}/alumnos/curp/${encodeURIComponent(criterio)}`;
      console.log("GET:", url);

      response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      console.log("respuesta curp status:", response.status);
    }

    if (response.ok) {
      const data = await response.json();
      console.log("✅ alumno encontrado:", data);

      alumnoSeleccionado = data;
      mostrarDatosAlumno(alumnoSeleccionado);

      if (!alumnoSeleccionado.id) {
        console.warn("⚠️ El JSON del alumno no trae 'id'. Revisa el DTO del backend.");
        return;
      }

      await validarRequisitosAlumno(alumnoSeleccionado.id);
    } else {
      let body = "";
      try { body = await response.text(); } catch (_) {}
      console.warn("❌ alumno no encontrado / error:", response.status, body);
      mostrarAlumnoNoEncontrado();
    }

  } catch (error) {
    console.error('❌ Error en buscarAlumno():', error);
    mostrarAlumnoNoEncontrado();
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
        const response = await fetch(`${API_BASE_URL}/titulos-electronicos/validar-requisitos/${alumnoId}`, {
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

    if (validacion.cumpleRequisitos) {
        contenedor.innerHTML = `
            <div class="alert alert-success">
                <i class="bi bi-check-circle-fill"></i>
                <strong>Requisitos cumplidos</strong><br>
                ${validacion.mensaje}
            </div>
        `;
        btnGenerar.disabled = false;
    } else {
        contenedor.innerHTML = `
            <div class="alert alert-danger">
                <i class="bi bi-exclamation-triangle-fill"></i>
                <strong>No cumple requisitos</strong><br>
                ${validacion.mensaje}
            </div>
        `;
        btnGenerar.disabled = true;
    }
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
        const response = await fetch(`${API_BASE_URL}/titulos-electronicos`, {
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
    const mensaje = `
        <strong>¡Título generado exitosamente!</strong><br>
        <strong>Folio de Control:</strong> ${titulo.folioControl}<br>
        <strong>Estatus:</strong> ${titulo.estatus}<br>
        <a href="${API_BASE_URL}/titulos-electronicos/${titulo.id}/descargar-xml"
           class="btn btn-sm btn-primary mt-2" target="_blank">
            <i class="bi bi-download"></i> Descargar XML
        </a>
    `;

    mostrarAlerta('alertaGeneracion', 'success', mensaje);

    // Cambiar a tab de consulta después de 3 segundos
    setTimeout(() => {
        const consultarTab = new bootstrap.Tab(document.getElementById('consultar-tab'));
        consultarTab.show();
    }, 3000);
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
                const response = await fetch(`${API_BASE_URL}/titulos-electronicos/folio/${criterio}`, {
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
                const responseAlumno = await fetch(`${API_BASE_URL}/alumnos/matricula/${criterio}`, {
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (responseAlumno.ok) {
                    const alumno = await responseAlumno.json();
                    const responseTitulos = await fetch(`${API_BASE_URL}/titulos-electronicos/alumno/${alumno.id}`, {
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
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted">
                    No se encontraron títulos con los criterios especificados
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = titulos.map(titulo => {
        const estatusBadge = obtenerBadgeEstatus(titulo.estatus);

        return `
            <tr>
                <td><strong>${titulo.folioControl}</strong></td>
                <td>${titulo.alumnoNombre || 'N/A'}</td>
                <td>${titulo.alumnoMatricula || 'N/A'}</td>
                <td>${titulo.programaNombre || 'N/A'}</td>
                <td>${formatearFecha(titulo.fechaExpedicion)}</td>
                <td>${estatusBadge}</td>
                <td>
                    <div class="btn-group btn-group-sm" role="group">
                        <a href="${API_BASE_URL}/titulos-electronicos/${titulo.id}/descargar-xml"
                           class="btn btn-primary" title="Descargar XML" target="_blank">
                            <i class="bi bi-download"></i>
                        </a>
                        <button class="btn btn-warning" onclick="abrirModalCambioEstatus(${titulo.id}, '${titulo.estatus}')"
                                title="Cambiar estatus">
                            <i class="bi bi-arrow-repeat"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
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
        const response = await fetch(`${API_BASE_URL}/titulos-electronicos/${tituloId}/estatus`, {
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

            alert('Estatus actualizado exitosamente');
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

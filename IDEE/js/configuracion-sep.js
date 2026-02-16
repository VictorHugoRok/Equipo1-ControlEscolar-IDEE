/**
 * configuracion-sep.js
 * Gestión de configuración institucional y responsables de firma para títulos electrónicos SEP
 */

console.log('📄 configuracion-sep.js cargado');

let configuracionActual = null;

/**
 * Función helper para crear headers con token si está disponible
 * Compatible tanto con seguridad habilitada como deshabilitada
 */
function getHeaders(includeContentType = true) {
    const headers = {};

    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }

    // Si existe token en localStorage, incluirlo
    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function() {
    console.log('🔍 DOMContentLoaded en configuracion-sep.js');

    // Solo inicializar si estamos en la página correcta
    const configuracionSection = document.getElementById('configuracionSepSection');
    console.log('configuracionSepSection:', configuracionSection);

    if (configuracionSection) {
        console.log('✅ Inicializando configuración SEP...');
        console.log('Tipo de inicializarEventos:', typeof inicializarEventos);
        try {
            inicializarEventosConfiguracion();
        } catch (error) {
            console.error('❌ Error al inicializar eventos:', error);
        }

        // Cargar datos solo cuando se muestre la sección
        // Observar cambios en la visibilidad de la sección
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.attributeName === 'class') {
                    const target = mutation.target;
                    if (!target.classList.contains('d-none')) {
                        // La sección se volvió visible, cargar datos
                        cargarConfiguracionInstitucional();
                        cargarResponsablesFirma();
                    }
                }
            });
        });

        observer.observe(configuracionSection, { attributes: true });

        // Si la sección ya está visible, cargar datos inmediatamente
        if (!configuracionSection.classList.contains('d-none')) {
            cargarConfiguracionInstitucional();
            cargarResponsablesFirma();
        }
    }
});

function inicializarEventosConfiguracion() {
    console.log('🎯 Ejecutando inicializarEventosConfiguracion()');

    // Formulario de Institución
    const formInstitucion = document.getElementById('formInstitucion');
    console.log('formInstitucion:', formInstitucion);
    if (formInstitucion) {
        formInstitucion.addEventListener('submit', guardarConfiguracionInstitucional);
        console.log('✓ Event listener agregado a formInstitucion');
    }

    const btnCancelarInstitucion = document.getElementById('btnCancelarInstitucion');
    if (btnCancelarInstitucion) {
        btnCancelarInstitucion.addEventListener('click', limpiarFormularioInstitucion);
    }

    // Formulario de Responsables
    const btnGuardarResponsable = document.getElementById('btnGuardarResponsable');
    if (btnGuardarResponsable) {
        btnGuardarResponsable.addEventListener('click', guardarResponsable);
    }

    // Modal - Reset form cuando se abre
    const modalResponsable = document.getElementById('modalResponsable');
    if (modalResponsable) {
        modalResponsable.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            if (!button || !button.dataset.responsableId) {
                limpiarFormularioResponsable();
            }
        });
    }

    // Formulario de Certificados
    const formCertificados = document.getElementById('formCertificados');
    console.log('formCertificados:', formCertificados);
    if (formCertificados) {
        formCertificados.addEventListener('submit', guardarCertificados);
        console.log('✓ Event listener agregado a formCertificados');
    } else {
        console.warn('⚠️ formCertificados NO encontrado');
    }
}

// ==================== CONFIGURACIÓN INSTITUCIONAL ====================

async function cargarConfiguracionInstitucional() {
    try {
        const response = await fetch(`${API_BASE_URL}/configuracion-institucional`, {
            headers: getHeaders()
        });

        if (response.ok) {
            configuracionActual = await response.json();
            mostrarConfiguracionEnFormulario(configuracionActual);
        } else if (response.status === 404) {
            console.log('No hay configuración institucional activa');
        } else {
            throw new Error('Error al cargar configuración');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaInstitucion', 'danger', 'Error al cargar la configuración institucional');
    }
}

function mostrarConfiguracionEnFormulario(config) {
    document.getElementById('institucionId').value = config.id || '';
    document.getElementById('cveInstitucion').value = config.cveInstitucion || '';
    document.getElementById('nombreInstitucion').value = config.nombreInstitucion || '';
    document.getElementById('idEntidadFederativa').value = config.idEntidadFederativa || '';
    document.getElementById('entidadFederativa').value = config.entidadFederativa || '';
    document.getElementById('noCertificadoSat').value = config.noCertificadoSat || '';
}

async function guardarConfiguracionInstitucional(event) {
    event.preventDefault();

    const id = document.getElementById('institucionId').value;
    const datos = {
        cveInstitucion: document.getElementById('cveInstitucion').value.trim(),
        nombreInstitucion: document.getElementById('nombreInstitucion').value.trim(),
        idEntidadFederativa: document.getElementById('idEntidadFederativa').value.trim(),
        entidadFederativa: document.getElementById('entidadFederativa').value.trim(),
        noCertificadoSat: document.getElementById('noCertificadoSat').value.trim() || null,
        activo: true
    };

    // Si existe configuración, incluir los datos de certificados
    if (configuracionActual) {
        datos.certificadoPath = configuracionActual.certificadoPath;
        datos.llavePrivadaPath = configuracionActual.llavePrivadaPath;
        datos.passwordLlavePrivada = configuracionActual.passwordLlavePrivada;
    }

    try {
        const url = id ? `${API_BASE_URL}/configuracion-institucional/${id}` : `${API_BASE_URL}/configuracion-institucional`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getHeaders(),
            body: JSON.stringify(datos)
        });

        if (response.ok) {
            const resultado = await response.json();
            configuracionActual = resultado;
            mostrarAlerta('alertaInstitucion', 'success', 'Configuración guardada exitosamente');
            mostrarConfiguracionEnFormulario(resultado);
        } else {
            throw new Error('Error al guardar configuración');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaInstitucion', 'danger', 'Error al guardar la configuración: ' + error.message);
    }
}

function limpiarFormularioInstitucion() {
    document.getElementById('formInstitucion').reset();
    document.getElementById('institucionId').value = '';
    ocultarAlerta('alertaInstitucion');
}

// ==================== RESPONSABLES DE FIRMA ====================

async function cargarResponsablesFirma() {
    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma`, {
            headers: getHeaders()
        });

        if (response.ok) {
            const responsables = await response.json();
            mostrarResponsablesEnTabla(responsables);
        } else {
            throw new Error('Error al cargar responsables');
        }
    } catch (error) {
        console.error('Error:', error);
        document.getElementById('tablaResponsables').innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-danger">
                    Error al cargar responsables de firma
                </td>
            </tr>
        `;
    }
}

function mostrarResponsablesEnTabla(responsables) {
    const tbody = document.getElementById('tablaResponsables');

    if (responsables.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted">
                    No hay responsables de firma registrados
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = responsables.map(resp => {
        const nombreCompleto = `${resp.nombre} ${resp.primerApellido} ${resp.segundoApellido || ''}`.trim();
        const estadoBadge = resp.activo
            ? '<span class="badge bg-success">Activo</span>'
            : '<span class="badge bg-secondary">Inactivo</span>';

        return `
            <tr>
                <td>${resp.ordenFirma}</td>
                <td>${nombreCompleto}</td>
                <td>${resp.curp}</td>
                <td>${resp.cargo}</td>
                <td>${resp.abrTitulo || '-'}</td>
                <td>${estadoBadge}</td>
                <td>
                    <button class="btn btn-sm btn-primary" onclick="editarResponsable(${resp.id})">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="eliminarResponsable(${resp.id})">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

async function guardarResponsable() {
    const id = document.getElementById('responsableId').value;

    const datos = {
        nombre: document.getElementById('responsableNombre').value.trim(),
        primerApellido: document.getElementById('responsablePrimerApellido').value.trim(),
        segundoApellido: document.getElementById('responsableSegundoApellido').value.trim() || null,
        curp: document.getElementById('responsableCurp').value.trim().toUpperCase(),
        idCargo: document.getElementById('responsableIdCargo').value.trim(),
        cargo: document.getElementById('responsableCargo').value.trim(),
        abrTitulo: document.getElementById('responsableAbrTitulo').value.trim() || null,
        ordenFirma: parseInt(document.getElementById('responsableOrdenFirma').value),
        activo: document.getElementById('responsableActivo').checked
    };

    // Validar CURP
    if (datos.curp.length !== 18) {
        mostrarAlerta('alertaResponsable', 'danger', 'El CURP debe tener 18 caracteres');
        return;
    }

    try {
        const url = id ? `${API_BASE_URL}/responsables-firma/${id}` : `${API_BASE_URL}/responsables-firma`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getHeaders(),
            body: JSON.stringify(datos)
        });

        if (response.ok) {
            // Cerrar modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('modalResponsable'));
            modal.hide();

            // Recargar tabla
            await cargarResponsablesFirma();

            mostrarAlerta('alertaResponsable', 'success', 'Responsable guardado exitosamente');
            setTimeout(() => ocultarAlerta('alertaResponsable'), 3000);
        } else {
            throw new Error('Error al guardar responsable');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaResponsable', 'danger', 'Error al guardar el responsable: ' + error.message);
    }
}

async function editarResponsable(id) {
    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma`, {
            headers: getHeaders()
        });

        if (response.ok) {
            const responsables = await response.json();
            const responsable = responsables.find(r => r.id === id);

            if (responsable) {
                document.getElementById('responsableId').value = responsable.id;
                document.getElementById('responsableNombre').value = responsable.nombre;
                document.getElementById('responsablePrimerApellido').value = responsable.primerApellido;
                document.getElementById('responsableSegundoApellido').value = responsable.segundoApellido || '';
                document.getElementById('responsableCurp').value = responsable.curp;
                document.getElementById('responsableIdCargo').value = responsable.idCargo;
                document.getElementById('responsableCargo').value = responsable.cargo;
                document.getElementById('responsableAbrTitulo').value = responsable.abrTitulo || '';
                document.getElementById('responsableOrdenFirma').value = responsable.ordenFirma;
                document.getElementById('responsableActivo').checked = responsable.activo;

                document.getElementById('modalResponsableTitle').innerHTML =
                    '<i class="bi bi-pencil"></i> Editar Responsable de Firma';

                const modal = new bootstrap.Modal(document.getElementById('modalResponsable'));
                modal.show();
            }
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Error al cargar los datos del responsable');
    }
}

async function eliminarResponsable(id) {
    if (!confirm('¿Está seguro de desactivar este responsable de firma?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma/${id}`, {
            method: 'DELETE',
            headers: getHeaders()
        });

        if (response.ok) {
            await cargarResponsablesFirma();
            alert('Responsable desactivado exitosamente');
        } else {
            throw new Error('Error al eliminar responsable');
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Error al eliminar el responsable: ' + error.message);
    }
}

function limpiarFormularioResponsable() {
    document.getElementById('formResponsable').reset();
    document.getElementById('responsableId').value = '';
    document.getElementById('responsableActivo').checked = true;
    document.getElementById('modalResponsableTitle').innerHTML =
        '<i class="bi bi-person-plus"></i> Nuevo Responsable de Firma';
    ocultarAlerta('alertaResponsable');
}

// ==================== CERTIFICADOS SAT ====================

async function guardarCertificados(event) {
    event.preventDefault();
    console.log('guardarCertificados llamado');

    if (!configuracionActual || !configuracionActual.id) {
        mostrarAlerta('alertaCertificados', 'warning',
            'Debe guardar primero la configuración institucional en la pestaña "Institución"');
        return;
    }

    // Obtener archivos del formulario
    const certificadoFile = document.getElementById('certificadoFile').files[0];
    const llavePrivadaFile = document.getElementById('llavePrivadaFile').files[0];
    const password = document.getElementById('passwordLlave').value.trim();

    // Validar que se hayan seleccionado los archivos
    if (!certificadoFile) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe seleccionar el archivo .cer del certificado');
        return;
    }

    if (!llavePrivadaFile) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe seleccionar el archivo .key de la llave privada');
        return;
    }

    if (!password) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe ingresar la contraseña de la llave privada');
        return;
    }

    // Validar extensiones de archivos
    if (!certificadoFile.name.toLowerCase().endsWith('.cer')) {
        mostrarAlerta('alertaCertificados', 'danger', 'El certificado debe ser un archivo .cer');
        return;
    }

    if (!llavePrivadaFile.name.toLowerCase().endsWith('.key')) {
        mostrarAlerta('alertaCertificados', 'danger', 'La llave privada debe ser un archivo .key');
        return;
    }

    // Crear FormData para enviar archivos
    const formData = new FormData();
    formData.append('cer', certificadoFile);
    formData.append('key', llavePrivadaFile);
    formData.append('password', password);

    // Mostrar estado de carga
    const submitButton = event.target.querySelector('button[type="submit"]');
    const originalText = submitButton.innerHTML;
    submitButton.innerHTML = '<i class="bi bi-hourglass-split"></i> Subiendo archivos...';
    submitButton.disabled = true;

    try {
        // Para FormData, solo incluir Authorization si hay token
        const headers = getHeaders(false); // false = no incluir Content-Type
        const fetchOptions = {
            method: 'POST',
            body: formData
        };

        // Solo agregar headers si hay token (getHeaders incluirá Authorization automáticamente)
        if (Object.keys(headers).length > 0) {
            fetchOptions.headers = headers;
        }

        const response = await fetch(`${API_BASE_URL}/configuracion-institucional/certificados`, fetchOptions);

        if (response.ok) {
            const resultado = await response.json();

            // Mostrar información de los archivos subidos
            document.getElementById('certificadoStatus').innerHTML = `
                <div class="alert alert-success alert-sm">
                    <i class="bi bi-check-circle"></i> ${resultado.certificadoFilename}
                    (${(resultado.certificadoSize / 1024).toFixed(2)} KB)
                </div>
            `;

            document.getElementById('llaveStatus').innerHTML = `
                <div class="alert alert-success alert-sm">
                    <i class="bi bi-check-circle"></i> ${resultado.llavePrivadaFilename}
                    (${(resultado.llavePrivadaSize / 1024).toFixed(2)} KB)
                </div>
            `;

            // Recargar configuración
            await cargarConfiguracionInstitucional();

            mostrarAlerta('alertaCertificados', 'success', resultado.mensaje || 'Certificados guardados exitosamente');

            // Limpiar formulario
            document.getElementById('formCertificados').reset();
            document.getElementById('passwordLlave').value = '';
        } else {
            const error = await response.json();
            throw new Error(error.error || 'Error al guardar certificados');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaCertificados', 'danger', 'Error al guardar certificados: ' + error.message);
    } finally {
        // Restaurar botón
        submitButton.innerHTML = originalText;
        submitButton.disabled = false;
    }
}

// ==================== UTILIDADES ====================

function mostrarAlerta(elementId, tipo, mensaje) {
    const alerta = document.getElementById(elementId);
    alerta.className = `alert alert-${tipo}`;
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');

    // Auto-ocultar después de 5 segundos
    setTimeout(() => ocultarAlerta(elementId), 5000);
}

function ocultarAlerta(elementId) {
    const alerta = document.getElementById(elementId);
    alerta.classList.add('d-none');
}

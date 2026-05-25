# Uso del XSD y XSLT del apartado "Código fuente" – Certificados (DEC)

## 1. XSD (esquema del certificado)

### Qué es
El XSD define la estructura válida del **Documento Electrónico de Certificación (DEC)**. El XML que generes debe cumplir este esquema para ser aceptado por la SEP.

### Dónde está
- **Ruta en el proyecto:** `backend/src/main/resources/xsd/CertificadoElectronico.xsd`
- **Elemento raíz en el XML:** `Dec` (no "CertificadoElectronico")
- **Namespace:** `https://www.siged.sep.gob.mx/certificados/`

### Qué hacer con él

| Acción | Descripción |
|--------|-------------|
| **Dejarlo donde está** | El archivo ya está en la ruta correcta. El código que genere el XML del certificado validará contra este XSD. |
| **Revisar que sea el oficial** | Compara con el XSD del apartado "Código fuente" del documento. Debe ser idéntico al que proporciona la SEP. |
| **Limpiar si hace falta** | Si al pegar el XSD quedaron números de página (ej. "33", "34") dentro de `<xs:annotation>`, quítalos para que el esquema sea válido. |
| **Versión** | En el XSD el atributo `version` del elemento raíz puede estar en `fixed="3.0"`. Si tu especificación dice otra (ej. 2.0), ajusta según el documento oficial. |

### Uso en la implementación (más adelante)
- En el servicio de certificados (ej. `CertificadoXmlGeneratorService` o similar), cargar este XSD desde classpath:  
  `getClass().getResourceAsStream("/xsd/CertificadoElectronico.xsd")`
- Validar el XML generado antes de guardar o firmar, igual que en `XmlGeneratorService.validarContraXSD()` para títulos.

---

## 2. XSLT (transformación del XML)

### Qué es
El XSLT transforma el **XML del certificado (Dec)** en otro formato, normalmente:
- **HTML** para vista previa o impresión del certificado, o
- Otro **XML** si la SEP pide un formato derivado.

El documento "Código fuente" incluye una hoja XSLT que debes usar tal como la proporciona la SEP.

### Dónde ponerlo

1. **Crear la carpeta** (si no existe):  
   `backend/src/main/resources/xsl/`  
   (O `xslt/` si prefieres; lo importante es ser consistente.)

2. **Nombre del archivo:**  
   Por ejemplo: `CertificadoElectronico.xslt` o `dec-plantilla.xslt`.  
   Si en el documento viene un nombre concreto, úsalo.

3. **Contenido:**  
   Copiar **íntegro** el XSLT del apartado "Código fuente" al archivo. No cambiar namespaces ni nombres de nodos salvo que el documento indique lo contrario.

### Uso en la implementación (más adelante)

- Cargar la hoja XSLT desde classpath, por ejemplo:  
  `getClass().getResourceAsStream("/xsl/CertificadoElectronico.xslt")`
- Usar `javax.xml.transform`:
  - **Source** del XML: el XML del certificado (String o `StreamSource`).
  - **Source** del XSLT: la hoja XSLT (por ejemplo desde el `InputStream` anterior).
  - **Result**: por ejemplo un `StreamResult` a un `StringWriter` (HTML como String) o a un archivo/response.
- Aplicar la transformación cuando necesites:
  - **Vista previa en el frontend:** el backend puede exponer un endpoint que devuelva el HTML generado (ej. `GET /api/certificados-electronicos/{id}/vista-previa`).
  - **Descarga/impresión:** generar HTML o el formato que indique la SEP y devolverlo o guardarlo.

No implementar aún la transformación; solo tener el XSLT en la ruta indicada para cuando se implemente el servicio de certificados.

---

## 3. Resumen de pasos

| Paso | XSD | XSLT |
|------|-----|------|
| 1 | Ya está en `xsd/CertificadoElectronico.xsd` | Crear carpeta `resources/xsl/` |
| 2 | Revisar que coincida con el documento y limpiar números de página | Copiar el XSLT del doc a `xsl/CertificadoElectronico.xslt` (o el nombre que indique la SEP) |
| 3 | Ajustar `version` si el estándar lo indica | — |
| 4 | En Fase 3: usar este XSD para validar el XML generado | En Fase 3 (o posterior): cargar este XSLT y aplicarlo para vista previa/impresión o formato requerido |

---

## 4. Estructura del XML (recordatorio)

El XML del certificado tiene **elemento raíz `<Dec>`** (con el namespace de certificados) y, en secuencia, nodos como:

- `ServicioFirmante` (idEntidad)
- `Ipes` (Responsable, idNombreInstitucion, nombreInstitucion, idCampus, campus, idEntidadFederativa, entidadFederativa)
- `Rvoe` (numero, fechaExpedicion)
- `Carrera` (idCarrera, claveCarrera, nombreCarrera, tipo periodo, plan, nivel, calificaciones min/max/aprobatoria)
- `Alumno` (numeroControl, curp, nombre, apellidos, idGenero, fechaNacimiento)
- `Expedicion` (idTipoCertificacion 79=Total/80=Parcial, tipoCertificacion, fecha, lugarExpedicion)
- `Asignaturas` (total, asignadas, promedio, créditos, numeroCiclos; lista de `Asignatura` con clave, nombre, ciclo, calificacion, créditos, tipoAsignatura, etc.)
- `Dreoe` (opcional, sello SEP)
- `SepIpes` (opcional, sello SEP)

Atributos del raíz: `version`, `tipoCertificado` (fijo 5), `folioControl`, `sello`, `certificadoResponsable`, `noCertificadoResponsable`.

Al implementar el generador de XML, seguir este orden y estos nombres para que el documento pase la validación contra `CertificadoElectronico.xsd`.

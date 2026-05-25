<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dec="https://www.siged.sep.gob.mx/certificados/" version="1.0">
<xsl:key name="kCiclo" match="dec:Asignatura" use="@ciclo"/>
<!--  claveCct: pasado como parámetro (no está en XSD oficial SEP)  -->
<xsl:param name="claveCct" select="''"/>
<!--  method="xml" produce XHTML bien formado; method="html" genera meta sin cerrar y OpenHTML falla al parsear  -->
<xsl:output method="xml" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>
<xsl:template match="/">
<xsl:apply-templates select="dec:Dec"/>
</xsl:template>
<xsl:template match="dec:Dec">
<html lang="es">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>Certificado - Documento Electrónico de Certificación (DEC)</title>
<style type="text/css"> @page { size: A4; margin: 1cm 2cm; } body { font-family: 'Times New Roman', Times, serif; font-size: 11pt; margin: 0 auto; width: 100%; max-width: 21cm; padding: 0; color: #222; box-sizing: border-box; position: relative; } /* ── Logos: anclados al borde superior del área de contenido (= 1 cm del borde real de la hoja) ── */ .logos-independientes { position: absolute; top: 0; /* top:0 del body = justo al inicio del margen @page (1 cm del borde físico) */ left: 0; right: 0; height: 3cm; /* altura máxima de los logos */ pointer-events: none; } .logos-independientes .logo-sep { position: absolute; left: 0; top: 0; width: 3cm; height: 3cm; object-fit: contain; display: block; } .logos-independientes .logo-inst { position: absolute; right: 0; top: 0; width: 3cm; height: 3cm; object-fit: contain; display: block; } /* ── Contenido: separado de la zona de logos + pequeño espacio de respiro ── */ .contenido-documento { margin-top: 2.2cm; /* ligeramente por debajo de los logos (3 cm) */ } .encabezado-documento { margin-bottom: 0.25em; } .encabezado-texto { padding-top: 0; text-align: center; } .encabezado-texto .linea { font-size: 10pt; font-weight: bold; margin: 0.12em 0; text-transform: none; } .encabezado-texto .linea-institucion { white-space: nowrap; } .encabezado-texto .linea.sep { margin-bottom: 0.5em; display: block; } .encabezado-texto .titulo-certificado { font-size: 11pt; font-weight: bold; margin: 1.6em 0 0 0; text-transform: none; } .seccion { margin: 0.4em 0; } .seccion h2 { font-size: 12pt; margin: 0.8em 0 0.4em 0; } .dato { margin: 0.2em 0; } .dato .etiqueta { font-weight: bold; display: inline-block; min-width: 12em; } .tabla-certificado { width: 100%; border-collapse: collapse; margin: 1em 0; font-size: 10pt; } .tabla-certificado th, .tabla-certificado td { border: 1px solid #666; padding: 0.4em 0.5em; text-align: left; } .tabla-certificado th { background: #e8e8e8; font-weight: bold; } .tabla-datos-alumno-programa { width: 100%; border-collapse: collapse; margin: 0.4em 0; font-size: 10pt; table-layout: fixed; } .tabla-datos-alumno-programa td { border: 1px solid #333; padding: 0.4em 0.5em; text-align: left; } .tabla-datos-alumno-programa td.etiqueta { background: #e0e0e0; font-weight: bold; width: 18%; } .tabla-datos-alumno-programa td.valor { background: #fff; width: 32%; } .tabla-calificaciones-cuadrantes { width: 100%; border-collapse: collapse; margin: 0.4em 0; margin-top: 2.1em; font-size: 9pt; } .tabla-calificaciones-cuadrantes > tbody > tr > td { vertical-align: top; padding: 0.2em; width: 50%; border: 1px solid #333; } .tabla-calificaciones-cuadrantes .cuadrante-inner-wrap { position: relative; min-height: 100%; display: block; } .tabla-calificaciones-cuadrantes .cuadrante-inner-wrap::after { content: ''; position: absolute; left: calc(100% - 3.5em); top: 1.3em; bottom: 0; width: 1px; background: #333; } .tabla-calificaciones-cuadrantes .header-asig-calif td { vertical-align: top; padding: 0.2em; width: 50%; border: 1px solid #333; } .tabla-calificaciones-cuadrantes .header-asig-calif .cuadrante-header { margin: 0; } .tabla-calificaciones-cuadrantes .cuadrante-header { width: 100%; border-collapse: collapse; table-layout: fixed; } .tabla-calificaciones-cuadrantes .cuadrante-header th { background: #e0e0e0; font-weight: bold; padding: 0.2em 0.3em; border: none; border-left: 1px solid #333; font-size: 8pt; } .tabla-calificaciones-cuadrantes .cuadrante-header th:first-child { border-left: none; } .tabla-calificaciones-cuadrantes .cuadrante-header th.asignatura { text-align: center; width: auto; } .tabla-calificaciones-cuadrantes .cuadrante-header th.calif { text-align: center; width: 3.5em; min-width: 3.5em; } .tabla-calificaciones-cuadrantes .cuadrante-inner { width: 100%; border-collapse: collapse; table-layout: fixed; } .tabla-calificaciones-cuadrantes .cuadrante-inner th { background: #e0e0e0; font-weight: bold; padding: 0.2em 0.3em; border: none; border-left: 1px solid #333; font-size: 8pt; } .tabla-calificaciones-cuadrantes .cuadrante-inner th:first-child { border-left: none; } .tabla-calificaciones-cuadrantes .cuadrante-inner th.semestre { text-align: center; } .tabla-calificaciones-cuadrantes .cuadrante-inner th.asignatura { text-align: left; } .tabla-calificaciones-cuadrantes .cuadrante-inner th.calif { text-align: center; width: 3.5em; min-width: 3.5em; } .tabla-calificaciones-cuadrantes .cuadrante-inner td { padding: 0.35em 0.3em; border: none; border-left: 1px solid #333; font-size: 8pt; } .tabla-calificaciones-cuadrantes .cuadrante-inner td:first-child { border-left: none; } .tabla-calificaciones-cuadrantes .cuadrante-inner td.asignatura { text-align: left; width: auto; } .tabla-calificaciones-cuadrantes .cuadrante-inner td.calif { text-align: right; width: 3.5em; min-width: 3.5em; } .tabla-certificado .centro { text-align: center; } .zona-qr-responsable { width: 100%; margin-top: 1.7em; overflow: visible; } .zona-qr-responsable .zona-qr { float: left; width: 2.75cm; } .zona-qr-responsable .zona-responsable { margin-left: 3.1cm; overflow: visible; } .zona-qr-responsable .zona-qr img { width: 2.75cm; height: 2.75cm; display: block; } .zona-qr-responsable .zona-qr .folio-qr { margin-top: 0.25em; font-size: 8pt; line-height: 1.1; font-weight: bold; text-align: center; word-break: break-word; } .zona-qr-responsable .zona-responsable .linea-resp { margin: 0.2em 0; font-size: 8pt; } .zona-qr-responsable .zona-responsable .sello { margin-top: 0.3em; font-family: monospace; font-size: 6pt; white-space: normal; /* permite el ajuste de línea natural */ overflow-wrap: anywhere; /* quiebra sólo cuando no hay otra opción */ word-break: normal; /* NO rompe antes de / ni otros caracteres */ hyphens: none; /* sin guiones automáticos */ -webkit-hyphens: none; -ms-hyphens: none; overflow: visible; } @media print { body { padding: 0; } .zona-qr-responsable .zona-qr img { width: 2.75cm !important; height: 2.75cm !important; } .zona-qr-responsable, .zona-qr-responsable .zona-responsable, .zona-qr-responsable .zona-responsable .sello { overflow: visible !important; } } </style>
<style type="text/css">
  /* Ajustes para tabla dinámica + columna créditos (solo vista previa/PDF) */
  .tabla-calificaciones-cuadrantes .cuadrante-header th.cred,
  .tabla-calificaciones-cuadrantes .cuadrante-inner th.cred,
  .tabla-calificaciones-cuadrantes .cuadrante-inner td.cred {
    width: 3.5em;
    min-width: 3.5em;
    text-align: right;
  }
  /* Mover divisor vertical para 2 columnas numéricas (calif + créd) */
  .tabla-calificaciones-cuadrantes .cuadrante-inner-wrap::after {
    left: calc(100% - 7em);
  }
</style>
</head>
<body>
<div class="logos-independientes">
<img class="logo-sep" src="PLACEHOLDER_SEP_LOGO" alt="SEP"/>
<img class="logo-inst" src="PLACEHOLDER_INSTITUCION_LOGO" alt="Institución"/>
</div>
<div class="contenido-documento">
<header class="encabezado-documento">
<div class="encabezado-texto">
<div class="linea sep">
SECRETARÍA DE EDUCACIÓN PÚBLICA
<br/>
</div>
<div class="linea linea-institucion">
<xsl:value-of select="dec:Ipes[1]/@nombreInstitucion"/>
</div>
<div class="linea">
<xsl:if test="$claveCct and string-length(normalize-space($claveCct)) > 0">
C.C.T.
<xsl:value-of select="$claveCct"/>
</xsl:if>
</div>
<div class="titulo-certificado">
<xsl:text>Certificado </xsl:text>
<xsl:value-of select="normalize-space(dec:Expedicion[1]/@tipoCertificacion)"/>
<xsl:text> de </xsl:text>
<xsl:value-of select="normalize-space(dec:Carrera[1]/@nivelEstudios)"/>
</div>
</div>
</header>
<div class="seccion">
<table class="tabla-datos-alumno-programa">
<tr>
<td class="etiqueta">Alumno:</td>
<td class="valor">
<xsl:value-of select="normalize-space(concat(dec:Alumno[1]/@nombre, ' ', dec:Alumno[1]/@primerApellido, ' ', dec:Alumno[1]/@segundoApellido))"/>
</td>
<td class="etiqueta">RVOE:</td>
<td class="valor">
<xsl:value-of select="dec:Rvoe[1]/@numero"/>
</td>
</tr>
<tr>
<td class="etiqueta">CURP:</td>
<td class="valor">
<xsl:value-of select="dec:Alumno[1]/@curp"/>
</td>
<td class="etiqueta">Programa:</td>
<td class="valor">
<xsl:value-of select="dec:Carrera[1]/@nombreCarrera"/>
</td>
</tr>
<tr>
<td class="etiqueta">Matrícula:</td>
<td class="valor">
<xsl:value-of select="dec:Alumno[1]/@numeroControl"/>
</td>
<td class="etiqueta">Clave del Plan:</td>
<td class="valor">
<xsl:value-of select="dec:Carrera[1]/@clavePlan"/>
</td>
</tr>
<tr>
<td class="etiqueta">Promedio General:</td>
<td class="valor">
<xsl:value-of select="dec:Asignaturas[1]/@promedio"/>
</td>
<td class="etiqueta">Créditos:</td>
<td class="valor">
<xsl:value-of select="dec:Asignaturas[1]/@totalCreditos"/>
</td>
</tr>
</table>
</div>
<div class="seccion">
<table class="tabla-calificaciones-cuadrantes">
<thead>
<tr class="header-asig-calif">
<td>
<table class="cuadrante-header">
<colgroup>
<col style="width:auto"/>
<col style="width:3.5em"/>
<col style="width:3.5em"/>
</colgroup>
<tr>
<th class="asignatura">ASIGNATURAS/CURSOS</th>
<th class="calif">CALIF. FINAL</th>
<th class="cred">CRÉD.</th>
</tr>
</table>
</td>
<td>
<table class="cuadrante-header">
<colgroup>
<col style="width:auto"/>
<col style="width:3.5em"/>
<col style="width:3.5em"/>
</colgroup>
<tr>
<th class="asignatura">ASIGNATURAS/CURSOS</th>
<th class="calif">CALIF. FINAL</th>
<th class="cred">CRÉD.</th>
</tr>
</table>
</td>
</tr>
</thead>
<tbody>
<xsl:variable name="numCiclos" select="number(dec:Asignaturas[1]/@numeroCiclos)"/>
<xsl:variable name="totalCiclos">
  <xsl:choose>
    <xsl:when test="not($numCiclos = $numCiclos) or $numCiclos &lt;= 0">4</xsl:when>
    <xsl:otherwise><xsl:value-of select="$numCiclos"/></xsl:otherwise>
  </xsl:choose>
</xsl:variable>
<xsl:call-template name="render-cuadrantes">
  <xsl:with-param name="dec" select="."/>
  <xsl:with-param name="indice" select="1"/>
  <xsl:with-param name="total" select="number($totalCiclos)"/>
</xsl:call-template>
</tbody>
</table>
</div>
<div class="seccion zona-qr-responsable">
<div class="zona-qr">
<img src="https://api.qrserver.com/v1/create-qr-code/?size=300x300&amp;data=https%3A%2F%2Fidee.edu.mx%2F" alt="QR" style="width:2.75cm;height:2.75cm"/>
<div class="folio-qr">
Folio:
<xsl:value-of select="@folioControl"/>
</div>
</div>
<div class="zona-responsable">
<div class="linea-resp">
<xsl:value-of select="normalize-space(concat(dec:Ipes[1]/dec:Responsable[1]/@nombre, ' ', dec:Ipes[1]/dec:Responsable[1]/@primerApellido, ' ', dec:Ipes[1]/dec:Responsable[1]/@segundoApellido))"/>
</div>
<div class="linea-resp">
CURP:
<xsl:value-of select="dec:Ipes[1]/dec:Responsable[1]/@curp"/>
</div>
<div class="linea-resp">
<xsl:value-of select="dec:Ipes[1]/dec:Responsable[1]/@cargo"/>
</div>
<div class="sello">
Sello:
<xsl:value-of select="@sello"/>
</div>
</div>
</div>
</div>
</body>
</html>
</xsl:template>
<xsl:template name="cuadrante-semestre">
<xsl:param name="dec"/>
<xsl:param name="numero" select="1"/>
<xsl:for-each select="$dec/dec:Asignaturas[1]/dec:Asignatura[generate-id(.)=generate-id(key('kCiclo', @ciclo)[1])]">
<xsl:sort select="@ciclo"/>
<xsl:if test="position()=$numero">
<xsl:variable name="ciclo" select="@ciclo"/>
<div class="cuadrante-inner-wrap">
<table class="cuadrante-inner">
<colgroup>
<col style="width:auto"/>
<col style="width:3.5em"/>
<col style="width:3.5em"/>
</colgroup>
<thead>
<tr>
<th colspan="3" class="semestre">
<xsl:choose>
<xsl:when test="$numero=1">PRIMER</xsl:when>
<xsl:when test="$numero=2">SEGUNDO</xsl:when>
<xsl:when test="$numero=3">TERCER</xsl:when>
<xsl:when test="$numero=4">CUARTO</xsl:when>
<xsl:otherwise>
<xsl:choose>
  <xsl:when test="$numero=5">QUINTO</xsl:when>
  <xsl:when test="$numero=6">SEXTO</xsl:when>
  <xsl:when test="$numero=7">SEPTIMO</xsl:when>
  <xsl:when test="$numero=8">OCTAVO</xsl:when>
  <xsl:when test="$numero=9">NOVENO</xsl:when>
  <xsl:when test="$numero=10">DECIMO</xsl:when>
  <xsl:when test="$numero=11">DECIMO PRIMERO</xsl:when>
  <xsl:when test="$numero=12">DECIMO SEGUNDO</xsl:when>
  <xsl:otherwise>
    <xsl:value-of select="$numero"/>
  </xsl:otherwise>
</xsl:choose>
</xsl:otherwise>
</xsl:choose>
<xsl:variable name="tipoPeriodoAttr" select="$dec/dec:Carrera[1]/@tipoPeriodo"/>
<xsl:text> </xsl:text>
<xsl:value-of select="normalize-space($tipoPeriodoAttr)"/>
</th>
</tr>
</thead>
<tbody>
<xsl:for-each select="$dec/dec:Asignaturas[1]/dec:Asignatura[@ciclo=$ciclo]">
<tr>
<td class="asignatura">
<xsl:value-of select="@nombre"/>
</td>
<td class="calif">
<xsl:value-of select="@calificacion"/>
</td>
<td class="cred">
<xsl:value-of select="@creditos"/>
</td>
</tr>
</xsl:for-each>
</tbody>
</table>
</div>
</xsl:if>
</xsl:for-each>
</xsl:template>

<!-- Renderiza filas de cuadrantes (2 por fila) de forma dinámica -->
<xsl:template name="render-cuadrantes">
<xsl:param name="dec"/>
<xsl:param name="indice" select="1"/>
<xsl:param name="total" select="4"/>
<xsl:if test="$indice &lt;= $total">
  <tr>
    <td>
      <xsl:call-template name="cuadrante-semestre">
        <xsl:with-param name="dec" select="$dec"/>
        <xsl:with-param name="numero" select="$indice"/>
      </xsl:call-template>
    </td>
    <td>
      <xsl:if test="$indice + 1 &lt;= $total">
        <xsl:call-template name="cuadrante-semestre">
          <xsl:with-param name="dec" select="$dec"/>
          <xsl:with-param name="numero" select="$indice + 1"/>
        </xsl:call-template>
      </xsl:if>
    </td>
  </tr>
  <xsl:call-template name="render-cuadrantes">
    <xsl:with-param name="dec" select="$dec"/>
    <xsl:with-param name="indice" select="$indice + 2"/>
    <xsl:with-param name="total" select="$total"/>
  </xsl:call-template>
</xsl:if>
</xsl:template>
</xsl:stylesheet>

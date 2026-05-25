<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:dec="https://www.siged.sep.gob.mx/certificados/"
    exclude-result-prefixes="dec" version="1.0"> 
  <xsl:output indent="no" omit-xml-declaration="true" encoding="UTF-8" method="text"/> 
  <!--
    Cadena original DEC según especificación SEP (XSLT 1.0 compatible con Java).
    - Delimitadores: inicia y termina con ||
    - Separadores: cada campo con |
    - Campos opcionales vacíos: mantener pipe para posición
    - Prohibido: pipe (|) en valores de atributos
    - Codificación: UTF-8
    - Orden exacto: Dec, ServicioFirmante, Ipes, Responsable, Rvoe, Carrera, Alumno, Expedicion, Asignaturas, Asignatura(detalle)
  -->
  <xsl:template match="dec:Dec"> 
    <xsl:text>||</xsl:text> 
    <!-- 1. Dec: versión | tipoCertificado -->
    <xsl:value-of select="normalize-space(@version)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(@tipoCertificado)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 2. ServicioFirmante: idEntidad -->
    <xsl:value-of select="normalize-space(dec:ServicioFirmante[1]/@idEntidad)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 3. Ipes: idNombreInstitucion | idCampus | idEntidadFederativa -->
    <xsl:value-of select="normalize-space(dec:Ipes[1]/@idNombreInstitucion)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Ipes[1]/@idCampus)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Ipes[1]/@idEntidadFederativa)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 4. Responsable: curp | idCargo -->
    <xsl:value-of select="normalize-space(dec:Ipes[1]/dec:Responsable[1]/@curp)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Ipes[1]/dec:Responsable[1]/@idCargo)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 5. Rvoe: numero | fechaExpedicion -->
    <xsl:value-of select="normalize-space(dec:Rvoe[1]/@numero)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Rvoe[1]/@fechaExpedicion)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 6. Carrera: idCarrera | idTipoPeriodo | clavePlan | idNivelEstudios | calificacionMinima | calificacionMaxima | calificacionMinimaAprobatoria -->
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@idCarrera)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@idTipoPeriodo)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@clavePlan)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@idNivelEstudios)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@calificacionMinima)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@calificacionMaxima)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Carrera[1]/@calificacionMinimaAprobatoria)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 7. Alumno: numeroControl | curp | nombre | primerApellido | segundoApellido | idGenero | fechaNacimiento -->
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@numeroControl)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@curp)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@nombre)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@primerApellido)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@segundoApellido)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@idGenero)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Alumno[1]/@fechaNacimiento)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 8. Expedicion: idTipoCertificacion | fecha | idLugarExpedicion -->
    <xsl:value-of select="normalize-space(dec:Expedicion[1]/@idTipoCertificacion)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Expedicion[1]/@fecha)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Expedicion[1]/@idLugarExpedicion)"/> 
    <xsl:text>|</xsl:text> 
    <!-- 9. Asignaturas (Resumen): total | asignadas | promedio | totalCreditos | creditosObtenidos | numeroCiclos -->
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@total)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@asignadas)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@promedio)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@totalCreditos)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@creditosObtenidos)"/> 
    <xsl:text>|</xsl:text> 
    <xsl:value-of select="normalize-space(dec:Asignaturas[1]/@numeroCiclos)"/> 
    <!-- 10. Asignatura (Detalle): por cada materia: idAsignatura | ciclo | calificacion | idTipoAsignatura | creditos -->
    <xsl:for-each select="dec:Asignaturas[1]/dec:Asignatura"> 
      <xsl:text>|</xsl:text> 
      <xsl:value-of select="normalize-space(@idAsignatura)"/> 
      <xsl:text>|</xsl:text> 
      <xsl:value-of select="normalize-space(@ciclo)"/> 
      <xsl:text>|</xsl:text> 
      <xsl:value-of select="normalize-space(@calificacion)"/> 
      <xsl:text>|</xsl:text> 
      <xsl:value-of select="normalize-space(@idTipoAsignatura)"/> 
      <xsl:text>|</xsl:text> 
      <xsl:value-of select="normalize-space(@creditos)"/> 
    </xsl:for-each> 
    <xsl:text>||</xsl:text> 
  </xsl:template> 
</xsl:stylesheet>

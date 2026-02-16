# 📚 Migraciones de Base de Datos - Flyway

## ¿Qué son las migraciones?

Los archivos en esta carpeta son **scripts SQL versionados** que se ejecutan automáticamente cuando inicias la aplicación Spring Boot. Flyway se encarga de:

1. ✅ Ejecutar las migraciones en orden (V3, V4, V5, V6, V7...)
2. ✅ Registrar qué migraciones ya se aplicaron
3. ✅ Evitar ejecutar la misma migración dos veces
4. ✅ Mantener tu esquema de BD sincronizado con tu código

---

## 📂 Archivos de Migración

| Archivo | Descripción | Ejecutado |
|---------|-------------|-----------|
| `V3__crear_modulo_titulos_electronicos.sql` | Crea tablas: `configuracion_institucional`, `responsables_firma`, `titulos_electronicos` | ✅ Al iniciar app |
| `V4__agregar_blob_certificados.sql` | Agrega columnas BLOB para almacenar archivos `.cer` y `.key` | ✅ Al iniciar app |
| `V5__datos_prueba_titulos.sql` | Inserta 1 programa educativo y 1 alumno de prueba | ✅ Al iniciar app |
| `V6__alumno_adicional_prueba.sql` | Inserta alumnos adicionales | ✅ Al iniciar app |
| **`V7__datos_completos_prueba_titulos.sql`** | **Inserta configuración institucional, responsables de firma y más alumnos EGRESADOS** | ✅ **NUEVO** |

---

## 🎯 Datos de Prueba Insertados (V7)

### 1️⃣ **Configuración Institucional**
```
Clave: IDEE001
Nombre: Instituto de Especialidades Estomatológicas IDEE
Entidad: Ciudad de México (09)
Estado: ACTIVA
```

### 2️⃣ **Responsables de Firma**

#### Director General (Orden 1)
```
Nombre: Dr. Roberto Méndez Sánchez
CURP: MESR750420HDFLNT01
Cargo: Director General (ID: 01)
```

#### Secretaria Académica (Orden 2)
```
Nombre: Mtra. Ana Patricia López Ramírez
CURP: LORA820615MDFLMN05
Cargo: Secretaria Académica (ID: 02)
```

### 3️⃣ **Alumnos EGRESADOS** (listos para generar título)

| Matrícula | Nombre | CURP | Estatus |
|-----------|--------|------|---------|
| `IDEE2024ESP001` | María Fernanda González Martínez | `GOMF950315MDFNRR08` | **EGRESADO** ✅ |
| `IDEE2024ESP002` | Carlos Alberto Ramírez Torres | `RATC920825HDFMRR03` | **EGRESADO** ✅ |
| `IDEE2024ESP003` | Laura Patricia Hernández García | `HEGL931010MDFRRR01` | **EGRESADO** ✅ |
| `IDEE2024ESP004` | Diego Fernando Castro Morales | `CAMD940212HDFSSR02` | ACTIVO ❌ |

> ⚠️ **Nota:** Solo los alumnos con estatus `EGRESADO` pueden generar títulos electrónicos.

---

## 🚀 Cómo Usar las Migraciones

### Primera Vez (Aplicación Nueva)

1. **Asegúrate de tener PostgreSQL corriendo:**
   ```bash
   # La base de datos debe existir
   # Nombre: idee_control_escolar
   # Usuario: postgres
   # Password: admin1234
   ```

2. **Ejecuta Maven para descargar Flyway:**
   ```bash
   mvn clean install
   ```

3. **Inicia la aplicación:**
   ```bash
   mvn spring-boot:run
   ```

4. **Flyway ejecutará automáticamente:**
   - V3 → Crea tablas
   - V4 → Agrega columnas BLOB
   - V5 → Inserta programa y 1 alumno
   - V6 → Inserta más alumnos
   - V7 → **Inserta configuración y responsables** ✨

### Ya Tienes Datos

Si ya corriste la aplicación antes:
- ✅ Flyway **solo ejecutará V7** (las nuevas migraciones)
- ✅ **NO volverá a ejecutar** V3, V4, V5, V6
- ✅ Tu tabla `flyway_schema_history` registra qué migraciones ya se aplicaron

---

## 📋 Verificar que se Aplicaron las Migraciones

### Opción 1: Revisar Logs de la Aplicación

Cuando inicies la app, verás:
```
INFO  o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "public"
```

### Opción 2: Consultar la Tabla Flyway

```sql
-- Ver historial de migraciones
SELECT installed_rank, version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Opción 3: Verificar Datos Insertados

```sql
-- Verificar configuración institucional
SELECT * FROM configuracion_institucional;

-- Verificar responsables de firma
SELECT nombre, primer_apellido, cargo, activo, orden_firma
FROM responsables_firma
ORDER BY orden_firma;

-- Verificar alumnos EGRESADOS
SELECT matricula, nombre, apellido_paterno, estatus_matricula
FROM alumnos
WHERE estatus_matricula = 'EGRESADO';
```

---

## ⚠️ Reglas Importantes

### ❌ **NUNCA hagas esto:**
1. **NO edites** archivos de migración ya aplicados (V3, V4, V5, V6, V7)
2. **NO cambies** el nombre de archivos de migración
3. **NO borres** migraciones del historial

### ✅ **Sí puedes hacer esto:**
1. **Crear nuevas migraciones** con versión mayor (V8, V9, etc.)
2. **Desactivar Flyway temporalmente** en `application.properties`:
   ```properties
   spring.flyway.enabled=false
   ```
3. **Limpiar la BD y empezar de cero** (desarrollo):
   ```bash
   # Borrar base de datos y crearla de nuevo
   dropdb idee_control_escolar
   createdb idee_control_escolar
   # Reiniciar aplicación → Flyway ejecutará todas las migraciones
   ```

---

## 🔧 Crear Nuevas Migraciones

Si necesitas agregar más datos o modificar el esquema:

1. **Crea un nuevo archivo** con el siguiente formato:
   ```
   V8__descripcion_de_la_migracion.sql
   ```

2. **Nomenclatura:**
   - `V` = Version (obligatorio)
   - `8` = Número de versión (debe ser mayor al último)
   - `__` = Doble guion bajo (obligatorio)
   - `descripcion` = Nombre descriptivo (sin espacios, usar guiones bajos)
   - `.sql` = Extensión

3. **Ejemplo - Agregar más programas:**
   ```sql
   -- V8__agregar_programas_educativos.sql
   INSERT INTO programas_educativos (...) VALUES (...);
   ```

---

## 🐛 Solución de Problemas

### Error: "Validate failed: Migrations have failed validation"

**Causa:** Editaste una migración ya aplicada.

**Solución:**
```bash
# Opción 1: Reparar Flyway
mvn flyway:repair

# Opción 2: Limpiar y empezar de cero (desarrollo)
dropdb idee_control_escolar
createdb idee_control_escolar
mvn spring-boot:run
```

### Error: "Schema-validation: missing table [nombre_tabla]"

**Causa:** Cambiaste `ddl-auto=validate` pero las tablas no existen.

**Solución:**
```properties
# En application.properties, temporalmente cambiar a:
spring.jpa.hibernate.ddl-auto=update
# Iniciar app, luego volver a:
spring.jpa.hibernate.ddl-auto=validate
```

---

## 📖 Más Información

- [Documentación Flyway](https://flywaydb.org/documentation/)
- [Spring Boot + Flyway](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)

---

## ✅ Checklist de Verificación

Después de iniciar la aplicación por primera vez:

- [ ] Flyway ejecutó las migraciones (revisar logs)
- [ ] Existe configuración institucional activa
- [ ] Existen 2 responsables de firma activos
- [ ] Existen 3 alumnos con estatus EGRESADO
- [ ] La tabla `flyway_schema_history` tiene 5+ registros

¡Listo! Ahora puedes generar títulos electrónicos 🎓

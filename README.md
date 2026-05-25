# IDEE — Sistema de Control Escolar

> Sistema integral de gestión académica para el **Instituto de Especialidades Estomatológicas (IDEE)**. Cubre el ciclo de vida académico completo: desde la inscripción de alumnos hasta la expedición de títulos y certificados electrónicos con validez oficial ante la SEP.

---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Características Principales](#características-principales)
- [Tecnologías](#tecnologías)
- [Arquitectura del Proyecto](#arquitectura-del-proyecto)
- [Estructura de Carpetas](#estructura-de-carpetas)
- [Requisitos Previos](#requisitos-previos)
- [Instalación y Ejecución](#instalación-y-ejecución)
- [Variables de Entorno](#variables-de-entorno)
- [Roles y Permisos](#roles-y-permisos)
- [Módulos del Sistema](#módulos-del-sistema)
- [API REST — Endpoints](#api-rest--endpoints)
- [Base de Datos](#base-de-datos)
- [Seguridad](#seguridad)
- [Equipo](#equipo)

---

## Descripción General

IDEE Control Escolar es una aplicación web full-stack diseñada para digitalizar y centralizar los procesos académicos-administrativos de una institución educativa de nivel superior. El sistema permite a administradores, secretarias, docentes y alumnos interactuar con sus datos académicos desde un único punto de acceso, con control granular de permisos por rol.

Uno de los pilares del sistema es la **generación de títulos y certificados electrónicos** con firma digital, cumpliendo con los estándares XML/XSD de la Dirección General de Incorporación y Revalidación de Estudios (DGIET) de la SEP.

---

## Características Principales

- **Gestión académica completa:** Alumnos, docentes, grupos, asignaturas, horarios y calificaciones.
- **Títulos electrónicos:** Generación de e-títulos en formato XML firmados digitalmente con certificados X.509, validados contra el XSD oficial de la SEP.
- **Certificados electrónicos:** Expedición de certificados de estudios con sello digital.
- **Evaluación docente:** Formularios configurables de desempeño respondidos de forma anónima por los alumnos.
- **Kardex académico:** Historial completo de materias y calificaciones por alumno.
- **Constancias:** Solicitud y generación de constancias en PDF con logo institucional y firmas autorizadas.
- **Carga masiva:** Importación de alumnos desde archivos Excel.
- **Control de acceso por roles:** Seis roles diferenciados con permisos granulares sobre cada recurso.
- **Autenticación JWT:** Tokens de acceso (24 h) y refresh tokens (7 días) con rate limiting en login.

---

## Tecnologías

### Backend

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.2.0 | Framework web y de configuración |
| Spring Security | 6.x | Autenticación y autorización |
| Spring Data JPA | 3.2.0 | Acceso a datos (ORM) |
| Hibernate | 6.x | Implementación JPA |
| PostgreSQL | 14+ | Base de datos producción |
| H2 | Embedded | Base de datos desarrollo |
| Flyway | 4.0.0 | Migraciones de base de datos |
| JJWT | 0.12.3 | Generación y validación de JWT |
| BouncyCastle | 1.77 | Firma digital (XAdES / PKCS#8) |
| OpenHTMLToPDF | 1.1.37 | Generación de PDFs |
| Apache POI | 5.2.5 | Lectura de Excel (.xlsx) |
| Lombok | 1.18.30 | Reducción de boilerplate |
| MapStruct | 1.5.5 | Mapeo Entity ↔ DTO |
| Maven | 3.6+ | Gestión de dependencias y build |
| Docker | — | Contenedorización |

### Frontend

| Tecnología | Versión | Uso |
|---|---|---|
| HTML5 / CSS3 | — | Estructura y estilos |
| JavaScript ES6+ | — | Lógica de cliente |
| Bootstrap | 5.3.3 | Framework de UI responsive |
| Bootstrap Icons | 1.11.3 | Iconografía |
| Font Awesome | 6.5.1 | Iconografía adicional |
| Fetch API | — | Comunicación con el backend (REST) |

---

## Arquitectura del Proyecto

```
┌─────────────────────────────────────────────────────────────┐
│                        NAVEGADOR                            │
│          HTML + CSS + JavaScript (Bootstrap 5)              │
│    Páginas: Login, Dashboard, Alumnos, Maestros, etc.       │
└────────────────────────┬────────────────────────────────────┘
                         │  HTTP / REST (JSON)
                         │  Authorization: Bearer <JWT>
┌────────────────────────▼────────────────────────────────────┐
│              BACKEND — Spring Boot 3.2 (Puerto 8080)        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │
│  │Controller│→ │ Service  │→ │Repository│→ │  Entity   │  │
│  │  (REST)  │  │(Business)│  │  (JPA)   │  │ (Modelo)  │  │
│  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │
│                                                             │
│  Spring Security (JWT) │ Flyway (Migraciones) │ BouncyCastle│
└────────────────────────┬────────────────────────────────────┘
                         │  JDBC
┌────────────────────────▼────────────────────────────────────┐
│              BASE DE DATOS                                  │
│         PostgreSQL (Producción) / H2 (Desarrollo)           │
│                  ~58 tablas                                 │
└─────────────────────────────────────────────────────────────┘
```

**Patrón de capas:** `Entity → Repository → Service → Controller → DTO`

---

## Estructura de Carpetas

```
Equipo1-ControlEscolar-IDEE/
├── IDEE/
│   ├── backend/                         # API REST — Spring Boot
│   │   ├── src/main/java/com/idee/controlescolar/
│   │   │   ├── config/                  # Configuración Spring (Security, CORS, DataLoader)
│   │   │   ├── controller/              # 26 controladores REST
│   │   │   ├── dto/                     # Objetos de transferencia de datos (50+)
│   │   │   ├── exception/               # Manejo global de excepciones
│   │   │   ├── model/                   # Entidades JPA (39 clases)
│   │   │   ├── repository/              # Repositorios Spring Data (20+)
│   │   │   ├── security/                # JWT, filtros, rate limiting, validador de permisos
│   │   │   ├── service/                 # Lógica de negocio (30+ servicios)
│   │   │   └── util/                    # Utilidades generales
│   │   ├── src/main/resources/
│   │   │   ├── application.properties           # Configuración base
│   │   │   ├── application-development.properties
│   │   │   ├── application-production.properties
│   │   │   ├── db/migration/            # Migraciones Flyway V1–V58
│   │   │   ├── xsd/                     # Esquemas XML oficiales SEP
│   │   │   │   ├── TituloElectronico.xsd
│   │   │   │   ├── CertificadoElectronico.xsd
│   │   │   │   └── Autenticacion.xsd
│   │   │   └── xsl/                     # Transformaciones XSLT para PDF
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── run-dev.ps1 / run-dev.cmd    # Scripts de inicio rápido
│   │
│   └── frontend/                        # SPA — HTML / CSS / JS Vanilla
│       ├── index.html                   # Página de login
│       ├── landing.html                 # Landing institucional
│       ├── pages/                       # Una página por módulo (18+)
│       │   ├── dashboard.html
│       │   ├── alumnos.html
│       │   ├── docentes.html
│       │   ├── calificaciones.html
│       │   ├── titulos-electronicos.html
│       │   ├── certificados-electronicos.html
│       │   ├── evaluacion-docente.html
│       │   ├── kardex.html
│       │   └── ...
│       ├── js/                          # Un script por módulo (20+)
│       │   ├── config.js                # URL base del API
│       │   ├── auth.js                  # Login / logout / refresh
│       │   ├── app.js                   # Inicialización de la SPA
│       │   └── ...
│       ├── components/                  # Sidebar reutilizable por rol
│       │   ├── sidebar.html
│       │   ├── sidebar-alumno.html
│       │   └── sidebar-maestro.html
│       ├── css/
│       │   ├── styles.css
│       │   ├── mobile.css
│       │   └── tablet.css
│       └── assets/                      # Logo, imágenes institucionales
│
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
└── LICENSE
```

---

## Requisitos Previos

| Herramienta | Versión mínima |
|---|---|
| Java JDK | 17 |
| Maven | 3.6 |
| PostgreSQL | 14 (producción) |
| Docker *(opcional)* | 20+ |
| Servidor HTTP *(frontend)* | Cualquiera (Live Server, Nginx, etc.) |

---

## Instalación y Ejecución

### 1. Clonar el repositorio

```bash
git clone https://github.com/VictorHugoRok/Equipo1-ControlEscolar-IDEE.git
cd Equipo1-ControlEscolar-IDEE
```

### 2. Configurar la base de datos

**Desarrollo (H2 en memoria — sin configuración extra):**
El perfil `development` levanta H2 automáticamente y ejecuta todas las migraciones Flyway + el `DataLoader` con datos de prueba.

**Producción (PostgreSQL):**
```sql
CREATE DATABASE idee_control_escolar;
CREATE USER idee_user WITH ENCRYPTED PASSWORD 'tu_password';
GRANT ALL PRIVILEGES ON DATABASE idee_control_escolar TO idee_user;
```

### 3. Variables de entorno necesarias

Ver sección [Variables de Entorno](#variables-de-entorno).

### 4. Levantar el backend

```bash
cd IDEE/backend

# Desarrollo (H2 + logs debug)
./mvnw spring-boot:run -Dspring-boot.run.profiles=development

# Producción (PostgreSQL)
./mvnw spring-boot:run -Dspring-boot.run.profiles=production
```

En Windows puedes usar el script incluido:
```powershell
.\run-dev.ps1
```

La API queda disponible en: `http://localhost:8080/api`

### 5. Levantar el frontend

Sirve la carpeta `IDEE/frontend/` con cualquier servidor HTTP estático:

```bash
# Con VS Code Live Server — clic derecho sobre index.html → "Open with Live Server"

# Con Python
cd IDEE/frontend
python -m http.server 5500

# Con Node (npx)
npx serve IDEE/frontend -p 5500
```

Accede en: `http://localhost:5500`

### 6. Docker (alternativa)

```bash
cd IDEE/backend
docker build -t idee-backend .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/idee_control_escolar \
  -e SPRING_DATASOURCE_USERNAME=idee_user \
  -e SPRING_DATASOURCE_PASSWORD=tu_password \
  -e JWT_SECRET=tu_secreto_jwt \
  idee-backend
```

---

## Variables de Entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo (`development` / `production`) | `development` |
| `SPRING_DATASOURCE_URL` | URL JDBC de la base de datos | H2 en memoria |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos | *(vacío)* |
| `JWT_SECRET` | Clave secreta para firmar JWT (mín. 256 bits) | *(requerido en prod)* |
| `JWT_EXPIRATION_MS` | Duración del token de acceso en ms | `86400000` (24 h) |
| `SPRING_MAIL_HOST` | Servidor SMTP para notificaciones | *(opcional)* |
| `SPRING_MAIL_USERNAME` | Usuario SMTP | *(opcional)* |
| `SPRING_MAIL_PASSWORD` | Contraseña SMTP | *(opcional)* |

---

## Roles y Permisos

| Rol | Descripción |
|---|---|
| `ADMIN` | Acceso total al sistema. Gestiona usuarios, configuración y catálogos. |
| `SECRETARIA_ACADEMICA` | Aprueba calificaciones, expide títulos, certificados y constancias. |
| `SECRETARIA_ADMINISTRATIVA` | Gestiona expedientes, valida documentos de alumnos y personal. |
| `COORDINADOR_ACADEMICO` | Coordinación de programas educativos, grupos y horarios. |
| `MAESTRO` | Captura calificaciones, consulta grupos y horarios asignados. |
| `ALUMNO` | Consulta su expediente, calificaciones, horario y solicita constancias. |

El sistema aplica validación de permisos en tiempo de ejecución mediante `PermisosValidator` en cada controlador.

---

## Módulos del Sistema

### Autenticación
- Login con JWT (access token 24 h + refresh token 7 días)
- Cambio de contraseña obligatorio en el primer acceso
- Rate limiting: máximo 5 intentos fallidos en ventanas de 15 minutos
- Endpoint `/auth/me` para obtener datos del usuario sin recarga

### Gestión de Alumnos
- Alta, baja, modificación y búsqueda de estudiantes
- Campos: matrícula, CURP, datos personales, fotografía, estado (ACTIVO / BAJA / EGRESADO)
- Expediente digital con documentos adjuntos
- Carga masiva desde archivo Excel con validación de errores

### Gestión de Docentes
- CRUD de maestros con cédulas profesionales
- Asignación a grupos y programas educativos
- Visualización de horario por docente

### Programas Educativos y Asignaturas
- Gestión de licenciaturas, maestrías y especialidades
- Almacenamiento de clave DGP, RVOE, créditos y duración
- Plan de estudios con asignaturas, créditos y horas

### Grupos y Horarios
- Creación de grupos por asignatura, período y maestro
- Bloques horarios con días y franjas de tiempo
- Consulta de horario para alumno y maestro

### Calificaciones y Kardex
- Captura de notas por el maestro con criterios ponderados (parciales, tareas, exámenes)
- Flujo de aprobación: `PENDIENTE_CAPTURA → CAPTURADA → APROBADA`
- Kardex con historial académico completo por alumno
- Promedios calculados automáticamente

### Títulos Electrónicos
- Generación de e-títulos en XML firmado digitalmente (BouncyCastle + X.509)
- Validación contra el XSD oficial de la DGIET/SEP antes de sellar
- Folio de control único por título
- Estados: `GENERADO → EN_TRAMITE → FIRMADO → EXPEDIDO`
- Descarga del XML oficial y cadena original para auditoría
- Generación por lotes (batch)

### Certificados Electrónicos
- Expedición de certificados de estudios con sello digital
- Cumplimiento de estándares XML/XSD de la SEP
- Vistas previas mediante XSLT antes de la firma

### Evaluación Docente
- Formularios configurables con bloques de preguntas (escala Likert, selección múltiple, texto abierto)
- Respuestas anónimas por período académico
- Generación de informes de desempeño por maestro

### Constancias
- Solicitud por parte del alumno desde su panel
- Flujo: `SOLICITADO → EN_PROCESO → LISTO → ENTREGADO`
- Generación de PDF con logo institucional, datos académicos y firmas autorizadas

### Ciclos y Períodos Académicos
- Gestión de ciclos escolares anuales y períodos (semestres / trimestres)
- Control de fechas y estados: activo, cerrado, archivado

### Personal y Staff
- Registro de personal administrativo y directivo
- Almacén de documentos y cédulas
- Gestión de responsables de firma para documentos oficiales

### Configuración Institucional
- Datos de la institución (nombre, clave, entidad federativa)
- Gestión de certificados X.509 para firma digital
- Orden y autoridades de firma de documentos

---

## API REST — Endpoints

Base URL: `http://localhost:8080/api`  
Autenticación: `Authorization: Bearer <token>`

<details>
<summary><strong>Autenticación — /auth</strong></summary>

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| POST | `/auth/login` | Iniciar sesión | Público |
| POST | `/auth/refresh` | Renovar token | Público |
| POST | `/auth/logout` | Cerrar sesión | Autenticado |
| GET | `/auth/me` | Datos del usuario actual | Autenticado |
| POST | `/auth/change-password` | Cambiar contraseña | Autenticado |

</details>

<details>
<summary><strong>Alumnos — /alumnos</strong></summary>

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/alumnos` | Listar todos |
| GET | `/alumnos/{id}` | Detalles de alumno |
| POST | `/alumnos` | Crear alumno |
| PUT | `/alumnos/{id}` | Actualizar |
| DELETE | `/alumnos/{id}` | Eliminar |
| GET | `/alumnos/me` | Datos del alumno autenticado |
| GET | `/alumnos/{id}/calificaciones` | Calificaciones del alumno |
| GET | `/alumnos/{id}/horarios` | Horario del alumno |
| POST | `/alumnos/cargar-masivo` | Carga masiva desde Excel |

</details>

<details>
<summary><strong>Maestros — /maestros</strong></summary>

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/maestros` | Listar docentes |
| GET | `/maestros/{id}` | Detalles |
| POST | `/maestros` | Crear maestro |
| PUT | `/maestros/{id}` | Actualizar |
| GET | `/maestros/me` | Datos del maestro autenticado |
| GET | `/maestros/{id}/grupos` | Grupos asignados |

</details>

<details>
<summary><strong>Calificaciones — /calificaciones</strong></summary>

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/calificaciones/alumno/{id}` | Notas por alumno |
| GET | `/calificaciones/grupo/{id}` | Notas de grupo completo |
| POST | `/calificaciones` | Capturar calificación |
| PUT | `/calificaciones/{id}` | Modificar |
| PUT | `/calificaciones/{id}/confirmar` | Aprobar (Sec. Académica) |

</details>

<details>
<summary><strong>Títulos y Certificados Electrónicos</strong></summary>

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/titulos-electronicos` | Generar título |
| GET | `/titulos-electronicos/{id}` | Obtener título |
| GET | `/titulos-electronicos/{id}/xml` | Descargar XML firmado |
| PUT | `/titulos-electronicos/{id}/estatus` | Cambiar estatus |
| POST | `/titulos-electronicos/batch` | Generación por lotes |
| POST | `/certificados-electronicos` | Generar certificado |
| GET | `/certificados-electronicos/{id}` | Obtener certificado |
| PUT | `/certificados-electronicos/{id}/estatus` | Cambiar estatus |

</details>

<details>
<summary><strong>Otros módulos</strong></summary>

| Módulo | Base URL |
|---|---|
| Programas educativos | `/programas-educativos` |
| Asignaturas | `/asignaturas` |
| Grupos | `/grupos` |
| Horarios | `/horarios` |
| Kardex | `/kardex` |
| Evaluación docente | `/evaluacion-docente` |
| Constancias | `/solicitudes-constancias` |
| Períodos académicos | `/periodos-academicos` |
| Ciclos escolares | `/ciclos-escolares` |
| Personal | `/personal` |
| Configuración | `/configuracion/institucional` |

</details>

---

## Base de Datos

- **Motor:** PostgreSQL 14+ (producción) / H2 (desarrollo)
- **Migraciones:** Flyway, versiones V1 a V58
- **Tablas principales (~58):** `usuarios`, `alumnos`, `maestros`, `personal`, `programas_educativos`, `asignaturas`, `grupos`, `calificaciones`, `titulos_electronicos`, `certificados_electronicos`, `evaluacion_docente_*`, `periodos_academicos`, `ciclos_escolares`, `refresh_tokens`, `responsables_firma`, `configuracion_institucional`, entre otras.
- **Auditoría:** Columnas `created_date` y `updated_date` en todas las entidades principales.

---

## Seguridad

- **Autenticación:** JWT (JJWT 0.12.3) — stateless, sin sesiones en servidor
- **Contraseñas:** Encriptadas con BCrypt
- **Rate limiting:** 5 intentos fallidos de login bloquean la cuenta por 15 minutos
- **CORS:** Configurado para orígenes permitidos (`localhost:3000`, `5173`, `5500`, dominio de producción)
- **CSRF:** Deshabilitado (API REST stateless)
- **Firma digital:** Certificados X.509 con BouncyCastle para sellar documentos oficiales
- **Validación:** DTOs validados con `@Valid` / Bean Validation en todos los endpoints
- **Permisos en runtime:** `PermisosValidator` verificado en cada controlador por rol y recurso

---

## Equipo

Proyecto trabajado por el **Equipo 1**.

| Integrante | GitHub |
|---|---|
| Victor Hugo Rosado | [@VictorHugoRok](https://github.com/VictorHugoRok) |

---

> Instituto de Especialidades Estomatológicas (IDEE) — Sistema de Control Escolar v1.0

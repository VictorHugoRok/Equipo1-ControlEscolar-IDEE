# Backend IDEE - Control Escolar
## Spring Boot REST API

## 📋 Descripción
Backend completo para el Sistema de Control Escolar del IDEE construido con Spring Boot 3.2.0, Spring Security con JWT, y JPA/Hibernate.

## 🛠️ Tecnologías
- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Security** (JWT Authentication)
- **Spring Data JPA** (Hibernate)
- **MySQL 8.0+**
- **Maven**
- **Lombok**
- **MapStruct**

## 📁 Estructura del Proyecto

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/idee/controlescolar/
│   │   │   ├── model/              # Entidades JPA
│   │   │   │   ├── Usuario.java
│   │   │   │   ├── Alumno.java
│   │   │   │   ├── Maestro.java
│   │   │   │   ├── ProgramaEducativo.java
│   │   │   │   ├── Asignatura.java
│   │   │   │   └── ... (otras entidades)
│   │   │   ├── repository/         # Repositorios JPA
│   │   │   ├── service/            # Lógica de negocio
│   │   │   ├── controller/         # API REST Controllers
│   │   │   ├── dto/                # Data Transfer Objects
│   │   │   ├── security/           # Configuración JWT
│   │   │   ├── config/             # Configuración Spring
│   │   │   ├── exception/          # Manejo de errores
│   │   │   └── util/               # Utilidades
│   │   └── resources/
│   │       ├── application.properties
│   │       └── data.sql (opcional)
│   └── test/
├── pom.xml
└── README.md
```

## 🚀 Instalación y Configuración

### 1. Prerequisitos
```bash
# Java 17
java -version

# Maven 3.6+
mvn -version

# MySQL 8.0+
mysql --version
```

### 2. Configurar Base de Datos

```sql
-- Crear base de datos
CREATE DATABASE idee_control_escolar CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Crear usuario (opcional)
CREATE USER 'idee_user'@'localhost' IDENTIFIED BY 'idee_password';
GRANT ALL PRIVILEGES ON idee_control_escolar.* TO 'idee_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Configurar application.properties

Edita `src/main/resources/application.properties`:

```properties
# Cambiar credenciales de BD según sea necesario
spring.datasource.url=jdbc:mysql://localhost:3306/idee_control_escolar
spring.datasource.username=root
spring.datasource.password=tu_password

# JWT Secret (cambiar en producción)
jwt.secret=TuClaveSecretaSuperSeguraParaProduccion2025
jwt.expiration=86400000
```

### 4. Compilar el Proyecto

```bash
cd backend
mvn clean install
```

### 5. Ejecutar la Aplicación

```bash
mvn spring-boot:run
```

La aplicación estará disponible en: `http://localhost:8080`

## 📊 Modelo de Datos

### Entidades Principales

#### Usuario
- Autenticación y autorización
- Tipos: ALUMNO, MAESTRO, ADMIN, SECRETARIA_ACADEMICA, SECRETARIA_ADMINISTRATIVA

#### Alumno
- Información personal y académica
- Matrícula, calificaciones, documentos
- Relación con programas y solicitudes

#### Maestro
- Información profesional y académica
- Grupos, asignaturas, horarios
- Criterios de evaluación

#### ProgramaEducativo
- Licenciaturas, maestrías, especialidades
- RVOE, créditos, duración
- Asignaturas del plan de estudios

#### Asignatura
- Materias del programa
- Créditos, horas, tipo (obligatoria/optativa)

### Otras Entidades
- **Grupo**: Grupos de estudiantes por asignatura
- **Calificacion**: Calificaciones y evaluaciones
- **HorarioBloque**: Horarios de clases
- **SolicitudConstancia**: Solicitudes de documentos
- **Observacion**: Observaciones del alumno
- **DocumentoAlumno**: Control de documentos entregados
- **CriterioEvaluacion**: Criterios de calificación por maestro

## 🔐 Autenticación y Seguridad

### Endpoints Públicos
```
POST /api/auth/login
POST /api/auth/register
```

### Endpoints Protegidos
Todos los demás endpoints requieren token JWT en el header:
```
Authorization: Bearer <token>
```

### Roles y Permisos

| Rol | Acceso |
|-----|--------|
| ALUMNO | Consultar sus datos, calificaciones, solicitar constancias |
| MAESTRO | Gestionar grupos, capturar calificaciones, criterios |
| SECRETARIA_ACADEMICA | Gestión académica completa, aprobar constancias |
| SECRETARIA_ADMINISTRATIVA | Gestión administrativa, validar documentos |
| ADMIN | Acceso total |

## 📡 API Endpoints

### Autenticación
```
POST   /api/auth/login              # Login
POST   /api/auth/register           # Registro
POST   /api/auth/refresh            # Refresh token
```

### Alumnos
```
GET    /api/alumnos                 # Listar todos
GET    /api/alumnos/{id}            # Ver detalle
POST   /api/alumnos                 # Crear alumno
PUT    /api/alumnos/{id}            # Actualizar
DELETE /api/alumnos/{id}            # Eliminar
GET    /api/alumnos/search          # Búsqueda avanzada
```

### Maestros
```
GET    /api/maestros                # Listar todos
GET    /api/maestros/{id}           # Ver detalle
POST   /api/maestros                # Crear maestro
PUT    /api/maestros/{id}           # Actualizar
```

### Programas
```
GET    /api/programas               # Listar programas
GET    /api/programas/{id}          # Ver detalle
POST   /api/programas               # Crear programa
GET    /api/programas/{id}/asignaturas  # Asignaturas del programa
```

### Calificaciones
```
GET    /api/calificaciones/alumno/{id}     # Calificaciones por alumno
POST   /api/calificaciones                  # Capturar calificaciones
PUT    /api/calificaciones/{id}/confirmar  # Confirmar calificaciones
```

### Horarios
```
GET    /api/horarios/alumno/{id}   # Horario de alumno
GET    /api/horarios/maestro/{id}  # Horario de maestro
POST   /api/horarios/bloque        # Crear bloque de horario
```

### Solicitudes de Constancias
```
GET    /api/solicitudes             # Listar solicitudes
POST   /api/solicitudes             # Nueva solicitud
PUT    /api/solicitudes/{id}/estado # Cambiar estado
```

## 🧪 Testing

```bash
# Ejecutar todos los tests
mvn test

# Ejecutar tests específicos
mvn test -Dtest=UsuarioServiceTest
```

## 📝 Próximos Pasos para Completar

### 1. Crear Repositorios (repository/)
```java
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {
    Optional<Alumno> findByMatricula(String matricula);
    List<Alumno> findByProgramaId(Long programaId);
}
```

### 2. Crear Servicios (service/)
```java
@Service
public class AlumnoService {
    // Lógica de negocio
}
```

### 3. Crear Controladores (controller/)
```java
@RestController
@RequestMapping("/api/alumnos")
public class AlumnoController {
    // Endpoints REST
}
```

### 4. Crear DTOs (dto/)
```java
public class AlumnoDTO {
    // Objeto de transferencia
}
```

### 5. Configurar CORS (config/)
```java
@Configuration
public class CorsConfig {
    // Configuración CORS
}
```

### 6. Configurar Seguridad (security/)
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // Configuración Spring Security
}
```

## 📚 Documentación API

Una vez ejecutando, la documentación Swagger estará disponible en:
```
http://localhost:8080/swagger-ui.html
```

## 🔧 Comandos Útiles

```bash
# Limpiar y compilar
mvn clean package

# Ejecutar sin tests
mvn spring-boot:run -DskipTests

# Ver dependencias
mvn dependency:tree

# Generar JAR
mvn clean package
java -jar target/control-escolar-1.0.0.jar
```

## 🐛 Troubleshooting

### Error de conexión a BD
- Verificar que MySQL esté corriendo
- Revisar credenciales en application.properties
- Verificar que la BD existe

### Error de compilación
- Verificar Java 17
- Limpiar proyecto: `mvn clean`
- Actualizar dependencias: `mvn dependency:resolve`

### Puerto 8080 ocupado
Cambiar puerto en application.properties:
```properties
server.port=8081
```

## 📧 Contacto y Soporte

Para dudas sobre el backend, revisar la documentación de Spring Boot:
- https://spring.io/projects/spring-boot
- https://spring.io/guides

## 📄 Licencia

Proyecto educacional para IDEE - Instituto de Especialidades Estomatológicas

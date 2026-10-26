# Project Assistance Service

Microservicio de gestión de proyectos, equipos y supervisión de asistencia para el rol **Project Manager** del sistema ASTC.

## Stack Tecnológico

- Java 21 + Quarkus 3.x
- Hibernate 6 + Panache
- SmallRye GraphQL
- PostgreSQL
- Redis
- RabbitMQ
- MinIO (S3-compatible)
- JWT (RS256)

## Arquitectura

Hexagonal estricta:

```
project-assistance/
├── application/
│   ├── in/              # Puertos de entrada (interfaces)
│   └── usecase/         # Implementaciones de casos de uso
├── domain/
│   ├── entity/          # Entidades JPA
│   └── model/           # Modelos de dominio, enums, value objects
└── infrastructure/
    ├── controller/graphql/  # Resolvers GraphQL + DTOs
    ├── messaging/           # Transactional Outbox
    ├── repository/          # Repositorios Panache
    ├── security/            # AuthContext (JWT)
    └── storage/             # Adapter S3/MinIO
```

## Responsabilidades

- Supervisar asistencia del equipo de trabajo
- Analizar dashboards por proyecto
- Revisar y exportar información de asistencia
- Gestionar alertas generadas por el sistema
- Aprobar o rechazar justificaciones
- Gestionar proyectos y miembros
- Asignar horarios a usuarios

## Tablas Propias

| Tabla | Descripción |
|-------|-------------|
| `attendances` | Registros de asistencia |
| `absences` | Ausencias registradas |
| `justifications` | Justificaciones de ausencias |
| `project_members` | Relación proyectos-usuarios |
| `alerts` | Alertas del sistema |

### Tablas Externas (NO creadas por este servicio)

- `users` — gestionada por backoffice-service
- `roles` — gestionada por backoffice-service

## Operaciones GraphQL

### Queries

| Nombre | Descripción |
|--------|-------------|
| `getDashboardPM` | Métricas consolidadas del PM |
| `listTeamAttendance` | Historial paginado de asistencia del equipo |
| `listTeamAttendanceHistory` | Historial completo con fotos |
| `exportAttendance` | Exportación CSV de asistencias |
| `listAttendanceAlerts` | Lista paginada de alertas |
| `getAlertDetail` | Detalle de alerta con severidad |
| `listJustificationsPM` | Lista de justificaciones del equipo |
| `getJustificationDetail` | Detalle con historial de cambios |
| `getAllProjects` | Lista de proyectos con miembros |
| `getProjectById` | Detalle de proyecto |
| `getProjectMembers` | Miembros del proyecto |
| `listScheduleOverrides` | Sobrescrituras de horarios |

### Mutations

| Nombre | Descripción |
|--------|-------------|
| `approveAlert` | Aprobar alerta → APPROVED |
| `cancelAlert` | Cancelar alerta → CANCELLED |
| `approveJustification` | Aprobar justificación → APPROVED |
| `rejectJustification` | Rechazar justificación → REJECTED |
| `requestObservation` | Solicitar correcciones → OBSERVATION |
| `createProject` | Crear proyecto con horarios y ubicación |
| `updateProject` | Actualizar datos del proyecto |
| `deleteProject` | Eliminar proyecto |
| `addProjectMember` | Agregar miembro al proyecto |
| `updateProjectMember` | Actualizar rol de miembro |
| `deleteProjectMember` | Eliminar miembro |
| `assignSchedule` | Asignar sobrescritura de horario |
| `deleteSchedule` | Eliminar sobrescritura de horario |

## Ejecución Local

### Con Docker Compose

```bash
cp .env.example .env
docker compose up -d
```

### Sin Docker (desarrollo)

```bash
# Levantar dependencias
docker compose up -d redis rabbitmq minio

# Ejecutar Quarkus en dev mode
./mvnw quarkus:dev
```

Servicios disponibles:
- **API**: http://localhost:8083/graphql
- **GraphQL UI**: http://localhost:8083/q/graphql-ui
- **Health**: http://localhost:8083/q/health

## Configuración JWT

1. Generar par de claves RSA:
```bash
openssl genrsa -out privateKey.pem 2048
openssl rsa -in privateKey.pem -pubout -out publicKey.pem
```

2. Colocar `publicKey.pem` en `src/main/resources/`

3. Generar token de prueba (claims requeridos):
```json
{
  "sub": "PM_USER_UUID",
  "email": "pm@astc.com",
  "role": "PROJECT_MANAGER",
  "groups": ["PROJECT_MANAGER"],
  "iss": "https://astc.auth.example.com",
  "exp": 9999999999
}
```

## Reglas de Negocio

- Solo PROJECT_MANAGER puede acceder a este servicio
- Un PM solo puede ver datos de miembros de sus proyectos
- Si el PM no tiene project_members → fallback: solo ve sus propios datos
- No puede modificar registros de asistencia directamente
- Solo puede aprobar, rechazar o solicitar observación en justificaciones
- Las URLs de documentos son presigned path-style para MinIO

## Eventos de Dominio

- `attendance.registered` — al registrar asistencia
- `absence.created` — al detectar falta automáticamente
- `justification.submitted` — enviada a revisión
- `justification.approved` — aprobada por PM
- `justification.rejected` — rechazada por PM
- `alert.created` — nueva alerta generada

## Integraciones

- **assistance-service**: Datos base de asistencia
- **backoffice-service**: Resolución de nombres de usuario
- **auth-service**: Validación JWT
- **RabbitMQ**: Sistema de eventos
- **MinIO**: Almacenamiento de archivos y evidencias
- **Redis**: Cache de consultas frecuentes

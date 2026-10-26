================================================================================
🧠 MÓDULO PROJECT MANAGER - ESPECIFICACIÓN DE DOMINIO (ENTERPRISE)
================================================================================

📌 RESUMEN DEL MÓDULO
--------------------------------------------------------------------------------
El módulo de Project Manager dentro del servicio de asistencia (assistance-service)
es responsable de la supervisión, control y análisis de la asistencia a nivel de
equipo y proyecto.

Este módulo permite a los usuarios con rol PROJECT_MANAGER gestionar información
del equipo, revisar alertas, aprobar o rechazar procesos y analizar métricas
del proyecto.

--------------------------------------------------------------------------------

Tecnologías:
- Java 21 + Quarkus
- API GraphQL (MicroProfile)
- Arquitectura hexagonal (Ports & Adapters)
- PostgreSQL (base de datos compartida del dominio)
- RabbitMQ (event-driven)
- Redis (opcional para cache)
- MinIO (almacenamiento de archivos)
- JWT (validación desde auth-service)

================================================================================

🎯 RESPONSABILIDADES DEL MÓDULO
--------------------------------------------------------------------------------

Este módulo es responsable de:

✔ Supervisar la asistencia del equipo de trabajo
✔ Analizar dashboards por proyecto
✔ Revisar y exportar información de asistencia
✔ Gestionar alertas generadas por el sistema
✔ Aprobar o rechazar justificaciones
✔ Aprobar o cancelar alertas del sistema

--------------------------------------------------------------------------------

================================================================================
🏗️ ARQUITECTURA INTERNA
================================================================================

El módulo sigue arquitectura hexagonal:

application/
├── in/ (ports de entrada)
├── service/ (casos de uso)

infrastructure/
├── controller/graphql (QueryResolver y MutationResolver)
├── dto (mapeo request/response)
├── security (AuthContext y validación de roles)

--------------------------------------------------------------------------------

Principio clave:
> El módulo no contiene lógica de persistencia directa,
> solo orquesta casos de uso del dominio.

================================================================================

🔐 SEGURIDAD
================================================================================

Autenticación:
- JWT emitido por auth-service (Better Auth)

Rol requerido:
- PROJECT_MANAGER

Reglas de seguridad:

✔ Solo PROJECT_MANAGER puede acceder a este módulo
✔ El userId siempre se obtiene desde AuthContext
✔ No se permite confiar en datos enviados por el cliente
✔ Todo acceso es auditado

Claim del token:
- sub → userId
- role → PROJECT_MANAGER

================================================================================

🌐 API GRAPHQL
================================================================================

Endpoint principal:
/graphql

--------------------------------------------------------------------------------

📊 QUERIES
--------------------------------------------------------------------------------

📍 Dashboard
- GetDashboardPM           (Métricas consolidadas: totalAttendances, totalAbsences, pendingJustifications)
                           Parámetros opcionales: projectId, userId, fromDate, toDate

📍 Asistencia de equipo
- ListTeamAttendance       (Historial paginado con filtros: projectId, userId, status, fechas)
- ListTeamAttendanceHistory (Historial completo con fotos para vista de historial)
- ExportAttendance         (Exportación CSV de asistencias)

📍 Alertas
- ListAttendanceAlerts     (Lista paginada de alertas: tipo, estado, fechas)
- GetAlertDetail           (Detalle de alerta con severidad)

📍 Justificaciones
- ListJustificationsPM     (Lista paginada con filtros: userId, status, fromDate, toDate)
                           Retorna: id, userId, description, documentUrl, status, submittedAt, absenceDate, absenceType
- GetJustificationDetail   (Detalle completo con history[] de cambios de estado)

📍 Proyectos
- GetAllProjects           (Lista de proyectos con miembros)
- GetProjectById           (Detalle de proyecto)
- GetProjectMembers        (Miembros del proyecto)

📍 Horarios
- ListScheduleOverrides    (Sobrescrituras de horarios por usuario)

--------------------------------------------------------------------------------

⚡ MUTATIONS
--------------------------------------------------------------------------------

📍 Alertas
- ApproveAlert             (Aprobar alerta → APPROVED)
- CancelAlert              (Cancelar alerta → CANCELLED)

📍 Justificaciones
- ApproveJustification     (Aprobar justificación → APPROVED, registra history)
- RejectJustification      (Rechazar justificación → REJECTED, registra history)
- RequestObservation       (Solicitar correcciones → OBSERVATION, requiere comment, registra history)

📍 Proyectos
- CreateProject            (Crear proyecto con horarios, ubicación, timezone)
- UpdateProject            (Actualizar datos del proyecto)
- DeleteProject            (Eliminar proyecto)
- AddProjectMember         (Agregar miembro al proyecto)
- UpdateProjectMember      (Actualizar rol de miembro)
- DeleteProjectMember      (Eliminar miembro del proyecto)

📍 Horarios
- AssignSchedule           (Asignar sobrescritura de horario a usuario)
- DeleteSchedule           (Eliminar sobrescritura de horario)

================================================================================

🗄️ BASE DE DATOS (POSTGRESQL + LIQUIBASE)
================================================================================

Propiedades del sistema:
- Base de datos compartida dentro del dominio assistance-service
- Migraciones gestionadas por Liquibase (contenedor: liquibase-project-assistance)
- No existen foreign keys hacia otros microservicios
- user_id y project_id son referencias externas (UUID sin relación directa)

--------------------------------------------------------------------------------

📌 TABLAS DEL DOMINIO
--------------------------------------------------------------------------------

1. attendances
--------------------------------------------------------------------------------
Registro de asistencia de usuarios del sistema.

Campos principales:
- id (UUID)
- user_id (UUID externo)
- project_id (UUID externo)
- date
- check_in / check_out
- status
- latitude / longitude
- photo_url
- created_at / updated_at

--------------------------------------------------------------------------------

2. absences
--------------------------------------------------------------------------------
Registro de ausencias del usuario.

Campos principales:
- id (UUID)
- user_id (UUID externo)
- project_id (UUID externo)
- date
- type
- justified (boolean)

--------------------------------------------------------------------------------

3. justifications
--------------------------------------------------------------------------------
Evidencias enviadas por el usuario para justificar ausencias.

Campos principales:
- id (UUID)
- absence_id (UUID)
- user_id (UUID externo)
- description
- document_url (S3 key, se generan presigned URLs con S3Presigner path-style)
- status (PENDING | SUBMITTED | OBSERVATION | APPROVED | REJECTED)
- comment (comentario del PM)
- submitted_at
- reviewed_at
- reviewed_by

4. project_members (gestión de equipos)
--------------------------------------------------------------------------------
Relación entre proyectos y usuarios con roles.

Campos:
- id (UUID)
- project_id (UUID)
- user_id (UUID externo)
- role (ADMIN | PROJECT_MANAGER | TEAM_MEMBER)
- created_at / updated_at

El PM resuelve los miembros de su equipo consultando project_members:
1. Busca sus propios project_members (userId = PM)
2. Obtiene los projectId de esos registros
3. Busca todos los miembros de esos proyectos
4. Incluye siempre al PM mismo como fallback

5. alerts (alertas del sistema)
--------------------------------------------------------------------------------
Sistema de alertas para supervisión del Project Manager.

Uso:
✔ Incidencias de asistencia
✔ Ausencias no justificadas
✔ Justificaciones pendientes o rechazadas
✔ Alertas generadas por el sistema de IA

Campos principales:
- id (UUID)
- user_id (UUID externo)
- project_id (UUID externo)
- type
- status
- detail
- latitude / longitude
- created_at / updated_at

================================================================================

📡 SISTEMA DE EVENTOS (RABBITMQ)
================================================================================

Exchange:
assistance.events

Eventos relacionados:

- attendance.registered
- absence.created
- justification.submitted
- justification.approved
- justification.rejected
- alert.created

Objetivo:
✔ Comunicación entre microservicios
✔ Notificaciones en tiempo real
✔ Integración con sistema de IA
✔ Auditoría y trazabilidad

================================================================================

🧠 REGLAS DE NEGOCIO
================================================================================

✔ Un Project Manager solo puede ver datos de miembros de sus proyectos (project_members)
✔ Si el PM no tiene project_members → fallback: solo ve sus propios datos
✔ No puede modificar registros de asistencia directamente
✔ Solo puede aprobar, rechazar o solicitar observación en justificaciones
✔ Las alertas representan eventos del sistema, no datos editables
✔ Exportaciones deben respetar filtros de seguridad
✔ Todo acceso es registrado para auditoría
✔ Las justificaciones incluyen absenceDate y absenceType (desde la ausencia asociada)
✔ Los nombres de usuario se resuelven desde backoffice-service (getAllUsers)
✔ Las URLs de documentos son presigned path-style para MinIO (S3Presigner con pathStyleAccessEnabled)
✔ El historial de justificación (history[]) registra cada cambio de estado con: previousStatus, newStatus, comment, changedBy, changedAt

================================================================================

📊 MODELO CONCEPTUAL DEL MÓDULO
================================================================================

El módulo funciona como:

📊 Capa de analítica (Dashboard)
👮 Capa de control (aprobaciones)
📤 Sistema de reportes (exportaciones)
⚠️ Sistema de alertas (monitoreo)

================================================================================

📦 INTEGRACIONES
================================================================================

Este módulo se integra con:

- TEAM_MEMBER module (datos base del sistema)
- auth-service (validación JWT)
- RabbitMQ (eventos del dominio)
- PostgreSQL (persistencia)
- MinIO (archivos y evidencias)
- AI service (detección de fraude en asistencia)

================================================================================

🚀 RESUMEN FINAL
================================================================================

El módulo Project Manager es:

✔ Una capa de supervisión del dominio
✔ Sistema de análisis y reporting
✔ Motor de aprobación y control
✔ Sistema de gestión de alertas
✔ Parte del ecosistema hexagonal del assistance-service

================================================================================
FIN DEL ESPEC
================================================================================
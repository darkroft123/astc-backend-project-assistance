
# GetJustificationDetail

## Descripción
Obtiene el detalle completo de una justificación específica, incluyendo información de la ausencia asociada, datos del usuario, estado actual y datos de revisión. Esta operación permite al Project Manager analizar una solicitud antes de aprobarla o rechazarla.

## Pantalla / Módulo
Detalle de justificación

## Rol de usuario
Project Manager

## Inputs
- id (String, requerido): Identificador único de la justificación

## Campos de respuesta
- id (String): Identificador de la justificación
- absenceId (String): Identificador de la ausencia asociada
- userId (String): Identificador del usuario
- description (String): Descripción de la justificación
- documentUrl (String): URL del documento adjunto
- status (String): Estado actual (PENDING, APPROVED, REJECTED)
- comment (String): Comentario del Project Manager
- submittedAt (String): Fecha de envío
- reviewedAt (String): Fecha de revisión

## Reglas de negocio
- La justificación debe existir en el sistema
- Solo usuarios con rol Project Manager pueden acceder a este detalle
- El estado debe reflejar la última acción realizada
- Si existe `reviewedAt`, significa que ya fue procesada

## Estado vacío
- No aplica estado vacío
- Si no existe la justificación, se debe retornar error

## Manejo de errores
- Error si el usuario no está autenticado
- Error si el usuario no tiene permisos
- Error si el id es inválido
- Error si la justificación no existe
- Error genérico si falla el backend

## Notas para frontend
- Mostrar toda la información en vista detallada
- Incluir botón de aprobar/rechazar si está en estado PENDING
- Mostrar historial o estado visual (badge)
- Manejar correctamente estados ya procesados

## Request
```graphql
query {
  GetJustificationDetail(id: "UUID") {
    id
    absenceId
    userId
    description
    documentUrl
    status
    comment
    submittedAt
    reviewedAt
  }
}
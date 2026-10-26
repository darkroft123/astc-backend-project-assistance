# ApproveJustification

## Descripción
Permite al Project Manager aprobar una justificación registrada por un miembro del equipo. Esta acción cambia el estado de la justificación a "APPROVED", guarda el comentario de revisión y registra la fecha de revisión.

## Pantalla / Módulo
Gestión de justificaciones

## Rol de usuario
Project Manager

## Inputs
- justificationId (String, requerido): Identificador único de la justificación
- comment (String, requerido): Comentario del Project Manager al aprobar la justificación

## Campos de respuesta
- id (String): Identificador único de la justificación
- absenceId (String): Identificador de la ausencia asociada
- userId (String): Identificador del usuario que registró la justificación
- description (String): Descripción ingresada en la justificación
- documentUrl (String): URL del documento adjunto, si existe
- status (String): Estado actualizado de la justificación
- comment (String): Comentario registrado durante la aprobación
- submittedAt (String): Fecha y hora en que la justificación fue enviada
- reviewedAt (String): Fecha y hora en que la justificación fue revisada

## Reglas de negocio
- La justificación debe existir en el sistema
- Solo un usuario con rol Project Manager puede aprobar la justificación
- La justificación debe encontrarse en estado "PENDING" para poder aprobarse
- Al aprobarse, el estado cambia a "APPROVED"
- El comentario ingresado debe guardarse junto con la revisión
- Debe registrarse la fecha y hora de revisión en el campo `reviewedAt`
- La operación puede disparar una notificación al usuario afectado, si la arquitectura actual lo soporta

## Estado vacío
- No aplica estado vacío porque es una operación de actualización
- Si la justificación no existe, se debe retornar error

## Manejo de errores
- Retorna error si el usuario no está autenticado
- Retorna error si el usuario no tiene permisos de Project Manager
- Retorna error si `justificationId` es inválido
- Retorna error si la justificación no existe
- Retorna error si la justificación ya fue aprobada o rechazada previamente
- Retorna error si ocurre una falla en el backend

## Notas para frontend
- Usar esta mutation desde la pantalla de revisión de justificaciones
- Mostrar cuadro de confirmación antes de aprobar
- Permitir ingresar el comentario antes de ejecutar la acción
- Refrescar la lista y el detalle después de aprobar
- Mostrar mensaje de éxito o error según el resultado

## Request
```graphql
mutation {
  ApproveJustification(
    justificationId: "UUID"
    comment: "Justificación aprobada correctamente"
  ) {
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
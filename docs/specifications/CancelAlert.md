# CancelAlert

## Descripción
Permite al Project Manager cancelar una alerta generada en el sistema. Esta acción cambia el estado de la alerta a "CANCELLED", indicando que ya no requiere atención o fue descartada.

## Pantalla / Módulo
Gestión de alertas

## Rol de usuario
Project Manager

## Inputs
- alertId (String, requerido): Identificador único de la alerta

## Campos de respuesta
- id (String): Identificador de la alerta
- type (String): Tipo de alerta
- status (String): Estado actualizado de la alerta
- detail (String): Descripción de la alerta

## Reglas de negocio
- La alerta debe existir en el sistema
- Solo un usuario con rol Project Manager puede cancelar la alerta
- Solo se pueden cancelar alertas que no estén ya canceladas
- Al cancelarse, el estado cambia a "CANCELLED"
- La acción puede registrar auditoría (usuario y fecha)

## Estado vacío
- No aplica (si la alerta no existe, se retorna error)

## Manejo de errores
- Retorna error si el usuario no está autenticado
- Retorna error si el usuario no tiene permisos de Project Manager
- Retorna error si `alertId` es inválido
- Retorna error si la alerta no existe
- Retorna error si la alerta ya fue cancelada
- Retorna error genérico si ocurre una falla en el backend

## Notas para frontend
- Usar en botones de acción dentro del listado de alertas
- Mostrar confirmación antes de cancelar
- Refrescar la lista después de la operación
- Mostrar mensaje de éxito o error

## Request
```graphql
mutation {
  CancelAlert(alertId: "UUID") {
    id
    type
    status
    detail
  }
}
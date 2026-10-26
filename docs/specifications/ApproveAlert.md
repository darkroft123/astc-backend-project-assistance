# ApproveAlert

## Descripción
Permite al Project Manager aprobar una alerta generada en el sistema. Esta acción cambia el estado de la alerta a "APPROVED" y confirma que ha sido revisada.

## Pantalla / Módulo
Gestión de alertas

## Rol de usuario
Project Manager

## Inputs
- alertId (String, requerido): Identificador único de la alerta

## Campos de respuesta
- id (String): Identificador de la alerta
- status (String): Estado actualizado de la alerta

## Reglas de negocio
- La alerta debe existir en el sistema
- Solo se pueden aprobar alertas con estado distinto de APPROVED
- Al aprobarse, el estado cambia a "APPROVED"
- La acción puede registrar auditoría (usuario y fecha)

## Estado vacío
- No aplica (si no existe, se retorna error)

## Manejo de errores
- Error si la alerta no existe
- Error si el usuario no está autenticado
- Error si la alerta ya fue aprobada
- Error genérico si falla el backend

## Notas para frontend
- Usar en botones de acción en la tabla de alertas
- Mostrar confirmación antes de ejecutar
- Refrescar lista luego de aprobar

## Request
```graphql
mutation {
  ApproveAlert(alertId: "UUID") {
    id
    status
  }
}

# RejectJustification

## Descripción
Permite rechazar una justificación. Cambia su estado a "REJECTED" y registra comentario del Project Manager.

## Pantalla / Módulo
Gestión de justificaciones

## Rol de usuario
Project Manager

## Inputs
- justificationId
- comment

## Campos de respuesta
- id
- status
- comment
- reviewedAt

## Reglas de negocio
- Debe existir
- Debe estar en estado PENDING
- Se guarda comentario

## Estado vacío
- No aplica

## Manejo de errores
- Error si no existe
- Error si ya procesada

## Notas para frontend
- Confirmación antes de rechazar

## Request
```graphql
mutation {
  RejectJustification(
    justificationId: "UUID"
    comment: "Rechazado"
  ) {
    id
    status
  }
}
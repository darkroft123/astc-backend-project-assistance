
# GetAlertDetail

## Descripción
Obtiene el detalle de una alerta específica.

## Pantalla / Módulo
Detalle de alerta

## Rol de usuario
Project Manager

## Inputs
- id (String, requerido)

## Campos de respuesta
- id
- type
- status
- detail
- latitude
- longitude
- createdAt

## Reglas de negocio
- La alerta debe existir

## Estado vacío
- No aplica (retorna error si no existe)

## Manejo de errores
- Error si id inválido
- Error si no existe

## Notas para frontend
- Mostrar detalle completo

## Request
```graphql
query {
  GetAlertDetail(id: "UUID") {
    id
    type
    status
    detail
    latitude
    longitude
    createdAt
  }
}
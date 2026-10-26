
# ListTeamAttendance

## Descripción
Lista las asistencias del equipo con filtros y paginación.

## Pantalla / Módulo
Gestión de asistencia

## Rol de usuario
Project Manager

## Inputs
- page (Int, requerido)
- size (Int, requerido)
- projectId (String, opcional)
- userId (String, opcional)
- fromDate (String, opcional)
- toDate (String, opcional)
- status (String, opcional)

## Campos de respuesta
- items (lista de asistencias)
- page
- size
- total

## Reglas de negocio
- Filtrado dinámico según parámetros
- Orden descendente por fecha

## Estado vacío
- Lista vacía

## Manejo de errores
- Error si parámetros inválidos
- Error si el usuario no está autenticado

## Notas para frontend
- Mostrar en tabla
- Implementar paginación
- Permitir filtros

## Request
```graphql
query {
  ListTeamAttendance(
    page: 0
    size: 10
    projectId: null
    userId: null
    fromDate: null
    toDate: null
    status: null
  ) {
    items {
      id
      userId
      projectId
      date
      checkIn
      checkOut
      status
    }
    page
    size
    total
  }
}
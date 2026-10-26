
# ListTeamAttendance

## Descripción
Permite obtener una lista paginada de registros de asistencia del equipo, con múltiples filtros disponibles para análisis detallado por parte del Project Manager.

## Pantalla / Módulo
Gestión de asistencia

## Rol de usuario
Project Manager

## Inputs
- page (Int, requerido): Número de página (base 0)
- size (Int, requerido): Cantidad de registros por página
- projectId (String, opcional): Filtrar por proyecto
- userId (String, opcional): Filtrar por usuario
- fromDate (String, opcional): Fecha de inicio
- toDate (String, opcional): Fecha de fin
- status (String, opcional): Estado de la asistencia

## Campos de respuesta
- items (List): Lista de asistencias
- page (Int): Página actual
- size (Int): Tamaño de página
- total (Int): Total de registros

## Reglas de negocio
- Se deben aplicar todos los filtros enviados
- La paginación debe ser consistente
- Los resultados deben ordenarse por fecha descendente
- No se deben retornar registros fuera del filtro

## Estado vacío
- Retorna lista vacía si no hay resultados
- total = 0

## Manejo de errores
- Error si page o size son inválidos
- Error si el usuario no está autenticado

## Notas para frontend
- Implementar tabla paginada
- Permitir filtros dinámicos
- Mostrar loader mientras carga

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
# ListJustifications

## Descripción
Permite obtener una lista paginada de justificaciones registradas en el sistema, aplicando filtros opcionales por usuario, estado y rango de fechas. Esta operación es utilizada por el Project Manager para revisar solicitudes de justificación, identificar pendientes y tomar decisiones de aprobación o rechazo.

## Pantalla / Módulo
Gestión de justificaciones

## Rol de usuario
Project Manager

## Inputs
- page (Int, requerido): Número de página (base 0)
- size (Int, requerido): Cantidad de registros por página
- userId (String, opcional): Identificador del usuario que generó la justificación
- status (String, opcional): Estado de la justificación (PENDING, APPROVED, REJECTED)
- fromDate (String, opcional): Fecha de inicio del filtro
- toDate (String, opcional): Fecha de fin del filtro

## Campos de respuesta
- items (List): Lista de justificaciones
- page (Int): Página actual
- size (Int): Tamaño de página
- total (Int): Total de registros disponibles

### Estructura de items
- id (String): Identificador de la justificación
- absenceId (String): Identificador de la ausencia asociada
- userId (String): Usuario que registró la justificación
- description (String): Descripción ingresada
- documentUrl (String): URL del documento adjunto
- status (String): Estado actual
- comment (String): Comentario del Project Manager
- submittedAt (String): Fecha de envío
- reviewedAt (String): Fecha de revisión

## Reglas de negocio
- Se deben aplicar todos los filtros enviados en la consulta
- Si se especifica `userId`, solo se retornan justificaciones de ese usuario
- Si se especifica `status`, solo se retornan justificaciones con ese estado
- Si se especifica rango de fechas, se filtra por `submittedAt`
- Los resultados deben ordenarse por fecha de envío descendente
- La paginación debe ser consistente con los parámetros `page` y `size`

## Estado vacío
- Si no existen registros, se retorna:
  - items: []
  - total: 0
- No debe generar error si no hay datos

## Manejo de errores
- Error si `page` o `size` son inválidos
- Error si el usuario no está autenticado
- Error si ocurre una falla en el backend

## Notas para frontend
- Mostrar en tabla paginada
- Permitir filtros por estado y usuario
- Usar colores o badges para representar estados
- Implementar loading y manejo de lista vacía

## Request
```graphql
query {
  ListJustifications(
    page: 0
    size: 10
    userId: null
    status: null
    fromDate: null
    toDate: null
  ) {
    items {
      id
      absenceId
      userId
      description
      status
      comment
      submittedAt
      reviewedAt
    }
    page
    size
    total
  }
}
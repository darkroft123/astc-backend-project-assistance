# GetDashboardPM

## Descripción
Obtiene un resumen consolidado de la información de asistencia, ausencias y justificaciones dentro de un rango de fechas determinado. Esta operación permite al Project Manager visualizar métricas clave del comportamiento del equipo en un periodo específico, facilitando la toma de decisiones y el seguimiento del desempeño.

## Pantalla / Módulo
Dashboard (vista principal del Project Manager)

## Rol de usuario
Project Manager

## Inputs
- projectId (String, opcional): Identificador del proyecto para filtrar los datos
- userId (String, opcional): Identificador del usuario para filtrar los datos
- fromDate (String, opcional): Fecha de inicio del rango en formato ISO (YYYY-MM-DD)
- toDate (String, opcional): Fecha de fin del rango en formato ISO (YYYY-MM-DD)

## Campos de respuesta
- totalAttendances (Int): Número total de asistencias registradas en el periodo
- totalAbsences (Int): Número total de inasistencias registradas
- pendingJustifications (Int): Número de justificaciones en estado pendiente

## Reglas de negocio
- Si se proporciona `projectId`, solo se consideran registros asociados a dicho proyecto
- Si se proporciona `userId`, los resultados se limitan a ese usuario
- Si se proporcionan fechas, los registros deben encontrarse dentro del rango
- Las justificaciones pendientes son aquellas con estado "PENDING"
- Los conteos deben ser consistentes con los datos persistidos en la base de datos

## Estado vacío
- Si no existen registros, todos los valores deben retornar como 0
- El sistema no debe fallar ni lanzar excepciones por ausencia de datos

## Manejo de errores
- Error si el usuario no está autenticado
- Error si el formato de fechas es inválido
- Error si ocurre una falla en la base de datos o en el backend

## Notas para frontend
- Mostrar los valores en tarjetas o widgets de resumen
- Implementar estado de carga mientras se obtiene la información
- Manejar correctamente valores en 0
- Evitar bloquear la UI en caso de ausencia de datos

## Request
```graphql
query {
  GetDashboardPM(
    projectId: null
    userId: null
    fromDate: "2026-01-01"
    toDate: "2026-12-31"
  ) {
    totalAttendances
    totalAbsences
    pendingJustifications
  }
}

# ExportAttendance

## Descripción
Genera un archivo CSV con los datos de asistencia filtrados.

## Pantalla / Módulo
Exportación de reportes

## Rol de usuario
Project Manager

## Inputs
- projectId (String, opcional)
- userId (String, opcional)
- fromDate (String, opcional)
- toDate (String, opcional)
- status (String, opcional)

## Campos de respuesta
- fileName (String)
- content (String CSV)

## Reglas de negocio
- Aplica filtros antes de exportar
- Genera CSV dinámicamente

## Estado vacío
- Retorna archivo vacío

## Manejo de errores
- Error si falla generación

## Notas para frontend
- Botón de exportación
- Descargar archivo

## Request
```graphql
query {
  ExportAttendance(
    projectId: null
    userId: null
    fromDate: null
    toDate: null
    status: null
  ) {
    fileName
    content
  }
}
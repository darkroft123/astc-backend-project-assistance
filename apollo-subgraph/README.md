# SMMS Assistance Apollo Subgraph

Este subgraph se coloca en la raíz del repositorio, al mismo nivel que `pom.xml` y `docker-compose.yml`.

## Estructura

```text
smms-assistance-service/
├── src/
├── pom.xml
├── docker-compose.yml
└── apollo-subgraph/
    ├── package.json
    ├── .env.example
    ├── README.md
    └── src/
        ├── index.js
        ├── schema.js
        ├── client.js
        └── resolvers.js
```

## Pasos

1. Copia esta carpeta como `apollo-subgraph` dentro de tu repo Quarkus.
2. Entra a la carpeta:
   ```bash
   cd apollo-subgraph
   ```
3. Instala dependencias:
   ```bash
   npm install
   ```
4. Crea tu `.env`:
   ```bash
   copy .env.example .env
   ```
5. Arranca el subgraph:
   ```bash
   npm run dev
   ```

## Variables

- `PORT=4010`
- `ASSISTANCE_GRAPHQL_URL=http://localhost:8082/graphql`

## Gateway

En tu gateway Apollo cambia:

```env
ASSISTANCE_URL=http://localhost:4010/
```

## Flujo

```text
Apollo Gateway (4000)
  -> Apollo Subgraph Node (4010)
      -> Quarkus Assistance (8082/graphql)
```

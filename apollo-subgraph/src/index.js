import dns from 'node:dns';
dns.setDefaultResultOrder('ipv4first');
import 'dotenv/config';
import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { typeDefs } from './schema.js';
import { resolvers } from './resolvers.js';


const server = new ApolloServer({
  schema: buildSubgraphSchema({
    typeDefs,
    resolvers,
  }),
});

const { url } = await startStandaloneServer(server, {
  listen: { port: Number(process.env.PORT) || 4010 },
  context: async ({ req }) => ({
    token: req.headers.authorization || '',
  }),
});

console.log(`🚀 Project Manager Apollo subgraph listo en ${url}`);
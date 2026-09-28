// Arranca la API con Postgres en memoria (PGlite) en un puerto fijo, para probar la app Android contra el servidor real:
//   node test/servidor-de-prueba.js
// (No se ejecuta con "npm test": no termina en .test.js)
import { crearApp } from '../src/app.js';
import { prepararEsquema } from '../src/db.js';
import { TOKEN_MOVIL, TOKEN_WEB, crearDbMemoria } from './helpers.js';

const db = await crearDbMemoria();
await prepararEsquema(db);
const app = crearApp({ db, tokens: [TOKEN_MOVIL, TOKEN_WEB] });
await app.listen({ port: Number(process.env.PORT ?? 3999), host: '127.0.0.1' });
console.log(`LISTO ${TOKEN_MOVIL} ${TOKEN_WEB}`);

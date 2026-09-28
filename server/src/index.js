import { crearApp } from './app.js';
import { crearDbPg, prepararEsquema } from './db.js';

const requerida = (nombre) => {
  const valor = process.env[nombre];
  if (!valor) {
    console.error(`Falta la variable de entorno ${nombre}`);
    process.exit(1);
  }
  return valor;
};

const databaseUrl = requerida('DATABASE_URL');
const tokens = requerida('API_TOKENS')
  .split(',')
  .map((t) => t.trim())
  .filter(Boolean);

// Un token corto se puede adivinar por fuerza bruta: se exige un mínimo razonable
if (tokens.length === 0 || tokens.some((t) => t.length < 24)) {
  console.error('API_TOKENS debe contener uno o más tokens (separados por comas) de al menos 24 caracteres cada uno.');
  process.exit(1);
}

const db = crearDbPg(databaseUrl, { ssl: process.env.DATABASE_SSL === 'true' });
await prepararEsquema(db);

const app = crearApp({
  db,
  tokens,
  corsOrigin: process.env.CORS_ORIGIN ?? '',
  logger: { level: process.env.LOG_LEVEL ?? 'info', redact: ['req.headers.authorization'] },
});

const cerrar = async () => {
  await app.close();
  await db.cerrar();
  process.exit(0);
};
process.on('SIGTERM', cerrar);
process.on('SIGINT', cerrar);

await app.listen({ port: Number(process.env.PORT ?? 3000), host: '0.0.0.0' });

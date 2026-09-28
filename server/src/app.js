import { createHash, timingSafeEqual } from 'node:crypto';
import Fastify from 'fastify';
import { SUBCATEGORIAS } from './catalogo.js';
import { borrarItem, cambiosDesde, guardarItem, listar, nuevoUid, obtener } from './epis.js';

const ID_FOTO = '^[A-Za-z0-9][A-Za-z0-9._-]{5,120}$';
const UUID = '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';
const idFotoRegex = new RegExp(ID_FOTO);

const esquemaItem = {
  type: 'object',
  required: ['parteCuerpo', 'nombreEpi'],
  properties: {
    uid: { type: 'string', pattern: UUID },
    baseRevision: { type: 'integer', minimum: 0 },
    eliminado: { type: 'boolean' },
    parteCuerpo: { type: 'string', minLength: 1, maxLength: 100 },
    subcategoria: { type: 'string', maxLength: 100 },
    nombreEpi: { type: 'string', minLength: 1, maxLength: 500 },
    marca: { type: 'string', maxLength: 300 },
    modelo: { type: 'string', maxLength: 300 },
    normativa: { type: 'string', maxLength: 2000 },
    simbolos: { type: 'string', maxLength: 20000 },
    fichaTecnica: { type: 'string', maxLength: 500 },
    distribuidor: { type: 'string', maxLength: 300 },
    observaciones: { type: 'string', maxLength: 5000 },
    fotos: { type: 'array', maxItems: 12, items: { type: 'string', pattern: ID_FOTO } },
    creadoEn: { type: 'integer', minimum: 0 },
  },
};

const hash = (s) => createHash('sha256').update(s).digest();

/**
 * @param db        interfaz de src/db.js
 * @param tokens    tokens válidos (uno por cliente: móvil, web…; se pueden revocar quitándolos)
 * @param corsOrigin origen permitido para llamadas desde un navegador (opcional; lo normal es llamar desde el servidor de la web)
 */
export function crearApp({ db, tokens, corsOrigin = '', fotosMaxBytes = 5 * 1024 * 1024, logger = false }) {
  const app = Fastify({ logger, bodyLimit: 2 * 1024 * 1024 });
  const huellasTokens = tokens.map(hash);

  // Las fotos llegan como cuerpo binario image/jpeg
  app.addContentTypeParser('image/jpeg', { parseAs: 'buffer', bodyLimit: fotosMaxBytes }, (_req, cuerpo, hecho) => hecho(null, cuerpo));

  // ---- CORS opcional -------------------------------------------------------------------
  if (corsOrigin) {
    app.addHook('onRequest', async (req, reply) => {
      reply.header('Access-Control-Allow-Origin', corsOrigin);
      reply.header('Vary', 'Origin');
      reply.header('Access-Control-Allow-Headers', 'authorization, content-type');
      reply.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, HEAD, OPTIONS');
      if (req.method === 'OPTIONS') return reply.code(204).send();
    });
  }

  // ---- Autenticación por token (Bearer) -------------------------------------------------
  // Públicos: /health y la lectura de fotos (sus identificadores son aleatorios y no adivinables).
  const esPublica = (req) => {
    const ruta = req.url.split('?')[0];
    if (ruta === '/health') return true;
    return (req.method === 'GET' || req.method === 'HEAD') && ruta.startsWith('/api/fotos/');
  };
  app.addHook('onRequest', async (req, reply) => {
    if (req.method === 'OPTIONS' || esPublica(req)) return;
    const cabecera = req.headers.authorization ?? '';
    const token = cabecera.startsWith('Bearer ') ? cabecera.slice(7) : '';
    const h = hash(token);
    const valido = huellasTokens.some((t) => timingSafeEqual(t, h));
    if (!valido) return reply.code(401).send({ error: 'No autorizado' });
  });

  // ---- Salud ----------------------------------------------------------------------------
  app.get('/health', async (_req, reply) => {
    try {
      await db.query('SELECT 1');
      return { estado: 'ok' };
    } catch {
      return reply.code(503).send({ estado: 'sin base de datos' });
    }
  });

  app.get('/api/catalogo', async () => ({ subcategorias: SUBCATEGORIAS }));

  // ---- Sincronización con la app Android ------------------------------------------------
  app.post(
    '/api/sync/push',
    {
      schema: {
        body: {
          type: 'object',
          required: ['items'],
          properties: { items: { type: 'array', maxItems: 100, items: { ...esquemaItem, required: ['uid', 'parteCuerpo', 'nombreEpi'] } } },
        },
      },
    },
    async (req) => {
      const resultados = [];
      for (const item of req.body.items) {
        const r = await db.tx((q) => guardarItem(q, item));
        resultados.push({ uid: item.uid, estado: r.estado, item: r.item });
      }
      return { resultados };
    },
  );

  app.get(
    '/api/sync/pull',
    {
      schema: {
        querystring: {
          type: 'object',
          properties: {
            desde: { type: 'integer', minimum: 0, default: 0 },
            limite: { type: 'integer', minimum: 1, maximum: 500, default: 200 },
          },
        },
      },
    },
    async (req) => cambiosDesde(db, req.query.desde, req.query.limite),
  );

  // ---- Consulta y edición desde la web ---------------------------------------------------
  app.get(
    '/api/epis',
    {
      schema: {
        querystring: {
          type: 'object',
          properties: {
            q: { type: 'string', maxLength: 200 },
            parte: { type: 'string', maxLength: 100 },
            subcategoria: { type: 'string', maxLength: 100 },
            incluirEliminados: { type: 'boolean', default: false },
            limite: { type: 'integer', minimum: 1, maximum: 1000, default: 200 },
            desplazamiento: { type: 'integer', minimum: 0, default: 0 },
          },
        },
      },
    },
    async (req) => listar(db, req.query),
  );

  app.get('/api/epis/:uid', { schema: { params: { type: 'object', properties: { uid: { type: 'string', pattern: UUID } } } } }, async (req, reply) => {
    const item = await obtener(db, req.params.uid);
    return item ?? reply.code(404).send({ error: 'No existe' });
  });

  // Alta desde la web: el servidor asigna el uid
  app.post('/api/epis', { schema: { body: esquemaItem } }, async (req, reply) => {
    const item = { ...req.body, uid: nuevoUid(), eliminado: false };
    const r = await db.tx((q) => guardarItem(q, item));
    return reply.code(201).send(r.item);
  });

  // Edición desde la web: hay que indicar la revisión que se estaba viendo (baseRevision)
  app.put(
    '/api/epis/:uid',
    {
      schema: {
        params: { type: 'object', properties: { uid: { type: 'string', pattern: UUID } } },
        body: { ...esquemaItem, required: ['parteCuerpo', 'nombreEpi', 'baseRevision'] },
      },
    },
    async (req, reply) => {
      const existente = await obtener(db, req.params.uid, true);
      if (!existente) return reply.code(404).send({ error: 'No existe' });
      const r = await db.tx((q) => guardarItem(q, { ...req.body, uid: req.params.uid, creadoEn: existente.creadoEn }));
      if (r.estado === 'conflicto') {
        return reply.code(409).send({ error: 'La ficha ha cambiado desde que la abriste; recárgala', item: r.item });
      }
      return r.item;
    },
  );

  app.delete(
    '/api/epis/:uid',
    {
      schema: {
        params: { type: 'object', properties: { uid: { type: 'string', pattern: UUID } } },
        querystring: { type: 'object', required: ['baseRevision'], properties: { baseRevision: { type: 'integer', minimum: 0 } } },
      },
    },
    async (req, reply) => {
      const r = await db.tx((q) => borrarItem(q, req.params.uid, req.query.baseRevision));
      if (r.estado === 'noExiste') return reply.code(404).send({ error: 'No existe' });
      if (r.estado === 'conflicto') return reply.code(409).send({ error: 'La ficha ha cambiado desde que la abriste', item: r.item });
      return r.item;
    },
  );

  // ---- Fotos -----------------------------------------------------------------------------
  const paramsFoto = { type: 'object', properties: { id: { type: 'string', pattern: ID_FOTO } } };

  app.put('/api/fotos/:id', { schema: { params: paramsFoto } }, async (req, reply) => {
    const cuerpo = req.body;
    const esJpeg = Buffer.isBuffer(cuerpo) && cuerpo.length > 4 && cuerpo[0] === 0xff && cuerpo[1] === 0xd8 && cuerpo[2] === 0xff;
    if (!esJpeg) return reply.code(400).send({ error: 'Solo se aceptan imágenes JPEG (Content-Type: image/jpeg)' });
    // Las fotos no cambian: si ya existe, no se sobrescribe
    const insertadas = await db.query('INSERT INTO epi_fotos (id, contenido) VALUES ($1, $2) ON CONFLICT (id) DO NOTHING RETURNING id', [req.params.id, cuerpo]);
    return reply.code(insertadas.length > 0 ? 201 : 200).send({ id: req.params.id });
  });

  // Fastify crea solo el HEAD equivalente a este GET
  app.get('/api/fotos/:id', { schema: { params: paramsFoto } }, async (req, reply) => {
    const [foto] = await db.query('SELECT contenido FROM epi_fotos WHERE id = $1', [req.params.id]);
    if (!foto) return reply.code(404).send({ error: 'No existe' });
    return reply
      .header('Content-Type', 'image/jpeg')
      .header('Cache-Control', 'public, max-age=31536000, immutable')
      .send(Buffer.from(foto.contenido));
  });

  // ---- Errores: nunca se filtran detalles internos ---------------------------------------
  app.setErrorHandler((error, req, reply) => {
    if (error.validation) return reply.code(400).send({ error: 'Datos no válidos', detalle: error.message });
    if (error.statusCode && error.statusCode < 500) return reply.code(error.statusCode).send({ error: error.message });
    req.log.error(error);
    return reply.code(500).send({ error: 'Error interno' });
  });

  return app;
}

export { idFotoRegex };

import { PGlite } from '@electric-sql/pglite';
import { crearApp } from '../src/app.js';
import { prepararEsquema } from '../src/db.js';

export const TOKEN_MOVIL = 'token-movil-0123456789abcdef';
export const TOKEN_WEB = 'token-web-0123456789abcdefgh';

/** Base de datos Postgres real (PGlite, en memoria) con la misma interfaz que usa la API. */
export async function crearDbMemoria() {
  const pg = new PGlite();
  const num = (filas) => filas; // PGlite ya devuelve objetos planos
  return {
    query: async (sql, params) => num((await pg.query(sql, params)).rows),
    exec: (sql) => pg.exec(sql),
    tx: (fn) => pg.transaction((t) => fn(async (sql, params) => num((await t.query(sql, params)).rows))),
    cerrar: () => pg.close(),
  };
}

export async function crearEntorno() {
  const db = await crearDbMemoria();
  await prepararEsquema(db);
  const app = crearApp({ db, tokens: [TOKEN_MOVIL, TOKEN_WEB] });
  await app.ready();

  const llamar = (metodo, url, { token = TOKEN_MOVIL, json, cuerpo, tipo } = {}) =>
    app.inject({
      method: metodo,
      url,
      headers: {
        ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...(tipo ? { 'content-type': tipo } : {}),
      },
      ...(json !== undefined ? { payload: json } : {}),
      ...(cuerpo !== undefined ? { payload: cuerpo } : {}),
    });

  return {
    db,
    app,
    llamar,
    cerrar: async () => {
      await app.close();
      await db.cerrar();
    },
  };
}

let contador = 0;
/** Ficha de prueba con uid nuevo. */
export function ficha(extra = {}) {
  contador += 1;
  const hex = contador.toString(16).padStart(12, '0');
  return {
    uid: `aaaaaaaa-bbbb-4ccc-8ddd-${hex}`,
    baseRevision: 0,
    parteCuerpo: 'Manos y Brazos',
    subcategoria: 'Protección mecánica',
    nombreEpi: `Guantes de protección mecánica ${contador}`,
    marca: 'RECA',
    modelo: `PROTECT ${200 + contador}`,
    normativa: 'EN 388:2016 · CE',
    simbolos: 'EN 388 (3110X)\n- Abrasión: nivel 3',
    fichaTecnica: '',
    distribuidor: '',
    observaciones: 'Talla 9',
    fotos: [],
    creadoEn: 1_700_000_000_000,
    ...extra,
  };
}

// JPEG mínimo válido en cuanto a cabecera (FF D8 FF) para las pruebas de fotos
export const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xd9]);

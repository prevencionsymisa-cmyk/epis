import { readFile } from 'node:fs/promises';
import pg from 'pg';
import { SUBCATEGORIAS } from './catalogo.js';

// int8 (bigint) llega como texto por defecto; las revisiones caben de sobra en un número JS.
pg.types.setTypeParser(20, (v) => Number(v));

/**
 * Interfaz mínima que usa el resto del código (así los tests pueden usar PGlite en lugar de pg):
 *   query(sql, params) -> filas
 *   exec(sql)          -> ejecuta varias sentencias
 *   tx(fn)             -> fn(q) dentro de una transacción; q(sql, params) -> filas
 *   cerrar()
 */
export function crearDbPg(connectionString, { ssl = false } = {}) {
  const pool = new pg.Pool({
    connectionString,
    ssl: ssl ? { rejectUnauthorized: false } : false,
    max: 5,
  });
  return {
    async query(sql, params) {
      return (await pool.query(sql, params)).rows;
    },
    async exec(sql) {
      await pool.query(sql);
    },
    async tx(fn) {
      const cliente = await pool.connect();
      try {
        await cliente.query('BEGIN');
        const resultado = await fn(async (sql, params) => (await cliente.query(sql, params)).rows);
        await cliente.query('COMMIT');
        return resultado;
      } catch (e) {
        await cliente.query('ROLLBACK').catch(() => {});
        throw e;
      } finally {
        cliente.release();
      }
    },
    async cerrar() {
      await pool.end();
    },
  };
}

/** Crea las tablas/triggers (idempotente) y rellena el catálogo de subcategorías. */
export async function prepararEsquema(db) {
  const sql = await readFile(new URL('./schema.sql', import.meta.url), 'utf8');
  await db.exec(sql);
  await db.tx(async (q) => {
    await q('DELETE FROM catalogo_subcategorias');
    for (const [parte, subs] of Object.entries(SUBCATEGORIAS)) {
      for (const [orden, sub] of subs.entries()) {
        await q('INSERT INTO catalogo_subcategorias (parte, subcategoria, orden) VALUES ($1, $2, $3)', [parte, sub, orden]);
      }
    }
  });
}

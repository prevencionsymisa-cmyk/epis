import { randomUUID } from 'node:crypto';

// Campos de texto de una ficha: nombre en JSON (igual que en la app) -> columna en la base de datos.
const CAMPOS_TEXTO = {
  parteCuerpo: 'parte_cuerpo',
  subcategoria: 'subcategoria',
  nombreEpi: 'nombre_epi',
  marca: 'marca',
  modelo: 'modelo',
  normativa: 'normativa',
  simbolos: 'simbolos',
  fichaTecnica: 'ficha_tecnica',
  distribuidor: 'distribuidor',
  observaciones: 'observaciones',
};

const aMs = (fecha) => (fecha instanceof Date ? fecha.getTime() : new Date(fecha).getTime());

/** Deja cada documento solo con { id, nombre } y en ese orden de claves, para poder comparar listas. */
const normalizarDocumentos = (lista) => (lista ?? []).map((d) => ({ id: d.id, nombre: d.nombre }));

/** Fila de la base de datos -> objeto JSON que ven la app y la web. */
export function filaAItem(fila) {
  const item = {
    uid: fila.uid,
    revision: Number(fila.revision),
    eliminado: fila.eliminado,
    fotos: fila.fotos ?? [],
    documentos: normalizarDocumentos(fila.documentos),
    creadoEn: aMs(fila.creado_en),
    actualizadoEn: aMs(fila.actualizado_en),
  };
  for (const [json, columna] of Object.entries(CAMPOS_TEXTO)) item[json] = fila[columna] ?? '';
  return item;
}

/** ¿Los datos que llegan son idénticos a los que ya hay guardados? (reintentos de red sin efecto) */
function mismosDatos(fila, item) {
  if (Boolean(item.eliminado) !== fila.eliminado) return false;
  const fotosA = JSON.stringify(fila.fotos ?? []);
  const fotosB = JSON.stringify(item.fotos ?? []);
  if (fotosA !== fotosB) return false;
  if (item.documentos !== undefined) {
    const docsA = JSON.stringify(normalizarDocumentos(fila.documentos));
    const docsB = JSON.stringify(normalizarDocumentos(item.documentos));
    if (docsA !== docsB) return false;
  }
  return Object.entries(CAMPOS_TEXTO).every(([json, columna]) => (item[json] ?? '') === (fila[columna] ?? ''));
}

/**
 * Valores de las columnas en el orden de COLUMNAS. Si el cliente no manda "documentos" (versiones antiguas
 * de la app o una web que no los gestiona) se conservan los que ya había: así no se borran sin querer.
 */
function valores(item, documentosActuales = []) {
  return [
    ...Object.keys(CAMPOS_TEXTO).map((json) => item[json] ?? ''),
    item.fotos ?? [],
    JSON.stringify(normalizarDocumentos(item.documentos ?? documentosActuales)),
    Boolean(item.eliminado),
  ];
}

const COLUMNAS = [...Object.values(CAMPOS_TEXTO), 'fotos', 'documentos', 'eliminado'];

/**
 * Crea o actualiza una ficha con control de concurrencia optimista:
 *  - Si la ficha no existe se crea.
 *  - Si existe y su revisión coincide con item.baseRevision (lo último que vio quien escribe) se actualiza.
 *  - Si alguien más la cambió entretanto y los datos difieren -> conflicto (se devuelve la versión del servidor).
 *  - Si los datos ya son idénticos (reintento tras perder la respuesta) -> ok sin tocar nada.
 * Devuelve { estado: 'ok' | 'conflicto', item }.
 */
export async function guardarItem(q, item) {
  const uid = item.uid;
  const creadoEn = item.creadoEn ? new Date(item.creadoEn) : new Date();

  // Inserción; si otra petición la creó a la vez (ON CONFLICT) se sigue por la vía de actualización
  const placeholders = COLUMNAS.map((_, i) => `$${i + 3}`).join(', ');
  const insertadas = await q(
    `INSERT INTO epis (uid, creado_en, ${COLUMNAS.join(', ')}) VALUES ($1, $2, ${placeholders})
     ON CONFLICT (uid) DO NOTHING RETURNING *`,
    [uid, creadoEn, ...valores(item)],
  );
  if (insertadas.length > 0) return { estado: 'ok', item: filaAItem(insertadas[0]) };

  const [actual] = await q('SELECT * FROM epis WHERE uid = $1 FOR UPDATE', [uid]);
  if (mismosDatos(actual, item)) return { estado: 'ok', item: filaAItem(actual) };
  if (Number(actual.revision) !== (item.baseRevision ?? 0)) return { estado: 'conflicto', item: filaAItem(actual) };

  const asignaciones = COLUMNAS.map((c, i) => `${c} = $${i + 2}`).join(', ');
  const [nueva] = await q(`UPDATE epis SET ${asignaciones} WHERE uid = $1 RETURNING *`, [uid, ...valores(item, actual.documentos)]);
  return { estado: 'ok', item: filaAItem(nueva) };
}

/** Borrado lógico con la misma comprobación de revisión. Devuelve { estado: 'ok' | 'conflicto' | 'noExiste', item? }. */
export async function borrarItem(q, uid, baseRevision) {
  const [actual] = await q('SELECT * FROM epis WHERE uid = $1 FOR UPDATE', [uid]);
  if (!actual || actual.eliminado) return { estado: 'noExiste' };
  if (Number(actual.revision) !== baseRevision) return { estado: 'conflicto', item: filaAItem(actual) };
  const [borrada] = await q('UPDATE epis SET eliminado = true WHERE uid = $1 RETURNING *', [uid]);
  return { estado: 'ok', item: filaAItem(borrada) };
}

/** Cambios posteriores a una revisión (incluye los borrados), paginados. */
export async function cambiosDesde(db, desde, limite) {
  const filas = await db.query('SELECT * FROM epis WHERE revision > $1 ORDER BY revision LIMIT $2', [desde, limite + 1]);
  const hayMas = filas.length > limite;
  const items = filas.slice(0, limite).map(filaAItem);
  const revision = items.length > 0 ? items[items.length - 1].revision : desde;
  return { items, revision, hayMas };
}

/** Listado para la web, con búsqueda y filtros. */
export async function listar(db, { q, parte, subcategoria, incluirEliminados, limite, desplazamiento }) {
  const condiciones = [];
  const params = [];
  const param = (v) => {
    params.push(v);
    return `$${params.length}`;
  };
  if (!incluirEliminados) condiciones.push('NOT eliminado');
  if (parte) condiciones.push(`parte_cuerpo = ${param(parte)}`);
  if (subcategoria) condiciones.push(`subcategoria = ${param(subcategoria)}`);
  if (q) {
    const p = param(`%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`);
    const columnas = ['nombre_epi', 'marca', 'modelo', 'normativa', 'simbolos', 'ficha_tecnica', 'distribuidor', 'observaciones', 'subcategoria', 'parte_cuerpo'];
    condiciones.push(`(${columnas.map((c) => `${c} ILIKE ${p}`).join(' OR ')})`);
  }
  const donde = condiciones.length > 0 ? `WHERE ${condiciones.join(' AND ')}` : '';
  const [{ total }] = await db.query(`SELECT count(*)::int AS total FROM epis ${donde}`, params);
  const filas = await db.query(
    `SELECT * FROM epis ${donde} ORDER BY parte_cuerpo, nombre_epi LIMIT ${param(limite)} OFFSET ${param(desplazamiento)}`,
    params,
  );
  return { total, items: filas.map(filaAItem) };
}

export async function obtener(db, uid, incluirEliminados = false) {
  const [fila] = await db.query('SELECT * FROM epis WHERE uid = $1', [uid]);
  if (!fila || (fila.eliminado && !incluirEliminados)) return null;
  return filaAItem(fila);
}

export const nuevoUid = () => randomUUID();

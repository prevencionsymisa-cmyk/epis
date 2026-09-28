import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { describe, it } from 'node:test';
import { PARTES_CUERPO, SUBCATEGORIAS, SUBCATEGORIA_OTRA } from '../src/catalogo.js';

// El catálogo del servidor debe ser idéntico al de la app Android; si alguien toca uno y no el otro, este test falla.
const RUTA_KOTLIN = new URL('../../app/src/main/java/com/episcan/app/data/Catalogo.kt', import.meta.url);

const comillas = (texto) => [...texto.matchAll(/"([^"]+)"/g)].map((m) => m[1]);

describe('catálogo', () => {
  it('coincide con Catalogo.kt de la app', async () => {
    const kotlin = await readFile(RUTA_KOTLIN, 'utf8');

    const partes = comillas(kotlin.match(/val PARTES_CUERPO = listOf\(([\s\S]*?)\n\)/)[1]);
    assert.deepEqual(PARTES_CUERPO, partes, 'zonas del cuerpo');

    const bloque = kotlin.match(/linkedMapOf\(([\s\S]*?)\)\.mapValues/)[1];
    const deLaApp = {};
    for (const m of bloque.matchAll(/"([^"]+)" to listOf\(([\s\S]*?)\),/g)) deLaApp[m[1]] = [...comillas(m[2]), SUBCATEGORIA_OTRA];
    assert.deepEqual(SUBCATEGORIAS, deLaApp, 'subcategorías por zona');
    assert.match(kotlin, new RegExp(`SUBCATEGORIA_OTRA = "${SUBCATEGORIA_OTRA}"`));
  });
});

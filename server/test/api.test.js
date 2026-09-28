import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { JPEG, TOKEN_WEB, crearEntorno, ficha } from './helpers.js';

let env;
before(async () => {
  env = await crearEntorno();
});
after(async () => {
  await env.cerrar();
});

const push = (items, opciones) => env.llamar('POST', '/api/sync/push', { json: { items }, ...opciones });
const pull = (desde = 0, limite) => env.llamar('GET', `/api/sync/pull?desde=${desde}${limite ? `&limite=${limite}` : ''}`);

describe('seguridad', () => {
  it('/health es público y responde ok', async () => {
    const r = await env.llamar('GET', '/health', { token: null });
    assert.equal(r.statusCode, 200);
    assert.equal(r.json().estado, 'ok');
  });

  it('sin token o con token incorrecto da 401', async () => {
    assert.equal((await env.llamar('GET', '/api/sync/pull', { token: null })).statusCode, 401);
    assert.equal((await env.llamar('GET', '/api/sync/pull', { token: 'token-inventado-que-no-existe' })).statusCode, 401);
    assert.equal((await env.llamar('GET', '/api/epis', { token: '' })).statusCode, 401);
  });

  it('los dos tokens configurados sirven (uno por cliente)', async () => {
    assert.equal((await env.llamar('GET', '/api/epis', { token: TOKEN_WEB })).statusCode, 200);
  });

  it('un JSON con campos de más o sin nombre se rechaza con 400', async () => {
    const sinNombre = { ...ficha(), nombreEpi: undefined };
    assert.equal((await push([sinNombre])).statusCode, 400);
    assert.equal((await push([ficha({ uid: 'no-es-un-uuid' })])).statusCode, 400);
    assert.equal((await push([ficha({ fotos: ['../../etc/passwd'] })])).statusCode, 400);
  });
});

describe('sincronización móvil <-> servidor', () => {
  it('una ficha nueva se guarda y aparece en el pull con revisión', async () => {
    const f = ficha();
    const r = await push([f]);
    assert.equal(r.statusCode, 200);
    const [res] = r.json().resultados;
    assert.equal(res.estado, 'ok');
    assert.ok(res.item.revision > 0);
    assert.equal(res.item.nombreEpi, f.nombreEpi);
    assert.equal(res.item.creadoEn, f.creadoEn);

    const p = (await pull(0)).json();
    const guardada = p.items.find((i) => i.uid === f.uid);
    assert.ok(guardada);
    assert.equal(guardada.revision, res.item.revision);
  });

  it('actualizar con la revisión correcta funciona y sube la revisión', async () => {
    const f = ficha();
    const rev1 = (await push([f])).json().resultados[0].item.revision;
    const r = (await push([{ ...f, baseRevision: rev1, marca: 'RECA Pro' }])).json().resultados[0];
    assert.equal(r.estado, 'ok');
    assert.equal(r.item.marca, 'RECA Pro');
    assert.ok(r.item.revision > rev1);
  });

  it('si otro cambió la ficha entretanto es un conflicto y se devuelve la versión del servidor', async () => {
    const f = ficha();
    const rev1 = (await push([f])).json().resultados[0].item.revision;
    // La web la edita
    const web = await env.llamar('PUT', `/api/epis/${f.uid}`, { token: TOKEN_WEB, json: { ...f, baseRevision: rev1, marca: 'Editada en la web' } });
    assert.equal(web.statusCode, 200);
    // El móvil, con la revisión antigua, intenta guardar otra cosa
    const r = (await push([{ ...f, baseRevision: rev1, modelo: 'Cambio del móvil' }])).json().resultados[0];
    assert.equal(r.estado, 'conflicto');
    assert.equal(r.item.marca, 'Editada en la web');
    assert.equal(r.item.modelo, f.modelo, 'el cambio del móvil NO se aplicó');
  });

  it('reintentar el mismo envío (se perdió la respuesta) no duplica ni da conflicto', async () => {
    const f = ficha();
    const a = (await push([f])).json().resultados[0];
    const b = (await push([f])).json().resultados[0]; // mismo contenido, baseRevision 0
    assert.equal(b.estado, 'ok');
    assert.equal(b.item.revision, a.item.revision, 'no se creó una revisión nueva');
    const lista = (await env.llamar('GET', '/api/epis?limite=1000')).json();
    assert.equal(lista.items.filter((i) => i.uid === f.uid).length, 1);
  });

  it('un borrado del móvil se propaga como eliminado y la ficha sale de los listados', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;
    const r = (await push([{ ...f, baseRevision: rev, eliminado: true }])).json().resultados[0];
    assert.equal(r.estado, 'ok');
    assert.equal(r.item.eliminado, true);

    const p = (await pull(rev - 1)).json();
    assert.equal(p.items.find((i) => i.uid === f.uid).eliminado, true);
    const lista = (await env.llamar('GET', '/api/epis?limite=1000')).json();
    assert.ok(!lista.items.some((i) => i.uid === f.uid));
    assert.equal((await env.llamar('GET', `/api/epis/${f.uid}`)).statusCode, 404);
  });

  it('el pull solo devuelve lo posterior a la revisión indicada y pagina', async () => {
    const antes = (await pull(0, 500)).json().revision;
    const fichas = [ficha(), ficha(), ficha()];
    await push(fichas);
    const pagina1 = (await pull(antes, 2)).json();
    assert.equal(pagina1.items.length, 2);
    assert.equal(pagina1.hayMas, true);
    const pagina2 = (await pull(pagina1.revision, 2)).json();
    assert.equal(pagina2.items.length, 1);
    assert.equal(pagina2.hayMas, false);
    const uids = [...pagina1.items, ...pagina2.items].map((i) => i.uid).sort();
    assert.deepEqual(uids, fichas.map((f) => f.uid).sort());
    // Sin novedades: devuelve la misma revisión
    const vacia = (await pull(pagina2.revision)).json();
    assert.equal(vacia.items.length, 0);
    assert.equal(vacia.revision, pagina2.revision);
  });

  it('las revisiones son estrictamente crecientes', async () => {
    const revs = [];
    for (let i = 0; i < 5; i++) revs.push((await push([ficha()])).json().resultados[0].item.revision);
    for (let i = 1; i < revs.length; i++) assert.ok(revs[i] > revs[i - 1]);
  });
});

describe('edición desde la web', () => {
  it('lista con búsqueda y filtros por zona y subcategoría', async () => {
    const guantes = ficha({ nombreEpi: 'Guantes búsqueda-unica-xyz', marca: 'ZZMarca' });
    const casco = ficha({ parteCuerpo: 'Cabeza', subcategoria: 'Casco de protección industrial', nombreEpi: 'Casco de obra', marca: 'Delta' });
    await push([guantes, casco]);

    const porTexto = (await env.llamar('GET', '/api/epis?q=busqueda-unica')).json();
    assert.equal(porTexto.total, 0, 'la búsqueda respeta los guiones tal cual (no es difusa)');
    const porTexto2 = (await env.llamar('GET', '/api/epis?q=b%C3%BAsqueda-unica-xyz')).json();
    assert.equal(porTexto2.items[0].uid, guantes.uid);

    const porMarca = (await env.llamar('GET', '/api/epis?q=zzmarca')).json();
    assert.equal(porMarca.total, 1, 'insensible a mayúsculas');

    const porZona = (await env.llamar('GET', `/api/epis?parte=${encodeURIComponent('Cabeza')}&limite=1000`)).json();
    assert.ok(porZona.items.every((i) => i.parteCuerpo === 'Cabeza'));
    assert.ok(porZona.items.some((i) => i.uid === casco.uid));

    const porSub = (await env.llamar('GET', `/api/epis?subcategoria=${encodeURIComponent('Casco de protección industrial')}`)).json();
    assert.ok(porSub.items.every((i) => i.subcategoria === 'Casco de protección industrial'));
  });

  it('la búsqueda trata % y _ como texto, no como comodines', async () => {
    await push([ficha({ nombreEpi: 'Guante 100% algodón' })]);
    const r = (await env.llamar('GET', `/api/epis?q=${encodeURIComponent('100%')}`)).json();
    assert.equal(r.total, 1);
    const todos = (await env.llamar('GET', `/api/epis?q=${encodeURIComponent('%')}`)).json();
    assert.ok(todos.total < (await env.llamar('GET', '/api/epis?limite=1000')).json().total, '"%" no devuelve todo');
  });

  it('crear desde la web asigna uid y el móvil lo recibe en el pull', async () => {
    const r = await env.llamar('POST', '/api/epis', {
      token: TOKEN_WEB,
      json: { parteCuerpo: 'Auditiva', subcategoria: 'Tapones auditivos', nombreEpi: 'Tapones de espuma' },
    });
    assert.equal(r.statusCode, 201);
    const creada = r.json();
    assert.match(creada.uid, /^[0-9a-f-]{36}$/);
    assert.equal(creada.marca, '');
    const p = (await pull(creada.revision - 1)).json();
    assert.ok(p.items.some((i) => i.uid === creada.uid));
  });

  it('editar exige baseRevision y con una revisión antigua da 409 con la versión actual', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;

    const sinBase = await env.llamar('PUT', `/api/epis/${f.uid}`, { token: TOKEN_WEB, json: { parteCuerpo: f.parteCuerpo, nombreEpi: 'X' } });
    assert.equal(sinBase.statusCode, 400);

    const ok = await env.llamar('PUT', `/api/epis/${f.uid}`, { token: TOKEN_WEB, json: { ...f, baseRevision: rev, fichaTecnica: 'FT-1' } });
    assert.equal(ok.statusCode, 200);
    assert.equal(ok.json().fichaTecnica, 'FT-1');
    assert.equal(ok.json().creadoEn, f.creadoEn, 'la fecha de creación no cambia');

    const viejo = await env.llamar('PUT', `/api/epis/${f.uid}`, { token: TOKEN_WEB, json: { ...f, baseRevision: rev, fichaTecnica: 'FT-2' } });
    assert.equal(viejo.statusCode, 409);
    assert.equal(viejo.json().item.fichaTecnica, 'FT-1');

    assert.equal((await env.llamar('PUT', '/api/epis/aaaaaaaa-bbbb-4ccc-8ddd-ffffffffffff', { token: TOKEN_WEB, json: { ...f, baseRevision: 1 } })).statusCode, 404);
  });

  it('borrar desde la web: 409 si la revisión no coincide, y borrado lógico si coincide', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;
    assert.equal((await env.llamar('DELETE', `/api/epis/${f.uid}?baseRevision=${rev + 99}`, { token: TOKEN_WEB })).statusCode, 409);
    const r = await env.llamar('DELETE', `/api/epis/${f.uid}?baseRevision=${rev}`, { token: TOKEN_WEB });
    assert.equal(r.statusCode, 200);
    assert.equal(r.json().eliminado, true);
    assert.equal((await env.llamar('DELETE', `/api/epis/${f.uid}?baseRevision=${r.json().revision}`, { token: TOKEN_WEB })).statusCode, 404);
    const p = (await pull(rev)).json();
    assert.equal(p.items.find((i) => i.uid === f.uid).eliminado, true, 'el móvil se enterará del borrado');
  });

  it('expone el catálogo de subcategorías', async () => {
    const c = (await env.llamar('GET', '/api/catalogo')).json();
    assert.ok(c.subcategorias['Manos y Brazos'].includes('Protección química'));
    assert.equal(c.subcategorias['Cabeza'].at(-1), 'Otra / no clasificada');
  });
});

describe('la base de datos protege la coherencia aunque la web escriba directamente con SQL', () => {
  it('un UPDATE directo recibe nueva revisión y llega a los móviles', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;
    await env.db.query("UPDATE epis SET marca = 'Cambiada por SQL' WHERE uid = $1", [f.uid]);
    const p = (await pull(rev)).json();
    const cambiada = p.items.find((i) => i.uid === f.uid);
    assert.equal(cambiada.marca, 'Cambiada por SQL');
    assert.ok(cambiada.revision > rev);
  });

  it('un INSERT directo (sin revision ni uid) también se numera', async () => {
    const antes = (await pull(0, 500)).json().revision;
    await env.db.query("INSERT INTO epis (parte_cuerpo, nombre_epi) VALUES ('Cabeza', 'Insertada por SQL')");
    const p = (await pull(antes)).json();
    const nueva = p.items.find((i) => i.nombreEpi === 'Insertada por SQL');
    assert.ok(nueva && nueva.revision > antes);
    assert.match(nueva.uid, /^[0-9a-f-]{36}$/);
  });

  it('un DELETE directo se convierte en borrado lógico y los móviles lo reciben', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;
    await env.db.query('DELETE FROM epis WHERE uid = $1', [f.uid]);
    const [fila] = await env.db.query('SELECT eliminado, revision FROM epis WHERE uid = $1', [f.uid]);
    assert.equal(fila.eliminado, true, 'la fila sigue existiendo, marcada como eliminada');
    assert.ok(Number(fila.revision) > rev);
    const p = (await pull(rev)).json();
    assert.equal(p.items.find((i) => i.uid === f.uid).eliminado, true);
  });

  it('la vista epis_activos oculta las eliminadas', async () => {
    const f = ficha();
    const rev = (await push([f])).json().resultados[0].item.revision;
    assert.equal((await env.db.query('SELECT 1 FROM epis_activos WHERE uid = $1', [f.uid])).length, 1);
    await env.llamar('DELETE', `/api/epis/${f.uid}?baseRevision=${rev}`, { token: TOKEN_WEB });
    assert.equal((await env.db.query('SELECT 1 FROM epis_activos WHERE uid = $1', [f.uid])).length, 0);
  });
});

describe('fotos', () => {
  const id = 'epi_0f8e4c2a-1111-4222-8333-444455556666.jpg';

  it('subir exige token y un JPEG de verdad', async () => {
    assert.equal((await env.llamar('PUT', `/api/fotos/${id}`, { token: null, cuerpo: JPEG, tipo: 'image/jpeg' })).statusCode, 401);
    const noEsJpeg = await env.llamar('PUT', `/api/fotos/${id}`, { cuerpo: Buffer.from('<html>no soy una foto</html>'), tipo: 'image/jpeg' });
    assert.equal(noEsJpeg.statusCode, 400);
    const texto = await env.llamar('PUT', `/api/fotos/${id}`, { json: { a: 1 } });
    assert.ok([400, 415].includes(texto.statusCode));
  });

  it('se sube una vez (201), se repite sin error (200) y se lee sin token', async () => {
    const a = await env.llamar('PUT', `/api/fotos/${id}`, { cuerpo: JPEG, tipo: 'image/jpeg' });
    assert.equal(a.statusCode, 201);
    const b = await env.llamar('PUT', `/api/fotos/${id}`, { cuerpo: JPEG, tipo: 'image/jpeg' });
    assert.equal(b.statusCode, 200);

    const lectura = await env.llamar('GET', `/api/fotos/${id}`, { token: null });
    assert.equal(lectura.statusCode, 200);
    assert.equal(lectura.headers['content-type'], 'image/jpeg');
    assert.deepEqual(Buffer.from(lectura.rawPayload), JPEG);

    const cabecera = await env.llamar('HEAD', `/api/fotos/${id}`, { token: null });
    assert.equal(cabecera.statusCode, 200);
    assert.equal((await env.llamar('HEAD', '/api/fotos/epi_no-existe-000000.jpg', { token: null })).statusCode, 404);
  });

  it('identificadores raros se rechazan (no se puede escapar de la ruta)', async () => {
    const r = await env.llamar('GET', '/api/fotos/..%2F..%2Fetc%2Fpasswd', { token: null });
    assert.ok([400, 404].includes(r.statusCode));
  });

  it('una ficha guarda los identificadores de sus fotos', async () => {
    const f = ficha({ fotos: [id, 'epi_segunda-foto-123456.jpg'] });
    const r = (await push([f])).json().resultados[0];
    assert.deepEqual(r.item.fotos, [id, 'epi_segunda-foto-123456.jpg']);
  });
});

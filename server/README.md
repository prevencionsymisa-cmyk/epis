# EPIs API

API de sincronización del catálogo de EPIs. La app Android (offline-first) guarda sus fichas aquí y tu web las lee y las
edita. Node 22 + Fastify + PostgreSQL.

```
Móvil (Room)  ──HTTPS + token──▶  API (este servicio, en Coolify)  ──▶  PostgreSQL (Coolify, red interna)
Web (Vercel)  ──HTTPS + token──▶  API
```

La base de datos **no se expone a internet**: móviles y web hablan solo con la API.

## 1. Desplegar en Coolify

### 1.1 Base de datos
1. En tu proyecto → **+ New → Database → PostgreSQL** (nómbrala `epis-db`) → **Deploy**.
2. Deja **desactivado** *Make it publicly available*.
3. Copia la **Postgres URL (internal)**: `postgres://usuario:clave@epis-db:5432/postgres`.
4. En la pestaña *Backups* de la base de datos programa una copia diaria (Coolify lo hace desde ahí).

### 1.2 La API
1. **+ New → Application → Private/Public Repository** (`prevencionsymisa-cmyk/epis`), rama `main`.
2. **Build Pack: Dockerfile**. **Base Directory: `/server`**. **Port: `3000`**.
3. En *Environment Variables*:

   | Variable | Valor |
   |---|---|
   | `DATABASE_URL` | la Postgres URL (internal) del paso anterior |
   | `API_TOKENS` | tokens separados por comas, de 24+ caracteres, **uno por cliente** (p. ej. uno para los móviles y otro para la web) |

   Genera cada token con `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`.
   Nunca los pongas en el repositorio.
4. **Domains**: `https://api-epis.tudominio.com` (Coolify obtiene el certificado HTTPS solo). El DNS de ese
   subdominio debe apuntar a la IP de tu servidor Coolify (registro **A**). *Un subdominio `*.vercel.app` no sirve para
   esto: no se puede apuntar a otro servidor.*
5. En *Health Check* usa la ruta `/health`. **Deploy**.
6. Comprueba: `https://api-epis.tudominio.com/health` → `{"estado":"ok"}`.

Al arrancar, la API crea las tablas y los triggers sola (es idempotente).

## 2. Conectar los móviles
En la app: **Ajustes → Sincronización con el servidor**: dirección `https://api-epis.tudominio.com`, el token de los
móviles y **Sincronizar ahora**. Después la sincronización es automática al abrir la app y tras cada cambio.

La primera vez sube todo el catálogo del móvil. Si dos móviles catalogan el mismo EPI por separado, en el servidor
quedará duplicado (la detección de duplicados de la app es local a cada móvil).

## 3. Conectar tu web (Vercel)

**Llama a la API desde el servidor de tu web** (route handlers, server actions o server components), no desde el
navegador: así el token queda en las variables de entorno de Vercel (`EPIS_API_URL`, `EPIS_API_TOKEN`; **nunca**
con prefijo `NEXT_PUBLIC_`) y no hace falta CORS.

```ts
// lib/epis.ts  (solo se importa desde código de servidor)
const API = process.env.EPIS_API_URL!;
const TOKEN = process.env.EPIS_API_TOKEN!;

async function api<T>(ruta: string, init: RequestInit = {}): Promise<T> {
  const r = await fetch(`${API}${ruta}`, {
    ...init,
    cache: 'no-store',
    headers: { authorization: `Bearer ${TOKEN}`, 'content-type': 'application/json', ...init.headers },
  });
  if (r.status === 409) throw new ConflictoError((await r.json()).item); // alguien la cambió mientras la editabas
  if (!r.ok) throw new Error(`API ${r.status}`);
  return r.json();
}

export const listarEpis = (q = '', parte = '') =>
  api<{ total: number; items: Epi[] }>(`/api/epis?limite=200&q=${encodeURIComponent(q)}&parte=${encodeURIComponent(parte)}`);

// `epi` debe llevar baseRevision = la `revision` de la ficha tal como la mostraste
export const guardarEpi = (epi: Epi & { baseRevision: number }) =>
  api<Epi>(`/api/epis/${epi.uid}`, { method: 'PUT', body: JSON.stringify(epi) });

export const borrarEpi = (uid: string, revision: number) =>
  api<Epi>(`/api/epis/${uid}?baseRevision=${revision}`, { method: 'DELETE' });

export const catalogo = () => api<{ subcategorias: Record<string, string[]> }>('/api/catalogo'); // para los desplegables

// Las fotos se leen sin token (sus identificadores no son adivinables): <img src={`${API}/api/fotos/${id}`} />
// Igual los PDF adjuntos de cada ficha (epi.documentos = [{ id, nombre }]):
//   <a href={`${API}/api/documentos/${doc.id}?nombre=${encodeURIComponent(doc.nombre)}`} target="_blank">{doc.nombre}</a>
```

Reglas para editar bien desde la web:
- Manda siempre `baseRevision` = la `revision` que tenía la ficha cuando la abrió el usuario. Si otro la cambió entretanto
  la API responde **409** con la versión actual: enséñasela al usuario en lugar de sobrescribir.
- `parteCuerpo` y `subcategoria` deben ser valores de `/api/catalogo` (la app los muestra en desplegables).
- Los borrados son lógicos (`eliminado`): los móviles se enteran en su siguiente sincronización.
- `documentos` (los PDF de la ficha técnica) es opcional al guardar: si no lo mandas, se conservan los que ya tenía
  la ficha. Mándalo solo si quieres cambiar la lista (`[]` los quita todos).

### Alternativa: SQL directo
Si tu web ya se conecta a Postgres, también puede leer/escribir `epis` directamente (solo si la web está en la misma red
de Coolify). La base garantiza la coherencia con triggers: cada `INSERT`/`UPDATE` recibe una `revision` nueva y un `DELETE`
se convierte en borrado lógico. Para leer usa la vista `epis_activos` y para las subcategorías la tabla
`catalogo_subcategorias`. Ojo: desde Vercel la base **no** es accesible (red interna), por eso se recomienda la API.

## 4. Referencia de la API
Todas las rutas `/api/*` (salvo lectura de fotos y documentos) exigen `Authorization: Bearer <token>`.

| Método y ruta | Uso |
|---|---|
| `GET /health` | Estado (público) |
| `POST /api/sync/push` · `GET /api/sync/pull?desde=N` | Sincronización de los móviles |
| `GET /api/epis?q=&parte=&subcategoria=&limite=&desplazamiento=` | Listado con búsqueda (`total` + `items`) |
| `GET /api/epis/:uid` | Una ficha |
| `POST /api/epis` | Crear (el servidor asigna el `uid`) |
| `PUT /api/epis/:uid` | Editar (`baseRevision` obligatorio; 409 si hay conflicto) |
| `DELETE /api/epis/:uid?baseRevision=N` | Borrado lógico |
| `GET /api/catalogo` | Zonas y subcategorías válidas |
| `PUT /api/fotos/:id` (`image/jpeg`) · `GET /api/fotos/:id` | Fotos (la lectura es pública) |
| `PUT /api/documentos/:id` (`application/pdf`, máx. 20 MB) · `GET /api/documentos/:id?nombre=` | PDF adjuntos, p. ej. la ficha técnica del distribuidor (la lectura es pública) |

## 5. Desarrollo y pruebas
```bash
cd server
npm ci
npm test                       # 32 tests contra Postgres real (PGlite, sin instalar nada)
node test/servidor-de-prueba.js   # API en 127.0.0.1:3999 para probar la app Android contra ella
```
`test/catalogo.test.js` comprueba que las subcategorías del servidor coinciden con las de la app
(`Catalogo.kt`): si cambias unas, cambia las otras.

Copias de seguridad, límites de tamaño y monitorización quedan en manos de Coolify. Las fotos y los PDF se guardan en la propia
base de datos (`epi_fotos` y `epi_documentos`), así que una copia de Postgres los incluye. Ojo con el tamaño: cada PDF
puede ocupar varios MB.

// Zonas del cuerpo y subcategorías. DEBE coincidir con app/src/main/java/com/episcan/app/data/Catalogo.kt
// (hay un test que lo comprueba, para que no se desincronicen).

export const SUBCATEGORIA_OTRA = 'Otra / no clasificada';

const POR_ZONA = {
  'Cabeza': [
    'Casco de protección industrial',
    'Casco de montaña / trabajos en altura',
    'Gorro o gorra antigolpes',
    'Casco dieléctrico',
  ],
  'Ojos y Cara': [
    'Gafas de montura universal',
    'Gafas de montura integral',
    'Pantalla facial',
    'Gafas o pantalla de soldadura',
  ],
  'Auditiva': ['Tapones auditivos', 'Orejeras', 'Orejeras acopladas a casco'],
  'Vías Respiratorias': [
    'Mascarilla autofiltrante (FFP)',
    'Media máscara con filtros',
    'Máscara completa con filtros',
    'Equipo de aire suministrado o autónomo',
  ],
  'Manos y Brazos': [
    'Protección mecánica',
    'Protección química',
    'Protección térmica (calor o frío)',
    'Protección dieléctrica',
    'Manguitos y manoplas',
  ],
  'Pies y Piernas': [
    'Calzado de seguridad (con puntera)',
    'Calzado de protección (sin puntera)',
    'Botas impermeables / de agua',
    'Polainas y cubrebotas',
  ],
  'Tronco y Abdomen': [
    'Ropa de alta visibilidad',
    'Ropa de protección química',
    'Ropa ignífuga / soldadura',
    'Delantal de protección',
    'Ropa de protección térmica',
  ],
  'Cuerpo Entero / Caídas': [
    'Arnés anticaídas',
    'Conectores y eslingas',
    'Anticaídas retráctil o deslizante',
    'Línea de vida y anclajes',
    'Cinturón de posicionamiento',
  ],
};

/** { zona: [subcategorías…, 'Otra / no clasificada'] }, en el orden de la app. */
export const SUBCATEGORIAS = Object.fromEntries(
  Object.entries(POR_ZONA).map(([zona, subs]) => [zona, [...subs, SUBCATEGORIA_OTRA]]),
);

export const PARTES_CUERPO = Object.keys(POR_ZONA);

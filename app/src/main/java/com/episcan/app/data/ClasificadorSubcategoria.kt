package com.episcan.app.data

import java.text.Normalizer

/**
 * Asigna una subcategoría a fichas que no la tienen, a partir de su nombre y su normativa.
 * Es un clasificador de reglas (sin IA): rápido, gratuito y predecible. Si no reconoce nada, devuelve null
 * y la ficha se queda sin subcategoría para que el técnico la elija a mano; nunca adivina.
 */
object ClasificadorSubcategoria {
    /**
     * @param generica solo se usa si ninguna regla específica de la zona coincide
     *   (p. ej. "casco" a secas = casco industrial, pero "casco de altura" = casco de montaña).
     * @param prioritaria gana a las demás aunque aparezca más tarde en el texto: es un matiz que cambia
     *   el tipo de equipo ("pantalla de soldadura" no es una pantalla facial cualquiera).
     */
    private class Regla(val subcategoria: String, patron: String, val generica: Boolean = false, val prioritaria: Boolean = false) {
        val patron = Regex(patron)
    }

    private val REGLAS: Map<String, List<Regla>> = mapOf(
        "Cabeza" to listOf(
            Regla("Casco dieléctrico", """dielectric|aislante|electric|\b440\s*v|50365""", prioritaria = true),
            Regla("Casco de montaña / trabajos en altura", """montan|altura|12492|escalada|rescate"""),
            Regla("Gorro o gorra antigolpes", """gorra|gorro|antigolpe|\b812\b"""),
            Regla("Casco de protección industrial", """casco|\b397\b""", generica = true),
        ),
        "Ojos y Cara" to listOf(
            Regla("Gafas o pantalla de soldadura", """soldad|\b(169|175|379)\b""", prioritaria = true),
            Regla("Pantalla facial", """pantalla|facial|careta|visera"""),
            Regla("Gafas de montura integral", """integral|panoram|monogafa"""),
            Regla("Gafas de montura universal", """gafa|universal|\b166\b""", generica = true),
        ),
        "Auditiva" to listOf(
            Regla("Tapones auditivos", """tapon|352\s*-\s*2"""),
            Regla("Orejeras acopladas a casco", """acoplad|352\s*-\s*3"""),
            Regla("Orejeras", """orejera|casco auditivo|352\s*-\s*1|\b352\b""", generica = true),
        ),
        "Vías Respiratorias" to listOf(
            Regla("Equipo de aire suministrado o autónomo", """autonom|aire suministrado|aire comprimido|linea de aire|\b(137|14593|14594|12419)\b""", prioritaria = true),
            Regla("Mascarilla autofiltrante (FFP)", """ffp|autofiltr|filtrante|mascarilla|\b149\b"""),
            Regla("Máscara completa con filtros", """mascara completa|\b136\b"""),
            Regla("Media máscara con filtros", """media mascara|semimascara|\b140\b""", generica = true),
        ),
        "Manos y Brazos" to listOf(
            Regla("Manguitos y manoplas", """manguito|manopla"""),
            Regla("Protección dieléctrica", """dielectric|electric|60903|aislante""", prioritaria = true),
            Regla("Protección química", """quimic|microorganism|\b374\b"""),
            Regla("Protección térmica (calor o frío)", """termic|calor|frio|llama|caliente|criog|\b(407|511|12477)\b"""),
            Regla("Protección mecánica", """mecanic|corte|abrasion|\b388\b"""),
        ),
        "Pies y Piernas" to listOf(
            Regla("Botas impermeables / de agua", """bota[s]? de agua|impermeab|\bpvc\b"""),
            Regla("Polainas y cubrebotas", """polaina|cubrebota|cubrecalzado|cubre bota"""),
            Regla("Calzado de protección (sin puntera)", """sin puntera|\b20347\b|ocupacional""", prioritaria = true),
            Regla("Calzado de seguridad (con puntera)", """seguridad|puntera|\b20345\b|\bs[1-5]\b"""),
        ),
        "Tronco y Abdomen" to listOf(
            Regla("Ropa de alta visibilidad", """visibilidad|reflect|fluorescen|20471"""),
            Regla("Ropa ignífuga / soldadura", """ignifug|soldad|retardante|\b(11611|11612)\b"""),
            Regla("Ropa de protección química", """quimic|\b(14605|13034|14126)\b"""),
            Regla("Delantal de protección", """delantal|mandil"""),
            Regla("Ropa de protección térmica", """termic|frio|frigor|\b(342|14058)\b"""),
        ),
        "Cuerpo Entero / Caídas" to listOf(
            Regla("Arnés anticaídas", """arnes|\b(361|813|12277)\b"""),
            Regla("Conectores y eslingas", """conector|mosqueton|eslinga|absorbedor|amarre|\b(362|355|354)\b"""),
            Regla("Anticaídas retráctil o deslizante", """retractil|deslizante|\b(353|360)\b"""),
            Regla("Línea de vida y anclajes", """linea de vida|anclaje|\b795\b"""),
            Regla("Cinturón de posicionamiento", """cinturon|posicionamiento|\b358\b"""),
        ),
    )

    /** Subcategoría propuesta para una ficha, o null si no hay una regla que encaje. */
    fun clasificar(zona: String, nombre: String, normativa: String): String? {
        val reglas = REGLAS[zona] ?: return null
        // El nombre manda; la normativa solo se consulta si el nombre no dice nada
        return elegir(reglas, plegar(nombre)) ?: elegir(reglas, plegar(normativa))
    }

    /**
     * Entre las reglas que coinciden: primero las prioritarias, luego las específicas, y las genéricas solo si
     * no hay ninguna. Dentro de cada nivel gana la que aparece antes en el texto ("riesgos mecánicos y el frío"
     * es mecánica).
     */
    private fun elegir(reglas: List<Regla>, texto: String): String? {
        if (texto.isBlank()) return null
        val coincidencias = reglas.mapNotNull { r -> r.patron.find(texto)?.let { r to it.range.first } }
        val prioritarias = coincidencias.filter { it.first.prioritaria }
        val especificas = coincidencias.filter { !it.first.generica }
        val candidatas = when {
            prioritarias.isNotEmpty() -> prioritarias
            especificas.isNotEmpty() -> especificas
            else -> coincidencias
        }
        return candidatas.minByOrNull { it.second }?.first?.subcategoria
    }

    /** Minúsculas y sin acentos, para que "Químicos" y "quimicos" coincidan con la misma regla. */
    private fun plegar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Solo para tests: todas las subcategorías que el clasificador puede devolver, por zona. */
    internal fun subcategoriasUsadas(): Map<String, List<String>> = REGLAS.mapValues { (_, rs) -> rs.map { it.subcategoria } }
}

package com.episcan.app.data

/**
 * Una norma o marcado con su significado. [titulo] es la norma (p. ej. "EN 388:2016+A1:2018 (3110X)"),
 * [texto] una explicación en prosa y [puntos] la lista de conceptos (abrasión, corte, sustancias químicas…).
 */
data class BloqueSimbolos(
    val titulo: String?,
    val texto: String?,
    val puntos: List<String>,
)

/**
 * Ordena el texto de "símbolos y significado". La IA a veces lo devuelve como un único párrafo
 * ("EN 388 (3110X): Abrasión nivel 3, Corte nivel 1. EN ISO 374-1 TIPO A: …") y otras ya con líneas y guiones;
 * este analizador entiende ambas formas y lo convierte en bloques legibles, sin perder ni inventar contenido.
 */
object FormatoSimbolos {
    private const val MAX_LARGO_TITULO = 110
    private const val MIN_PUNTOS_PARA_LISTA = 3

    private val MARCADOR_PUNTO = Regex("""^[-•*·–—]\s+""")
    private val DOS_PUNTOS_Y_ESPACIO = Regex(""":\s""")
    /** Fin de frase: punto o punto y coma seguido de una mayúscula (nueva norma o marcado). */
    private val FIN_DE_FRASE = Regex("""(?<=[.;])\s+(?=[A-ZÁÉÍÓÚÜÑ¿¡])""")
    /** "… (Ácido nítrico 65%) y T (Formaldehído 37%)": la "y" que une dos elementos con letra-código. */
    private val Y_ENTRE_ELEMENTOS = Regex("""(?<=\))\s+y\s+(?=\S{1,4}\s*\()""")
    /** Abreviaturas cuyo punto no termina la frase ("CE 0598 Cat. III"). */
    private val ABREVIATURAS = setOf("cat", "nº", "n°", "nro", "art", "sz", "ref", "cód", "cod", "fig", "núm", "num", "apdo", "ud", "uds")

    private class Constructor(var titulo: String? = null, var texto: String? = null, val puntos: MutableList<String> = mutableListOf())

    fun parsear(texto: String): List<BloqueSimbolos> = runCatching { analizar(texto) }
        .getOrElse { if (texto.isBlank()) emptyList() else listOf(BloqueSimbolos(null, texto.trim(), emptyList())) }

    private fun analizar(texto: String): List<BloqueSimbolos> {
        val bloques = mutableListOf<Constructor>()
        var actual: Constructor? = null

        for (linea in texto.replace("\r", "").lines()) {
            val l = linea.trim()
            if (l.isEmpty()) continue

            val marcador = MARCADOR_PUNTO.find(l)
            if (marcador != null) {
                val punto = l.substring(marcador.value.length).trim().trimEnd('.')
                if (punto.isEmpty()) continue
                val bloque = actual ?: Constructor().also { bloques += it; actual = it }
                // Una línea suelta sin ":" seguida de guiones era el título del bloque
                if (bloque.titulo == null && bloque.texto != null && bloque.puntos.isEmpty()) {
                    bloque.titulo = bloque.texto
                    bloque.texto = null
                }
                bloque.puntos += punto
            } else {
                for (frase in dividirFrases(l)) {
                    val bloque = Constructor().also { bloques += it; actual = it }
                    val (titulo, resto) = separarTitulo(frase)
                    bloque.titulo = titulo
                    if (resto.isNotEmpty()) {
                        val lista = dividirEnPuntos(resto)
                        if (lista != null) bloque.puntos += lista else bloque.texto = resto
                    }
                }
            }
        }
        return bloques.map { BloqueSimbolos(it.titulo, it.texto, it.puntos.toList()) }
    }

    private fun dividirFrases(linea: String): List<String> {
        val resultado = mutableListOf<String>()
        for (pieza in FIN_DE_FRASE.split(linea)) {
            if (resultado.isNotEmpty() && terminaEnAbreviatura(resultado.last())) {
                resultado[resultado.lastIndex] = resultado.last() + " " + pieza
            } else {
                resultado += pieza
            }
        }
        return resultado.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun terminaEnAbreviatura(frase: String): Boolean {
        if (!frase.endsWith(".")) return false
        return frase.substringAfterLast(' ').trimEnd('.').lowercase() in ABREVIATURAS
    }

    /** "Título: contenido" → (Título, contenido). Solo cuenta un ":" seguido de espacio (el de "EN 388:2016" no). */
    private fun separarTitulo(frase: String): Pair<String?, String> {
        val indice = DOS_PUNTOS_Y_ESPACIO.find(frase)?.range?.first ?: -1
        if (indice in 1..MAX_LARGO_TITULO) return frase.substring(0, indice).trim() to frase.substring(indice + 1).trim()
        if (frase.endsWith(":")) return frase.dropLast(1).trim() to ""
        return null to frase
    }

    /**
     * Convierte "A nivel 3, B nivel 1, C nivel 0" en una lista, pero solo si claramente lo es
     * (3 o más elementos y casi todos con cifras o paréntesis). Una frase con comas, como
     * "Protección contra bacterias, hongos y virus", se deja como texto.
     */
    private fun dividirEnPuntos(contenido: String): List<String>? {
        val partes = dividirEnNivelSuperior(contenido.trim().trimEnd('.'))
            .map { it.trim().removePrefix("y ").trim() }
            .flatMap { it.split(Y_ENTRE_ELEMENTOS) }
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
        if (partes.size < MIN_PUNTOS_PARA_LISTA) return null
        val estructurados = partes.count { p -> p.any { it.isDigit() } || '(' in p }
        return if (estructurados * 2 > partes.size) partes else null
    }

    /** Parte por comas y puntos y coma que no estén dentro de un paréntesis ni sean un decimal ("0,5"). */
    private fun dividirEnNivelSuperior(texto: String): List<String> {
        val partes = mutableListOf<String>()
        val actual = StringBuilder()
        var profundidad = 0
        texto.forEachIndexed { i, c ->
            when {
                c == '(' -> { profundidad++; actual.append(c) }
                c == ')' -> { profundidad = maxOf(0, profundidad - 1); actual.append(c) }
                (c == ',' || c == ';') && profundidad == 0 && !esDecimal(texto, i) -> {
                    partes += actual.toString(); actual.clear()
                }
                else -> actual.append(c)
            }
        }
        partes += actual.toString()
        return partes
    }

    private fun esDecimal(texto: String, i: Int): Boolean =
        texto[i] == ',' && i > 0 && i < texto.lastIndex && texto[i - 1].isDigit() && texto[i + 1].isDigit()

    /** Texto plano con la misma estructura (para el Excel): título, y debajo un "• " por concepto. */
    fun aTextoPlano(bloques: List<BloqueSimbolos>): String = bloques.joinToString("\n\n") { b ->
        val titulo = b.titulo
        val texto = b.texto
        buildList {
            when {
                titulo != null && texto != null && b.puntos.isEmpty() -> add("$titulo: $texto")
                titulo != null -> {
                    add(titulo)
                    if (texto != null) add(texto)
                }
                texto != null -> add(texto)
            }
            b.puntos.forEach { add("• $it") }
        }.joinToString("\n")
    }
}

package com.uwu.animex.data.api

import com.uwu.animex.data.model.Movie
import kotlin.math.max

object TitleMatcher {
    class Result<T>(
        val best: T?,

        val ranked: List<T>,

        val guess: T? = best,
    )

    private const val MAX_QUERY_LENGTH = 64
    private const val MAX_QUERIES = 7
    private const val MAX_SOURCES = 5
    private const val CONFIDENT_SCORE = 0.9
    private const val CONFIDENT_SIMILARITY = 0.8
    private const val CONFIDENT_MARGIN = 0.08
    private const val MIN_CANDIDATE_SCORE = 0.45
    private const val MAX_CANDIDATES = 8
    private const val GUESS_SCORE = 0.6

    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val NOISE = Regex("(?i)sub(?:title)?\\s+indo(?:nesia)?|\\bbatch\\b|\\bbluray\\b|\\bbd\\b|\\buncensored\\b")
    private val EPISODE = Regex("(?i)\\b(?:episode|eps?)\\s*\\d+(?:\\s*-\\s*\\d+)?\\b")
    private val ORDINAL_SEASON = Regex("(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+(?:season|cour|part)\\b")
    private val SEASON = Regex("(?i)\\b(?:season|musim)\\s*(\\d+)\\b")
    private val PART = Regex("(?i)\\b(?:part|cour)\\s*\\d+\\b")
    private val ROMAN_SEASON = Regex("\\s(II|III|IV|V|VI)$")
    private val ROMANS = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)
    private val SPECIAL_HINT = Regex("(?i)movie|film|ova|oad|special")
    private val NON_SERIES = Regex("(?i)movie|film|ova|oad|special|music")
    private val SPACES = Regex("\\s+")

    private fun normalize(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun strip(title: String): String =
        title
            .replace(BRACKETS, " ")
            .replace(NOISE, " ")
            .replace(EPISODE, " ")
            .replace(ORDINAL_SEASON, " ")
            .replace(SEASON, " ")
            .replace(PART, " ")
            .replace(ROMAN_SEASON, " ")
            .replace(SPACES, " ")
            .trim(' ', ':', '-', '~')

    private fun seasonOf(title: String): Int? {
        val clean = title.replace(BRACKETS, " ").trim()
        ORDINAL_SEASON.find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON.find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return ROMAN_SEASON.find(clean)?.groupValues?.get(1)?.let { ROMANS[it] }
    }

    private fun isLatin(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.count { it.code < 0x250 } * 10 >= letters.length * 8
    }

    private fun dice(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val grams = HashMap<String, Int>()
        for (i in 0 until a.length - 1) grams.merge(a.substring(i, i + 2), 1, Int::plus)
        var hits = 0
        for (i in 0 until b.length - 1) {
            val g = b.substring(i, i + 2)
            val left = grams[g] ?: 0
            if (left > 0) {
                grams[g] = left - 1
                hits++
            }
        }
        return 2.0 * hits / (a.length + b.length - 2)
    }

    private fun similarity(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var s = dice(a, b)
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        if (short.length >= 6 && long.startsWith(short)) {
            s = max(s, 0.9 + 0.1 * short.length / long.length)
        }
        return s
    }

    private fun cut(
        s: String,
        maxLen: Int,
    ): String {
        if (s.length <= maxLen) return s
        val words = s.split(' ')
        val sb = StringBuilder()
        for (w in words) {
            if (sb.length + w.length + (if (sb.isEmpty()) 0 else 1) > maxLen) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
        }
        return sb.toString().ifEmpty { s.take(maxLen) }
    }

    private fun queriesFor(sources: List<String>): List<String> {
        val out = LinkedHashSet<String>()

        for (s in sources) {
            val stripped = strip(s)
            if (stripped.length >= 3) out.add(cut(stripped, MAX_QUERY_LENGTH))
        }
        for (s in sources) {
            val stripped = strip(s)
            val base = stripped.substringBefore(':').trim()
            if (base.length >= 3) out.add(cut(base, MAX_QUERY_LENGTH))
            val words = stripped.split(' ').filter { it.isNotBlank() }
            if (words.size > 4) out.add(words.take(4).joinToString(" "))
            if (words.size > 2) out.add(words.take(2).joinToString(" "))
        }
        return out.filter { it.length >= 3 }.take(MAX_QUERIES)
    }

    private class Scored<T>(
        val item: T,
        val similarity: Double,
        val score: Double,
    )

    private fun <T> scoreItem(
        item: T,
        titles: List<String>,
        type: String?,
        wantedBases: List<String>,
        wantedSeason: Int,
        wantsSpecial: Boolean,
    ): Scored<T> {
        val own = titles.map { it.trim() }.filter { it.isNotEmpty() }
        var best = 0.0
        for (t in own) {
            val tn = normalize(strip(t))
            if (tn.isEmpty()) continue
            for (w in wantedBases) best = max(best, similarity(w, tn))
        }
        val candSeason = own.firstNotNullOfOrNull { seasonOf(it) }
        val seasonAdj =
            when {
                candSeason == null && wantedSeason == 1 -> 0.05
                candSeason == null -> -0.25
                candSeason == wantedSeason -> 0.1
                else -> -0.5
            }
        val formatAdj = if (!wantsSpecial && NON_SERIES.containsMatchIn(type.orEmpty())) -0.15 else 0.0
        return Scored(item, best, best + seasonAdj + formatAdj)
    }

    private fun <T> decide(scored: Collection<Scored<T>>): Result<T> {
        val ranked = scored.sortedByDescending { it.score }
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val confident =
            top != null &&
                top.score >= CONFIDENT_SCORE &&
                top.similarity >= CONFIDENT_SIMILARITY &&
                (second == null || top.score - second.score >= CONFIDENT_MARGIN)
        val best = if (confident) top.item else null
        val guess = best ?: top?.takeIf { it.score >= GUESS_SCORE }?.item
        val shortlist = ranked.filter { it.score >= MIN_CANDIDATE_SCORE }.take(MAX_CANDIDATES).map { it.item }
        return Result(best, shortlist, guess)
    }

    suspend fun <T> match(
        titles: List<String>,
        fetch: suspend (String) -> List<T>,
        idOf: (T) -> String?,
        titlesOf: (T) -> List<String>,
        typeOf: (T) -> String? = { null },
    ): Result<T> {
        val sources =
            titles
                .map { it.trim() }
                .filter { it.length >= 2 && isLatin(it) }
                .distinct()
                .take(MAX_SOURCES)
        if (sources.isEmpty()) return Result(null, emptyList())

        val wantedBases = sources.map { normalize(strip(it)) }.filter { it.length >= 2 }.distinct()
        val wantedSeason = sources.firstNotNullOfOrNull { seasonOf(it) } ?: 1
        val wantsSpecial = SPECIAL_HINT.containsMatchIn(sources.first())

        val scored = LinkedHashMap<String, Scored<T>>()
        for (q in queriesFor(sources)) {
            for (item in fetch(q)) {
                val id = idOf(item) ?: continue
                if (id !in scored) {
                    scored[id] = scoreItem(item, titlesOf(item), typeOf(item), wantedBases, wantedSeason, wantsSpecial)
                }
            }
            val r = decide(scored.values)
            if (r.best != null) return Result(r.best, listOf(r.best), r.best)
        }
        return decide(scored.values)
    }

    suspend fun find(titles: List<String>): Result<Movie> =
        match(
            titles = titles,
            fetch = { q -> runCatching { Api.search(q) }.getOrNull().orEmpty() },
            idOf = { it.id },
            titlesOf = { listOf(it.title.orEmpty()) + it.synonyms.orEmpty().split(',') },
            typeOf = { it.type },
        )

    fun titlesOf(movie: Movie): List<String> =
        listOf(movie.title.orEmpty()) + movie.synonyms.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .take(5)
}

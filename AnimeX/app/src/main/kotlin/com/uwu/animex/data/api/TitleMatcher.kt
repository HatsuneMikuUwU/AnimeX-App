package com.uwu.animex.data.api

import com.uwu.animex.data.model.Movie
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

object TitleMatcher {
    class Result<T>(
        val best: T?,
        val ranked: List<T>,
        val guess: T? = best,
    )

    private const val MIN_TITLE_LENGTH = 3
    private const val MAX_QUERY_LENGTH = 64
    private const val MAX_QUERIES = 9
    private const val MAX_SOURCES = 5
    private const val CONFIDENT_SCORE = 0.9
    private const val CONFIDENT_SIMILARITY = 0.8
    private const val CONFIDENT_MARGIN = 0.08
    private const val EARLY_SIMILARITY = 0.97
    private const val EARLY_QUERY_COUNT = 3
    private const val MIN_CANDIDATE_SCORE = 0.45
    private const val MIN_CANDIDATE_SIMILARITY = 0.55
    private const val MAX_CANDIDATES = 8
    private const val GUESS_SCORE = 0.6
    private const val PARTIAL_MIN_LENGTH = 6
    private const val PARTIAL_BASE = 0.7
    private const val PARTIAL_WEIGHT = 0.2
    private const val SYNONYM_DISCOUNT = 0.03

    private val NOISE =
        Regex(
            "(?i)sub(?:title)?\\s+indo(?:nesia)?|\\b(?:batch|bluray|bd|uncensored|lengkap|tamat|ongoing)\\b",
        )
    private val DUB_SUB = Regex("(?i)\\(?\\b[ds]ub(?:bed)?\\b\\)?")
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val EPISODE = Regex("(?i)\\b(?:episode|eps?)\\s*\\d+(?:\\s*-\\s*\\d+)?\\b")
    private val ORDINAL_SEASON = Regex("(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+season\\b")
    private val WORD_SEASON =
        Regex("(?i)\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)\\s+season\\b")
    private val SEASON = Regex("(?i)\\b(?:season|musim)\\s*(\\d+)\\b")
    private val SHORT_SEASON = Regex("(?i)\\bS(\\d{1,2})\\b")
    private val ORDINAL_PART = Regex("(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+(?:part|cour)\\b")
    private val PART = Regex("(?i)\\b(?:part|cour)\\s*(\\d+)\\b")
    private val ROMAN_SEASON = Regex("\\s(II|III|IV|V|VI)(?=\\s*[:~\\-]|\\s*$)")
    private val TRAILING_NUMBER = Regex("\\s+([2-6])$")
    private val ROMANS = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)
    private val WORD_NUMBERS =
        listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    private val SPECIAL_HINT = Regex("(?i)\\b(?:movie|film|ova|oad|special)\\b")
    private val SPACES = Regex("\\s+")
    private val PUNCT = Regex("[^\\p{L}\\p{N}\\s]")
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val PARTICLES = mapOf("wo" to "o", "ha" to "wa")

    private enum class Kind { MOVIE, TV, TV_SHORT, ONA, OVA, SPECIAL, MUSIC }

    private val SERIES_KINDS = setOf(Kind.TV, Kind.TV_SHORT, Kind.ONA, Kind.OVA)
    private val MOVIE_KINDS = setOf(Kind.MOVIE)
    private val EXTRA_KINDS = setOf(Kind.OVA, Kind.SPECIAL, Kind.ONA)
    private val MUSIC_KINDS = setOf(Kind.MUSIC)

    private val MOVIE_WORD = Regex("(?i)\\b(?:movie|film)\\b")
    private val EXTRA_WORD = Regex("(?i)\\b(?:ova|oad|special)\\b")
    private val MUSIC_WORD = Regex("(?i)\\bmusic\\b")

    private fun unaccent(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(MARKS, "")

    private fun collapseVowels(s: String): String = s.replace("ou", "o").replace("oo", "o").replace("uu", "u")

    /**
     * Comparison key: no accents, no punctuation/spaces, particles spelled one way
     * ("wo" -> "o", "ha" -> "wa") and long vowels collapsed ("Souma" == "Soma").
     */
    private fun normalize(s: String?): String {
        val tokens = unaccent(s.orEmpty()).lowercase().split(NON_ALNUM).filter { it.isNotEmpty() }
        return collapseVowels(tokens.joinToString("") { PARTICLES[it] ?: it })
    }

    private fun plain(s: String): String = s.replace(PUNCT, " ").replace(SPACES, " ").trim()

    private fun exactKey(title: String): String = prepare(title).lowercase()

    private fun prepare(title: String): String =
        title
            .replace(NOISE, " ")
            .replace(DUB_SUB, " ")
            .replace(SPACES, " ")
            .trim()

    private fun strip(title: String): String =
        prepare(title)
            .replace(BRACKETS, " ")
            .replace(EPISODE, " ")
            .replace(ORDINAL_SEASON, " ")
            .replace(WORD_SEASON, " ")
            .replace(SEASON, " ")
            .replace(SHORT_SEASON, " ")
            .replace(ORDINAL_PART, " ")
            .replace(PART, " ")
            .replace(ROMAN_SEASON, " ")
            .replace(SPACES, " ")
            .trim(' ', ':', '-', '~')
            .replace(TRAILING_NUMBER, "")
            .trim(' ', ':', '-', '~')

    private fun coreOf(title: String): String =
        title
            .replace(BRACKETS, " ")
            .replace(EPISODE, " ")
            .replace(ORDINAL_PART, " ")
            .replace(PART, " ")
            .replace(SPACES, " ")
            .trim()

    private fun seasonOf(title: String): Int? {
        val clean = coreOf(title)
        ORDINAL_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        WORD_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.let { WORD_NUMBERS.indexOf(it.lowercase()) + 1 }
            ?.takeIf { it > 0 }
            ?.let { return it }
        SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        SHORT_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        ROMAN_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.let { ROMANS[it] }
            ?.let { return it }
        return TRAILING_NUMBER
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun partOf(title: String): Int? {
        val clean = title.replace(BRACKETS, " ")
        ORDINAL_PART
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        return PART
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun isLatin(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.count { it.code < 0x250 } * 10 >= letters.length * 8
    }

    private fun kindsOfWanted(
        type: String?,
        wantsSpecial: Boolean,
    ): Set<Kind>? {
        val t = type.orEmpty()
        return when {
            t.isBlank() -> if (wantsSpecial) null else SERIES_KINDS
            MUSIC_WORD.containsMatchIn(t) -> MUSIC_KINDS
            MOVIE_WORD.containsMatchIn(t) -> MOVIE_KINDS
            EXTRA_WORD.containsMatchIn(t) -> EXTRA_KINDS
            else -> SERIES_KINDS
        }
    }

    private fun kindOf(type: String?): Kind? {
        val key = type?.trim()?.uppercase()?.replace(' ', '_') ?: return null
        if (key == "TV_SPECIAL") return Kind.SPECIAL
        return Kind.entries.firstOrNull { it.name == key }
    }

    /**
     * 1.0 for identical keys. A key fully contained in the other ("Naruto" in
     * "Naruto Shippuuden") is usually a different entry (sequel, arc, spin-off),
     * so it only gets a reduced score that can never reach "confident" by itself.
     */
    private fun similarity(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        var s = Levenshtein.ratio(a, b) / 100.0
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        if (short.length >= PARTIAL_MIN_LENGTH && Levenshtein.partialRatio(a, b) >= 100) {
            s = max(s, PARTIAL_BASE + PARTIAL_WEIGHT * short.length / long.length)
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

        fun add(q: String) {
            if (q.length >= MIN_TITLE_LENGTH) out.add(cut(q, MAX_QUERY_LENGTH))
        }

        fun variants(s: String) {
            val stripped = strip(s)
            add(stripped.substringBefore(':').trim())
            // Quotes, hyphens and accents often break keyword search on the source.
            val clean = plain(unaccent(stripped))
            add(clean)
            val words = clean.split(' ').filter { it.isNotBlank() }
            // The first word may be spelled differently (e.g. "Himesama" vs "Hime-sama").
            if (words.size > 2) add(words.drop(1).joinToString(" "))
            if (words.size > 4) add(words.take(4).joinToString(" "))
            if (words.size > 2) add(words.take(2).joinToString(" "))
            // Long vowels ("Souma" vs "Soma").
            val lower = clean.lowercase()
            val collapsed = collapseVowels(lower)
            if (collapsed != lower) add(collapsed)
        }

        val first = sources.firstOrNull() ?: return emptyList()
        add(first)
        add(strip(first))
        variants(first)
        for (s in sources.drop(1)) add(strip(s))
        for (s in sources.drop(1)) variants(s)
        return out.take(MAX_QUERIES)
    }

    private class Wanted(
        val exactKeys: Set<String>,
        val bases: List<String>,
        val season: Int,
        val part: Int?,
        val kinds: Set<Kind>?,
        val year: Int?,
    )

    private class Scored<T>(
        val item: T,
        val exact: Boolean,
        val strict: Boolean,
        val similarity: Double,
        val score: Double,
    )

    private fun bestSimilarity(
        title: String,
        wanted: Wanted,
    ): Double {
        val key = normalize(strip(title))
        if (key.isEmpty()) return 0.0
        var sim = 0.0
        for (w in wanted.bases) sim = max(sim, similarity(w, key))
        return sim
    }

    private fun <T> scoreItem(
        item: T,
        titles: List<String>,
        synonyms: List<String>,
        type: String?,
        year: Int?,
        wanted: Wanted,
    ): Scored<T> {
        val own = titles.map { it.trim() }.filter { it.isNotEmpty() }
        val syn = synonyms.map { it.trim() }.filter { it.isNotEmpty() }

        val exact = own.any { exactKey(it) in wanted.exactKeys }

        var sim = if (exact) 1.0 else 0.0
        if (!exact) {
            for (t in own) sim = max(sim, bestSimilarity(t, wanted))
            for (t in syn) sim = max(sim, bestSimilarity(t, wanted) - SYNONYM_DISCOUNT)
        }

        val candSeason = own.firstNotNullOfOrNull { seasonOf(it) } ?: syn.firstNotNullOfOrNull { seasonOf(it) }
        val seasonAdj =
            when {
                candSeason == null && wanted.season == 1 -> 0.05
                candSeason == null -> -0.25
                candSeason == wanted.season -> 0.1
                else -> -0.5
            }

        val candPart = own.firstNotNullOfOrNull { partOf(it) } ?: syn.firstNotNullOfOrNull { partOf(it) }
        val wantedPart = wanted.part
        val partAdj =
            when {
                wantedPart != null && candPart != null -> if (candPart == wantedPart) 0.05 else -0.4
                wantedPart == null && candPart != null && candPart > 1 -> -0.2
                wantedPart != null && wantedPart > 1 && candPart == null -> -0.15
                else -> 0.0
            }

        val candKind = kindOf(type)
        val typeOk = wanted.kinds == null || candKind == null || candKind in wanted.kinds
        val typeAdj = if (typeOk) 0.0 else -0.25

        val diff = if (wanted.year != null && year != null) abs(wanted.year - year) else null
        val yearOk = diff == null || diff == 0
        val yearAdj =
            when {
                diff == null -> 0.0
                diff == 0 -> 0.05
                diff == 1 -> 0.0
                else -> -0.3
            }

        val strict = exact && typeOk && yearOk && seasonAdj > -0.5 && partAdj > -0.4
        return Scored(item, exact, strict, sim, sim + seasonAdj + partAdj + typeAdj + yearAdj)
    }

    private fun <T> decide(scored: Collection<Scored<T>>): Result<T> {
        val ranked = scored.sortedByDescending { it.score }
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)

        val strict = ranked.firstOrNull { it.strict }
        val fuzzyConfident =
            top != null &&
                top.score >= CONFIDENT_SCORE &&
                top.similarity >= CONFIDENT_SIMILARITY &&
                (second == null || top.score - second.score >= CONFIDENT_MARGIN)

        val best = strict?.item ?: top?.takeIf { fuzzyConfident }?.item
        val guess = best ?: top?.takeIf { it.exact || it.score >= GUESS_SCORE }?.item
        val shortlist =
            ranked
                .filter { it.exact || (it.similarity >= MIN_CANDIDATE_SIMILARITY && it.score >= MIN_CANDIDATE_SCORE) }
                .take(MAX_CANDIDATES)
                .map { it.item }
        return Result(best, shortlist, guess)
    }

    suspend fun <T> match(
        titles: List<String>,
        fetch: suspend (String) -> List<T>,
        idOf: (T) -> String?,
        titlesOf: (T) -> List<String>,
        synonymsOf: (T) -> List<String> = { emptyList() },
        typeOf: (T) -> String? = { null },
        yearOf: (T) -> Int? = { null },
        wantedType: String? = null,
        wantedYear: Int? = null,
    ): Result<T> {
        val sources =
            titles
                .map { prepare(it) }
                .filter { it.length >= MIN_TITLE_LENGTH && isLatin(it) }
                .distinctBy { it.lowercase() }
                .take(MAX_SOURCES)
        if (sources.isEmpty()) return Result(null, emptyList())

        val wantsSpecial =
            SPECIAL_HINT.containsMatchIn(sources.first()) ||
                wantedType?.let { EXTRA_WORD.containsMatchIn(it) || MOVIE_WORD.containsMatchIn(it) } == true
        val wanted =
            Wanted(
                exactKeys = sources.map { exactKey(it) }.toSet(),
                bases = sources.map { normalize(strip(it)) }.filter { it.length >= 2 }.distinct(),
                season = sources.firstNotNullOfOrNull { seasonOf(it) } ?: 1,
                part = sources.firstNotNullOfOrNull { partOf(it) },
                kinds = kindsOfWanted(wantedType, wantsSpecial),
                year = wantedYear,
            )

        val scored = LinkedHashMap<String, Scored<T>>()
        for ((index, q) in queriesFor(sources).withIndex()) {
            for (item in fetch(q)) {
                val id = idOf(item) ?: continue
                if (id !in scored) {
                    scored[id] = scoreItem(item, titlesOf(item), synonymsOf(item), typeOf(item), yearOf(item), wanted)
                }
            }
            val best = decide(scored.values).best ?: continue
            val s = scored.values.firstOrNull { it.item === best } ?: continue
            // A strict or near-identical hit is final. A merely fuzzy one gets a few more
            // queries in case an exact entry only shows up for a different search term.
            if (s.strict || s.similarity >= EARLY_SIMILARITY || index + 1 >= EARLY_QUERY_COUNT) {
                return Result(best, listOf(best), best)
            }
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
            yearOf = { yearOf(it) },
        )

    fun yearOf(movie: Movie): Int? =
        movie.year
            ?.filter(Char::isDigit)
            ?.take(4)
            ?.toIntOrNull()
            ?.takeIf { it in 1900..2100 }

    fun titlesOf(movie: Movie): List<String> =
        listOf(movie.title.orEmpty()) + movie.synonyms
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .take(5)
}

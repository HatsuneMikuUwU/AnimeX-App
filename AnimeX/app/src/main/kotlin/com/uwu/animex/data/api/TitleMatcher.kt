package com.uwu.animex.data.api

import com.uwu.animex.data.model.Movie
import kotlin.math.abs
import kotlin.math.max

/**
 * Title matching modelled on CloudStream's `APIHolder.getTracker`:
 *
 *  1. Titles are lower-cased, `dub`/`sub` tags removed, short titles dropped, duplicates removed.
 *  2. The first title is searched as-is (extra, shorter queries are only a fallback).
 *  3. A candidate is a **strict** match when one of its titles equals one of ours (case-insensitive)
 *     AND its type fits AND its year equals ours. The first strict match wins.
 *  4. Otherwise a **less accurate** guess is returned: an exact-title hit, or the best fuzzy match.
 *
 * On top of CloudStream, season markers (`3rd season` == `season 3` == `III`) are normalised, which the
 * CloudStream source itself lists as a known gap, and fuzzy similarity uses [Levenshtein].
 */
object TitleMatcher {
    class Result<T>(
        val best: T?,
        val ranked: List<T>,
        val guess: T? = best,
    )

    private const val MIN_TITLE_LENGTH = 3
    private const val MAX_QUERY_LENGTH = 64
    private const val MAX_QUERIES = 7
    private const val MAX_SOURCES = 5
    private const val CONFIDENT_SCORE = 0.9
    private const val CONFIDENT_SIMILARITY = 0.8
    private const val CONFIDENT_MARGIN = 0.08
    private const val MIN_CANDIDATE_SCORE = 0.45
    private const val MAX_CANDIDATES = 8
    private const val GUESS_SCORE = 0.6
    private const val PARTIAL_MIN_LENGTH = 6

    private val DUB_SUB = Regex("(?i)\\(?\\b[ds]ub(?:bed)?\\b\\)?")
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val NOISE = Regex("(?i)sub(?:title)?\\s+indo(?:nesia)?|\\bbatch\\b|\\bbluray\\b|\\bbd\\b|\\buncensored\\b")
    private val EPISODE = Regex("(?i)\\b(?:episode|eps?)\\s*\\d+(?:\\s*-\\s*\\d+)?\\b")
    private val ORDINAL_SEASON = Regex("(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+(?:season|cour|part)\\b")
    private val SEASON = Regex("(?i)\\b(?:season|musim)\\s*(\\d+)\\b")
    private val PART = Regex("(?i)\\b(?:part|cour)\\s*\\d+\\b")
    private val ROMAN_SEASON = Regex("\\s(II|III|IV|V|VI)$")
    private val ROMANS = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)
    private val SPECIAL_HINT = Regex("(?i)\\b(?:movie|film|ova|oad|special)\\b")
    private val SPACES = Regex("\\s+")

    private enum class Kind { MOVIE, TV, TV_SHORT, ONA, OVA, SPECIAL, MUSIC }

    private val SERIES_KINDS = setOf(Kind.TV, Kind.TV_SHORT, Kind.ONA, Kind.OVA)
    private val MOVIE_KINDS = setOf(Kind.MOVIE)
    private val EXTRA_KINDS = setOf(Kind.OVA, Kind.SPECIAL, Kind.ONA)
    private val MUSIC_KINDS = setOf(Kind.MUSIC)

    private val MOVIE_WORD = Regex("(?i)\\b(?:movie|film)\\b")
    private val EXTRA_WORD = Regex("(?i)\\b(?:ova|oad|special)\\b")
    private val MUSIC_WORD = Regex("(?i)\\bmusic\\b")

    private fun normalize(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /** CloudStream step 1: drop the dub/sub tag, keep everything else. */
    private fun prepare(title: String): String = title.replace(DUB_SUB, " ").replace(SPACES, " ").trim()

    private fun strip(title: String): String =
        prepare(title)
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
        ORDINAL_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        return ROMAN_SEASON
            .find(clean)
            ?.groupValues
            ?.get(1)
            ?.let { ROMANS[it] }
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
        return Kind.entries.firstOrNull { it.name == key }
    }

    /** Levenshtein ratio, with a partial-match floor so "Title" still scores high against "Title: Subtitle". */
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

        // CloudStream searches the first title untouched; do the same before any fallback.
        sources.firstOrNull()?.let { out.add(cut(it, MAX_QUERY_LENGTH)) }
        for (s in sources) {
            val stripped = strip(s)
            if (stripped.length >= MIN_TITLE_LENGTH) out.add(cut(stripped, MAX_QUERY_LENGTH))
        }
        for (s in sources) {
            val stripped = strip(s)
            val base = stripped.substringBefore(':').trim()
            if (base.length >= MIN_TITLE_LENGTH) out.add(cut(base, MAX_QUERY_LENGTH))
            val words = stripped.split(' ').filter { it.isNotBlank() }
            if (words.size > 4) out.add(words.take(4).joinToString(" "))
            if (words.size > 2) out.add(words.take(2).joinToString(" "))
        }
        return out.filter { it.length >= MIN_TITLE_LENGTH }.take(MAX_QUERIES)
    }

    private class Wanted(
        val exactKeys: Set<String>,
        val bases: List<String>,
        val season: Int,
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

    private fun <T> scoreItem(
        item: T,
        titles: List<String>,
        type: String?,
        year: Int?,
        wanted: Wanted,
    ): Scored<T> {
        val own = titles.map { it.trim() }.filter { it.isNotEmpty() }

        // Exact (case-insensitive) title equality, the check CloudStream's strict match is built on.
        val exact = own.any { normalize(prepare(it)) in wanted.exactKeys }

        var sim = if (exact) 1.0 else 0.0
        if (!exact) {
            for (t in own) {
                val tn = normalize(strip(t))
                if (tn.isEmpty()) continue
                for (w in wanted.bases) sim = max(sim, similarity(w, tn))
            }
        }

        val candSeason = own.firstNotNullOfOrNull { seasonOf(it) }
        val seasonAdj =
            when {
                candSeason == null && wanted.season == 1 -> 0.05
                candSeason == null -> -0.25
                candSeason == wanted.season -> 0.1
                else -> -0.5
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

        val strict = exact && typeOk && yearOk && seasonAdj > -0.5
        return Scored(item, exact, strict, sim, sim + seasonAdj + typeAdj + yearAdj)
    }

    private fun <T> decide(scored: Collection<Scored<T>>): Result<T> {
        // sortedByDescending is stable: ties keep provider order, like CloudStream's `find { }`.
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
        // "lessAccurate": an exact title alone is enough, otherwise the fuzzy score must clear the bar.
        val guess = best ?: top?.takeIf { it.exact || it.score >= GUESS_SCORE }?.item
        val shortlist =
            ranked
                .filter { it.exact || it.score >= MIN_CANDIDATE_SCORE }
                .take(MAX_CANDIDATES)
                .map { it.item }
        return Result(best, shortlist, guess)
    }

    /**
     * @param titles titles of the show, first one is searched first (CloudStream: engName, name, japName).
     * @param wantedType our own type ("TV", "Movie", "OVA"...), narrows candidates like `TrackerType`.
     * @param wantedYear our own release year, compared with [yearOf] of each candidate.
     */
    suspend fun <T> match(
        titles: List<String>,
        fetch: suspend (String) -> List<T>,
        idOf: (T) -> String?,
        titlesOf: (T) -> List<String>,
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
                exactKeys = sources.map { normalize(it) }.filter { it.length >= 2 }.toSet(),
                bases = sources.map { normalize(strip(it)) }.filter { it.length >= 2 }.distinct(),
                season = sources.firstNotNullOfOrNull { seasonOf(it) } ?: 1,
                kinds = kindsOfWanted(wantedType, wantsSpecial),
                year = wantedYear,
            )

        val scored = LinkedHashMap<String, Scored<T>>()
        for (q in queriesFor(sources)) {
            for (item in fetch(q)) {
                val id = idOf(item) ?: continue
                if (id !in scored) {
                    scored[id] = scoreItem(item, titlesOf(item), typeOf(item), yearOf(item), wanted)
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

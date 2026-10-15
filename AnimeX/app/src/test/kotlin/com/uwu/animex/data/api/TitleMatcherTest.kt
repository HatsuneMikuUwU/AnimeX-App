package com.uwu.animex.data.api

import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {
    private data class Cand(
        val id: String,
        val titles: List<String>,
        val type: String? = null,
        val year: Int? = null,
    )

    private fun matchOf(
        titles: List<String>,
        candidates: List<Cand>,
        wantedType: String? = null,
        wantedYear: Int? = null,
        queries: MutableList<String> = mutableListOf(),
        fetch: (String) -> List<Cand> = { candidates },
    ): TitleMatcher.Result<Cand> =
        runBlocking {
            TitleMatcher.match(
                titles = titles,
                fetch = { q ->
                    queries += q
                    fetch(q)
                },
                idOf = { it.id },
                titlesOf = { it.titles },
                typeOf = { it.type },
                yearOf = { it.year },
                wantedType = wantedType,
                wantedYear = wantedYear,
            )
        }

    // --- exact title (CloudStream strict match) ---------------------------------------------------

    @Test
    fun exactTitleMatches() {
        val r = matchOf(listOf("Kimetsu no Yaiba"), listOf(Cand("1", listOf("Kimetsu no Yaiba"), "TV", 2019)))
        assertEquals("1", r.best?.id)
    }

    @Test
    fun exactTitleIgnoresCase() {
        val r = matchOf(listOf("KIMETSU NO YAIBA"), listOf(Cand("1", listOf("Kimetsu no Yaiba"), "TV", 2019)))
        assertEquals("1", r.best?.id)
    }

    @Test
    fun dubTagIsRemovedFromOurTitle() {
        val r = matchOf(listOf("Kimetsu no Yaiba (Dub)"), listOf(Cand("1", listOf("Kimetsu no Yaiba"), "TV")))
        assertEquals("1", r.best?.id)
    }

    @Test
    fun titleStartingWithSubIsNotTruncated() {
        val r = matchOf(listOf("Subaru no Bouken"), listOf(Cand("1", listOf("Subaru no Bouken"), "TV")))
        assertEquals("1", r.best?.id)
    }

    @Test
    fun punctuationDifferenceStillResolves() {
        val r =
            matchOf(
                listOf("Re:Zero kara Hajimeru Isekai Seikatsu"),
                listOf(Cand("1", listOf("Re Zero kara Hajimeru Isekai Seikatsu"), "TV")),
            )
        assertEquals("1", r.best?.id)
    }

    @Test
    fun firstStrictMatchWinsInProviderOrder() {
        val r =
            matchOf(
                listOf("Black Clover"),
                listOf(
                    Cand("1", listOf("Black Clover"), "TV", 2017),
                    Cand("2", listOf("Black Clover"), "TV", 2017),
                ),
            )
        assertEquals("1", r.best?.id)
    }

    // --- seasons ----------------------------------------------------------------------------------

    @Test
    fun ordinalSeasonPicksTheRightSeason() {
        val s1 = Cand("1", listOf("Mushoku Tensei: Jobless Reincarnation"), "TV", 2021)
        val s2 = Cand("2", listOf("Mushoku Tensei: Jobless Reincarnation Season 2"), "TV", 2021)
        val r = matchOf(listOf("Mushoku Tensei: Jobless Reincarnation 2nd Season"), listOf(s1, s2))
        assertEquals("2", r.best?.id)
    }

    @Test
    fun arabicSeasonMatchesRomanNumeral() {
        val s1 = Cand("1", listOf("Overlord"), "TV")
        val s3 = Cand("3", listOf("Overlord III"), "TV")
        val r = matchOf(listOf("Overlord Season 3"), listOf(s1, s3))
        assertEquals("3", r.best?.id)
    }

    // --- type and year (CloudStream matchingTypes / matchingYears) ---------------------------------

    @Test
    fun movieIsNotPickedForASeries() {
        val movie = Cand("m", listOf("Fate/stay night"), "MOVIE", 2010)
        val tv = Cand("t", listOf("Fate/stay night"), "TV", 2006)
        val r = matchOf(listOf("Fate/stay night"), listOf(movie, tv), wantedType = "TV")
        assertEquals("t", r.best?.id)
    }

    @Test
    fun typeMismatchAloneIsOnlyAGuess() {
        val movie = Cand("m", listOf("Fate/stay night"), "MOVIE", 2010)
        val r = matchOf(listOf("Fate/stay night"), listOf(movie), wantedType = "TV")
        assertNull(r.best)
        assertEquals("m", r.guess?.id)
    }

    @Test
    fun movieIsPickedWhenWeWantAMovie() {
        val movie = Cand("m", listOf("Kimi no Na wa"), "MOVIE", 2016)
        val tv = Cand("t", listOf("Kimi no Na wa"), "TV", 2016)
        val r = matchOf(listOf("Kimi no Na wa"), listOf(tv, movie), wantedType = "Movie")
        assertEquals("m", r.best?.id)
    }

    @Test
    fun sameYearBeatsAnEarlierEntryWithADifferentYear() {
        val old = Cand("1", listOf("Black Clover"), "TV", 2015)
        val right = Cand("2", listOf("Black Clover"), "TV", 2017)
        val r = matchOf(listOf("Black Clover"), listOf(old, right), wantedYear = 2017)
        assertEquals("2", r.best?.id)
    }

    @Test
    fun oneYearOffStillResolves() {
        val r = matchOf(listOf("Black Clover"), listOf(Cand("1", listOf("Black Clover"), "TV", 2016)), wantedYear = 2017)
        assertEquals("1", r.best?.id)
    }

    @Test
    fun farOffYearIsOnlyAGuess() {
        val r = matchOf(listOf("Black Clover"), listOf(Cand("1", listOf("Black Clover"), "TV", 2010)), wantedYear = 2017)
        assertNull(r.best)
        assertEquals("1", r.guess?.id)
    }

    // --- no match, input filtering, queries --------------------------------------------------------

    @Test
    fun unrelatedTitleGivesNothing() {
        val r = matchOf(listOf("Naruto"), listOf(Cand("1", listOf("Bleach"), "TV")))
        assertNull(r.best)
        assertNull(r.guess)
    }

    @Test
    fun nonLatinTitlesAreIgnoredWithoutSearching() {
        val queries = mutableListOf<String>()
        val r = matchOf(listOf("進撃の巨人"), listOf(Cand("1", listOf("x"))), queries = queries)
        assertNull(r.best)
        assertTrue(r.ranked.isEmpty())
        assertTrue(queries.isEmpty())
    }

    @Test
    fun latinTitleIsUsedWhenMixedWithNonLatin() {
        val r =
            matchOf(
                listOf("進撃の巨人", "Shingeki no Kyojin"),
                listOf(Cand("1", listOf("Shingeki no Kyojin"), "TV")),
            )
        assertEquals("1", r.best?.id)
    }

    @Test
    fun firstTitleIsSearchedAsIs() {
        val queries = mutableListOf<String>()
        matchOf(
            listOf("Kimetsu no Yaiba: Yuukaku-hen"),
            listOf(Cand("1", listOf("Kimetsu no Yaiba: Yuukaku-hen"), "TV")),
            queries = queries,
        )
        assertEquals(listOf("Kimetsu no Yaiba: Yuukaku-hen"), queries)
    }

    @Test
    fun fallsBackToShorterQueryWhenTheFullTitleFindsNothing() {
        val queries = mutableListOf<String>()
        val hit = Cand("1", listOf("Kimetsu no Yaiba: Yuukaku-hen"), "TV")
        val r =
            matchOf(
                listOf("Kimetsu no Yaiba: Yuukaku-hen"),
                listOf(hit),
                queries = queries,
                fetch = { q -> if (q == "Kimetsu no Yaiba") listOf(hit) else emptyList() },
            )
        assertEquals("Kimetsu no Yaiba: Yuukaku-hen", queries.first())
        assertTrue(queries.size > 1)
        assertNotNull(r.best)
    }

    // --- helpers ----------------------------------------------------------------------------------

    @Test
    fun yearOfParsesMovieYear() {
        assertEquals(2019, TitleMatcher.yearOf(Movie(year = "2019")))
        assertEquals(2019, TitleMatcher.yearOf(Movie(year = "2019-2020")))
        assertNull(TitleMatcher.yearOf(Movie(year = "n/a")))
        assertNull(TitleMatcher.yearOf(Movie(year = null)))
    }
}

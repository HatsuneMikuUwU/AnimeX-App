package com.uwu.animex.data.api

import com.apollographql.apollo.api.Optional
import com.uwu.animex.anilist.MediaCharactersByMalIdQuery
import com.uwu.animex.anilist.MediaCharactersBySearchQuery
import com.uwu.animex.anilist.fragment.MediaCharactersFragment
import com.uwu.animex.core.network.AniListApollo
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie

enum class CharacterRole { MAIN, SUPPORTING, BACKGROUND }

data class Person(
    val name: String,
    val image: String?,
)

data class AnimeCharacter(
    val id: Int? = null,
    val character: Person,
    val role: CharacterRole?,
    val voiceActor: Person?,
)

internal object TitleNoise {
    val parenBracket = Regex("\\(.*?\\)|\\[.*?]")
    val subIndo = Regex("(?i)subtitle indonesia|sub indo")
    val whitespace = Regex("\\s+")
}

object CharacterRepo {
    private val cache =
        object : LinkedHashMap<String, List<AnimeCharacter>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AnimeCharacter>>?) = size > 40
        }

    suspend fun load(movie: Movie): List<AnimeCharacter> {
        val key = movie.id ?: return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }

        val result = runSuspendCatching { fetch(movie) }.getOrNull() ?: return emptyList()
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun fetch(movie: Movie): List<AnimeCharacter> {
        Mal.malIdFor(movie.id)?.let { malId ->
            val media = byMalId(malId)
            if (media != null) return parse(media)
        }

        for (q in titleQueries(movie.title.orEmpty())) {
            val media = bySearch(q)
            if (media != null) {
                val list = parse(media)
                if (list.isNotEmpty()) return list
            }
        }
        return emptyList()
    }

    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(TitleNoise.parenBracket, " ")
        val clean = noParen.replace(TitleNoise.subIndo, " ")
        return listOf(title, clean)
            .map { it.replace(TitleNoise.whitespace, " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    private suspend fun byMalId(malId: Int): MediaCharactersFragment? =
        AniListApollo
            .executeOrNull {
                AniListApollo.client.query(MediaCharactersByMalIdQuery(Optional.present(malId))).execute()
            }?.Media
            ?.mediaCharactersFragment

    private suspend fun bySearch(query: String): MediaCharactersFragment? =
        AniListApollo
            .executeOrNull {
                AniListApollo.client.query(MediaCharactersBySearchQuery(Optional.present(query))).execute()
            }?.Media
            ?.mediaCharactersFragment

    private fun parse(media: MediaCharactersFragment): List<AnimeCharacter> =
        media.characters
            ?.edges
            .orEmpty()
            .mapNotNull { edge ->
                edge ?: return@mapNotNull null
                val node = edge.node ?: return@mapNotNull null
                val name = personName(node.name?.userPreferred, node.name?.full, node.name?.native)
                name ?: return@mapNotNull null
                AnimeCharacter(
                    id = node.id,
                    character = Person(name, personImage(node.image?.large, node.image?.medium)),
                    role =
                        when (edge.role?.rawValue) {
                            "MAIN" -> CharacterRole.MAIN
                            "SUPPORTING" -> CharacterRole.SUPPORTING
                            "BACKGROUND" -> CharacterRole.BACKGROUND
                            else -> null
                        },
                    voiceActor =
                        edge.voiceActors.orEmpty().firstNotNullOfOrNull { va ->
                            va ?: return@firstNotNullOfOrNull null
                            val vaName = personName(va.name?.userPreferred, va.name?.full, va.name?.native)
                            vaName ?: return@firstNotNullOfOrNull null
                            Person(vaName, personImage(va.image?.large, va.image?.medium))
                        },
                )
            }

    private fun personName(
        userPreferred: String?,
        full: String?,
        native: String?,
    ): String? = listOf(userPreferred, full, native).firstOrNull { !it.isNullOrBlank() }

    private fun personImage(
        large: String?,
        medium: String?,
    ): String? = listOf(large, medium).firstOrNull { !it.isNullOrBlank() }
}

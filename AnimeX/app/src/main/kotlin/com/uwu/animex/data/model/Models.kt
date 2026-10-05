package com.uwu.animex.data.model

data class Envelope<T>(
    val status: Int? = null,
    val error: Boolean? = null,
    val data: T? = null,
    val message: String? = null,
)

data class Movie(
    val id: String? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val synonyms: String? = null,
    val image_poster: String? = null,
    val image_cover: String? = null,
    val type: String? = null,
    val year: String? = null,
    val status: String? = null,
    val genre: String? = null,
    val studio: String? = null,
    val views: String? = null,
    val favorites: String? = null,
    val aired_start: String? = null,
    val aired_end: String? = null,
    val day: String? = null,
    val time: String? = null,
    val episode_index: String? = null,
    val episode_id: String? = null,
    val episode_title: String? = null,
    val key_time_update: String? = null,
    val season: String? = null,
)

data class Episode(
    val id: String? = null,
    val index: String? = null,
    val title: String? = null,
    val image: String? = null,
    val views: String? = null,
    val key_time: String? = null,
    val id_movie: String? = null,
)

data class Server(
    val id: String? = null,
    val link: String? = null,
    val quality: String? = null,
    val name: String? = null,
    val type: String? = null,
    val domain: String? = null,
) {
    val isDirect: Boolean
        get() {
            val l = link.orEmpty().lowercase()
            return type.equals("direct", true) || ".mp4" in l || ".m3u8" in l
        }
    val qualityValue: Int get() = quality.orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0
}

data class MovieListData(val movie: List<Movie>? = null)
data class MovieDetailData(
    val movie: Movie? = null,
    val season: List<Movie>? = null,
    /** Default play episode from detail API (same field AnimeIn uses for btnPlay). */
    val episode: Episode? = null,
)
data class EpisodeListData(val episode: List<Episode>? = null)
data class StreamData(val server: List<Server>? = null)

data class Slider(val id: String? = null, val image: String? = null, val type: String? = null, val link: String? = null)

data class HomeData(
    val slider: List<Slider> = emptyList(),
    val history: List<Movie> = emptyList(),
    val update: List<Movie> = emptyList(),
    val hot: List<Movie> = emptyList(),
    val new: List<Movie> = emptyList(),
    val today: List<Movie> = emptyList(),
    val random: List<Movie> = emptyList(),
    val waiting: List<Movie> = emptyList(),
    val popular: List<Movie> = emptyList(),
)

data class ExploreItem(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val label: String? = null,
    val type: String? = null,
    val group: String? = null,
    val image: String? = null,
    val image_poster: String? = null,
    val color: String? = null,
) {
    val displayName: String get() = name ?: title ?: label ?: id.orEmpty()
    val imageUrl: String? get() = image ?: image_poster
    val subtitle: String? get() = type?.takeIf { it.isNotBlank() } ?: group?.takeIf { it.isNotBlank() }
}

data class ExploreData(
    val type: List<ExploreItem> = emptyList(),
    val genre: List<ExploreItem> = emptyList(),
    val studio: List<ExploreItem> = emptyList(),
    val year: List<ExploreItem> = emptyList(),
) {
    val typeOrDefault: List<ExploreItem>
        get() = type.ifEmpty { DEFAULT_TYPES }

    companion object {
        val DEFAULT_TYPES = listOf(
            ExploreItem(id = "2", name = "MOVIE"),
            ExploreItem(id = "3", name = "ONA"),
            ExploreItem(id = "4", name = "OVA"),
            ExploreItem(id = "5", name = "LIVE ACTION"),
            ExploreItem(id = "6", name = "SERIES"),
            ExploreItem(id = "7", name = "SPECIAL"),
        )
    }
}

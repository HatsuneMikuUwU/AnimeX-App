package com.uwu.animex.data.animein

data class AnimeInUser(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val image: String = "",
    val image_name: String? = null,
    val date_join: String? = null,
    val pokemon: String? = null,
    val image_masukotto: String? = null,
    val money_coin: String = "0",
    val money_gem: String = "0",
    val views: String = "0",
    val likes: String = "0",
    val pro: Boolean = false,
    val admin: Boolean = false,
) {
    val displayAvatar: String?
        get() =
            when {
                !image_name.isNullOrBlank() && !image.isNullOrBlank() ->
                    if (image.endsWith("/")) image + image_name else "$image/$image_name"
                !image.isNullOrBlank() && !image.endsWith("/") -> image
                !image_masukotto.isNullOrBlank() -> image_masukotto
                else -> null
            }
}

data class AnimeInProfileEnvelope(
    val status: Int = 0,
    val error: Boolean = false,
    val message: String? = null,
    val data: AnimeInProfileData? = null,
)

data class AnimeInProfileData(
    val user: AnimeInUser? = null,
    val pro: Boolean = false,
    val admin: Boolean = false,
)

data class AnimeInAuthEnvelope(
    val status: Int = 0,
    val error: Boolean = false,
    val message: String? = null,
    val data: AnimeInAuthData? = null,
)

data class AnimeInAuthData(
    val user: AnimeInAuthUser? = null,
    val message: String? = null,
)

data class AnimeInAuthUser(
    val id: String? = null,
    val key_client: String? = null,
)

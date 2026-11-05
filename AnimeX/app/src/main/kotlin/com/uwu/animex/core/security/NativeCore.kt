package com.uwu.animex.core.security

object NativeCore {
    const val SECRET_MAL = 0
    const val SECRET_ANILIST = 1
    const val SECRET_GATE = 2
    const val SECRET_BASE = 3

    const val GATE_SETUP = 0
    const val HOME_LIST = 1
    const val HOME_SECTION = 2
    const val HOME_NEW_EPISODE = 3
    const val SCHEDULE = 4
    const val EXPLORE_MOVIE = 5
    const val FIND = 6
    const val DETAIL = 7
    const val EPISODE_LIST = 8
    const val TRAILER_MOVIE = 9
    const val TRAILER_GLOBAL = 10
    const val STREAM = 11
    const val EXPLORE_DATA = 12
    const val EXPLORE_GENRE = 13
    const val EXPLORE_YEAR = 14
    const val AUTH_LOGIN = 15
    const val AUTH_REGISTER = 16
    const val CHAT_DATA = 17
    const val CHAT_DO = 18

    init {
        System.loadLibrary("animexcore")
    }

    external fun secret(id: Int): String

    external fun endpoint(
        op: Int,
        arg: String,
    ): String

    external fun isVolatile(path: String): Boolean

    external fun exploreTarget(kind: String): Array<String>

    external fun searchPlan(): Array<String>

    external fun scheduleDays(): Array<String>

    external fun normalizeBase(raw: String): String

    external fun gateDomain(json: String): String

    fun path(
        op: Int,
        arg: String = "",
    ): String = endpoint(op, arg)
}

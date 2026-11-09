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
    const val USER_PROFILE = 16
    const val DISCUSSION_LIST = 17
    const val DISCUSSION_POST = 18
    const val DISCUSSION_DELETE = 19
    const val DISCUSSION_EDIT = 20
    const val DISCUSSION_REPORT = 21
    const val COMMENT_LIST = 22
    const val COMMENT_POST = 23
    const val COMMENT_DELETE = 24
    const val COMMENT_EDIT = 25
    const val COMMENT_REPORT = 26
    const val COMMENT_ACTION = 27
    const val REPLY_LIST = 28
    const val REPLY_POST = 29
    const val HOME_CUPLIX = 30
    const val FYP_SCROLL = 31
    const val FYP_VIEW = 32
    const val FYP_LIKE = 33

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

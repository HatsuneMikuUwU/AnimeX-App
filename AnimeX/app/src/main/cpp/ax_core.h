#pragma once
#include <string>
#include <vector>

namespace ax {

enum Secret { MAL = 0, ANILIST = 1, GATE = 2, BASE = 3 };

enum Op {
    GATE_SETUP = 0,
    HOME_LIST = 1,
    HOME_SECTION = 2,
    HOME_NEW_EPISODE = 3,
    SCHEDULE = 4,
    EXPLORE_MOVIE = 5,
    FIND = 6,
    DETAIL = 7,
    EPISODE_LIST = 8,
    TRAILER_MOVIE = 9,
    TRAILER_GLOBAL = 10,
    STREAM = 11,
    EXPLORE_DATA = 12,
    EXPLORE_GENRE = 13,
    EXPLORE_YEAR = 14,
    AUTH_LOGIN = 15,
    USER_PROFILE = 16,
    DISCUSSION_LIST = 17,
    DISCUSSION_POST = 18,
    DISCUSSION_DELETE = 19,
    DISCUSSION_EDIT = 20,
    DISCUSSION_REPORT = 21,
    COMMENT_LIST = 22,
    COMMENT_POST = 23,
    COMMENT_DELETE = 24,
    COMMENT_EDIT = 25,
    COMMENT_REPORT = 26,
    COMMENT_ACTION = 27,
    REPLY_LIST = 28,
    REPLY_POST = 29,
    HOME_CUPLIX = 30,
    FYP_SCROLL = 31,
    FYP_VIEW = 32,
    FYP_LIKE = 33,
};

std::string secret(int id, const char* masked_hex);

std::string endpoint(int op, const std::string& arg);

bool is_volatile(const std::string& path);

std::vector<std::string> explore_target(const std::string& kind);

std::vector<std::string> search_plan();

std::vector<std::string> schedule_days();

std::string normalize_base(const std::string& raw);

std::string gate_domain(const std::string& json);

}

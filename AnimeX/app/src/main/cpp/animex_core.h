#pragma once

#include <string>
#include <vector>

namespace animex {

enum SecretKind {
  SECRET_MY_ANIME_LIST = 0,
  SECRET_ANILIST = 1,
  SECRET_API_GATE = 2,
};

enum EndpointOperation {
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
  FOR_YOU_PAGE_SCROLL = 31,
  FOR_YOU_PAGE_VIEW = 32,
  FOR_YOU_PAGE_LIKE = 33,
  FOR_YOU_PAGE_SERVER = 34,
  FOR_YOU_PAGE_COMMENT_LIST = 35,
  FOR_YOU_PAGE_COMMENT_POST = 36,
  FOR_YOU_PAGE_COMMENT_DELETE = 37,
  FOR_YOU_PAGE_COMMENT_EDIT = 38,
  FOR_YOU_PAGE_COMMENT_REPORT = 39,
};

std::string decodeSecret(const char *maskedHexText);

std::string buildEndpointPath(int operation, const std::string &argument);

bool isVolatilePath(const std::string &path);

std::vector<std::string> exploreTargetFor(const std::string &kind);

std::vector<std::string> buildSearchPlan();

std::vector<std::string> scheduleDayNames();

}

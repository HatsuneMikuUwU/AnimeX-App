#include "animex_core.h"

#include <cctype>
#include <cstddef>
#include <utility>

namespace animex {
namespace {

constexpr unsigned char obfuscationByteAt(std::size_t index) {
  return static_cast<unsigned char>(0x5A ^ ((index * 7 + 3) & 0xFF));
}

template <std::size_t LiteralSize> struct ObfuscatedText {
  char obfuscatedCharacters[LiteralSize]{};

  constexpr explicit ObfuscatedText(const char (&literal)[LiteralSize]) {
    for (std::size_t index = 0; index < LiteralSize; ++index) {
      obfuscatedCharacters[index] =
          static_cast<char>(literal[index] ^ obfuscationByteAt(index));
    }
  }

  std::string reveal() const {
    std::string revealedText;
    revealedText.reserve(LiteralSize);
    for (std::size_t index = 0; index + 1 < LiteralSize; ++index) {
      revealedText.push_back(static_cast<char>(obfuscatedCharacters[index] ^
                                               obfuscationByteAt(index)));
    }
    return revealedText;
  }
};

#define OBFUSCATED_TEXT(literal)                                               \
  ([]() -> std::string {                                                       \
    static constexpr ObfuscatedText<sizeof(literal)> obfuscatedText(literal);  \
    return obfuscatedText.reveal();                                            \
  }())

std::string secretMaskingKey() { return OBFUSCATED_TEXT("com.uwu.animex:v1"); }

int hexDigitToNumber(char character) {
  if (character >= '0' && character <= '9')
    return character - '0';
  if (character >= 'a' && character <= 'f')
    return character - 'a' + 10;
  if (character >= 'A' && character <= 'F')
    return character - 'A' + 10;
  return -1;
}

std::string toLowerText(const std::string &text) {
  std::string lowerText;
  lowerText.reserve(text.size());
  for (char character : text) {
    lowerText.push_back(
        static_cast<char>(std::tolower(static_cast<unsigned char>(character))));
  }
  return lowerText;
}

bool tryDecodeHexByte(char highCharacter, char lowCharacter,
                      unsigned char &decodedByte) {
  const int highNibble = hexDigitToNumber(highCharacter);
  const int lowNibble = hexDigitToNumber(lowCharacter);
  if (highNibble < 0 || lowNibble < 0)
    return false;
  decodedByte = static_cast<unsigned char>((highNibble << 4) | lowNibble);
  return true;
}

std::vector<std::string> makeExploreTarget(const std::string &path,
                                           const std::string &parameterName,
                                           bool usesPagination) {
  return {path, parameterName, usesPagination ? "1" : "0"};
}

std::string makeSearchStep(const std::string &path,
                           const std::string &parameterName,
                           bool usesPagination) {
  return path + "|" + parameterName + "|" + (usesPagination ? "1" : "0");
}

}

std::string decodeSecret(const char *maskedHexText) {
  const std::string hexText = maskedHexText ? maskedHexText : "";
  if (hexText.empty() || hexText.size() % 2 != 0)
    return "";

  const std::string maskingKey = secretMaskingKey();
  std::string decodedSecret(hexText.size() / 2, '\0');

  for (std::size_t index = 0; index < decodedSecret.size(); ++index) {
    unsigned char maskedByte;
    if (!tryDecodeHexByte(hexText[index * 2], hexText[index * 2 + 1],
                          maskedByte))
      return "";

    const unsigned char keyByte =
        static_cast<unsigned char>(maskingKey[index % maskingKey.size()]);
    decodedSecret[index] = static_cast<char>(maskedByte ^ keyByte);
  }
  return decodedSecret;
}

std::string buildEndpointPath(int operation, const std::string &argument) {
  switch (operation) {
  case GATE_SETUP:
    return OBFUSCATED_TEXT("data/setup/data");
  case HOME_LIST:
    return OBFUSCATED_TEXT("data/home/list");
  case HOME_SECTION:
    return OBFUSCATED_TEXT("3/2/home/") + argument;
  case HOME_NEW_EPISODE:
    return OBFUSCATED_TEXT("data/home/list_new_episode");
  case SCHEDULE:
    return OBFUSCATED_TEXT("3/2/schedule/data");
  case EXPLORE_MOVIE:
    return OBFUSCATED_TEXT("3/2/explore/movie");
  case FIND:
    return OBFUSCATED_TEXT("data/movie/find");
  case DETAIL:
    return OBFUSCATED_TEXT("3/2/movie/detail/") + argument;
  case EPISODE_LIST:
    return OBFUSCATED_TEXT("3/2/movie/episode/") + argument;
  case TRAILER_MOVIE:
    return OBFUSCATED_TEXT("data/movie/trailer/list");
  case TRAILER_GLOBAL:
    return OBFUSCATED_TEXT("data/trailer/list");
  case STREAM:
    return OBFUSCATED_TEXT("3/2/episode/streamnew/") + argument;
  case EXPLORE_DATA:
    return OBFUSCATED_TEXT("3/2/explore/data");
  case EXPLORE_GENRE:
    return OBFUSCATED_TEXT("3/2/explore/genre");
  case EXPLORE_YEAR:
    return OBFUSCATED_TEXT("3/2/explore/year");
  case AUTH_LOGIN:
    return OBFUSCATED_TEXT("auth/login");
  case USER_PROFILE:
    return OBFUSCATED_TEXT("3/2/user/profile/data");

  case DISCUSSION_LIST:
    return OBFUSCATED_TEXT("data/movie/discussion/list");
  case DISCUSSION_POST:
    return OBFUSCATED_TEXT("data/movie/discussion/do");
  case DISCUSSION_DELETE:
    return OBFUSCATED_TEXT("data/movie/discussion/delete");
  case DISCUSSION_EDIT:
    return OBFUSCATED_TEXT("data/movie/discussion/edit_do");
  case DISCUSSION_REPORT:
    return OBFUSCATED_TEXT("data/movie/discussion/report");

  case COMMENT_LIST:
    return OBFUSCATED_TEXT("3/2/comment/data");
  case COMMENT_POST:
    return OBFUSCATED_TEXT("3/2/comment/do");
  case COMMENT_DELETE:
    return OBFUSCATED_TEXT("3/2/comment/delete");
  case COMMENT_EDIT:
    return OBFUSCATED_TEXT("3/2/comment/edit_do");
  case COMMENT_REPORT:
    return OBFUSCATED_TEXT("3/2/comment/report");
  case COMMENT_ACTION:
    return OBFUSCATED_TEXT("3/2/comment/action");

  case REPLY_LIST:
    return OBFUSCATED_TEXT("data/comment_replay/data");
  case REPLY_POST:
    return OBFUSCATED_TEXT("data/comment_replay/do");

  case HOME_CUPLIX:
    return OBFUSCATED_TEXT("data/home/fyp");
  case FOR_YOU_PAGE_SCROLL:
    return OBFUSCATED_TEXT("data/fyp2/list_scroll");
  case FOR_YOU_PAGE_VIEW:
    return OBFUSCATED_TEXT("data/fyp/view");
  case FOR_YOU_PAGE_LIKE:
    return OBFUSCATED_TEXT("data/user/fyp/like");
  case FOR_YOU_PAGE_SERVER:
    return OBFUSCATED_TEXT("data/fyp/server");
  case FOR_YOU_PAGE_COMMENT_LIST:
    return OBFUSCATED_TEXT("data/fyp/comment");
  case FOR_YOU_PAGE_COMMENT_POST:
    return OBFUSCATED_TEXT("data/fyp/comment");
  case FOR_YOU_PAGE_COMMENT_DELETE:
    return OBFUSCATED_TEXT("data/fyp/delete_comment");
  case FOR_YOU_PAGE_COMMENT_EDIT:
    return OBFUSCATED_TEXT("data/fyp/edit_comment");
  case FOR_YOU_PAGE_COMMENT_REPORT:
    return OBFUSCATED_TEXT("data/fyp/comment_report");

  default:
    return "";
  }
}

bool isVolatilePath(const std::string &path) {
  return path.find(OBFUSCATED_TEXT("streamnew")) != std::string::npos;
}

std::vector<std::string> exploreTargetFor(const std::string &kind) {
  const std::string lowerKind = toLowerText(kind);

  if (lowerKind == "genre") {
    return makeExploreTarget(OBFUSCATED_TEXT("3/2/explore/movie_genre"),
                             OBFUSCATED_TEXT("id_genre"), false);
  }
  if (lowerKind == "type" || lowerKind == "tipe") {
    return makeExploreTarget(OBFUSCATED_TEXT("3/2/explore/movie_type"),
                             OBFUSCATED_TEXT("type"), false);
  }
  if (lowerKind == "studio") {
    return makeExploreTarget(OBFUSCATED_TEXT("3/2/explore/movie_studio"),
                             OBFUSCATED_TEXT("studio"), false);
  }
  if (lowerKind == "year" || lowerKind == "tahun") {
    return makeExploreTarget(OBFUSCATED_TEXT("3/2/explore/movie_year"),
                             OBFUSCATED_TEXT("year"), true);
  }
  return makeExploreTarget(OBFUSCATED_TEXT("3/2/explore/movie"),
                           OBFUSCATED_TEXT("keyword"), false);
}

std::vector<std::string> buildSearchPlan() {
  const std::string exploreMoviePath = OBFUSCATED_TEXT("3/2/explore/movie");
  const std::string findPath = OBFUSCATED_TEXT("data/movie/find");
  const std::string keywordParameter = OBFUSCATED_TEXT("keyword");

  std::vector<std::string> searchPlan;
  searchPlan.push_back(
      makeSearchStep(exploreMoviePath, keywordParameter, true));
  searchPlan.push_back(makeSearchStep(findPath, keywordParameter, false));

  const std::vector<std::string> fallbackParameterNames = {
      OBFUSCATED_TEXT("query"),  OBFUSCATED_TEXT("q"),
      OBFUSCATED_TEXT("search"), OBFUSCATED_TEXT("title"),
      OBFUSCATED_TEXT("name"),
  };
  for (const std::string &parameterName : fallbackParameterNames)
    searchPlan.push_back(makeSearchStep(exploreMoviePath, parameterName, true));
  return searchPlan;
}

std::vector<std::string> scheduleDayNames() {
  return {
      OBFUSCATED_TEXT("SENIN"),  OBFUSCATED_TEXT("SELASA"),
      OBFUSCATED_TEXT("RABU"),   OBFUSCATED_TEXT("KAMIS"),
      OBFUSCATED_TEXT("JUMAT"),  OBFUSCATED_TEXT("SABTU"),
      OBFUSCATED_TEXT("MINGGU"),
  };
}

}

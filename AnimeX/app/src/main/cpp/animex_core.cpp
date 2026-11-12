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

const char *const kBaseUrlHasQueryOrFragment = "!q";

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

bool isWhitespace(char character) {
  return std::isspace(static_cast<unsigned char>(character)) != 0;
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

std::string trimWhitespace(const std::string &text) {
  std::size_t beginIndex = 0;
  std::size_t endIndex = text.size();
  while (beginIndex < endIndex && isWhitespace(text[beginIndex]))
    ++beginIndex;
  while (endIndex > beginIndex && isWhitespace(text[endIndex - 1]))
    --endIndex;
  return text.substr(beginIndex, endIndex - beginIndex);
}

bool containsOnlyDigits(const std::string &text) {
  if (text.empty())
    return false;
  for (char character : text) {
    if (character < '0' || character > '9')
      return false;
  }
  return true;
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

class JsonReader {
public:
  explicit JsonReader(const std::string &jsonText) : text_(jsonText) {}

  bool findStringByPath(const std::vector<std::string> &keyPath,
                        std::string *result) {
    return findStringAtPathIndex(keyPath, 0, result);
  }

private:
  static constexpr int kMaximumNestingDepth = 64;

  const std::string &text_;
  std::size_t position_ = 0;
  int nestingDepth_ = 0;

  bool isAtEnd() const { return position_ >= text_.size(); }

  bool nextCharacterIs(char expected) const {
    return !isAtEnd() && text_[position_] == expected;
  }

  bool consumeCharacter(char expected) {
    if (!nextCharacterIs(expected))
      return false;
    ++position_;
    return true;
  }

  void skipWhitespace() {
    while (!isAtEnd() && isWhitespace(text_[position_]))
      ++position_;
  }

  static void appendUtf8(std::string &output, unsigned codePoint) {
    if (codePoint < 0x80) {
      output.push_back(static_cast<char>(codePoint));
    } else if (codePoint < 0x800) {
      output.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
      output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else if (codePoint < 0x10000) {
      output.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
      output.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
      output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else {
      output.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
      output.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
      output.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
      output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
  }

  bool readFourHexDigits(unsigned &value) {
    if (position_ + 4 > text_.size())
      return false;
    value = 0;
    for (int offset = 0; offset < 4; ++offset) {
      const int digit = hexDigitToNumber(text_[position_ + offset]);
      if (digit < 0)
        return false;
      value = (value << 4) | static_cast<unsigned>(digit);
    }
    position_ += 4;
    return true;
  }

  bool readUnicodeEscape(std::string &output) {
    unsigned codePoint;
    if (!readFourHexDigits(codePoint))
      return false;

    const bool isHighSurrogate = codePoint >= 0xD800 && codePoint < 0xDC00;
    const bool followedByUnicodeEscape = position_ + 1 < text_.size() &&
                                         text_[position_] == '\\' &&
                                         text_[position_ + 1] == 'u';
    if (isHighSurrogate && followedByUnicodeEscape) {
      position_ += 2;
      unsigned lowSurrogate;
      if (!readFourHexDigits(lowSurrogate))
        return false;
      codePoint =
          0x10000 + ((codePoint - 0xD800) << 10) + (lowSurrogate - 0xDC00);
    }
    appendUtf8(output, codePoint);
    return true;
  }

  bool readEscapeSequence(std::string &output) {
    if (isAtEnd())
      return false;
    const char escapeCharacter = text_[position_++];
    switch (escapeCharacter) {
    case '"':
      output.push_back('"');
      return true;
    case '\\':
      output.push_back('\\');
      return true;
    case '/':
      output.push_back('/');
      return true;
    case 'b':
      output.push_back('\b');
      return true;
    case 'f':
      output.push_back('\f');
      return true;
    case 'n':
      output.push_back('\n');
      return true;
    case 'r':
      output.push_back('\r');
      return true;
    case 't':
      output.push_back('\t');
      return true;
    case 'u':
      return readUnicodeEscape(output);
    default:
      return false;
    }
  }

  bool readString(std::string *result) {
    if (!consumeCharacter('"'))
      return false;

    std::string decodedText;
    while (!isAtEnd()) {
      const char character = text_[position_++];
      if (character == '"') {
        if (result)
          *result = std::move(decodedText);
        return true;
      }
      if (character != '\\') {
        decodedText.push_back(character);
      } else if (!readEscapeSequence(decodedText)) {
        return false;
      }
    }
    return false;
  }

  bool skipScalar() {
    const std::size_t valueStart = position_;
    while (!isAtEnd() && text_[position_] != ',' && text_[position_] != '}' &&
           text_[position_] != ']' && !isWhitespace(text_[position_])) {
      ++position_;
    }
    return position_ > valueStart;
  }

  bool skipObjectKeyAndColon() {
    if (!readString(nullptr))
      return false;
    skipWhitespace();
    return consumeCharacter(':');
  }

  bool skipContainer() {
    const char openingCharacter = text_[position_];
    const bool isObject = openingCharacter == '{';
    const char closingCharacter = isObject ? '}' : ']';

    ++position_;
    skipWhitespace();
    if (consumeCharacter(closingCharacter))
      return true;

    for (;;) {
      skipWhitespace();
      if (isObject && !skipObjectKeyAndColon())
        return false;
      if (!skipValue())
        return false;

      skipWhitespace();
      if (consumeCharacter(','))
        continue;
      return consumeCharacter(closingCharacter);
    }
  }

  bool skipValue() {
    if (++nestingDepth_ > kMaximumNestingDepth)
      return false;

    skipWhitespace();
    bool isValid;
    if (isAtEnd()) {
      isValid = false;
    } else if (text_[position_] == '"') {
      isValid = readString(nullptr);
    } else if (text_[position_] == '{' || text_[position_] == '[') {
      isValid = skipContainer();
    } else {
      isValid = skipScalar();
    }

    --nestingDepth_;
    return isValid;
  }

  bool findStringAtPathIndex(const std::vector<std::string> &keyPath,
                             std::size_t pathIndex, std::string *result) {
    skipWhitespace();
    if (pathIndex == keyPath.size())
      return readString(result);
    if (!consumeCharacter('{'))
      return false;

    for (;;) {
      skipWhitespace();
      if (nextCharacterIs('}'))
        return false;

      std::string currentKey;
      if (!readString(&currentKey))
        return false;

      skipWhitespace();
      if (!consumeCharacter(':'))
        return false;

      if (currentKey == keyPath[pathIndex])
        return findStringAtPathIndex(keyPath, pathIndex + 1, result);

      if (!skipValue())
        return false;

      skipWhitespace();
      if (!consumeCharacter(','))
        return false;
    }
  }
};

struct ParsedBaseUrl {
  std::string scheme;
  std::string host;
  std::string port;
  std::string pathAndQuery;
};

std::string prepareRawUrl(const std::string &rawUrl) {
  std::string preparedUrl = trimWhitespace(rawUrl);
  if (preparedUrl.compare(0, 2, "//") == 0)
    preparedUrl = "https:" + preparedUrl;
  return preparedUrl;
}

bool splitSchemeFromUrl(const std::string &url, std::string &scheme,
                        std::string &remainder) {
  const std::size_t separatorIndex = url.find("://");
  if (separatorIndex == std::string::npos)
    return false;

  scheme = toLowerText(url.substr(0, separatorIndex));
  if (scheme != "http" && scheme != "https")
    return false;

  remainder = url.substr(separatorIndex + 3);
  return true;
}

bool containsForbiddenUrlCharacter(const std::string &text) {
  for (char character : text) {
    if (isWhitespace(character) || character == '\\')
      return true;
  }
  return false;
}

bool splitHostAndPort(const std::string &authority, std::string &host,
                      std::string &port) {
  host = authority;
  port.clear();

  const std::size_t colonIndex = authority.rfind(':');
  const bool colonSeparatesPort =
      colonIndex != std::string::npos &&
      authority.find(']', colonIndex) == std::string::npos;
  if (colonSeparatesPort) {
    host = authority.substr(0, colonIndex);
    port = authority.substr(colonIndex + 1);
    if (!port.empty() && !containsOnlyDigits(port))
      return false;
  }
  return !host.empty();
}

bool parseBaseUrl(const std::string &rawUrl, ParsedBaseUrl &parsedUrl) {
  const std::string preparedUrl = prepareRawUrl(rawUrl);
  if (preparedUrl.empty())
    return false;

  std::string afterScheme;
  if (!splitSchemeFromUrl(preparedUrl, parsedUrl.scheme, afterScheme))
    return false;
  if (containsForbiddenUrlCharacter(afterScheme))
    return false;

  const std::size_t authorityEndIndex = afterScheme.find_first_of("/?#");
  const std::string authority = afterScheme.substr(0, authorityEndIndex);
  parsedUrl.pathAndQuery = authorityEndIndex == std::string::npos
                               ? ""
                               : afterScheme.substr(authorityEndIndex);

  if (authority.empty() || authority.find('@') != std::string::npos)
    return false;
  return splitHostAndPort(authority, parsedUrl.host, parsedUrl.port);
}

void upgradeToHttpsAndDropDefaultPort(ParsedBaseUrl &parsedUrl) {
  if (parsedUrl.scheme == "http") {
    if (parsedUrl.port == "80")
      parsedUrl.port.clear();
    parsedUrl.scheme = "https";
  } else if (parsedUrl.port == "443") {
    parsedUrl.port.clear();
  }
}

void removeTrailingSlashes(std::string &path) {
  while (!path.empty() && path.back() == '/')
    path.pop_back();
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

std::string normalizeBaseUrl(const std::string &rawUrl) {
  ParsedBaseUrl parsedUrl;
  if (!parseBaseUrl(rawUrl, parsedUrl))
    return "";

  upgradeToHttpsAndDropDefaultPort(parsedUrl);

  if (parsedUrl.pathAndQuery.find_first_of("?#") != std::string::npos)
    return kBaseUrlHasQueryOrFragment;
  removeTrailingSlashes(parsedUrl.pathAndQuery);

  const std::string portSuffix =
      parsedUrl.port.empty() ? "" : ":" + parsedUrl.port;
  return parsedUrl.scheme + "://" + parsedUrl.host + portSuffix +
         parsedUrl.pathAndQuery + "/";
}

std::string extractGateDomain(const std::string &jsonText) {
  const std::vector<std::string> keyPath = {OBFUSCATED_TEXT("data"),
                                            OBFUSCATED_TEXT("domain_api"),
                                            OBFUSCATED_TEXT("value")};

  JsonReader jsonReader(jsonText);
  std::string domainValue;
  if (!jsonReader.findStringByPath(keyPath, &domainValue))
    return "";
  return domainValue;
}

}

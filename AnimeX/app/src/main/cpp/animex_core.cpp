#include "animex_core.h"

#include <cctype>
#include <cstddef>

// Rahasia dikirim dari Gradle lewat CMake dalam bentuk hex yang sudah di-XOR.
// Jika tidak diisi, nilainya string kosong supaya build tetap berhasil.
#ifndef ANIMEX_MASKED_SECRET_MY_ANIME_LIST
#define ANIMEX_MASKED_SECRET_MY_ANIME_LIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_ANILIST
#define ANIMEX_MASKED_SECRET_ANILIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_API_GATE
#define ANIMEX_MASKED_SECRET_API_GATE ""
#endif
#ifndef ANIMEX_MASKED_SECRET_API_BASE
#define ANIMEX_MASKED_SECRET_API_BASE ""
#endif

namespace animex {
namespace {

// ---------------------------------------------------------------------------
// Penyamaran teks saat compile (agar string tidak terbaca langsung di binary)
// ---------------------------------------------------------------------------

constexpr unsigned char obfuscationByteAt(std::size_t index) {
    return static_cast<unsigned char>(0x5A ^ ((index * 7 + 3) & 0xFF));
}

template <std::size_t LiteralSize>
struct ObfuscatedText {
    char obfuscatedCharacters[LiteralSize]{};

    constexpr explicit ObfuscatedText(const char (&literal)[LiteralSize]) {
        for (std::size_t index = 0; index < LiteralSize; ++index) {
            obfuscatedCharacters[index] =
                static_cast<char>(literal[index] ^ obfuscationByteAt(index));
        }
    }

    // LiteralSize sudah termasuk karakter null di akhir, jadi dilewati.
    std::string reveal() const {
        std::string revealedText;
        revealedText.reserve(LiteralSize);
        for (std::size_t index = 0; index + 1 < LiteralSize; ++index) {
            revealedText.push_back(
                static_cast<char>(obfuscatedCharacters[index] ^ obfuscationByteAt(index)));
        }
        return revealedText;
    }
};

#define OBFUSCATED_TEXT(literal)                                                   \
    ([]() -> std::string {                                                         \
        static constexpr ObfuscatedText<sizeof(literal)> obfuscatedText(literal);  \
        return obfuscatedText.reveal();                                            \
    }())

std::string secretMaskingKey() { return OBFUSCATED_TEXT("com.uwu.animex:v1"); }

// ---------------------------------------------------------------------------
// Fungsi bantu umum
// ---------------------------------------------------------------------------

// Mengubah satu karakter hex menjadi angka 0-15. Mengembalikan -1 jika bukan hex.
int hexDigitToNumber(char character) {
    if (character >= '0' && character <= '9') return character - '0';
    if (character >= 'a' && character <= 'f') return character - 'a' + 10;
    if (character >= 'A' && character <= 'F') return character - 'A' + 10;
    return -1;
}

bool isWhitespace(char character) {
    return std::isspace(static_cast<unsigned char>(character)) != 0;
}

char toLowerCharacter(char character) {
    return static_cast<char>(std::tolower(static_cast<unsigned char>(character)));
}

std::string toLowerText(const std::string& text) {
    std::string lowerText;
    lowerText.reserve(text.size());
    for (char character : text) lowerText.push_back(toLowerCharacter(character));
    return lowerText;
}

bool containsOnlyDigits(const std::string& text) {
    if (text.empty()) return false;
    for (char character : text) {
        if (character < '0' || character > '9') return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Pembaca JSON sederhana: hanya mencari satu nilai string berdasarkan path key
// ---------------------------------------------------------------------------

class JsonReader {
public:
    explicit JsonReader(const std::string& jsonText) : text_(jsonText) {}

    // Mencari nilai string pada path key, contoh: {"data", "domain_api", "value"}.
    bool findStringByPath(const std::vector<std::string>& keyPath, std::string* result) {
        return findStringAtDepth(keyPath, 0, result);
    }

private:
    static constexpr int kMaximumNestingDepth = 64;

    const std::string& text_;
    std::size_t position_ = 0;
    int nestingDepth_ = 0;

    bool isAtEnd() const { return position_ >= text_.size(); }

    void skipWhitespace() {
        while (!isAtEnd() && isWhitespace(text_[position_])) ++position_;
    }

    // Menulis satu code point Unicode ke `output` dalam format UTF-8.
    static void appendUtf8(std::string& output, unsigned codePoint) {
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

    // Membaca 4 digit hex setelah "\u".
    bool readFourHexDigits(unsigned& value) {
        if (position_ + 4 > text_.size()) return false;
        value = 0;
        for (int offset = 0; offset < 4; ++offset) {
            int digit = hexDigitToNumber(text_[position_ + offset]);
            if (digit < 0) return false;
            value = (value << 4) | static_cast<unsigned>(digit);
        }
        position_ += 4;
        return true;
    }

    // Membaca string JSON (termasuk escape). `result` boleh nullptr jika
    // isinya tidak dibutuhkan dan hanya perlu dilewati.
    bool readString(std::string* result) {
        if (isAtEnd() || text_[position_] != '"') return false;
        ++position_;

        std::string decodedText;
        while (!isAtEnd()) {
            char character = text_[position_++];

            if (character == '"') {
                if (result) *result = std::move(decodedText);
                return true;
            }
            if (character != '\\') {
                decodedText.push_back(character);
                continue;
            }

            if (isAtEnd()) return false;
            char escapeCharacter = text_[position_++];
            switch (escapeCharacter) {
                case '"':  decodedText.push_back('"');  break;
                case '\\': decodedText.push_back('\\'); break;
                case '/':  decodedText.push_back('/');  break;
                case 'b':  decodedText.push_back('\b'); break;
                case 'f':  decodedText.push_back('\f'); break;
                case 'n':  decodedText.push_back('\n'); break;
                case 'r':  decodedText.push_back('\r'); break;
                case 't':  decodedText.push_back('\t'); break;
                case 'u': {
                    unsigned codePoint;
                    if (!readFourHexDigits(codePoint)) return false;

                    // Pasangan surrogate (karakter di luar BMP) digabung jadi satu code point.
                    bool isHighSurrogate = codePoint >= 0xD800 && codePoint < 0xDC00;
                    bool followedByUnicodeEscape = position_ + 1 < text_.size() &&
                                                   text_[position_] == '\\' &&
                                                   text_[position_ + 1] == 'u';
                    if (isHighSurrogate && followedByUnicodeEscape) {
                        position_ += 2;
                        unsigned lowSurrogate;
                        if (!readFourHexDigits(lowSurrogate)) return false;
                        codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                    }
                    appendUtf8(decodedText, codePoint);
                    break;
                }
                default:
                    return false;
            }
        }
        return false;  // String tidak pernah ditutup.
    }

    // Melewati satu objek, array, string, atau nilai biasa (angka, true, false, null).
    bool skipValue() {
        if (++nestingDepth_ > kMaximumNestingDepth) return false;

        skipWhitespace();
        bool isValid = false;

        if (isAtEnd()) {
            isValid = false;
        } else if (text_[position_] == '"') {
            isValid = readString(nullptr);
        } else if (text_[position_] == '{' || text_[position_] == '[') {
            isValid = skipContainer();
        } else {
            std::size_t valueStart = position_;
            while (!isAtEnd() && text_[position_] != ',' && text_[position_] != '}' &&
                   text_[position_] != ']' && !isWhitespace(text_[position_])) {
                ++position_;
            }
            isValid = position_ > valueStart;
        }

        --nestingDepth_;
        return isValid;
    }

    // Melewati objek {...} atau array [...] beserta seluruh isinya.
    bool skipContainer() {
        const char openingCharacter = text_[position_];
        const char closingCharacter = (openingCharacter == '{') ? '}' : ']';
        const bool isObject = (openingCharacter == '{');

        ++position_;
        skipWhitespace();

        // Container kosong.
        if (!isAtEnd() && text_[position_] == closingCharacter) {
            ++position_;
            return true;
        }

        bool isValid = true;
        while (isValid) {
            skipWhitespace();

            if (isObject) {
                isValid = readString(nullptr);  // Key
                skipWhitespace();
                isValid = isValid && !isAtEnd() && text_[position_++] == ':';
            }
            isValid = isValid && skipValue();

            skipWhitespace();
            if (!isValid || isAtEnd()) return false;

            if (text_[position_] == ',') {
                ++position_;
                continue;
            }
            if (text_[position_] == closingCharacter) {
                ++position_;
                break;
            }
            isValid = false;
        }
        return isValid;
    }

    bool findStringAtDepth(const std::vector<std::string>& keyPath, std::size_t pathIndex,
                           std::string* result) {
        skipWhitespace();

        // Seluruh path sudah diikuti, nilai di posisi ini harus berupa string.
        if (pathIndex == keyPath.size()) return readString(result);

        if (isAtEnd() || text_[position_] != '{') return false;
        ++position_;

        for (;;) {
            skipWhitespace();
            if (!isAtEnd() && text_[position_] == '}') return false;  // Key tidak ditemukan.

            std::string currentKey;
            if (!readString(&currentKey)) return false;

            skipWhitespace();
            if (isAtEnd() || text_[position_++] != ':') return false;

            if (currentKey == keyPath[pathIndex]) {
                return findStringAtDepth(keyPath, pathIndex + 1, result);
            }

            if (!skipValue()) return false;

            skipWhitespace();
            if (!isAtEnd() && text_[position_] == ',') {
                ++position_;
                continue;
            }
            return false;
        }
    }
};

// Memotong spasi di awal dan akhir teks.
std::string trimWhitespace(const std::string& text) {
    std::size_t beginIndex = 0;
    std::size_t endIndex = text.size();
    while (beginIndex < endIndex && isWhitespace(text[beginIndex])) ++beginIndex;
    while (endIndex > beginIndex && isWhitespace(text[endIndex - 1])) --endIndex;
    return text.substr(beginIndex, endIndex - beginIndex);
}

}  // namespace

// ---------------------------------------------------------------------------
// Fungsi publik
// ---------------------------------------------------------------------------

std::string decodeSecret(int secretId, const char* maskedHexText) {
    (void)secretId;  // Belum dipakai, disimpan agar tanda tangan fungsi tetap sama.

    std::string hexText = maskedHexText ? maskedHexText : "";
    if (hexText.empty() || hexText.size() % 2 != 0) return "";

    const std::string maskingKey = secretMaskingKey();
    std::string decodedSecret(hexText.size() / 2, '\0');

    for (std::size_t index = 0; index < decodedSecret.size(); ++index) {
        int highNibble = hexDigitToNumber(hexText[index * 2]);
        int lowNibble = hexDigitToNumber(hexText[index * 2 + 1]);
        if (highNibble < 0 || lowNibble < 0) return "";

        unsigned char maskedByte = static_cast<unsigned char>((highNibble << 4) | lowNibble);
        unsigned char keyByte = static_cast<unsigned char>(maskingKey[index % maskingKey.size()]);
        decodedSecret[index] = static_cast<char>(maskedByte ^ keyByte);
    }
    return decodedSecret;
}

std::string buildEndpointPath(int operation, const std::string& argument) {
    switch (operation) {
        case GATE_SETUP:       return OBFUSCATED_TEXT("data/setup/data");
        case HOME_LIST:        return OBFUSCATED_TEXT("data/home/list");
        case HOME_SECTION:     return OBFUSCATED_TEXT("3/2/home/") + argument;
        case HOME_NEW_EPISODE: return OBFUSCATED_TEXT("data/home/list_new_episode");
        case SCHEDULE:         return OBFUSCATED_TEXT("3/2/schedule/data");
        case EXPLORE_MOVIE:    return OBFUSCATED_TEXT("3/2/explore/movie");
        case FIND:             return OBFUSCATED_TEXT("data/movie/find");
        case DETAIL:           return OBFUSCATED_TEXT("3/2/movie/detail/") + argument;
        case EPISODE_LIST:     return OBFUSCATED_TEXT("3/2/movie/episode/") + argument;
        case TRAILER_MOVIE:    return OBFUSCATED_TEXT("data/movie/trailer/list");
        case TRAILER_GLOBAL:   return OBFUSCATED_TEXT("data/trailer/list");
        case STREAM:           return OBFUSCATED_TEXT("3/2/episode/streamnew/") + argument;
        case EXPLORE_DATA:     return OBFUSCATED_TEXT("3/2/explore/data");
        case EXPLORE_GENRE:    return OBFUSCATED_TEXT("3/2/explore/genre");
        case EXPLORE_YEAR:     return OBFUSCATED_TEXT("3/2/explore/year");
        case AUTH_LOGIN:       return OBFUSCATED_TEXT("auth/login");
        case USER_PROFILE:     return OBFUSCATED_TEXT("3/2/user/profile/data");

        case DISCUSSION_LIST:   return OBFUSCATED_TEXT("data/movie/discussion/list");
        case DISCUSSION_POST:   return OBFUSCATED_TEXT("data/movie/discussion/do");
        case DISCUSSION_DELETE: return OBFUSCATED_TEXT("data/movie/discussion/delete");
        case DISCUSSION_EDIT:   return OBFUSCATED_TEXT("data/movie/discussion/edit_do");
        case DISCUSSION_REPORT: return OBFUSCATED_TEXT("data/movie/discussion/report");

        case COMMENT_LIST:   return OBFUSCATED_TEXT("3/2/comment/data");
        case COMMENT_POST:   return OBFUSCATED_TEXT("3/2/comment/do");
        case COMMENT_DELETE: return OBFUSCATED_TEXT("3/2/comment/delete");
        case COMMENT_EDIT:   return OBFUSCATED_TEXT("3/2/comment/edit_do");
        case COMMENT_REPORT: return OBFUSCATED_TEXT("3/2/comment/report");
        case COMMENT_ACTION: return OBFUSCATED_TEXT("3/2/comment/action");

        case REPLY_LIST: return OBFUSCATED_TEXT("data/comment_replay/data");
        case REPLY_POST: return OBFUSCATED_TEXT("data/comment_replay/do");

        case HOME_CUPLIX:          return OBFUSCATED_TEXT("data/home/fyp");
        case FOR_YOU_PAGE_SCROLL:  return OBFUSCATED_TEXT("data/fyp2/list_scroll");
        case FOR_YOU_PAGE_VIEW:    return OBFUSCATED_TEXT("data/fyp/view");
        case FOR_YOU_PAGE_LIKE:    return OBFUSCATED_TEXT("data/user/fyp/like");
        case FOR_YOU_PAGE_SERVER:  return OBFUSCATED_TEXT("data/fyp/server");

        // Daftar dan kirim komentar memakai path yang sama.
        case FOR_YOU_PAGE_COMMENT_LIST:   return OBFUSCATED_TEXT("data/fyp/comment");
        case FOR_YOU_PAGE_COMMENT_POST:   return OBFUSCATED_TEXT("data/fyp/comment");
        case FOR_YOU_PAGE_COMMENT_DELETE: return OBFUSCATED_TEXT("data/fyp/delete_comment");
        case FOR_YOU_PAGE_COMMENT_EDIT:   return OBFUSCATED_TEXT("data/fyp/edit_comment");
        case FOR_YOU_PAGE_COMMENT_REPORT: return OBFUSCATED_TEXT("data/fyp/comment_report");

        default: return "";
    }
}

bool isVolatilePath(const std::string& path) {
    return path.find(OBFUSCATED_TEXT("streamnew")) != std::string::npos;
}

std::vector<std::string> exploreTargetFor(const std::string& kind) {
    const std::string lowerKind = toLowerText(kind);

    // Hasil: {path, nama parameter, "1" jika memakai halaman atau "0" jika tidak}.
    if (lowerKind == "genre") {
        return {OBFUSCATED_TEXT("3/2/explore/movie_genre"), OBFUSCATED_TEXT("id_genre"), "0"};
    }
    if (lowerKind == "type" || lowerKind == "tipe") {
        return {OBFUSCATED_TEXT("3/2/explore/movie_type"), OBFUSCATED_TEXT("type"), "0"};
    }
    if (lowerKind == "studio") {
        return {OBFUSCATED_TEXT("3/2/explore/movie_studio"), OBFUSCATED_TEXT("studio"), "0"};
    }
    if (lowerKind == "year" || lowerKind == "tahun") {
        return {OBFUSCATED_TEXT("3/2/explore/movie_year"), OBFUSCATED_TEXT("year"), "1"};
    }
    return {OBFUSCATED_TEXT("3/2/explore/movie"), OBFUSCATED_TEXT("keyword"), "0"};
}

std::vector<std::string> buildSearchPlan() {
    const std::string exploreMoviePath = OBFUSCATED_TEXT("3/2/explore/movie");

    // Format tiap langkah: "path|nama parameter|memakaiHalaman".
    std::vector<std::string> searchPlan;
    searchPlan.push_back(exploreMoviePath + "|" + OBFUSCATED_TEXT("keyword") + "|1");
    searchPlan.push_back(OBFUSCATED_TEXT("data/movie/find") + "|" + OBFUSCATED_TEXT("keyword") + "|0");

    // Cadangan: coba nama parameter lain jika server tidak mengenali "keyword".
    const std::vector<std::string> fallbackParameterNames = {
        OBFUSCATED_TEXT("query"), OBFUSCATED_TEXT("q"),     OBFUSCATED_TEXT("search"),
        OBFUSCATED_TEXT("title"), OBFUSCATED_TEXT("name"),
    };
    for (const std::string& parameterName : fallbackParameterNames) {
        searchPlan.push_back(exploreMoviePath + "|" + parameterName + "|1");
    }
    return searchPlan;
}

std::vector<std::string> scheduleDayNames() {
    return {
        OBFUSCATED_TEXT("SENIN"),  OBFUSCATED_TEXT("SELASA"), OBFUSCATED_TEXT("RABU"),
        OBFUSCATED_TEXT("KAMIS"),  OBFUSCATED_TEXT("JUMAT"),  OBFUSCATED_TEXT("SABTU"),
        OBFUSCATED_TEXT("MINGGU"),
    };
}

std::string normalizeBaseUrl(const std::string& rawUrl) {
    const std::string trimmedUrl = [&] {
        std::string trimmed = trimWhitespace(rawUrl);
        // URL tanpa skema ("//contoh.com") dianggap https.
        if (trimmed.compare(0, 2, "//") == 0) trimmed = "https:" + trimmed;
        return trimmed;
    }();
    if (trimmedUrl.empty()) return "";

    // --- Skema: hanya http atau https ---
    const std::size_t schemeSeparatorIndex = trimmedUrl.find("://");
    if (schemeSeparatorIndex == std::string::npos) return "";

    std::string scheme = toLowerText(trimmedUrl.substr(0, schemeSeparatorIndex));
    if (scheme != "http" && scheme != "https") return "";

    // --- Bagian setelah "://": tidak boleh ada spasi atau backslash ---
    const std::string afterScheme = trimmedUrl.substr(schemeSeparatorIndex + 3);
    for (char character : afterScheme) {
        if (isWhitespace(character) || character == '\\') return "";
    }

    // --- Pisahkan authority (host:port) dari path ---
    const std::size_t authorityEndIndex = afterScheme.find_first_of("/?#");
    const std::string authority = afterScheme.substr(0, authorityEndIndex);
    std::string pathAndQuery =
        (authorityEndIndex == std::string::npos) ? "" : afterScheme.substr(authorityEndIndex);

    // Kosong, atau memuat user:password@ (tidak diizinkan).
    if (authority.empty() || authority.find('@') != std::string::npos) return "";

    // --- Pisahkan host dan port ---
    std::string host = authority;
    std::string port;
    const std::size_t portSeparatorIndex = authority.rfind(':');
    const bool colonIsPortSeparator =
        portSeparatorIndex != std::string::npos &&
        authority.find(']', portSeparatorIndex) == std::string::npos;  // Bukan bagian IPv6.
    if (colonIsPortSeparator) {
        host = authority.substr(0, portSeparatorIndex);
        port = authority.substr(portSeparatorIndex + 1);
        if (!port.empty() && !containsOnlyDigits(port)) return "";
    }
    if (host.empty()) return "";

    // --- Paksa https dan buang port bawaan ---
    if (scheme == "http") {
        if (port.empty() || port == "80") port = "";
        scheme = "https";
    } else if (port == "443") {
        port.clear();
    }

    // Alamat dasar tidak boleh memuat query atau fragment.
    if (pathAndQuery.find_first_of("?#") != std::string::npos) return "!q";

    // Buang garis miring di akhir, lalu tambahkan tepat satu.
    while (!pathAndQuery.empty() && pathAndQuery.back() == '/') pathAndQuery.pop_back();

    const std::string portSuffix = port.empty() ? "" : ":" + port;
    return scheme + "://" + host + portSuffix + pathAndQuery + "/";
}

std::string extractGateDomain(const std::string& jsonText) {
    JsonReader jsonReader(jsonText);
    std::string domainValue;
    const std::vector<std::string> keyPath = {
        OBFUSCATED_TEXT("data"), OBFUSCATED_TEXT("domain_api"), OBFUSCATED_TEXT("value")};
    if (!jsonReader.findStringByPath(keyPath, &domainValue)) return "";
    return domainValue;
}

}  // namespace animex

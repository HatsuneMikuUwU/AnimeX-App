#include "ax_core.h"

#include <cctype>
#include <cstddef>

#ifndef AX_K0
#define AX_K0 ""
#endif
#ifndef AX_K1
#define AX_K1 ""
#endif
#ifndef AX_K2
#define AX_K2 ""
#endif
#ifndef AX_K3
#define AX_K3 ""
#endif

namespace ax {
namespace {

// ---- compile-time string obfuscation (literals do not appear in .rodata) ----
constexpr unsigned char mix(std::size_t i) {
    return static_cast<unsigned char>(0x5A ^ ((i * 7 + 3) & 0xFF));
}

template <std::size_t N>
struct Obf {
    char d[N]{};
    constexpr explicit Obf(const char (&s)[N]) {
        for (std::size_t i = 0; i < N; ++i) d[i] = static_cast<char>(s[i] ^ mix(i));
    }
    std::string str() const {
        std::string out;
        out.reserve(N);
        for (std::size_t i = 0; i + 1 < N; ++i) out.push_back(static_cast<char>(d[i] ^ mix(i)));
        return out;
    }
};

#define OBF(lit) ([]() -> std::string { static constexpr Obf<sizeof(lit)> o(lit); return o.str(); }())

std::string salt() { return OBF("com.uwu.animex:v1"); }

int hexv(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

// ---- minimal JSON walker: find a string at an object key path ----
struct Json {
    const std::string& s;
    std::size_t i = 0;
    int depth = 0;

    explicit Json(const std::string& src) : s(src) {}

    void ws() {
        while (i < s.size() && std::isspace(static_cast<unsigned char>(s[i]))) ++i;
    }

    static void utf8(std::string& out, unsigned cp) {
        if (cp < 0x80) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }

    bool hex4(unsigned& v) {
        if (i + 4 > s.size()) return false;
        v = 0;
        for (int k = 0; k < 4; ++k) {
            int h = hexv(s[i + k]);
            if (h < 0) return false;
            v = (v << 4) | static_cast<unsigned>(h);
        }
        i += 4;
        return true;
    }

    bool str(std::string* out) {
        if (i >= s.size() || s[i] != '"') return false;
        ++i;
        std::string buf;
        while (i < s.size()) {
            char c = s[i++];
            if (c == '"') {
                if (out) *out = std::move(buf);
                return true;
            }
            if (c != '\\') {
                buf.push_back(c);
                continue;
            }
            if (i >= s.size()) return false;
            char e = s[i++];
            switch (e) {
                case '"': buf.push_back('"'); break;
                case '\\': buf.push_back('\\'); break;
                case '/': buf.push_back('/'); break;
                case 'b': buf.push_back('\b'); break;
                case 'f': buf.push_back('\f'); break;
                case 'n': buf.push_back('\n'); break;
                case 'r': buf.push_back('\r'); break;
                case 't': buf.push_back('\t'); break;
                case 'u': {
                    unsigned cp;
                    if (!hex4(cp)) return false;
                    if (cp >= 0xD800 && cp < 0xDC00 && i + 1 < s.size() && s[i] == '\\' && s[i + 1] == 'u') {
                        i += 2;
                        unsigned lo;
                        if (!hex4(lo)) return false;
                        cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                    }
                    utf8(buf, cp);
                    break;
                }
                default: return false;
            }
        }
        return false;
    }

    bool skip() {
        if (++depth > 64) return false;
        ws();
        bool ok = false;
        if (i >= s.size()) {
            ok = false;
        } else if (s[i] == '"') {
            ok = str(nullptr);
        } else if (s[i] == '{' || s[i] == '[') {
            char open = s[i], close = open == '{' ? '}' : ']';
            ++i;
            ws();
            if (i < s.size() && s[i] == close) {
                ++i;
                ok = true;
            } else {
                ok = true;
                while (ok) {
                    ws();
                    if (open == '{') {
                        ok = str(nullptr);
                        ws();
                        ok = ok && i < s.size() && s[i++] == ':';
                    }
                    ok = ok && skip();
                    ws();
                    if (!ok || i >= s.size()) { ok = false; break; }
                    if (s[i] == ',') { ++i; continue; }
                    if (s[i] == close) { ++i; break; }
                    ok = false;
                }
            }
        } else {
            std::size_t st = i;
            while (i < s.size() && s[i] != ',' && s[i] != '}' && s[i] != ']' &&
                   !std::isspace(static_cast<unsigned char>(s[i])))
                ++i;
            ok = i > st;
        }
        --depth;
        return ok;
    }

    bool find(const std::vector<std::string>& path, std::size_t d, std::string* out) {
        ws();
        if (d == path.size()) return str(out);
        if (i >= s.size() || s[i] != '{') return false;
        ++i;
        for (;;) {
            ws();
            if (i < s.size() && s[i] == '}') return false;
            std::string key;
            if (!str(&key)) return false;
            ws();
            if (i >= s.size() || s[i++] != ':') return false;
            if (key == path[d]) return find(path, d + 1, out);
            if (!skip()) return false;
            ws();
            if (i < s.size() && s[i] == ',') { ++i; continue; }
            return false;
        }
    }
};

bool all_digits(const std::string& v) {
    if (v.empty()) return false;
    for (char c : v) if (c < '0' || c > '9') return false;
    return true;
}

}  // namespace

std::string secret(int id, const char* masked_hex) {
    (void)id;
    std::string hex = masked_hex ? masked_hex : "";
    if (hex.empty() || hex.size() % 2 != 0) return "";
    std::string key = salt();
    std::string out(hex.size() / 2, '\0');
    for (std::size_t i = 0; i < out.size(); ++i) {
        int hi = hexv(hex[i * 2]), lo = hexv(hex[i * 2 + 1]);
        if (hi < 0 || lo < 0) return "";
        out[i] = static_cast<char>(((hi << 4) | lo) ^ static_cast<unsigned char>(key[i % key.size()]));
    }
    return out;
}

std::string endpoint(int op, const std::string& arg) {
    switch (op) {
        case GATE_SETUP: return OBF("data/setup/data");
        case HOME_LIST: return OBF("data/home/list");
        case HOME_SECTION: return OBF("3/2/home/") + arg;
        case HOME_NEW_EPISODE: return OBF("data/home/list_new_episode");
        case SCHEDULE: return OBF("3/2/schedule/data");
        case EXPLORE_MOVIE: return OBF("3/2/explore/movie");
        case FIND: return OBF("data/movie/find");
        case DETAIL: return OBF("3/2/movie/detail/") + arg;
        case EPISODE_LIST: return OBF("3/2/movie/episode/") + arg;
        case TRAILER_MOVIE: return OBF("data/movie/trailer/list");
        case TRAILER_GLOBAL: return OBF("data/trailer/list");
        case STREAM: return OBF("3/2/episode/streamnew/") + arg;
        case EXPLORE_DATA: return OBF("3/2/explore/data");
        case EXPLORE_GENRE: return OBF("3/2/explore/genre");
        case EXPLORE_YEAR: return OBF("3/2/explore/year");
        default: return "";
    }
}

bool is_volatile(const std::string& path) {
    return path.find(OBF("streamnew")) != std::string::npos;
}

std::vector<std::string> explore_target(const std::string& kind) {
    std::string k;
    for (char c : kind) k.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    if (k == "genre") return {OBF("3/2/explore/movie_genre"), OBF("id_genre"), "0"};
    if (k == "type" || k == "tipe") return {OBF("3/2/explore/movie_type"), OBF("type"), "0"};
    if (k == "studio") return {OBF("3/2/explore/movie_studio"), OBF("studio"), "0"};
    if (k == "year" || k == "tahun") return {OBF("3/2/explore/movie_year"), OBF("year"), "1"};
    return {OBF("3/2/explore/movie"), OBF("keyword"), "0"};
}

std::vector<std::string> search_plan() {
    std::string em = OBF("3/2/explore/movie");
    std::vector<std::string> plan;
    plan.push_back(em + "|" + OBF("keyword") + "|1");
    plan.push_back(OBF("data/movie/find") + "|" + OBF("keyword") + "|0");
    for (const std::string& key : {OBF("query"), OBF("q"), OBF("search"), OBF("title"), OBF("name")})
        plan.push_back(em + "|" + key + "|1");
    return plan;
}

std::vector<std::string> schedule_days() {
    return {OBF("SENIN"), OBF("SELASA"), OBF("RABU"), OBF("KAMIS"), OBF("JUMAT"), OBF("SABTU"), OBF("MINGGU")};
}

std::string normalize_base(const std::string& raw_in) {
    std::size_t b = 0, e = raw_in.size();
    while (b < e && std::isspace(static_cast<unsigned char>(raw_in[b]))) ++b;
    while (e > b && std::isspace(static_cast<unsigned char>(raw_in[e - 1]))) --e;
    std::string raw = raw_in.substr(b, e - b);
    if (raw.empty()) return "";
    if (raw.compare(0, 2, "//") == 0) raw = "https:" + raw;

    std::size_t sep = raw.find("://");
    if (sep == std::string::npos) return "";
    std::string scheme;
    for (std::size_t k = 0; k < sep; ++k) scheme.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(raw[k]))));
    if (scheme != "http" && scheme != "https") return "";

    std::string rest = raw.substr(sep + 3);
    for (char c : rest) {
        if (std::isspace(static_cast<unsigned char>(c)) || c == '\\') return "";
    }
    std::size_t cut = rest.find_first_of("/?#");
    std::string authority = rest.substr(0, cut);
    std::string tail = cut == std::string::npos ? "" : rest.substr(cut);
    if (authority.empty() || authority.find('@') != std::string::npos) return "";

    std::string host = authority, port;
    std::size_t colon = authority.rfind(':');
    if (colon != std::string::npos && authority.find(']', colon) == std::string::npos) {
        host = authority.substr(0, colon);
        port = authority.substr(colon + 1);
        if (!port.empty() && !all_digits(port)) return "";
    }
    if (host.empty()) return "";

    if (scheme == "http") {
        if (port.empty() || port == "80") port = "";
        scheme = "https";
    } else if (port == "443") {
        port.clear();
    }
    if (tail.find_first_of("?#") != std::string::npos) return "!q";

    while (!tail.empty() && tail.back() == '/') tail.pop_back();
    return scheme + "://" + host + (port.empty() ? "" : ":" + port) + tail + "/";
}

std::string gate_domain(const std::string& json) {
    Json p(json);
    std::string out;
    if (!p.find({OBF("data"), OBF("domain_api"), OBF("value")}, 0, &out)) return "";
    return out;
}

}  // namespace ax

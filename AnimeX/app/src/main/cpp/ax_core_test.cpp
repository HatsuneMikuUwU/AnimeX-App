
#include <cstdio>
#include <cstdlib>

#include "ax_core.h"

static int fails = 0;
#define CHECK_EQ(a, b)                                                                         \
    do {                                                                                       \
        std::string x = (a), y = (b);                                                          \
        if (x != y) { std::printf("FAIL line %d: '%s' != '%s'\n", __LINE__, x.c_str(), y.c_str()); ++fails; } \
    } while (0)

int main() {

    const std::string salt = "com.uwu.animex:v1";
    std::string plain = "hello-https://x.y", hex;
    for (size_t i = 0; i < plain.size(); ++i) {
        char buf[3];
        std::snprintf(buf, sizeof buf, "%02x", (plain[i] ^ salt[i % salt.size()]) & 0xff);
        hex += buf;
    }
    CHECK_EQ(ax::secret(0, hex.c_str()), plain);
    CHECK_EQ(ax::secret(0, ""), "");
    CHECK_EQ(ax::secret(0, "zz"), "");

    CHECK_EQ(ax::endpoint(ax::DETAIL, "123"), "3/2/movie/detail/123");
    CHECK_EQ(ax::endpoint(ax::HOME_SECTION, "hot"), "3/2/home/hot");
    CHECK_EQ(ax::endpoint(ax::STREAM, "9"), "3/2/episode/streamnew/9");
    CHECK_EQ(ax::endpoint(ax::GATE_SETUP, ""), "data/setup/data");
    CHECK_EQ(ax::endpoint(ax::EPISODE_LIST, "5"), "3/2/movie/episode/5");
    CHECK_EQ(ax::endpoint(ax::AUTH_LOGIN, ""), "auth/login");
    CHECK_EQ(ax::endpoint(ax::AUTH_REGISTER, ""), "auth/register");
    CHECK_EQ(ax::endpoint(ax::USER_PROFILE_DATA, ""), "3/2/user/profile/data");
    CHECK_EQ(ax::endpoint(ax::CHAT_DATA, ""), "3/2/chat/data");
    CHECK_EQ(ax::endpoint(ax::CHAT_DO, ""), "3/2/chat/do");
    CHECK_EQ(ax::endpoint(ax::MOVIE_DISCUSSION_LIST, ""), "data/movie/discussion/list");
    CHECK_EQ(ax::endpoint(ax::MOVIE_DISCUSSION_DO, ""), "data/movie/discussion/do");
    CHECK_EQ(ax::endpoint(ax::MOVIE_DISCUSSION_DELETE, ""), "data/movie/discussion/delete");
    CHECK_EQ(ax::endpoint(99, ""), "");
    if (!ax::is_volatile("3/2/episode/streamnew/1") || ax::is_volatile("3/2/movie/detail/1")) ++fails;
    if (!ax::is_volatile("auth/login") || !ax::is_volatile("3/2/chat/data") || !ax::is_volatile("data/movie/discussion/list")) ++fails;

    auto t = ax::explore_target("TAHUN");
    CHECK_EQ(t[0], "3/2/explore/movie_year");
    CHECK_EQ(t[1], "year");
    CHECK_EQ(t[2], "1");
    CHECK_EQ(ax::explore_target("x")[1], "keyword");

    auto plan = ax::search_plan();
    CHECK_EQ(std::to_string(plan.size()), "7");
    CHECK_EQ(plan[1], "data/movie/find|keyword|0");
    CHECK_EQ(ax::schedule_days()[6], "MINGGU");

    CHECK_EQ(ax::normalize_base("http://api.example.com"), "https://api.example.com/");
    CHECK_EQ(ax::normalize_base(" https://a.b/v1// "), "https://a.b/v1/");
    CHECK_EQ(ax::normalize_base("//a.b:8443/x"), "https://a.b:8443/x/");
    CHECK_EQ(ax::normalize_base("http://a.b:80/"), "https://a.b/");
    CHECK_EQ(ax::normalize_base("https://a.b:443"), "https://a.b/");
    CHECK_EQ(ax::normalize_base("https://a.b/x?y=1"), "!q");
    CHECK_EQ(ax::normalize_base("https://a.b/#f"), "!q");
    CHECK_EQ(ax::normalize_base("ftp://a.b"), "");
    CHECK_EQ(ax::normalize_base("https://u:p@a.b"), "");
    CHECK_EQ(ax::normalize_base("a.b"), "");
    CHECK_EQ(ax::normalize_base("https://a.b:xx"), "");

    CHECK_EQ(ax::gate_domain(R"({"error":false,"x":[1,{"a":"b"},null],"data":{"k":1,"domain_api":{"value":"https:\/\/api.z\u00e9.id"}}})"),
             "https://api.z\xc3\xa9.id");
    CHECK_EQ(ax::gate_domain(R"({"data":{"domain_api":null}})"), "");
    CHECK_EQ(ax::gate_domain("not json"), "");
    CHECK_EQ(ax::gate_domain(R"({"data":{"domain_api":{"value":"u"}})"), "u");

    std::printf(fails ? "FAILED (%d)\n" : "OK\n", fails);
    return fails ? 1 : 0;
}

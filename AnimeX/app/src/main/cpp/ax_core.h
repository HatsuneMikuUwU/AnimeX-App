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
};

// Decoded secret value for the given id, empty when not configured.
std::string secret(int id, const char* masked_hex);

// API path (relative to base URL) for an operation. Empty when op is unknown.
std::string endpoint(int op, const std::string& arg);

// True for responses that must never be cached.
bool is_volatile(const std::string& path);

// {path, filterKey, "1" if year-like else "0"} for an explore kind.
std::vector<std::string> explore_target(const std::string& kind);

// Ordered search attempts, each "path|queryKey|sortFlag(1/0)".
std::vector<std::string> search_plan();

std::vector<std::string> schedule_days();

// Returns normalized base ("https://host/path/"), "" if invalid,
// "!q" if it carries query/fragment.
std::string normalize_base(const std::string& raw);

// Extracts data.domain_api.value from the gate response, "" if absent.
std::string gate_domain(const std::string& json);

}  // namespace ax

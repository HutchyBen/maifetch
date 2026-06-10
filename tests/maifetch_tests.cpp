#include "config.hpp"
#include "maitea_client.hpp"
#include "output.hpp"

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <map>
#include <stdexcept>

namespace {

void require(bool condition, const char* message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

maifetch::EnvGetter env_from(std::map<std::string, std::string> values) {
  return [values = std::move(values)](std::string_view key) -> std::optional<std::string> {
    if (const auto it = values.find(std::string(key)); it != values.end()) {
      return it->second;
    }
    return std::nullopt;
  };
}

std::filesystem::path temp_config(std::string_view text) {
  auto path = std::filesystem::temp_directory_path() / "maifetch-test-config.json";
  std::ofstream file(path);
  file << text;
  return path;
}

void test_config_precedence() {
  const auto config_path = temp_config(R"({"accessToken":"file-token","baseUrl":"https://file.example","logoSize":8,"scoreCount":2,"noColor":false})");
  const auto config = maifetch::load_config(
      {"--base-url", "https://cli.example", "--score-count", "6", "--no-color"},
      env_from({{"MAIFETCH_CONFIG_FILE", config_path.string()},
                {"MAIFETCH_TOKEN", "env-token"},
                {"MAIFETCH_BASE_URL", "https://env.example"},
                {"MAIFETCH_LOGO_SIZE", "0"},
                {"MAIFETCH_SCORE_COUNT", "5"}}));

  require(config.access_token == "env-token", "environment should override config token");
  require(config.base_url == "https://cli.example", "CLI should override environment base URL");
  require(config.logo_size == 0, "environment should override config logo size");
  require(config.score_count == 6, "CLI should override environment score count");
  require(config.no_color, "CLI no-color should be enabled");
}

void test_config_validation() {
  bool threw = false;
  try {
    (void)maifetch::load_config({"--access-token", "token", "--score-count", "13"}, env_from({}));
  } catch (const std::runtime_error& error) {
    threw = std::string(error.what()) == "score count cannot be higher than 12";
  }
  require(threw, "score count validation should match original limit");
}

void test_help_text() {
  const auto help = maifetch::help_text();
  require(help.find("--access-token") != std::string::npos, "help should document token option");
  require(help.find("--base-url") != std::string::npos, "help should document base URL option");
  require(help.find("--no-color") != std::string::npos, "help should document no-color option");
}

void test_api_json_parsing() {
  const auto profiles = maifetch::parse_profiles_response(R"({
    "data": [{
      "id": 42,
      "name": "Kimi",
      "rating": 1523,
      "rating_highest": 1600,
      "level": 33,
      "play_stats": { "total": 91 },
      "options": { "icon": { "png": "https://example.test/icon.png" } }
    }]
  })");
  require(profiles.size() == 1, "one profile should parse");
  require(profiles.front().id == 42, "profile id should parse");
  require(profiles.front().icon_png == "https://example.test/icon.png", "profile icon URL should parse");

  const auto plays = maifetch::parse_plays_response(R"({
    "data": [{
      "song": { "name": { "en": "World Vanquisher" } },
      "difficulty_level": { "value": "master" },
      "score_formatted": "1,000,000",
      "achievement_formatted": "100.5000",
      "rank": "SSS+",
      "full_combo_label": "FC"
    }]
  })");
  require(plays.size() == 1, "one play should parse");
  require(plays.front().song_name_en == "World Vanquisher", "play song should parse");
  require(plays.front().full_combo_label.value_or("") == "FC", "full combo label should parse");
  require(plays.front().score_detail.tap.perfect == 0, "missing score detail should default safely");
}

void test_api_wrapper_surface_parsing() {
  const auto tracks = maifetch::parse_tracks_response(R"({
    "data": [{
      "id": 10,
      "code": "mai-test",
      "name": { "en": "Test Track", "jp": "JP Track" },
      "artist": { "en": "Test Artist", "jp": "JP Artist" }
    }]
  })");
  require(tracks.size() == 1, "one track should parse");
  require(tracks.front().name.en == "Test Track", "track English name should parse");
  require(tracks.front().artist.jp == "JP Artist", "track Japanese artist should parse");

  const auto play_page = maifetch::parse_plays_page_response(R"({
    "data": [{
      "id": 77,
      "achievement": 1005000,
      "track": 10,
      "score": 1000000,
      "score_formatted": "1,000,000",
      "achievement_formatted": "100.5000",
      "score_detail": { "tap": { "perfect": 12, "great": 1, "good": 0, "bad": 0 } },
      "rank": "SSS+",
      "full_combo": 1,
      "full_combo_label": "FC",
      "is_high_score": true,
      "is_all_perfect": false,
      "is_track_skip": false,
      "difficulty_level": { "key": 4, "value": "master", "label": "Master" },
      "play_date_unix": 1700000000,
      "song": { "id": 10, "code": "mai-test", "name": { "en": "Test Track" }, "artist": { "en": "Test Artist" } },
      "player": { "id": 42, "name": "Kimi", "rating": 1523, "rating_highest": 1600, "level": 33, "play_stats": { "total": 91 } }
    }],
    "links": { "first": "https://maitea.app/api/v1/plays?page=1", "last": "https://maitea.app/api/v1/plays?page=2", "prev": null, "next": "https://maitea.app/api/v1/plays?page=2" },
    "meta": { "current_page": 1, "from": 1, "last_page": 2, "path": "https://maitea.app/api/v1/plays", "per_page": 15, "to": 1, "total": 16 }
  })");
  require(play_page.data.size() == 1, "play page data should parse");
  require(play_page.data.front().is_high_score, "play high-score flag should parse");
  require(play_page.data.front().score_detail.tap.perfect == 12, "play score detail should parse");
  require(play_page.links.next.value_or("") == "https://maitea.app/api/v1/plays?page=2", "next page URL should parse");
  require(play_page.meta.total == 16, "page metadata total should parse");

  const auto score_page = maifetch::parse_scores_page_response(R"({
    "data": [{
      "id": 88,
      "achievement": 1009000,
      "achievement_formatted": "100.9000",
      "score": 1000000,
      "score_formatted": "1,000,000",
      "rank": "SSS+",
      "full_combo": 2,
      "full_combo_label": "AP",
      "is_all_perfect": true,
      "is_all_perfect_plus": false,
      "difficulty_level": { "key": 4, "value": "master", "label": "Master" },
      "song": { "id": 10, "code": "mai-test", "name": { "en": "Test Track" }, "artist": { "en": "Test Artist" } },
      "player": { "id": 42, "name": "Kimi", "rating": 1523, "rating_highest": 1600, "level": 33, "play_stats": { "total": 91 } }
    }],
    "links": { "first": "first", "last": "last", "prev": null, "next": null },
    "meta": { "current_page": 1, "from": 1, "last_page": 1, "path": "scores", "per_page": 15, "to": 1, "total": 1 }
  })");
  require(score_page.data.size() == 1, "score page data should parse");
  require(score_page.data.front().is_all_perfect, "score all-perfect flag should parse");
  require(score_page.data.front().difficulty_level.label == "Master", "score difficulty label should parse");

  const auto status = maifetch::parse_status_response(R"({
    "webui": {
      "api": "ok",
      "db_read": { "status": "ok", "query_time": "1ms" },
      "db_write": { "status": "ok", "query_time": "2ms" }
    },
    "game": { "status": "online" },
    "last_updated": 1700000000
  })");
  require(status.webui.api == "ok", "status API field should parse");
  require(status.webui.db_write.query_time == "2ms", "status write DB query time should parse");
  require(status.game.status == "online", "game status should parse");
  require(status.last_updated == 1700000000, "last updated timestamp should parse");
}

void test_output_format() {
  maifetch::Profile profile{
      .id = 42,
      .name = "Ｋｉｍｉ",
      .rating = 1523,
      .rating_highest = 1600,
      .level = 33,
      .total_credits = 91,
      .icon_png = "",
  };
  std::vector<maifetch::Play> plays{
      {.song_name_en = "World Vanquisher",
       .difficulty = "master",
       .score_formatted = "1,000,000",
       .achievement_formatted = "100.5000",
       .rank = "SSS+",
       .full_combo_label = "FC"},
  };
  const auto output = maifetch::render_output(profile, plays, 0, 4, {}, true);
  require(output.find("Kimi") != std::string::npos, "full-width name should normalize");
  require(output.find("ID") != std::string::npos, "ID label should render");
  require(output.find("15.23 / 16.00") != std::string::npos, "rating should keep two decimals");
  require(output.find("World Vanquisher") != std::string::npos, "recent play should render");
  require(output.find("\033") == std::string::npos, "no-color output should omit ANSI escapes");
}

}  // namespace

int main() {
  try {
    test_config_precedence();
    test_config_validation();
    test_help_text();
    test_api_json_parsing();
    test_api_wrapper_surface_parsing();
    test_output_format();
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
  return 0;
}

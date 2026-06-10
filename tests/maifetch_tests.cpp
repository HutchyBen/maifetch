#include "maifetch.hpp"

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <utility>

namespace {

void require(bool condition, const std::string& message) {
  if (!condition) {
    std::cerr << "FAIL: " << message << '\n';
    std::exit(1);
  }
}

maifetch::EnvReader env_from(std::map<std::string, std::string> values) {
  return [values = std::move(values)](const std::string& name) -> std::optional<std::string> {
    const auto found = values.find(name);
    if (found == values.end()) {
      return std::nullopt;
    }
    return found->second;
  };
}

std::filesystem::path write_config(const std::string& text) {
  const auto dir = std::filesystem::temp_directory_path() / "maifetch-cpp20-tests";
  std::filesystem::create_directories(dir);
  const auto file = dir / "maifetch.json";
  std::ofstream out(file);
  out << text;
  return file;
}

void config_priority_test() {
  const auto config_file = write_config(R"({
    "accessToken": "file-token",
    "scoreCount": 2,
    "logoSize": 8,
    "baseUrl": "https://file.example"
  })");

  const auto config = maifetch::load_config(
      {"--config-file", config_file.string(),
       "--score-count", "6",
       "--logo-size=0",
       "--base-url=https://cli.example"},
      env_from({
          {"MAIFETCH_TOKEN", "env-token"},
          {"MAIFETCH_SCORE_COUNT", "5"},
          {"MAIFETCH_LOGO_SIZE", "12"},
          {"MAIFETCH_BASE_URL", "https://env.example"},
      }),
      config_file.parent_path());

  require(config.access_token == "env-token", "environment overrides config-file token");
  require(config.score_count == 6, "CLI score count overrides environment");
  require(config.logo_size == 0, "CLI logo size can disable logo");
  require(config.base_url == "https://cli.example", "CLI base URL overrides environment");
  require(config.config_file == config_file, "explicit config file is retained");
}

void env_config_file_test() {
  const auto config_file = write_config(R"({
    "accessToken": "file-token",
    "scoreCount": 3,
    "baseUrl": "https://file.example"
  })");

  const auto config = maifetch::load_config(
      {},
      env_from({
          {"MAIFETCH_CONFIG_FILE", config_file.string()},
      }),
      std::filesystem::temp_directory_path() / "maifetch-missing-default");

  require(config.access_token == "file-token", "environment config path is loaded");
  require(config.score_count == 3, "environment config values are applied");
  require(config.base_url == "https://file.example", "config base URL is applied");
  require(config.config_file == config_file, "environment config file is retained");
}

void config_validation_test() {
  bool saw_missing_token = false;
  try {
    (void)maifetch::load_config({}, env_from({}), std::filesystem::temp_directory_path());
  } catch (const maifetch::ConfigError& err) {
    saw_missing_token = std::string(err.what()).find("access token") != std::string::npos;
  }
  require(saw_missing_token, "missing access token is rejected");

  bool saw_score_limit = false;
  try {
    (void)maifetch::load_config({"--access-token", "token", "--score-count", "13"},
                                env_from({}),
                                std::filesystem::temp_directory_path());
  } catch (const maifetch::ConfigError& err) {
    saw_score_limit = std::string(err.what()).find("score count") != std::string::npos;
  }
  require(saw_score_limit, "score count over 12 is rejected");
}

void api_json_test() {
  const std::string full_width_mai = "\xEF\xBC\xAD\xEF\xBC\xA1\xEF\xBC\xA9";
  const auto profiles = maifetch::parse_profiles_response(R"({
    "data": [{
      "id": 42,
      "name": "\uFF2D\uFF21\uFF29",
      "rating": 1234,
      "rating_highest": 1500,
      "level": 17,
      "play_stats": { "total": 99 },
      "options": { "icon": { "id": 1, "png": "https://example.test/icon.png", "webp": "" } }
    }]
  })");

  require(profiles.size() == 1, "one profile parsed");
  require(profiles.front().id == 42, "profile id parsed");
  require(profiles.front().name == full_width_mai, "profile name parsed");
  require(profiles.front().total_credits == 99, "profile total credits parsed");
  require(profiles.front().icon.png == "https://example.test/icon.png", "profile icon parsed");

  const auto plays = maifetch::parse_plays_response(R"({
    "data": [{
      "score_formatted": "1,000,000",
      "achievement_formatted": "100.5000",
      "rank": "SSS+",
      "full_combo_label": "FC",
      "difficulty_level": { "value": "master" },
      "song": { "name": { "en": "Test Song" } }
    }]
  })");

  require(plays.size() == 1, "one play parsed");
  require(plays.front().song_name_en == "Test Song", "song name parsed");
  require(plays.front().difficulty == "master", "difficulty parsed");
  require(plays.front().full_combo_label.value_or("") == "FC", "full-combo label parsed");
}

void api_wrapper_json_test() {
  const auto scores = maifetch::parse_scores_response(R"({
    "data": [{
      "id": 101,
      "achievement": 1005000,
      "achievement_formatted": "100.5000",
      "score": 1000000,
      "score_formatted": "1,000,000",
      "rank": "SSS+",
      "full_combo_label": "FC",
      "difficulty_level": { "value": "expert" },
      "song": { "name": { "en": "Score Song" } }
    }]
  })");

  require(scores.size() == 1, "one score parsed");
  require(scores.front().id == 101, "score id parsed");
  require(scores.front().score == 1000000, "score value parsed");
  require(scores.front().song_name_en == "Score Song", "score song parsed");
  require(scores.front().difficulty == "expert", "score difficulty parsed");

  const auto tracks = maifetch::parse_tracks_response(R"({
    "data": [{
      "id": 9,
      "code": "track-code",
      "name": { "en": "Track EN", "jp": "Track JP" },
      "artist": { "en": "Artist EN", "jp": "Artist JP" }
    }]
  })");

  require(tracks.size() == 1, "one track parsed");
  require(tracks.front().id == 9, "track id parsed");
  require(tracks.front().code == "track-code", "track code parsed");
  require(tracks.front().name.en == "Track EN", "track name parsed");
  require(tracks.front().artist.jp == "Artist JP", "track artist parsed");

  const auto status = maifetch::parse_status_response(R"({
    "webui": {
      "api": "ok",
      "db_read": { "status": "ok", "query_time": "1ms" },
      "db_write": { "status": "ok", "query_time": "2ms" }
    },
    "game": { "status": "online" },
    "last_updated": 1234567890
  })");

  require(status.webui.api == "ok", "status webui api parsed");
  require(status.webui.db_read.query_time == "1ms", "status db read parsed");
  require(status.webui.db_write.status == "ok", "status db write parsed");
  require(status.game.status == "online", "game status parsed");
  require(status.last_updated == 1234567890, "status timestamp parsed");
}

void formatting_test() {
  maifetch::Profile profile;
  profile.id = 7;
  profile.name = "\xEF\xBC\xAD\xEF\xBC\xA1\xEF\xBC\xA9";
  profile.rating = 1234;
  profile.rating_highest = 1567;
  profile.level = 20;
  profile.total_credits = 44;

  maifetch::Play play;
  play.song_name_en = "Song";
  play.difficulty = "expert";
  play.score_formatted = "999,999";
  play.achievement_formatted = "99.9999";
  play.rank = "SS";

  maifetch::Config config;
  config.access_token = "token";
  config.logo_size = 0;
  config.score_count = 1;
  config.no_color = true;

  const auto output = maifetch::format_output(profile, {play}, config);
  require(output.find("MAI") != std::string::npos, "full-width profile name is normalized");
  require(output.find("Rating: 12.34 / 15.67") != std::string::npos, "rating is formatted");
  require(output.find("Recent Scores:") != std::string::npos, "recent score heading is printed");
  require(output.find("Song  Expert") != std::string::npos, "play line is printed");
}

}  // namespace

int main() {
  config_priority_test();
  env_config_file_test();
  config_validation_test();
  api_json_test();
  api_wrapper_json_test();
  formatting_test();
  std::cout << "maifetch_tests passed\n";
  return 0;
}

#pragma once

#include <cstdint>
#include <filesystem>
#include <functional>
#include <map>
#include <optional>
#include <stdexcept>
#include <string>
#include <variant>
#include <vector>

namespace maifetch {

class ConfigError : public std::runtime_error {
public:
  using std::runtime_error::runtime_error;
};

class ApiError : public std::runtime_error {
public:
  using std::runtime_error::runtime_error;
};

struct Config {
  std::string access_token;
  std::filesystem::path config_file;
  std::uint32_t score_count = 4;
  int logo_size = 20;
  std::string base_url = "https://maitea.app";
  bool no_color = false;
};

struct Image {
  int id = 0;
  std::string png;
  std::string webp;
};

struct LocalizedText {
  std::string en;
  std::string jp;
};

struct TrackInfo {
  int id = 0;
  std::string code;
  LocalizedText name;
  LocalizedText artist;
};

struct Profile {
  int id = 0;
  std::string name;
  int rating = 0;
  int rating_highest = 0;
  int level = 0;
  int total_credits = 0;
  Image icon;
};

struct Play {
  int id = 0;
  int score = 0;
  int achievement = 0;
  std::string song_name_en;
  std::string difficulty;
  std::string score_formatted;
  std::string achievement_formatted;
  std::string rank;
  std::optional<std::string> full_combo_label;
};

struct Score {
  int id = 0;
  int score = 0;
  int achievement = 0;
  std::string song_name_en;
  std::string difficulty;
  std::string score_formatted;
  std::string achievement_formatted;
  std::string rank;
  std::optional<std::string> full_combo_label;
};

struct StatusProbe {
  std::string status;
  std::string query_time;
};

struct WebStatus {
  std::string api;
  StatusProbe db_read;
  StatusProbe db_write;
};

struct GameStatus {
  std::string status;
};

struct Status {
  WebStatus webui;
  GameStatus game;
  std::int64_t last_updated = 0;
};

class Json {
public:
  using object = std::map<std::string, Json>;
  using array = std::vector<Json>;
  using value = std::variant<std::nullptr_t, bool, double, std::string, object, array>;

  Json();
  explicit Json(std::nullptr_t);
  explicit Json(bool input);
  explicit Json(double input);
  explicit Json(std::string input);
  explicit Json(object input);
  explicit Json(array input);

  [[nodiscard]] bool is_null() const;
  [[nodiscard]] const object& as_object() const;
  [[nodiscard]] const array& as_array() const;
  [[nodiscard]] const std::string& as_string() const;
  [[nodiscard]] double as_number() const;
  [[nodiscard]] bool as_bool() const;

private:
  value value_;
};

using EnvReader = std::function<std::optional<std::string>(const std::string&)>;

std::filesystem::path default_user_config_dir();
std::optional<std::string> default_env_reader(const std::string& name);
Json parse_json(const std::string& input);
Config load_config(const std::vector<std::string>& args,
                   const EnvReader& env_reader = default_env_reader,
                   std::filesystem::path default_config_dir = default_user_config_dir());
std::string help_text();

std::vector<Profile> parse_profiles_response(const std::string& json_text);
std::vector<Play> parse_plays_response(const std::string& json_text);
std::vector<Score> parse_scores_response(const std::string& json_text);
std::vector<TrackInfo> parse_tracks_response(const std::string& json_text);
Status parse_status_response(const std::string& json_text);
std::vector<std::string> create_info_lines(const Profile& profile,
                                           const std::vector<Play>& plays,
                                           std::uint32_t score_count,
                                           bool no_color);
std::string format_output(const Profile& profile,
                          const std::vector<Play>& plays,
                          const Config& config);

class MaiTeaClient {
public:
  explicit MaiTeaClient(Config config);
  [[nodiscard]] std::vector<Profile> get_profiles() const;
  [[nodiscard]] std::vector<Play> get_recent_plays() const;
  [[nodiscard]] std::vector<Play> get_all_recent_plays() const;
  [[nodiscard]] std::vector<Score> get_best_scores() const;
  [[nodiscard]] std::vector<Score> get_all_best_scores() const;
  [[nodiscard]] std::vector<TrackInfo> get_tracks() const;
  [[nodiscard]] Status get_status() const;

private:
  [[nodiscard]] std::string get(const std::string& path) const;

  Config config_;
};

}  // namespace maifetch

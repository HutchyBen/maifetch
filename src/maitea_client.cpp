#include "maitea_client.hpp"

#include <nlohmann/json.hpp>

#include <map>
#include <stdexcept>
#include <utility>

namespace maifetch {
namespace {

std::map<std::string, std::string> auth_headers(const std::string& token) {
  return {
      {"Authorization", "Bearer " + token},
      {"Content-Type", "application/json"},
      {"Accept", "application/json"},
  };
}

std::string api_url(std::string base_url, std::string_view path) {
  while (!base_url.empty() && base_url.back() == '/') {
    base_url.pop_back();
  }
  return base_url + std::string(path);
}

std::string read_string(const nlohmann::json& json, std::initializer_list<std::string_view> path) {
  const nlohmann::json* current = &json;
  for (const auto key : path) {
    if (!current->is_object() || !current->contains(std::string(key))) {
      return "";
    }
    current = &current->at(std::string(key));
  }
  return current->is_string() ? current->get<std::string>() : "";
}

int read_int(const nlohmann::json& json, std::initializer_list<std::string_view> path) {
  const nlohmann::json* current = &json;
  for (const auto key : path) {
    if (!current->is_object() || !current->contains(std::string(key))) {
      return 0;
    }
    current = &current->at(std::string(key));
  }
  return current->is_number_integer() ? current->get<int>() : 0;
}

long long read_int64(const nlohmann::json& json, std::initializer_list<std::string_view> path) {
  const nlohmann::json* current = &json;
  for (const auto key : path) {
    if (!current->is_object() || !current->contains(std::string(key))) {
      return 0;
    }
    current = &current->at(std::string(key));
  }
  return current->is_number_integer() ? current->get<long long>() : 0;
}

bool read_bool(const nlohmann::json& json, std::initializer_list<std::string_view> path) {
  const nlohmann::json* current = &json;
  for (const auto key : path) {
    if (!current->is_object() || !current->contains(std::string(key))) {
      return false;
    }
    current = &current->at(std::string(key));
  }
  return current->is_boolean() ? current->get<bool>() : false;
}

std::optional<std::string> read_optional_string(const nlohmann::json& json, std::initializer_list<std::string_view> path) {
  const nlohmann::json* current = &json;
  for (const auto key : path) {
    if (!current->is_object() || !current->contains(std::string(key))) {
      return std::nullopt;
    }
    current = &current->at(std::string(key));
  }
  if (current->is_null()) {
    return std::nullopt;
  }
  return current->is_string() ? std::optional<std::string>(current->get<std::string>()) : std::nullopt;
}

Profile parse_profile_item(const nlohmann::json& item) {
  return Profile{
      .id = read_int(item, {"id"}),
      .name = read_string(item, {"name"}),
      .rating = read_int(item, {"rating"}),
      .rating_highest = read_int(item, {"rating_highest"}),
      .level = read_int(item, {"level"}),
      .total_credits = read_int(item, {"play_stats", "total"}),
      .icon_png = read_string(item, {"options", "icon", "png"}),
  };
}

TrackInfo parse_track_item(const nlohmann::json& item) {
  return TrackInfo{
      .id = read_int(item, {"id"}),
      .code = read_string(item, {"code"}),
      .name =
          LocalizedText{
              .en = read_string(item, {"name", "en"}),
              .jp = read_string(item, {"name", "jp"}),
          },
      .artist =
          LocalizedText{
              .en = read_string(item, {"artist", "en"}),
              .jp = read_string(item, {"artist", "jp"}),
          },
  };
}

DifficultyLevel parse_difficulty_level(const nlohmann::json& item) {
  return DifficultyLevel{
      .key = read_int(item, {"difficulty_level", "key"}),
      .value = read_string(item, {"difficulty_level", "value"}),
      .label = read_string(item, {"difficulty_level", "label"}),
  };
}

Notes parse_notes(const nlohmann::json& item, std::string_view key) {
  return Notes{
      .perfect = read_int(item, {"score_detail", key, "perfect"}),
      .great = read_int(item, {"score_detail", key, "great"}),
      .good = read_int(item, {"score_detail", key, "good"}),
      .bad = read_int(item, {"score_detail", key, "bad"}),
  };
}

Play parse_play_item(const nlohmann::json& item) {
  const TrackInfo song = item.contains("song") && item.at("song").is_object() ? parse_track_item(item.at("song")) : TrackInfo{};
  const Profile player = item.contains("player") && item.at("player").is_object() ? parse_profile_item(item.at("player")) : Profile{};
  return Play{
      .id = read_int(item, {"id"}),
      .achievement = read_int(item, {"achievement"}),
      .song_name_en = song.name.en,
      .difficulty = read_string(item, {"difficulty_level", "value"}),
      .track = read_int(item, {"track"}),
      .score = read_int(item, {"score"}),
      .score_formatted = read_string(item, {"score_formatted"}),
      .achievement_formatted = read_string(item, {"achievement_formatted"}),
      .score_detail =
          ScoreDetail{
              .hits = parse_notes(item, "hits"),
              .tap = parse_notes(item, "tap"),
              .hold = parse_notes(item, "hold"),
              .slide = parse_notes(item, "slide"),
              .break_notes = parse_notes(item, "break"),
          },
      .rank = read_string(item, {"rank"}),
      .full_combo = read_int(item, {"full_combo"}),
      .full_combo_label = read_optional_string(item, {"full_combo_label"}),
      .is_high_score = read_bool(item, {"is_high_score"}),
      .is_all_perfect = read_bool(item, {"is_all_perfect"}),
      .is_track_skip = read_bool(item, {"is_track_skip"}),
      .difficulty_level = parse_difficulty_level(item),
      .play_date_unix = read_int(item, {"play_date_unix"}),
      .song = song,
      .player = player,
  };
}

Score parse_score_item(const nlohmann::json& item) {
  const TrackInfo song = item.contains("song") && item.at("song").is_object() ? parse_track_item(item.at("song")) : TrackInfo{};
  const Profile player = item.contains("player") && item.at("player").is_object() ? parse_profile_item(item.at("player")) : Profile{};
  return Score{
      .id = read_int(item, {"id"}),
      .achievement = read_int(item, {"achievement"}),
      .achievement_formatted = read_string(item, {"achievement_formatted"}),
      .score = read_int(item, {"score"}),
      .score_formatted = read_string(item, {"score_formatted"}),
      .rank = read_string(item, {"rank"}),
      .full_combo = read_int(item, {"full_combo"}),
      .full_combo_label = read_optional_string(item, {"full_combo_label"}),
      .is_all_perfect = read_bool(item, {"is_all_perfect"}),
      .is_all_perfect_plus = read_bool(item, {"is_all_perfect_plus"}),
      .difficulty_level = parse_difficulty_level(item),
      .song = song,
      .player = player,
  };
}

PageLinks parse_page_links(const nlohmann::json& json) {
  return PageLinks{
      .first = read_string(json, {"links", "first"}),
      .last = read_string(json, {"links", "last"}),
      .prev = read_optional_string(json, {"links", "prev"}),
      .next = read_optional_string(json, {"links", "next"}),
  };
}

PageMeta parse_page_meta(const nlohmann::json& json) {
  return PageMeta{
      .current_page = read_int(json, {"meta", "current_page"}),
      .from = read_int(json, {"meta", "from"}),
      .last_page = read_int(json, {"meta", "last_page"}),
      .path = read_string(json, {"meta", "path"}),
      .per_page = read_int(json, {"meta", "per_page"}),
      .to = read_int(json, {"meta", "to"}),
      .total = read_int(json, {"meta", "total"}),
  };
}

}  // namespace

MaiTeaClient::MaiTeaClient(std::string access_token, std::string base_url, HttpClient http)
    : access_token_(std::move(access_token)), base_url_(std::move(base_url)), http_(std::move(http)) {}

nlohmann::json MaiTeaClient::get_json(std::string path) const {
  const auto response = http_.get(api_url(base_url_, path), auth_headers(access_token_));
  return nlohmann::json::parse(response.body);
}

std::vector<Profile> parse_profiles_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  std::vector<Profile> result;
  for (const auto& item : json.value("data", nlohmann::json::array())) {
    result.push_back(parse_profile_item(item));
  }
  return result;
}

std::vector<TrackInfo> parse_tracks_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  std::vector<TrackInfo> result;
  for (const auto& item : json.value("data", nlohmann::json::array())) {
    result.push_back(parse_track_item(item));
  }
  return result;
}

std::vector<Play> parse_plays_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  std::vector<Play> result;
  for (const auto& item : json.value("data", nlohmann::json::array())) {
    result.push_back(parse_play_item(item));
  }
  return result;
}

Page<std::vector<Play>> parse_plays_page_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  return Page<std::vector<Play>>{
      .data = parse_plays_response(body),
      .links = parse_page_links(json),
      .meta = parse_page_meta(json),
  };
}

Page<std::vector<Score>> parse_scores_page_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  std::vector<Score> scores;
  for (const auto& item : json.value("data", nlohmann::json::array())) {
    scores.push_back(parse_score_item(item));
  }
  return Page<std::vector<Score>>{
      .data = std::move(scores),
      .links = parse_page_links(json),
      .meta = parse_page_meta(json),
  };
}

Status parse_status_response(const std::string& body) {
  const auto json = nlohmann::json::parse(body);
  return Status{
      .webui =
          WebStatus{
              .api = read_string(json, {"webui", "api"}),
              .db_read =
                  DbStatus{
                      .status = read_string(json, {"webui", "db_read", "status"}),
                      .query_time = read_string(json, {"webui", "db_read", "query_time"}),
                  },
              .db_write =
                  DbStatus{
                      .status = read_string(json, {"webui", "db_write", "status"}),
                      .query_time = read_string(json, {"webui", "db_write", "query_time"}),
                  },
          },
      .game = GameStatus{.status = read_string(json, {"game", "status"})},
      .last_updated = read_int64(json, {"last_updated"}),
  };
}

std::vector<Profile> MaiTeaClient::profiles() const {
  return parse_profiles_response(get_json("/api/v1/profiles").dump());
}

std::vector<TrackInfo> MaiTeaClient::tracks() const {
  return parse_tracks_response(get_json("/api/v1/tracks").dump());
}

std::vector<Play> MaiTeaClient::plays() const {
  return plays_page().data;
}

Page<std::vector<Play>> MaiTeaClient::plays_page() const {
  return parse_plays_page_response(get_json("/api/v1/plays").dump());
}

Page<std::vector<Play>> MaiTeaClient::all_plays_page() const {
  return parse_plays_page_response(get_json("/api/v1/plays/all").dump());
}

Page<std::vector<Score>> MaiTeaClient::best_scores_page() const {
  return parse_scores_page_response(get_json("/api/v1/scores").dump());
}

Page<std::vector<Score>> MaiTeaClient::all_best_scores_page() const {
  return parse_scores_page_response(get_json("/api/v1/scores/all").dump());
}

Status MaiTeaClient::status() const {
  return parse_status_response(get_json("/api/status").dump());
}

std::vector<unsigned char> MaiTeaClient::download(std::string url) const {
  return http_.get(std::move(url)).bytes;
}

}  // namespace maifetch

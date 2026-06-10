#pragma once

#include "http_client.hpp"

#include <nlohmann/json.hpp>

#include <optional>
#include <string>
#include <vector>

namespace maifetch {

struct LocalizedText {
  std::string en;
  std::string jp;
};

struct Image {
  int id = 0;
  std::string png;
  std::string webp;
};

struct Profile {
  int id = 0;
  std::string name;
  int rating = 0;
  int rating_highest = 0;
  int level = 0;
  int total_credits = 0;
  std::string icon_png;
};

struct TrackInfo {
  int id = 0;
  std::string code;
  LocalizedText name;
  LocalizedText artist;
};

struct DifficultyLevel {
  int key = 0;
  std::string value;
  std::string label;
};

struct Notes {
  int perfect = 0;
  int great = 0;
  int good = 0;
  int bad = 0;
};

struct ScoreDetail {
  Notes hits;
  Notes tap;
  Notes hold;
  Notes slide;
  Notes break_notes;
};

struct Play {
  int id = 0;
  int achievement = 0;
  std::string song_name_en;
  std::string difficulty;
  int track = 0;
  int score = 0;
  std::string score_formatted;
  std::string achievement_formatted;
  ScoreDetail score_detail;
  std::string rank;
  int full_combo = 0;
  std::optional<std::string> full_combo_label;
  bool is_high_score = false;
  bool is_all_perfect = false;
  bool is_track_skip = false;
  DifficultyLevel difficulty_level;
  int play_date_unix = 0;
  TrackInfo song;
  Profile player;
};

struct Score {
  int id = 0;
  int achievement = 0;
  std::string achievement_formatted;
  int score = 0;
  std::string score_formatted;
  std::string rank;
  int full_combo = 0;
  std::optional<std::string> full_combo_label;
  bool is_all_perfect = false;
  bool is_all_perfect_plus = false;
  DifficultyLevel difficulty_level;
  TrackInfo song;
  Profile player;
};

struct PageLinks {
  std::string first;
  std::string last;
  std::optional<std::string> prev;
  std::optional<std::string> next;
};

struct PageMeta {
  int current_page = 0;
  int from = 0;
  int last_page = 0;
  std::string path;
  int per_page = 0;
  int to = 0;
  int total = 0;
};

template <class T>
struct Page {
  T data;
  PageLinks links;
  PageMeta meta;
};

struct DbStatus {
  std::string status;
  std::string query_time;
};

struct WebStatus {
  std::string api;
  DbStatus db_read;
  DbStatus db_write;
};

struct GameStatus {
  std::string status;
};

struct Status {
  WebStatus webui;
  GameStatus game;
  long long last_updated = 0;
};

class MaiTeaClient {
 public:
  explicit MaiTeaClient(std::string access_token, std::string base_url = "https://maitea.app", HttpClient http = {});

  std::vector<Profile> profiles() const;
  std::vector<TrackInfo> tracks() const;
  std::vector<Play> plays() const;
  Page<std::vector<Play>> plays_page() const;
  Page<std::vector<Play>> all_plays_page() const;
  Page<std::vector<Score>> best_scores_page() const;
  Page<std::vector<Score>> all_best_scores_page() const;
  Status status() const;
  std::vector<unsigned char> download(std::string url) const;

 private:
  nlohmann::json get_json(std::string path) const;

  std::string access_token_;
  std::string base_url_;
  HttpClient http_;
};

std::vector<Profile> parse_profiles_response(const std::string& body);
std::vector<TrackInfo> parse_tracks_response(const std::string& body);
std::vector<Play> parse_plays_response(const std::string& body);
Page<std::vector<Play>> parse_plays_page_response(const std::string& body);
Page<std::vector<Score>> parse_scores_page_response(const std::string& body);
Status parse_status_response(const std::string& body);

}  // namespace maifetch

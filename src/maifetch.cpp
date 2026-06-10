#include "maifetch.hpp"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <curl/curl.h>
#include <fstream>
#include <iomanip>
#include <initializer_list>
#include <memory>
#include <optional>
#include <sstream>
#include <string_view>
#include <utility>

namespace maifetch {
namespace {

class JsonParser {
public:
  explicit JsonParser(std::string_view input) : input_(input) {}

  Json parse() {
    auto result = parse_value();
    skip_ws();
    if (pos_ != input_.size()) {
      fail("unexpected trailing JSON");
    }
    return result;
  }

private:
  void fail(const std::string& message) const {
    throw ConfigError("invalid JSON: " + message);
  }

  [[nodiscard]] char peek() const {
    if (pos_ >= input_.size()) {
      return '\0';
    }
    return input_[pos_];
  }

  char consume() {
    if (pos_ >= input_.size()) {
      fail("unexpected end of input");
    }
    return input_[pos_++];
  }

  void expect(char expected) {
    const auto actual = consume();
    if (actual != expected) {
      fail(std::string("expected '") + expected + "'");
    }
  }

  void skip_ws() {
    while (pos_ < input_.size() &&
           std::isspace(static_cast<unsigned char>(input_[pos_])) != 0) {
      ++pos_;
    }
  }

  Json parse_value() {
    skip_ws();
    switch (peek()) {
      case '{':
        return Json(parse_object());
      case '[':
        return Json(parse_array());
      case '"':
        return Json(parse_string());
      case 't':
        consume_literal("true");
        return Json(true);
      case 'f':
        consume_literal("false");
        return Json(false);
      case 'n':
        consume_literal("null");
        return Json(nullptr);
      default:
        return Json(parse_number());
    }
  }

  void consume_literal(std::string_view literal) {
    if (input_.substr(pos_, literal.size()) != literal) {
      fail("unexpected literal");
    }
    pos_ += literal.size();
  }

  static void append_utf8(std::string& out, unsigned int codepoint) {
    if (codepoint <= 0x7F) {
      out.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7FF) {
      out.push_back(static_cast<char>(0xC0U | (codepoint >> 6U)));
      out.push_back(static_cast<char>(0x80U | (codepoint & 0x3FU)));
    } else {
      out.push_back(static_cast<char>(0xE0U | (codepoint >> 12U)));
      out.push_back(static_cast<char>(0x80U | ((codepoint >> 6U) & 0x3FU)));
      out.push_back(static_cast<char>(0x80U | (codepoint & 0x3FU)));
    }
  }

  std::string parse_string() {
    expect('"');
    std::string out;
    while (peek() != '"') {
      const auto chr = consume();
      if (chr != '\\') {
        out.push_back(chr);
        continue;
      }

      const auto escaped = consume();
      switch (escaped) {
        case '"':
        case '\\':
        case '/':
          out.push_back(escaped);
          break;
        case 'b':
          out.push_back('\b');
          break;
        case 'f':
          out.push_back('\f');
          break;
        case 'n':
          out.push_back('\n');
          break;
        case 'r':
          out.push_back('\r');
          break;
        case 't':
          out.push_back('\t');
          break;
        case 'u': {
          unsigned int codepoint = 0;
          for (int i = 0; i < 4; ++i) {
            const auto hex = consume();
            codepoint <<= 4U;
            if (hex >= '0' && hex <= '9') {
              codepoint += static_cast<unsigned int>(hex - '0');
            } else if (hex >= 'a' && hex <= 'f') {
              codepoint += static_cast<unsigned int>(hex - 'a' + 10);
            } else if (hex >= 'A' && hex <= 'F') {
              codepoint += static_cast<unsigned int>(hex - 'A' + 10);
            } else {
              fail("invalid unicode escape");
            }
          }
          append_utf8(out, codepoint);
          break;
        }
        default:
          fail("invalid escape sequence");
      }
    }
    expect('"');
    return out;
  }

  double parse_number() {
    const auto start = pos_;
    if (peek() == '-') {
      ++pos_;
    }
    while (std::isdigit(static_cast<unsigned char>(peek())) != 0) {
      ++pos_;
    }
    if (peek() == '.') {
      ++pos_;
      while (std::isdigit(static_cast<unsigned char>(peek())) != 0) {
        ++pos_;
      }
    }
    if (peek() == 'e' || peek() == 'E') {
      ++pos_;
      if (peek() == '+' || peek() == '-') {
        ++pos_;
      }
      while (std::isdigit(static_cast<unsigned char>(peek())) != 0) {
        ++pos_;
      }
    }
    if (start == pos_) {
      fail("expected value");
    }
    return std::stod(std::string(input_.substr(start, pos_ - start)));
  }

  Json::array parse_array() {
    expect('[');
    Json::array out;
    skip_ws();
    if (peek() == ']') {
      consume();
      return out;
    }
    while (true) {
      out.push_back(parse_value());
      skip_ws();
      const auto sep = consume();
      if (sep == ']') {
        break;
      }
      if (sep != ',') {
        fail("expected array comma");
      }
    }
    return out;
  }

  Json::object parse_object() {
    expect('{');
    Json::object out;
    skip_ws();
    if (peek() == '}') {
      consume();
      return out;
    }
    while (true) {
      skip_ws();
      const auto key = parse_string();
      skip_ws();
      expect(':');
      out.emplace(key, parse_value());
      skip_ws();
      const auto sep = consume();
      if (sep == '}') {
        break;
      }
      if (sep != ',') {
        fail("expected object comma");
      }
    }
    return out;
  }

  std::string_view input_;
  std::size_t pos_ = 0;
};

const Json& field(const Json::object& obj, const std::string& key) {
  static const Json null_json(nullptr);
  const auto found = obj.find(key);
  if (found == obj.end()) {
    return null_json;
  }
  return found->second;
}

std::string string_field(const Json::object& obj, const std::string& key) {
  const auto& value = field(obj, key);
  if (value.is_null()) {
    return {};
  }
  return value.as_string();
}

int int_field(const Json::object& obj, const std::string& key) {
  const auto& value = field(obj, key);
  if (value.is_null()) {
    return 0;
  }
  return static_cast<int>(value.as_number());
}

LocalizedText localized_text_field(const Json::object& obj, const std::string& key) {
  LocalizedText text;
  const auto& value = field(obj, key);
  if (value.is_null()) {
    return text;
  }
  const auto& text_obj = value.as_object();
  text.en = string_field(text_obj, "en");
  text.jp = string_field(text_obj, "jp");
  return text;
}

std::string read_file(const std::filesystem::path& file) {
  std::ifstream in(file);
  if (!in) {
    throw ConfigError("could not open config file: " + file.string());
  }
  std::ostringstream buffer;
  buffer << in.rdbuf();
  return buffer.str();
}

std::optional<int> parse_int(const std::string& input, const std::string& label) {
  try {
    std::size_t used = 0;
    const auto value = std::stoi(input, &used, 10);
    if (used != input.size()) {
      throw std::invalid_argument("trailing characters");
    }
    return value;
  } catch (const std::exception&) {
    throw ConfigError("invalid integer for " + label + ": " + input);
  }
}

std::optional<std::string> first_env(const EnvReader& env_reader,
                                     std::initializer_list<std::string_view> names) {
  for (const auto name : names) {
    if (auto value = env_reader(std::string(name))) {
      return value;
    }
  }
  return std::nullopt;
}

struct CurlGlobal {
  CurlGlobal() {
    if (curl_global_init(CURL_GLOBAL_DEFAULT) != CURLE_OK) {
      throw ApiError("could not initialize libcurl");
    }
  }

  ~CurlGlobal() {
    curl_global_cleanup();
  }
};

std::size_t write_response(char* data, std::size_t size, std::size_t count, void* user_data) {
  auto* output = static_cast<std::string*>(user_data);
  output->append(data, size * count);
  return size * count;
}

struct CliOptions {
  std::optional<std::string> access_token;
  std::optional<std::filesystem::path> config_file;
  std::optional<int> score_count;
  std::optional<int> logo_size;
  std::optional<std::string> base_url;
  bool no_color = false;
};

std::string take_value(const std::vector<std::string>& args, std::size_t& index, std::string_view name) {
  if (index + 1 >= args.size()) {
    throw ConfigError(std::string("missing value for ") + std::string(name));
  }
  ++index;
  return args[index];
}

CliOptions parse_cli(const std::vector<std::string>& args) {
  CliOptions out;
  for (std::size_t i = 0; i < args.size(); ++i) {
    const auto& arg = args[i];
    const auto eq = arg.find('=');
    const auto key = eq == std::string::npos ? arg : arg.substr(0, eq);
    const auto inline_value = eq == std::string::npos ? std::optional<std::string>{} : std::optional<std::string>{arg.substr(eq + 1)};
    const auto value_or_next = [&](std::string_view name) {
      return inline_value.has_value() ? *inline_value : take_value(args, i, name);
    };

    if (key == "--access-token" || key == "-a" || key == "-t") {
      out.access_token = value_or_next(key);
    } else if (key == "--config-file" || key == "-c") {
      out.config_file = value_or_next(key);
    } else if (key == "--score-count" || key == "-s") {
      out.score_count = parse_int(value_or_next(key), key);
    } else if (key == "--logo-size" || key == "-l") {
      out.logo_size = parse_int(value_or_next(key), key);
    } else if (key == "--base-url") {
      out.base_url = value_or_next(key);
    } else if (key == "--no-color") {
      out.no_color = true;
    } else {
      throw ConfigError("unknown argument: " + arg);
    }
  }
  return out;
}

void apply_config_json(Config& config, const std::filesystem::path& file) {
  const auto json = parse_json(read_file(file)).as_object();
  if (!field(json, "accessToken").is_null()) {
    config.access_token = string_field(json, "accessToken");
  }
  if (!field(json, "scoreCount").is_null()) {
    config.score_count = static_cast<std::uint32_t>(int_field(json, "scoreCount"));
  }
  if (!field(json, "logoSize").is_null()) {
    config.logo_size = int_field(json, "logoSize");
  }
  if (!field(json, "baseUrl").is_null()) {
    config.base_url = string_field(json, "baseUrl");
  }
}

void apply_env(Config& config, const EnvReader& env_reader) {
  if (auto token = first_env(env_reader, {"MAIFETCH_TOKEN", "MAITEA_TOKEN"})) {
    config.access_token = *token;
  }
  if (auto score_count = first_env(env_reader, {"MAIFETCH_SCORE_COUNT", "MAITEA_SCORE_COUNT"})) {
    config.score_count = static_cast<std::uint32_t>(*parse_int(*score_count, "score count"));
  }
  if (auto logo_size = first_env(env_reader, {"MAIFETCH_LOGO_SIZE", "MAITEA_LOGO_SIZE"})) {
    config.logo_size = *parse_int(*logo_size, "logo size");
  }
  if (auto base_url = first_env(env_reader, {"MAIFETCH_BASE_URL", "MAITEA_BASE_URL"})) {
    config.base_url = *base_url;
  }
}

void apply_cli(Config& config, const CliOptions& cli) {
  if (cli.access_token) {
    config.access_token = *cli.access_token;
  }
  if (cli.score_count) {
    config.score_count = static_cast<std::uint32_t>(*cli.score_count);
  }
  if (cli.logo_size) {
    config.logo_size = *cli.logo_size;
  }
  if (cli.base_url) {
    config.base_url = *cli.base_url;
  }
  if (cli.no_color) {
    config.no_color = true;
  }
}

std::string ansi_fg(std::string text, int r, int g, int b, bool no_color) {
  if (no_color) {
    return text;
  }
  return "\033[38;2;" + std::to_string(r) + ";" + std::to_string(g) + ";" + std::to_string(b) + "m" + text + "\033[0m";
}

std::string ansi_bg(std::string text, int fr, int fg, int fb, int br, int bg, int bb, bool no_color) {
  if (no_color) {
    return text;
  }
  return "\033[38;2;" + std::to_string(fr) + ";" + std::to_string(fg) + ";" + std::to_string(fb) +
         ";48;2;" + std::to_string(br) + ";" + std::to_string(bg) + ";" + std::to_string(bb) + "m" +
         text + "\033[0m";
}

std::string label(std::string text, bool no_color) {
  return ansi_fg(std::move(text), 72, 184, 200, no_color);
}

std::string difficulty_label(const std::string& difficulty, bool no_color) {
  if (difficulty == "easy") {
    return ansi_bg("Easy", 255, 255, 255, 69, 174, 255, no_color);
  }
  if (difficulty == "basic") {
    return ansi_bg("Basic", 255, 255, 255, 111, 212, 61, no_color);
  }
  if (difficulty == "advanced") {
    return ansi_bg("Advanced", 255, 255, 255, 248, 183, 9, no_color);
  }
  if (difficulty == "expert") {
    return ansi_bg("Expert", 255, 255, 255, 255, 46, 66, no_color);
  }
  if (difficulty == "master") {
    return ansi_bg("Master", 255, 255, 255, 171, 140, 233, no_color);
  }
  if (difficulty == "remaster" || difficulty == "re:master") {
    return ansi_bg("Re:Master", 255, 255, 255, 207, 114, 237, no_color);
  }
  if (difficulty == "utage") {
    return ansi_bg("Utage", 255, 255, 255, 255, 68, 1, no_color);
  }
  return difficulty;
}

std::string rank_label(const std::string& rank, bool no_color) {
  if (rank == "SSS+") {
    return ansi_fg("S", 255, 200, 54, no_color) + ansi_fg("S", 225, 38, 165, no_color) +
           ansi_fg("S", 73, 64, 233, no_color) + ansi_fg("+", 21, 203, 148, no_color);
  }
  if (rank == "SSS") {
    return ansi_fg("S", 255, 200, 54, no_color) + ansi_fg("S", 232, 39, 148, no_color) +
           ansi_fg("S", 18, 195, 144, no_color);
  }
  if (rank == "SS+" || rank == "SS") {
    return ansi_bg(rank, 248, 200, 75, 143, 71, 33, no_color);
  }
  if (rank == "S+" || rank == "S") {
    return ansi_bg(rank, 248, 200, 75, 75, 82, 82, no_color);
  }
  if (rank == "AAA" || rank == "AA" || rank == "A") {
    return ansi_fg(rank, 23, 163, 255, no_color);
  }
  return rank;
}

std::string normalize_full_width_ascii(std::string_view input) {
  std::string out;
  for (std::size_t i = 0; i < input.size();) {
    const auto c = static_cast<unsigned char>(input[i]);
    if (i + 2 < input.size() && c == 0xEFU) {
      const auto c1 = static_cast<unsigned char>(input[i + 1]);
      const auto c2 = static_cast<unsigned char>(input[i + 2]);
      if (c1 == 0xBCU && c2 >= 0x81U && c2 <= 0xBFU) {
        out.push_back(static_cast<char>(0x21U + (c2 - 0x81U)));
        i += 3;
        continue;
      }
      if (c1 == 0xBDU && c2 >= 0x80U && c2 <= 0x9EU) {
        out.push_back(static_cast<char>(0x60U + (c2 - 0x80U)));
        i += 3;
        continue;
      }
    }
    out.push_back(input[i]);
    ++i;
  }
  return out;
}

std::vector<std::string> logo_lines(int logo_size, bool no_color) {
  if (logo_size <= 0) {
    return {};
  }
  const auto width = std::max(10, logo_size * 2);
  const auto height = std::max(4, logo_size);
  std::vector<std::string> lines;
  lines.reserve(static_cast<std::size_t>(height));
  const std::string text = "MaiTea";
  for (int row = 0; row < height; ++row) {
    std::string line(static_cast<std::size_t>(width), ' ');
    if (row == height / 2 && width > static_cast<int>(text.size())) {
      const auto start = static_cast<std::size_t>((width - static_cast<int>(text.size())) / 2);
      line.replace(start, text.size(), text);
    } else if (row == 0 || row == height - 1) {
      std::fill(line.begin(), line.end(), '#');
    } else {
      line.front() = '#';
      line.back() = '#';
    }
    lines.push_back(ansi_fg(line, 72, 184, 200, no_color));
  }
  return lines;
}

std::string join_url(std::string base_url, const std::string& path) {
  while (!base_url.empty() && base_url.back() == '/') {
    base_url.pop_back();
  }
  if (!path.empty() && path.front() == '/') {
    return base_url + path;
  }
  return base_url + "/" + path;
}

}  // namespace

Json::Json() : value_(nullptr) {}
Json::Json(std::nullptr_t) : value_(nullptr) {}
Json::Json(bool input) : value_(input) {}
Json::Json(double input) : value_(input) {}
Json::Json(std::string input) : value_(std::move(input)) {}
Json::Json(object input) : value_(std::move(input)) {}
Json::Json(array input) : value_(std::move(input)) {}

bool Json::is_null() const {
  return std::holds_alternative<std::nullptr_t>(value_);
}

const Json::object& Json::as_object() const {
  if (!std::holds_alternative<object>(value_)) {
    throw ConfigError("expected JSON object");
  }
  return std::get<object>(value_);
}

const Json::array& Json::as_array() const {
  if (!std::holds_alternative<array>(value_)) {
    throw ConfigError("expected JSON array");
  }
  return std::get<array>(value_);
}

const std::string& Json::as_string() const {
  if (!std::holds_alternative<std::string>(value_)) {
    throw ConfigError("expected JSON string");
  }
  return std::get<std::string>(value_);
}

double Json::as_number() const {
  if (!std::holds_alternative<double>(value_)) {
    throw ConfigError("expected JSON number");
  }
  return std::get<double>(value_);
}

bool Json::as_bool() const {
  if (!std::holds_alternative<bool>(value_)) {
    throw ConfigError("expected JSON bool");
  }
  return std::get<bool>(value_);
}

std::filesystem::path default_user_config_dir() {
#ifdef _WIN32
  if (const char* appdata = std::getenv("APPDATA")) {
    return std::filesystem::path(appdata);
  }
#elif defined(__APPLE__)
  if (const char* home = std::getenv("HOME")) {
    return std::filesystem::path(home) / "Library" / "Application Support";
  }
#else
  if (const char* xdg = std::getenv("XDG_CONFIG_HOME")) {
    return std::filesystem::path(xdg);
  }
  if (const char* home = std::getenv("HOME")) {
    return std::filesystem::path(home) / ".config";
  }
#endif
  return std::filesystem::current_path();
}

std::optional<std::string> default_env_reader(const std::string& name) {
  if (const char* value = std::getenv(name.c_str())) {
    return std::string(value);
  }
  return std::nullopt;
}

Json parse_json(const std::string& input) {
  return JsonParser(input).parse();
}

Config load_config(const std::vector<std::string>& args,
                   const EnvReader& env_reader,
                   std::filesystem::path default_config_dir) {
  const auto cli = parse_cli(args);
  Config config;
  const auto env_config_file = first_env(env_reader, {"MAIFETCH_CONFIG_FILE", "MAITEA_CONFIG_FILE"});
  config.config_file = cli.config_file.value_or(
      env_config_file ? std::filesystem::path(*env_config_file) : default_config_dir / "maifetch.json");

  if (std::filesystem::exists(config.config_file)) {
    apply_config_json(config, config.config_file);
  } else if (cli.config_file) {
    throw ConfigError("config file does not exist: " + config.config_file.string());
  }

  apply_env(config, env_reader);
  apply_cli(config, cli);

  if (config.access_token.empty()) {
    throw ConfigError("access token is required");
  }
  if (config.score_count > 12) {
    throw ConfigError("score count cannot be higher than 12");
  }
  return config;
}

std::string help_text() {
  return R"(maifetch - lazy MaiTea profile fetcher

Usage:
  maifetch [options]

Options:
  -a, -t, --access-token <token>  MaiTea access token
  -c, --config-file <path>        JSON config file
  -s, --score-count <count>       Recent score count to show, max 12
  -l, --logo-size <size>          Logo size; zero or negative disables it
      --base-url <url>            MaiTea API base URL, mostly useful for tests
      --no-color                  Disable ANSI truecolor output
  -h, --help                      Show this help text

Configuration priority: CLI > environment > config file > defaults.
Environment variables: MAIFETCH_TOKEN, MAIFETCH_CONFIG_FILE, MAIFETCH_SCORE_COUNT, MAIFETCH_LOGO_SIZE, MAIFETCH_BASE_URL.
The documented MAITEA_* aliases are also supported.
)";
}

std::vector<Profile> parse_profiles_response(const std::string& json_text) {
  std::vector<Profile> profiles;
  const auto root = parse_json(json_text).as_object();
  for (const auto& item : field(root, "data").as_array()) {
    const auto& obj = item.as_object();
    Profile profile;
    profile.id = int_field(obj, "id");
    profile.name = string_field(obj, "name");
    profile.rating = int_field(obj, "rating");
    profile.rating_highest = int_field(obj, "rating_highest");
    profile.level = int_field(obj, "level");

    const auto& play_stats = field(obj, "play_stats");
    if (!play_stats.is_null()) {
      profile.total_credits = int_field(play_stats.as_object(), "total");
    }

    const auto& options = field(obj, "options");
    if (!options.is_null()) {
      const auto& icon = field(options.as_object(), "icon");
      if (!icon.is_null()) {
        profile.icon.id = int_field(icon.as_object(), "id");
        profile.icon.png = string_field(icon.as_object(), "png");
        profile.icon.webp = string_field(icon.as_object(), "webp");
      }
    }
    profiles.push_back(std::move(profile));
  }
  return profiles;
}

std::vector<Play> parse_plays_response(const std::string& json_text) {
  std::vector<Play> plays;
  const auto root = parse_json(json_text).as_object();
  for (const auto& item : field(root, "data").as_array()) {
    const auto& obj = item.as_object();
    Play play;
    play.id = int_field(obj, "id");
    play.score = int_field(obj, "score");
    play.achievement = int_field(obj, "achievement");
    play.score_formatted = string_field(obj, "score_formatted");
    play.achievement_formatted = string_field(obj, "achievement_formatted");
    play.rank = string_field(obj, "rank");

    const auto& fc_label = field(obj, "full_combo_label");
    if (!fc_label.is_null()) {
      play.full_combo_label = fc_label.as_string();
    }

    const auto& difficulty = field(obj, "difficulty_level");
    if (!difficulty.is_null()) {
      play.difficulty = string_field(difficulty.as_object(), "value");
    }

    const auto& song = field(obj, "song");
    if (!song.is_null()) {
      const auto& name = field(song.as_object(), "name");
      if (!name.is_null()) {
        play.song_name_en = string_field(name.as_object(), "en");
      }
    }
    plays.push_back(std::move(play));
  }
  return plays;
}

std::vector<Score> parse_scores_response(const std::string& json_text) {
  std::vector<Score> scores;
  const auto root = parse_json(json_text).as_object();
  for (const auto& item : field(root, "data").as_array()) {
    const auto& obj = item.as_object();
    Score score;
    score.id = int_field(obj, "id");
    score.score = int_field(obj, "score");
    score.achievement = int_field(obj, "achievement");
    score.score_formatted = string_field(obj, "score_formatted");
    score.achievement_formatted = string_field(obj, "achievement_formatted");
    score.rank = string_field(obj, "rank");

    const auto& fc_label = field(obj, "full_combo_label");
    if (!fc_label.is_null()) {
      score.full_combo_label = fc_label.as_string();
    }

    const auto& difficulty = field(obj, "difficulty_level");
    if (!difficulty.is_null()) {
      score.difficulty = string_field(difficulty.as_object(), "value");
    }

    const auto& song = field(obj, "song");
    if (!song.is_null()) {
      const auto& name = field(song.as_object(), "name");
      if (!name.is_null()) {
        score.song_name_en = string_field(name.as_object(), "en");
      }
    }
    scores.push_back(std::move(score));
  }
  return scores;
}

std::vector<TrackInfo> parse_tracks_response(const std::string& json_text) {
  std::vector<TrackInfo> tracks;
  const auto root = parse_json(json_text).as_object();
  for (const auto& item : field(root, "data").as_array()) {
    const auto& obj = item.as_object();
    TrackInfo track;
    track.id = int_field(obj, "id");
    track.code = string_field(obj, "code");
    track.name = localized_text_field(obj, "name");
    track.artist = localized_text_field(obj, "artist");
    tracks.push_back(std::move(track));
  }
  return tracks;
}

Status parse_status_response(const std::string& json_text) {
  const auto root = parse_json(json_text).as_object();
  Status status;
  const auto& webui = field(root, "webui");
  if (!webui.is_null()) {
    const auto& webui_obj = webui.as_object();
    status.webui.api = string_field(webui_obj, "api");
    const auto& db_read = field(webui_obj, "db_read");
    if (!db_read.is_null()) {
      status.webui.db_read.status = string_field(db_read.as_object(), "status");
      status.webui.db_read.query_time = string_field(db_read.as_object(), "query_time");
    }
    const auto& db_write = field(webui_obj, "db_write");
    if (!db_write.is_null()) {
      status.webui.db_write.status = string_field(db_write.as_object(), "status");
      status.webui.db_write.query_time = string_field(db_write.as_object(), "query_time");
    }
  }

  const auto& game = field(root, "game");
  if (!game.is_null()) {
    status.game.status = string_field(game.as_object(), "status");
  }
  if (!field(root, "last_updated").is_null()) {
    status.last_updated = static_cast<std::int64_t>(field(root, "last_updated").as_number());
  }
  return status;
}

std::vector<std::string> create_info_lines(const Profile& profile,
                                           const std::vector<Play>& plays,
                                           std::uint32_t score_count,
                                           bool no_color) {
  const auto name = normalize_full_width_ascii(profile.name);
  std::vector<std::string> lines;
  lines.push_back(label(name, no_color));
  lines.push_back(std::string(name.size(), '-'));
  lines.push_back(label("ID", no_color) + ": " + std::to_string(profile.id));

  std::ostringstream rating;
  rating << std::fixed << std::setprecision(2)
         << (static_cast<double>(profile.rating) / 100.0) << " / "
         << (static_cast<double>(profile.rating_highest) / 100.0);
  lines.push_back(label("Rating", no_color) + ": " + rating.str());

  lines.push_back(label("Level", no_color) + ": " + std::to_string(profile.level));
  lines.push_back(label("Total Credits", no_color) + ": " + std::to_string(profile.total_credits));
  lines.push_back(label("Recent Scores", no_color) + ":");

  const auto count = std::min<std::size_t>(score_count, plays.size());
  for (std::size_t i = 0; i < count; ++i) {
    const auto& play = plays[i];
    lines.push_back("  " + play.song_name_en + "  " + difficulty_label(play.difficulty, no_color));
    lines.push_back("  " + play.score_formatted + " " + play.achievement_formatted + "% " +
                    rank_label(play.rank, no_color) + " " + play.full_combo_label.value_or(""));
    lines.emplace_back();
  }
  return lines;
}

std::string format_output(const Profile& profile,
                          const std::vector<Play>& plays,
                          const Config& config) {
  const auto info = create_info_lines(profile, plays, config.score_count, config.no_color);
  const auto logo = logo_lines(config.logo_size, config.no_color);

  std::ostringstream out;
  if (logo.empty()) {
    for (const auto& line : info) {
      out << line << '\n';
    }
    return out.str();
  }

  const auto max_lines = std::max(logo.size(), info.size());
  const auto logo_width = static_cast<std::size_t>(std::max(10, config.logo_size * 2));
  for (std::size_t i = 0; i < max_lines; ++i) {
    if (i < logo.size()) {
      out << logo[i];
    } else {
      out << std::string(logo_width, ' ');
    }
    out << "  ";
    if (i < info.size()) {
      out << info[i];
    }
    out << '\n';
  }
  return out.str();
}

MaiTeaClient::MaiTeaClient(Config config) : config_(std::move(config)) {}

std::vector<Profile> MaiTeaClient::get_profiles() const {
  return parse_profiles_response(get("/api/v1/profiles"));
}

std::vector<Play> MaiTeaClient::get_recent_plays() const {
  return parse_plays_response(get("/api/v1/plays"));
}

std::vector<Play> MaiTeaClient::get_all_recent_plays() const {
  return parse_plays_response(get("/api/v1/plays/all"));
}

std::vector<Score> MaiTeaClient::get_best_scores() const {
  return parse_scores_response(get("/api/v1/scores"));
}

std::vector<Score> MaiTeaClient::get_all_best_scores() const {
  return parse_scores_response(get("/api/v1/scores/all"));
}

std::vector<TrackInfo> MaiTeaClient::get_tracks() const {
  return parse_tracks_response(get("/api/v1/tracks"));
}

Status MaiTeaClient::get_status() const {
  return parse_status_response(get("/api/status"));
}

std::string MaiTeaClient::get(const std::string& path) const {
  static const CurlGlobal curl_global;
  (void)curl_global;
  const auto url = join_url(config_.base_url, path);
  const auto auth_header = "Authorization: Bearer " + config_.access_token;

  using CurlHandle = std::unique_ptr<CURL, decltype(&curl_easy_cleanup)>;
  CurlHandle handle(curl_easy_init(), curl_easy_cleanup);
  if (!handle) {
    throw ApiError("could not initialize curl handle");
  }

  curl_slist* raw_headers = nullptr;
  raw_headers = curl_slist_append(raw_headers, auth_header.c_str());
  raw_headers = curl_slist_append(raw_headers, "Content-Type: application/json");
  raw_headers = curl_slist_append(raw_headers, "Accept: application/json");
  using HeaderList = std::unique_ptr<curl_slist, decltype(&curl_slist_free_all)>;
  HeaderList headers(raw_headers, curl_slist_free_all);

  std::string output;
  curl_easy_setopt(handle.get(), CURLOPT_URL, url.c_str());
  curl_easy_setopt(handle.get(), CURLOPT_HTTPHEADER, headers.get());
  curl_easy_setopt(handle.get(), CURLOPT_WRITEFUNCTION, write_response);
  curl_easy_setopt(handle.get(), CURLOPT_WRITEDATA, &output);
  curl_easy_setopt(handle.get(), CURLOPT_TIMEOUT, 30L);
  curl_easy_setopt(handle.get(), CURLOPT_FOLLOWLOCATION, 1L);
  curl_easy_setopt(handle.get(), CURLOPT_USERAGENT, "maifetch-cpp20/0.2");

  const auto result = curl_easy_perform(handle.get());
  if (result != CURLE_OK) {
    throw ApiError(std::string("MaiTea request failed: ") + curl_easy_strerror(result));
  }

  long response_code = 0;
  curl_easy_getinfo(handle.get(), CURLINFO_RESPONSE_CODE, &response_code);
  if (response_code < 200 || response_code >= 300) {
    throw ApiError("MaiTea API returned HTTP " + std::to_string(response_code) + ": " + output);
  }

  return output;
}

}  // namespace maifetch

#include "config.hpp"

#include <cstdlib>
#include <fstream>
#include <nlohmann/json.hpp>
#include <sstream>
#include <stdexcept>

namespace maifetch {
namespace {

std::string require_value(const std::vector<std::string>& args, std::size_t& index, std::string_view flag) {
  if (index + 1 >= args.size()) {
    throw std::runtime_error(std::string(flag) + " requires a value");
  }
  ++index;
  return args[index];
}

std::optional<std::string> real_env(std::string_view name) {
  if (const char* value = std::getenv(std::string(name).c_str())) {
    return std::string(value);
  }
  return std::nullopt;
}

std::optional<std::string> first_env(const EnvGetter& env, std::initializer_list<std::string_view> names) {
  for (const auto name : names) {
    if (auto value = env(name); value && !value->empty()) {
      return value;
    }
  }
  return std::nullopt;
}

template <class Number>
Number parse_number(const std::string& value, std::string_view flag) {
  std::istringstream stream(value);
  Number parsed{};
  stream >> parsed;
  if (!stream || !stream.eof()) {
    throw std::runtime_error(std::string(flag) + " must be numeric");
  }
  return parsed;
}

bool parse_bool(const std::string& value, std::string_view flag) {
  if (value == "1" || value == "true" || value == "TRUE" || value == "yes" || value == "YES") {
    return true;
  }
  if (value == "0" || value == "false" || value == "FALSE" || value == "no" || value == "NO") {
    return false;
  }
  throw std::runtime_error(std::string(flag) + " must be true or false");
}

void apply_json(Config& config, const nlohmann::json& json) {
  if (json.contains("accessToken")) {
    config.access_token = json.at("accessToken").get<std::string>();
  }
  if (json.contains("baseUrl")) {
    config.base_url = json.at("baseUrl").get<std::string>();
  }
  if (json.contains("logoSize")) {
    config.logo_size = json.at("logoSize").get<int>();
  }
  if (json.contains("scoreCount")) {
    config.score_count = json.at("scoreCount").get<unsigned>();
  }
  if (json.contains("noColor")) {
    config.no_color = json.at("noColor").get<bool>();
  }
}

}  // namespace

std::filesystem::path default_config_file() {
#ifdef _WIN32
  if (const char* appdata = std::getenv("APPDATA")) {
    return std::filesystem::path(appdata) / "maifetch.json";
  }
#elif defined(__APPLE__)
  if (const char* home = std::getenv("HOME")) {
    return std::filesystem::path(home) / "Library" / "Application Support" / "maifetch.json";
  }
#else
  if (const char* xdg = std::getenv("XDG_CONFIG_HOME")) {
    return std::filesystem::path(xdg) / "maifetch.json";
  }
  if (const char* home = std::getenv("HOME")) {
    return std::filesystem::path(home) / ".config" / "maifetch.json";
  }
#endif
  return "maifetch.json";
}

CliOptions parse_cli(const std::vector<std::string>& args) {
  CliOptions options;
  for (std::size_t i = 0; i < args.size(); ++i) {
    const std::string& arg = args[i];
    const auto split = arg.find('=');
    const std::string flag = split == std::string::npos ? arg : arg.substr(0, split);
    const auto read_value = [&]() {
      if (split != std::string::npos) {
        return arg.substr(split + 1);
      }
      return require_value(args, i, flag);
    };

    if (flag == "--help" || flag == "-h") {
      options.help = true;
    } else if (flag == "--access-token" || flag == "-a" || flag == "-t") {
      options.access_token = read_value();
    } else if (flag == "--base-url") {
      options.base_url = read_value();
    } else if (flag == "--config-file" || flag == "-c") {
      options.config_file = read_value();
    } else if (flag == "--logo-size" || flag == "-l") {
      options.logo_size = parse_number<int>(read_value(), flag);
    } else if (flag == "--score-count" || flag == "-s") {
      options.score_count = parse_number<unsigned>(read_value(), flag);
    } else if (flag == "--no-color") {
      if (split != std::string::npos) {
        options.no_color = parse_bool(arg.substr(split + 1), flag);
      } else {
        options.no_color = true;
      }
    } else {
      throw std::runtime_error("unknown argument: " + arg);
    }
  }
  return options;
}

Config load_config(const std::vector<std::string>& args) {
  return load_config(args, real_env);
}

Config load_config(const std::vector<std::string>& args, EnvGetter env_getter) {
  const CliOptions cli = parse_cli(args);
  Config config;
  bool explicit_config_file = false;
  if (cli.config_file) {
    config.config_file = *cli.config_file;
    explicit_config_file = true;
  } else if (auto env_config = first_env(env_getter, {"MAIFETCH_CONFIG_FILE", "MAITEA_CONFIG_FILE"})) {
    config.config_file = *env_config;
    explicit_config_file = true;
  } else {
    config.config_file = default_config_file();
  }

  if (std::filesystem::exists(config.config_file)) {
    std::ifstream file(config.config_file);
    if (!file) {
      throw std::runtime_error("could not open config file: " + config.config_file.string());
    }
    nlohmann::json json;
    file >> json;
    apply_json(config, json);
  } else if (explicit_config_file) {
    throw std::runtime_error("config file does not exist: " + config.config_file.string());
  }

  if (auto value = first_env(env_getter, {"MAIFETCH_TOKEN", "MAITEA_TOKEN"})) {
    config.access_token = *value;
  }
  if (auto value = first_env(env_getter, {"MAIFETCH_BASE_URL", "MAITEA_BASE_URL"})) {
    config.base_url = *value;
  }
  if (auto value = first_env(env_getter, {"MAIFETCH_LOGO_SIZE", "MAITEA_LOGO_SIZE"})) {
    config.logo_size = parse_number<int>(*value, "logo size");
  }
  if (auto value = first_env(env_getter, {"MAIFETCH_SCORE_COUNT", "MAITEA_SCORE_COUNT"})) {
    config.score_count = parse_number<unsigned>(*value, "score count");
  }
  if (auto value = first_env(env_getter, {"MAIFETCH_NO_COLOR", "MAITEA_NO_COLOR", "NO_COLOR"})) {
    config.no_color = parse_bool(*value, "no color");
  }

  if (cli.access_token) {
    config.access_token = *cli.access_token;
  }
  if (cli.base_url) {
    config.base_url = *cli.base_url;
  }
  if (cli.logo_size) {
    config.logo_size = *cli.logo_size;
  }
  if (cli.score_count) {
    config.score_count = *cli.score_count;
  }
  if (cli.no_color) {
    config.no_color = true;
  }

  if (config.access_token.empty() && !cli.help) {
    throw std::runtime_error("access token is required");
  }
  if (config.base_url.empty()) {
    throw std::runtime_error("base URL cannot be empty");
  }
  if (config.score_count > 12) {
    throw std::runtime_error("score count cannot be higher than 12");
  }
  return config;
}

std::string help_text() {
  return R"(Usage: maifetch [options]

Options:
  -a, -t, --access-token <token>  Access token for the MaiTea account
      --base-url <url>            MaiTea API base URL
  -c, --config-file <path>        JSON config file to use
  -l, --logo-size <number>        Size of the ASCII logo; zero or lower disables it
  -s, --score-count <number>      Amount of recent scores to show; max 12
      --no-color                  Disable ANSI colour output
  -h, --help                      Show this help
)";
}

}  // namespace maifetch

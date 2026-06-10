#pragma once

#include <filesystem>
#include <functional>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace maifetch {

struct Config {
  std::string access_token;
  std::string base_url = "https://maitea.app";
  std::filesystem::path config_file;
  int logo_size = 20;
  unsigned score_count = 4;
  bool no_color = false;
};

struct CliOptions {
  std::optional<std::string> access_token;
  std::optional<std::string> base_url;
  std::optional<std::filesystem::path> config_file;
  std::optional<int> logo_size;
  std::optional<unsigned> score_count;
  bool no_color = false;
  bool help = false;
};

using EnvGetter = std::function<std::optional<std::string>(std::string_view)>;

std::filesystem::path default_config_file();
CliOptions parse_cli(const std::vector<std::string>& args);
Config load_config(const std::vector<std::string>& args);
Config load_config(const std::vector<std::string>& args, EnvGetter env_getter);
std::string help_text();

}  // namespace maifetch

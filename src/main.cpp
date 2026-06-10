#include "config.hpp"
#include "maitea_client.hpp"
#include "output.hpp"

#include <future>
#include <iostream>

int main(int argc, char** argv) {
  std::vector<std::string> args;
  for (int i = 1; i < argc; ++i) {
    args.emplace_back(argv[i]);
  }

  try {
    const auto cli = maifetch::parse_cli(args);
    if (cli.help) {
      std::cout << maifetch::help_text();
      return 0;
    }

    const auto config = maifetch::load_config(args);
    maifetch::MaiTeaClient client(config.access_token, config.base_url);
    const auto profiles = client.profiles();
    if (profiles.empty()) {
      std::cout << "No profiles found\n";
      return 0;
    }

    auto plays_future = std::async(std::launch::async, [&client]() { return client.plays(); });
    if (plays_future.wait_for(std::chrono::seconds(30)) != std::future_status::ready) {
      std::cout << "API timed out\n";
      return 0;
    }
    const auto plays = plays_future.get();

    std::vector<unsigned char> image_bytes;
    if (config.logo_size > 0 && !profiles.front().icon_png.empty()) {
      image_bytes = client.download(profiles.front().icon_png);
    }
    std::cout << maifetch::render_output(profiles.front(), plays, config.logo_size, config.score_count, image_bytes, config.no_color);
  } catch (const std::exception& error) {
    std::cout << error.what() << '\n';
    return 1;
  }
  return 0;
}

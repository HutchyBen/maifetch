#include "maifetch.hpp"

#include <exception>
#include <iostream>
#include <string>
#include <vector>

int main(int argc, char** argv) {
  std::vector<std::string> args;
  args.reserve(static_cast<std::size_t>(argc > 0 ? argc - 1 : 0));
  for (int i = 1; i < argc; ++i) {
    args.emplace_back(argv[i]);
  }

  for (const auto& arg : args) {
    if (arg == "--help" || arg == "-h") {
      std::cout << maifetch::help_text();
      return 0;
    }
  }

  try {
    const auto config = maifetch::load_config(args);
    const maifetch::MaiTeaClient client(config);
    const auto profiles = client.get_profiles();
    if (profiles.empty()) {
      std::cout << "No profiles found\n";
      return 0;
    }

    const auto plays = client.get_recent_plays();
    std::cout << maifetch::format_output(profiles.front(), plays, config);
    return 0;
  } catch (const std::exception& err) {
    std::cerr << err.what() << '\n';
    return 1;
  }
}

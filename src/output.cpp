#include "output.hpp"

#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>

#include <algorithm>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace maifetch {
namespace {

std::string fg(std::string_view text, int r, int g, int b) {
  std::ostringstream out;
  out << "\033[38;2;" << r << ';' << g << ';' << b << 'm' << text << "\033[0m";
  return out.str();
}

std::string fg_bg(std::string_view text, int fr, int fg_value, int fb, int br, int bg, int bb) {
  std::ostringstream out;
  out << "\033[38;2;" << fr << ';' << fg_value << ';' << fb << ";48;2;" << br << ';' << bg << ';' << bb << 'm' << text
      << "\033[0m";
  return out.str();
}

std::string format_rating(int value) {
  std::ostringstream out;
  out << std::fixed << std::setprecision(2) << (static_cast<double>(value) / 100.0);
  return out.str();
}

std::string utf8_from_codepoint(char32_t cp) {
  std::string out;
  if (cp <= 0x7F) {
    out.push_back(static_cast<char>(cp));
  } else if (cp <= 0x7FF) {
    out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  } else if (cp <= 0xFFFF) {
    out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  } else {
    out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  }
  return out;
}

std::vector<char32_t> utf8_to_codepoints(std::string_view text) {
  std::vector<char32_t> result;
  for (std::size_t i = 0; i < text.size();) {
    const auto c = static_cast<unsigned char>(text[i]);
    if (c < 0x80) {
      result.push_back(c);
      ++i;
    } else if ((c >> 5) == 0x6 && i + 1 < text.size()) {
      result.push_back(((c & 0x1F) << 6) | (static_cast<unsigned char>(text[i + 1]) & 0x3F));
      i += 2;
    } else if ((c >> 4) == 0xE && i + 2 < text.size()) {
      result.push_back(((c & 0x0F) << 12) | ((static_cast<unsigned char>(text[i + 1]) & 0x3F) << 6) |
                       (static_cast<unsigned char>(text[i + 2]) & 0x3F));
      i += 3;
    } else if ((c >> 3) == 0x1E && i + 3 < text.size()) {
      result.push_back(((c & 0x07) << 18) | ((static_cast<unsigned char>(text[i + 1]) & 0x3F) << 12) |
                       ((static_cast<unsigned char>(text[i + 2]) & 0x3F) << 6) |
                       (static_cast<unsigned char>(text[i + 3]) & 0x3F));
      i += 4;
    } else {
      ++i;
    }
  }
  return result;
}

std::string join_lines(const std::vector<std::string>& lines) {
  std::ostringstream out;
  for (const auto& line : lines) {
    out << line << '\n';
  }
  return out.str();
}

}  // namespace

std::string wide_to_normal(std::string_view text) {
  std::string out;
  for (auto cp : utf8_to_codepoints(text)) {
    if (cp >= 0xFF01 && cp <= 0xFF5E) {
      cp -= 0xFEE0;
    } else if (cp == 0x3000) {
      cp = ' ';
    }
    out += utf8_from_codepoint(cp);
  }
  return out;
}

std::string colour(std::string_view text, bool no_color) {
  if (no_color) {
    return std::string(text);
  }
  return fg(text, 72, 184, 200);
}

std::string difficulty_string(std::string_view difficulty, bool no_color) {
  if (no_color) {
    return std::string(difficulty);
  }
  if (difficulty == "easy") return fg_bg("Easy", 255, 255, 255, 69, 174, 255);
  if (difficulty == "basic") return fg_bg("Basic", 255, 255, 255, 111, 212, 61);
  if (difficulty == "advanced") return fg_bg("Advanced", 255, 255, 255, 248, 183, 9);
  if (difficulty == "expert") return fg_bg("Expert", 255, 255, 255, 255, 46, 66);
  if (difficulty == "master") return fg_bg("Master", 255, 255, 255, 171, 140, 233);
  if (difficulty == "remaster" || difficulty == "re:master") return fg_bg("Re:Master", 255, 255, 255, 207, 114, 237);
  if (difficulty == "utage") return fg_bg("Utage", 255, 255, 255, 255, 68, 1);
  return std::string(difficulty);
}

std::string rank_string(std::string_view rank, bool no_color) {
  if (no_color) {
    return std::string(rank);
  }
  if (rank == "SSS+") return fg("S", 255, 200, 54) + fg("S", 225, 38, 165) + fg("S", 73, 64, 233) + fg("+", 21, 203, 148);
  if (rank == "SSS") return fg("S", 255, 200, 54) + fg("S", 232, 39, 148) + fg("S", 18, 195, 144);
  if (rank == "SS+" || rank == "SS") return fg_bg(rank, 248, 200, 75, 143, 71, 33);
  if (rank == "S+" || rank == "S") return fg_bg(rank, 248, 200, 75, 75, 82, 82);
  if (rank == "AAA" || rank == "AA" || rank == "A") return fg(rank, 23, 163, 255);
  return std::string(rank);
}

std::vector<std::string> create_info_lines(const Profile& profile, const std::vector<Play>& plays, unsigned score_count, bool no_color) {
  const std::string name = wide_to_normal(profile.name);
  std::vector<std::string> lines{
      colour(name, no_color),
      std::string(name.size(), '-'),
      colour("ID", no_color) + ": " + std::to_string(profile.id),
      colour("Rating", no_color) + ": " + format_rating(profile.rating) + " / " + format_rating(profile.rating_highest),
      colour("Level", no_color) + ": " + std::to_string(profile.level),
      colour("Total Credits", no_color) + ": " + std::to_string(profile.total_credits),
      colour("Recent Scores", no_color) + ":",
  };

  const auto limit = std::min<std::size_t>(score_count, plays.size());
  for (std::size_t i = 0; i < limit; ++i) {
    const auto& play = plays[i];
    lines.push_back("  " + play.song_name_en + "  " + difficulty_string(play.difficulty, no_color));
    lines.push_back("  " + play.score_formatted + " " + play.achievement_formatted + "% " + rank_string(play.rank, no_color) + " " +
                    play.full_combo_label.value_or(""));
    lines.emplace_back("");
  }
  return lines;
}

std::vector<std::string> create_logo_lines(const std::vector<unsigned char>& image_bytes, int logo_size, bool no_color) {
  if (logo_size <= 0 || image_bytes.empty()) {
    return {};
  }

  int width = 0;
  int height = 0;
  int channels = 0;
  unsigned char* pixels = stbi_load_from_memory(image_bytes.data(), static_cast<int>(image_bytes.size()), &width, &height, &channels, 4);
  if (!pixels) {
    throw std::runtime_error("could not decode profile icon");
  }

  const int out_width = logo_size * 2;
  const int out_height = logo_size;
  std::vector<std::string> lines;
  lines.reserve(static_cast<std::size_t>(out_height));
  for (int y = 0; y < out_height; ++y) {
    std::string line;
    const int src_y = std::clamp(static_cast<int>(std::floor((static_cast<double>(y) / out_height) * height)), 0, height - 1);
    for (int x = 0; x < out_width; ++x) {
      const int src_x = std::clamp(static_cast<int>(std::floor((static_cast<double>(x) / out_width) * width)), 0, width - 1);
      const auto idx = static_cast<std::size_t>((src_y * width + src_x) * 4);
      const int alpha = pixels[idx + 3];
      if (alpha < 16) {
        line += ' ';
      } else {
        line += no_color ? "#" : fg("#", pixels[idx], pixels[idx + 1], pixels[idx + 2]);
      }
    }
    lines.push_back(std::move(line));
  }
  stbi_image_free(pixels);
  return lines;
}

std::string render_output(const Profile& profile,
                          const std::vector<Play>& plays,
                          int logo_size,
                          unsigned score_count,
                          const std::vector<unsigned char>& image_bytes,
                          bool no_color) {
  const auto info_lines = create_info_lines(profile, plays, score_count, no_color);
  if (logo_size <= 0) {
    return join_lines(info_lines);
  }

  const auto logo_lines = create_logo_lines(image_bytes, logo_size, no_color);
  std::ostringstream out;
  const std::size_t max_lines = std::max(info_lines.size(), logo_lines.size());
  const std::string padding = "  ";
  for (std::size_t i = 0; i < max_lines; ++i) {
    const std::string logo = i < logo_lines.size() ? logo_lines[i] : std::string(static_cast<std::size_t>(logo_size * 2), ' ');
    const std::string info = i < info_lines.size() ? info_lines[i] : "";
    out << logo << padding << info << '\n';
  }
  return out.str();
}

}  // namespace maifetch

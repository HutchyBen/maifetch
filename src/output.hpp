#pragma once

#include "maitea_client.hpp"

#include <string>
#include <vector>

namespace maifetch {

std::string wide_to_normal(std::string_view text);
std::string colour(std::string_view text, bool no_color = false);
std::string difficulty_string(std::string_view difficulty, bool no_color = false);
std::string rank_string(std::string_view rank, bool no_color = false);
std::vector<std::string> create_info_lines(const Profile& profile, const std::vector<Play>& plays, unsigned score_count, bool no_color = false);
std::vector<std::string> create_logo_lines(const std::vector<unsigned char>& image_bytes, int logo_size, bool no_color = false);
std::string render_output(const Profile& profile,
                          const std::vector<Play>& plays,
                          int logo_size,
                          unsigned score_count,
                          const std::vector<unsigned char>& image_bytes = {},
                          bool no_color = false);

}  // namespace maifetch

#pragma once

#include <map>
#include <string>
#include <vector>

namespace maifetch {

struct HttpResponse {
  int status = 0;
  std::string body;
  std::vector<unsigned char> bytes;
};

class HttpClient {
 public:
  HttpResponse get(std::string url, const std::map<std::string, std::string>& headers = {}) const;
};

}  // namespace maifetch

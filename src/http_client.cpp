#include "http_client.hpp"

#include <httplib.h>

#include <stdexcept>

namespace maifetch {
namespace {

struct ParsedUrl {
  bool https = true;
  std::string host;
  std::string path = "/";
};

ParsedUrl parse_url(const std::string& url) {
  ParsedUrl parsed;
  std::string_view rest = url;
  if (rest.starts_with("https://")) {
    parsed.https = true;
    rest.remove_prefix(8);
  } else if (rest.starts_with("http://")) {
    parsed.https = false;
    rest.remove_prefix(7);
  } else {
    throw std::runtime_error("unsupported URL: " + url);
  }

  const auto slash = rest.find('/');
  parsed.host = std::string(rest.substr(0, slash));
  if (slash != std::string_view::npos) {
    parsed.path = std::string(rest.substr(slash));
  }
  return parsed;
}

httplib::Headers to_headers(const std::map<std::string, std::string>& headers) {
  httplib::Headers converted;
  for (const auto& [key, value] : headers) {
    converted.emplace(key, value);
  }
  return converted;
}

template <class Client>
HttpResponse perform_get(Client& client, const ParsedUrl& parsed, const std::map<std::string, std::string>& headers, const std::string& url) {
  client.set_follow_location(true);
  client.set_read_timeout(30, 0);
  client.set_connection_timeout(10, 0);

  const auto response = client.Get(parsed.path, to_headers(headers));
  if (!response) {
    throw std::runtime_error("HTTP request failed: " + url);
  }
  if (response->status < 200 || response->status >= 300) {
    throw std::runtime_error("HTTP " + std::to_string(response->status) + " for " + url);
  }

  return HttpResponse{
      .status = response->status,
      .body = response->body,
      .bytes = std::vector<unsigned char>(response->body.begin(), response->body.end()),
  };
}

}  // namespace

HttpResponse HttpClient::get(std::string url, const std::map<std::string, std::string>& headers) const {
  const ParsedUrl parsed = parse_url(url);
  if (parsed.https) {
    httplib::SSLClient client(parsed.host);
    return perform_get(client, parsed, headers, url);
  }
  httplib::Client client(parsed.host);
  return perform_get(client, parsed, headers, url);
}

}  // namespace maifetch

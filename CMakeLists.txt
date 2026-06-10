cmake_minimum_required(VERSION 3.24)

project(maifetch VERSION 0.1.0 LANGUAGES CXX)

include(FetchContent)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(MAIFETCH_BUILD_TESTS "Build maifetch tests" ON)

FetchContent_Declare(
  nlohmann_json
  URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
)

set(HTTPLIB_REQUIRE_OPENSSL ON CACHE BOOL "" FORCE)
FetchContent_Declare(
  cpp_httplib
  URL https://github.com/yhirose/cpp-httplib/archive/refs/tags/v0.18.5.tar.gz
)

FetchContent_Declare(
  stb
  GIT_REPOSITORY https://github.com/nothings/stb.git
  GIT_TAG f58f558c120e9b32c217290b80bad1a0729fbb2c
)

FetchContent_MakeAvailable(nlohmann_json cpp_httplib stb)
find_package(OpenSSL REQUIRED)

add_library(maifetch_core
  src/config.cpp
  src/http_client.cpp
  src/maitea_client.cpp
  src/output.cpp
)
target_include_directories(maifetch_core
  PUBLIC src
  PRIVATE ${stb_SOURCE_DIR}
)
target_link_libraries(maifetch_core
  PUBLIC
    nlohmann_json::nlohmann_json
    httplib::httplib
    OpenSSL::SSL
    OpenSSL::Crypto
)
target_compile_definitions(maifetch_core PRIVATE _CRT_SECURE_NO_WARNINGS)

add_executable(maifetch src/main.cpp)
target_link_libraries(maifetch PRIVATE maifetch_core)

if(MAIFETCH_BUILD_TESTS)
  enable_testing()
  add_executable(maifetch_tests tests/maifetch_tests.cpp)
  target_link_libraries(maifetch_tests PRIVATE maifetch_core)
  add_test(NAME maifetch_tests COMMAND maifetch_tests)
endif()

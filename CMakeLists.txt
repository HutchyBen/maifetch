cmake_minimum_required(VERSION 3.20)

project(maifetch VERSION 0.2.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(CURL REQUIRED)

add_library(maifetch_core
  src/maifetch.cpp
)

target_include_directories(maifetch_core PUBLIC
  ${CMAKE_CURRENT_SOURCE_DIR}/src
)

target_link_libraries(maifetch_core PUBLIC CURL::libcurl)

if(MSVC)
  target_compile_options(maifetch_core PRIVATE /W4 /permissive-)
else()
  target_compile_options(maifetch_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

add_executable(maifetch
  src/main.cpp
)

target_link_libraries(maifetch PRIVATE maifetch_core)

enable_testing()

add_executable(maifetch_tests
  tests/maifetch_tests.cpp
)

target_link_libraries(maifetch_tests PRIVATE maifetch_core)

add_test(NAME maifetch_tests COMMAND maifetch_tests)

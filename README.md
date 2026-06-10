# maifetch

A lazy fetch tool for [maitea](https://maitea.app), rewritten in modern C++20.
It also keeps a small MaiTea API wrapper for the profile, track, score, recent-play, and status calls used by the original Go module.

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## Configuration

| variable     | description                                        | default                                    | environment variable                         | cli argument                    |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------------------------------|---------------------------------|
| access token | token for your MaiTea account (REQUIRED)           | `N/A`                                      | `MAIFETCH_TOKEN` or `MAITEA_TOKEN`           | `--access-token` `-a` `-t`      |
| base URL     | MaiTea API base URL                                | `https://maitea.app`                       | `MAIFETCH_BASE_URL` or `MAITEA_BASE_URL`     | `--base-url`                    |
| logo size    | size of the ASCII logo; zero or lower disables it  | `20`                                       | `MAIFETCH_LOGO_SIZE` or `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`              |
| score count  | amount of scores to display (max 12)               | `4`                                        | `MAIFETCH_SCORE_COUNT` or `MAITEA_SCORE_COUNT` | `--score-count` `-s`          |
| color output | whether to render ANSI color                       | `true`                                     | `MAIFETCH_NO_COLOR`, `MAITEA_NO_COLOR`, or `NO_COLOR` | `--no-color`           |
| config file  | JSON file to store config variables                | [refer to below](#default-config-location) | `MAIFETCH_CONFIG_FILE` or `MAITEA_CONFIG_FILE` | `--config-file` `-c`          |

Values are loaded in this order:

1. defaults
2. JSON config file
3. environment variables
4. command-line arguments

## Default config location

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json` or `~/.config/maifetch.json` |
| macOS    | `~/Library/Application Support/maifetch.json`               |

Example config:

```json
{
  "accessToken": "",
  "baseUrl": "https://maitea.app",
  "logoSize": 20,
  "scoreCount": 4,
  "noColor": false
}
```

## Build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

The CMake build fetches the C++ dependencies during configure:

- nlohmann/json
- cpp-httplib with OpenSSL
- stb image loader

The CLI uses the wrapper's profile and recent-play calls. The library layer also includes parsers and client methods for tracks, paged plays, all plays, best scores, all best scores, and status responses.

## Run

```bash
./build/maifetch --access-token "<token>"
```

Useful options:

```bash
./build/maifetch --logo-size 0 --score-count 4 --access-token "<token>"
./build/maifetch --config-file ./maifetch.json
./build/maifetch --no-color --access-token "<token>"
```

## Validate

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build
ctest --test-dir build --output-on-failure
```

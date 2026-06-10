# maifetch

A lazy terminal fetch tool for [MaiTea](https://maitea.app), rewritten in modern C++20.

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## Features

- Fetches MaiTea profile information and recent plays with authenticated API requests.
- Preserves the existing config priority: CLI > environment > config file > defaults.
- Provides ANSI truecolor output with `--no-color` for plain terminals.
- Builds with CMake and C++20, with a small test executable for parser, config, and formatting behavior.

## Requirements

- A C++20 compiler
- CMake 3.20 or newer
- libcurl development headers

On Debian or Ubuntu:

```sh
sudo apt-get install cmake g++ libcurl4-openssl-dev
```

## Configuration

| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (required)           | `N/A`                                      | `MAIFETCH_TOKEN` or `MAITEA_TOKEN`       | `--access-token` `-a` `-t` |
| logo size    | size of the terminal logo, zero disables it        | `20`                                       | `MAIFETCH_LOGO_SIZE` or `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display, max 12                | `4`                                        | `MAIFETCH_SCORE_COUNT` or `MAITEA_SCORE_COUNT`  | `--score-count` `-s`  |
| API base URL | MaiTea API base URL, mostly useful for tests       | `https://maitea.app`                       | `MAIFETCH_BASE_URL` or `MAITEA_BASE_URL`       | `--base-url`          |
| config file  | JSON file to store config variables                | [refer to below](#default-config-location) | `MAIFETCH_CONFIG_FILE` or `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |

Example config:

```json
{
  "accessToken": "your-token",
  "scoreCount": 4,
  "logoSize": 20,
  "baseUrl": "https://maitea.app"
}
```

### Default Config Location

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json` or `~/.config/maifetch.json` |
| macOS    | `~/Library/Application Support/maifetch.json`               |

## Build

```sh
git clone https://github.com/HutchyBen/maifetch
cd maifetch
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release --parallel
```

Run the built executable:

```sh
./build/maifetch --access-token "$MAIFETCH_TOKEN"
```

On Windows, the executable path is usually:

```powershell
.\build\Release\maifetch.exe --access-token $env:MAIFETCH_TOKEN
```

## Test

```sh
ctest --test-dir build --output-on-failure
```

The test suite covers config priority, environment config-file selection, JSON parsing, and output formatting.

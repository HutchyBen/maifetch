<!-- AI-assisted with OpenAI GPT-5 Codex. -->
# maifetch

A lazy terminal fetch tool for [MaiTea](https://maitea.app), rewritten in
Clojure for the current bounty direction.

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## Features

- Clojure CLI implementation with `deps.edn`.
- Preserves the original configuration priority:
  command line > environment > config file > defaults.
- Supports the original MaiTea config names and arguments:
  `--access-token` / `-a` / `-t`, `--logo-size` / `-l`,
  `--score-count` / `-s`, and `--config-file` / `-c`.
- Fetches `/api/v1/profiles` and `/api/v1/plays` with authenticated Java
  HTTP client requests.
- Formats profile fields and recent scores in the same terminal-fetch style as
  the original Go version.
- Includes fixture-driven tests so CI can validate CLI parsing, config
  precedence, JSON extraction, output formatting, and validation errors without
  live MaiTea credentials.

## Requirements

- Java 21 or newer
- Clojure CLI

## Configuration

| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (required)           | `N/A`                                      | `MAITEA_TOKEN`       | `--access-token` `-a` `-t` |
| logo size    | size of the ASCII logo, zero or negative disables  | `20`                                       | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display, max 12                | `4`                                        | `MAITEA_SCORE_COUNT` | `--score-count` `-s`  |
| config file  | JSON file to store config variables                | [refer to below](#default-config-location) | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |

Example config:

```json
{
  "accessToken": "your-token",
  "scoreCount": 4,
  "logoSize": 20
}
```

### Default Config Location

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json` or `~/.config/maifetch.json` |
| macOS    | `~/Library/Application Support/maifetch.json`               |

## Run

```sh
clojure -M:run --access-token "$MAITEA_TOKEN" --logo-size 0
```

`--logo-size 0` disables the profile icon area. A positive logo size fetches the
profile icon and renders a simple terminal ASCII logo beside the profile output.

## Test

```sh
clojure -M:test
```

The test suite runs against checked-in MaiTea JSON fixtures through
`MAIFETCH_PROFILE_FIXTURE` and `MAIFETCH_PLAYS_FIXTURE`, avoiding any live
network credentials in CI.

<!-- AI-assisted with OpenAI GPT-5 Codex. -->
# maifetch

A lazy terminal fetch tool for [MaiTea](https://maitea.app), rewritten in
GnuCOBOL for the current bounty direction.

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## Features

- GnuCOBOL CLI implementation with no Go runtime dependency.
- Preserves the original configuration priority:
  command line > environment > config file > defaults.
- Supports the original MaiTea config names and arguments:
  `--access-token` / `-a` / `-t`, `--logo-size` / `-l`,
  `--score-count` / `-s`, and `--config-file` / `-c`.
- Fetches `/api/v1/profiles` and `/api/v1/plays` with authenticated `curl`
  requests, then formats the same profile and recent-score fields as the Go
  version.
- Includes fixture-driven tests so CI can validate CLI parsing, JSON field
  extraction, output formatting, and the no-token error path without a live
  MaiTea token.

## Requirements

- GnuCOBOL 3.x or newer
- `curl` for live MaiTea API requests

On Debian or Ubuntu:

```sh
sudo apt-get update
sudo apt-get install -y gnucobol curl
```

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

The COBOL rewrite follows the same practical defaults as the original Go
implementation:

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json` or `~/.config/maifetch.json` |
| macOS    | `~/Library/Application Support/maifetch.json`               |

## Build

```sh
make
```

Run the built executable:

```sh
./build/maifetch --access-token "$MAITEA_TOKEN" --logo-size 0
```

`--logo-size 0` disables the profile icon area, matching the original tool's
no-logo mode. The COBOL implementation keeps a blank logo column when a
positive logo size is configured because the original Go image-to-ASCII library
does not have a direct GnuCOBOL equivalent.

## Test

```sh
make test
```

The test suite compiles the GnuCOBOL program and runs it against checked-in
MaiTea JSON fixtures via `MAIFETCH_PROFILE_FIXTURE` and
`MAIFETCH_PLAYS_FIXTURE`, avoiding any live network credentials in CI.

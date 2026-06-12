<!-- AI-assisted with OpenAI GPT-5. -->

# maifetch
a really lazy fetch tool for [maitea](https://maitea.app) written in F# on .NET\
also contains a little api wrapper for maitea too :D

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## configuration
| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (REQUIRED)           | `N/A`                                      | `MAITEA_TOKEN`       | `--access-token` `-a` |
| logo size    | size of the ASCII logo (zero or negative disables) | `20`                                       | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display (max 12)               | `4`                                        | `MAITEA_SCORE_COUNT`  | `--score-count` `-s`  |
| config file  | json file to store config variables                | [refer to below](#default-config-location) | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |
| base URL     | API base URL override for tests/proxies            | `https://maitea.app`                       | `MAITEA_BASE_URL`    | `--base-url`          |

### Default config location
obtained from `os.UserConfigDir` 

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json`  `~/.config/maifetch.json` |
| OSX      | `~/Library/Application Support/maifetch.json`               |

## how to build
1. clone the project with `git clone https://github.com/HutchyBen/maifetch`
2. install the .NET 8 SDK
3. build with `dotnet build maifetch.sln --configuration Release`
4. run with `dotnet run --project src/Maifetch -- --access-token <token>`, ensuring access token is either
    - in config file
    - in environment variables
    - in command line options

## how to test
```sh
dotnet build maifetch.sln --configuration Release
dotnet run --project tests/Maifetch.Tests/Maifetch.Tests.fsproj --configuration Release
```


## todo
- test it properly
- add friendly errors

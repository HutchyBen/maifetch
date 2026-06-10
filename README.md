# maifetch
a really lazy fetch tool for [maitea](https://maitea.app) written in Kotlin\
also contains a little api wrapper for maitea too :D

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## configuration
| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (REQUIRED)           | `N/A`                                      | `MAITEA_TOKEN`       | `--access-token` `-a` |
| logo size    | size of the ASCII logo (zero or negative disables) | `20`                                       | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display (max 12)               | `4`                                        | `MAITEA_SCORE_COUNT`  | `--score-count` `-s`  |
| config file  | json file to store config variables                | [refer to below](#default-config-location) | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |

The Kotlin rewrite also accepts `MAIFETCH_TOKEN`, `MAIFETCH_LOGO_SIZE`, `MAIFETCH_SCORE_COUNT`, and `MAIFETCH_CONFIG_FILE` as compatibility aliases.

### Default config location
obtained from `os.UserConfigDir` 

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Windows  | `%APPDATA%/maifetch.json`                                   |
| Linux    | `$XDG_CONFIG_HOME/maifetch.json`  `~/.config/maifetch.json` |
| OSX      | `~/Library/Application Support/maifetch.json`               |

## how to build
1. clone the project with `git clone https://github.com/HutchyBen/maifetch`
2. build with `mvn package`
3. run with `java -jar target/maifetch-0.1.0.jar`, ensuring access token is either
    - in config file
    - in environment variables
    - in command line options

The Kotlin sources target Java 8 bytecode. Tests can be run with Maven as well:

```powershell
mvn test-compile
mvn exec:java "-Dexec.mainClass=maifetch.MaifetchTests" "-Dexec.classpathScope=test"
```

## todo
- add friendly errors

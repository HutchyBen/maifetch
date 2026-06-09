# maifetch
a really lazy fetch tool for [maitea](https://maitea.app) written in Java 8\
also contains a little api wrapper for maitea too :D

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## configuration
| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (REQUIRED)           | `N/A`                                      | `MAITEA_TOKEN`       | `--access-token` `-a` |
| logo size    | size of the ASCII logo (zero or negative disables) | `20`                                       | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display (max 12)               | `4`                                        | `MAITEA_SCORE_COUNT`  | `--score-count` `-s`  |
| config file  | json file to store config variables                | [refer to below](#default-config-location) | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |

The Java 8 rewrite also accepts `MAIFETCH_TOKEN`, `MAIFETCH_LOGO_SIZE`, `MAIFETCH_SCORE_COUNT`, and `MAIFETCH_CONFIG_FILE` as compatibility aliases.

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

If you do not have Maven installed, the project can still be checked with only a JDK:

```powershell
New-Item -ItemType Directory -Force build/classes, build/test-classes
$main = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName }
javac --release 8 -encoding UTF-8 -d build/classes $main
$test = Get-ChildItem -Recurse src/test/java -Filter *.java | ForEach-Object { $_.FullName }
javac --release 8 -encoding UTF-8 -cp build/classes -d build/test-classes $test
java -cp "build/classes;build/test-classes" maifetch.MaifetchTests
```

## todo
- add friendly errors

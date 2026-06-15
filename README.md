# maifetch

a really lazy fetch tool for [maitea](https://maitea.app) rewritten in x86-64
assembly for NASM.

![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)

## configuration

| variable     | description                                        | default                                    | environment variable | cli argument          |
|--------------|----------------------------------------------------|--------------------------------------------|----------------------|-----------------------|
| access token | token for your MaiTea account (REQUIRED)           | `N/A`                                      | `MAITEA_TOKEN`       | `--access-token` `-a` |
| logo size    | size of the ASCII logo (zero or negative disables) | `20`                                       | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display (max 12)               | `4`                                        | `MAITEA_SCORE_COUNT` | `--score-count` `-s`  |
| config file  | json file to store config variables                | [refer to below](#default-config-location) | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |

### Default config location

| platform | location                                                    |
|----------|-------------------------------------------------------------|
| Linux    | `$XDG_CONFIG_HOME/maifetch.json` or `~/.config/maifetch.json` |

## how to build

1. clone the project with `git clone https://github.com/HutchyBen/maifetch`
2. install NASM, GCC, and curl
3. build with:

```sh
mkdir -p build
nasm -f elf64 src/maifetch.asm -o build/maifetch.o
gcc -no-pie build/maifetch.o -o build/maifetch
```

4. run outputted executable ensuring access token is either:
    - in config file
    - in environment variables
    - in command line options

## testing

Run the fixture test without a real MaiTea token:

```sh
./test/run-fixture.sh
```

## todo

- add friendlier errors

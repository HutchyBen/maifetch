# maifetch
a really lazy fetch tool for [maitea](https://maitea.app) written in go\
also contains a little api wrapper for maitea too :D
## image
![image](https://github.com/user-attachments/assets/96cd7018-8a00-4785-a1a8-9fe503263662)
s
## configuration
| variable     | description                                        | json entry      | environment variable | command line          |
|--------------|----------------------------------------------------|-----------------|----------------------|-----------------------|
| access token | token for your MaiTea account (REQUIRED)           | `"accessToken"` | `MAITEA_TOKEN`       | `--access-token` `-a` |
| logo size    | size of the ASCII logo (zero or negative disables) | `"logoSize"`    | `MAITEA_LOGO_SIZE`   | `--logo-size` `-l`    |
| score count  | amount of scores to display (max 12)               | `"scoreCount"`    | `MAITEA_SCORE_COUNT`  | `--score-count` `-s`   |
| config file  | json file to store config variables                | `N/A`           | `MAITEA_CONFIG_FILE` | `--config-file` `-c`  |


## how to build
1. clone the project with `git clone https://github.com/HutchyBen/maifetch`
2. build with `go build maifetch/cmd/maifetch`
3. run outputted executable ensuring access token is either
    - in config file
    - in environment variables
    - in command line options


## todo
- test it properly
- add friendly errors
- add a loading message because api is slow

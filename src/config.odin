package main

import "core:encoding/json"
import "core:fmt"
import "core:os"
import "core:strconv"
import "core:testing"

Config :: struct {
	access_token: string,
	config_file:  string,
	logo_size:    int,
	score_count:  int,
}

Cli_Options :: struct {
	access_token: Maybe(string),
	config_file:  Maybe(string),
	logo_size:    Maybe(int),
	score_count:  Maybe(int),
	help:         bool,
}

File_Config :: struct {
	accessToken: Maybe(string),
	logoSize:    Maybe(int),
	scoreCount:  Maybe(int),
}

HELP_TEXT :: `Usage: maifetch [options]

Options:
  -a, -t, --access-token <token>   MaiTea access token
  -c, --config-file <path>         JSON config file path
  -l, --logo-size <number>         ASCII logo size; <=0 disables logo
  -s, --score-count <number>       Recent score count; max 12
  -h, --help                       Show this help

Environment:
  MAITEA_TOKEN / MAIFETCH_TOKEN
  MAITEA_CONFIG_FILE / MAIFETCH_CONFIG_FILE
  MAITEA_LOGO_SIZE / MAIFETCH_LOGO_SIZE
  MAITEA_SCORE_COUNT / MAIFETCH_SCORE_COUNT
`

default_config_file :: proc() -> string {
	when ODIN_OS == .Windows {
		if appdata, ok := os.lookup_env("APPDATA", context.allocator); ok {
			return fmt.aprintf("%s/maifetch.json", appdata)
		}
	} else {
		if xdg, ok := os.lookup_env("XDG_CONFIG_HOME", context.allocator); ok {
			return fmt.aprintf("%s/maifetch.json", xdg)
		}
		if home, ok := os.lookup_env("HOME", context.allocator); ok {
			when ODIN_OS == .Darwin {
				return fmt.aprintf("%s/Library/Application Support/maifetch.json", home)
			} else {
				return fmt.aprintf("%s/.config/maifetch.json", home)
			}
		}
	}
	return "maifetch.json"
}

parse_args :: proc(args: []string) -> (opts: Cli_Options, err: string) {
	for i := 0; i < len(args); i += 1 {
		arg := args[i]
		switch arg {
		case "-h", "--help":
			opts.help = true
		case "-a", "-t", "--access-token":
			i += 1
			if i >= len(args) {
				err = "missing value for access token"
				return
			}
			opts.access_token = args[i]
		case "-c", "--config-file":
			i += 1
			if i >= len(args) {
				err = "missing value for config file"
				return
			}
			opts.config_file = args[i]
		case "-l", "--logo-size":
			i += 1
			if i >= len(args) {
				err = "missing value for logo size"
				return
			}
			value, ok := strconv.parse_int(args[i])
			if !ok {
				err = "invalid logo size"
				return
			}
			opts.logo_size = value
		case "-s", "--score-count":
			i += 1
			if i >= len(args) {
				err = "missing value for score count"
				return
			}
			value, ok := strconv.parse_int(args[i])
			if !ok {
				err = "invalid score count"
				return
			}
			opts.score_count = value
		case:
			err = fmt.aprintf("unknown argument: %s", arg)
			return
		}
	}
	return
}

read_file_config :: proc(path: string, cfg: ^Config) {
	bytes, file_err := os.read_entire_file(path, context.allocator)
	if file_err != nil {
		return
	}
	defer delete(bytes)

	parsed: File_Config
	if json.unmarshal(bytes, &parsed) != nil {
		return
	}
	if token, ok := parsed.accessToken.?; ok && token != "" {
		cfg.access_token = token
	}
	if logo_size, ok := parsed.logoSize.?; ok {
		cfg.logo_size = logo_size
	}
	if score_count, ok := parsed.scoreCount.?; ok {
		cfg.score_count = score_count
	}
}

apply_env_string :: proc(cfg: ^Config, names: []string, field: string) {
	for name in names {
		if value, ok := os.lookup_env(name, context.allocator); ok && value != "" {
			switch field {
			case "access_token":
				cfg.access_token = value
			case "config_file":
				cfg.config_file = value
			}
			return
		}
	}
}

apply_env_number :: proc(names: []string) -> (value: int, ok: bool, err: string) {
	for name in names {
		if raw, found := os.lookup_env(name, context.allocator); found && raw != "" {
			value, ok = strconv.parse_int(raw)
			if !ok {
				err = fmt.aprintf("invalid numeric value for %s", name)
			}
			return
		}
	}
	return 0, false, ""
}

load_config :: proc(args: []string) -> (cfg: Config, opts: Cli_Options, err: string) {
	opts, err = parse_args(args)
	if err != "" || opts.help {
		return
	}

	cfg = Config{
		config_file = default_config_file(),
		logo_size   = 20,
		score_count = 4,
	}

	if path, ok := opts.config_file.?; ok {
		cfg.config_file = path
	}
	read_file_config(cfg.config_file, &cfg)

	apply_env_string(&cfg, []string{"MAITEA_TOKEN", "MAIFETCH_TOKEN"}, "access_token")
	apply_env_string(&cfg, []string{"MAITEA_CONFIG_FILE", "MAIFETCH_CONFIG_FILE"}, "config_file")

	if value, ok, env_err := apply_env_number([]string{"MAITEA_LOGO_SIZE", "MAIFETCH_LOGO_SIZE"}); env_err != "" {
		err = env_err
		return
	} else if ok {
		cfg.logo_size = value
	}
	if value, ok, env_err := apply_env_number([]string{"MAITEA_SCORE_COUNT", "MAIFETCH_SCORE_COUNT"}); env_err != "" {
		err = env_err
		return
	} else if ok {
		cfg.score_count = value
	}

	if token, ok := opts.access_token.?; ok {
		cfg.access_token = token
	}
	if path, ok := opts.config_file.?; ok {
		cfg.config_file = path
	}
	if logo_size, ok := opts.logo_size.?; ok {
		cfg.logo_size = logo_size
	}
	if score_count, ok := opts.score_count.?; ok {
		cfg.score_count = score_count
	}

	if cfg.access_token == "" {
		err = "access token is required"
		return
	}
	if cfg.score_count > 12 {
		err = "score count cannot be higher than 12"
		return
	}
	return
}

@(test)
test_parse_args_supports_documented_and_legacy_token_flags :: proc(t: ^testing.T) {
	opts, err := parse_args([]string{"-a", "abc", "-l", "0", "-s", "2"})
	testing.expect_value(t, err, "")
	token, token_ok := opts.access_token.?
	testing.expect(t, token_ok)
	testing.expect_value(t, token, "abc")
	logo_size, logo_ok := opts.logo_size.?
	testing.expect(t, logo_ok)
	testing.expect_value(t, logo_size, 0)
	score_count, score_ok := opts.score_count.?
	testing.expect(t, score_ok)
	testing.expect_value(t, score_count, 2)

	legacy, legacy_err := parse_args([]string{"-t", "old"})
	testing.expect_value(t, legacy_err, "")
	legacy_token, legacy_ok := legacy.access_token.?
	testing.expect(t, legacy_ok)
	testing.expect_value(t, legacy_token, "old")
}

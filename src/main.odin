package main

import "core:fmt"
import "core:os"

main :: proc() {
	args := os.args
	if len(args) > 1 {
		args = args[1:]
	} else {
		args = []string{}
	}

	cfg, opts, cfg_err := load_config(args)
	if cfg_err != "" {
		fmt.eprintln(cfg_err)
		fmt.eprintln(HELP_TEXT)
		return
	}
	if opts.help {
		fmt.println(HELP_TEXT)
		return
	}

	client := Api_Client{access_token = cfg.access_token}
	profiles, profiles_err := get_profiles(client)
	if profiles_err != "" {
		fmt.eprintln(profiles_err)
		return
	}
	if len(profiles.data) == 0 {
		fmt.println("No profiles found")
		return
	}

	plays, plays_err := get_plays(client)
	if plays_err != "" {
		fmt.eprintln(plays_err)
		return
	}

	output := format_output(profiles.data[0], plays.data, cfg.logo_size, cfg.score_count)
	fmt.print(output)
}

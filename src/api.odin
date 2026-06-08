package main

import "core:encoding/json"
import "core:fmt"
import "core:os"
import "core:strings"

Api_Client :: struct {
	access_token: string,
}

curl_get :: proc(client: Api_Client, path: string) -> (body: []byte, err: string) {
	url := path
	if !strings.has_prefix(path, BASE_URL) {
		url = fmt.aprintf("%s%s", BASE_URL, path)
	}

	auth_header := fmt.aprintf("Authorization: Bearer %s", client.access_token)
	command := []string{
		"curl",
		"-fsSL",
		"-H",
		auth_header,
		"-H",
		"Accept: application/json",
		"-H",
		"Content-Type: application/json",
		url,
	}
	state, stdout, stderr, process_err := os.process_exec(os.Process_Desc{command = command}, context.allocator)
	if process_err != nil {
		err = "failed to start curl"
		return
	}
	if state.exit_code != 0 {
		err = fmt.aprintf("curl failed: %s", string(stderr))
		delete(stdout)
		delete(stderr)
		return
	}
	delete(stderr)
	body = stdout
	return
}

get_profiles :: proc(client: Api_Client) -> (profiles: Profiles_Response, err: string) {
	body, fetch_err := curl_get(client, "/api/v1/profiles")
	if fetch_err != "" {
		err = fetch_err
		return
	}
	defer delete(body)

	if json.unmarshal(body, &profiles) != nil {
		err = "failed to parse profiles response"
		return
	}
	return
}
get_plays :: proc(client: Api_Client) -> (plays: Plays_Page, err: string) {
	body, fetch_err := curl_get(client, "/api/v1/plays")
	if fetch_err != "" {
		err = fetch_err
		return
	}
	defer delete(body)

	if json.unmarshal(body, &plays) != nil {
		err = "failed to parse plays response"
		return
	}
	return
}

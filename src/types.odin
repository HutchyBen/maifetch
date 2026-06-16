package main

BASE_URL :: "https://maitea.app"

Image :: struct {
	id:   int,
	png:  string,
	webp: string,
}

Localized_Name :: struct {
	en: string,
	jp: string,
}

Track_Info :: struct {
	id:     int,
	code:   string,
	name:   Localized_Name,
	artist: Localized_Name,
}

Play_Ref :: struct {
	id:        int,
	date:      string,
	date_unix: int,
	api_route: string,
}

Play_Stats :: struct {
	total:  int,
	wins:   int,
	vs:     int,
	sync:   int,
	first:  Play_Ref,
	latest: Play_Ref,
}

Nameplate :: struct {
	id:   int,
	png:  string,
	webp: string,
}

Profile_Options :: struct {
	icon:      Image,
	icon_deka: Image,
	nameplate: Nameplate,
	frame:     Nameplate,
}

Profile :: struct {
	id:             int,
	name:           string,
	rating:         int,
	rating_highest: int,
	level:          int,
	play_stats:     Play_Stats,
	options:        Profile_Options,
}

Notes :: struct {
	perfect: int,
	great:   int,
	good:    int,
	bad:     int,
}

Score_Detail :: struct {
	hits:  Notes,
	tap:   Notes,
	hold:  Notes,
	slide: Notes,
	break_: Notes `json:"break"`,
}

Difficulty_Level :: struct {
	key:   int,
	value: string,
	label: string,
}

Play :: struct {
	id:                     int,
	achievement:            int,
	achievement_formatted:  string,
	track:                  int,
	score:                  int,
	score_formatted:        string,
	score_detail:           Score_Detail,
	rank:                   string,
	full_combo:             int,
	full_combo_label:       Maybe(string),
	is_high_score:          bool,
	is_all_perfect:         bool,
	is_track_skip:          bool,
	difficulty_level:       Difficulty_Level,
	play_date:              string,
	play_date_unix:         int,
	song:                   Track_Info,
	player:                 Profile,
}

Profiles_Response :: struct {
	data: []Profile,
}

Plays_Page :: struct {
	data:  []Play,
	links: struct {
		first: string,
		last:  string,
		prev:  Maybe(string),
		next:  Maybe(string),
	},
	meta: struct {
		current_page: int,
		from:         Maybe(int),
		last_page:    int,
		path:         string,
		per_page:     int,
		to:           Maybe(int),
		total:        int,
	},
}

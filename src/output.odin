package main

import "core:fmt"
import "core:strings"
import "core:testing"
import "core:unicode/utf8"

write_colour :: proc(b: ^strings.Builder, text: string) {
	fmt.sbprintf(b, "\x1b[38;2;72;184;200m%s\x1b[0m", text)
}

write_fg :: proc(b: ^strings.Builder, text: string, r, g, blue: int) {
	fmt.sbprintf(b, "\x1b[38;2;%i;%i;%im%s\x1b[0m", r, g, blue, text)
}

write_fg_bg :: proc(b: ^strings.Builder, text: string, fr, fg, fb, br, bg, bb: int) {
	fmt.sbprintf(b, "\x1b[38;2;%i;%i;%im\x1b[48;2;%i;%i;%im%s\x1b[0m", fr, fg, fb, br, bg, bb, text)
}

write_difficulty :: proc(b: ^strings.Builder, diff: string) {
	switch diff {
	case "easy":
		write_fg_bg(b, "Easy", 255, 255, 255, 69, 174, 255)
	case "basic":
		write_fg_bg(b, "Basic", 255, 255, 255, 111, 212, 61)
	case "advanced":
		write_fg_bg(b, "Advanced", 255, 255, 255, 248, 183, 9)
	case "expert":
		write_fg_bg(b, "Expert", 255, 255, 255, 255, 46, 66)
	case "master":
		write_fg_bg(b, "Master", 255, 255, 255, 171, 140, 233)
	case "remaster", "re:master":
		write_fg_bg(b, "Re:Master", 255, 255, 255, 207, 114, 237)
	case "utage":
		write_fg_bg(b, "Utage", 255, 255, 255, 255, 68, 1)
	case:
		strings.write_string(b, diff)
	}
}

write_rank :: proc(b: ^strings.Builder, rank: string) {
	switch rank {
	case "SSS+":
		write_fg(b, "S", 255, 200, 54)
		write_fg(b, "S", 225, 38, 165)
		write_fg(b, "S", 73, 64, 233)
		write_fg(b, "+", 21, 203, 148)
	case "SSS":
		write_fg(b, "S", 255, 200, 54)
		write_fg(b, "S", 232, 39, 148)
		write_fg(b, "S", 18, 195, 144)
	case "SS+", "SS":
		write_fg_bg(b, rank, 248, 200, 75, 143, 71, 33)
	case "S+", "S":
		write_fg_bg(b, rank, 248, 200, 75, 75, 82, 82)
	case "AAA", "AA", "A":
		write_fg(b, rank, 23, 163, 255)
	case:
		strings.write_string(b, rank)
	}
}

wide_to_normal :: proc(text: string, allocator := context.allocator) -> string {
	b := strings.builder_make(allocator)
	defer strings.builder_destroy(&b)

	rest := text
	for len(rest) > 0 {
		cp, size := utf8.decode_rune(rest)
		if size <= 0 {
			break
		}
		if cp >= 0xff01 && cp <= 0xff5e {
			cp -= 0xfee0
		}
		strings.write_rune(&b, cp)
		rest = rest[size:]
	}
	return strings.clone(strings.to_string(b), allocator)
}

write_rating :: proc(b: ^strings.Builder, raw: int) {
	whole := raw / 100
	cents := raw % 100
	if cents < 0 {
		cents = -cents
	}
	fmt.sbprintf(b, "%i.%02i", whole, cents)
}

format_output :: proc(profile: Profile, plays: []Play, logo_size, score_count: int, allocator := context.allocator) -> string {
	_ = logo_size
	b := strings.builder_make(allocator)

	name := wide_to_normal(profile.name, allocator)
	write_colour(&b, name)
	strings.write_byte(&b, '\n')
	for _ in 0..<len(name) {
		strings.write_byte(&b, '-')
	}
	strings.write_byte(&b, '\n')

	write_colour(&b, "ID")
	fmt.sbprintf(&b, ": %i\n", profile.id)
	write_colour(&b, "Rating")
	strings.write_string(&b, ": ")
	write_rating(&b, profile.rating)
	strings.write_string(&b, " / ")
	write_rating(&b, profile.rating_highest)
	strings.write_byte(&b, '\n')
	write_colour(&b, "Level")
	fmt.sbprintf(&b, ": %i\n", profile.level)
	write_colour(&b, "Total Credits")
	fmt.sbprintf(&b, ": %i\n", profile.play_stats.total)
	write_colour(&b, "Recent Scores")
	strings.write_string(&b, ":\n")

	limit := score_count
	if limit > len(plays) {
		limit = len(plays)
	}
	if limit < 0 {
		limit = 0
	}
	for i in 0..<limit {
		play := plays[i]
		fmt.sbprintf(&b, "  %s  ", play.song.name.en)
		write_difficulty(&b, play.difficulty_level.value)
		strings.write_byte(&b, '\n')
		fmt.sbprintf(&b, "  %s %s%% ", play.score_formatted, play.achievement_formatted)
		write_rank(&b, play.rank)
		if label, ok := play.full_combo_label.?; ok && label != "" {
			fmt.sbprintf(&b, " %s", label)
		}
		strings.write_string(&b, " \n\n")
	}

	return strings.to_string(b)
}

@(test)
test_wide_characters_are_normalized :: proc(t: ^testing.T) {
	normalized := wide_to_normal("\xef\xbc\xa1\xef\xbc\xa2\xef\xbc\xa3\xef\xbc\x91\xef\xbc\x92\xef\xbc\x93")
	testing.expect_value(t, normalized, "ABC123")
}

@(test)
test_output_contains_profile_and_scores :: proc(t: ^testing.T) {
	profile := Profile{
		id = 7,
		name = "Alice",
		rating = 12345,
		rating_highest = 13000,
		level = 42,
		play_stats = {total = 99},
	}
	plays := []Play{{
		score_formatted = "1,000,000",
		achievement_formatted = "100.0000",
		rank = "SSS+",
		full_combo_label = "FC",
		difficulty_level = {value = "expert"},
		song = {name = {en = "Song A"}},
	}}
	out := format_output(profile, plays, 0, 1)
	testing.expect(t, strings.contains(out, "Alice"))
	testing.expect(t, strings.contains(out, "Rating"))
	testing.expect(t, strings.contains(out, "Song A"))
	testing.expect(t, strings.contains(out, "1,000,000 100.0000%"))
}

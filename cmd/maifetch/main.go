package main

import (
	"fmt"
	_ "image/jpeg"
	_ "image/png"
	"maifetch/pkg/maitea"
	"strings"
)

func createInfoStrings(profile maitea.Profile, plays []maitea.Play) []string {
	name := WideToNormal(profile.Name)

	scoreStrings := make([]string, 5*3) // this is really lazy coding line1. name and diff, line2. score and achivement and fc label. line3. padding

	// get 5 latest plays
	for i := 0; i < 5; i++ {
		play := plays[i]
		fcLabel := ""
		if play.FullComboLabel != nil {
			fcLabel = *play.FullComboLabel
		}
		scoreStrings[i*3] = fmt.Sprintf("  %s  %s", play.Song.Name.En, maitea.DifficultyString(play.DifficultyLevel.Value))
		scoreStrings[i*3+1] = fmt.Sprintf("  %s %s%% %s %s", play.ScoreFormatted, play.AchievementFormatted, maitea.RankString(play.Rank), fcLabel)
		scoreStrings[i*3+2] = ""
	}

	return append([]string{
		Colour(name),
		strings.Repeat("-", len(name)),
		fmt.Sprintf("%s: %d", Colour("ID"), profile.Id),
		fmt.Sprintf("%s: %.2f / %.2f", Colour("Rating"), float32(profile.Rating)/100.0, float32(profile.RatingHighest)/100),
		fmt.Sprintf("%s: %d", Colour("Level"), profile.Level),
		fmt.Sprintf("%s: %d", Colour("Total Credits"), profile.PlayStats.Total),
		fmt.Sprintf("%s:", Colour("Recent Scores")),
	}, scoreStrings...)
}

func Output(api *maitea.APIClient, profile maitea.Profile, logoSize int) {
	logo, err := UrlToAscii(profile.Options.Icon.Png, logoSize)
	if err != nil {
		fmt.Println(err)
		return
	}

	logoLines := strings.Split(logo, "\n")
	plays, err := api.GetPlays()
	if err != nil {
		fmt.Println(err)
		return
	}
	page := plays.CurrentPage()
	infoLines := createInfoStrings(profile, page)

	// Output all the lines together
	maxLength := len(logoLines)
	if len(infoLines) > maxLength {
		maxLength = len(infoLines)
	}
	padding := strings.Repeat(" ", 2)
	// Iterate up to the maximum length
	for i := 0; i < maxLength; i++ {
		logoStr := strings.Repeat(" ", logoSize*2)
		infoStr := ""
		if i < len(logoLines)-1 {
			logoStr = logoLines[i]
		}
		if i < len(infoLines)-1 {
			infoStr = infoLines[i]
		}
		fmt.Println(logoStr, padding, infoStr)
	}
}

func main() {
	logoSize := 20
	client := maitea.NewAPIClient("PUT YOUR FLIPPING TOKEN HERE") // todo: not be lazy and make a config system or something lol
	profiles, err := client.GetProfiles()
	if err != nil {
		fmt.Println(err)
	}

	// TODO: Make command line options

	if len(profiles) == 0 {
		fmt.Println("No profiles found")
		return
	}

	Output(client, profiles[0], logoSize)
}

package main

import (
	"fmt"
	"github.com/briandowns/spinner"
	_ "image/jpeg"
	_ "image/png"
	"maifetch/pkg/maitea"
	"strings"
	"time"
)

func createInfoStrings(profile maitea.Profile, plays []maitea.Play, scoreCount uint) []string {
	name := WideToNormal(profile.Name)

	scoreStrings := make([]string, scoreCount*3) // this is really lazy coding line1. name and diff, line2. score and achivement and fc label. line3. padding

	// get 5 latest plays
	for i := uint(0); i < scoreCount; i++ {
		play := plays[i]
		fcLabel := ""
		if play.FullComboLabel != nil {
			fcLabel = *play.FullComboLabel
		}
		scoreStrings[i*3] = fmt.Sprintf("  %s  %s", play.Song.Name.En, maitea.DifficultyString(play.DifficultyLevel.Value))
		scoreStrings[i*3+1] = fmt.Sprintf("  %s %s%% %s %s", play.ScoreFormatted, play.AchievementFormatted, maitea.RankString(play.Rank), fcLabel)
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

func printCombined(infoLines []string, logoLines []string, logoSize int) {
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

func Output(page []maitea.Play, profile maitea.Profile, logoSize int, scoreCount uint) {
	infoLines := createInfoStrings(profile, page, scoreCount)

	if logoSize > 0 {
		logo, err := UrlToAscii(profile.Options.Icon.Png, logoSize)
		if err != nil {
			fmt.Println(err)
			return
		}
		logoLines := strings.Split(logo, "\n")
		printCombined(infoLines, logoLines, logoSize)

	} else {
		fmt.Println(strings.Join(infoLines, "\n"))
	}
}

func main() {
	config, err := LoadConfig()
	if err != nil {
		fmt.Println(err)
		return
	}

	client := maitea.NewAPIClient(config.AccessToken)
	profiles, err := client.GetProfiles()
	if err != nil {
		fmt.Println(err)
		return
	}

	if len(profiles) == 0 {
		fmt.Println("No profiles found")
		return
	}

	// get users recent plays
	apiLoading := make(chan []maitea.Play)
	go func() {

		plays, err := client.GetPlays()
		if err != nil {
			fmt.Println(err)
			return
		}
		apiLoading <- plays.CurrentPage()
	}()
	s := spinner.New(spinner.CharSets[9], 100*time.Millisecond)
	s.Suffix = " Loading..."
	s.Start()
	select {
	case plays := <-apiLoading:
		s.Stop()
		Output(plays, profiles[0], config.LogoSize, config.ScoreCount)
		return
	}
}

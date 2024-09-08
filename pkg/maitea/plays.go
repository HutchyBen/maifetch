package maitea

import (
	"time"
)

type Notes struct {
	Perfect int `json:"perfect"`
	Great   int `json:"great"`
	Good    int `json:"good"`
	Bad     int `json:"bad"`
}

type Score struct {
	Id                   int     `json:"id"`
	Achievement          int     `json:"achievement"`
	AchievementFormatted string  `json:"achievement_formatted"`
	Score                int     `json:"score"`
	ScoreFormatted       string  `json:"score_formatted"`
	Rank                 string  `json:"rank"`
	FullCombo            int     `json:"full_combo"`
	FullComboLabel       *string `json:"full_combo_label"`
	IsAllPerfect         bool    `json:"is_all_perfect"`
	IsAllPerfectPlus     bool    `json:"is_all_perfect_plus"`
	DifficultyLevel      struct {
		Key   int    `json:"key"`
		Value string `json:"value"`
		Label string `json:"label"`
	} `json:"difficulty_level"`
	Song   TrackInfo `json:"song"`
	Player Profile   `json:"player"`
}

// ughhh if only i could do a cheeky clean embedding of score into play but i dont wanna deal with the fact all perfect plus isnt in play lol

type Play struct {
	Id                   int    `json:"id"`
	Achievement          int    `json:"achievement"`
	AchievementFormatted string `json:"achievement_formatted"`
	Track                int    `json:"track"`
	Score                int    `json:"score"`
	ScoreFormatted       string `json:"score_formatted"`
	ScoreDetail          struct {
		Hits  Notes `json:"hits"`
		Tap   Notes `json:"tap"`
		Hold  Notes `json:"hold"`
		Slide Notes `json:"slide"`
		Break Notes `json:"break"`
	} `json:"score_detail"`
	Rank            string  `json:"rank"`
	FullCombo       int     `json:"full_combo"`
	FullComboLabel  *string `json:"full_combo_label"`
	IsHighScore     bool    `json:"is_high_score"`
	IsAllPerfect    bool    `json:"is_all_perfect"`
	IsTrackSkip     bool    `json:"is_track_skip"`
	DifficultyLevel struct {
		Key   int    `json:"key"`
		Value string `json:"value"`
		Label string `json:"label"`
	} `json:"difficulty_level"`
	PlayDate     time.Time `json:"play_date"`
	PlayDateUnix int       `json:"play_date_unix"`
	Song         TrackInfo `json:"song"`
	Player       Profile   `json:"player"`
}

// GetPlays will get recent plays done by the authenticated user
// It is sorted by recently played and is paginated
func (api *APIClient) GetPlays() (Pager[[]Play], error) {
	page, err := getPage[[]Play](api, baseURL+"/api/v1/plays")
	if err != nil {
		return Pager[[]Play]{}, err
	}

	return Pager[[]Play]{page}, nil
}

// GetAllPlays will get all the plays done by the authenticated user
// It is sorted by recently played and is NOT paginated
// This is very slow. GetPlays is recommended
func (api *APIClient) GetAllPlays() (Pager[[]Play], error) {
	page, err := getPage[[]Play](api, baseURL+"/api/v1/plays/all")
	if err != nil {
		return Pager[[]Play]{}, err
	}

	return Pager[[]Play]{page}, nil
}

// GetBestScores will get recent plays done by the authenticated user
// It is sorted by internal ID and is paginated
func (api *APIClient) GetBestScores() (Pager[[]Score], error) {
	page, err := getPage[[]Score](api, baseURL+"/api/v1/scores")
	if err != nil {
		return Pager[[]Score]{}, err
	}

	return Pager[[]Score]{page}, nil
}

// GetAllBestScores will get all the best scores done by the authenticated user
// It is sorted by internal ID and is NOT paginated
// This is very slow. GetBestScores is recommended
func (api *APIClient) GetAllBestScores() (Pager[[]Score], error) {
	page, err := getPage[[]Score](api, baseURL+"/api/v1/scores/all")
	if err != nil {
		return Pager[[]Score]{}, err
	}

	return Pager[[]Score]{page}, nil
}

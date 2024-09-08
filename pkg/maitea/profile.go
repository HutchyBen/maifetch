package maitea

import (
	"encoding/json"
	"time"
)

type Profile struct {
	Id            int    `json:"id"`
	Name          string `json:"name"`
	Rating        int    `json:"rating"`
	RatingHighest int    `json:"rating_highest"`
	Level         int    `json:"level"`
	PlayStats     struct {
		Total int `json:"total"`
		Wins  int `json:"wins"`
		Vs    int `json:"vs"`
		Sync  int `json:"sync"`
		First struct {
			Id       int       `json:"id"`
			Date     time.Time `json:"date"`
			DateUnix int       `json:"date_unix"`
			ApiRoute string    `json:"api_route"`
		} `json:"first"`
		Latest struct {
			Id       int       `json:"id"`
			Date     time.Time `json:"date"`
			DateUnix int       `json:"date_unix"`
			ApiRoute string    `json:"api_route"`
		} `json:"latest"`
	} `json:"play_stats"`
	Options struct {
		Icon      Image `json:"icon"`
		IconDeka  Image `json:"icon_deka"`
		Nameplate struct {
			Id   int    `json:"id"`
			Png  string `json:"png"`
			Webp string `json:"webp"`
		} `json:"nameplate"`
		Frame struct {
			Id   int    `json:"id"`
			Png  string `json:"png"`
			Webp string `json:"webp"`
		} `json:"frame"`
	} `json:"options"`
}

// GetProfiles will return all profiles attached to the access token given
func (api *APIClient) GetProfiles() ([]Profile, error) {
	res, err := api.Get("/api/v1/profiles")
	if err != nil {
		return nil, err
	}

	profiles := struct {
		Data []Profile `json:"data"`
	}{}

	err = json.NewDecoder(res.Body).Decode(&profiles)
	if err != nil {
		return nil, err
	}

	return profiles.Data, nil
}

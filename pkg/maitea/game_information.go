package maitea

import (
	"encoding/json"
)

type TrackInfo struct {
	Id   int    `json:"id"`
	Code string `json:"code"`
	Name struct {
		En string `json:"en"`
		Jp string `json:"jp"`
	} `json:"name"`
	Artist struct {
		En string `json:"en"`
		Jp string `json:"jp"`
	} `json:"artist"`
}

// GetTracks will return every track available in MaiMai
func (api *APIClient) GetTracks() ([]TrackInfo, error) {
	res, err := api.Get("/api/v1/tracks")
	if err != nil {
		return nil, err
	}

	jsonRes := struct {
		Data []TrackInfo `json:"data"`
	}{}

	err = json.NewDecoder(res.Body).Decode(&jsonRes)
	if err != nil {
		return nil, err
	}
	return jsonRes.Data, nil
}

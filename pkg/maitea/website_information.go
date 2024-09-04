package maitea

import "encoding/json"

type Status struct {
	Webui struct {
		Api    string `json:"api"`
		DbRead struct {
			Status    string `json:"status"`
			QueryTime string `json:"query_time"`
		} `json:"db_read"`
		DbWrite struct {
			Status    string `json:"status"`
			QueryTime string `json:"query_time"`
		} `json:"db_write"`
	} `json:"webui"`
	Game struct {
		Status string `json:"status"`
	} `json:"game"`
	LastUpdated int64 `json:"last_updated"`
}

// Status will return the server status of MaiTea
func (api *APIClient) Status() (Status, error) {
	res, err := api.Get("/api/status")
	if err != nil {
		return Status{}, err
	}

	var status Status
	err = json.NewDecoder(res.Body).Decode(&status)
	if err != nil {
		return Status{}, err
	}
	return status, nil
}

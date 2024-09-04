package maitea

import "net/http"

var baseURL string = "https://maitea.app"

type Image struct {
	Id   int    `json:"id"`
	Png  string `json:"png"`
	Webp string `json:"webp"`
}

type APIClient struct {
	accessToken string
	client      http.Client
}

// Creates a new APIClient with the access token and a default http.Client
func NewAPIClient(accessToken string) *APIClient {
	return &APIClient{accessToken, http.Client{}}
}

// Get is used when making requests to MaiTea and ensures all requests are authenticated
func (api *APIClient) Get(url string) (*http.Response, error) {
	req, err := http.NewRequest("GET", baseURL+url, nil)
	if err != nil {
		return nil, err
	}

	req.Header.Add("Authorization", "Bearer "+api.accessToken)
	req.Header.Add("Content-Type", "application/json")
	req.Header.Add("Accept", "application/json")

	return api.client.Do(req)
}

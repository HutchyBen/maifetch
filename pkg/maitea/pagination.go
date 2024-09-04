package maitea

import (
	"encoding/json"
	"strings"
)

type PageNonExistant struct{}

func (m *PageNonExistant) Error() string {
	return "Page does not exist"
}

type Pager[T any] struct {
	currentPage PagerPage[T]
}

type PagerPage[T any] struct {
	apiClient *APIClient // TODO: find better way please i beg
	Data      T          `json:"data"`
	Links     struct {
		First string  `json:"first"`
		Last  string  `json:"last"`
		Prev  *string `json:"prev"`
		Next  *string `json:"next"`
	} `json:"links"`
	Meta struct {
		CurrentPage int `json:"current_page"`
		From        int `json:"from"`
		LastPage    int `json:"last_page"`
		Links       []struct {
			Url    *string `json:"url"`
			Label  string  `json:"label"`
			Active bool    `json:"active"`
		} `json:"links"`
		Path    string `json:"path"`
		PerPage int    `json:"per_page"`
		To      int    `json:"to"`
		Total   int    `json:"total"`
	} `json:"meta"`
}

// I am depressed I have to pass apiClient as a parameter even though its not user facing.
func getPage[T any](apiClient *APIClient, pageURL string) (PagerPage[T], error) {
	res, err := apiClient.Get(strings.TrimPrefix(pageURL, "https://maitea.app")) // this is not elegant
	if err != nil {
		return PagerPage[T]{}, err
	}
	var page PagerPage[T]
	err = json.NewDecoder(res.Body).Decode(&page)
	if err != nil {
		return PagerPage[T]{}, err
	}
	page.apiClient = apiClient //😭
	return page, nil
}

func (p *Pager[T]) CurrentPage() T {
	return p.currentPage.Data
}

func (p *Pager[T]) Next() (T, error) {
	if p.currentPage.Links.Next == nil {
		return p.currentPage.Data, &PageNonExistant{}
	}

	page, err := getPage[T](p.currentPage.apiClient, *p.currentPage.Links.Next) //😭
	if err != nil {
		return p.currentPage.Data, err
	}

	p.currentPage = page
	return page.Data, nil
}

func (p *Pager[T]) Prev() (T, error) {
	if p.currentPage.Links.Prev == nil {
		return p.currentPage.Data, &PageNonExistant{}
	}

	page, err := getPage[T](p.currentPage.apiClient, *p.currentPage.Links.Prev) //😭
	if err != nil {
		return p.currentPage.Data, err
	}

	p.currentPage = page
	return page.Data, nil
}

func (p *Pager[T]) Last() (T, error) {
	page, err := getPage[T](p.currentPage.apiClient, p.currentPage.Links.Last) //😭
	if err != nil {
		return p.currentPage.Data, err
	}

	p.currentPage = page
	return page.Data, nil
}

func (p *Pager[T]) First() (T, error) {
	page, err := getPage[T](p.currentPage.apiClient, p.currentPage.Links.First) //😭
	if err != nil {
		return p.currentPage.Data, err
	}

	p.currentPage = page
	return page.Data, nil
}

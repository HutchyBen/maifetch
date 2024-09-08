package main

import (
	"image"
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/aybabtme/rgbterm"
	"github.com/qeesung/image2ascii/convert"
)

func WideToNormal(str string) string {
	out := make([]rune, utf8.RuneCountInString(str))

	i := 0
	for _, chr := range str {
		out[i] = chr - 0xFEE0
		i++
	}
	return string(out)
}

func Colour(str string) string {
	return rgbterm.FgString(str, 72, 184, 200)
}

func UrlToAscii(url string, size int) (string, error) {
	convertOptions := convert.DefaultOptions
	convertOptions.FixedWidth = size * 2
	convertOptions.FixedHeight = size
	// Create the image converter
	converter := convert.NewImageConverter()
	res, err := http.Get(url)
	if err != nil {
		return "", err
	}

	img, _, err := image.Decode(res.Body)
	if err != nil {
		return "", err
	}

	// make black black!!!
	logo := converter.Image2ASCIIString(img, &convertOptions)
	blankChar := rgbterm.FgString("#", 0, 0, 0)
	return strings.ReplaceAll(logo, " ", blankChar), nil
}

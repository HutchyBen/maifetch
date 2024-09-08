package main

import (
	"encoding/json"
	"fmt"
	"github.com/alecthomas/kong"
	"github.com/kelseyhightower/envconfig"
	"os"
)

type MaifetchConfig struct {
	AccessToken string   `envconfig:"token" json:"accessToken" help:"Access Token for the MaiTea account" short:"t"`
	ConfigFile  *os.File `help:"Config file to use" short:"c" json:"-" envconfig:"CONFIG_FILE"`
	ScoreCount  uint     `help:"Amount of recent scores to view (max 12)" json:"scoreCount" envconfig:"SCORE_COUNT" short:"s"`
	LogoSize    int      `envconfig:"logo_size" json:"logoSize" help:"Size of the ASCII logo (<1 to disable)" short:"l"`
}

// LoadConfig will load config in order of priority CLI > ENV_VARS > CONFIG_FILE
func LoadConfig() (*MaifetchConfig, error) {
	var cliOptions MaifetchConfig // this is seperate as needs to be parsed first as generally want config file from here but takes priority later
	var config MaifetchConfig

	defaultPath, err := os.UserConfigDir()
	if err != nil {
		return nil, err
	}

	// default. I would do this as a kong default but I don't want to deal with what if user explicitly says 20 later on
	config.LogoSize = 20
	config.ScoreCount = 4 // typical cab is 4 songs a credit from my small sample size of like 4 maimai places ive been

	kong.Parse(&cliOptions, kong.Vars{
		"config": defaultPath,
	})

	// Load JSON
	if cliOptions.ConfigFile == nil {
		// TODO: add a verbose error logging option
		cliOptions.ConfigFile, _ = os.Open(defaultPath + "/maifetch.json")
	}

	// if config file exists load it
	if cliOptions.ConfigFile != nil {
		defer func(jsonFile *os.File) {
			err = jsonFile.Close()
			if err != nil {
				panic("could not close json file")
			}
		}(cliOptions.ConfigFile)
		err = json.NewDecoder(cliOptions.ConfigFile).Decode(&config)
		if err != nil {
			return nil, err
		}
	}

	// Override JSON with environment
	err = envconfig.Process("maifetch", &config)
	if err != nil {
		return nil, err
	}

	// Override with cliOptions
	if cliOptions.AccessToken != "" {
		config.AccessToken = cliOptions.AccessToken
	}

	if cliOptions.LogoSize != 0 {
		config.LogoSize = cliOptions.LogoSize
	}

	if cliOptions.ScoreCount > 0 {
		config.ScoreCount = cliOptions.ScoreCount
	}

	if config.AccessToken == "" {
		return nil, fmt.Errorf("access token is required")
	}

	if config.ScoreCount > 12 {
		return nil, fmt.Errorf("score count cannot be higher than 12")
	}

	return &config, nil
}

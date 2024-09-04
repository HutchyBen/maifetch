package maitea

import "github.com/aybabtme/rgbterm"

// this is absolute slop lolol

func DifficultyString(diff string) string {
	switch diff {
	case "easy":
		return rgbterm.String("Easy", 255, 255, 255, 69, 174, 255)
	case "basic":
		return rgbterm.String("Basic", 255, 255, 255, 111, 212, 61)
	case "advanced":
		return rgbterm.String("Advanced", 255, 255, 255, 248, 183, 9)
	case "expert":
		return rgbterm.String("Expert", 255, 255, 255, 255, 46, 66)
	case "master":
		return rgbterm.String("Master", 255, 255, 255, 171, 140, 233)
	case "remaster":
	case "re:master":
		return rgbterm.String("Re:Master", 255, 255, 255, 207, 114, 237)
	case "utage":
		return rgbterm.String("Utage", 255, 255, 255, 255, 68, 1)
	default:
		return diff
	}
	return diff // why is this throwing an error?
}

func RankString(rank string) string {
	switch rank {
	case "SSS+":
		return rgbterm.FgString("S", 255, 200, 54) + rgbterm.FgString("S", 225, 38, 165) + rgbterm.FgString("S", 73, 64, 233) + rgbterm.FgString("+", 21, 203, 148)
	case "SSS":
		return rgbterm.FgString("S", 255, 200, 54) + rgbterm.FgString("S", 232, 39, 148) + rgbterm.FgString("S", 18, 195, 144)
	case "SS+":
		return rgbterm.String("SS+", 248, 200, 75, 143, 71, 33)
	case "SS":
		return rgbterm.String("SS", 248, 200, 75, 143, 71, 33)
	case "S+":
		return rgbterm.String("S+", 248, 200, 75, 75, 82, 82)
	case "S":
		return rgbterm.String("S", 248, 200, 75, 75, 82, 82)
	case "AAA":
		return rgbterm.FgString("AAA", 23, 163, 255)
	case "AA":
		return rgbterm.FgString("AA", 23, 163, 255)
	case "A":
		return rgbterm.FgString("A", 23, 163, 255)
	default:
		return rank
	}
}

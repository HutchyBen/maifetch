// AI-assisted with OpenAI GPT-5.
namespace Maifetch

type Image =
    { Id: int
      Png: string
      Webp: string }

type LocalizedName =
    { En: string
      Jp: string }

type TrackInfo =
    { Id: int
      Code: string
      Name: LocalizedName
      Artist: LocalizedName }

type DifficultyLevel =
    { Key: int
      Value: string
      Label: string }

type PlayStats =
    { Total: int
      Wins: int
      Vs: int
      Sync: int }

type ProfileOptions =
    { Icon: Image
      IconDeka: Image }

type Profile =
    { Id: int
      Name: string
      Rating: int
      RatingHighest: int
      Level: int
      PlayStats: PlayStats
      Options: ProfileOptions }

type Play =
    { Id: int
      Achievement: int
      AchievementFormatted: string
      Track: int
      Score: int
      ScoreFormatted: string
      Rank: string
      FullCombo: int
      FullComboLabel: string option
      DifficultyLevel: DifficultyLevel
      Song: TrackInfo
      Player: Profile option }

type PageLinks =
    { First: string
      Last: string
      Prev: string option
      Next: string option }

type PagerPage<'T> =
    { Data: 'T
      Links: PageLinks }

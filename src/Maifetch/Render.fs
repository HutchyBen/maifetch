// AI-assisted with OpenAI GPT-5.
namespace Maifetch

open System
open System.Globalization
open System.Text

module Render =
    let private esc = char 27

    let color foreground background text =
        let fr, fg, fb = foreground
        let br, bg, bb = background
        $"{esc}[38;2;{fr};{fg};{fb};48;2;{br};{bg};{bb}m{text}{esc}[0m"

    let fg foreground text =
        let r, g, b = foreground
        $"{esc}[38;2;{r};{g};{b}m{text}{esc}[0m"

    let accent text = fg (72, 184, 200) text

    let rating value = (decimal value / 100M).ToString("0.00", CultureInfo.InvariantCulture)

    let wideToNormal (value: string) =
        let builder = StringBuilder()

        for ch in value do
            let code = int ch

            if code >= 0xFF01 && code <= 0xFF5E then
                builder.Append(char (code - 0xFEE0)) |> ignore
            else
                builder.Append ch |> ignore

        builder.ToString()

    let difficultyLabel value =
        match value with
        | "easy" -> color (255, 255, 255) (69, 174, 255) "Easy"
        | "basic" -> color (255, 255, 255) (111, 212, 61) "Basic"
        | "advanced" -> color (255, 255, 255) (248, 183, 9) "Advanced"
        | "expert" -> color (255, 255, 255) (255, 46, 66) "Expert"
        | "master" -> color (255, 255, 255) (171, 140, 233) "Master"
        | "remaster"
        | "re:master" -> color (255, 255, 255) (207, 114, 237) "Re:Master"
        | "utage" -> color (255, 255, 255) (255, 68, 1) "Utage"
        | other -> other

    let rankLabel value =
        match value with
        | "SSS+" -> fg (255, 200, 54) "S" + fg (225, 38, 165) "S" + fg (73, 64, 233) "S" + fg (21, 203, 148) "+"
        | "SSS" -> fg (255, 200, 54) "S" + fg (232, 39, 148) "S" + fg (18, 195, 144) "S"
        | "SS+"
        | "SS" -> color (248, 200, 75) (143, 71, 33) value
        | "S+"
        | "S" -> color (248, 200, 75) (75, 82, 82) value
        | "AAA"
        | "AA"
        | "A" -> fg (23, 163, 255) value
        | other -> other

    let infoLines profile plays scoreCount =
        let name = wideToNormal profile.Name
        let count = Math.Min(Math.Max(scoreCount, 0), List.length plays)
        let idLabel = accent "ID"
        let ratingLabel = accent "Rating"
        let levelLabel = accent "Level"
        let totalCreditsLabel = accent "Total Credits"
        let recentScoresLabel = accent "Recent Scores"

        let header =
            [ accent name
              String.replicate name.Length "-"
              $"{idLabel}: {profile.Id}"
              $"{ratingLabel}: {rating profile.Rating} / {rating profile.RatingHighest}"
              $"{levelLabel}: {profile.Level}"
              $"{totalCreditsLabel}: {profile.PlayStats.Total}"
              $"{recentScoresLabel}:" ]

        let scoreLines =
            plays
            |> List.truncate count
            |> List.collect (fun play ->
                let difficulty = difficultyLabel play.DifficultyLevel.Value
                let rank = rankLabel play.Rank
                let fullCombo = defaultArg play.FullComboLabel ""

                [ $"  {play.Song.Name.En}  {difficulty}"
                  $"  {play.ScoreFormatted} {play.AchievementFormatted}%% {rank} {fullCombo}"
                  "" ])

        header @ scoreLines

    let print profile plays scoreCount =
        infoLines profile plays scoreCount |> List.iter (printfn "%s")

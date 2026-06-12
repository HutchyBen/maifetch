// AI-assisted with OpenAI GPT-5.
namespace Maifetch.Tests

open System
open Maifetch

module Program =
    let private expect condition message =
        if not condition then
            failwith message

    let private expectEqual expected actual message =
        if expected <> actual then
            failwith $"{message}. Expected: {expected}; actual: {actual}"

    let private withEnv name value test =
        let prior = Environment.GetEnvironmentVariable name
        Environment.SetEnvironmentVariable(name, value)

        try
            test ()
        finally
            Environment.SetEnvironmentVariable(name, prior)

    let cliOverridesEnvironment () =
        withEnv "MAITEA_CONFIG_FILE" "/tmp/maifetch-test-missing.json" (fun () ->
            withEnv "MAITEA_TOKEN" "env-token" (fun () ->
                withEnv "MAITEA_SCORE_COUNT" "3" (fun () ->
                    let config = Config.load [| "--access-token"; "cli-token"; "--score-count"; "6"; "--logo-size"; "0" |]
                    expectEqual "cli-token" config.AccessToken "CLI access token should win"
                    expectEqual 6 config.ScoreCount "CLI score count should win"
                    expectEqual 0 config.LogoSize "CLI logo size should win")))

    let scoreCountIsCapped () =
        withEnv "MAITEA_CONFIG_FILE" "/tmp/maifetch-test-missing.json" (fun () ->
            try
                Config.load [| "--access-token"; "token"; "--score-count"; "13" |] |> ignore
                failwith "score count above twelve should fail"
            with :? ArgumentException as error ->
                expect (error.Message.Contains "score count cannot be higher than 12") "score-count error text")

    let accessTokenIsRequired () =
        withEnv "MAITEA_CONFIG_FILE" "/tmp/maifetch-test-missing.json" (fun () ->
            withEnv "MAITEA_TOKEN" null (fun () ->
                withEnv "MAIFETCH_TOKEN" null (fun () ->
                    try
                        Config.load [||] |> ignore
                        failwith "missing access token should fail"
                    with :? ArgumentException as error ->
                        expect (error.Message.Contains "access token is required") "access-token error text")))

    let renderIncludesProfileAndRecentScore () =
        let profile =
            { Id = 123
              Name = "Ｔｅｓｔ"
              Rating = 1500
              RatingHighest = 1600
              Level = 42
              PlayStats = { Total = 99; Wins = 0; Vs = 0; Sync = 0 }
              Options = { Icon = { Id = 0; Png = ""; Webp = "" }; IconDeka = { Id = 0; Png = ""; Webp = "" } } }

        let play =
            { Id = 1
              Achievement = 1000000
              AchievementFormatted = "100.0000"
              Track = 1
              Score = 1000000
              ScoreFormatted = "1,000,000"
              Rank = "SSS+"
              FullCombo = 1
              FullComboLabel = Some "FC"
              DifficultyLevel = { Key = 0; Value = "master"; Label = "Master" }
              Song = { Id = 1; Code = "song"; Name = { En = "Song"; Jp = "" }; Artist = { En = ""; Jp = "" } }
              Player = None }

        let joined = Render.infoLines profile [ play ] 1 |> String.concat "\n"
        expect (joined.Contains "Test") "full-width profile name should normalize"
        expect (joined.Contains "123") "profile id should render"
        expect (joined.Contains "1,000,000") "score should render"
        expect (joined.Contains "100.0000%") "achievement should render"
        expect (joined.Contains "Song") "song name should render"

    [<EntryPoint>]
    let main _ =
        let tests =
            [ "cli overrides environment", cliOverridesEnvironment
              "score count capped", scoreCountIsCapped
              "access token required", accessTokenIsRequired
              "render output", renderIncludesProfileAndRecentScore ]

        for name, test in tests do
            test ()
            printfn "ok - %s" name

        0

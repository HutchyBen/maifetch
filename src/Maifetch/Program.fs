// AI-assisted with OpenAI GPT-5.
namespace Maifetch

open System

module Program =
    [<EntryPoint>]
    let main args =
        try
            let config = Config.load args
            let client = MaiteaClient config
            let profiles = client.GetProfilesAsync().GetAwaiter().GetResult()

            match profiles with
            | [] ->
                printfn "No profiles found"
                0
            | profile :: _ ->
                printf "Loading..."
                let plays = client.GetPlaysAsync().GetAwaiter().GetResult()
                printfn ""
                Render.print profile plays.Data config.ScoreCount
                0
        with
        | :? Config.HelpRequested ->
            printfn "%s" Config.usage
            0
        | :? ArgumentException as error ->
            printfn "%s" error.Message
            1
        | error ->
            printfn "%s" error.Message
            1

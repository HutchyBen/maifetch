// AI-assisted with OpenAI GPT-5.
namespace Maifetch

open System
open System.IO
open System.Text.Json

type Config =
    { AccessToken: string
      ScoreCount: int
      LogoSize: int
      ConfigFile: string option
      BaseUrl: string }

type CliOptions =
    { AccessToken: string option
      ScoreCount: int option
      LogoSize: int option
      ConfigFile: string option
      BaseUrl: string option }

module Config =
    let private emptyCli: CliOptions =
        { AccessToken = None
          ScoreCount = None
          LogoSize = None
          ConfigFile = None
          BaseUrl = None }

    let defaults: Config =
        { AccessToken = ""
          ScoreCount = 4
          LogoSize = 20
          ConfigFile = None
          BaseUrl = "https://maitea.app" }

    let usage =
        String.concat
            Environment.NewLine
            [ "Usage: maifetch [options]"
              ""
              "Options:"
              "  -a, -t, --access-token <token>  Access token for the MaiTea account"
              "  -s, --score-count <count>       Amount of recent scores to view (max 12)"
              "  -l, --logo-size <size>          Size of the ASCII logo (<1 disables)"
              "  -c, --config-file <path>        JSON config file to use"
              "      --base-url <url>            MaiTea API base URL"
              "  -h, --help                      Show this help" ]

    exception HelpRequested

    let private parseInt name (value: string) =
        match Int32.TryParse value with
        | true, parsed -> parsed
        | false, _ -> invalidArg name $"invalid integer for {name}: {value}"

    let parseCli (args: string array) =
        let rec loop index (options: CliOptions) =
            if index >= args.Length then
                options
            else
                let arg = args[index]

                let nextValue () =
                    if index + 1 >= args.Length then
                        invalidArg arg $"missing value for {arg}"

                    args[index + 1]

                match arg with
                | "--access-token"
                | "-a"
                | "-t" -> loop (index + 2) { options with AccessToken = Some(nextValue ()) }
                | "--score-count"
                | "-s" -> loop (index + 2) { options with ScoreCount = Some(parseInt arg (nextValue ())) }
                | "--logo-size"
                | "-l" -> loop (index + 2) { options with LogoSize = Some(parseInt arg (nextValue ())) }
                | "--config-file"
                | "-c" -> loop (index + 2) { options with ConfigFile = Some(nextValue ()) }
                | "--base-url" -> loop (index + 2) { options with BaseUrl = Some(nextValue ()) }
                | "--help"
                | "-h" -> raise HelpRequested
                | _ -> invalidArg arg $"unknown argument: {arg}"

        loop 0 emptyCli

    let private env (names: string list) =
        names
        |> List.tryPick (fun name ->
            let value = Environment.GetEnvironmentVariable name
            if String.IsNullOrWhiteSpace value then None else Some value)

    let defaultConfigPath () =
        if OperatingSystem.IsWindows() then
            match env [ "APPDATA" ] with
            | Some appData -> Path.Combine(appData, "maifetch.json")
            | None -> Path.Combine(".", "maifetch.json")
        elif OperatingSystem.IsMacOS() then
            let home = Environment.GetFolderPath Environment.SpecialFolder.UserProfile
            Path.Combine(home, "Library", "Application Support", "maifetch.json")
        else
            match env [ "XDG_CONFIG_HOME" ] with
            | Some xdg -> Path.Combine(xdg, "maifetch.json")
            | None ->
                let home = Environment.GetFolderPath Environment.SpecialFolder.UserProfile
                Path.Combine(home, ".config", "maifetch.json")

    let private getString (element: JsonElement) (name: string) =
        match element.TryGetProperty name with
        | true, value when value.ValueKind = JsonValueKind.String -> Some(value.GetString())
        | _ -> None

    let private getInt (element: JsonElement) (name: string) =
        match element.TryGetProperty name with
        | true, value when value.ValueKind = JsonValueKind.Number ->
            match value.TryGetInt32() with
            | true, parsed -> Some parsed
            | false, _ -> None
        | _ -> None

    let readConfigFile (path: string): Config =
        if not (File.Exists path) then
            defaults
        else
            use document = JsonDocument.Parse(File.ReadAllText path)
            let root = document.RootElement

            { defaults with
                AccessToken = defaultArg (getString root "accessToken") defaults.AccessToken
                ScoreCount = defaultArg (getInt root "scoreCount") defaults.ScoreCount
                LogoSize = defaultArg (getInt root "logoSize") defaults.LogoSize
                BaseUrl = defaultArg (getString root "baseUrl") defaults.BaseUrl }

    let private choose first second fallback =
        first |> Option.orElse second |> Option.defaultValue fallback

    let load (args: string array): Config =
        let cli = parseCli args

        let configPath =
            cli.ConfigFile
            |> Option.orElse (env [ "MAITEA_CONFIG_FILE"; "MAIFETCH_CONFIG_FILE" ])
            |> Option.defaultWith defaultConfigPath

        let file = readConfigFile configPath

        let merged: Config =
            { AccessToken = choose cli.AccessToken (env [ "MAITEA_TOKEN"; "MAIFETCH_TOKEN" ]) file.AccessToken
              ScoreCount =
                choose
                    cli.ScoreCount
                    (env [ "MAITEA_SCORE_COUNT"; "MAIFETCH_SCORE_COUNT" ] |> Option.map (parseInt "score-count"))
                    file.ScoreCount
              LogoSize =
                choose
                    cli.LogoSize
                    (env [ "MAITEA_LOGO_SIZE"; "MAIFETCH_LOGO_SIZE" ] |> Option.map (parseInt "logo-size"))
                    file.LogoSize
              ConfigFile = Some configPath
              BaseUrl = choose cli.BaseUrl (env [ "MAITEA_BASE_URL"; "MAIFETCH_BASE_URL" ]) file.BaseUrl }

        if String.IsNullOrWhiteSpace merged.AccessToken then
            invalidArg "access-token" "access token is required"

        if merged.ScoreCount > 12 then
            invalidArg "score-count" "score count cannot be higher than 12"

        merged

// AI-assisted with OpenAI GPT-5.
namespace Maifetch

open System
open System.Net.Http
open System.Net.Http.Headers
open System.Text.Json
open System.Threading.Tasks

type MaiteaClient(config: Config, ?httpClient: HttpClient) =
    let client = defaultArg httpClient (new HttpClient())
    let baseUri = Uri(config.BaseUrl.TrimEnd('/') + "/")

    do
        client.DefaultRequestHeaders.Authorization <- AuthenticationHeaderValue("Bearer", config.AccessToken)
        client.DefaultRequestHeaders.UserAgent.ParseAdd("maifetch-fsharp/0.1.0")

    let getJsonAsync (path: string) =
        task {
            let uri =
                if Uri.IsWellFormedUriString(path, UriKind.Absolute) then Uri path
                else Uri(baseUri, path.TrimStart('/'))

            use! response = client.GetAsync uri
            response.EnsureSuccessStatusCode() |> ignore
            let! body = response.Content.ReadAsStringAsync()
            return JsonDocument.Parse body
        }

    let stringAt (fallback: string) (name: string) (element: JsonElement) =
        match element.TryGetProperty name with
        | true, value when value.ValueKind = JsonValueKind.String -> value.GetString()
        | _ -> fallback

    let intAt (fallback: int) (name: string) (element: JsonElement) =
        match element.TryGetProperty name with
        | true, value when value.ValueKind = JsonValueKind.Number ->
            match value.TryGetInt32() with
            | true, parsed -> parsed
            | false, _ -> fallback
        | _ -> fallback

    let optionStringAt (name: string) (element: JsonElement) =
        match element.TryGetProperty name with
        | true, value when value.ValueKind = JsonValueKind.String -> Some(value.GetString())
        | _ -> None

    let elementAt (name: string) (element: JsonElement) =
        match element.TryGetProperty name with
        | true, value -> Some value
        | _ -> None

    let parseImage (element: JsonElement): Image =
        { Id = intAt 0 "id" element
          Png = stringAt "" "png" element
          Webp = stringAt "" "webp" element }

    let parseLocalized (element: JsonElement): LocalizedName =
        { En = stringAt "" "en" element
          Jp = stringAt "" "jp" element }

    let parseTrack (element: JsonElement): TrackInfo =
        { Id = intAt 0 "id" element
          Code = stringAt "" "code" element
          Name = elementAt "name" element |> Option.map parseLocalized |> Option.defaultValue { En = ""; Jp = "" }
          Artist = elementAt "artist" element |> Option.map parseLocalized |> Option.defaultValue { En = ""; Jp = "" } }

    let parseDifficulty (element: JsonElement): DifficultyLevel =
        { Key = intAt 0 "key" element
          Value = stringAt "" "value" element
          Label = stringAt "" "label" element }

    let parsePlayStats (element: JsonElement): PlayStats =
        { Total = intAt 0 "total" element
          Wins = intAt 0 "wins" element
          Vs = intAt 0 "vs" element
          Sync = intAt 0 "sync" element }

    let parseProfileOptions (element: JsonElement): ProfileOptions =
        { Icon = elementAt "icon" element |> Option.map parseImage |> Option.defaultValue { Id = 0; Png = ""; Webp = "" }
          IconDeka =
            elementAt "icon_deka" element
            |> Option.orElse (elementAt "iconDeka" element)
            |> Option.map parseImage
            |> Option.defaultValue { Id = 0; Png = ""; Webp = "" } }

    let parseProfile (element: JsonElement): Profile =
        { Id = intAt 0 "id" element
          Name = stringAt "" "name" element
          Rating = intAt 0 "rating" element
          RatingHighest =
            elementAt "rating_highest" element
            |> Option.map (fun value -> if value.ValueKind = JsonValueKind.Number then value.GetInt32() else 0)
            |> Option.orElse (elementAt "ratingHighest" element |> Option.map (fun value -> value.GetInt32()))
            |> Option.defaultValue 0
          Level = intAt 0 "level" element
          PlayStats =
            elementAt "play_stats" element
            |> Option.orElse (elementAt "playStats" element)
            |> Option.map parsePlayStats
            |> Option.defaultValue { Total = 0; Wins = 0; Vs = 0; Sync = 0 }
          Options =
            elementAt "options" element
            |> Option.map parseProfileOptions
            |> Option.defaultValue { Icon = { Id = 0; Png = ""; Webp = "" }; IconDeka = { Id = 0; Png = ""; Webp = "" } } }

    let parseLinks (element: JsonElement): PageLinks =
        { First = stringAt "" "first" element
          Last = stringAt "" "last" element
          Prev = optionStringAt "prev" element
          Next = optionStringAt "next" element }

    let parsePlay (element: JsonElement): Play =
        { Id = intAt 0 "id" element
          Achievement = intAt 0 "achievement" element
          AchievementFormatted = stringAt "" "achievement_formatted" element
          Track = intAt 0 "track" element
          Score = intAt 0 "score" element
          ScoreFormatted = stringAt "" "score_formatted" element
          Rank = stringAt "" "rank" element
          FullCombo = intAt 0 "full_combo" element
          FullComboLabel = optionStringAt "full_combo_label" element
          DifficultyLevel =
            elementAt "difficulty_level" element
            |> Option.map parseDifficulty
            |> Option.defaultValue { Key = 0; Value = ""; Label = "" }
          Song = elementAt "song" element |> Option.map parseTrack |> Option.defaultValue { Id = 0; Code = ""; Name = { En = ""; Jp = "" }; Artist = { En = ""; Jp = "" } }
          Player = elementAt "player" element |> Option.map parseProfile }

    member _.GetProfilesAsync() =
        task {
            use! document = getJsonAsync "/api/v1/profiles"
            return [ for item in document.RootElement.EnumerateArray() -> parseProfile item ]
        }

    member _.GetPlaysAsync() =
        task {
            use! document = getJsonAsync "/api/v1/plays"
            let root = document.RootElement
            let data = elementAt "data" root |> Option.defaultValue root
            let links = elementAt "links" root |> Option.map parseLinks |> Option.defaultValue { First = ""; Last = ""; Prev = None; Next = None }
            return { Data = [ for item in data.EnumerateArray() -> parsePlay item ]; Links = links }
        }

use reqwest::blocking::{Client, Response};
use serde::de::DeserializeOwned;
use serde::Deserialize;
use std::error::Error;
use std::fmt;
use std::time::Duration;

const BASE_URL: &str = "https://maitea.app";

#[derive(Debug, Deserialize)]
pub struct Image {
    pub id: i64,
    pub png: String,
    pub webp: String,
}

#[derive(Debug, Deserialize)]
pub struct Profile {
    pub id: i64,
    pub name: String,
    pub rating: i64,
    pub rating_highest: i64,
    pub level: i64,
    pub play_stats: PlayStats,
    pub options: ProfileOptions,
}

#[derive(Debug, Deserialize)]
pub struct PlayStats {
    pub total: i64,
    pub wins: i64,
    pub vs: i64,
    pub sync: i64,
    pub first: Option<ProfilePlayMarker>,
    pub latest: Option<ProfilePlayMarker>,
}

#[derive(Debug, Deserialize)]
pub struct ProfilePlayMarker {
    pub id: i64,
    pub date: String,
    pub date_unix: i64,
    pub api_route: String,
}

#[derive(Debug, Deserialize)]
pub struct ProfileOptions {
    pub icon: Image,
    pub icon_deka: Image,
    pub nameplate: Image,
    pub frame: Image,
}

#[derive(Debug, Deserialize)]
pub struct TrackInfo {
    pub id: i64,
    pub code: String,
    pub name: LocalizedName,
    pub artist: LocalizedName,
}

#[derive(Debug, Deserialize)]
pub struct LocalizedName {
    pub en: String,
    pub jp: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct DifficultyLevel {
    pub key: Option<i64>,
    pub value: String,
    pub label: String,
}

#[derive(Debug, Deserialize)]
pub struct Notes {
    pub perfect: i64,
    pub great: i64,
    pub good: i64,
    pub bad: i64,
}

#[derive(Debug, Deserialize)]
pub struct ScoreDetail {
    pub hits: Notes,
    pub tap: Notes,
    pub hold: Notes,
    pub slide: Notes,
    pub r#break: Notes,
}

#[derive(Debug, Deserialize)]
pub struct Play {
    pub id: i64,
    pub achievement_formatted: String,
    pub achievement: i64,
    pub track: i64,
    pub score: i64,
    pub score_formatted: String,
    pub score_detail: ScoreDetail,
    pub rank: String,
    pub full_combo: i64,
    pub full_combo_label: Option<String>,
    pub is_high_score: bool,
    pub is_all_perfect: bool,
    pub is_track_skip: bool,
    pub difficulty_level: DifficultyLevel,
    pub play_date: Option<String>,
    pub play_date_unix: Option<i64>,
    pub song: TrackInfo,
    pub player: Profile,
}

#[derive(Debug, Deserialize)]
pub struct Score {
    pub id: i64,
    pub achievement: i64,
    pub achievement_formatted: String,
    pub score: i64,
    pub score_formatted: String,
    pub rank: String,
    pub full_combo: i64,
    pub full_combo_label: Option<String>,
    pub is_all_perfect: bool,
    pub is_all_perfect_plus: bool,
    pub difficulty_level: DifficultyLevel,
    pub song: TrackInfo,
    pub player: Profile,
}

#[derive(Debug, Deserialize)]
pub struct Status {
    pub webui: WebStatus,
    pub game: GameStatus,
    pub last_updated: i64,
}

#[derive(Debug, Deserialize)]
pub struct WebStatus {
    pub api: String,
    pub db_read: DbStatus,
    pub db_write: DbStatus,
}

#[derive(Debug, Deserialize)]
pub struct DbStatus {
    pub status: String,
    pub query_time: String,
}

#[derive(Debug, Deserialize)]
pub struct GameStatus {
    pub status: String,
}

#[derive(Debug, Deserialize)]
struct DataEnvelope<T> {
    data: T,
}

#[derive(Debug)]
pub struct PageDoesNotExist;

impl fmt::Display for PageDoesNotExist {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("Page does not exist")
    }
}

impl Error for PageDoesNotExist {}

pub struct Pager<T> {
    current_page: PagerPage<T>,
}

#[derive(Debug)]
pub struct PagerPage<T> {
    data: T,
    links: PageLinks,
    meta: PageMeta,
}

#[derive(Debug, Deserialize)]
struct RawPagerPage<T> {
    data: T,
    links: PageLinks,
    meta: PageMeta,
}

#[derive(Debug, Deserialize)]
pub struct PageLinks {
    pub first: String,
    pub last: String,
    pub prev: Option<String>,
    pub next: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct PageMeta {
    pub current_page: i64,
    pub from: Option<i64>,
    pub last_page: i64,
    pub links: Vec<PageMetaLink>,
    pub path: String,
    pub per_page: i64,
    pub to: Option<i64>,
    pub total: i64,
}

#[derive(Debug, Deserialize)]
pub struct PageMetaLink {
    pub url: Option<String>,
    pub label: String,
    pub active: bool,
}

impl<T> Pager<T>
where
    T: DeserializeOwned,
{
    pub fn current_page(&self) -> &T {
        &self.current_page.data
    }

    pub fn page_info(&self) -> &PageMeta {
        &self.current_page.meta
    }

    pub fn links(&self) -> &PageLinks {
        &self.current_page.links
    }

    pub fn next(&mut self, api: &ApiClient) -> Result<&T, Box<dyn Error>> {
        let url = self
            .current_page
            .links
            .next
            .clone()
            .ok_or(PageDoesNotExist)?;
        self.current_page = api.get_page(&url)?;
        Ok(&self.current_page.data)
    }

    pub fn prev(&mut self, api: &ApiClient) -> Result<&T, Box<dyn Error>> {
        let url = self
            .current_page
            .links
            .prev
            .clone()
            .ok_or(PageDoesNotExist)?;
        self.current_page = api.get_page(&url)?;
        Ok(&self.current_page.data)
    }

    pub fn last(&mut self, api: &ApiClient) -> Result<&T, Box<dyn Error>> {
        self.current_page = api.get_page(&self.current_page.links.last)?;
        Ok(&self.current_page.data)
    }

    pub fn first(&mut self, api: &ApiClient) -> Result<&T, Box<dyn Error>> {
        self.current_page = api.get_page(&self.current_page.links.first)?;
        Ok(&self.current_page.data)
    }
}

pub struct ApiClient {
    access_token: String,
    client: Client,
}

impl ApiClient {
    pub fn new(access_token: String) -> Result<Self, Box<dyn Error>> {
        let client = Client::builder().timeout(Duration::from_secs(30)).build()?;

        Ok(Self {
            access_token,
            client,
        })
    }

    pub fn get_profiles(&self) -> Result<Vec<Profile>, Box<dyn Error>> {
        self.get_data("/api/v1/profiles")
    }

    pub fn get_tracks(&self) -> Result<Vec<TrackInfo>, Box<dyn Error>> {
        self.get_data("/api/v1/tracks")
    }

    pub fn status(&self) -> Result<Status, Box<dyn Error>> {
        self.get("/api/status")?.json().map_err(Into::into)
    }

    pub fn get_plays(&self) -> Result<Pager<Vec<Play>>, Box<dyn Error>> {
        self.get_pager("/api/v1/plays")
    }

    pub fn get_all_plays(&self) -> Result<Pager<Vec<Play>>, Box<dyn Error>> {
        self.get_pager("/api/v1/plays/all")
    }

    pub fn get_best_scores(&self) -> Result<Pager<Vec<Score>>, Box<dyn Error>> {
        self.get_pager("/api/v1/scores")
    }

    pub fn get_all_best_scores(&self) -> Result<Pager<Vec<Score>>, Box<dyn Error>> {
        self.get_pager("/api/v1/scores/all")
    }

    pub fn get_bytes(&self, url: &str) -> Result<Vec<u8>, Box<dyn Error>> {
        let response = self.client.get(url).send()?.error_for_status()?;
        Ok(response.bytes()?.to_vec())
    }

    fn get_data<T>(&self, path: &str) -> Result<T, Box<dyn Error>>
    where
        T: DeserializeOwned,
    {
        let response: DataEnvelope<T> = self.get(path)?.json()?;
        Ok(response.data)
    }

    fn get_pager<T>(&self, path: &str) -> Result<Pager<T>, Box<dyn Error>>
    where
        T: DeserializeOwned,
    {
        Ok(Pager {
            current_page: self.get_page(path)?,
        })
    }

    fn get_page<T>(&self, page_url: &str) -> Result<PagerPage<T>, Box<dyn Error>>
    where
        T: DeserializeOwned,
    {
        let raw: RawPagerPage<T> = self.get(page_url)?.json()?;
        Ok(PagerPage {
            data: raw.data,
            links: raw.links,
            meta: raw.meta,
        })
    }

    fn get(&self, path: &str) -> Result<Response, Box<dyn Error>> {
        let route = path.strip_prefix(BASE_URL).unwrap_or(path);
        let url = format!("{}{}", BASE_URL, route);
        let response = self
            .client
            .get(url)
            .bearer_auth(&self.access_token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .send()?
            .error_for_status()?;

        Ok(response)
    }
}

pub fn difficulty_string(diff: &str) -> String {
    match diff {
        "easy" => bg("Easy", 69, 174, 255),
        "basic" => bg("Basic", 111, 212, 61),
        "advanced" => bg("Advanced", 248, 183, 9),
        "expert" => bg("Expert", 255, 46, 66),
        "master" => bg("Master", 171, 140, 233),
        "remaster" | "re:master" => bg("Re:Master", 207, 114, 237),
        "utage" => bg("Utage", 255, 68, 1),
        _ => diff.to_string(),
    }
}

pub fn rank_string(rank: &str) -> String {
    match rank {
        "SSS+" => format!(
            "{}{}{}{}",
            fg("S", 255, 200, 54),
            fg("S", 225, 38, 165),
            fg("S", 73, 64, 233),
            fg("+", 21, 203, 148)
        ),
        "SSS" => format!(
            "{}{}{}",
            fg("S", 255, 200, 54),
            fg("S", 232, 39, 148),
            fg("S", 18, 195, 144)
        ),
        "SS+" => color("SS+", (248, 200, 75), (143, 71, 33)),
        "SS" => color("SS", (248, 200, 75), (143, 71, 33)),
        "S+" => color("S+", (248, 200, 75), (75, 82, 82)),
        "S" => color("S", (248, 200, 75), (75, 82, 82)),
        "AAA" => fg("AAA", 23, 163, 255),
        "AA" => fg("AA", 23, 163, 255),
        "A" => fg("A", 23, 163, 255),
        _ => rank.to_string(),
    }
}

pub fn accent(text: &str) -> String {
    fg(text, 72, 184, 200)
}

fn fg(text: &str, r: u8, g: u8, b: u8) -> String {
    format!("\x1b[38;2;{};{};{}m{}\x1b[0m", r, g, b, text)
}

fn bg(text: &str, r: u8, g: u8, b: u8) -> String {
    color(text, (255, 255, 255), (r, g, b))
}

fn color(text: &str, foreground: (u8, u8, u8), background: (u8, u8, u8)) -> String {
    let (fr, fg, fb) = foreground;
    let (br, bg, bb) = background;
    format!(
        "\x1b[38;2;{};{};{}m\x1b[48;2;{};{};{}m{}\x1b[0m",
        fr, fg, fb, br, bg, bb, text
    )
}

use clap::Parser;
use image::imageops::FilterType;
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{self, Write};
use std::path::PathBuf;
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};
use std::thread;
use std::time::Duration;
use thiserror::Error;

const BASE_URL: &str = "https://maitea.app";

#[derive(Debug, Error)]
enum MaifetchError {
    #[error("access token is required")]
    MissingAccessToken,
    #[error("score count cannot be higher than 12")]
    ScoreCountTooHigh,
    #[error("No profiles found")]
    NoProfiles,
    #[error("{0}")]
    Io(#[from] io::Error),
    #[error("{0}")]
    Http(#[from] reqwest::Error),
    #[error("{0}")]
    Json(#[from] serde_json::Error),
    #[error("{0}")]
    Image(#[from] image::ImageError),
}

#[derive(Parser, Debug, Default)]
#[command(author, version, about)]
struct CliOptions {
    #[arg(short = 't', short_alias = 'a', long = "access-token")]
    access_token: Option<String>,

    #[arg(short = 'c', long = "config-file")]
    config_file: Option<PathBuf>,

    #[arg(short = 's', long = "score-count")]
    score_count: Option<usize>,

    #[arg(short = 'l', long = "logo-size")]
    logo_size: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct MaifetchConfig {
    #[serde(default)]
    access_token: String,
    #[serde(default = "default_logo_size")]
    logo_size: i32,
    #[serde(default = "default_score_count")]
    score_count: usize,
}

impl Default for MaifetchConfig {
    fn default() -> Self {
        Self {
            access_token: String::new(),
            logo_size: default_logo_size(),
            score_count: default_score_count(),
        }
    }
}

fn default_logo_size() -> i32 {
    20
}

fn default_score_count() -> usize {
    4
}

fn main() {
    if let Err(err) = run() {
        println!("{err}");
    }
}

fn run() -> Result<(), MaifetchError> {
    let config = load_config()?;
    let client = ApiClient::new(config.access_token)?;

    let profiles = client.get_profiles()?;
    let profile = profiles.first().ok_or(MaifetchError::NoProfiles)?;

    let spinner = Spinner::start("Loading...");
    let plays_result = client.get_plays();
    spinner.stop();

    let plays = plays_result?.current_page();
    output(&plays, profile, config.logo_size, config.score_count)
}

fn load_config() -> Result<MaifetchConfig, MaifetchError> {
    let cli = CliOptions::parse();
    let mut config = MaifetchConfig::default();

    let config_file = cli
        .config_file
        .clone()
        .or_else(|| env_first(&["MAIFETCH_CONFIG_FILE", "MAITEA_CONFIG_FILE"]).map(PathBuf::from))
        .or_else(default_config_path);
    if let Some(path) = config_file {
        match File::open(&path) {
            Ok(file) => config = serde_json::from_reader(file)?,
            Err(err) if cli.config_file.is_some() => return Err(err.into()),
            Err(_) => {}
        }
    }

    apply_env(&mut config);

    if let Some(access_token) = cli.access_token {
        config.access_token = access_token;
    }
    if let Some(logo_size) = cli.logo_size {
        config.logo_size = logo_size;
    }
    if let Some(score_count) = cli.score_count {
        config.score_count = score_count;
    }

    if config.access_token.is_empty() {
        return Err(MaifetchError::MissingAccessToken);
    }
    if config.score_count > 12 {
        return Err(MaifetchError::ScoreCountTooHigh);
    }

    Ok(config)
}

fn default_config_path() -> Option<PathBuf> {
    dirs::config_dir().map(|path| path.join("maifetch.json"))
}

fn apply_env(config: &mut MaifetchConfig) {
    if let Some(value) = env_first(&["MAIFETCH_TOKEN", "MAITEA_TOKEN"]) {
        config.access_token = value;
    }
    if let Some(value) = env_first(&["MAIFETCH_LOGO_SIZE", "MAITEA_LOGO_SIZE"]) {
        if let Ok(parsed) = value.parse::<i32>() {
            config.logo_size = parsed;
        }
    }
    if let Some(value) = env_first(&["MAIFETCH_SCORE_COUNT", "MAITEA_SCORE_COUNT"]) {
        if let Ok(parsed) = value.parse::<usize>() {
            config.score_count = parsed;
        }
    }
}

fn env_first(names: &[&str]) -> Option<String> {
    names.iter().find_map(|name| match std::env::var(name) {
        Ok(value) if !value.is_empty() => Some(value),
        _ => None,
    })
}

struct Spinner {
    running: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl Spinner {
    fn start(message: &'static str) -> Self {
        let running = Arc::new(AtomicBool::new(true));
        let thread_running = Arc::clone(&running);
        let handle = thread::spawn(move || {
            let frames = ["-", "\\", "|", "/"];
            let mut index = 0;
            while thread_running.load(Ordering::Relaxed) {
                eprint!("\r{} {message}", frames[index % frames.len()]);
                let _ = io::stderr().flush();
                index += 1;
                thread::sleep(Duration::from_millis(100));
            }
            eprint!("\r{}\r", " ".repeat(message.len() + 4));
            let _ = io::stderr().flush();
        });

        Self {
            running,
            handle: Some(handle),
        }
    }

    fn stop(mut self) {
        self.running.store(false, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

#[derive(Clone, Debug)]
struct ApiClient {
    access_token: String,
    client: reqwest::blocking::Client,
}

impl ApiClient {
    fn new(access_token: String) -> Result<Self, reqwest::Error> {
        let client = reqwest::blocking::Client::builder()
            .timeout(Duration::from_secs(30))
            .build()?;

        Ok(Self {
            access_token,
            client,
        })
    }

    fn get<T: serde::de::DeserializeOwned>(&self, path_or_url: &str) -> Result<T, reqwest::Error> {
        let path = path_or_url.strip_prefix(BASE_URL).unwrap_or(path_or_url);
        let url = format!("{BASE_URL}{path}");

        self.client
            .get(url)
            .bearer_auth(&self.access_token)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .header(reqwest::header::ACCEPT, "application/json")
            .send()?
            .error_for_status()?
            .json()
    }

    fn get_profiles(&self) -> Result<Vec<Profile>, reqwest::Error> {
        let response: DataResponse<Vec<Profile>> = self.get("/api/v1/profiles")?;
        Ok(response.data)
    }

    #[allow(dead_code)]
    fn get_tracks(&self) -> Result<Vec<TrackInfo>, reqwest::Error> {
        let response: DataResponse<Vec<TrackInfo>> = self.get("/api/v1/tracks")?;
        Ok(response.data)
    }

    fn get_plays(&self) -> Result<Pager<Vec<Play>>, reqwest::Error> {
        let page = self.get_page("/api/v1/plays")?;
        Ok(Pager { current_page: page })
    }

    #[allow(dead_code)]
    fn get_all_plays(&self) -> Result<Pager<Vec<Play>>, reqwest::Error> {
        let page = self.get_page("/api/v1/plays/all")?;
        Ok(Pager { current_page: page })
    }

    #[allow(dead_code)]
    fn get_best_scores(&self) -> Result<Pager<Vec<Score>>, reqwest::Error> {
        let page = self.get_page("/api/v1/scores")?;
        Ok(Pager { current_page: page })
    }

    #[allow(dead_code)]
    fn get_all_best_scores(&self) -> Result<Pager<Vec<Score>>, reqwest::Error> {
        let page = self.get_page("/api/v1/scores/all")?;
        Ok(Pager { current_page: page })
    }

    #[allow(dead_code)]
    fn status(&self) -> Result<Status, reqwest::Error> {
        self.get("/api/status")
    }

    fn get_page<T: serde::de::DeserializeOwned>(
        &self,
        path_or_url: &str,
    ) -> Result<PagerPage<T>, reqwest::Error> {
        let mut page: PagerPage<T> = self.get(path_or_url)?;
        page.api_client = Some(self.clone());
        Ok(page)
    }
}

#[derive(Debug, Deserialize)]
struct DataResponse<T> {
    data: T,
}

#[derive(Clone, Debug, Deserialize)]
struct ImageInfo {
    #[allow(dead_code)]
    id: i32,
    png: String,
    #[allow(dead_code)]
    webp: String,
}

#[derive(Clone, Debug, Deserialize)]
struct LocalizedName {
    en: String,
    #[allow(dead_code)]
    jp: String,
}

#[derive(Clone, Debug, Deserialize)]
struct TrackInfo {
    #[allow(dead_code)]
    id: i32,
    #[allow(dead_code)]
    code: String,
    name: LocalizedName,
    #[allow(dead_code)]
    artist: LocalizedName,
}

#[derive(Clone, Debug, Deserialize)]
struct PlayStats {
    total: i32,
    #[allow(dead_code)]
    wins: i32,
    #[allow(dead_code)]
    vs: i32,
    #[allow(dead_code)]
    sync: i32,
}

#[derive(Clone, Debug, Deserialize)]
struct ProfileOptions {
    icon: ImageInfo,
}

#[derive(Clone, Debug, Deserialize)]
struct Profile {
    id: i32,
    name: String,
    rating: i32,
    rating_highest: i32,
    level: i32,
    play_stats: PlayStats,
    options: ProfileOptions,
}

#[derive(Clone, Debug, Deserialize)]
struct DifficultyLevel {
    #[allow(dead_code)]
    key: i32,
    value: String,
    #[allow(dead_code)]
    label: String,
}

#[derive(Clone, Debug, Deserialize)]
struct Notes {
    #[allow(dead_code)]
    perfect: i32,
    #[allow(dead_code)]
    great: i32,
    #[allow(dead_code)]
    good: i32,
    #[allow(dead_code)]
    bad: i32,
}

#[derive(Clone, Debug, Deserialize)]
struct ScoreDetail {
    #[allow(dead_code)]
    hits: Notes,
    #[allow(dead_code)]
    tap: Notes,
    #[allow(dead_code)]
    hold: Notes,
    #[allow(dead_code)]
    slide: Notes,
    #[serde(rename = "break")]
    #[allow(dead_code)]
    break_notes: Notes,
}

#[derive(Clone, Debug, Deserialize)]
struct Play {
    #[allow(dead_code)]
    id: i32,
    #[allow(dead_code)]
    achievement: i32,
    achievement_formatted: String,
    #[allow(dead_code)]
    track: i32,
    #[allow(dead_code)]
    score: i32,
    score_formatted: String,
    #[allow(dead_code)]
    #[serde(default)]
    score_detail: Option<ScoreDetail>,
    rank: String,
    #[allow(dead_code)]
    full_combo: i32,
    full_combo_label: Option<String>,
    #[allow(dead_code)]
    is_high_score: bool,
    #[allow(dead_code)]
    is_all_perfect: bool,
    #[allow(dead_code)]
    is_track_skip: bool,
    difficulty_level: DifficultyLevel,
    song: TrackInfo,
}

#[derive(Clone, Debug, Deserialize)]
struct Score {
    #[allow(dead_code)]
    id: i32,
    #[allow(dead_code)]
    achievement: i32,
    #[allow(dead_code)]
    achievement_formatted: String,
    #[allow(dead_code)]
    score: i32,
    #[allow(dead_code)]
    score_formatted: String,
    #[allow(dead_code)]
    rank: String,
    #[allow(dead_code)]
    full_combo: i32,
    #[allow(dead_code)]
    full_combo_label: Option<String>,
    #[allow(dead_code)]
    is_all_perfect: bool,
    #[allow(dead_code)]
    is_all_perfect_plus: bool,
    #[allow(dead_code)]
    difficulty_level: DifficultyLevel,
    #[allow(dead_code)]
    song: TrackInfo,
    #[allow(dead_code)]
    player: Profile,
}

#[derive(Clone, Debug, Deserialize)]
struct PagerLinks {
    first: String,
    last: String,
    prev: Option<String>,
    next: Option<String>,
}

#[derive(Clone, Debug, Deserialize)]
struct PagerPage<T> {
    #[serde(skip)]
    api_client: Option<ApiClient>,
    data: T,
    links: PagerLinks,
}

#[derive(Clone, Debug)]
struct Pager<T> {
    current_page: PagerPage<T>,
}

impl<T> Pager<T>
where
    T: Clone + serde::de::DeserializeOwned,
{
    fn current_page(&self) -> T {
        self.current_page.data.clone()
    }

    #[allow(dead_code)]
    fn next(&mut self) -> Result<T, MaifetchError> {
        let next = self
            .current_page
            .links
            .next
            .clone()
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "Page does not exist"))?;
        self.load_page(&next)
    }

    #[allow(dead_code)]
    fn prev(&mut self) -> Result<T, MaifetchError> {
        let prev = self
            .current_page
            .links
            .prev
            .clone()
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "Page does not exist"))?;
        self.load_page(&prev)
    }

    #[allow(dead_code)]
    fn first(&mut self) -> Result<T, MaifetchError> {
        let first = self.current_page.links.first.clone();
        self.load_page(&first)
    }

    #[allow(dead_code)]
    fn last(&mut self) -> Result<T, MaifetchError> {
        let last = self.current_page.links.last.clone();
        self.load_page(&last)
    }

    fn load_page(&mut self, url: &str) -> Result<T, MaifetchError> {
        let client = self
            .current_page
            .api_client
            .clone()
            .ok_or_else(|| io::Error::new(io::ErrorKind::Other, "missing API client"))?;
        self.current_page = client.get_page(url)?;
        Ok(self.current_page.data.clone())
    }
}

#[derive(Clone, Debug, Deserialize)]
struct Status {
    #[allow(dead_code)]
    webui: serde_json::Value,
    #[allow(dead_code)]
    game: serde_json::Value,
    #[allow(dead_code)]
    last_updated: i64,
}

fn output(
    plays: &[Play],
    profile: &Profile,
    logo_size: i32,
    score_count: usize,
) -> Result<(), MaifetchError> {
    let info_lines = create_info_strings(profile, plays, score_count);

    if logo_size > 0 {
        let logo = url_to_ascii(&profile.options.icon.png, logo_size as u32)?;
        let logo_lines: Vec<String> = logo.lines().map(str::to_owned).collect();
        print_combined(&info_lines, &logo_lines, logo_size);
    } else {
        println!("{}", info_lines.join("\n"));
    }

    Ok(())
}

fn create_info_strings(profile: &Profile, plays: &[Play], score_count: usize) -> Vec<String> {
    let name = wide_to_normal(&profile.name);
    let visible_scores = score_count.min(plays.len());
    let mut score_strings = vec![String::new(); score_count * 3];

    for i in 0..visible_scores {
        let play = &plays[i];
        let fc_label = play.full_combo_label.as_deref().unwrap_or("");
        score_strings[i * 3] = format!(
            "  {}  {}",
            play.song.name.en,
            difficulty_string(&play.difficulty_level.value)
        );
        score_strings[i * 3 + 1] = format!(
            "  {} {}% {} {}",
            play.score_formatted,
            play.achievement_formatted,
            rank_string(&play.rank),
            fc_label
        );
    }

    let mut lines = vec![
        colour(&name),
        "-".repeat(name.chars().count()),
        format!("{}: {}", colour("ID"), profile.id),
        format!(
            "{}: {:.2} / {:.2}",
            colour("Rating"),
            profile.rating as f32 / 100.0,
            profile.rating_highest as f32 / 100.0
        ),
        format!("{}: {}", colour("Level"), profile.level),
        format!("{}: {}", colour("Total Credits"), profile.play_stats.total),
        format!("{}:", colour("Recent Scores")),
    ];
    lines.extend(score_strings);
    lines
}

fn print_combined(info_lines: &[String], logo_lines: &[String], logo_size: i32) {
    let max_length = logo_lines.len().max(info_lines.len());
    let padding = "  ";
    let logo_width = (logo_size * 2).max(0) as usize;

    for i in 0..max_length {
        let logo_str = if i + 1 < logo_lines.len() {
            logo_lines[i].clone()
        } else {
            " ".repeat(logo_width)
        };
        let info_str = if i + 1 < info_lines.len() {
            info_lines[i].clone()
        } else {
            String::new()
        };
        println!("{logo_str} {padding} {info_str}");
    }
}

fn wide_to_normal(input: &str) -> String {
    input
        .chars()
        .map(|ch| match ch {
            '\u{FF01}'..='\u{FF5E}' => char::from_u32(ch as u32 - 0xFEE0).unwrap_or(ch),
            '\u{3000}' => ' ',
            _ => ch,
        })
        .collect()
}

fn colour(input: &str) -> String {
    fg(input, 72, 184, 200)
}

fn fg(input: &str, r: u8, g: u8, b: u8) -> String {
    format!("\x1b[38;2;{r};{g};{b}m{input}\x1b[0m")
}

fn bg(input: &str, fr: u8, fg_: u8, fb: u8, br: u8, bg_: u8, bb: u8) -> String {
    format!("\x1b[38;2;{fr};{fg_};{fb};48;2;{br};{bg_};{bb}m{input}\x1b[0m")
}

fn difficulty_string(diff: &str) -> String {
    match diff {
        "easy" => bg("Easy", 255, 255, 255, 69, 174, 255),
        "basic" => bg("Basic", 255, 255, 255, 111, 212, 61),
        "advanced" => bg("Advanced", 255, 255, 255, 248, 183, 9),
        "expert" => bg("Expert", 255, 255, 255, 255, 46, 66),
        "master" => bg("Master", 255, 255, 255, 171, 140, 233),
        "remaster" | "re:master" => bg("Re:Master", 255, 255, 255, 207, 114, 237),
        "utage" => bg("Utage", 255, 255, 255, 255, 68, 1),
        other => other.to_string(),
    }
}

fn rank_string(rank: &str) -> String {
    match rank {
        "SSS+" => {
            fg("S", 255, 200, 54)
                + &fg("S", 225, 38, 165)
                + &fg("S", 73, 64, 233)
                + &fg("+", 21, 203, 148)
        }
        "SSS" => fg("S", 255, 200, 54) + &fg("S", 232, 39, 148) + &fg("S", 18, 195, 144),
        "SS+" => bg("SS+", 248, 200, 75, 143, 71, 33),
        "SS" => bg("SS", 248, 200, 75, 143, 71, 33),
        "S+" => bg("S+", 248, 200, 75, 75, 82, 82),
        "S" => bg("S", 248, 200, 75, 75, 82, 82),
        "AAA" => fg("AAA", 23, 163, 255),
        "AA" => fg("AA", 23, 163, 255),
        "A" => fg("A", 23, 163, 255),
        other => other.to_string(),
    }
}

fn url_to_ascii(url: &str, size: u32) -> Result<String, MaifetchError> {
    let bytes = reqwest::blocking::get(url)?.error_for_status()?.bytes()?;
    let image = image::load_from_memory(&bytes)?.to_rgb8();
    let resized = image::imageops::resize(&image, size * 2, size, FilterType::Triangle);
    let ramp = ['@', '%', '#', '*', '+', '=', '-', ':', '.', ' '];
    let mut output = String::new();

    for y in 0..resized.height() {
        for x in 0..resized.width() {
            let [r, g, b] = resized.get_pixel(x, y).0;
            let luminance = (0.299 * r as f32 + 0.587 * g as f32 + 0.114 * b as f32) as u8;
            let idx = (luminance as usize * (ramp.len() - 1)) / 255;
            let ch = if ramp[idx] == ' ' { '#' } else { ramp[idx] };
            output.push_str(&fg(&ch.to_string(), r, g, b));
        }
        output.push('\n');
    }

    Ok(output)
}

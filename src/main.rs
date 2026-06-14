use std::{
    env,
    fs::File,
    io::{self, Read, Write},
    path::PathBuf,
    thread,
    time::{Duration, Instant},
};

use anyhow::{anyhow, Context, Result};
use clap::Parser;
use image::{imageops::FilterType, GenericImageView};
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};

const BASE_URL: &str = "https://maitea.app";

#[derive(Parser, Debug)]
#[command(name = "maifetch")]
struct Cli {
    #[arg(
        short = 'a',
        long = "access-token",
        help = "Access Token for the MaiTea account"
    )]
    access_token: Option<String>,

    #[arg(short = 'c', long = "config-file", help = "Config file to use")]
    config_file: Option<PathBuf>,

    #[arg(
        short = 's',
        long = "score-count",
        help = "Amount of recent scores to view (max 12)"
    )]
    score_count: Option<u32>,

    #[arg(
        short = 'l',
        long = "logo-size",
        help = "Size of the ASCII logo (<1 to disable)"
    )]
    logo_size: Option<i32>,
}

#[derive(Debug, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct FileConfig {
    access_token: Option<String>,
    logo_size: Option<i32>,
    score_count: Option<u32>,
}

#[derive(Debug)]
struct Config {
    access_token: String,
    logo_size: i32,
    score_count: u32,
}

#[derive(Debug, Deserialize)]
struct ApiList<T> {
    data: T,
}

#[derive(Debug, Deserialize)]
struct ImageInfo {
    png: String,
}

#[derive(Debug, Deserialize)]
struct Profile {
    id: i64,
    name: String,
    rating: i64,
    rating_highest: i64,
    level: i64,
    play_stats: PlayStats,
    options: ProfileOptions,
}

#[derive(Debug, Deserialize)]
struct PlayStats {
    total: i64,
}

#[derive(Debug, Deserialize)]
struct ProfileOptions {
    icon: ImageInfo,
}

#[derive(Debug, Deserialize)]
struct LocalizedText {
    en: String,
}

#[derive(Debug, Deserialize)]
struct TrackInfo {
    name: LocalizedText,
}

#[derive(Debug, Deserialize)]
struct DifficultyLevel {
    value: String,
}

#[derive(Debug, Deserialize)]
struct Play {
    achievement_formatted: String,
    score_formatted: String,
    rank: String,
    full_combo_label: Option<String>,
    difficulty_level: DifficultyLevel,
    song: TrackInfo,
}

fn main() {
    let args = normalize_legacy_args();
    let cli = Cli::parse_from(args);

    if let Err(err) = run(cli) {
        println!("{err}");
    }
}

fn normalize_legacy_args() -> Vec<String> {
    env::args()
        .map(|arg| if arg == "-t" { "-a".to_string() } else { arg })
        .collect()
}

fn run(cli: Cli) -> Result<()> {
    let config = load_config(cli)?;
    let api = MaiTeaClient::new(config.access_token);

    let profiles = api.get_profiles()?;
    let profile = profiles
        .first()
        .ok_or_else(|| anyhow!("No profiles found"))?;

    let plays = wait_for_plays(&api)?;
    output(&plays, profile, config.logo_size, config.score_count)
}

fn load_config(cli: Cli) -> Result<Config> {
    let mut merged = FileConfig {
        access_token: None,
        logo_size: Some(20),
        score_count: Some(4),
    };

    let config_path = cli
        .config_file
        .clone()
        .or_else(|| env_path("MAITEA_CONFIG_FILE"))
        .or_else(|| env_path("MAIFETCH_CONFIG_FILE"))
        .or_else(default_config_file);

    if let Some(path) = config_path {
        if let Ok(file) = File::open(&path) {
            let file_config: FileConfig = serde_json::from_reader(file)
                .with_context(|| format!("could not decode config file {}", path.display()))?;
            merge_file_config(&mut merged, file_config);
        }
    }

    merge_env_config(&mut merged);

    if let Some(access_token) = cli.access_token {
        merged.access_token = Some(access_token);
    }
    if let Some(logo_size) = cli.logo_size {
        merged.logo_size = Some(logo_size);
    }
    if let Some(score_count) = cli.score_count {
        merged.score_count = Some(score_count);
    }

    let access_token = merged
        .access_token
        .filter(|token| !token.is_empty())
        .ok_or_else(|| anyhow!("access token is required"))?;
    let score_count = merged.score_count.unwrap_or(4);

    if score_count > 12 {
        return Err(anyhow!("score count cannot be higher than 12"));
    }

    Ok(Config {
        access_token,
        logo_size: merged.logo_size.unwrap_or(20),
        score_count,
    })
}

fn default_config_file() -> Option<PathBuf> {
    dirs::config_dir().map(|path| path.join("maifetch.json"))
}

fn merge_file_config(target: &mut FileConfig, source: FileConfig) {
    if source.access_token.is_some() {
        target.access_token = source.access_token;
    }
    if source.logo_size.is_some() {
        target.logo_size = source.logo_size;
    }
    if source.score_count.is_some() {
        target.score_count = source.score_count;
    }
}

fn merge_env_config(config: &mut FileConfig) {
    if let Some(value) = env_string("MAITEA_TOKEN").or_else(|| env_string("MAIFETCH_TOKEN")) {
        config.access_token = Some(value);
    }
    if let Some(value) = env_string("MAITEA_LOGO_SIZE").or_else(|| env_string("MAIFETCH_LOGO_SIZE"))
    {
        if let Ok(parsed) = value.parse() {
            config.logo_size = Some(parsed);
        }
    }
    if let Some(value) =
        env_string("MAITEA_SCORE_COUNT").or_else(|| env_string("MAIFETCH_SCORE_COUNT"))
    {
        if let Ok(parsed) = value.parse() {
            config.score_count = Some(parsed);
        }
    }
}

fn env_path(name: &str) -> Option<PathBuf> {
    env::var_os(name).map(PathBuf::from)
}

fn env_string(name: &str) -> Option<String> {
    env::var(name).ok()
}

struct MaiTeaClient {
    access_token: String,
    client: Client,
}

impl MaiTeaClient {
    fn new(access_token: String) -> Self {
        Self {
            access_token,
            client: Client::new(),
        }
    }

    fn get<T>(&self, route: &str) -> Result<T>
    where
        T: for<'de> Deserialize<'de>,
    {
        let url = format!("{BASE_URL}{route}");
        let response = self
            .client
            .get(url)
            .bearer_auth(&self.access_token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .send()?
            .error_for_status()?;

        Ok(response.json()?)
    }

    fn get_profiles(&self) -> Result<Vec<Profile>> {
        let response: ApiList<Vec<Profile>> = self.get("/api/v1/profiles")?;
        Ok(response.data)
    }

    fn get_plays(&self) -> Result<Vec<Play>> {
        let response: ApiList<Vec<Play>> = self.get("/api/v1/plays")?;
        Ok(response.data)
    }
}

fn wait_for_plays(api: &MaiTeaClient) -> Result<Vec<Play>> {
    let (tx, rx) = std::sync::mpsc::channel();
    let api = MaiTeaClient {
        access_token: api.access_token.clone(),
        client: Client::new(),
    };

    thread::spawn(move || {
        let _ = tx.send(api.get_plays());
    });

    let started = Instant::now();
    let frames = ["|", "/", "-", "\\"];
    let mut frame_index = 0;

    while started.elapsed() < Duration::from_secs(30) {
        match rx.try_recv() {
            Ok(result) => {
                clear_spinner_line();
                return result;
            }
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                print!("\r{} Loading...", frames[frame_index % frames.len()]);
                let _ = io::stdout().flush();
                frame_index += 1;
                thread::sleep(Duration::from_millis(100));
            }
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                clear_spinner_line();
                return Err(anyhow!("API request failed"));
            }
        }
    }

    clear_spinner_line();
    Err(anyhow!("API timed out"))
}

fn clear_spinner_line() {
    print!("\r\x1b[2K");
    let _ = io::stdout().flush();
}

fn output(plays: &[Play], profile: &Profile, logo_size: i32, score_count: u32) -> Result<()> {
    let info_lines = create_info_strings(profile, plays, score_count);

    if logo_size > 0 {
        let logo = url_to_ascii(&profile.options.icon.png, logo_size as u32)?;
        let logo_lines: Vec<_> = logo.lines().map(str::to_string).collect();
        print_combined(&info_lines, &logo_lines, logo_size as usize);
    } else {
        println!("{}", info_lines.join("\n"));
    }

    Ok(())
}

fn create_info_strings(profile: &Profile, plays: &[Play], score_count: u32) -> Vec<String> {
    let name = wide_to_normal(&profile.name);
    let mut lines = vec![
        colour_fg(&name, 72, 184, 200),
        "-".repeat(name.chars().count()),
        format!("{}: {}", colour_fg("ID", 72, 184, 200), profile.id),
        format!(
            "{}: {:.2} / {:.2}",
            colour_fg("Rating", 72, 184, 200),
            profile.rating as f32 / 100.0,
            profile.rating_highest as f32 / 100.0
        ),
        format!("{}: {}", colour_fg("Level", 72, 184, 200), profile.level),
        format!(
            "{}: {}",
            colour_fg("Total Credits", 72, 184, 200),
            profile.play_stats.total
        ),
        format!("{}:", colour_fg("Recent Scores", 72, 184, 200)),
    ];

    for play in plays.iter().take(score_count as usize) {
        let fc_label = play.full_combo_label.as_deref().unwrap_or("");
        lines.push(format!(
            "  {}  {}",
            play.song.name.en,
            difficulty_string(&play.difficulty_level.value)
        ));
        lines.push(format!(
            "  {} {}% {} {}",
            play.score_formatted,
            play.achievement_formatted,
            rank_string(&play.rank),
            fc_label
        ));
        lines.push(String::new());
    }

    lines
}

fn print_combined(info_lines: &[String], logo_lines: &[String], logo_size: usize) {
    let max_length = info_lines.len().max(logo_lines.len());
    let empty_logo = " ".repeat(logo_size * 2);

    for i in 0..max_length {
        let logo = logo_lines
            .get(i)
            .map_or(empty_logo.as_str(), String::as_str);
        let info = info_lines.get(i).map_or("", String::as_str);
        println!("{logo}   {info}");
    }
}

fn wide_to_normal(input: &str) -> String {
    input
        .chars()
        .map(|chr| {
            if ('\u{ff01}'..='\u{ff5e}').contains(&chr) {
                char::from_u32(chr as u32 - 0xfee0).unwrap_or(chr)
            } else {
                chr
            }
        })
        .collect()
}

fn difficulty_string(diff: &str) -> String {
    match diff {
        "easy" => colour("Easy", 255, 255, 255, 69, 174, 255),
        "basic" => colour("Basic", 255, 255, 255, 111, 212, 61),
        "advanced" => colour("Advanced", 255, 255, 255, 248, 183, 9),
        "expert" => colour("Expert", 255, 255, 255, 255, 46, 66),
        "master" => colour("Master", 255, 255, 255, 171, 140, 233),
        "remaster" | "re:master" => colour("Re:Master", 255, 255, 255, 207, 114, 237),
        "utage" => colour("Utage", 255, 255, 255, 255, 68, 1),
        _ => diff.to_string(),
    }
}

fn rank_string(rank: &str) -> String {
    match rank {
        "SSS+" => format!(
            "{}{}{}{}",
            colour_fg("S", 255, 200, 54),
            colour_fg("S", 225, 38, 165),
            colour_fg("S", 73, 64, 233),
            colour_fg("+", 21, 203, 148)
        ),
        "SSS" => format!(
            "{}{}{}",
            colour_fg("S", 255, 200, 54),
            colour_fg("S", 232, 39, 148),
            colour_fg("S", 18, 195, 144)
        ),
        "SS+" => colour("SS+", 248, 200, 75, 143, 71, 33),
        "SS" => colour("SS", 248, 200, 75, 143, 71, 33),
        "S+" => colour("S+", 248, 200, 75, 75, 82, 82),
        "S" => colour("S", 248, 200, 75, 75, 82, 82),
        "AAA" => colour_fg("AAA", 23, 163, 255),
        "AA" => colour_fg("AA", 23, 163, 255),
        "A" => colour_fg("A", 23, 163, 255),
        _ => rank.to_string(),
    }
}

fn colour(text: &str, fr: u8, fg: u8, fb: u8, br: u8, bg: u8, bb: u8) -> String {
    format!("\x1b[38;2;{fr};{fg};{fb}m\x1b[48;2;{br};{bg};{bb}m{text}\x1b[0m")
}

fn colour_fg(text: &str, r: u8, g: u8, b: u8) -> String {
    format!("\x1b[38;2;{r};{g};{b}m{text}\x1b[0m")
}

fn url_to_ascii(url: &str, size: u32) -> Result<String> {
    let mut response = Client::new().get(url).send()?.error_for_status()?;
    let mut bytes = Vec::new();
    response.read_to_end(&mut bytes)?;

    let image = image::load_from_memory(&bytes)?;
    let resized = image.resize_exact(size * 2, size, FilterType::Nearest);
    let chars = [' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'];
    let mut output = String::new();

    for y in 0..resized.height() {
        for x in 0..resized.width() {
            let pixel = resized.get_pixel(x, y);
            let [r, g, b, alpha] = pixel.0;
            if alpha == 0 {
                output.push(' ');
                continue;
            }

            let brightness =
                (0.2126 * f32::from(r) + 0.7152 * f32::from(g) + 0.0722 * f32::from(b)) / 255.0;
            let index = (brightness * (chars.len() - 1) as f32).round() as usize;
            output.push_str(&colour_fg(&chars[index].to_string(), r, g, b));
        }
        output.push('\n');
    }

    Ok(output)
}

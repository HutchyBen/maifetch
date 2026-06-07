use clap::Parser;
use image::{imageops::FilterType, GenericImageView};
use maifetch::maitea::{accent, difficulty_string, rank_string, ApiClient, Play, Profile};
use serde::{Deserialize, Serialize};
use std::error::Error;
use std::fs::File;
use std::io;
use std::path::PathBuf;

#[derive(Debug, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct FileConfig {
    access_token: Option<String>,
    score_count: Option<u8>,
    logo_size: Option<i32>,
}

#[derive(Debug, Clone)]
struct Config {
    access_token: String,
    score_count: u8,
    logo_size: i32,
}

#[derive(Parser, Debug)]
#[command(name = "maifetch", about = "A lazy fetch tool for MaiTea")]
struct Cli {
    #[arg(short = 'a', long = "access-token", alias = "token")]
    access_token: Option<String>,

    #[arg(short = 't', hide = true)]
    token_short: Option<String>,

    #[arg(short = 'c', long = "config-file")]
    config_file: Option<PathBuf>,

    #[arg(short = 's', long = "score-count")]
    score_count: Option<u8>,

    #[arg(short = 'l', long = "logo-size")]
    logo_size: Option<i32>,
}

fn main() {
    if let Err(error) = run() {
        println!("{error}");
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let config = load_config()?;
    let client = ApiClient::new(config.access_token.clone())?;
    let profiles = client.get_profiles()?;

    if profiles.is_empty() {
        println!("No profiles found");
        return Ok(());
    }

    let plays = client.get_plays()?;
    output(
        &client,
        plays.current_page(),
        &profiles[0],
        config.logo_size,
        config.score_count,
    )?;

    Ok(())
}

fn load_config() -> Result<Config, Box<dyn Error>> {
    let cli = Cli::parse();
    let config_file = cli
        .config_file
        .clone()
        .or_else(|| env_first(&["MAITEA_CONFIG_FILE", "MAIFETCH_CONFIG_FILE"]).map(PathBuf::from))
        .or_else(default_config_path);

    let mut config = config_file
        .as_ref()
        .map(read_config_file)
        .transpose()?
        .unwrap_or_default();

    if let Some(token) = env_first(&["MAITEA_TOKEN", "MAIFETCH_TOKEN"]) {
        config.access_token = Some(token);
    }

    if let Some(value) = env_first(&["MAITEA_SCORE_COUNT", "MAIFETCH_SCORE_COUNT"]) {
        config.score_count = Some(value.parse()?);
    }

    if let Some(value) = env_first(&["MAITEA_LOGO_SIZE", "MAIFETCH_LOGO_SIZE"]) {
        config.logo_size = Some(value.parse()?);
    }

    if let Some(value) = cli.access_token.or(cli.token_short) {
        config.access_token = Some(value);
    }

    if let Some(value) = cli.score_count {
        config.score_count = Some(value);
    }

    if let Some(value) = cli.logo_size {
        config.logo_size = Some(value);
    }

    let access_token = config
        .access_token
        .filter(|token| !token.trim().is_empty())
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "access token is required"))?;
    let score_count = config.score_count.unwrap_or(4);
    let logo_size = config.logo_size.unwrap_or(20);

    if score_count > 12 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "score count cannot be higher than 12",
        )
        .into());
    }

    Ok(Config {
        access_token,
        score_count,
        logo_size,
    })
}

fn read_config_file(path: &PathBuf) -> Result<FileConfig, Box<dyn Error>> {
    match File::open(path) {
        Ok(file) => Ok(serde_json::from_reader(file)?),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(FileConfig::default()),
        Err(error) => Err(error.into()),
    }
}

fn env_first(names: &[&str]) -> Option<String> {
    names.iter().find_map(|name| std::env::var(name).ok())
}

fn default_config_path() -> Option<PathBuf> {
    dirs::config_dir().map(|path| path.join("maifetch.json"))
}

fn output(
    client: &ApiClient,
    plays: &[Play],
    profile: &Profile,
    logo_size: i32,
    score_count: u8,
) -> Result<(), Box<dyn Error>> {
    let info_lines = create_info_strings(profile, plays, score_count);

    if logo_size > 0 {
        let logo = url_to_ascii(client, &profile.options.icon.png, logo_size as u32)?;
        let logo_lines: Vec<String> = logo.lines().map(str::to_string).collect();
        print_combined(&info_lines, &logo_lines, logo_size);
    } else {
        println!("{}", info_lines.join("\n"));
    }

    Ok(())
}

fn create_info_strings(profile: &Profile, plays: &[Play], score_count: u8) -> Vec<String> {
    let name = wide_to_normal(&profile.name);
    let mut lines = vec![
        accent(&name),
        "-".repeat(name.chars().count()),
        format!("{}: {}", accent("ID"), profile.id),
        format!(
            "{}: {:.2} / {:.2}",
            accent("Rating"),
            profile.rating as f32 / 100.0,
            profile.rating_highest as f32 / 100.0
        ),
        format!("{}: {}", accent("Level"), profile.level),
        format!("{}: {}", accent("Total Credits"), profile.play_stats.total),
        format!("{}:", accent("Recent Scores")),
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

fn print_combined(info_lines: &[String], logo_lines: &[String], logo_size: i32) {
    let max_length = info_lines.len().max(logo_lines.len());
    let padding = "  ";
    let blank_logo = " ".repeat((logo_size * 2).max(0) as usize);

    for index in 0..max_length {
        let logo = if index < logo_lines.len() {
            logo_lines[index].as_str()
        } else {
            blank_logo.as_str()
        };
        let info = if index < info_lines.len() {
            info_lines[index].as_str()
        } else {
            ""
        };
        println!("{logo} {padding} {info}");
    }
}

fn wide_to_normal(text: &str) -> String {
    text.chars()
        .map(|ch| {
            let code = ch as u32;
            if (0xFF01..=0xFF5E).contains(&code) {
                char::from_u32(code - 0xFEE0).unwrap_or(ch)
            } else {
                ch
            }
        })
        .collect()
}

fn url_to_ascii(client: &ApiClient, url: &str, size: u32) -> Result<String, Box<dyn Error>> {
    let bytes = client.get_bytes(url)?;
    let image = image::load_from_memory(&bytes)?;
    let resized = image.resize_exact(size * 2, size, FilterType::Nearest);
    let mut out = String::new();

    for y in 0..resized.height() {
        for x in 0..resized.width() {
            let rgba = resized.get_pixel(x, y);
            let [r, g, b, a] = rgba.0;
            if a == 0 {
                out.push(' ');
            } else {
                out.push_str(&format!("\x1b[38;2;{};{};{}m#\x1b[0m", r, g, b));
            }
        }
        if y + 1 < resized.height() {
            out.push('\n');
        }
    }

    Ok(out)
}

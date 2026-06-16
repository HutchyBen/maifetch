use std::fs;
use std::path::PathBuf;
use std::process;

mod ast;
mod codegen;
mod parser;

#[derive(clap::Parser)]
#[command(name = "soup-lang", about = "Soup Language transpiler — a glorious incoherent soup that compiles to Lua")]
struct Cli {
    /// Input file (.soup)
    input: PathBuf,

    /// Output file (default: stdout)
    #[arg(short, long)]
    output: Option<PathBuf>,

    /// Print the Lua output
    #[arg(short, long)]
    print: bool,
}

fn main() {
    let cli = <Cli as clap::Parser>::parse();

    let source = match fs::read_to_string(&cli.input) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("Error reading {}: {}", cli.input.display(), e);
            process::exit(1);
        }
    };

    let program = match parser::parse_input(&source) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("{}", e);
            process::exit(1);
        }
    };

    let mut gen = codegen::Codegen::new();
    let output = gen.generate(&program);

    if let Some(ref out_path) = cli.output {
        if let Err(e) = fs::write(out_path, &output) {
            eprintln!("Error writing {}: {}", out_path.display(), e);
            process::exit(1);
        }
        eprintln!("Wrote {}", out_path.display());
    }

    if cli.print || cli.output.is_none() {
        print!("{}", output);
    }
}

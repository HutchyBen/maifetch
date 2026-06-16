use soup_lang::ast::*;
use soup_lang::parser::parse_input;

fn main() {
    let expr = r#"let name = (profile["name"] ?: "Unknown") |> wide_to_normal"#;
    let program = parse_input(expr).expect("Parse failed");
    println!("{:#?}", program);
}

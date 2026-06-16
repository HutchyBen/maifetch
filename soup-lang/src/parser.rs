use pest::Parser;

use crate::ast::*;

#[derive(pest_derive::Parser)]
#[grammar = "grammar.pest"]
pub struct AltLuaParser;

pub fn parse_input(source: &str) -> Result<Program, String> {
    let pairs = AltLuaParser::parse(Rule::program, source)
        .map_err(|e| format!("Parse error: {}", e))?;
    let pair = pairs.into_iter().next().unwrap();
    Ok(parse_program(pair))
}

fn parse_program(pair: pest::iterators::Pair<Rule>) -> Program {
    let mut stmts = Vec::new();
    for child in pair.into_inner() {
        match child.as_rule() {
            Rule::let_decl | Rule::const_decl | Rule::assignment
            | Rule::if_stmt | Rule::for_stmt | Rule::while_stmt | Rule::repeat_stmt
            | Rule::fn_decl | Rule::consteval_fn_decl | Rule::class_decl
            | Rule::return_stmt | Rule::pass_stmt | Rule::import_stmt | Rule::from_import_stmt
            | Rule::defer_stmt | Rule::errdefer_stmt | Rule::with_stmt
            | Rule::divide_stmt | Rule::perform_stmt
            | Rule::goto_stmt | Rule::label_stmt | Rule::asm_stmt | Rule::progn_stmt
            | Rule::expr_stmt => stmts.push(parse_stmt(child)),
            _ => {}
        }
    }
    Program { stmts }
}

fn parse_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    match pair.as_rule() {
        Rule::let_decl => parse_let_decl(pair),
        Rule::const_decl => parse_const_decl(pair),
        Rule::assignment => parse_assignment(pair),
        Rule::if_stmt => parse_if_stmt(pair),
        Rule::for_stmt => parse_for_stmt(pair),
        Rule::while_stmt => parse_while_stmt(pair),
        Rule::repeat_stmt => parse_repeat_stmt(pair),
        Rule::fn_decl => parse_fn_decl(pair),
        Rule::consteval_fn_decl => parse_consteval_fn_decl(pair),
        Rule::class_decl => parse_class_decl(pair),
        Rule::return_stmt => parse_return_stmt(pair),
        Rule::pass_stmt => Stmt::Pass,
        Rule::import_stmt => parse_import_stmt(pair),
        Rule::from_import_stmt => parse_from_import_stmt(pair),
        Rule::defer_stmt => parse_defer_stmt(pair),
        Rule::errdefer_stmt => parse_errdefer_stmt(pair),
        Rule::with_stmt => parse_with_stmt(pair),
        Rule::divide_stmt => parse_divide_stmt(pair),
        Rule::perform_stmt => parse_perform_stmt(pair),
        Rule::goto_stmt => parse_goto_stmt(pair),
        Rule::label_stmt => parse_label_stmt(pair),
        Rule::asm_stmt => parse_asm_stmt(pair),
        Rule::progn_stmt => parse_progn_stmt(pair),
        Rule::expr_stmt => {
            let expr = parse_expr(pair.into_inner().next().unwrap());
            Stmt::Expr(Box::new(expr))
        }
        _ => panic!("unexpected statement rule: {:?}", pair.as_rule()),
    }
}

fn parse_let_decl(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let next = inner.next().unwrap();
    match next.as_rule() {
        Rule::expr => Stmt::Let(name, Box::new(parse_expr(next))),
        _ => {
            // skip type annotation (ident), get the value
            let value = parse_expr(inner.next().unwrap());
            Stmt::Let(name, Box::new(value))
        }
    }
}

fn parse_const_decl(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let next = inner.next().unwrap();
    match next.as_rule() {
        Rule::expr => Stmt::Const(name, Box::new(parse_expr(next))),
        _ => {
            // skip type annotation (ident), get the value
            let value = parse_expr(inner.next().unwrap());
            Stmt::Const(name, Box::new(value))
        }
    }
}

fn parse_assignment(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let target = parse_lvalue(inner.next().unwrap());
    let value = parse_expr(inner.next().unwrap());
    Stmt::Assign(Box::new(target), Box::new(value))
}

fn parse_lvalue(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut expr = Expr::Ident(first.as_str().to_string());
    for suffix in inner {
        match suffix.as_rule() {
            Rule::index_expr => {
                let index = parse_expr(suffix.into_inner().next().unwrap());
                expr = Expr::Index(Box::new(expr), Box::new(index));
            }
            Rule::member_expr => {
                let name = suffix.into_inner().next().unwrap().as_str().to_string();
                expr = Expr::Member(Box::new(expr), name);
            }
            Rule::safe_member_expr => {
                let name = suffix.into_inner().next().unwrap().as_str().to_string();
                expr = Expr::SafeMember(Box::new(expr), name);
            }
            _ => panic!("unexpected lvalue suffix: {:?}", suffix.as_rule()),
        }
    }
    expr
}

fn parse_if_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let cond = parse_expr(inner.next().unwrap());
    let body = parse_block(inner.next().unwrap());
    let mut elifs = Vec::new();
    let mut else_body = None;
    for child in inner {
        match child.as_rule() {
            Rule::elif_clause => {
                let mut e = child.into_inner();
                let econd = parse_expr(e.next().unwrap());
                let ebody = parse_block(e.next().unwrap());
                elifs.push((Box::new(econd), ebody));
            }
            Rule::else_clause => {
                let mut e = child.into_inner();
                else_body = Some(parse_block(e.next().unwrap()));
            }
            _ => {}
        }
    }
    Stmt::If(Box::new(cond), body, elifs, else_body)
}

fn parse_for_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let iter = parse_expr(inner.next().unwrap());
    let body = parse_block(inner.next().unwrap());
    Stmt::For(name, Box::new(iter), body)
}

fn parse_while_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let cond = parse_expr(inner.next().unwrap());
    let body = parse_block(inner.next().unwrap());
    Stmt::While(Box::new(cond), body)
}

fn parse_repeat_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let body = parse_block(inner.next().unwrap());
    let cond = parse_expr(inner.next().unwrap());
    Stmt::Repeat(Box::new(cond), body)
}

fn parse_fn_decl(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let mut params = Vec::new();
    let next = inner.next().unwrap();
    match next.as_rule() {
        Rule::param_list => {
            parse_param_list_inner(next, &mut params);
            let next_child = inner.next().unwrap();
            match next_child.as_rule() {
                Rule::ident => {
                    // Return type annotation — skip it
                    let body = parse_block(inner.next().unwrap());
                    Stmt::Fn(name, params, body)
                }
                Rule::block => {
                    Stmt::Fn(name, params, parse_block(next_child))
                }
                _ => panic!("unexpected fn_decl child after params: {:?}", next_child.as_rule()),
            }
        }
        Rule::block => Stmt::Fn(name, params, parse_block(next)),
        Rule::ident => {
            let _ret_type = next;
            let body = parse_block(inner.next().unwrap());
            Stmt::Fn(name, params, body)
        }
        _ => panic!("unexpected fn_decl child: {:?}", next.as_rule()),
    }
}

fn parse_consteval_fn_decl(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let mut params = Vec::new();
    let next = inner.next().unwrap();
    match next.as_rule() {
        Rule::param_list => {
            parse_param_list_inner(next, &mut params);
            let body = parse_block(inner.next().unwrap());
            Stmt::ConstEvalFn(name, params, body)
        }
        Rule::block => Stmt::ConstEvalFn(name, params, parse_block(next)),
        _ => panic!("unexpected consteval_fn_decl child: {:?}", next.as_rule()),
    }
}

fn parse_param_list_inner(pair: pest::iterators::Pair<Rule>, params: &mut Vec<String>) {
    for p in pair.into_inner() {
        // p is a param_item: ident(name) [, ident(type)]
        if let Some(name) = p.into_inner().next() {
            params.push(name.as_str().to_string());
        }
    }
}

fn parse_class_decl(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let mut parent = None;
    let next = inner.next().unwrap();
    match next.as_rule() {
        Rule::block => Stmt::Class(name, parent, parse_block(next)),
        _ => {
            parent = Some(next.as_str().to_string());
            let body = parse_block(inner.next().unwrap());
            Stmt::Class(name, parent, body)
        }
    }
}

fn parse_return_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    if let Some(expr_pair) = inner.next() {
        Stmt::Return(Some(Box::new(parse_expr(expr_pair))))
    } else {
        Stmt::Return(None)
    }
}

fn parse_import_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let inner = pair.into_inner().next().unwrap();
    let path = parse_string_lit(inner);
    Stmt::Import(path)
}

fn parse_from_import_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let path = parse_string_lit(inner.next().unwrap());
    let mut names = Vec::new();
    for name in inner {
        names.push(name.as_str().to_string());
    }
    Stmt::FromImport(path, names)
}

fn parse_defer_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let expr = parse_expr(pair.into_inner().next().unwrap());
    Stmt::Defer(Box::new(expr))
}

fn parse_errdefer_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let expr = parse_expr(pair.into_inner().next().unwrap());
    Stmt::ErrDefer(Box::new(expr))
}

fn parse_with_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let mut clauses = Vec::new();
    loop {
        match inner.peek().map(|p| p.as_rule()) {
            Some(Rule::with_clause) => {
                let p = inner.next().unwrap();
                let mut pi = p.into_inner();
                let name = pi.next().unwrap().as_str().to_string();
                let expr = parse_expr(pi.next().unwrap());
                clauses.push((name, expr));
            }
            Some(Rule::comma) => {
                inner.next().unwrap();
            }
            _ => break,
        }
    }
    let mut else_body = None;
    if let Some(rule) = inner.peek().map(|p| p.as_rule()) {
        if rule == Rule::else_clause {
            inner.next().unwrap();
            else_body = Some(parse_block(inner.next().unwrap()));
        }
    }
    let body = parse_block(inner.next().unwrap());
    Stmt::With(clauses, body, else_body)
}

fn parse_divide_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let divisor = parse_expr(inner.next().unwrap());
    let target = parse_lvalue(inner.next().unwrap());
    Stmt::Divide(Box::new(divisor), Box::new(target))
}

fn parse_perform_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let inner = pair.into_inner().next().unwrap();
    match inner.as_rule() {
        Rule::block => Stmt::PerformBlock(parse_block(inner)),
        Rule::ident => Stmt::Perform(inner.as_str().to_string()),
        Rule::integer_lit => Stmt::Perform(inner.as_str().to_string()),
        _ => panic!("unexpected perform child: {:?}", inner.as_rule()),
    }
}

fn parse_goto_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let target = pair.into_inner().next().unwrap().as_str().to_string();
    Stmt::Goto(target)
}

fn parse_label_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let mut inner = pair.into_inner();
    let label = inner.next().unwrap().as_str().to_string();
    let body = parse_block(inner.next().unwrap());
    Stmt::Label(label, body)
}

fn parse_asm_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let inner = pair.into_inner().next().unwrap();
    let asm_text = inner.as_str().trim().to_string();
    Stmt::Asm(asm_text)
}

fn parse_progn_stmt(pair: pest::iterators::Pair<Rule>) -> Stmt {
    let body = parse_block(pair.into_inner().next().unwrap());
    Stmt::Progn(body)
}

fn parse_block(pair: pest::iterators::Pair<Rule>) -> Vec<Stmt> {
    let mut stmts = Vec::new();
    for child in pair.into_inner() {
        stmts.push(parse_stmt(child));
    }
    stmts
}

fn parse_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    parse_elvis_expr(pair.into_inner().next().unwrap())
}

fn parse_elvis_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_pipe_expr(first);
    if let Some(tail) = inner.next() {
        let rhs = parse_pipe_expr(tail.into_inner().next().unwrap());
        result = Expr::Elvis(Box::new(result), Box::new(rhs));
    }
    result
}

fn parse_pipe_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_or_expr(first);
    for tail in inner {
        let rhs = parse_or_expr(tail.into_inner().next().unwrap());
        result = Expr::Pipe(Box::new(result), Box::new(rhs));
    }
    result
}

fn parse_or_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_and_expr(first);
    for tail in inner {
        let mut t = tail.into_inner();
        let rhs = parse_and_expr(t.next().unwrap());
        result = Expr::BinOp(Box::new(result), BinOp::Or, Box::new(rhs));
    }
    result
}

fn parse_and_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_comp_expr(first);
    for tail in inner {
        let mut t = tail.into_inner();
        let rhs = parse_comp_expr(t.next().unwrap());
        result = Expr::BinOp(Box::new(result), BinOp::And, Box::new(rhs));
    }
    result
}

fn parse_comp_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let full_text = pair.as_str().to_string();
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let first_text = first.as_str().to_string();
    let mut result = parse_concat_expr(first);
    for tail in inner {
        if tail.as_rule() == Rule::comp_tail {
            let mut t = tail.into_inner();
            let op_pair = t.next().unwrap();
            let rhs = parse_concat_expr(t.next().unwrap());
            let op = match op_pair.as_str() {
                "==" => BinOp::Eq,
                "!=" => BinOp::Neq,
                "<=" => BinOp::Le,
                ">=" => BinOp::Ge,
                "<" => BinOp::Lt,
                ">" => BinOp::Gt,
                "in" => BinOp::In,
                "is" => BinOp::Is,
                s => panic!("unknown comp op: {}", s),
            };
            result = Expr::BinOp(Box::new(result), op, Box::new(rhs));
        } else if tail.as_rule() == Rule::concat_tail {
            let mut t = tail.into_inner();
            let _op = t.next();
            if let Some(rhs) = t.next() {
                result = Expr::BinOp(Box::new(result), BinOp::Concat, Box::new(parse_add_expr(rhs)));
            }
        } else {
            let trimmed = full_text[first_text.len()..].trim_start();
            let op_len = if trimmed.starts_with("..") || trimmed.starts_with("==") || trimmed.starts_with("!=") || trimmed.starts_with("<=") || trimmed.starts_with(">=") { 2 }
                else if trimmed.starts_with("in") || trimmed.starts_with("is") { 2 }
                else if trimmed.starts_with("<") || trimmed.starts_with(">") { 1 }
                else { panic!("unknown op at '{}'", trimmed) };
            let op_str = &trimmed[..op_len];
            let op = match op_str {
                ".." => BinOp::Concat,
                "==" => BinOp::Eq,
                "!=" => BinOp::Neq,
                "<=" => BinOp::Le,
                ">=" => BinOp::Ge,
                "<" => BinOp::Lt,
                ">" => BinOp::Gt,
                "in" => BinOp::In,
                "is" => BinOp::Is,
                s => panic!("unknown comp op: {}", s),
            };
            result = Expr::BinOp(Box::new(result), op, Box::new(parse_any_expr(tail)));
        }
    }
    result
}

fn parse_concat_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    // clone the pair for fallback if into_inner() yields nothing
    let fallback_pair = pair.clone();
    let mut inner = pair.into_inner();
    let first = match inner.next() {
        Some(p) => p,
        None => return parse_any_expr(fallback_pair),
    };
    let mut result = parse_add_expr(first);
    for tail in inner {
        if tail.as_rule() == Rule::concat_tail {
            let mut t = tail.into_inner();
            let _op = t.next().unwrap();
            if let Some(rhs) = t.next() {
                result = Expr::BinOp(Box::new(result), BinOp::Concat, Box::new(parse_add_expr(rhs)));
            }
        }
    }
    result
}

fn parse_any_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    match pair.as_rule() {
        Rule::string_lit => Expr::String(parse_string_lit(pair)),
        Rule::number_lit => {
            let s = pair.as_str();
            Expr::Number(s.parse().expect("invalid number literal"))
        }
        Rule::bool_lit => Expr::Bool(pair.as_str() == "True"),
        Rule::none_lit => Expr::None,
        Rule::ident => Expr::Ident(pair.as_str().to_string()),
        _ => {
            let text = pair.as_str().to_string();
            let rule = format!("{:?}", pair.as_rule());
            let mut inner = pair.into_inner();
            if let Some(p) = inner.next() {
                parse_any_expr(p)
            } else {
                panic!("cannot parse expression: rule={} text='{}'", rule, text)
            }
        }
    }
}

fn parse_add_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_mul_expr(first);
    for tail in inner {
        let mut t = tail.into_inner();
        let op_pair = t.next().unwrap();
        let rhs = parse_mul_expr(t.next().unwrap());
        let op = match op_pair.as_str() {
            "+" => BinOp::Add,
            "-" => BinOp::Sub,
            s => panic!("unknown add op: {}", s),
        };
        result = Expr::BinOp(Box::new(result), op, Box::new(rhs));
    }
    result
}

fn parse_mul_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_unary_expr(first);
    for tail in inner {
        let mut t = tail.into_inner();
        let op_pair = t.next().unwrap();
        let rhs = parse_unary_expr(t.next().unwrap());
        let op = match op_pair.as_str() {
            "*" => BinOp::Mul,
            "/" => BinOp::Div,
            "//" => BinOp::IntDiv,
            "%" => BinOp::Mod,
            s => panic!("unknown mul op: {}", s),
        };
        result = Expr::BinOp(Box::new(result), op, Box::new(rhs));
    }
    result
}

fn parse_unary_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    match first.as_rule() {
        Rule::unary_op => {
            let op = match first.as_str() {
                "-" => UnaryOp::Neg,
                "not" => UnaryOp::Not,
                s => panic!("unknown unary op: {}", s),
            };
            let expr = parse_unary_expr(inner.next().unwrap());
            Expr::UnaryOp(op, Box::new(expr))
        }
        Rule::power_expr => parse_power_expr(first),
        Rule::call_expr => {
            let mut expr = parse_call_expr(first);
            for tail in inner {
                let mut t = tail.into_inner();
                let rhs = parse_call_expr(t.next().unwrap());
                expr = Expr::BinOp(Box::new(expr), BinOp::Pow, Box::new(rhs));
            }
            expr
        }
        _ => panic!("unexpected unary_expr child: {:?}", first.as_rule()),
    }
}

fn parse_power_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut result = parse_call_expr(first);
    for tail in inner {
        let mut t = tail.into_inner();
        let rhs = parse_call_expr(t.next().unwrap());
        result = Expr::BinOp(Box::new(result), BinOp::Pow, Box::new(rhs));
    }
    result
}

fn parse_call_expr(pair: pest::iterators::Pair<Rule>) -> Expr {
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let mut expr = parse_atom(first);
    for op in inner {
        match op.as_rule() {
            Rule::call_args => {
                let args = op.into_inner()
                    .next()
                    .map(|al| al.into_inner().map(|a| parse_expr(a)).collect())
                    .unwrap_or_default();
                expr = Expr::Call(Box::new(expr), args);
            }
            Rule::index_expr => {
                let index = parse_expr(op.into_inner().next().unwrap());
                expr = Expr::Index(Box::new(expr), Box::new(index));
            }
            Rule::member_expr => {
                let name = op.into_inner().next().unwrap().as_str().to_string();
                expr = Expr::Member(Box::new(expr), name);
            }
            Rule::safe_member_expr => {
                let name = op.into_inner().next().unwrap().as_str().to_string();
                expr = Expr::SafeMember(Box::new(expr), name);
            }
            _ => panic!("unexpected postfix op: {:?}", op.as_rule()),
        }
    }
    expr
}

fn parse_atom(pair: pest::iterators::Pair<Rule>) -> Expr {
    match pair.as_rule() {
        Rule::number_lit => {
            let s = pair.as_str();
            let val: f64 = s.parse().expect("invalid number literal");
            Expr::Number(val)
        }
        Rule::string_lit => {
            Expr::String(parse_string_lit(pair))
        }
        Rule::bool_lit => {
            Expr::Bool(pair.as_str() == "True")
        }
        Rule::none_lit => Expr::None,
        Rule::list_lit => {
            let exprs: Vec<Expr> = pair.into_inner().map(|e| parse_expr(e)).collect();
            Expr::List(exprs)
        }
        Rule::dict_lit => {
            let pairs: Vec<(Expr, Expr)> = pair
                .into_inner()
                .map(|p| {
                    let mut inner = p.into_inner();
                    let key = parse_expr(inner.next().unwrap());
                    let val = parse_expr(inner.next().unwrap());
                    (key, val)
                })
                .collect();
            Expr::Dict(pairs)
        }
        Rule::lambda => {
            let mut inner: Vec<_> = pair.into_inner().collect();
            let body = inner.pop().unwrap();
            let params: Vec<String> = inner.iter().map(|p| p.as_str().to_string()).collect();
            Expr::Lambda(params, Box::new(parse_expr(body)))
        }
        Rule::sexpr => {
            let mut inner = pair.into_inner();
            let name = inner.next().unwrap().as_str().to_string();
            let args: Vec<Expr> = inner.map(|e| parse_expr(e)).collect();
            Expr::SExpr(name, args)
        }
        Rule::ident => Expr::Ident(pair.as_str().to_string()),
        Rule::paren_expr => parse_expr(pair.into_inner().next().unwrap()),
        Rule::expr => parse_expr(pair),
        _ => panic!("unexpected atom rule: {:?}", pair.as_rule()),
    }
}

fn parse_string_lit(pair: pest::iterators::Pair<Rule>) -> String {
    let raw = pair.as_str();
    let inner = &raw[1..raw.len() - 1];
    let mut result = String::with_capacity(inner.len());
    let mut chars = inner.chars();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next() {
                Some('n') => result.push('\n'),
                Some('t') => result.push('\t'),
                Some('r') => result.push('\r'),
                Some('"') => result.push('"'),
                Some('\\') => result.push('\\'),
                Some(c) => {
                    result.push('\\');
                    result.push(c);
                }
                None => result.push('\\'),
            }
        } else {
            result.push(c);
        }
    }
    result
}

use std::collections::HashMap;

use crate::ast::*;

pub struct Codegen {
    indent_level: usize,
    class_name: Option<String>,
    needs_contains: bool,
    defer_stack: Vec<Vec<String>>,
    consteval_fns: HashMap<String, (Vec<String>, Vec<Stmt>)>,
    labels_seen: Vec<String>,
}

impl Codegen {
    pub fn new() -> Self {
        Codegen {
            indent_level: 0,
            class_name: None,
            needs_contains: false,
            defer_stack: Vec::new(),
            consteval_fns: HashMap::new(),
            labels_seen: Vec::new(),
        }
    }

    pub fn generate(&mut self, program: &Program) -> String {
        self.needs_contains = false;
        self.detect_needs(&program.stmts);
        self.collect_consteval_fns(&program.stmts);
        self.collect_labels(&program.stmts);

        let mut out = String::new();

        if self.needs_contains {
            out.push_str("local function __contains(tbl, val)\n");
            out.push_str("    for _, v in pairs(tbl) do\n");
            out.push_str("        if v == val then\n");
            out.push_str("            return true\n");
            out.push_str("        end\n");
            out.push_str("    end\n");
            out.push_str("    return false\n");
            out.push_str("end\n\n");
        }

        // Emit PERFORM target functions from labels
        for label in &self.labels_seen {
            out.push_str(&format!("local function __perf_{}() end\n", label));
        }

        self.defer_stack.push(Vec::new());
        for stmt in &program.stmts {
            self.emit_stmt(stmt, &mut out);
        }
        self.flush_defers(&mut out);

        out
    }

    fn collect_consteval_fns(&mut self, stmts: &[Stmt]) {
        for stmt in stmts {
            if let Stmt::ConstEvalFn(name, params, body) = stmt {
                self.consteval_fns
                    .insert(name.clone(), (params.clone(), body.clone()));
            }
            for s in stmt.children() {
                self.collect_consteval_fns(s);
            }
        }
    }

    fn collect_labels(&mut self, stmts: &[Stmt]) {
        for stmt in stmts {
            if let Stmt::Label(label, _) = stmt {
                self.labels_seen.push(label.clone());
            }
            for s in stmt.children() {
                self.collect_labels(s);
            }
        }
    }

    fn detect_needs(&mut self, stmts: &[Stmt]) {
        for stmt in stmts {
            match stmt {
                Stmt::Expr(e) | Stmt::Return(Some(e)) => self.detect_expr_needs(e),
                Stmt::Let(_, e) | Stmt::Const(_, e) => self.detect_expr_needs(e),
                Stmt::Assign(_, e) => self.detect_expr_needs(e),
                Stmt::Defer(e) | Stmt::ErrDefer(e) => self.detect_expr_needs(e),
                Stmt::If(c, body, elifs, else_body) => {
                    self.detect_expr_needs(c);
                    self.detect_needs(body);
                    for (ec, eb) in elifs {
                        self.detect_expr_needs(ec);
                        self.detect_needs(eb);
                    }
                    if let Some(eb) = else_body {
                        self.detect_needs(eb);
                    }
                }
                Stmt::For(_, e, body) | Stmt::While(e, body) => {
                    self.detect_expr_needs(e);
                    self.detect_needs(body);
                }
                Stmt::Repeat(e, body) => {
                    self.detect_expr_needs(e);
                    self.detect_needs(body);
                }
                Stmt::Fn(_, _, body)
                | Stmt::Class(_, _, body)
                | Stmt::ConstEvalFn(_, _, body)
                | Stmt::Progn(body)
                | Stmt::PerformBlock(body) => self.detect_needs(body),
                Stmt::With(clauses, body, else_body) => {
                    for (_, e) in clauses {
                        self.detect_expr_needs(e);
                    }
                    self.detect_needs(body);
                    if let Some(eb) = else_body {
                        self.detect_needs(eb);
                    }
                }
                Stmt::Divide(d, _) => self.detect_expr_needs(d),
                _ => {}
            }
        }
    }

    fn detect_expr_needs(&mut self, expr: &Expr) {
        match expr {
            Expr::BinOp(lhs, op, rhs) => {
                if matches!(op, BinOp::In) {
                    self.needs_contains = true;
                }
                self.detect_expr_needs(lhs);
                self.detect_expr_needs(rhs);
            }
            Expr::UnaryOp(_, e) => self.detect_expr_needs(e),
            Expr::List(items) => {
                for item in items {
                    self.detect_expr_needs(item);
                }
            }
            Expr::Dict(pairs) => {
                for (k, v) in pairs {
                    self.detect_expr_needs(k);
                    self.detect_expr_needs(v);
                }
            }
            Expr::Call(f, args) => {
                self.detect_expr_needs(f);
                for a in args {
                    self.detect_expr_needs(a);
                }
            }
            Expr::Index(obj, idx) => {
                self.detect_expr_needs(obj);
                self.detect_expr_needs(idx);
            }
            Expr::Member(obj, _) | Expr::SafeMember(obj, _) => {
                self.detect_expr_needs(obj);
            }
            Expr::Lambda(_, body) => self.detect_expr_needs(body),
            Expr::Pipe(lhs, rhs) => {
                self.detect_expr_needs(lhs);
                self.detect_expr_needs(rhs);
            }
            Expr::Elvis(lhs, rhs) => {
                self.detect_expr_needs(lhs);
                self.detect_expr_needs(rhs);
            }
            Expr::SExpr(_, args) => {
                for a in args {
                    self.detect_expr_needs(a);
                }
            }
            _ => {}
        }
    }

    fn indent(&self) -> String {
        "    ".repeat(self.indent_level)
    }

    fn emit_block(&mut self, stmts: &[Stmt], out: &mut String) {
        self.defer_stack.push(Vec::new());
        for s in stmts {
            self.emit_stmt(s, out);
        }
        self.flush_defers(out);
    }

    fn emit_defers_before_return(&mut self, out: &mut String) {
        if let Some(stack) = self.defer_stack.last_mut() {
            let defers = std::mem::take(stack);
            for d in defers.into_iter().rev() {
                out.push_str(&self.indent());
                out.push_str(&d);
                out.push('\n');
            }
        }
    }

    fn flush_defers(&mut self, out: &mut String) {
        if let Some(defers) = self.defer_stack.pop() {
            for d in defers.into_iter().rev() {
                out.push_str(&self.indent());
                out.push_str(&d);
                out.push('\n');
            }
        }
    }

    fn emit_stmt(&mut self, stmt: &Stmt, out: &mut String) {
        match stmt {
            Stmt::Let(name, expr) => {
                out.push_str(&self.indent());
                out.push_str("local ");
                out.push_str(name);
                out.push_str(" = ");
                self.emit_expr(expr, out);
                out.push('\n');
            }
            Stmt::Const(name, expr) => {
                out.push_str(&self.indent());
                out.push_str("local ");
                out.push_str(name);
                out.push_str(" = ");
                self.emit_expr(expr, out);
                out.push('\n');
            }
            Stmt::Assign(target, value) => {
                out.push_str(&self.indent());
                self.emit_expr(target, out);
                out.push_str(" = ");
                self.emit_expr(value, out);
                out.push('\n');
            }
            Stmt::If(cond, body, elifs, else_body) => {
                out.push_str(&self.indent());
                out.push_str("if ");
                self.emit_expr(cond, out);
                out.push_str(" then\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;

                for (econd, ebody) in elifs {
                    out.push_str(&self.indent());
                    out.push_str("elseif ");
                    self.emit_expr(econd, out);
                    out.push_str(" then\n");
                    self.indent_level += 1;
                    self.emit_block(ebody, out);
                    self.indent_level -= 1;
                }

                if let Some(ebody) = else_body {
                    out.push_str(&self.indent());
                    out.push_str("else\n");
                    self.indent_level += 1;
                    self.emit_block(ebody, out);
                    self.indent_level -= 1;
                }

                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::For(name, iter, body) => {
                out.push_str(&self.indent());
                out.push_str("for _, ");
                out.push_str(name);
                out.push_str(" in pairs(");
                self.emit_expr(iter, out);
                out.push_str(") do\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::While(cond, body) => {
                out.push_str(&self.indent());
                out.push_str("while ");
                self.emit_expr(cond, out);
                out.push_str(" do\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::Repeat(cond, body) => {
                out.push_str(&self.indent());
                out.push_str("while true do\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                out.push_str(&self.indent());
                out.push_str("if ");
                self.emit_expr(cond, out);
                out.push_str(" then break end\n");
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::Fn(name, params, body) => {
                let (prefix, is_local) = if let Some(ref cn) = self.class_name {
                    (format!("{}.", cn), false)
                } else {
                    (String::new(), true)
                };
                out.push_str(&self.indent());
                if is_local {
                    out.push_str("local ");
                }
                out.push_str("function ");
                out.push_str(&prefix);
                out.push_str(name);
                out.push('(');
                for (i, p) in params.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    out.push_str(p);
                }
                out.push_str(")\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::ConstEvalFn(name, params, body) => {
                let (prefix, is_local) = if let Some(ref cn) = self.class_name {
                    (format!("{}.", cn), false)
                } else {
                    (String::new(), true)
                };
                out.push_str(&self.indent());
                if is_local {
                    out.push_str("local ");
                }
                out.push_str("function ");
                out.push_str(&prefix);
                out.push_str(name);
                out.push('(');
                for (i, p) in params.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    out.push_str(p);
                }
                out.push_str(")\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::Class(name, parent, body) => {
                out.push_str(&self.indent());
                out.push_str("local ");
                out.push_str(name);
                out.push_str(" = {}\n");
                out.push_str(&self.indent());
                out.push_str(name);
                out.push_str(".__index = ");
                out.push_str(name);
                out.push('\n');

                if let Some(ref p) = parent {
                    out.push_str(&self.indent());
                    out.push_str("setmetatable(");
                    out.push_str(name);
                    out.push_str(", {__index = ");
                    out.push_str(p);
                    out.push_str("})\n");
                }

                let old = self.class_name.take();
                self.class_name = Some(name.clone());
                for s in body {
                    self.emit_stmt(s, out);
                }
                self.class_name = old;
            }
            Stmt::Return(Some(expr)) => {
                self.emit_defers_before_return(out);
                out.push_str(&self.indent());
                out.push_str("return ");
                self.emit_expr(expr, out);
                out.push('\n');
            }
            Stmt::Return(None) => {
                self.emit_defers_before_return(out);
                out.push_str(&self.indent());
                out.push_str("return\n");
            }
            Stmt::Pass => {
                out.push_str(&self.indent());
                out.push_str("-- pass\n");
            }
            Stmt::Import(path) => {
                let varname = path
                    .rsplit('/')
                    .next()
                    .unwrap_or(path)
                    .trim_end_matches(".soup")
                    .trim_end_matches(".soup")
                    .trim_end_matches(".lua");
                out.push_str(&self.indent());
                out.push_str("local ");
                out.push_str(varname);
                out.push_str(" = require(");
                emit_lua_string(path, out);
                out.push_str(")\n");
            }
            Stmt::FromImport(path, names) => {
                let mod_var = format!("__mod_{}", path.replace('/', "_").replace('.', "_"));
                out.push_str(&self.indent());
                out.push_str("local ");
                out.push_str(&mod_var);
                out.push_str(" = require(");
                emit_lua_string(path, out);
                out.push_str(")\n");
                for name in names {
                    out.push_str(&self.indent());
                    out.push_str("local ");
                    out.push_str(name);
                    out.push_str(" = ");
                    out.push_str(&mod_var);
                    out.push_str(".");
                    out.push_str(name);
                    out.push('\n');
                }
            }
            Stmt::Defer(expr) => {
                let mut code = String::new();
                self.emit_expr(expr, &mut code);
                if let Some(stack) = self.defer_stack.last_mut() {
                    stack.push(code);
                }
            }
            Stmt::ErrDefer(expr) => {
                let mut code = String::new();
                self.emit_expr(expr, &mut code);
                if let Some(stack) = self.defer_stack.last_mut() {
                    stack.push(code);
                }
            }
            Stmt::With(clauses, body, else_body) => {
                let else_label = format!("__with_else_{}", self.gen_uid());
                let end_label = format!("__with_end_{}", self.gen_uid());

                if let Some(ebody) = else_body {
                    for (i, (name, expr)) in clauses.iter().enumerate() {
                        if i == 0 {
                            out.push_str(&self.indent());
                            out.push_str("local ");
                            out.push_str(name);
                            out.push_str(" = ");
                            self.emit_expr(expr, out);
                            out.push('\n');
                            out.push_str(&self.indent());
                            out.push_str("if not ");
                            out.push_str(name);
                            out.push_str(" then goto ");
                            out.push_str(&else_label);
                            out.push_str(" end\n");
                        } else {
                            out.push_str(&self.indent());
                            out.push_str("local ");
                            out.push_str(name);
                            out.push_str(" = ");
                            self.emit_expr(expr, out);
                            out.push('\n');
                            out.push_str(&self.indent());
                            out.push_str("if not ");
                            out.push_str(name);
                            out.push_str(" then goto ");
                            out.push_str(&else_label);
                            out.push_str(" end\n");
                        }
                    }
                    out.push_str(&self.indent());
                    out.push_str("do\n");
                    self.indent_level += 1;
                    self.emit_block(body, out);
                    self.indent_level -= 1;
                    out.push_str(&self.indent());
                    out.push_str("end\n");
                    out.push_str(&self.indent());
                    out.push_str("goto ");
                    out.push_str(&end_label);
                    out.push('\n');
                    out.push_str(&self.indent());
                    out.push_str("::");
                    out.push_str(&else_label);
                    out.push_str("::\n");
                    self.indent_level += 1;
                    self.emit_block(ebody, out);
                    self.indent_level -= 1;
                    out.push_str(&self.indent());
                    out.push_str("::");
                    out.push_str(&end_label);
                    out.push_str("::\n");
                } else {
                    for (name, expr) in clauses.iter() {
                        out.push_str(&self.indent());
                        out.push_str("local ");
                        out.push_str(name);
                        out.push_str(" = ");
                        self.emit_expr(expr, out);
                        out.push('\n');
                    }
                    let all_names: Vec<&str> = clauses.iter().map(|(n, _)| n.as_str()).collect();
                    out.push_str(&self.indent());
                    out.push_str("if ");
                    for (i, name) in all_names.iter().enumerate() {
                        if i > 0 {
                            out.push_str(" and ");
                        }
                        out.push_str(name);
                    }
                    out.push_str(" then\n");
                    self.indent_level += 1;
                    self.emit_block(body, out);
                    self.indent_level -= 1;
                    out.push_str(&self.indent());
                    out.push_str("end\n");
                }
            }
            Stmt::Divide(divisor, target) => {
                out.push_str(&self.indent());
                self.emit_expr(target, out);
                out.push_str(" = ");
                self.emit_expr(target, out);
                out.push_str(" / ");
                self.emit_expr(divisor, out);
                out.push('\n');
            }
            Stmt::Perform(name) => {
                out.push_str(&self.indent());
                out.push_str("__perf_");
                out.push_str(name);
                out.push_str("()\n");
            }
            Stmt::PerformBlock(body) => {
                out.push_str(&self.indent());
                out.push_str("do\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::Goto(target) => {
                out.push_str(&self.indent());
                out.push_str("goto label_");
                out.push_str(target);
                out.push('\n');
            }
            Stmt::Label(label, body) => {
                out.push_str(&self.indent());
                out.push_str("::label_");
                out.push_str(label);
                out.push_str("::\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
            }
            Stmt::Asm(text) => {
                out.push_str(&self.indent());
                out.push_str("do local fn, err = (load or loadstring)([=[\n");
                out.push_str(text);
                out.push_str("\n]=]); if fn then fn() else print(\"asm:\", err) end end\n");
            }
            Stmt::Progn(body) => {
                out.push_str(&self.indent());
                out.push_str("do\n");
                self.indent_level += 1;
                self.emit_block(body, out);
                self.indent_level -= 1;
                out.push_str(&self.indent());
                out.push_str("end\n");
            }
            Stmt::Expr(expr) => {
                if matches!(expr.as_ref(), Expr::Call(..)) {
                    out.push_str(&self.indent());
                    self.emit_expr(expr, out);
                    out.push('\n');
                } else {
                    out.push_str(&self.indent());
                    out.push_str("do local _ = ");
                    self.emit_expr(expr, out);
                    out.push_str(" end\n");
                }
            }
        }
    }

    fn gen_uid(&mut self) -> String {
        use std::sync::atomic::{AtomicU64, Ordering};
        static COUNTER: AtomicU64 = AtomicU64::new(0);
        COUNTER.fetch_add(1, Ordering::Relaxed).to_string()
    }

    fn emit_expr(&mut self, expr: &Expr, out: &mut String) {
        match expr {
            Expr::Number(n) => {
                let s = format!("{}", n);
                out.push_str(&s);
            }
            Expr::String(s) => {
                emit_lua_string_with_interp(s, out);
            }
            Expr::Bool(b) => {
                out.push_str(if *b { "true" } else { "false" });
            }
            Expr::None => {
                out.push_str("nil");
            }
            Expr::List(items) => {
                out.push('{');
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    self.emit_expr(item, out);
                }
                out.push('}');
            }
            Expr::Dict(pairs) => {
                out.push('{');
                for (i, (key, val)) in pairs.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    match key {
                        Expr::Ident(name) => {
                            out.push_str(name);
                            out.push_str(" = ");
                        }
                        Expr::String(s) => {
                            out.push('[');
                            emit_lua_string(s, out);
                            out.push_str("] = ");
                        }
                        _ => {
                            out.push('[');
                            self.emit_expr(key, out);
                            out.push_str("] = ");
                        }
                    }
                    self.emit_expr(val, out);
                }
                out.push('}');
            }
            Expr::Lambda(params, body) => {
                out.push_str("function(");
                for (i, p) in params.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    out.push_str(p);
                }
                out.push_str(") return ");
                self.emit_expr(body, out);
                out.push_str(" end");
            }
            Expr::Ident(name) => {
                out.push_str(name);
            }
            Expr::BinOp(lhs, op, rhs) => match op {
                BinOp::Add => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" + ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Sub => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" - ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Mul => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" * ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Div => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" / ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::IntDiv => {
                    out.push_str("math.floor(");
                    self.emit_expr(lhs, out);
                    out.push_str(" / ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Mod => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" % ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Pow => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" ^ ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Eq => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" == ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Neq => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" ~= ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Lt => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" < ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Gt => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" > ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Le => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" <= ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Ge => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" >= ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::And => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" and ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Or => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" or ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::Concat => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" .. ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
                BinOp::In => {
                    out.push_str("__contains(");
                    self.emit_expr(rhs, out);
                    out.push_str(", ");
                    self.emit_expr(lhs, out);
                    out.push(')');
                }
                BinOp::Is => {
                    out.push('(');
                    self.emit_expr(lhs, out);
                    out.push_str(" == ");
                    self.emit_expr(rhs, out);
                    out.push(')');
                }
            },
            Expr::UnaryOp(op, e) => match op {
                UnaryOp::Neg => {
                    out.push_str("(-");
                    self.emit_expr(e, out);
                    out.push(')');
                }
                UnaryOp::Not => {
                    out.push_str("not (");
                    self.emit_expr(e, out);
                    out.push(')');
                }
            },
            Expr::Call(func, args) => {
                // Try to detect and rewrite stream API chains
                if let Some(_stream_code) = self.try_emit_stream(func, args, out) {
                    // Already emitted
                    return;
                }

                // Try consteval evaluation
                if let Expr::Ident(fname) = func.as_ref() {
                    if let Some(result) = self.try_consteval(fname, args) {
                        self.emit_expr(&result, out);
                        return;
                    }
                }

                self.emit_expr(func, out);
                out.push('(');
                for (i, arg) in args.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    self.emit_expr(arg, out);
                }
                out.push(')');
            }
            Expr::Index(obj, index) => {
                self.emit_expr(obj, out);
                out.push('[');
                self.emit_expr(index, out);
                out.push(']');
            }
            Expr::Member(obj, name) => {
                self.emit_expr(obj, out);
                out.push('.');
                out.push_str(name);
            }
            Expr::Pipe(lhs, rhs) => {
                self.emit_pipe(lhs, rhs, out);
            }
            Expr::SafeMember(obj, name) => {
                out.push_str("(function() local _ = ");
                self.emit_expr(obj, out);
                out.push_str("; return _ and _.");
                out.push_str(name);
                out.push_str(" end)()");
            }
            Expr::Elvis(lhs, rhs) => {
                out.push('(');
                self.emit_expr(lhs, out);
                out.push_str(" or ");
                self.emit_expr(rhs, out);
                out.push(')');
            }
            Expr::SExpr(name, args) => {
                out.push_str(name);
                out.push('(');
                for (i, arg) in args.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    self.emit_expr(arg, out);
                }
                out.push(')');
            }
        }
    }

    fn emit_pipe(&mut self, lhs: &Expr, rhs: &Expr, out: &mut String) {
        match rhs {
            Expr::Call(func, args) => {
                self.emit_expr(func, out);
                out.push('(');
                for (i, arg) in args.iter().enumerate() {
                    if i > 0 {
                        out.push_str(", ");
                    }
                    self.emit_expr(arg, out);
                }
                if !args.is_empty() {
                    out.push_str(", ");
                }
                self.emit_expr(lhs, out);
                out.push(')');
            }
            Expr::Ident(name) => {
                out.push_str(name);
                out.push('(');
                self.emit_expr(lhs, out);
                out.push(')');
            }
            _ => {
                out.push('(');
                self.emit_expr(rhs, out);
                out.push(')');
                out.push('(');
                self.emit_expr(lhs, out);
                out.push(')');
            }
        }
    }

    fn try_emit_stream(&mut self, func: &Expr, args: &[Expr], out: &mut String) -> Option<()> {
        if !args.is_empty() {
            return None;
        }

        let (source, filter_pred, map_fn) = self.detect_stream_chain(func)?;

        out.push_str("(function()\n");
        self.indent_level += 1;

        if let Some(ref pred) = filter_pred {
            out.push_str(&self.indent());
            out.push_str("local __pred = ");
            self.emit_expr(&pred, out);
            out.push('\n');
        }
        if let Some(ref mf) = map_fn {
            out.push_str(&self.indent());
            out.push_str("local __map = ");
            self.emit_expr(&mf, out);
            out.push('\n');
        }

        out.push_str(&self.indent());
        out.push_str("local __res = {}\n");
        out.push_str(&self.indent());
        out.push_str("for _, __item in pairs(");
        self.emit_expr(&source, out);
        out.push_str(") do\n");
        self.indent_level += 1;

        if filter_pred.is_some() {
            out.push_str(&self.indent());
            out.push_str("if __pred(__item) then\n");
            self.indent_level += 1;
        }

        out.push_str(&self.indent());
        out.push_str("__res[#__res + 1] = ");
        if map_fn.is_some() {
            out.push_str("__map(__item)");
        } else {
            out.push_str("__item");
        }
        out.push('\n');

        if filter_pred.is_some() {
            self.indent_level -= 1;
            out.push_str(&self.indent());
            out.push_str("end\n");
        }

        self.indent_level -= 1;
        out.push_str(&self.indent());
        out.push_str("end\n");
        out.push_str(&self.indent());
        out.push_str("return __res\n");
        self.indent_level -= 1;
        out.push_str(&self.indent());
        out.push_str("end)()");

        Some(())
    }

    fn detect_stream_chain(&self, expr: &Expr) -> Option<(Expr, Option<Expr>, Option<Expr>)> {
        // match: .collect() — called as Call(Member(..,"collect"), [])
        // OR just .collect — called as just Member(..,"collect") with outer args being []
        match expr {
            Expr::Call(inner, collect_args) if collect_args.is_empty() => {
                if let Expr::Member(obj, name) = inner.as_ref() {
                    if name == "collect" {
                        return self.detect_map_chain(obj);
                    }
                }
            }
            Expr::Member(obj, name) if name == "collect" => {
                return self.detect_map_chain(obj);
            }
            _ => {}
        }
        None
    }

    fn detect_map_chain(&self, expr: &Expr) -> Option<(Expr, Option<Expr>, Option<Expr>)> {
        if let Expr::Call(inner, map_args) = expr {
            if map_args.len() == 1 {
                if let Expr::Member(obj, name) = inner.as_ref() {
                    if name == "map" {
                        let (source, filter, _) = self.detect_filter_chain(obj)?;
                        return Some((source, filter, Some(map_args[0].clone())));
                    }
                }
            }
        }
        None
    }

    fn detect_filter_chain(&self, expr: &Expr) -> Option<(Expr, Option<Expr>, Option<Expr>)> {
        if let Expr::Call(inner, filter_args) = expr {
            if filter_args.len() == 1 {
                if let Expr::Member(obj, name) = inner.as_ref() {
                    if name == "filter" {
                        let (source, _, _) = self.detect_stream_source(obj)?;
                        return Some((source, Some(filter_args[0].clone()), None));
                    }
                }
            }
        }
        // No filter: pass through
        let (source, _, _) = self.detect_stream_source(expr)?;
        Some((source, None, None))
    }

    fn detect_stream_source(&self, expr: &Expr) -> Option<(Expr, Option<Expr>, Option<Expr>)> {
        if let Expr::Call(inner, source_args) = expr {
            if source_args.is_empty() {
                if let Expr::Member(obj, name) = inner.as_ref() {
                    if name == "stream" {
                        return Some(((**obj).clone(), None, None));
                    }
                }
            }
        }
        None
    }

    fn try_consteval(&mut self, fname: &str, args: &[Expr]) -> Option<Expr> {
        let (params, body) = self.consteval_fns.get(fname)?.clone();
        if params.len() != args.len() {
            return None;
        }

        let mut env: HashMap<String, Expr> = HashMap::new();
        for (p, a) in params.iter().zip(args.iter()) {
            let evaluated = self.eval_const_expr(a, &HashMap::new())?;
            env.insert(p.clone(), evaluated);
        }

        for stmt in &body {
            if let Stmt::Return(Some(expr)) = stmt {
                return self.eval_const_expr(expr, &env);
            }
        }
        None
    }

    fn eval_const_expr(&mut self, expr: &Expr, env: &HashMap<String, Expr>) -> Option<Expr> {
        match expr {
            Expr::Number(_) | Expr::String(_) | Expr::Bool(_) | Expr::None => Some(expr.clone()),
            Expr::Ident(name) => env.get(name).cloned(),
            Expr::UnaryOp(op, e) => {
                let v = self.eval_const_expr(e, env)?;
                match (op, &v) {
                    (UnaryOp::Neg, Expr::Number(n)) => Some(Expr::Number(-n)),
                    (UnaryOp::Not, Expr::Bool(b)) => Some(Expr::Bool(!b)),
                    _ => None,
                }
            }
            Expr::BinOp(lhs, op, rhs) => {
                let l = self.eval_const_expr(lhs, env)?;
                let r = self.eval_const_expr(rhs, env)?;
                match (op, &l, &r) {
                    (BinOp::Add, Expr::Number(a), Expr::Number(b)) => Some(Expr::Number(a + b)),
                    (BinOp::Sub, Expr::Number(a), Expr::Number(b)) => Some(Expr::Number(a - b)),
                    (BinOp::Mul, Expr::Number(a), Expr::Number(b)) => Some(Expr::Number(a * b)),
                    (BinOp::Div, Expr::Number(a), Expr::Number(b)) => Some(Expr::Number(a / b)),
                    (BinOp::Mod, Expr::Number(a), Expr::Number(b)) => Some(Expr::Number(a % b)),
                    (BinOp::Pow, Expr::Number(a), Expr::Number(b)) => {
                        Some(Expr::Number(a.powf(*b)))
                    }
                    (BinOp::Concat, Expr::String(a), Expr::String(b)) => {
                        Some(Expr::String(format!("{}{}", a, b)))
                    }
                    (BinOp::Eq, _, _) => Some(Expr::Bool(self.const_eq(&l, &r))),
                    (BinOp::Neq, _, _) => Some(Expr::Bool(!self.const_eq(&l, &r))),
                    _ => None,
                }
            }
            Expr::Call(func, args) => {
                if let Expr::Ident(fname) = func.as_ref() {
                    let eval_args: Vec<Expr> = args
                        .iter()
                        .map(|a| self.eval_const_expr(a, env))
                        .collect::<Option<Vec<_>>>()?;
                    return self.try_consteval(fname, &eval_args);
                }
                None
            }
            _ => None,
        }
    }

    fn const_eq(&self, a: &Expr, b: &Expr) -> bool {
        match (a, b) {
            (Expr::Number(x), Expr::Number(y)) => (x - y).abs() < 1e-10,
            (Expr::String(x), Expr::String(y)) => x == y,
            (Expr::Bool(x), Expr::Bool(y)) => x == y,
            (Expr::None, Expr::None) => true,
            _ => false,
        }
    }
}

// Stmt children iterator for recursive traversal
impl Stmt {
    fn children(&self) -> Vec<&[Stmt]> {
        match self {
            Stmt::If(_, body, elifs, else_body) => {
                let mut v = vec![body.as_slice()];
                for (_, eb) in elifs {
                    v.push(eb.as_slice());
                }
                if let Some(eb) = else_body {
                    v.push(eb.as_slice());
                }
                v
            }
            Stmt::For(_, _, body)
            | Stmt::While(_, body)
            | Stmt::Repeat(_, body)
            | Stmt::Fn(_, _, body)
            | Stmt::ConstEvalFn(_, _, body)
            | Stmt::Class(_, _, body)
            | Stmt::Progn(body)
            | Stmt::PerformBlock(body)
            | Stmt::Label(_, body) => vec![body.as_slice()],
            Stmt::With(_, body, else_body) => {
                let mut v = vec![body.as_slice()];
                if let Some(eb) = else_body {
                    v.push(eb.as_slice());
                }
                v
            }
            _ => vec![],
        }
    }
}

fn emit_lua_string(s: &str, out: &mut String) {
    out.push('"');
    for c in s.chars() {
        match c {
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            c => out.push(c),
        }
    }
    out.push('"');
}

fn emit_lua_string_with_interp(s: &str, out: &mut String) {
    if let Some(parts) = split_interpolation(s) {
        for (i, part) in parts.iter().enumerate() {
            if i > 0 {
                out.push_str(" .. ");
            }
            match part {
                InterpPart::Text(t) => emit_lua_string(t, out),
                InterpPart::Expr(e) => out.push_str(e),
            }
        }
    } else {
        emit_lua_string(s, out);
    }
}

enum InterpPart<'a> {
    Text(&'a str),
    Expr(&'a str),
}

fn split_interpolation<'a>(s: &'a str) -> Option<Vec<InterpPart<'a>>> {
    let mut parts = Vec::new();
    let mut rest = s;
    let mut found = false;

    while let Some(start) = rest.find('{') {
        if start > 0 {
            parts.push(InterpPart::Text(&rest[..start]));
        }

        let after_brace = &rest[start + 1..];
        if let Some(end) = after_brace.find('}') {
            let expr = &after_brace[..end];
            parts.push(InterpPart::Expr(expr));
            rest = &after_brace[end + 1..];
            found = true;
        } else {
            parts.push(InterpPart::Text(&rest[start..]));
            rest = "";
            break;
        }
    }

    if !rest.is_empty() {
        parts.push(InterpPart::Text(rest));
    }

    if found {
        Some(parts)
    } else {
        None
    }
}

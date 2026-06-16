#[derive(Debug, Clone)]
pub struct Program {
    pub stmts: Vec<Stmt>,
}

#[derive(Debug, Clone)]
pub enum Stmt {
    Let(String, Box<Expr>),
    Const(String, Box<Expr>),
    Assign(Box<Expr>, Box<Expr>),
    If(Box<Expr>, Vec<Stmt>, Vec<(Box<Expr>, Vec<Stmt>)>, Option<Vec<Stmt>>),
    For(String, Box<Expr>, Vec<Stmt>),
    While(Box<Expr>, Vec<Stmt>),
    Repeat(Box<Expr>, Vec<Stmt>),
    Fn(String, Vec<String>, Vec<Stmt>),
    Class(String, Option<String>, Vec<Stmt>),
    Return(Option<Box<Expr>>),
    Pass,
    Import(String),
    FromImport(String, Vec<String>),
    Expr(Box<Expr>),
    Defer(Box<Expr>),
    ErrDefer(Box<Expr>),
    With(Vec<(String, Expr)>, Vec<Stmt>, Option<Vec<Stmt>>),
    Divide(Box<Expr>, Box<Expr>),
    Perform(String),
    PerformBlock(Vec<Stmt>),
    Goto(String),
    Label(String, Vec<Stmt>),
    Asm(String),
    ConstEvalFn(String, Vec<String>, Vec<Stmt>),
    Progn(Vec<Stmt>),
}

#[derive(Debug, Clone)]
pub enum Expr {
    Number(f64),
    String(String),
    Bool(bool),
    None,
    List(Vec<Expr>),
    Dict(Vec<(Expr, Expr)>),
    Lambda(Vec<String>, Box<Expr>),
    Ident(String),
    BinOp(Box<Expr>, BinOp, Box<Expr>),
    UnaryOp(UnaryOp, Box<Expr>),
    Call(Box<Expr>, Vec<Expr>),
    Index(Box<Expr>, Box<Expr>),
    Member(Box<Expr>, String),
    Pipe(Box<Expr>, Box<Expr>),
    SafeMember(Box<Expr>, String),
    Elvis(Box<Expr>, Box<Expr>),

    SExpr(String, Vec<Expr>),
}

#[derive(Debug, Clone)]
pub enum BinOp {
    Add, Sub, Mul, Div, IntDiv, Mod, Pow,
    Eq, Neq, Lt, Gt, Le, Ge,
    And, Or, Concat, In, Is,
}

#[derive(Debug, Clone)]
pub enum UnaryOp {
    Neg,
    Not,
}

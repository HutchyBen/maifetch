const std = @import("std");

const Allocator = std.mem.Allocator;

pub const ConfigError = error{
    MissingValue,
    UnknownArgument,
    AccessTokenRequired,
    ScoreCountTooHigh,
    InvalidNumber,
} || anyerror;

pub const Config = struct {
    allocator: Allocator,
    access_token: []const u8 = "",
    config_file: []const u8 = "",
    logo_size: i32 = 20,
    score_count: u32 = 4,
    access_token_owned: bool = false,
    config_file_owned: bool = false,

    pub fn init(allocator: Allocator) Config {
        return .{ .allocator = allocator };
    }

    pub fn deinit(self: *Config) void {
        if (self.access_token_owned and self.access_token.len > 0) {
            self.allocator.free(self.access_token);
        }
        if (self.config_file_owned and self.config_file.len > 0) {
            self.allocator.free(self.config_file);
        }
        self.* = undefined;
    }

    fn setAccessToken(self: *Config, value: []const u8) Allocator.Error!void {
        if (self.access_token_owned and self.access_token.len > 0) {
            self.allocator.free(self.access_token);
        }
        self.access_token = try self.allocator.dupe(u8, value);
        self.access_token_owned = true;
    }

    fn setConfigFile(self: *Config, value: []const u8) Allocator.Error!void {
        if (self.config_file_owned and self.config_file.len > 0) {
            self.allocator.free(self.config_file);
        }
        self.config_file = try self.allocator.dupe(u8, value);
        self.config_file_owned = true;
    }
};

const FileConfig = struct {
    accessToken: ?[]const u8 = null,
    logoSize: ?i32 = null,
    scoreCount: ?u32 = null,
};

pub const CliOptions = struct {
    access_token: ?[]const u8 = null,
    config_file: ?[]const u8 = null,
    logo_size: ?i32 = null,
    score_count: ?u32 = null,
    help: bool = false,
};

pub const help_text =
    \\Usage: maifetch [options]
    \\
    \\Options:
    \\  -a, -t, --access-token <token>   MaiTea access token
    \\  -c, --config-file <path>         JSON config file path
    \\  -l, --logo-size <number>         ASCII logo size; <=0 disables logo
    \\  -s, --score-count <number>       Recent score count; max 12
    \\  -h, --help                       Show this help
    \\
    \\Environment:
    \\  MAITEA_TOKEN / MAIFETCH_TOKEN
    \\  MAITEA_CONFIG_FILE / MAIFETCH_CONFIG_FILE
    \\  MAITEA_LOGO_SIZE / MAIFETCH_LOGO_SIZE
    \\  MAITEA_SCORE_COUNT / MAIFETCH_SCORE_COUNT
    \\
;

pub fn helpText() []const u8 {
    return help_text;
}

pub fn defaultConfigFile(allocator: Allocator) ![]u8 {
    if (@import("builtin").os.tag == .windows) {
        const appdata = std.process.getEnvVarOwned(allocator, "APPDATA") catch |err| switch (err) {
            error.EnvironmentVariableNotFound => try std.fs.getAppDataDir(allocator, ""),
            else => return err,
        };
        defer allocator.free(appdata);
        return std.fs.path.join(allocator, &.{ appdata, "maifetch.json" });
    }

    if (std.process.getEnvVarOwned(allocator, "XDG_CONFIG_HOME")) |xdg| {
        defer allocator.free(xdg);
        return std.fs.path.join(allocator, &.{ xdg, "maifetch.json" });
    } else |err| switch (err) {
        error.EnvironmentVariableNotFound => {},
        else => return err,
    }

    const home = try std.process.getEnvVarOwned(allocator, "HOME");
    defer allocator.free(home);
    if (@import("builtin").os.tag == .macos) {
        return std.fs.path.join(allocator, &.{ home, "Library", "Application Support", "maifetch.json" });
    }
    return std.fs.path.join(allocator, &.{ home, ".config", "maifetch.json" });
}

pub fn parseArgs(args: []const []const u8) ConfigError!CliOptions {
    var opts: CliOptions = .{};
    var i: usize = 0;
    while (i < args.len) : (i += 1) {
        const arg = args[i];
        if (std.mem.eql(u8, arg, "-h") or std.mem.eql(u8, arg, "--help")) {
            opts.help = true;
        } else if (std.mem.eql(u8, arg, "-a") or std.mem.eql(u8, arg, "-t") or std.mem.eql(u8, arg, "--access-token")) {
            i += 1;
            if (i >= args.len) return error.MissingValue;
            opts.access_token = args[i];
        } else if (std.mem.eql(u8, arg, "-c") or std.mem.eql(u8, arg, "--config-file")) {
            i += 1;
            if (i >= args.len) return error.MissingValue;
            opts.config_file = args[i];
        } else if (std.mem.eql(u8, arg, "-l") or std.mem.eql(u8, arg, "--logo-size")) {
            i += 1;
            if (i >= args.len) return error.MissingValue;
            opts.logo_size = std.fmt.parseInt(i32, args[i], 10) catch return error.InvalidNumber;
        } else if (std.mem.eql(u8, arg, "-s") or std.mem.eql(u8, arg, "--score-count")) {
            i += 1;
            if (i >= args.len) return error.MissingValue;
            opts.score_count = std.fmt.parseInt(u32, args[i], 10) catch return error.InvalidNumber;
        } else {
            return error.UnknownArgument;
        }
    }
    return opts;
}

fn readFileConfig(allocator: Allocator, path: []const u8, cfg: *Config) ConfigError!void {
    const bytes = std.fs.cwd().readFileAlloc(allocator, path, 1024 * 1024) catch |err| switch (err) {
        error.FileNotFound => return,
        else => return err,
    };
    defer allocator.free(bytes);

    var parsed = try std.json.parseFromSlice(FileConfig, allocator, bytes, .{ .ignore_unknown_fields = true });
    defer parsed.deinit();

    if (parsed.value.accessToken) |token| try cfg.setAccessToken(token);
    if (parsed.value.logoSize) |logo_size| cfg.logo_size = logo_size;
    if (parsed.value.scoreCount) |score_count| cfg.score_count = score_count;
}

fn applyEnvString(allocator: Allocator, cfg: *Config, comptime names: []const []const u8, setter: fn (*Config, []const u8) Allocator.Error!void) !void {
    inline for (names) |name| {
        if (std.process.getEnvVarOwned(allocator, name)) |value| {
            defer allocator.free(value);
            try setter(cfg, value);
            return;
        } else |err| switch (err) {
            error.EnvironmentVariableNotFound => {},
            else => return err,
        }
    }
}

fn applyEnvNumber(allocator: Allocator, comptime T: type, comptime names: []const []const u8) !?T {
    inline for (names) |name| {
        if (std.process.getEnvVarOwned(allocator, name)) |value| {
            defer allocator.free(value);
            return std.fmt.parseInt(T, value, 10) catch return error.InvalidNumber;
        } else |err| switch (err) {
            error.EnvironmentVariableNotFound => {},
            else => return err,
        }
    }
    return null;
}

pub fn load(allocator: Allocator, args: []const []const u8) ConfigError!Config {
    const cli = try parseArgs(args);
    var cfg = Config.init(allocator);
    errdefer cfg.deinit();

    if (cli.config_file) |path| {
        try cfg.setConfigFile(path);
    } else {
        const default_path = try defaultConfigFile(allocator);
        cfg.config_file = default_path;
        cfg.config_file_owned = true;
    }

    try readFileConfig(allocator, cfg.config_file, &cfg);

    try applyEnvString(allocator, &cfg, &.{ "MAITEA_TOKEN", "MAIFETCH_TOKEN" }, Config.setAccessToken);
    try applyEnvString(allocator, &cfg, &.{ "MAITEA_CONFIG_FILE", "MAIFETCH_CONFIG_FILE" }, Config.setConfigFile);
    if (try applyEnvNumber(allocator, i32, &.{ "MAITEA_LOGO_SIZE", "MAIFETCH_LOGO_SIZE" })) |logo_size| cfg.logo_size = logo_size;
    if (try applyEnvNumber(allocator, u32, &.{ "MAITEA_SCORE_COUNT", "MAIFETCH_SCORE_COUNT" })) |score_count| cfg.score_count = score_count;

    if (cli.access_token) |token| try cfg.setAccessToken(token);
    if (cli.config_file) |path| try cfg.setConfigFile(path);
    if (cli.logo_size) |logo_size| cfg.logo_size = logo_size;
    if (cli.score_count) |score_count| cfg.score_count = score_count;

    if (cfg.access_token.len == 0) return error.AccessTokenRequired;
    if (cfg.score_count > 12) return error.ScoreCountTooHigh;

    return cfg;
}

test "parseArgs supports documented and legacy token flags" {
    const args = [_][]const u8{ "-a", "abc", "-l", "0", "-s", "2" };
    const opts = try parseArgs(&args);
    try std.testing.expectEqualStrings("abc", opts.access_token.?);
    try std.testing.expectEqual(@as(i32, 0), opts.logo_size.?);
    try std.testing.expectEqual(@as(u32, 2), opts.score_count.?);

    const legacy = [_][]const u8{ "-t", "old" };
    const legacy_opts = try parseArgs(&legacy);
    try std.testing.expectEqualStrings("old", legacy_opts.access_token.?);
}

test "file config is loaded before CLI overrides" {
    const allocator = std.testing.allocator;
    const path = "zig-cache-test-maifetch.json";
    try std.fs.cwd().writeFile(.{ .sub_path = path, .data = "{\"accessToken\":\"file-token\",\"logoSize\":1,\"scoreCount\":3}" });
    defer std.fs.cwd().deleteFile(path) catch {};

    const args = [_][]const u8{ "--config-file", path, "--access-token", "cli-token", "--score-count", "2" };
    var cfg = try load(allocator, &args);
    defer cfg.deinit();
    try std.testing.expectEqualStrings("cli-token", cfg.access_token);
    try std.testing.expectEqual(@as(i32, 1), cfg.logo_size);
    try std.testing.expectEqual(@as(u32, 2), cfg.score_count);
}

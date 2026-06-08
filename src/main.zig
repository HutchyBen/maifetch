const std = @import("std");
const maifetch = @import("maifetch");

pub fn main() !void {
    var debug_allocator: std.heap.DebugAllocator(.{}) = .init;
    defer _ = debug_allocator.deinit();
    const allocator = debug_allocator.allocator();

    const args_all = try std.process.argsAlloc(allocator);
    defer std.process.argsFree(allocator, args_all);
    const args = if (args_all.len > 1) args_all[1..] else &[_][]const u8{};

    const cli = maifetch.config.parseArgs(args) catch |err| {
        try printError("{s}\n", .{@errorName(err)});
        try printError("{s}", .{maifetch.config.helpText()});
        return;
    };
    if (cli.help) {
        try printOut("{s}", .{maifetch.config.helpText()});
        return;
    }

    var cfg = maifetch.config.load(allocator, args) catch |err| {
        switch (err) {
            error.AccessTokenRequired => try printError("access token is required\n", .{}),
            error.ScoreCountTooHigh => try printError("score count cannot be higher than 12\n", .{}),
            else => try printError("{s}\n", .{@errorName(err)}),
        }
        return;
    };
    defer cfg.deinit();

    var client = maifetch.maitea.ApiClient.init(allocator, cfg.access_token);
    defer client.deinit();

    var profiles = client.getProfiles() catch |err| {
        try printError("{s}\n", .{@errorName(err)});
        return;
    };
    defer profiles.deinit();

    if (profiles.value.data.len == 0) {
        try printOut("No profiles found\n", .{});
        return;
    }

    var plays = client.getPlays() catch |err| {
        try printError("{s}\n", .{@errorName(err)});
        return;
    };
    defer plays.deinit();

    var stdout_buffer: [4096]u8 = undefined;
    var stdout_writer = std.fs.File.stdout().writer(&stdout_buffer);
    const stdout = &stdout_writer.interface;
    try maifetch.output.writeOutput(stdout, profiles.value.data[0], plays.value.data, cfg.logo_size, cfg.score_count);
    try stdout.flush();
}

fn printOut(comptime fmt: []const u8, args: anytype) !void {
    var buffer: [4096]u8 = undefined;
    var writer = std.fs.File.stdout().writer(&buffer);
    try writer.interface.print(fmt, args);
    try writer.interface.flush();
}

fn printError(comptime fmt: []const u8, args: anytype) !void {
    var buffer: [4096]u8 = undefined;
    var writer = std.fs.File.stderr().writer(&buffer);
    try writer.interface.print(fmt, args);
    try writer.interface.flush();
}

test "main imports library modules" {
    _ = maifetch.config.helpText();
}

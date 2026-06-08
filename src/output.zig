const std = @import("std");
const maitea = @import("maitea.zig");

pub fn writeColour(writer: *std.Io.Writer, text: []const u8) !void {
    try writer.print("\x1b[38;2;72;184;200m{s}\x1b[0m", .{text});
}

fn writeFg(writer: *std.Io.Writer, text: []const u8, r: u8, g: u8, b: u8) !void {
    try writer.print("\x1b[38;2;{};{};{}m{s}\x1b[0m", .{ r, g, b, text });
}

fn writeFgBg(writer: *std.Io.Writer, text: []const u8, fr: u8, fg: u8, fb: u8, br: u8, bg: u8, bb: u8) !void {
    try writer.print("\x1b[38;2;{};{};{}m\x1b[48;2;{};{};{}m{s}\x1b[0m", .{ fr, fg, fb, br, bg, bb, text });
}

pub fn writeDifficulty(writer: *std.Io.Writer, diff: []const u8) !void {
    if (std.mem.eql(u8, diff, "easy")) return writeFgBg(writer, "Easy", 255, 255, 255, 69, 174, 255);
    if (std.mem.eql(u8, diff, "basic")) return writeFgBg(writer, "Basic", 255, 255, 255, 111, 212, 61);
    if (std.mem.eql(u8, diff, "advanced")) return writeFgBg(writer, "Advanced", 255, 255, 255, 248, 183, 9);
    if (std.mem.eql(u8, diff, "expert")) return writeFgBg(writer, "Expert", 255, 255, 255, 255, 46, 66);
    if (std.mem.eql(u8, diff, "master")) return writeFgBg(writer, "Master", 255, 255, 255, 171, 140, 233);
    if (std.mem.eql(u8, diff, "remaster") or std.mem.eql(u8, diff, "re:master")) return writeFgBg(writer, "Re:Master", 255, 255, 255, 207, 114, 237);
    if (std.mem.eql(u8, diff, "utage")) return writeFgBg(writer, "Utage", 255, 255, 255, 255, 68, 1);
    try writer.writeAll(diff);
}

pub fn writeRank(writer: *std.Io.Writer, rank: []const u8) !void {
    if (std.mem.eql(u8, rank, "SSS+")) {
        try writeFg(writer, "S", 255, 200, 54);
        try writeFg(writer, "S", 225, 38, 165);
        try writeFg(writer, "S", 73, 64, 233);
        return writeFg(writer, "+", 21, 203, 148);
    }
    if (std.mem.eql(u8, rank, "SSS")) {
        try writeFg(writer, "S", 255, 200, 54);
        try writeFg(writer, "S", 232, 39, 148);
        return writeFg(writer, "S", 18, 195, 144);
    }
    if (std.mem.eql(u8, rank, "SS+") or std.mem.eql(u8, rank, "SS")) return writeFgBg(writer, rank, 248, 200, 75, 143, 71, 33);
    if (std.mem.eql(u8, rank, "S+") or std.mem.eql(u8, rank, "S")) return writeFgBg(writer, rank, 248, 200, 75, 75, 82, 82);
    if (std.mem.eql(u8, rank, "AAA") or std.mem.eql(u8, rank, "AA") or std.mem.eql(u8, rank, "A")) return writeFg(writer, rank, 23, 163, 255);
    try writer.writeAll(rank);
}

pub fn writeWideToNormal(writer: *std.Io.Writer, text: []const u8) !void {
    var view = try std.unicode.Utf8View.init(text);
    var it = view.iterator();
    while (it.nextCodepoint()) |cp| {
        const normal = if (cp >= 0xff01 and cp <= 0xff5e) cp - 0xfee0 else cp;
        var buf: [4]u8 = undefined;
        const len = try std.unicode.utf8Encode(normal, &buf);
        try writer.writeAll(buf[0..len]);
    }
}

pub fn allocWideToNormal(allocator: std.mem.Allocator, text: []const u8) ![]u8 {
    var out = std.Io.Writer.Allocating.init(allocator);
    defer out.deinit();
    try writeWideToNormal(&out.writer, text);
    return out.toOwnedSlice();
}

pub fn writeInfo(writer: *std.Io.Writer, profile: maitea.Profile, plays: []const maitea.Play, score_count: u32) !void {
    var name_writer = std.Io.Writer.Allocating.init(std.heap.page_allocator);
    defer name_writer.deinit();
    try writeWideToNormal(&name_writer.writer, profile.name);
    const name = name_writer.written();

    try writeColour(writer, name);
    try writer.writeByte('\n');
    try writer.splatByteAll('-', name.len);
    try writer.writeByte('\n');

    try writeColour(writer, "ID");
    try writer.print(": {}\n", .{profile.id});
    try writeColour(writer, "Rating");
    try writer.writeAll(": ");
    try writeRating(writer, profile.rating);
    try writer.writeAll(" / ");
    try writeRating(writer, profile.rating_highest);
    try writer.writeByte('\n');
    try writeColour(writer, "Level");
    try writer.print(": {}\n", .{profile.level});
    try writeColour(writer, "Total Credits");
    try writer.print(": {}\n", .{profile.play_stats.total});
    try writeColour(writer, "Recent Scores");
    try writer.writeAll(":\n");

    const limit = @min(@as(usize, @intCast(score_count)), plays.len);
    var i: usize = 0;
    while (i < limit) : (i += 1) {
        const play = plays[i];
        try writer.print("  {s}  ", .{play.song.name.en});
        try writeDifficulty(writer, play.difficulty_level.value);
        try writer.writeByte('\n');

        try writer.print("  {s} {s}% ", .{ play.score_formatted, play.achievement_formatted });
        try writeRank(writer, play.rank);
        if (play.full_combo_label) |label| {
            if (label.len > 0) try writer.print(" {s}", .{label});
        }
        try writer.writeAll(" \n\n");
    }
}

fn writeRating(writer: *std.Io.Writer, raw: i64) !void {
    const whole = @divTrunc(raw, 100);
    const cents = @abs(@mod(raw, 100));
    try writer.print("{}.{:0>2}", .{ whole, cents });
}

pub fn writeOutput(writer: *std.Io.Writer, profile: maitea.Profile, plays: []const maitea.Play, logo_size: i32, score_count: u32) !void {
    _ = logo_size;
    try writeInfo(writer, profile, plays, score_count);
}

test "wide characters are normalized" {
    const allocator = std.testing.allocator;
    const fullwidth = "\xef\xbc\xa1\xef\xbc\xa2\xef\xbc\xa3\xef\xbc\x91\xef\xbc\x92\xef\xbc\x93";
    const normalized = try allocWideToNormal(allocator, fullwidth);
    defer allocator.free(normalized);
    try std.testing.expectEqualStrings("ABC123", normalized);
}

test "info output includes profile and score fields" {
    var buffer = std.Io.Writer.Allocating.init(std.testing.allocator);
    defer buffer.deinit();

    const profile: maitea.Profile = .{
        .id = 7,
        .name = "Alice",
        .rating = 12345,
        .rating_highest = 13000,
        .level = 42,
        .play_stats = .{ .total = 99 },
    };
    const plays = [_]maitea.Play{.{
        .score_formatted = "1,000,000",
        .achievement_formatted = "100.0000",
        .rank = "SSS+",
        .full_combo_label = "FC",
        .difficulty_level = .{ .value = "expert" },
        .song = .{ .name = .{ .en = "Song A" } },
    }};

    try writeOutput(&buffer.writer, profile, &plays, 0, 1);
    const out = buffer.written();
    try std.testing.expect(std.mem.indexOf(u8, out, "Alice") != null);
    try std.testing.expect(std.mem.indexOf(u8, out, "Rating") != null);
    try std.testing.expect(std.mem.indexOf(u8, out, "Song A") != null);
    try std.testing.expect(std.mem.indexOf(u8, out, "1,000,000 100.0000%") != null);
}

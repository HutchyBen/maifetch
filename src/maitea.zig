const std = @import("std");

const Allocator = std.mem.Allocator;
pub const base_url = "https://maitea.app";

pub const Image = struct {
    id: i64 = 0,
    png: []const u8 = "",
    webp: []const u8 = "",
};

pub const LocalizedName = struct {
    en: []const u8 = "",
    jp: []const u8 = "",
};

pub const TrackInfo = struct {
    id: i64 = 0,
    code: []const u8 = "",
    name: LocalizedName = .{},
    artist: LocalizedName = .{},
};

pub const PlayRef = struct {
    id: i64 = 0,
    date: []const u8 = "",
    date_unix: i64 = 0,
    api_route: []const u8 = "",
};

pub const PlayStats = struct {
    total: i64 = 0,
    wins: i64 = 0,
    vs: i64 = 0,
    sync: i64 = 0,
    first: PlayRef = .{},
    latest: PlayRef = .{},
};

pub const Nameplate = struct {
    id: i64 = 0,
    png: []const u8 = "",
    webp: []const u8 = "",
};

pub const ProfileOptions = struct {
    icon: Image = .{},
    icon_deka: Image = .{},
    nameplate: Nameplate = .{},
    frame: Nameplate = .{},
};

pub const Profile = struct {
    id: i64 = 0,
    name: []const u8 = "",
    rating: i64 = 0,
    rating_highest: i64 = 0,
    level: i64 = 0,
    play_stats: PlayStats = .{},
    options: ProfileOptions = .{},
};

pub const Notes = struct {
    perfect: i64 = 0,
    great: i64 = 0,
    good: i64 = 0,
    bad: i64 = 0,
};

pub const ScoreDetail = struct {
    hits: Notes = .{},
    tap: Notes = .{},
    hold: Notes = .{},
    slide: Notes = .{},
    @"break": Notes = .{},
};

pub const DifficultyLevel = struct {
    key: i64 = 0,
    value: []const u8 = "",
    label: []const u8 = "",
};

pub const Score = struct {
    id: i64 = 0,
    achievement: i64 = 0,
    achievement_formatted: []const u8 = "",
    score: i64 = 0,
    score_formatted: []const u8 = "",
    rank: []const u8 = "",
    full_combo: i64 = 0,
    full_combo_label: ?[]const u8 = null,
    is_all_perfect: bool = false,
    is_all_perfect_plus: bool = false,
    difficulty_level: DifficultyLevel = .{},
    song: TrackInfo = .{},
    player: Profile = .{},
};

pub const Play = struct {
    id: i64 = 0,
    achievement: i64 = 0,
    achievement_formatted: []const u8 = "",
    track: i64 = 0,
    score: i64 = 0,
    score_formatted: []const u8 = "",
    score_detail: ScoreDetail = .{},
    rank: []const u8 = "",
    full_combo: i64 = 0,
    full_combo_label: ?[]const u8 = null,
    is_high_score: bool = false,
    is_all_perfect: bool = false,
    is_track_skip: bool = false,
    difficulty_level: DifficultyLevel = .{},
    play_date: []const u8 = "",
    play_date_unix: i64 = 0,
    song: TrackInfo = .{},
    player: Profile = .{},
};

pub const Status = struct {
    webui: struct {
        api: []const u8 = "",
        db_read: struct {
            status: []const u8 = "",
            query_time: []const u8 = "",
        } = .{},
        db_write: struct {
            status: []const u8 = "",
            query_time: []const u8 = "",
        } = .{},
    } = .{},
    game: struct {
        status: []const u8 = "",
    } = .{},
    last_updated: i64 = 0,
};

pub fn DataResponse(comptime T: type) type {
    return struct {
        data: T,
    };
}

pub fn Page(comptime T: type) type {
    return struct {
        data: T,
        links: struct {
            first: []const u8 = "",
            last: []const u8 = "",
            prev: ?[]const u8 = null,
            next: ?[]const u8 = null,
        } = .{},
        meta: struct {
            current_page: i64 = 0,
            from: ?i64 = null,
            last_page: i64 = 0,
            path: []const u8 = "",
            per_page: i64 = 0,
            to: ?i64 = null,
            total: i64 = 0,
        } = .{},
    };
}

pub fn parseProfiles(allocator: Allocator, body: []const u8) !std.json.Parsed(DataResponse([]Profile)) {
    return std.json.parseFromSlice(DataResponse([]Profile), allocator, body, .{ .ignore_unknown_fields = true });
}

pub fn parseTracks(allocator: Allocator, body: []const u8) !std.json.Parsed(DataResponse([]TrackInfo)) {
    return std.json.parseFromSlice(DataResponse([]TrackInfo), allocator, body, .{ .ignore_unknown_fields = true });
}

pub fn parsePlays(allocator: Allocator, body: []const u8) !std.json.Parsed(Page([]Play)) {
    return std.json.parseFromSlice(Page([]Play), allocator, body, .{ .ignore_unknown_fields = true });
}

pub fn parseScores(allocator: Allocator, body: []const u8) !std.json.Parsed(Page([]Score)) {
    return std.json.parseFromSlice(Page([]Score), allocator, body, .{ .ignore_unknown_fields = true });
}

pub fn parseStatus(allocator: Allocator, body: []const u8) !std.json.Parsed(Status) {
    return std.json.parseFromSlice(Status, allocator, body, .{ .ignore_unknown_fields = true });
}

pub const ApiClient = struct {
    allocator: Allocator,
    access_token: []const u8,
    client: std.http.Client,

    pub fn init(allocator: Allocator, access_token: []const u8) ApiClient {
        return .{
            .allocator = allocator,
            .access_token = access_token,
            .client = .{ .allocator = allocator },
        };
    }

    pub fn deinit(self: *ApiClient) void {
        self.client.deinit();
    }

    pub fn get(self: *ApiClient, path: []const u8) ![]u8 {
        const normalized_path = if (std.mem.startsWith(u8, path, base_url))
            path[base_url.len..]
        else
            path;
        const url = try std.fmt.allocPrint(self.allocator, "{s}{s}", .{ base_url, normalized_path });
        defer self.allocator.free(url);

        const authorization = try std.fmt.allocPrint(self.allocator, "Bearer {s}", .{self.access_token});
        defer self.allocator.free(authorization);

        const headers = [_]std.http.Header{
            .{ .name = "Authorization", .value = authorization },
            .{ .name = "Content-Type", .value = "application/json" },
            .{ .name = "Accept", .value = "application/json" },
        };

        var response = std.Io.Writer.Allocating.init(self.allocator);
        defer response.deinit();

        const result = try self.client.fetch(.{
            .location = .{ .url = url },
            .method = .GET,
            .response_writer = &response.writer,
            .extra_headers = &headers,
        });
        const status_code: u16 = @intFromEnum(result.status);
        if (status_code < 200 or status_code >= 300) return error.HttpStatusNotOk;
        return try response.toOwnedSlice();
    }

    pub fn getProfiles(self: *ApiClient) !std.json.Parsed(DataResponse([]Profile)) {
        const body = try self.get("/api/v1/profiles");
        defer self.allocator.free(body);
        return parseProfiles(self.allocator, body);
    }

    pub fn getPlays(self: *ApiClient) !std.json.Parsed(Page([]Play)) {
        const body = try self.get("/api/v1/plays");
        defer self.allocator.free(body);
        return parsePlays(self.allocator, body);
    }

    pub fn getTracks(self: *ApiClient) !std.json.Parsed(DataResponse([]TrackInfo)) {
        const body = try self.get("/api/v1/tracks");
        defer self.allocator.free(body);
        return parseTracks(self.allocator, body);
    }

    pub fn getStatus(self: *ApiClient) !std.json.Parsed(Status) {
        const body = try self.get("/api/status");
        defer self.allocator.free(body);
        return parseStatus(self.allocator, body);
    }
};

pub fn difficultyLabel(diff: []const u8) []const u8 {
    if (std.mem.eql(u8, diff, "easy")) return "Easy";
    if (std.mem.eql(u8, diff, "basic")) return "Basic";
    if (std.mem.eql(u8, diff, "advanced")) return "Advanced";
    if (std.mem.eql(u8, diff, "expert")) return "Expert";
    if (std.mem.eql(u8, diff, "master")) return "Master";
    if (std.mem.eql(u8, diff, "remaster") or std.mem.eql(u8, diff, "re:master")) return "Re:Master";
    if (std.mem.eql(u8, diff, "utage")) return "Utage";
    return diff;
}

test "parse profile and plays responses" {
    const allocator = std.testing.allocator;
    const profiles_json =
        \\{"data":[{"id":7,"name":"Alice","rating":12345,"rating_highest":13000,"level":42,"play_stats":{"total":99},"options":{"icon":{"id":1,"png":"https://example.test/icon.png","webp":""}}}]}
    ;
    var profiles = try parseProfiles(allocator, profiles_json);
    defer profiles.deinit();
    try std.testing.expectEqual(@as(usize, 1), profiles.value.data.len);
    try std.testing.expectEqualStrings("Alice", profiles.value.data[0].name);
    try std.testing.expectEqual(@as(i64, 99), profiles.value.data[0].play_stats.total);

    const plays_json =
        \\{"data":[{"id":1,"achievement_formatted":"100.0000","score_formatted":"1,000,000","rank":"SSS+","full_combo_label":"FC","difficulty_level":{"value":"expert"},"song":{"name":{"en":"Song A"}}}],"links":{"first":"x","last":"x","prev":null,"next":null},"meta":{"current_page":1,"last_page":1,"path":"x","per_page":15,"total":1}}
    ;
    var plays = try parsePlays(allocator, plays_json);
    defer plays.deinit();
    try std.testing.expectEqual(@as(usize, 1), plays.value.data.len);
    try std.testing.expectEqualStrings("Song A", plays.value.data[0].song.name.en);
    try std.testing.expectEqualStrings("Expert", difficultyLabel(plays.value.data[0].difficulty_level.value));
}

package com.github.hutchyben.maifetch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OutputRendererTest {
    @Test
    public void convertsFullWidthNamesWithoutDamagingAscii() {
        assertEquals("ABC 123", OutputRenderer.wideToNormal("ＡＢＣ　１２３"));
        assertEquals("MaiTea", OutputRenderer.wideToNormal("MaiTea"));
    }

    @Test
    public void createsProfileAndRecentScoreLines() {
        MaiteaModels.Profile profile = new MaiteaModels.Profile();
        profile.id = 42;
        profile.name = "ＴＥＳＴ";
        profile.rating = 12345;
        profile.ratingHighest = 13000;
        profile.level = 99;
        profile.playStats = new MaiteaModels.PlayStats();
        profile.playStats.total = 321;

        MaiteaModels.Play play = new MaiteaModels.Play();
        play.song = new MaiteaModels.TrackInfo();
        play.song.name = new MaiteaModels.LocalizedName();
        play.song.name.en = "Song Name";
        play.difficultyLevel = new MaiteaModels.DifficultyLevel();
        play.difficultyLevel.value = "expert";
        play.scoreFormatted = "1,000,000";
        play.achievementFormatted = "100.5000";
        play.rank = "SSS+";
        play.fullComboLabel = "FC";

        List<MaiteaModels.Play> plays = new ArrayList<MaiteaModels.Play>();
        plays.add(play);

        List<String> lines = OutputRenderer.createInfoStrings(profile, plays, 1);

        assertTrue(lines.get(0).contains("TEST"));
        assertTrue(lines.contains(Console.colour("ID") + ": 42"));
        assertTrue(lines.get(7).contains("Song Name"));
        assertTrue(lines.get(8).contains("1,000,000 100.5000%"));
    }
}

package com.github.hutchyben.maifetch;

import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            Config config = Config.load(args);
            MaiteaClient client = new MaiteaClient(config.getAccessToken());
            List<MaiteaModels.Profile> profiles = client.getProfiles();

            if (profiles.isEmpty()) {
                System.out.println("No profiles found");
                return;
            }

            Pager<List<MaiteaModels.Play>> plays = client.getPlays();
            OutputRenderer.output(plays.currentPage(), profiles.get(0), config.getLogoSize(), config.getScoreCount(), System.out);
        } catch (Config.HelpRequestedException e) {
            System.out.println(Config.usage());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

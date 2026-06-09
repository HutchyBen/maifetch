package maifetch;

import maifetch.api.MaiTeaClient;
import maifetch.api.Models;
import maifetch.api.Pager;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Maifetch {
    private Maifetch() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (ConfigLoader.HelpRequested ignored) {
            printHelp();
        } catch (Exception error) {
            System.out.println(error.getMessage());
        }
    }

    static void run(String[] args) throws Exception {
        Config config = ConfigLoader.load(args);
        MaiTeaClient client = new MaiTeaClient(config.getAccessToken());
        List<Models.Profile> profiles = client.getProfiles();
        if (profiles.isEmpty()) {
            System.out.println("No profiles found");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Models.Play>> plays = executor.submit(new Callable<List<Models.Play>>() {
                @Override
                public List<Models.Play> call() throws Exception {
                    Pager<Models.Play> pager = client.getPlays();
                    return pager.currentPage();
                }
            });
            List<Models.Play> currentPage = plays.get(30, TimeUnit.SECONDS);
            output(currentPage, profiles.get(0), config.getLogoSize(), config.getScoreCount());
        } finally {
            executor.shutdownNow();
        }
    }

    static void output(List<Models.Play> plays, Models.Profile profile, int logoSize, int scoreCount) throws Exception {
        List<String> infoLines = OutputFormatter.createInfoLines(profile, plays, scoreCount);
        if (logoSize > 0) {
            List<String> logoLines = AsciiLogo.fromUrl(profile.options.icon.png, logoSize);
            System.out.println(OutputFormatter.renderCombined(infoLines, logoLines, logoSize));
        } else {
            System.out.println(OutputFormatter.renderWithoutLogo(profile, plays, scoreCount));
        }
    }

    private static void printHelp() {
        System.out.println("Usage: maifetch [options]");
        System.out.println("  -a, --access-token TOKEN   token for your MaiTea account");
        System.out.println("  -t TOKEN                   legacy token shortcut");
        System.out.println("  -l, --logo-size SIZE       ASCII logo size; zero or negative disables it");
        System.out.println("  -s, --score-count COUNT    recent scores to display, max 12");
        System.out.println("  -c, --config-file FILE     JSON config file");
    }
}

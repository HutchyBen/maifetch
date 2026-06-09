package maifetch;

import java.io.File;

public final class Config {
    private final String accessToken;
    private final File configFile;
    private final int logoSize;
    private final int scoreCount;

    public Config(String accessToken, File configFile, int logoSize, int scoreCount) {
        this.accessToken = accessToken == null ? "" : accessToken;
        this.configFile = configFile;
        this.logoSize = logoSize;
        this.scoreCount = scoreCount;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public File getConfigFile() {
        return configFile;
    }

    public int getLogoSize() {
        return logoSize;
    }

    public int getScoreCount() {
        return scoreCount;
    }
}

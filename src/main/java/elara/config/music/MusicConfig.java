package elara.config.music;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Page;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.PageLocation;
import elara.config.gui.MusicPlayerPage;

/**
 * Standalone OneConfig mod entry point for the in-game music player.
 * Replaces the former {@code @Page}-based registration inside ElaraConfig so
 * Music Player is recognised as its own module in the OneConfig mod list.
 */
public class MusicConfig extends Config {
    public static MusicConfig INSTANCE;

    @Page(name = "Player", description = "In-game music player", location = PageLocation.TOP)
    public MusicPlayerPage playerPage;

    public MusicConfig() {
        super(new Mod("Music Player", ModType.HUD), "elara/musicplayer.json");

        try {
            MusicPlayerConfig.init();
        } catch (Throwable e) {
            System.err.println("[MusicPlayer] MusicPlayerConfig init failed: " + e);
        }

        try {
            MusicPlayerManager.init();
        } catch (Throwable e) {
            System.err.println("[MusicPlayer] MusicPlayerManager init failed: " + e);
        }

        try {
            this.playerPage = new MusicPlayerPage();
        } catch (Throwable e) {
            System.err.println("[MusicPlayer] MusicPlayerPage init failed: " + e);
        }

        this.initialize();
    }

    public static void init() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new MusicConfig();
            } catch (Throwable t) {
                System.err.println("[MusicPlayer] Failed to initialize MusicConfig: " + t);
                t.printStackTrace();
            }
        }
    }
}
package elara.config.gui;

import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.text.TextInputField;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Font;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.config.ElaraConfig;
import elara.config.NotificationHelper;
import elara.config.music.CacheManager;
import elara.config.music.CoverManager;
import elara.config.music.MusicApi;
import elara.config.music.MusicEngine;
import elara.config.music.MusicListFetcher;
import elara.config.music.MusicPlayerConfig;
import elara.config.music.MusicPlayerManager;
import elara.config.music.Playlist;
import elara.config.music.Song;
import elara.config.music.SongInfo;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayerPage extends Page {
    private static final int CONTENT_WIDTH = 920;
    private static final int LEFT_PADDING = 20;
    private static final int TOP_MARGIN = 40;
    private final ArrayList<BasicButton> tabButtons = new ArrayList();
    private String selectedTab = "Player";
    private String prevTab = "Player";
    private int totalSize = 728;
    private float savedScroll = 0.0f;
    public static boolean hudShowCover = true;
    public static boolean hudShowSpectrum = true;
    public static boolean hudShowProgress = true;
    public static boolean hudHideWhenNotPlaying = false;
    public static float hudScale = 1.0f;
    public static float hudPosX = 0.0f;
    public static float hudPosY = 0.0f;
    private static final long FRAME_INTERVAL_MS = 16L;
    private long lastUpdateTime = 0L;
    private float cachedProgress = 0.0f;
    private float smoothedProgress = 0.0f;
    private int cachedPosition = 0;
    private int cachedDuration = 0;
    private String cachedTitle = "No song loaded";
    private String cachedArtist = "\u2014";
    private float[] cachedSpectrum = new float[24];
    private float[] smoothedSpectrum = new float[24];
    private boolean cachedIsPlaying = false;
    private float cachedVolume = 0.7f;
    private boolean cachedIsDownloading = false;
    private float cachedDownloadProgress = 0.0f;
    private final HashMap<String, Float> textWidthCache = new HashMap();
    private static final int TEXT_CACHE_MAX = 256;
    private long cachedCacheCount = 0L;
    private long cachedCacheSize = 0L;
    private long lastCacheUpdateTime = 0L;
    private static final long CACHE_UPDATE_INTERVAL_MS = 1000L;
    private float tabAnimProgress = 1.0f;
    private long tabAnimStartTime = 0L;
    private static final long TAB_ANIM_DURATION_MS = 220L;
    private long listAnimStartTime = 0L;
    private static final long LIST_ANIM_DURATION_MS = 300L;
    private float lyricCenterAnim = 0.0f;
    private int lastLyricIdx = -1;
    private final HashMap<String, Float> toggleAnimState = new HashMap();
    private boolean draggingProgress = false;
    private float dragProgress = 0.0f;
    private boolean mouseWasDownProgress = false;
    private boolean draggingVolume = false;
    private boolean mouseWasDownVolume = false;
    private boolean draggingScale = false;
    private boolean mouseWasDownScale = false;
    private final List<BasicButton> controlButtons = new ArrayList<BasicButton>();
    private final BasicButton refreshBtn = new BasicButton(140, 36, "Refresh", 2, ColorPalette.SECONDARY);
    private final BasicButton clearBtn = new BasicButton(120, 36, "Clear", 2, ColorPalette.SECONDARY);
    private final BasicButton openFolderBtn = new BasicButton(160, 36, "Open Folder", 2, ColorPalette.SECONDARY);
    private final BasicButton resetPosBtn = new BasicButton(180, 36, "Reset Position", 2, ColorPalette.SECONDARY);
    private final BasicButton openCacheDirBtn = new BasicButton(160, 36, "Open Cache Dir", 2, ColorPalette.SECONDARY);
    private final BasicButton clearCacheBtn = new BasicButton(140, 36, "Clear Cache", 2, ColorPalette.SECONDARY);
    private final BasicButton searchBtn = new BasicButton(100, 32, "Search", 2, ColorPalette.PRIMARY);
    private final BasicButton hotSongsBtn = new BasicButton(140, 32, "Hot Songs", 2, ColorPalette.SECONDARY);
    private final BasicButton refreshOnlineBtn = new BasicButton(100, 32, "Refresh", 2, ColorPalette.SECONDARY);
    private final BasicButton loginBtn = new BasicButton(100, 32, "Login", 2, ColorPalette.PRIMARY);
    private final TextInputField searchInput = new TextInputField(500, 32, "Search songs...", false, false);
    private List<SongInfo> onlineSongList = new ArrayList<SongInfo>();
    private boolean isOnlineLoading = false;
    private int onlineSelectedIndex = -1;
    private String lastSearchKeyword = "";
    private volatile String apiStatus = "";
    private boolean apiChecked = false;
    private OnlineView onlineView = OnlineView.SEARCH;
    private volatile boolean userInfoLoaded = false;
    private volatile String userNickname = "";
    private volatile String userAvatarUrl = "";
    private volatile int userLevel = 0;
    private volatile int userListenSongs = 0;
    private volatile int userPlaylistCount = 0;
    private volatile int userFollows = 0;
    private volatile int userFolloweds = 0;
    private volatile String userAvatarPath = null;
    private volatile boolean loadingUserInfo = false;
    private List<MusicApi.PlaylistInfo> userPlaylists = new ArrayList<MusicApi.PlaylistInfo>();
    private boolean loadingPlaylists = false;
    private MusicApi.PlaylistInfo currentPlaylist = null;
    private volatile String currentPlaylistCoverPath = null;
    private volatile boolean loadingPlaylistDetail = false;
    private List<SongInfo> playlistSongList = new ArrayList<SongInfo>();
    private final BasicButton searchTabBtn = new BasicButton(100, 32, "Search", 2, ColorPalette.SECONDARY);
    private final BasicButton playlistsTabBtn = new BasicButton(110, 32, "Playlists", 2, ColorPalette.SECONDARY);
    private final BasicButton backToPlaylistsBtn = new BasicButton(100, 32, "\u2190 Back", 2, ColorPalette.SECONDARY);
    private final BasicButton playAllBtn = new BasicButton(120, 36, "Play All", 2, ColorPalette.PRIMARY);
    private boolean showQrOverlay = false;
    private boolean qrLoading = false;
    private String qrImagePath = null;
    private String qrLoginStatus = "";
    private boolean qrChecking = false;
    private boolean qrLoginSuccess = false;
    private final AtomicBoolean qrShouldStop = new AtomicBoolean(false);
    private ScheduledExecutorService qrScheduler;
    private final BasicButton qrCloseBtn = new BasicButton(120, 36, "Close", 2, ColorPalette.SECONDARY);
    private final BasicButton qrRetryBtn = new BasicButton(120, 36, "Retry", 2, ColorPalette.PRIMARY);

    public MusicPlayerPage() {
        super("Music Player");
        this.syncHudFromConfig();
        this.buildTabButtons();
        this.buildControlButtons();
        this.searchBtn.setClickAction(this::performOnlineSearch);
        this.hotSongsBtn.setClickAction(this::loadHotSongs);
        this.refreshOnlineBtn.setClickAction(this::refreshOnlineData);
        this.loginBtn.setClickAction(this::openLoginScreen);
        this.searchTabBtn.setClickAction(() -> this.switchOnlineView(OnlineView.SEARCH));
        this.playlistsTabBtn.setClickAction(() -> this.switchOnlineView(OnlineView.PLAYLISTS));
        this.backToPlaylistsBtn.setClickAction(() -> this.switchOnlineView(OnlineView.PLAYLISTS));
        this.playAllBtn.setClickAction(this::playAllPlaylistSongs);
    }

    private void syncHudFromConfig() {
        hudShowCover = MusicPlayerConfig.hudShowCover();
        hudShowSpectrum = MusicPlayerConfig.hudShowSpectrum();
        hudShowProgress = MusicPlayerConfig.hudShowProgress();
        hudHideWhenNotPlaying = MusicPlayerConfig.hudHideWhenNotPlaying();
        hudScale = MusicPlayerConfig.hudScale();
        hudPosX = MusicPlayerConfig.hudPosX();
        hudPosY = MusicPlayerConfig.hudPosY();
    }

    private void saveHudToConfig() {
        MusicPlayerConfig.saveHudSettings(hudShowCover, hudShowSpectrum, hudShowProgress, hudHideWhenNotPlaying, hudScale, hudPosX, hudPosY);
    }

    private void buildTabButtons() {
        this.tabButtons.clear();
        for (String tab : new String[]{"Player", "Playlist", "Online", "Settings"}) {
            BasicButton btn = new BasicButton(140, 36, tab, 2, ColorPalette.SECONDARY);
            btn.setToggleable(true);
            btn.setToggled(tab.equals(this.selectedTab));
            btn.setClickAction(() -> {
                if (!this.selectedTab.equals(tab)) {
                    this.prevTab = this.selectedTab;
                    this.selectedTab = tab;
                    this.tabAnimStartTime = System.currentTimeMillis();
                    this.tabAnimProgress = 0.0f;
                    if ("Online".equals(tab) || "Playlist".equals(tab)) {
                        this.listAnimStartTime = System.currentTimeMillis();
                    }
                }
                for (BasicButton b : this.tabButtons) {
                    b.setToggled(b.getText().equals(this.selectedTab));
                }
                this.scrollTarget = 0.0f;
                this.scrollAnimation = null;
            });
            this.tabButtons.add(btn);
        }
    }

    private void buildControlButtons() {
        String[] names;
        this.controlButtons.clear();
        for (String name : names = new String[]{"Prev", "Play", "Next"}) {
            BasicButton btn = new BasicButton(120, 40, name, 2, ColorPalette.PRIMARY);
            btn.setClickAction(() -> {});
            this.controlButtons.add(btn);
        }
    }

    private void updateCachedData() {
        boolean shouldUpdate;
        long now = System.currentTimeMillis();
        boolean bl = shouldUpdate = now - this.lastUpdateTime >= 16L;
        if (shouldUpdate) {
            this.lastUpdateTime = now;
        }
        for (int i = 0; i < this.smoothedSpectrum.length; ++i) {
            int n = i;
            this.smoothedSpectrum[n] = this.smoothedSpectrum[n] + (this.cachedSpectrum[i] - this.smoothedSpectrum[i]) * 0.25f;
        }
        if (!this.draggingProgress) {
            float diff = this.cachedProgress - this.smoothedProgress;
            this.smoothedProgress = Math.abs(diff) > 1.0E-4f ? (this.smoothedProgress += diff * 0.3f) : this.cachedProgress;
        }
        if (!shouldUpdate) {
            return;
        }
        MusicEngine engine = MusicPlayerManager.getEngine();
        if (engine != null) {
            this.cachedProgress = engine.getProgress();
            this.cachedPosition = engine.getPosition();
            this.cachedDuration = engine.getDuration();
            this.cachedTitle = engine.getTitle();
            this.cachedArtist = engine.getArtist();
            this.cachedIsPlaying = engine.isPlaying();
            this.cachedVolume = engine.getVolume();
            this.cachedIsDownloading = engine.isDownloading();
            this.cachedDownloadProgress = engine.getDownloadProgress();
            float[] spec = engine.getSpectrum();
            System.arraycopy(spec, 0, this.cachedSpectrum, 0, Math.min(spec.length, this.cachedSpectrum.length));
        } else {
            this.cachedProgress = 0.0f;
            this.cachedPosition = 0;
            this.cachedDuration = 0;
            this.cachedTitle = "No song loaded";
            this.cachedArtist = "\u2014";
            this.cachedIsPlaying = false;
            this.cachedVolume = 0.7f;
            this.cachedIsDownloading = false;
            this.cachedDownloadProgress = 0.0f;
        }
    }

    private float centerTextY(float boxY, float boxH, float fontSize, Font font) {
        return boxY + (boxH - fontSize) / 2.0f + fontSize * 0.75f;
    }

    private float tw(long vg, String text, float fontSize, Font font) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        String key = text + "|" + fontSize + "|" + font.hashCode();
        Float cached = this.textWidthCache.get(key);
        if (cached != null) {
            return cached.floatValue();
        }
        float w = NanoVGHelper.INSTANCE.getTextWidth(vg, text, fontSize, font);
        if (this.textWidthCache.size() < 256) {
            this.textWidthCache.put(key, Float.valueOf(w));
        }
        return w;
    }

    private float easeOutCubic(float t) {
        float f = 1.0f - t;
        return 1.0f - f * f * f;
    }

    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        this.updateCachedData();
        if (this.tabAnimProgress < 1.0f) {
            long elapsed = System.currentTimeMillis() - this.tabAnimStartTime;
            this.tabAnimProgress = Math.min(1.0f, (float)elapsed / 220.0f);
        }
        float eased = this.easeOutCubic(this.tabAnimProgress);
        int animYOffset = (int)((1.0f - eased) * 10.0f);
        int drawY = y + animYOffset;
        if ("Player".equals(this.selectedTab)) {
            this.drawPlayerTab(vg, x, drawY, inputHandler);
        } else if ("Playlist".equals(this.selectedTab)) {
            this.drawPlaylistTab(vg, x, drawY, inputHandler);
        } else if ("Online".equals(this.selectedTab)) {
            this.drawOnlineTab(vg, x, drawY, inputHandler);
        } else {
            this.drawSettingsTab(vg, x, drawY, inputHandler);
        }
        if (this.showQrOverlay) {
            this.drawQrOverlay(vg, x, y, inputHandler);
        }
    }

    private void drawPlayerTab(long vg, int x, int y, InputHandler inputHandler) {
        float volDisplay;
        int repeatColor;
        String repeatLabel;
        float displayProgress;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        MusicEngine engine = MusicPlayerManager.getEngine();
        int cy = y + 40 + 32;
        int contentX = x + 20;

        // ---- Hero card ----
        int cardX = contentX;
        int cardW = 920;
        int cardY = cy;
        int cardH = 264;
        nvg.drawRoundedRect(vg, (float)cardX, (float)cardY, (float)cardW, (float)cardH, ElaraColors.gray800Alpha(220), 18.0f);
        nvg.drawRoundedRect(vg, (float)cardX, (float)cardY, 4.0f, (float)cardH, ElaraColors.accent(), 2.0f);

        int COVER_SIZE = 200;
        int COVER_X = cardX + 28;
        int COVER_Y = cardY + 32;
        String coverPath = null;
        if (engine != null && engine.getCurrentSong() != null) {
            coverPath = CoverManager.getCoverPath(engine.getCurrentSong());
        }
        if (coverPath != null) {
            nvg.drawRoundImage(vg, coverPath, (float)COVER_X, (float)COVER_Y, 200.0f, 200.0f, 16.0f, null);
        } else {
            nvg.drawRoundedRect(vg, (float)COVER_X, (float)COVER_Y, 200.0f, 200.0f, ElaraColors.GRAY_800, 16.0f);
            float fontSize = 26.0f;
            float musicW = this.tw(vg, "Music", fontSize, Fonts.MEDIUM);
            float ty = this.centerTextY(COVER_Y, 200.0f, fontSize, Fonts.MEDIUM);
            nvg.drawText(vg, "Music", (float)COVER_X + (200.0f - musicW) / 2.0f, ty, this.cachedIsPlaying ? ElaraColors.accent() : ElaraColors.GRAY_300, fontSize, Fonts.MEDIUM);
        }
        if (this.cachedIsPlaying) {
            nvg.drawHollowRoundRect(vg, (float)(COVER_X - 4), (float)(COVER_Y - 4), 208.0f, 208.0f, ElaraColors.accent(), 20.0f, 2.0f);
        }

        int INFO_X = COVER_X + COVER_SIZE + 36;
        int INFO_W = cardX + cardW - INFO_X - 28;

        String np = "NOW PLAYING";
        float npW = this.tw(vg, np, 11.0f, Fonts.BOLD);
        nvg.drawRoundedRect(vg, (float)INFO_X, (float)(cardY + 34), npW + 22.0f, 22.0f, ElaraColors.accentDim(), 11.0f);
        nvg.drawText(vg, np, (float)(INFO_X + 11), this.centerTextY(cardY + 34, 22.0f, 11.0f, Fonts.BOLD), ElaraColors.WHITE, 11.0f, Fonts.BOLD);

        if (this.cachedIsDownloading) {
            String bufferingText = "Buffering... " + (int)(this.cachedDownloadProgress * 100.0f) + "%";
            float bw = this.tw(vg, bufferingText, 12.0f, Fonts.MEDIUM);
            nvg.drawText(vg, bufferingText, (float)(cardX + cardW - 28) - bw, (float)(cardY + 40), ElaraColors.accent(), 12.0f, Fonts.MEDIUM);
        }

        String title = MusicLayout.truncByWidth(vg, this.cachedTitle, 26.0f, Fonts.BOLD, (float)INFO_W, this.textWidthCache);
        nvg.drawText(vg, title, (float)INFO_X, (float)(cardY + 88), -1, 26.0f, Fonts.BOLD);
        String artist = MusicLayout.truncByWidth(vg, this.cachedArtist, 15.0f, Fonts.MEDIUM, (float)INFO_W, this.textWidthCache);
        nvg.drawText(vg, artist, (float)INFO_X, (float)(cardY + 122), ElaraColors.white60(), 15.0f, Fonts.MEDIUM);

        int BAR_Y = cardY + 156;
        int BAR_W = INFO_W;
        int BAR_H = 6;
        boolean barHovered = inputHandler.isAreaHovered((float)INFO_X, (float)(BAR_Y - 8), (float)BAR_W, 22.0f);
        boolean barMouseDown = Platform.getMousePlatform().isButtonDown(0);
        if (barHovered && barMouseDown && !this.mouseWasDownProgress && this.cachedDuration > 0) {
            this.draggingProgress = true;
        }
        if (this.draggingProgress && !barMouseDown) {
            if (engine != null && this.currentSongExists(engine)) {
                engine.seek(this.dragProgress);
            }
            this.draggingProgress = false;
        }
        this.mouseWasDownProgress = barMouseDown;
        if (this.draggingProgress) {
            this.dragProgress = (inputHandler.mouseX() - (float)INFO_X) / (float)BAR_W;
            displayProgress = this.dragProgress = Math.max(0.0f, Math.min(1.0f, this.dragProgress));
        } else {
            displayProgress = this.smoothedProgress;
        }
        int displayPos = this.draggingProgress ? (int)(displayProgress * (float)this.cachedDuration) : this.cachedPosition;
        String posStr = this.formatTime(displayPos);
        String durStr = this.formatTime(this.cachedDuration);
        nvg.drawRoundedRect(vg, (float)INFO_X, (float)BAR_Y, (float)BAR_W, 6.0f, ElaraColors.GRAY_700, 3.0f);
        if (displayProgress > 0.0f) {
            nvg.drawRoundedRect(vg, (float)INFO_X, (float)(BAR_Y - 1), (float)BAR_W * displayProgress, 8.0f, ElaraColors.accent(), 4.0f);
        }
        if (this.draggingProgress || barHovered) {
            float knobX = (float)INFO_X + (float)BAR_W * displayProgress - 12.0f;
            nvg.drawRoundedRect(vg, knobX, (float)(BAR_Y - 9), 24.0f, 24.0f, -1, 12.0f);
        }
        float timeY = this.centerTextY(BAR_Y + 6 + 8, 24.0f, 12.0f, Fonts.MEDIUM);
        nvg.drawText(vg, posStr, (float)INFO_X, timeY, ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
        float durW = this.tw(vg, durStr, 12.0f, Fonts.MEDIUM);
        nvg.drawText(vg, durStr, (float)(INFO_X + BAR_W) - durW, timeY, ElaraColors.white60(), 12.0f, Fonts.MEDIUM);

        int btnY = cardY + 190;
        int btnX = INFO_X;
        int btnGap = 14;
        int BTN_H = 40;
        BasicButton prevBtn = this.controlButtons.get(0);
        prevBtn.draw(vg, (float)btnX, (float)btnY, inputHandler);
        if (this.isButtonClicked(inputHandler, btnX, btnY, prevBtn.getWidth(), BTN_H) && engine != null) {
            engine.previous();
        }
        BasicButton playBtn = this.controlButtons.get(1);
        playBtn.setText(this.cachedIsPlaying ? "Pause" : "Play");
        playBtn.draw(vg, (float)(btnX += prevBtn.getWidth() + btnGap), (float)btnY, inputHandler);
        if (this.isButtonClicked(inputHandler, btnX, btnY, playBtn.getWidth(), BTN_H) && engine != null) {
            engine.togglePlay();
        }
        BasicButton nextBtn = this.controlButtons.get(2);
        nextBtn.draw(vg, (float)(btnX += playBtn.getWidth() + btnGap), (float)btnY, inputHandler);
        if (this.isButtonClicked(inputHandler, btnX, btnY, nextBtn.getWidth(), BTN_H) && engine != null) {
            engine.next();
        }
        btnX += nextBtn.getWidth() + btnGap;
        if (engine != null) {
            switch (engine.getRepeatMode()) {
                case ONE: {
                    repeatLabel = "Repeat 1";
                    repeatColor = ElaraColors.accent();
                    break;
                }
                case ALL: {
                    repeatLabel = "Repeat All";
                    repeatColor = ElaraColors.accent();
                    break;
                }
                case SHUFFLE: {
                    repeatLabel = "Shuffle";
                    repeatColor = ElaraColors.accent();
                    break;
                }
                default: {
                    repeatLabel = "No Repeat";
                    repeatColor = ElaraColors.white60();
                    break;
                }
            }
        } else {
            repeatLabel = "No Repeat";
            repeatColor = ElaraColors.white60();
        }
        float repeatLabelW = this.tw(vg, repeatLabel, 12.0f, Fonts.MEDIUM);
        int repeatBtnW = (int)repeatLabelW + 24;
        int repeatBtnH = 32;
        int repeatBtnY = btnY + (BTN_H - repeatBtnH) / 2;
        boolean repeatHovered = inputHandler.isAreaHovered((float)btnX, (float)repeatBtnY, (float)repeatBtnW, (float)repeatBtnH);
        nvg.drawRoundedRect(vg, (float)btnX, (float)repeatBtnY, (float)repeatBtnW, (float)repeatBtnH, repeatHovered ? ElaraColors.GRAY_700 : ElaraColors.GRAY_800, 14.0f);
        if (repeatHovered && repeatColor == ElaraColors.white60()) {
            repeatColor = ElaraColors.white90();
        }
        nvg.drawText(vg, repeatLabel, (float)(btnX + 12), this.centerTextY(repeatBtnY, repeatBtnH, 12.0f, Fonts.MEDIUM), repeatColor, 12.0f, Fonts.MEDIUM);
        if (this.isButtonClicked(inputHandler, btnX, repeatBtnY, repeatBtnW, repeatBtnH) && engine != null) {
            engine.cycleRepeatMode();
        }

        int volRowY = cardY + cardH + 18;
        float volLabelW = this.tw(vg, "Volume", 13.0f, Fonts.MEDIUM);
        nvg.drawText(vg, "Volume", (float)contentX, (float)(volRowY + 8), ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
        int volBarX = contentX + (int)volLabelW + 16;
        int volBarW = 920 - (int)volLabelW - 16 - 45;
        int volBarY = volRowY + 4;
        boolean volHovered = inputHandler.isAreaHovered((float)(volBarX - 12), (float)(volBarY - 12), (float)(volBarW + 24), 30.0f);
        boolean volMouseDown = Platform.getMousePlatform().isButtonDown(0);
        if (volHovered && volMouseDown && !this.mouseWasDownVolume) {
            this.draggingVolume = true;
        }
        if (this.draggingVolume && !volMouseDown) {
            this.draggingVolume = false;
        }
        this.mouseWasDownVolume = volMouseDown;
        if (this.draggingVolume) {
            float newVol = (inputHandler.mouseX() - (float)volBarX) / (float)volBarW;
            volDisplay = Math.max(0.0f, Math.min(1.0f, newVol));
            if (engine != null) {
                engine.setVolume(volDisplay);
            }
        } else {
            volDisplay = this.cachedVolume;
        }
        nvg.drawRoundedRect(vg, (float)volBarX, (float)volBarY, (float)volBarW, 6.0f, ElaraColors.GRAY_700, 3.0f);
        nvg.drawRoundedRect(vg, (float)volBarX, (float)(volBarY - 1), (float)volBarW * volDisplay, 8.0f, ElaraColors.accent(), 4.0f);
        String volStr = (int)(volDisplay * 100.0f) + "%";
        float volTextW = this.tw(vg, volStr, 12.0f, Fonts.MEDIUM);
        nvg.drawText(vg, volStr, (float)(contentX + 920) - volTextW, (float)(volRowY + 8), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);

        this.drawSpectrum(vg, contentX, volRowY + 56, 920, 90);
        this.drawLyricsPanel(vg, contentX, volRowY + 170, 920, inputHandler, engine);
        this.totalSize = volRowY + 170 + 200 + 40 - y;
    }

    private void drawLyricsPanel(long vg, int x, int y, int width, InputHandler inputHandler, MusicEngine engine) {
        List<MusicEngine.LyricLine> lyrics;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawText(vg, "LYRICS", (float)x, (float)y, ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        int panelH = 180;
        nvg.drawRoundedRect(vg, (float)x, (float)(y += 20), (float)width, (float)panelH, ElaraColors.GRAY_800, 8.0f);
        List<MusicEngine.LyricLine> list = lyrics = engine != null ? engine.getLyrics() : null;
        if (lyrics == null || lyrics.isEmpty()) {
            String msg = engine != null && engine.isPlaying() ? "No lyrics available" : "Play a song to see lyrics";
            float msgW = this.tw(vg, msg, 14.0f, Fonts.MEDIUM);
            nvg.drawText(vg, msg, (float)x + ((float)width - msgW) / 2.0f, (float)y + (float)panelH / 2.0f, ElaraColors.GRAY_300, 14.0f, Fonts.MEDIUM);
            return;
        }
        int currentIdx = engine.getCurrentLyricIndex();
        if (currentIdx < 0) {
            currentIdx = 0;
        }
        int currentTs = lyrics.get(currentIdx).timeMs;
        int firstSame = currentIdx;
        int lastSame = currentIdx;
        while (firstSame > 0 && lyrics.get(firstSame - 1).timeMs == currentTs) {
            firstSame--;
        }
        while (lastSame < lyrics.size() - 1 && lyrics.get(lastSame + 1).timeMs == currentTs) {
            lastSame++;
        }
        float targetCenter = (float)(firstSame + lastSame) / 2.0f;
        if (currentIdx != this.lastLyricIdx && Math.abs(targetCenter - this.lyricCenterAnim) > 1.0f) {
            this.lyricCenterAnim = targetCenter;
        }
        this.lastLyricIdx = currentIdx;
        this.lyricCenterAnim += (targetCenter - this.lyricCenterAnim) * 0.15f;
        float lineSpacing = 26.0f;
        float centerY = (float)y + (float)panelH / 2.0f - 4.0f;
        float maxW = width - 48;
        int range = 4;
        int startIdx = Math.max(0, (int)this.lyricCenterAnim - range);
        int endIdx = Math.min(lyrics.size(), (int)this.lyricCenterAnim + range + 1);
        for (int i = startIdx; i < endIdx; ++i) {
            int baseColor;
            float dist;
            float alpha;
            float offset = (float)i - this.lyricCenterAnim;
            float lineY = centerY + offset * lineSpacing;
            if (lineY < (float)y - lineSpacing || lineY > (float)(y + panelH) + lineSpacing || (alpha = Math.max(0.0f, 1.0f - (dist = Math.abs(offset)) * 0.28f)) < 0.02f) continue;
            float fontSize = 16.0f - dist * 1.2f;
            if (fontSize < 11.0f) {
                fontSize = 11.0f;
            }
            MusicEngine.LyricLine line = lyrics.get(i);
            boolean isCurrent = line.timeMs == currentTs;
            int n = baseColor = isCurrent ? ElaraColors.accent() : ElaraColors.WHITE;
            if (isCurrent) {
                float pulse = 0.85f + 0.15f * (float)Math.sin((double)System.currentTimeMillis() / 400.0);
                alpha *= pulse;
            }
            String text = line.text;
            float textW = this.tw(vg, text, fontSize, isCurrent ? Fonts.BOLD : Fonts.MEDIUM);
            if (textW > maxW) {
                while (text.length() > 3) {
                    String string = text + "...";
                    Font font = isCurrent ? Fonts.BOLD : Fonts.MEDIUM;
                    if (!(this.tw(vg, string, fontSize, font) > maxW)) break;
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "...";
                textW = this.tw(vg, text, fontSize, isCurrent ? Fonts.BOLD : Fonts.MEDIUM);
            }
            float textX = (float)x + ((float)width - textW) / 2.0f;
            int a = (int)(alpha * 255.0f);
            int finalColor = a << 24 | baseColor & 0xFFFFFF;
            nvg.drawText(vg, text, textX, lineY, finalColor, fontSize, isCurrent ? Fonts.BOLD : Fonts.MEDIUM);
        }
    }

    private void drawSpectrum(long vg, int x, int y, int width, int height) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawText(vg, "SPECTRUM", (float)x, (float)(y - 8), ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        int bars = 24;
        float totalGap = (float)width * 0.3f;
        float gap = totalGap / (float)(bars - 1);
        float barWidth = ((float)width - totalGap) / (float)bars;
        for (int i = 0; i < bars; ++i) {
            float h = Math.min(this.smoothedSpectrum[i] * (float)height, (float)height);
            float bx = (float)x + (float)i * (barWidth + gap);
            nvg.drawRoundedRect(vg, bx, (float)(y + height) - h, barWidth, Math.max(h, 2.0f), ElaraColors.accent(), 1.0f);
        }
    }

    private void drawPlaylistTab(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        Playlist playlist = MusicPlayerManager.getPlaylist();
        int cy = y + 40 + 32;
        int contentX = x + 20;
        int songCount = playlist != null ? playlist.size() : 0;
        nvg.drawText(vg, "PLAYLIST", (float)contentX, (float)cy, ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        nvg.drawText(vg, songCount + " songs", (float)contentX, (float)(cy + 28), -1, 20.0f, Fonts.BOLD);
        String folderStr = "./config/Elara/music/Local/ (one song per folder)";
        float folderW = this.tw(vg, folderStr, 12.0f, Fonts.MEDIUM);
        nvg.drawText(vg, folderStr, (float)(contentX + 920) - folderW, (float)(cy + 32), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
        nvg.drawLine(vg, (float)contentX, (float)(cy + 56), (float)(contentX + 920), (float)(cy + 56), 1.0f, ElaraColors.GRAY_600);
        int headerH = 24;
        float headerY = this.centerTextY(cy += 72, headerH, 11.0f, Fonts.BOLD);
        nvg.drawText(vg, "#", (float)contentX, headerY, ElaraColors.white60(), 11.0f, Fonts.BOLD);
        nvg.drawText(vg, "TITLE", (float)(contentX + 48), headerY, ElaraColors.white60(), 11.0f, Fonts.BOLD);
        nvg.drawText(vg, "ARTIST", (float)(contentX + 460), headerY, ElaraColors.white60(), 11.0f, Fonts.BOLD);
        String durHead = "DURATION";
        float durHeadW = this.tw(vg, durHead, 11.0f, Fonts.MEDIUM);
        nvg.drawText(vg, durHead, (float)(contentX + 920) - durHeadW, headerY, ElaraColors.white60(), 11.0f, Fonts.BOLD);
        nvg.drawLine(vg, (float)contentX, (float)(cy += headerH + 4), (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_700);
        cy += 10;
        if (playlist == null || playlist.getSongs().isEmpty()) {
            nvg.drawText(vg, "No songs found.", (float)contentX, (float)(cy + 28), ElaraColors.white60(), 16.0f, Fonts.MEDIUM);
            nvg.drawText(vg, "Put .mp3 files in ./config/Elara/music/ and click Refresh.", (float)contentX, (float)(cy + 56), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
            cy += 96;
        } else {
            MusicEngine engine = MusicPlayerManager.getEngine();
            int idx = 0;
            int ROW_HEIGHT = 52;
            for (Song song : playlist.getSongs()) {
                if ((float)(cy + 52) >= (float)y - this.scroll && (float)cy <= (float)(y + 728) - this.scroll) {
                    boolean isCurrent = engine != null && engine.getCurrentSong() == song;
                    float textY = this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM);
                    if (isCurrent) {
                        nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 920.0f, 48.0f, ElaraColors.GRAY_700, 8.0f);
                    }
                    String status = isCurrent ? (this.cachedIsPlaying ? "\u25b6" : "\u275a\u275a") : String.valueOf(idx + 1);
                    float statusW = this.tw(vg, status, 14.0f, Fonts.MEDIUM);
                    nvg.drawText(vg, status, (float)(contentX + 20) - statusW / 2.0f, textY, isCurrent ? ElaraColors.accent() : ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
                    String title = this.truncate(song.getTitle(), 38);
                    nvg.drawText(vg, title, (float)(contentX + 60), textY, isCurrent ? ElaraColors.WHITE : ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
                    String artist = this.truncate(song.getArtist(), 28);
                    nvg.drawText(vg, artist, (float)(contentX + 460), textY, ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
                    String dur = song.getFormattedDuration();
                    float durW = this.tw(vg, dur, 13.0f, Fonts.MEDIUM);
                    float durY = this.centerTextY(cy, 48.0f, 13.0f, Fonts.MEDIUM);
                    nvg.drawText(vg, dur, (float)(contentX + 920) - durW, durY, ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
                    if (inputHandler.isClicked() && inputHandler.mouseX() >= (float)contentX && inputHandler.mouseX() <= (float)(contentX + 920) && inputHandler.mouseY() >= (float)cy && inputHandler.mouseY() <= (float)(cy + 52 - 4) && engine != null) {
                        if (isCurrent) {
                            engine.togglePlay();
                        } else {
                            engine.play(song);
                        }
                    }
                }
                cy += 52;
                ++idx;
            }
        }
        this.refreshBtn.draw(vg, (float)contentX, (float)(cy += 20), inputHandler);
        if (this.isButtonClicked(inputHandler, contentX, cy, this.refreshBtn.getWidth(), 36.0f) && playlist != null) {
            playlist.refresh();
        }
        this.clearBtn.draw(vg, (float)(contentX + this.refreshBtn.getWidth() + 12), (float)cy, inputHandler);
        if (this.isButtonClicked(inputHandler, contentX + this.refreshBtn.getWidth() + 12, cy, this.clearBtn.getWidth(), 36.0f) && playlist != null) {
            playlist.clear();
        }
        this.openFolderBtn.draw(vg, (float)(contentX + this.refreshBtn.getWidth() + this.clearBtn.getWidth() + 24), (float)cy, inputHandler);
        if (this.isButtonClicked(inputHandler, contentX + this.refreshBtn.getWidth() + this.clearBtn.getWidth() + 24, cy, this.openFolderBtn.getWidth(), 36.0f)) {
            try {
                // Open the Local/ folder — the one-song-per-folder local music
                // root. Fallback to the music/ root if Local/ is unavailable.
                File dir = Playlist.LOCAL_DIR;
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                if (!dir.isDirectory()) {
                    dir = Playlist.MUSIC_DIR;
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                }
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(dir);
                } else {
                    Runtime.getRuntime().exec("explorer.exe \"" + dir.getAbsolutePath() + "\"");
                }
            }
            catch (Exception e) {
                System.err.println("[Elara] Failed to open music folder: " + e.getMessage());
            }
        }
        this.totalSize = cy + 60 - y;
    }

    private void drawSettingsTab(long vg, int x, int y, InputHandler inputHandler) {
        float scaleDisplay;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int cy = y + 40 + 32;
        int contentX = x + 20;
        nvg.drawText(vg, "SETTINGS", (float)contentX, (float)cy, ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        nvg.drawText(vg, "Music Player Settings", (float)contentX, (float)(cy + 24), -1, 18.0f, Fonts.BOLD);
        nvg.drawLine(vg, (float)contentX, (float)(cy + 52), (float)(contentX + 920), (float)(cy + 52), 1.0f, ElaraColors.GRAY_600);
        nvg.drawText(vg, "ONLINE MUSIC", (float)contentX, (float)(cy += 72), ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        float dropdownLabelY = this.centerTextY(cy += 28, 48.0f, 14.0f, Fonts.MEDIUM);
        nvg.drawText(vg, "API Provider", (float)contentX, dropdownLabelY, ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
        MusicPlayerConfig.ApiProvider currentProvider = MusicPlayerConfig.INSTANCE != null ? MusicPlayerConfig.INSTANCE.apiProvider : MusicPlayerConfig.ApiProvider.MUSIC_163_CN;
        String providerText = currentProvider.toString();
        float providerTextW = this.tw(vg, providerText, 14.0f, Fonts.MEDIUM);
        int dropdownX = contentX + 920 - 200;
        int dropdownY = cy + 8;
        int dropdownW = 180;
        int dropdownH = 32;
        boolean dropdownHover = inputHandler.isAreaHovered((float)dropdownX, (float)dropdownY, (float)dropdownW, (float)dropdownH);
        nvg.drawRoundedRect(vg, (float)dropdownX, (float)dropdownY, (float)dropdownW, (float)dropdownH, ElaraColors.GRAY_700, 8.0f);
        nvg.drawText(vg, providerText, (float)dropdownX + ((float)dropdownW - providerTextW) / 2.0f, this.centerTextY(dropdownY, dropdownH, 14.0f, Fonts.MEDIUM), dropdownHover ? ElaraColors.accent() : ElaraColors.WHITE, 14.0f, Fonts.MEDIUM);
        nvg.drawText(vg, "\u25bc", (float)(dropdownX + dropdownW - 24), this.centerTextY(dropdownY, dropdownH, 14.0f, Fonts.MEDIUM), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
        if (dropdownHover && inputHandler.isClicked() && MusicPlayerConfig.INSTANCE != null) {
            MusicPlayerConfig.ApiProvider[] providers = MusicPlayerConfig.ApiProvider.values();
            int currentIndex = 0;
            for (int i = 0; i < providers.length; ++i) {
                if (providers[i] != currentProvider) continue;
                currentIndex = i;
                break;
            }
            int nextIndex = (currentIndex + 1) % providers.length;
            MusicPlayerConfig.INSTANCE.apiProvider = providers[nextIndex];
            MusicPlayerConfig.INSTANCE.save();
        }
        cy += 56;
        if (MusicPlayerConfig.INSTANCE != null && MusicPlayerConfig.INSTANCE.apiProvider == MusicPlayerConfig.ApiProvider.CUSTOM) {
            nvg.drawText(vg, "Custom API URL", (float)contentX, this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
            String customUrl = MusicPlayerConfig.INSTANCE.customApiUrl;
            int inputX = contentX + 140;
            int inputY = cy + 8;
            int inputW = 764;
            int inputH = 32;
            nvg.drawRoundedRect(vg, (float)inputX, (float)inputY, (float)inputW, (float)inputH, ElaraColors.GRAY_700, 8.0f);
            nvg.drawText(vg, customUrl.isEmpty() ? "Enter API URL..." : customUrl, (float)(inputX + 12), this.centerTextY(inputY, inputH, 14.0f, Fonts.MEDIUM), customUrl.isEmpty() ? ElaraColors.white30() : ElaraColors.WHITE, 14.0f, Fonts.MEDIUM);
            cy += 56;
        }
        nvg.drawText(vg, "Music Source", (float)contentX, this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
        MusicPlayerConfig.MusicListSource currentSource = MusicPlayerConfig.getMusicListSource();
        String sourceText = currentSource.toString();
        float sourceTextW = this.tw(vg, sourceText, 14.0f, Fonts.MEDIUM);
        int sourceDropdownX = contentX + 920 - 200;
        int sourceDropdownY = cy + 8;
        boolean sourceDropdownHover = inputHandler.isAreaHovered((float)sourceDropdownX, (float)sourceDropdownY, (float)dropdownW, (float)dropdownH);
        nvg.drawRoundedRect(vg, (float)sourceDropdownX, (float)sourceDropdownY, (float)dropdownW, (float)dropdownH, ElaraColors.GRAY_700, 8.0f);
        nvg.drawText(vg, sourceText, (float)sourceDropdownX + ((float)dropdownW - sourceTextW) / 2.0f, this.centerTextY(sourceDropdownY, dropdownH, 14.0f, Fonts.MEDIUM), sourceDropdownHover ? ElaraColors.accent() : ElaraColors.WHITE, 14.0f, Fonts.MEDIUM);
        nvg.drawText(vg, "\u25bc", (float)(sourceDropdownX + dropdownW - 24), this.centerTextY(sourceDropdownY, dropdownH, 14.0f, Fonts.MEDIUM), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
        if (sourceDropdownHover && inputHandler.isClicked()) {
            MusicPlayerConfig.MusicListSource newSource = currentSource == MusicPlayerConfig.MusicListSource.HOT_SONGS ? MusicPlayerConfig.MusicListSource.PERSONALIZED : MusicPlayerConfig.MusicListSource.HOT_SONGS;
            MusicPlayerConfig.setMusicListSource(newSource);
        }
        nvg.drawLine(vg, (float)contentX, (float)(cy += 40), (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
        nvg.drawText(vg, "CACHE", (float)contentX, (float)(cy += 28), ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        this.updateCacheStats();
        CacheManager cacheManager = CacheManager.getInstance();
        long maxCacheSize = MusicPlayerConfig.getMaxCacheSizeBytes();
        String cacheInfo = this.cachedCacheCount + " files \u00b7 " + this.formatCacheSize(this.cachedCacheSize) + " / " + this.formatCacheSize(maxCacheSize);
        nvg.drawText(vg, "Cached Music", (float)contentX, (float)((cy += 28) + 8), ElaraColors.white90(), 15.0f, Fonts.MEDIUM);
        float cacheInfoW = this.tw(vg, cacheInfo, 13.0f, Fonts.MEDIUM);
        nvg.drawText(vg, cacheInfo, (float)(contentX + 920) - cacheInfoW, (float)(cy + 10), ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
        float cacheProgress = maxCacheSize > 0L ? (float)this.cachedCacheSize / (float)maxCacheSize : 0.0f;
        cacheProgress = Math.min(1.0f, Math.max(0.0f, cacheProgress));
        nvg.drawRoundedRect(vg, (float)contentX, (float)(cy + 32), 920.0f, 6.0f, ElaraColors.GRAY_700, 3.0f);
        nvg.drawRoundedRect(vg, (float)contentX, (float)(cy + 31), 920.0f * cacheProgress, 8.0f, ElaraColors.accent(), 4.0f);
        this.openCacheDirBtn.draw(vg, (float)contentX, (float)(cy += 60), inputHandler);
        if (this.isButtonClicked(inputHandler, contentX, cy, this.openCacheDirBtn.getWidth(), 36.0f)) {
            try {
                File cacheDir = cacheManager.getCacheDir();
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                Desktop.getDesktop().open(cacheDir);
            }
            catch (Exception e) {
                System.err.println("[Elara] Failed to open cache dir: " + e.getMessage());
            }
        }
        this.clearCacheBtn.draw(vg, (float)(contentX + this.openCacheDirBtn.getWidth() + 12), (float)cy, inputHandler);
        if (this.isButtonClicked(inputHandler, contentX + this.openCacheDirBtn.getWidth() + 12, cy, this.clearCacheBtn.getWidth(), 36.0f)) {
            cacheManager.clearCache();
        }
        cy += 56;
        nvg.drawLine(vg, (float)contentX, (float)cy, (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
        // ---- HUD DISPLAY ----
        nvg.drawText(vg, "HUD DISPLAY", (float)contentX, (float)(cy += 28), ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        cy += 24;
        this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Show Cover", hudShowCover, () -> { hudShowCover = !hudShowCover; this.saveHudToConfig(); });
        cy += 48;
        this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Show Spectrum", hudShowSpectrum, () -> { hudShowSpectrum = !hudShowSpectrum; this.saveHudToConfig(); });
        cy += 48;
        this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Show Progress Bar", hudShowProgress, () -> { hudShowProgress = !hudShowProgress; this.saveHudToConfig(); });
        cy += 48;
        this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Hide When Not Playing", hudHideWhenNotPlaying, () -> { hudHideWhenNotPlaying = !hudHideWhenNotPlaying; this.saveHudToConfig(); });
        cy += 52;
        // ---- HUD APPEARANCE ----
        nvg.drawLine(vg, (float)contentX, (float)cy, (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
        nvg.drawText(vg, "HUD APPEARANCE", (float)contentX, (float)(cy += 28), ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        cy += 24;
        MusicHud musicHud = this.getMusicHud();
        if (musicHud != null) {
            this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Round Border", musicHud.roundBorder, () -> { musicHud.roundBorder = !musicHud.roundBorder; if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save(); });
            cy += 48;
            // Corner radius slider
            nvg.drawText(vg, "Corner Radius", (float)contentX, this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
            float cr = musicHud.cornerRadius;
            float crLabelW = this.tw(vg, String.format("%.1f", cr), 12.0f, Fonts.MEDIUM);
            nvg.drawText(vg, String.format("%.1f", cr), (float)(contentX + 920) - crLabelW - 16.0f, this.centerTextY(cy, 48.0f, 12.0f, Fonts.MEDIUM), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
            int crBarX = contentX + 160; int crBarW = 920 - 160 - 60; int crBarY = cy + 20;
            nvg.drawRoundedRect(vg, (float)crBarX, (float)crBarY, (float)crBarW, 6.0f, ElaraColors.GRAY_700, 3.0f);
            float crProg = cr / 20.0f;
            nvg.drawRoundedRect(vg, (float)crBarX, (float)(crBarY - 1), (float)crBarW * crProg, 8.0f, ElaraColors.accent(), 4.0f);
            boolean crHover = inputHandler.isAreaHovered((float)(crBarX - 12), (float)(crBarY - 12), (float)(crBarW + 24), 30.0f);
            boolean crDown = Platform.getMousePlatform().isButtonDown(0);
            if (crHover && crDown && !this.draggingScale) { this.draggingScale = true; }
            if (this.draggingScale && !crDown) { this.draggingScale = false; if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save(); }
            if (this.draggingScale) {
                float np = (inputHandler.mouseX() - (float)crBarX) / (float)crBarW;
                musicHud.cornerRadius = Math.max(0.0f, Math.min(20.0f, np * 20.0f));
            }
            this.mouseWasDownScale = crDown;
            cy += 48;
            this.drawHudToggleRow(vg, inputHandler, contentX, cy, "Show Outline", musicHud.showOutline, () -> { musicHud.showOutline = !musicHud.showOutline; if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save(); });
            cy += 48;
            // Outline width slider
            nvg.drawText(vg, "Outline Width", (float)contentX, this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
            float ow = musicHud.outlineWidth;
            float owLabelW = this.tw(vg, String.format("%.1f", ow), 12.0f, Fonts.MEDIUM);
            nvg.drawText(vg, String.format("%.1f", ow), (float)(contentX + 920) - owLabelW - 16.0f, this.centerTextY(cy, 48.0f, 12.0f, Fonts.MEDIUM), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
            int owBarX = contentX + 160; int owBarW = 920 - 160 - 60; int owBarY = cy + 20;
            nvg.drawRoundedRect(vg, (float)owBarX, (float)owBarY, (float)owBarW, 6.0f, ElaraColors.GRAY_700, 3.0f);
            float owProg = (ow - 1.0f) / 4.0f;
            nvg.drawRoundedRect(vg, (float)owBarX, (float)(owBarY - 1), (float)owBarW * owProg, 8.0f, ElaraColors.accent(), 4.0f);
            boolean owHover = inputHandler.isAreaHovered((float)(owBarX - 12), (float)(owBarY - 12), (float)(owBarW + 24), 30.0f);
            boolean owDown = Platform.getMousePlatform().isButtonDown(0);
            if (owHover && owDown && !this.draggingScale) { this.draggingScale = true; }
            if (this.draggingScale && !owDown) { this.draggingScale = false; if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save(); }
            if (this.draggingScale) {
                float np = (inputHandler.mouseX() - (float)owBarX) / (float)owBarW;
                musicHud.outlineWidth = Math.max(1.0f, Math.min(5.0f, 1.0f + np * 4.0f));
            }
            cy += 52;
            // Outline color preview
            nvg.drawText(vg, "Outline Color", (float)contentX, this.centerTextY(cy, 36.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
            int colorSwatchX = contentX + 920 - 48;
            int colorSwatchY = cy + 4;
            int oc = musicHud.outlineColor.getRGB();
            nvg.drawRoundedRect(vg, (float)colorSwatchX, (float)colorSwatchY, 36.0f, 28.0f, oc, 6.0f);
            nvg.drawHollowRoundRect(vg, (float)colorSwatchX, (float)colorSwatchY, 36.0f, 28.0f, ElaraColors.GRAY_600, 6.0f, 1.0f);
            boolean colorHover = inputHandler.isAreaHovered((float)colorSwatchX, (float)colorSwatchY, 36.0f, 28.0f);
            if (colorHover && inputHandler.isClicked()) {
                // Cycle through preset colors
                OneColor[] presets = {
                    new OneColor(90, 200, 250, 255),
                    new OneColor(255, 255, 255, 255),
                    new OneColor(255, 100, 100, 255),
                    new OneColor(100, 255, 100, 255),
                    new OneColor(255, 200, 0, 255),
                    new OneColor(180, 100, 255, 255)
                };
                int curIdx = 0;
                for (int pi = 0; pi < presets.length; pi++) {
                    if (presets[pi].getRGB() == oc) { curIdx = pi; break; }
                }
                musicHud.outlineColor = presets[(curIdx + 1) % presets.length];
                if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save();
            }
            cy += 40;
        }
        // ---- HUD POSITION ----
        nvg.drawLine(vg, (float)contentX, (float)cy, (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
        nvg.drawText(vg, "HUD POSITION", (float)contentX, (float)(cy += 28), ElaraColors.accentDim(), 12.0f, Fonts.BOLD);
        cy += 24;
        // Scale slider
        nvg.drawText(vg, "HUD Scale", (float)contentX, this.centerTextY(cy, 48.0f, 14.0f, Fonts.MEDIUM), ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
        float sc = hudScale;
        float scLabelW = this.tw(vg, String.format("%.2f", sc), 12.0f, Fonts.MEDIUM);
        nvg.drawText(vg, String.format("%.2f", sc), (float)(contentX + 920) - scLabelW - 16.0f, this.centerTextY(cy, 48.0f, 12.0f, Fonts.MEDIUM), ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
        int scBarX = contentX + 160; int scBarW = 920 - 160 - 60; int scBarY = cy + 20;
        nvg.drawRoundedRect(vg, (float)scBarX, (float)scBarY, (float)scBarW, 6.0f, ElaraColors.GRAY_700, 3.0f);
        float scProg = (sc - 0.5f) / 1.5f;
        nvg.drawRoundedRect(vg, (float)scBarX, (float)(scBarY - 1), (float)scBarW * scProg, 8.0f, ElaraColors.accent(), 4.0f);
        boolean scHover = inputHandler.isAreaHovered((float)(scBarX - 12), (float)(scBarY - 12), (float)(scBarW + 24), 30.0f);
        boolean scDown = Platform.getMousePlatform().isButtonDown(0);
        if (scHover && scDown && !this.draggingVolume) { this.draggingVolume = true; }
        if (this.draggingVolume && !scDown) { this.draggingVolume = false; this.saveHudToConfig(); }
        if (this.draggingVolume) {
            float np = (inputHandler.mouseX() - (float)scBarX) / (float)scBarW;
            hudScale = Math.max(0.5f, Math.min(2.0f, 0.5f + np * 1.5f));
        }
        this.mouseWasDownVolume = scDown;
        cy += 48;
        // Reset position button
        this.resetPosBtn.draw(vg, (float)contentX, (float)cy, inputHandler);
        if (this.isButtonClicked(inputHandler, contentX, cy, this.resetPosBtn.getWidth(), 36.0f)) {
            hudScale = 1.0f; hudPosX = 0.0f; hudPosY = 0.0f;
            this.saveHudToConfig();
            MusicHud hud = this.getMusicHud();
            if (hud != null) { hud.resetHudPosition(); hud.setHudScale(1.0f); if (ElaraConfig.INSTANCE != null) ElaraConfig.INSTANCE.save(); }
        }
        cy += 48;
        // HUD preview
        nvg.drawText(vg, "HUD PREVIEW", (float)contentX, (float)(cy += 8), ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        cy += 20;
        this.drawHudPreview(vg, contentX, cy, inputHandler);
        cy += 140;
        this.totalSize = cy + 40 - y;
    }

    private MusicHud getMusicHud() {
        if (ElaraConfig.INSTANCE != null) {
            return ElaraConfig.INSTANCE.musicHud;
        }
        return null;
    }

    private void updateCacheStats() {
        long now = System.currentTimeMillis();
        if (now - this.lastCacheUpdateTime >= 1000L) {
            CacheManager cacheManager = CacheManager.getInstance();
            this.cachedCacheCount = cacheManager.getFileCount();
            this.cachedCacheSize = cacheManager.getCacheSizeBytes();
            this.lastCacheUpdateTime = now;
        }
    }

    private String formatCacheSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 0x100000L) {
            return String.format("%.1f KB", Float.valueOf((float)bytes / 1024.0f));
        }
        if (bytes < 0x40000000L) {
            return String.format("%.1f MB", Float.valueOf((float)bytes / 1048576.0f));
        }
        return String.format("%.2f GB", Float.valueOf((float)bytes / 1.07374182E9f));
    }

    private void drawHudPreview(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, (float)x, (float)y, 360.0f, 120.0f, ElaraColors.gray800Alpha(204), 12.0f);
        if (hudShowCover) {
            nvg.drawRoundedRect(vg, (float)(x + 16), (float)(y + 18), 84.0f, 84.0f, ElaraColors.GRAY_800, 8.0f);
            float noteW = this.tw(vg, "\u266a", 40.0f, Fonts.MEDIUM);
            float ty = this.centerTextY(y + 18, 84.0f, 40.0f, Fonts.BOLD);
            nvg.drawText(vg, "\u266a", (float)(x + 16) + (84.0f - noteW) / 2.0f, ty, ElaraColors.accent(), 40.0f, Fonts.MEDIUM);
        }
        float textX = x + 116;
        nvg.drawText(vg, "Music HUD", textX, (float)(y + 32), -1, 15.0f, Fonts.MEDIUM);
        if (hudShowProgress) {
            nvg.drawRoundedRect(vg, textX, (float)(y + 56), 220.0f, 4.0f, ElaraColors.GRAY_600, 2.0f);
            nvg.drawRoundedRect(vg, textX, (float)(y + 56), 110.0f, 4.0f, ElaraColors.accent(), 2.0f);
            float timeY = this.centerTextY(y + 64, 16.0f, 10.0f, Fonts.MEDIUM);
            nvg.drawText(vg, "1:23", textX, timeY, ElaraColors.white60(), 10.0f, Fonts.MEDIUM);
            String dur = "3:45";
            float durW = this.tw(vg, dur, 10.0f, Fonts.MEDIUM);
            nvg.drawText(vg, dur, textX + 220.0f - durW, timeY, ElaraColors.white60(), 10.0f, Fonts.MEDIUM);
        }
        if (hudShowSpectrum) {
            int bars = 12;
            float specW = 220.0f;
            float barW = specW / (float)bars * 0.7f;
            float gap = specW / (float)bars * 0.3f;
            long t = System.currentTimeMillis() / 100L;
            for (int i = 0; i < bars; ++i) {
                float bh = (float)(Math.sin((double)t * 0.3 + (double)i * 0.5) * 0.3 + 0.5) * 18.0f;
                bh = Math.max(bh, 1.0f);
                float bx = textX + (float)i * (barW + gap);
                nvg.drawRoundedRect(vg, bx, (float)(y + 96) - bh, barW, bh, ElaraColors.accent(), 1.0f);
            }
        }
    }

    private void drawHudToggleRow(long vg, InputHandler inputHandler, int x, int y, String label, boolean enabled, Runnable onClick) {
        int toggleColor;
        float newPos;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int rowH = 48;
        float labelY = this.centerTextY(y, rowH / 2, 14.0f, Fonts.MEDIUM);
        nvg.drawText(vg, label, (float)x, labelY, ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
        float descY = this.centerTextY(y + rowH / 2, rowH / 2, 11.0f, Fonts.MEDIUM);
        nvg.drawText(vg, enabled ? "Enabled" : "Disabled", (float)x, descY, enabled ? ElaraColors.accentDim() : ElaraColors.white30(), 11.0f, Fonts.MEDIUM);
        int toggleW = 42;
        int toggleH = 24;
        int toggleX = x + 920 - toggleW - 16;
        int toggleY = y + (rowH - toggleH) / 2 + 4;
        boolean hover = inputHandler.isAreaHovered((float)toggleX, (float)toggleY, (float)toggleW, (float)toggleH);
        float targetPos = enabled ? 1.0f : 0.0f;
        Float currentPos = this.toggleAnimState.get(label);
        if (currentPos == null) {
            currentPos = Float.valueOf(targetPos);
        }
        if (Math.abs((newPos = currentPos.floatValue() + (targetPos - currentPos.floatValue()) * 0.3f) - targetPos) < 0.01f) {
            newPos = targetPos;
        }
        this.toggleAnimState.put(label, Float.valueOf(newPos));
        int n = toggleColor = enabled ? ColorPalette.PRIMARY.getNormalColor() : ColorPalette.SECONDARY.getNormalColor();
        if (hover) {
            toggleColor = enabled ? ColorPalette.PRIMARY.getHoveredColor() : ColorPalette.SECONDARY.getHoveredColor();
        }
        nvg.drawRoundedRect(vg, (float)toggleX, (float)toggleY, (float)toggleW, (float)toggleH, toggleColor, 12.0f);
        int dotSize = 18;
        int dotOffX = toggleX + 3;
        int dotOnX = toggleX + toggleW - dotSize - 3;
        int dotX = (int)((float)dotOffX + (float)(dotOnX - dotOffX) * newPos);
        int dotY = toggleY + (toggleH - dotSize) / 2;
        nvg.drawRoundedRect(vg, (float)dotX, (float)dotY, (float)dotSize, (float)dotSize, -1, 9.0f);
        if (hover && inputHandler.isClicked()) {
            onClick.run();
        }
    }

    private boolean isButtonClicked(InputHandler inputHandler, float x, float y, float w, float h) {
        return inputHandler.isClicked() && inputHandler.mouseX() >= x && inputHandler.mouseX() <= x + w && inputHandler.mouseY() >= y && inputHandler.mouseY() <= y + h;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "\u2026";
    }

    private boolean currentSongExists(MusicEngine engine) {
        return engine.getCurrentSong() != null;
    }

    private String formatTime(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public int drawStatic(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int iX = x + 16;
        for (BasicButton btn : this.tabButtons) {
            btn.draw(vg, (float)iX, (float)(y + 16), inputHandler);
            iX += btn.getWidth() + 8;
        }
        return 60;
    }

    public int getMaxScrollHeight() {
        if (this.showQrOverlay) {
            return 728;
        }
        return Math.max(this.totalSize, 728);
    }

    public boolean isBase() {
        return false;
    }

    private void drawOnlineTab(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int cy = y + 40 + 32;
        int contentX = x + 20;

        // ---- Hero header card ----
        int headerH = 72;
        nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 920.0f, (float)headerH, ElaraColors.gray800Alpha(220), 14.0f);
        nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 4.0f, (float)headerH, ElaraColors.accent(), 2.0f);
        nvg.drawText(vg, "ONLINE MUSIC", (float)(contentX + 24), (float)(cy + 18), ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        nvg.drawText(vg, "NetEase Cloud Music", (float)(contentX + 24), (float)(cy + 46), -1, 18.0f, Fonts.BOLD);
        if (!this.apiChecked) { this.checkApiConnection(); }
        if (!this.apiStatus.isEmpty()) {
            float statusW = this.tw(vg, this.apiStatus, 11.0f, Fonts.MEDIUM);
            int sx = contentX + 920 - (int)statusW - 24;
            nvg.drawText(vg, this.apiStatus, (float)sx, (float)(cy + 46), this.apiStatus.contains("Connected") ? -11751600 : -44462, 11.0f, Fonts.MEDIUM);
        }
        cy += headerH + 16;

        if (this.onlineView != OnlineView.PLAYLIST_DETAIL) {
            this.searchTabBtn.setToggleable(true);
            this.playlistsTabBtn.setToggleable(true);
            this.searchTabBtn.setToggled(this.onlineView == OnlineView.SEARCH);
            this.playlistsTabBtn.setToggled(this.onlineView == OnlineView.PLAYLISTS);
            this.searchTabBtn.draw(vg, (float)contentX, (float)cy, inputHandler);
            this.playlistsTabBtn.draw(vg, (float)(contentX + this.searchTabBtn.getWidth() + 8), (float)cy, inputHandler);
            this.updateLoginButtonState();
            this.loginBtn.draw(vg, (float)(contentX + 920 - this.loginBtn.getWidth()), (float)cy, inputHandler);
            nvg.drawLine(vg, (float)contentX, (float)(cy += this.searchTabBtn.getHeight() + 20), (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
            cy += 16;
        }
        if (this.onlineView == OnlineView.SEARCH) {
            cy = this.drawOnlineSearchView(vg, contentX, cy, inputHandler);
        } else if (this.onlineView == OnlineView.PLAYLISTS) {
            cy = this.drawUserPlaylistsView(vg, contentX, cy, inputHandler);
        } else if (this.onlineView == OnlineView.PLAYLIST_DETAIL) {
            cy = this.drawPlaylistDetailView(vg, contentX, cy, inputHandler);
        }
        this.totalSize = cy + 40 - y;
    }

    private int drawOnlineSearchView(long vg, int contentX, int cy, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        // ---- Search bar row ----
        this.searchInput.draw(vg, (float)contentX, (float)cy, inputHandler);
        this.searchBtn.draw(vg, (float)(contentX + this.searchInput.getWidth() + 8), (float)cy, inputHandler);
        this.hotSongsBtn.draw(vg, (float)contentX, (float)(cy += this.searchInput.getHeight() + 20), inputHandler);
        this.refreshOnlineBtn.draw(vg, (float)(contentX + this.hotSongsBtn.getWidth() + 12), (float)cy, inputHandler);
        cy += 44;
        // ---- Results ----
        if (this.isOnlineLoading) {
            nvg.drawText(vg, "Loading...", (float)contentX, (float)(cy + 28), ElaraColors.white60(), 16.0f, Fonts.MEDIUM);
            cy += 60;
        } else if (this.onlineSongList.isEmpty()) {
            String emptyMsg = this.lastSearchKeyword.isEmpty() ? "Click \"Hot Songs\" to load popular songs" : "No results found";
            nvg.drawText(vg, emptyMsg, (float)contentX, (float)(cy + 28), ElaraColors.white60(), 16.0f, Fonts.MEDIUM);
            cy += 60;
        } else {
            nvg.drawText(vg, "RESULTS", (float)contentX, (float)cy, ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
            cy += 24;
            int rowHeight = 48;
            for (int i = 0; i < this.onlineSongList.size(); ++i) {
                boolean isSelected;
                SongInfo song = this.onlineSongList.get(i);
                int rowY = cy + i * rowHeight;
                boolean bl = isSelected = i == this.onlineSelectedIndex;
                boolean isHovered = inputHandler.isAreaHovered((float)contentX, (float)rowY, 920.0f, (float)(rowHeight - 6));
                int rowBg = isSelected ? ElaraColors.GRAY_700 : (isHovered ? ElaraColors.GRAY_750 : ElaraColors.gray800Alpha(180));
                nvg.drawRoundedRect(vg, (float)contentX, (float)rowY, 920.0f, (float)(rowHeight - 6), rowBg, 8.0f);
                if (isSelected) {
                    nvg.drawRoundedRect(vg, (float)contentX, (float)rowY, 3.0f, (float)(rowHeight - 6), ElaraColors.accent(), 2.0f);
                }
                float rowTextY = this.centerTextY(rowY, rowHeight - 6, 14.0f, Fonts.MEDIUM);
                String status = isSelected ? "\u25b6" : String.valueOf(i + 1);
                float statusW = this.tw(vg, status, 13.0f, Fonts.MEDIUM);
                nvg.drawText(vg, status, (float)(contentX + 24) - statusW / 2.0f, rowTextY, isSelected ? ElaraColors.accent() : ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
                String title = MusicLayout.truncByWidth(vg, song.getName(), 14.0f, Fonts.MEDIUM, 380.0f, this.textWidthCache);
                nvg.drawText(vg, title, (float)(contentX + 56), rowTextY, isSelected ? ElaraColors.WHITE : ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
                String artist = MusicLayout.truncByWidth(vg, song.getArtist(), 13.0f, Fonts.MEDIUM, 200.0f, this.textWidthCache);
                nvg.drawText(vg, artist, (float)(contentX + 460), rowTextY, ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
                String dur = song.getFormattedDuration();
                float durW = this.tw(vg, dur, 12.0f, Fonts.MEDIUM);
                nvg.drawText(vg, dur, (float)(contentX + 920) - durW - 20.0f, rowTextY, ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
                if (!inputHandler.isClicked() || !(inputHandler.mouseX() >= (float)contentX) || !(inputHandler.mouseX() <= (float)(contentX + 920)) || !(inputHandler.mouseY() >= (float)rowY) || !(inputHandler.mouseY() <= (float)(rowY + rowHeight - 6))) continue;
                this.onlineSelectedIndex = i;
                this.playOnlineSong(song, i);
            }
            cy += this.onlineSongList.size() * rowHeight;
        }
        return cy;
    }

    private int drawUserPlaylistsView(long vg, int contentX, int cy, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        MusicListFetcher fetcher = MusicListFetcher.getInstance();
        if (fetcher.isLoggedIn() && !this.loadingPlaylists && this.userPlaylists.isEmpty() && !this.userInfoLoaded) {
            this.loadUserPlaylistsAndInfo();
        }
        if (fetcher.isLoggedIn()) {
            String path;
            // ---- Profile card ----
            int profileCardH = 120;
            nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 920.0f, (float)profileCardH, ElaraColors.gray800Alpha(220), 14.0f);
            nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 4.0f, (float)profileCardH, ElaraColors.accent(), 2.0f);
            int avatarSize = 64;
            int avatarX = contentX + 28;
            int avatarY = cy + 28;
            nvg.drawRoundedRect(vg, (float)avatarX, (float)avatarY, (float)avatarSize, (float)avatarSize, ElaraColors.GRAY_700, 10.0f);
            if (this.userAvatarPath == null && this.userAvatarUrl != null && !this.userAvatarUrl.isEmpty() && (path = CoverManager.getNetworkCoverPath(this.userAvatarUrl)) != null) {
                this.userAvatarPath = path;
            }
            if (this.userAvatarPath != null) {
                nvg.drawRoundImage(vg, this.userAvatarPath, (float)avatarX, (float)avatarY, (float)avatarSize, (float)avatarSize, 10.0f, MusicPlayerPage.class);
            } else {
                String firstLetter = this.userNickname.isEmpty() ? "U" : this.userNickname.substring(0, 1);
                float flSize = 28.0f;
                float flW = this.tw(vg, firstLetter, flSize, Fonts.BOLD);
                float flY = this.centerTextY(avatarY, avatarSize, flSize, Fonts.BOLD);
                nvg.drawText(vg, firstLetter, (float)avatarX + ((float)avatarSize - flW) / 2.0f, flY, ElaraColors.accent(), flSize, Fonts.BOLD);
            }
            int infoX = avatarX + avatarSize + 24;
            nvg.drawText(vg, this.userNickname.isEmpty() ? "Loading..." : this.userNickname, (float)infoX, (float)(avatarY + 24), -1, 20.0f, Fonts.BOLD);
            if (this.userLevel > 0) {
                int levelBadgeW = 52;
                int levelBadgeH = 22;
                int levelBadgeX = infoX;
                int levelBadgeY = avatarY + 36;
                nvg.drawRoundedRect(vg, (float)levelBadgeX, (float)levelBadgeY, (float)levelBadgeW, (float)levelBadgeH, ElaraColors.accent(), 11.0f);
                String levelStr = "Lv." + this.userLevel;
                float lsW = this.tw(vg, levelStr, 11.0f, Fonts.BOLD);
                float lsY = this.centerTextY(levelBadgeY, levelBadgeH, 11.0f, Fonts.BOLD);
                nvg.drawText(vg, levelStr, (float)levelBadgeX + ((float)levelBadgeW - lsW) / 2.0f, lsY, -1, 11.0f, Fonts.BOLD);
            }
            // Stats row inside profile card
            int statY = cy + profileCardH - 28;
            String[] statLabels = new String[]{"Playlists", "Follows", "Fans", "Scrobbles"};
            int[] statValues = new int[]{this.userPlaylistCount, this.userFollows, this.userFolloweds, this.userListenSongs};
            int statGap = 160;
            for (int i = 0; i < statLabels.length; ++i) {
                int sx = contentX + 28 + i * statGap;
                float valW = this.tw(vg, String.valueOf(statValues[i]), 16.0f, Fonts.BOLD);
                float lblW = this.tw(vg, statLabels[i], 11.0f, Fonts.MEDIUM);
                nvg.drawText(vg, String.valueOf(statValues[i]), (float)sx, (float)(statY - 4), -1, 16.0f, Fonts.BOLD);
                nvg.drawText(vg, statLabels[i], (float)sx, (float)(statY + 18), ElaraColors.white60(), 11.0f, Fonts.MEDIUM);
            }
            cy += profileCardH + 20;
            nvg.drawLine(vg, (float)contentX, (float)cy, (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
            cy += 16;
        } else {
            // ---- Sign-in prompt card ----
            nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 920.0f, 120.0f, ElaraColors.gray800Alpha(220), 14.0f);
            nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 4.0f, 120.0f, ElaraColors.GRAY_600, 2.0f);
            nvg.drawText(vg, "Sign in to see your playlists", (float)(contentX + 28), (float)(cy + 40), -1, 18.0f, Fonts.BOLD);
            nvg.drawText(vg, "Log in with NetEase Cloud Music to access your saved playlists and favorites", (float)(contentX + 28), (float)(cy + 68), ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
            this.loginBtn.draw(vg, (float)(contentX + 28), (float)(cy + 84), inputHandler);
            cy += 140;
        }
        if (this.loadingPlaylists) {
            nvg.drawText(vg, "Loading playlists...", (float)contentX, (float)(cy + 20), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
            cy += 60;
        } else if (!this.userPlaylists.isEmpty()) {
            nvg.drawText(vg, "MY PLAYLISTS", (float)contentX, (float)cy, ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
            cy += 24;
            int cols = 4;
            int cardW = (920 - (cols - 1) * 16) / cols;
            int cardH = cardW + 56;
            int rowGap = 20;
            int totalRows = (int)Math.ceil((double)this.userPlaylists.size() / (double)cols);
            for (int row = 0; row < totalRows; ++row) {
                for (int col = 0; col < cols; ++col) {
                    int idx = row * cols + col;
                    if (idx >= this.userPlaylists.size()) continue;
                    MusicApi.PlaylistInfo playlist = this.userPlaylists.get(idx);
                    int cardX = contentX + col * (cardW + 16);
                    int cardY = cy + row * (cardH + rowGap);
                    boolean plHovered = inputHandler.isAreaHovered((float)cardX, (float)cardY, (float)cardW, (float)cardH);
                    nvg.drawRoundedRect(vg, (float)cardX, (float)cardY, (float)cardW, (float)(cardW + 56), plHovered ? ElaraColors.GRAY_750 : ElaraColors.gray800Alpha(200), 10.0f);
                    // Cover area
                    nvg.drawRoundedRect(vg, (float)cardX, (float)cardY, (float)cardW, (float)cardW, ElaraColors.GRAY_700, 10.0f);
                    String coverPath = CoverManager.getNetworkCoverPath(playlist.coverUrl);
                    if (coverPath != null) {
                        nvg.drawRoundImage(vg, coverPath, (float)cardX, (float)cardY, (float)cardW, (float)cardW, 10.0f, MusicPlayerPage.class);
                    }
                    if (plHovered) {
                        nvg.drawRoundedRect(vg, (float)cardX, (float)cardY, (float)cardW, (float)cardW, ElaraColors.blackAlpha(80), 10.0f);
                        float playW = this.tw(vg, "\u25b6 Play", 12.0f, Fonts.BOLD);
                        nvg.drawRoundedRect(vg, (float)cardX + ((float)cardW - playW - 24.0f) / 2.0f, (float)(cardY + cardW / 2 - 14), playW + 24.0f, 28.0f, ElaraColors.accent(), 14.0f);
                        nvg.drawText(vg, "\u25b6 Play", (float)cardX + ((float)cardW - playW) / 2.0f, this.centerTextY((float)(cardY + cardW / 2 - 14), 28.0f, 12.0f, Fonts.BOLD), -1, 12.0f, Fonts.BOLD);
                    }
                    // Track count badge
                    String trackStr = playlist.trackCount + " songs";
                    float trackBadgeW = this.tw(vg, trackStr, 10.0f, Fonts.MEDIUM) + 14.0f;
                    int trackBadgeH = 20;
                    int trackBadgeX = cardX + cardW - (int)trackBadgeW - 8;
                    int trackBadgeY = cardY + cardW - trackBadgeH - 8;
                    nvg.drawRoundedRect(vg, (float)trackBadgeX, (float)trackBadgeY, trackBadgeW, (float)trackBadgeH, -872415232, 10.0f);
                    float tbY = this.centerTextY(trackBadgeY, trackBadgeH, 10.0f, Fonts.MEDIUM);
                    nvg.drawText(vg, trackStr, (float)(trackBadgeX + 7), tbY, -1, 10.0f, Fonts.MEDIUM);
                    // Playlist name + creator
                    String plName = MusicLayout.truncByWidth(vg, playlist.name, 13.0f, Fonts.MEDIUM, (float)cardW, this.textWidthCache);
                    nvg.drawText(vg, plName, (float)cardX, (float)(cardY + cardW + 24), -1, 13.0f, Fonts.MEDIUM);
                    String creatorStr = MusicLayout.truncByWidth(vg, playlist.creator, 11.0f, Fonts.MEDIUM, (float)cardW, this.textWidthCache);
                    nvg.drawText(vg, "by " + creatorStr, (float)cardX, (float)(cardY + cardW + 44), ElaraColors.white60(), 11.0f, Fonts.MEDIUM);
                    if (!inputHandler.isClicked() || !(inputHandler.mouseX() >= (float)cardX) || !(inputHandler.mouseX() <= (float)(cardX + cardW)) || !(inputHandler.mouseY() >= (float)cardY) || !(inputHandler.mouseY() <= (float)(cardY + cardH))) continue;
                    this.openPlaylistDetail(playlist);
                }
            }
            cy += totalRows * (cardH + rowGap);
        } else if (fetcher.isLoggedIn()) {
            nvg.drawText(vg, "No playlists found", (float)contentX, (float)(cy + 20), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
            cy += 60;
        }
        return cy;
    }

    private int drawPlaylistDetailView(long vg, int contentX, int cy, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        this.backToPlaylistsBtn.draw(vg, (float)contentX, (float)cy, inputHandler);
        cy += this.backToPlaylistsBtn.getHeight() + 20;
        if (this.currentPlaylist == null) {
            return cy;
        }
        // ---- Detail header card ----
        int headerH = 200;
        nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 920.0f, (float)headerH, ElaraColors.gray800Alpha(220), 14.0f);
        nvg.drawRoundedRect(vg, (float)contentX, (float)cy, 4.0f, (float)headerH, ElaraColors.accent(), 2.0f);
        int COVER_SIZE = 160;
        int coverX = contentX + 28;
        int coverY = cy + 20;
        nvg.drawRoundedRect(vg, (float)coverX, (float)coverY, (float)COVER_SIZE, (float)COVER_SIZE, ElaraColors.GRAY_700, 12.0f);
        if (this.currentPlaylistCoverPath != null) {
            nvg.drawRoundImage(vg, this.currentPlaylistCoverPath, (float)coverX, (float)coverY, (float)COVER_SIZE, (float)COVER_SIZE, 12.0f, MusicPlayerPage.class);
        }
        int infoX = coverX + COVER_SIZE + 32;
        int infoW = contentX + 920 - infoX - 28;
        nvg.drawText(vg, "PLAYLIST", (float)infoX, (float)(coverY + 8), ElaraColors.accentDim(), 11.0f, Fonts.BOLD);
        String plName = MusicLayout.truncByWidth(vg, this.currentPlaylist.name, 24.0f, Fonts.BOLD, (float)infoW, this.textWidthCache);
        nvg.drawText(vg, plName, (float)infoX, (float)(coverY + 36), -1, 24.0f, Fonts.BOLD);
        nvg.drawText(vg, "by " + this.currentPlaylist.creator, (float)infoX, (float)(coverY + 72), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
        String songCountStr = this.currentPlaylist.trackCount + " songs";
        nvg.drawText(vg, songCountStr, (float)infoX, (float)(coverY + 100), ElaraColors.white30(), 12.0f, Fonts.MEDIUM);
        this.playAllBtn.draw(vg, (float)infoX, (float)(coverY + 130), inputHandler);
        cy += headerH + 16;
        nvg.drawLine(vg, (float)contentX, (float)cy, (float)(contentX + 920), (float)cy, 1.0f, ElaraColors.GRAY_600);
        cy += 16;
        if (this.loadingPlaylistDetail) {
            nvg.drawText(vg, "Loading songs...", (float)contentX, (float)(cy + 20), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
            cy += 60;
        } else if (this.playlistSongList.isEmpty()) {
            nvg.drawText(vg, "No songs in this playlist", (float)contentX, (float)(cy + 20), ElaraColors.white60(), 14.0f, Fonts.MEDIUM);
            cy += 60;
        } else {
            int rowHeight = 48;
            for (int i = 0; i < this.playlistSongList.size(); ++i) {
                boolean isSelected;
                SongInfo song = this.playlistSongList.get(i);
                int rowY = cy + i * rowHeight;
                boolean bl = isSelected = i == this.onlineSelectedIndex;
                boolean isHovered = inputHandler.isAreaHovered((float)contentX, (float)rowY, 920.0f, (float)(rowHeight - 6));
                int rowBg = isSelected ? ElaraColors.GRAY_700 : (isHovered ? ElaraColors.GRAY_750 : ElaraColors.gray800Alpha(180));
                nvg.drawRoundedRect(vg, (float)contentX, (float)rowY, 920.0f, (float)(rowHeight - 6), rowBg, 8.0f);
                if (isSelected) {
                    nvg.drawRoundedRect(vg, (float)contentX, (float)rowY, 3.0f, (float)(rowHeight - 6), ElaraColors.accent(), 2.0f);
                }
                float rowTextY = this.centerTextY(rowY, rowHeight - 6, 14.0f, Fonts.MEDIUM);
                String status = isSelected ? "\u25b6" : String.valueOf(i + 1);
                float statusW = this.tw(vg, status, 13.0f, Fonts.MEDIUM);
                nvg.drawText(vg, status, (float)(contentX + 24) - statusW / 2.0f, rowTextY, isSelected ? ElaraColors.accent() : ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
                String title = MusicLayout.truncByWidth(vg, song.getName(), 14.0f, Fonts.MEDIUM, 350.0f, this.textWidthCache);
                nvg.drawText(vg, title, (float)(contentX + 56), rowTextY, isSelected ? ElaraColors.WHITE : ElaraColors.white90(), 14.0f, Fonts.MEDIUM);
                String artist = MusicLayout.truncByWidth(vg, song.getArtist(), 13.0f, Fonts.MEDIUM, 180.0f, this.textWidthCache);
                nvg.drawText(vg, artist, (float)(contentX + 430), rowTextY, ElaraColors.white60(), 13.0f, Fonts.MEDIUM);
                String album = MusicLayout.truncByWidth(vg, song.getAlbum(), 12.0f, Fonts.MEDIUM, 160.0f, this.textWidthCache);
                nvg.drawText(vg, album, (float)(contentX + 630), rowTextY, ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
                String dur = song.getFormattedDuration();
                float durW = this.tw(vg, dur, 12.0f, Fonts.MEDIUM);
                nvg.drawText(vg, dur, (float)(contentX + 920) - durW - 20.0f, rowTextY, ElaraColors.white60(), 12.0f, Fonts.MEDIUM);
                if (!inputHandler.isClicked() || !(inputHandler.mouseX() >= (float)contentX) || !(inputHandler.mouseX() <= (float)(contentX + 920)) || !(inputHandler.mouseY() >= (float)rowY) || !(inputHandler.mouseY() <= (float)(rowY + rowHeight - 6))) continue;
                this.onlineSelectedIndex = i;
                this.playOnlineSong(song, i, this.playlistSongList);
            }
            cy += this.playlistSongList.size() * rowHeight;
        }
        return cy;
    }

    private void switchOnlineView(OnlineView view) {
        this.onlineView = view;
        if (view == OnlineView.PLAYLISTS) {
            this.currentPlaylist = null;
            this.currentPlaylistCoverPath = null;
        }
    }

    private void loadUserPlaylistsAndInfo() {
        if (this.loadingPlaylists) {
            return;
        }
        this.loadingPlaylists = true;
        this.loadingUserInfo = true;
        MusicListFetcher fetcher = MusicListFetcher.getInstance();
        String uid = fetcher.getUserId();
        if (uid.isEmpty()) {
            this.loadingPlaylists = false;
            this.loadingUserInfo = false;
            return;
        }
        fetcher.fetchUserDetail(uid).thenAccept(detail -> {
            if (detail != null && detail.success) {
                this.userNickname = detail.nickname;
                this.userAvatarUrl = detail.avatarUrl;
                this.userLevel = detail.level;
                this.userListenSongs = detail.listenSongs;
                this.userPlaylistCount = detail.playlistCount;
                this.userFollows = detail.follows;
                this.userFolloweds = detail.followeds;
                this.userInfoLoaded = true;
                if (detail.avatarUrl != null && !detail.avatarUrl.isEmpty()) {
                    CoverManager.preloadCover(detail.avatarUrl, null);
                    String avatarPath = CoverManager.getNetworkCoverPath(detail.avatarUrl);
                    this.userAvatarPath = avatarPath != null ? avatarPath : null;
                }
            }
            this.loadingUserInfo = false;
        });
        fetcher.fetchUserPlaylists(uid).thenAccept(playlists -> {
            this.userPlaylists.clear();
            if (playlists != null) {
                this.userPlaylists.addAll((Collection<MusicApi.PlaylistInfo>)playlists);
            }
            this.loadingPlaylists = false;
        });
    }

    private void openPlaylistDetail(MusicApi.PlaylistInfo playlist) {
        this.currentPlaylist = playlist;
        this.currentPlaylistCoverPath = null;
        this.loadingPlaylistDetail = true;
        this.playlistSongList.clear();
        this.onlineSelectedIndex = -1;
        this.onlineView = OnlineView.PLAYLIST_DETAIL;
        if (playlist.coverUrl != null && !playlist.coverUrl.isEmpty()) {
            new Thread(() -> {
                String path = CoverManager.getNetworkCoverPath(playlist.coverUrl);
                if (path != null) {
                    this.currentPlaylistCoverPath = path;
                }
            }, "CoverDownload").start();
        }
        MusicListFetcher.getInstance().fetchPlaylist(playlist.id).thenAccept(songs -> {
            this.playlistSongList.clear();
            if (songs != null) {
                this.playlistSongList.addAll((Collection<SongInfo>)songs);
            }
            this.loadingPlaylistDetail = false;
        });
    }

    private void playAllPlaylistSongs() {
        if (this.playlistSongList.isEmpty()) {
            return;
        }
        SongInfo first = this.playlistSongList.get(0);
        if (first != null) {
            this.playOnlineSong(first, 0, this.playlistSongList);
        }
    }

    private void performOnlineSearch() {
        String keyword = this.searchInput.getInput();
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        this.lastSearchKeyword = keyword.trim();
        this.isOnlineLoading = true;
        CompletableFuture<List<SongInfo>> future = MusicListFetcher.getInstance().search(keyword.trim(), 50);
        future.thenAccept(songs -> {
            this.onlineSongList.clear();
            if (songs != null) {
                this.onlineSongList.addAll(songs);
            }
            this.isOnlineLoading = false;
            this.onlineSelectedIndex = -1;
        }).exceptionally(e -> {
            this.isOnlineLoading = false;
            this.onlineSongList.clear();
            this.apiStatus = "API Error: " + e.getMessage();
            return null;
        });
    }

    private void loadHotSongs() {
        this.isOnlineLoading = true;
        this.lastSearchKeyword = "";
        this.searchInput.setInput("");
        CompletableFuture<List<SongInfo>> future = MusicListFetcher.getInstance().fetchHotSongs(50);
        future.thenAccept(songs -> {
            this.onlineSongList.clear();
            if (songs != null) {
                this.onlineSongList.addAll(songs);
            }
            this.isOnlineLoading = false;
            this.onlineSelectedIndex = -1;
        }).exceptionally(e -> {
            this.isOnlineLoading = false;
            this.onlineSongList.clear();
            this.apiStatus = "API Error: " + e.getMessage();
            return null;
        });
    }

    private void refreshOnlineData() {
        this.apiChecked = false;
        if (this.lastSearchKeyword.isEmpty()) {
            this.loadHotSongs();
        } else {
            this.performOnlineSearch();
        }
    }

    private void checkApiConnection() {
        this.apiChecked = true;
        this.apiStatus = "Checking API connection...";
        new Thread(() -> {
            try {
                boolean connected = MusicPlayerManager.getApiManager().testConnection();
                this.apiStatus = connected ? "\u2705 API Connected" : "\u274c API Connection Failed";
            }
            catch (Exception e) {
                this.apiStatus = "\u274c API Error: " + e.getMessage();
            }
        }).start();
    }

    private void playOnlineSong(SongInfo song, int queueIndex, List<SongInfo> queue) {
        MusicEngine engine = MusicPlayerManager.getEngine();
        if (engine != null) {
            engine.setOnlineQueue(queue, queueIndex);
            engine.playNetwork(song);
        }
    }

    private void playOnlineSong(SongInfo song, int queueIndex) {
        this.playOnlineSong(song, queueIndex, this.onlineSongList);
    }

    private void playOnlineSong(SongInfo song) {
        this.playOnlineSong(song, this.onlineSongList.indexOf(song), this.onlineSongList);
    }

    private void updateLoginButtonState() {
        if (MusicListFetcher.getInstance().isLoggedIn()) {
            this.loginBtn.setText("Logout");
            this.loginBtn.setColorPalette(ColorPalette.SECONDARY);
        } else {
            this.loginBtn.setText("Login");
            this.loginBtn.setColorPalette(ColorPalette.PRIMARY);
        }
    }

    private void openLoginScreen() {
        if (MusicListFetcher.getInstance().isLoggedIn()) {
            MusicListFetcher.getInstance().logout();
            NotificationHelper.sendLogout();
        } else {
            this.showQrOverlay = true;
            this.startQrLogin();
        }
    }

    private void startQrLogin() {
        this.qrShouldStop.set(false);
        this.qrLoading = true;
        this.qrImagePath = null;
        this.qrChecking = false;
        this.qrLoginSuccess = false;
        this.qrLoginStatus = "Getting QR code...";
        if (this.qrScheduler != null && !this.qrScheduler.isShutdown()) {
            this.qrScheduler.shutdownNow();
        }
        ((CompletableFuture)MusicListFetcher.getInstance().loginByQR().thenAccept(path -> {
            this.qrLoading = false;
            if (path != null && !path.isEmpty()) {
                this.qrImagePath = path;
                this.qrLoginStatus = "Scan with NetEase Cloud Music";
                this.startQrCheckLoop();
            } else {
                this.qrLoginStatus = "Failed to get QR code";
            }
        })).exceptionally(e -> {
            this.qrLoading = false;
            this.qrLoginStatus = "Network error";
            return null;
        });
    }

    private void startQrCheckLoop() {
        this.qrChecking = true;
        this.qrScheduler = Executors.newSingleThreadScheduledExecutor();
        int[] attempts = new int[]{0};
        this.qrScheduler.scheduleAtFixedRate(() -> {
            if (this.qrShouldStop.get() || !this.qrChecking) {
                return;
            }
            attempts[0] = attempts[0] + 1;
            if (attempts[0] >= 60) {
                this.qrChecking = false;
                this.qrLoginStatus = "Login timeout";
                return;
            }
            ((CompletableFuture)MusicListFetcher.getInstance().checkQRLogin().thenAccept(result -> {
                if (this.qrShouldStop.get()) {
                    return;
                }
                switch (result.getStatus()) {
                    case WAITING: {
                        this.qrLoginStatus = "Waiting for scan";
                        break;
                    }
                    case SCANNED: {
                        this.qrLoginStatus = "Scanned, please confirm";
                        break;
                    }
                    case SUCCESS: {
                        this.qrChecking = false;
                        this.qrLoginSuccess = true;
                        this.qrShouldStop.set(true);
                        this.qrLoginStatus = "Login successful!";
                        this.updateLoginButtonState();
                        NotificationHelper.sendLoginSuccess(MusicListFetcher.getInstance().getNickname());
                        break;
                    }
                    case EXPIRED: {
                        this.qrChecking = false;
                        this.qrLoginStatus = "QR code expired";
                        break;
                    }
                    case FAILED: {
                        this.qrChecking = false;
                        this.qrLoginStatus = "Login failed: " + result.getMessage();
                        break;
                    }
                    case TIMEOUT: {
                        this.qrChecking = false;
                        this.qrLoginStatus = "Login timeout";
                    }
                }
            })).exceptionally(e -> {
                this.qrChecking = false;
                return null;
            });
        }, 0L, 2L, TimeUnit.SECONDS);
    }

    private void closeQrOverlay() {
        this.showQrOverlay = false;
        this.qrShouldStop.set(true);
        this.qrChecking = false;
        this.qrLoading = false;
        this.qrImagePath = null;
        this.qrLoginSuccess = false;
        if (this.qrScheduler != null && !this.qrScheduler.isShutdown()) {
            this.qrScheduler.shutdownNow();
        }
    }

    private void drawQrOverlay(long vg, int x, int y, InputHandler inputHandler) {
        boolean showRetry;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int overlayW = 400;
        int overlayH = 440;
        int overlayX = x + (920 - overlayW) / 2;
        int overlayY = y + 80;
        nvg.drawRoundedRect(vg, (float)overlayX, (float)overlayY, (float)overlayW, (float)overlayH, -216393190, 16.0f);
        nvg.drawHollowRoundRect(vg, (float)overlayX, (float)overlayY, (float)overlayW, (float)overlayH, ElaraColors.accent(), 16.0f, 2.0f);
        nvg.drawCenteredText(vg, "QR CODE LOGIN", (float)overlayX + (float)overlayW / 2.0f, (float)(overlayY + 32), -1, 18.0f, Fonts.BOLD);
        int qrSize = 200;
        int qrX = overlayX + (overlayW - qrSize) / 2;
        int qrY = overlayY + 70;
        nvg.drawRoundedRect(vg, (float)(qrX - 8), (float)(qrY - 8), (float)(qrSize + 16), (float)(qrSize + 16), -14013910, 12.0f);
        if (this.qrImagePath != null) {
            nvg.drawImage(vg, this.qrImagePath, (float)qrX, (float)qrY, (float)qrSize, (float)qrSize);
        } else {
            nvg.drawRoundedRect(vg, (float)qrX, (float)qrY, (float)qrSize, (float)qrSize, -15658735, 8.0f);
            String loadingText = this.qrLoading ? "Loading..." : "Failed";
            int loadingColor = this.qrLoading ? ElaraColors.white60() : -44462;
            nvg.drawCenteredText(vg, loadingText, (float)qrX + (float)qrSize / 2.0f, (float)qrY + (float)qrSize / 2.0f - 7.0f, loadingColor, 16.0f, Fonts.MEDIUM);
        }
        int statusColor = this.qrLoginSuccess ? -11751600 : (this.qrChecking ? ElaraColors.white60() : (this.qrLoginStatus.contains("expired") || this.qrLoginStatus.contains("failed") || this.qrLoginStatus.contains("error") ? -44462 : ElaraColors.white60()));
        nvg.drawCenteredText(vg, this.qrLoginStatus, (float)overlayX + (float)overlayW / 2.0f, (float)(qrY + qrSize + 36), statusColor, 14.0f, Fonts.MEDIUM);
        int btnY = overlayY + overlayH - 56;
        int btnSpacing = 12;
        int totalBtnW = this.qrCloseBtn.getWidth() + btnSpacing + this.qrRetryBtn.getWidth();
        int btnStartX = overlayX + (overlayW - totalBtnW) / 2;
        this.qrCloseBtn.draw(vg, (float)btnStartX, (float)btnY, inputHandler);
        if (this.qrCloseBtn.isClicked()) {
            this.closeQrOverlay();
        }
        boolean bl = showRetry = !this.qrChecking && !this.qrLoading && !this.qrLoginSuccess;
        if (showRetry) {
            this.qrRetryBtn.draw(vg, (float)(btnStartX + this.qrCloseBtn.getWidth() + btnSpacing), (float)btnY, inputHandler);
            if (this.qrRetryBtn.isClicked()) {
                this.startQrLogin();
            }
        }
    }

    public void keyTyped(char key, int keyCode) {
        this.searchInput.keyTyped(key, keyCode);
        if (keyCode == 28 && this.searchInput.isToggled()) {
            this.performOnlineSearch();
        }
    }

    private static enum OnlineView {
        SEARCH,
        PLAYLISTS,
        PLAYLIST_DETAIL;
    }
}
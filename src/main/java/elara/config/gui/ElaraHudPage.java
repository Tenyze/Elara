package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import elara.config.ElaraConfig;
import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigColorElement;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSlider;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSwitch;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.hud.Hud;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.config.music.MusicPlayerConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * HUD settings page laid out like {@link ElaraModulesPage}: a top row of
 * toggleable category tabs (Potion / Target / Music HUD) drawn in
 * {@link #drawStatic}, with the selected category's options shown beneath a
 * title + divider. Options are lazily built on first draw so that
 * {@link ElaraConfig#INSTANCE} and every {@code @HUD} instance are fully
 * initialised — that is what makes the annotated fields serialise and persist
 * across restarts.
 */
public class ElaraHudPage extends Page {
    private static final int WHITE_90 = ElaraColors.white90();
    private static final int WHITE_60 = ElaraColors.white60();
    private static final int GRAY_300 = ElaraColors.GRAY_600;

    private static final String POTION_HUD = "Potion HUD";
    private static final String TARGET_HUD = "Target HUD";
    private static final String MUSIC_HUD = "Music HUD";
    private static final String SESSION_HUD = "Session HUD";
    private static final String[] CATEGORIES = new String[]{POTION_HUD, TARGET_HUD, MUSIC_HUD, SESSION_HUD};

    private final List<BasicOption> potionOptions = new ArrayList<>();
    private final List<BasicOption> targetOptions = new ArrayList<>();
    private final List<BasicOption> musicOptions = new ArrayList<>();
    private final List<BasicOption> sessionOptions = new ArrayList<>();
    private final ArrayList<BasicButton> categoryButtons = new ArrayList<>();
    private boolean optionsBuilt = false;
    private int totalSize = 728;
    private String selectedCategory = POTION_HUD;

    public ElaraHudPage() {
        super("HUD Settings");
        this.buildCategoryButtons();
    }

    private void buildCategoryButtons() {
        this.categoryButtons.clear();
        for (String cat : CATEGORIES) {
            BasicButton btn = new BasicButton(0, 32, cat, 2, ColorPalette.SECONDARY);
            btn.setToggleable(true);
            btn.setToggled(cat.equals(this.selectedCategory));
            btn.setClickAction(() -> {
                this.selectedCategory = cat;
                for (BasicButton b : this.categoryButtons) {
                    b.setToggled(b.getText().equals(this.selectedCategory));
                }
                this.scrollTarget = 0.0f;
                this.scrollAnimation = null;
            });
            this.categoryButtons.add(btn);
        }
    }

    private void buildOptions() {
        this.potionOptions.clear();
        this.targetOptions.clear();
        this.musicOptions.clear();
        this.sessionOptions.clear();
        ElaraConfig config = ElaraConfig.INSTANCE;
        if (config == null) {
            return;
        }

        // ---- Potion HUD ----
        try {
            PotionHud hud = config.potionHud;
            if (hud != null) {
                this.potionOptions.add(new HudToggleOption(hud, "Enabled"));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "showDuration"), hud, "Show Duration", "Show potion effect duration", "PotionHUD", "Display", 1));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "showAmplifier"), hud, "Show Amplifier", "Show potion amplifier level", "PotionHUD", "Display", 1));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "showIcon"), hud, "Show Icon", "Show potion effect icon", "PotionHUD", "Display", 1));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "showBackground"), hud, "Show Background", "Show HUD background panel", "PotionHUD", "Appearance", 1));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "blurBackground"), hud, "Blur Background", "Enable glassmorphism blur effect on background", "PotionHUD", "Appearance", 1));
                this.potionOptions.add(new ConfigSlider(field(PotionHud.class, "blurRadius"), hud, "Blur Radius", "Blur strength (4~16 recommended)", "PotionHUD", "Appearance", 4f, 16f, 0));
                this.potionOptions.add(new ConfigColorElement(field(PotionHud.class, "backgroundColor"), hud, "Background Color", "Background color and opacity", "PotionHUD", "Appearance", 1, true));
                this.potionOptions.add(new ConfigSlider(field(PotionHud.class, "contentScale"), hud, "Scale", "HUD content scale multiplier", "PotionHUD", "Appearance", 0.5f, 2.0f, 0));
                // Round settings
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "roundBorder"), hud, "Round Border", "Enable rounded corners", "PotionHUD", "Round", 1));
                this.potionOptions.add(new ConfigSlider(field(PotionHud.class, "cornerRadius"), hud, "Corner Radius", "Rounded corner radius", "PotionHUD", "Round", 0f, 20f, 0));
                this.potionOptions.add(new ConfigSwitch(field(PotionHud.class, "showOutline"), hud, "Show Outline", "Show border outline", "PotionHUD", "Round", 1));
                this.potionOptions.add(new ConfigSlider(field(PotionHud.class, "outlineWidth"), hud, "Outline Width", "Width of border outline", "PotionHUD", "Round", 1f, 5f, 0));
                this.potionOptions.add(new ConfigColorElement(field(PotionHud.class, "outlineColor"), hud, "Outline Color", "Color of border outline", "PotionHUD", "Round", 1, true));
            }
        } catch (Throwable e) {
            System.err.println("[Elara] ElaraHudPage PotionHUD options failed: " + e);
            e.printStackTrace();
        }

        // ---- Target HUD ----
        try {
            TargetHud hud = config.targetHud;
            if (hud != null) {
                this.targetOptions.add(new HudToggleOption(hud, "Enabled"));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "showText"), hud, "Show Text", "Show target name and health text", "TargetHUD", "Display", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "kaOnly"), hud, "KillAura Only", "Only show target while KillAura is active", "TargetHUD", "Targeting", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "healthFromTag"), hud, "Health From Tag", "Read health from below-name scoreboard tag", "TargetHUD", "Health", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "healthFromTab"), hud, "Health From Tab", "Read health from tab-list scoreboard", "TargetHUD", "Health", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "redTheme"), hud, "Red Theme", "Use red color theme", "TargetHUD", "Appearance", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "blurBackground"), hud, "Blur Background", "Enable glassmorphism blur effect on background", "TargetHUD", "Appearance", 1));
                this.targetOptions.add(new ConfigSlider(field(TargetHud.class, "blurRadius"), hud, "Blur Radius", "Blur strength (4~16 recommended)", "TargetHUD", "Appearance", 4f, 16f, 0));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "verticalMode"), hud, "Vertical Mode", "Vertical layout mode", "TargetHUD", "Layout", 1));
                this.targetOptions.add(new ConfigSlider(field(TargetHud.class, "contentScale"), hud, "Scale", "HUD content scale multiplier", "TargetHUD", "Scale", 0.5f, 2.0f, 0));
                this.targetOptions.add(new ConfigSlider(field(TargetHud.class, "cornerRad"), hud, "Corner Radius", "Rounded corner radius of the HUD background", "TargetHUD", "Style", 0f, 20f, 0));
                // Round settings
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "roundBorder"), hud, "Round Border", "Enable rounded corners", "TargetHUD", "Round", 1));
                this.targetOptions.add(new ConfigSwitch(field(TargetHud.class, "showOutline"), hud, "Show Outline", "Show border outline", "TargetHUD", "Round", 1));
                this.targetOptions.add(new ConfigSlider(field(TargetHud.class, "outlineWidth"), hud, "Outline Width", "Width of border outline", "TargetHUD", "Round", 1f, 5f, 0));
                this.targetOptions.add(new ConfigColorElement(field(TargetHud.class, "outlineColor"), hud, "Outline Color", "Color of border outline", "TargetHUD", "Round", 1, true));
            }
        } catch (Throwable e) {
            System.err.println("[Elara] ElaraHudPage TargetHUD options failed: " + e);
            e.printStackTrace();
        }

        // ---- Music HUD ----
        try {
            Hud hud = config.musicHud;
            if (hud != null) {
                // Mirror the saved MusicPlayerConfig values into the live static
                // fields the HUD actually reads, so the toggles below (and the
                // rendered HUD) always reflect persisted state.
                ElaraHudPage.syncMusicHudFromConfig();
                this.musicOptions.add(new HudToggleOption(hud, "Enabled"));
                this.musicOptions.add(new MusicHudToggleOption("hudShowCover", "Show Cover", "Show album cover art"));
                this.musicOptions.add(new MusicHudToggleOption("hudShowSpectrum", "Show Spectrum", "Show audio spectrum visualizer"));
                this.musicOptions.add(new MusicHudToggleOption("hudShowProgress", "Show Progress Bar", "Show playback progress bar"));
                this.musicOptions.add(new MusicHudToggleOption("hudHideWhenNotPlaying", "Hide When Not Playing", "Hide the HUD when no song is playing"));
                // Blur settings
                this.musicOptions.add(new ConfigSwitch(field(MusicHud.class, "blurBackground"), hud, "Blur Background", "Enable glassmorphism blur effect on background", "MusicHUD", "Appearance", 1));
                this.musicOptions.add(new ConfigSlider(field(MusicHud.class, "blurRadius"), hud, "Blur Radius", "Blur strength (4~16 recommended)", "MusicHUD", "Appearance", 4f, 16f, 0));
                // Round settings
                this.musicOptions.add(new ConfigSwitch(field(MusicHud.class, "roundBorder"), hud, "Round Border", "Enable rounded corners", "MusicHUD", "Round", 1));
                this.musicOptions.add(new ConfigSlider(field(MusicHud.class, "cornerRadius"), hud, "Corner Radius", "Rounded corner radius", "MusicHUD", "Round", 0f, 20f, 0));
                this.musicOptions.add(new ConfigSwitch(field(MusicHud.class, "showOutline"), hud, "Show Outline", "Show border outline", "MusicHUD", "Round", 1));
                this.musicOptions.add(new ConfigSlider(field(MusicHud.class, "outlineWidth"), hud, "Outline Width", "Width of border outline", "MusicHUD", "Round", 1f, 5f, 0));
                this.musicOptions.add(new ConfigColorElement(field(MusicHud.class, "outlineColor"), hud, "Outline Color", "Color of border outline", "MusicHUD", "Round", 1, true));
            }
        } catch (Throwable e) {
            System.err.println("[Elara] ElaraHudPage MusicHUD options failed: " + e);
            e.printStackTrace();
        }

        // ---- Session HUD ----
        try {
            SessionInfoHud hud = config.sessionHud;
            if (hud != null) {
                this.sessionOptions.add(new HudToggleOption(hud, "Enabled"));
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "showPlayTime"), hud, "Show Play Time", "Show session play time", "SessionHUD", "Display", 1));
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "showKills"), hud, "Show Kills", "Show kill count", "SessionHUD", "Display", 1));
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "showWins"), hud, "Show Wins", "Show game wins", "SessionHUD", "Display", 1));
                this.sessionOptions.add(new ConfigSlider(field(SessionInfoHud.class, "contentScale"), hud, "Scale", "HUD content scale multiplier", "SessionHUD", "Appearance", 0.5f, 2.0f, 0));
                // Blur settings
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "blurBackground"), hud, "Blur Background", "Enable glassmorphism blur effect on background", "SessionHUD", "Appearance", 1));
                this.sessionOptions.add(new ConfigSlider(field(SessionInfoHud.class, "blurRadius"), hud, "Blur Radius", "Blur strength (4~16 recommended)", "SessionHUD", "Appearance", 4f, 16f, 0));
                // Round settings
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "roundBorder"), hud, "Round Border", "Enable rounded corners", "SessionHUD", "Round", 1));
                this.sessionOptions.add(new ConfigSlider(field(SessionInfoHud.class, "cornerRadius"), hud, "Corner Radius", "Rounded corner radius", "SessionHUD", "Round", 0f, 20f, 0));
                this.sessionOptions.add(new ConfigSwitch(field(SessionInfoHud.class, "showOutline"), hud, "Show Outline", "Show border outline", "SessionHUD", "Round", 1));
                this.sessionOptions.add(new ConfigSlider(field(SessionInfoHud.class, "outlineWidth"), hud, "Outline Width", "Width of border outline", "SessionHUD", "Round", 1f, 5f, 0));
                this.sessionOptions.add(new ConfigColorElement(field(SessionInfoHud.class, "outlineColor"), hud, "Outline Color", "Color of border outline", "SessionHUD", "Round", 1, true));
            }
        } catch (Throwable e) {
            System.err.println("[Elara] ElaraHudPage SessionHUD options failed: " + e);
            e.printStackTrace();
        }
    }

    private static void syncMusicHudFromConfig() {
        try {
            MusicPlayerPage.hudShowCover = MusicPlayerConfig.hudShowCover();
            MusicPlayerPage.hudShowSpectrum = MusicPlayerConfig.hudShowSpectrum();
            MusicPlayerPage.hudShowProgress = MusicPlayerConfig.hudShowProgress();
            MusicPlayerPage.hudHideWhenNotPlaying = MusicPlayerConfig.hudHideWhenNotPlaying();
            MusicPlayerPage.hudScale = MusicPlayerConfig.hudScale();
            MusicPlayerPage.hudPosX = MusicPlayerConfig.hudPosX();
            MusicPlayerPage.hudPosY = MusicPlayerConfig.hudPosY();
        } catch (Throwable ignored) {
            // MusicPlayerConfig not initialised yet — fall back to defaults.
        }
    }

    private List<BasicOption> currentOptions() {
        if (TARGET_HUD.equals(this.selectedCategory)) {
            return this.targetOptions;
        }
        if (MUSIC_HUD.equals(this.selectedCategory)) {
            return this.musicOptions;
        }
        if (SESSION_HUD.equals(this.selectedCategory)) {
            return this.sessionOptions;
        }
        return this.potionOptions;
    }

    private static Field field(Class<?> clazz, String name) {
        try {
            Field f = clazz.getField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + clazz.getSimpleName() + "." + name, e);
        }
    }

    @Override
    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        if (!this.optionsBuilt || this.currentOptions().isEmpty()) {
            this.buildOptions();
            this.optionsBuilt = true;
        }
        // Force rebuild if selected category has no options (HUD might have been initialized later)
        if (this.currentOptions().isEmpty()) {
            this.buildOptions();
        }
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int iX = x + 16;
        int iY = y + 72;

        List<BasicOption> options = this.currentOptions();
        if (options.isEmpty()) {
            nvg.drawText(vg, "No settings for this HUD.", (float) (x + 16), (float) (y + 72), WHITE_60, 14.0f, Fonts.MEDIUM);
            this.totalSize = 200;
            return;
        }

        nvg.drawText(vg, this.selectedCategory, (float) iX, (float) (iY + 24), WHITE_90, 16.0f, Fonts.BOLD);
        nvg.drawLine(vg, (float) iX, (float) (iY + 32), (float) (iX + 796), (float) (iY + 32), 1.0f, GRAY_300);
        iY += 48;

        int rowY = iY;
        for (BasicOption option : options) {
            try {
                option.draw(vg, iX, rowY, inputHandler);
                rowY += option.getHeight() + 8;
            } catch (Throwable e) {
                rowY += 48;
            }
        }
        rowY += 24;
        nvg.drawText(vg, "Tip: Open Edit HUD (default: RIGHT_SHIFT) to drag the HUD.", (float) iX, (float) rowY, WHITE_60, 13.0f, Fonts.MEDIUM);
        rowY += 40;

        this.totalSize = rowY - y + 40;
    }

    @Override
    public int drawStatic(long vg, int x, int y, InputHandler inputHandler) {
        int iX = x + 16;
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        for (BasicButton btn : this.categoryButtons) {
            if (btn.getWidth() == 0) {
                btn.setWidth((int) (Math.ceil(nvg.getTextWidth(vg, btn.getText(), 12.0f, Fonts.MEDIUM) / 8.0) * 8.0 + 16.0));
            }
            btn.draw(vg, (float) iX, (float) (y + 16), inputHandler);
            iX += btn.getWidth() + 8;
        }
        return 60;
    }

    @Override
    public boolean isBase() {
        return false;
    }

    @Override
    public int getMaxScrollHeight() {
        return Math.max(this.totalSize, 728);
    }

    /**
     * A toggle option that flips a HUD's {@code enabled} flag (protected in
     * {@link Hud}) via reflection, since the base class exposes no
     * {@code setEnabled()}. Reflecting on the base field keeps a single code
     * path for every HUD subclass (PotionHud, TargetHud, MusicHud, ...).
     */
    private static class HudToggleOption extends BasicOption {
        private static final Field ENABLED_FIELD;

        static {
            try {
                ENABLED_FIELD = Hud.class.getDeclaredField("enabled");
                ENABLED_FIELD.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Hud.enabled field not found", e);
            }
        }

        private final Hud hud;
        private final BasicButton button;

        HudToggleOption(Hud hud, String name) {
            super(null, null, name, "Toggle this HUD on or off", "HUD", "General", 1);
            this.hud = hud;
            this.button = new BasicButton(64, 32, "", 2, ColorPalette.SECONDARY);
            this.button.setToggleable(true);
            this.button.setToggled(hud.isEnabled());
            this.button.setClickAction(() -> {
                try {
                    boolean next = !hud.isEnabled();
                    ENABLED_FIELD.setBoolean(hud, next);
                    this.button.setToggled(next);
                    if (ElaraConfig.INSTANCE != null) {
                        ElaraConfig.INSTANCE.save();
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("[Elara] HudToggleOption toggle failed: " + e);
                }
            });
        }

        @Override
        public void draw(long vg, int x, int y, InputHandler inputHandler) {
            NanoVGHelper nvg = NanoVGHelper.INSTANCE;
            boolean enabled = hud.isEnabled();
            this.button.setToggled(enabled);
            nvg.drawText(vg, this.name, (float) x, (float) (y + 17), this.nameColor, 14.0f, Fonts.MEDIUM);
            this.button.setText(enabled ? "ON" : "OFF");
            this.button.draw(vg, (float) (x + 224), (float) y, inputHandler);
        }

        @Override
        public int getHeight() {
            return 32;
        }
    }

    /**
     * A toggle bound to one of {@link MusicPlayerPage}'s static HUD flags
     * (e.g. {@code hudShowCover}). Flipping it updates the live static field
     * the {@link MusicHud} reads, then persists the whole HUD setting block
     * through {@link MusicPlayerConfig#saveHudSettings}, mirroring the
     * save path used by the in-page toggles in {@code MusicPlayerPage}.
     */
    private static class MusicHudToggleOption extends BasicOption {
        private final String fieldName;
        private final BasicButton button;

        MusicHudToggleOption(String fieldName, String name, String description) {
            super(null, null, name, description, "MusicHUD", "Display", 1);
            this.fieldName = fieldName;
            this.button = new BasicButton(64, 32, "", 2, ColorPalette.SECONDARY);
            this.button.setToggleable(true);
            this.button.setClickAction(this::toggle);
        }

        private boolean getValue() {
            try {
                Field f = MusicPlayerPage.class.getField(this.fieldName);
                return f.getBoolean(null);
            } catch (Exception e) {
                return false;
            }
        }

        private void toggle() {
            boolean next = !this.getValue();
            try {
                Field f = MusicPlayerPage.class.getField(this.fieldName);
                f.setBoolean(null, next);
            } catch (Exception e) {
                System.err.println("[Elara] MusicHudToggleOption set failed: " + e);
            }
            try {
                MusicPlayerConfig.saveHudSettings(
                        MusicPlayerPage.hudShowCover,
                        MusicPlayerPage.hudShowSpectrum,
                        MusicPlayerPage.hudShowProgress,
                        MusicPlayerPage.hudHideWhenNotPlaying,
                        MusicPlayerPage.hudScale,
                        MusicPlayerPage.hudPosX,
                        MusicPlayerPage.hudPosY);
            } catch (Throwable ignored) {
                // MusicPlayerConfig not available — value still updates live.
            }
        }

        @Override
        public void draw(long vg, int x, int y, InputHandler inputHandler) {
            NanoVGHelper nvg = NanoVGHelper.INSTANCE;
            boolean enabled = this.getValue();
            this.button.setToggled(enabled);
            nvg.drawText(vg, this.name, (float) x, (float) (y + 17), this.nameColor, 14.0f, Fonts.MEDIUM);
            this.button.setText(enabled ? "ON" : "OFF");
            this.button.draw(vg, (float) (x + 224), (float) y, inputHandler);
        }

        @Override
        public int getHeight() {
            return 32;
        }
    }
}

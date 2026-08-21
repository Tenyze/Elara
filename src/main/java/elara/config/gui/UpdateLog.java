package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;

import java.util.ArrayList;
import java.util.List;

public class UpdateLog extends BasicOption {
    private static final int PAD_X = 24;
    private static final int CONTENT_W = 960;
    private static final int CARD_SPACE = 16;
    private static final int CARD_PAD = 20;
    private static final int LINE_H = 22;

    private final List<VersionEntry> entries = new ArrayList<>();
    private final int height;

    public UpdateLog() {
        super(null, null, "", "", "Update", "Changelog", 2);
        this.entries.add(new VersionEntry("v6.3 Pre Release \"Odyssey\"", "2026-08-20",
                "Fixed AimAssist targeting error (RotationUtil angle normalization)",
                "Added ElaraLauncher: standalone WPF launcher with Java auto-detection",
                "Version isolation support (per-version mods/saves/config)",
                "Download mirror options: Official / Third-party / Mixed",
                "Offline Sign-in window with custom avatar support",
                "Music directory follows version isolation",
                "Optimized scrollbar and ComboBox styles",
                "Single-file self-contained exe build"
        ));
        this.entries.add(new VersionEntry("v6.2x", "2026-08-20",
                "Fixed Scaffold placement (blockCount initialization)",
                "Improved AimAssist: chest targeting + smooth acceleration",
                "Integrated KnockbackDelay into Knockback as Delay mode",
                "Enhanced BedPlates with 9-slice pseudo-rounded rectangles",
                "Updated Clutch with 2x2 grid scanning for landing detection",
                "Migrated MusicPlayer to independent OneConfig module (@Mod)",
                "Locked MusicPlayer to NetEase Cloud Music (no other sources)",
                "Rewrote HomeContentOption & UpdateLog with plain layout",
                "Removed obsolete modules: SprintReset, Jesus, AutoHeal, Speed"
        ));
        this.entries.add(new VersionEntry("v6.1x", "2025-08-19",
                "Added AimAssist smooth acceleration",
                "Optimized KillAura rotation physics engine",
                "Fixed Velocity reflection packet type error",
                "Improved BlockHit delayed block algorithm"
        ));
        this.entries.add(new VersionEntry("v5.1x", "2025-08-09",
                "Added Intave anti-cheat bypass support",
                "Refactored Knockback module + Delay mode",
                "Fixed most reported issues from v5.0x",
                "Optimized module card layout in OneConfig"
        ));
        this.entries.add(new VersionEntry("Indev", "2025-07-22",
                "First official release",
                "Updated some content",
                "OneConfig GUI integration"
        ));
        int h = 24 + 12 + 24 + 8 + 14 + 10 + 20;
        for (VersionEntry e : entries) {
            h += cardHeight(e) + CARD_SPACE;
        }
        this.height = h;
    }

    private static int cardHeight(VersionEntry e) {
        int head = 22 + 6 + 12; // 版本号 + 下方 6 + 条目上距
        int body = e.items.size() * LINE_H;
        return CARD_PAD + head + body + CARD_PAD;
    }

    @Override
    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        int leftX = x + PAD_X;
        float cy = y + 16;

        // ---------- 页标题 ----------
        nvg.drawText(vg, "UPDATE LOG", leftX, cy + 12, ElaraColors.accentDim(), 11F, Fonts.BOLD);
        cy += 30;
        nvg.drawText(vg, "Version History", leftX, cy + 24, ElaraColors.WHITE, 24F, Fonts.BOLD);
        cy += 24 + 8;
        nvg.drawText(vg, "Release notes & patch summaries for public builds.",
                leftX, cy + 14, ElaraColors.white60(), 14F, Fonts.MEDIUM);
        cy += 14 + 10;
        nvg.drawLine(vg, leftX, cy, leftX + CONTENT_W, cy, 1F, ElaraColors.GRAY_600);
        cy += 20;

        // ---------- 版本卡片列表（简洁卡片，无时间线装饰） ----------
        for (VersionEntry entry : entries) {
            int h = cardHeight(entry);
            nvg.drawRoundedRect(vg, x, cy, CONTENT_W, h, ElaraColors.GRAY_800, 10F);

            // 左：版本号；右：发布日期（同视觉中心线）
            nvg.drawText(vg, entry.version, leftX, cy + CARD_PAD + 21, ElaraColors.WHITE, 18F, Fonts.BOLD);
            float dateY = cy + CARD_PAD + 21 - (18F - 13F) * 0.42F;
            nvg.drawText(vg, entry.date, leftX + CONTENT_W - PAD_X - nvg.getTextWidth(vg, entry.date, 13F, Fonts.MEDIUM),
                    dateY, ElaraColors.white60(), 13F, Fonts.MEDIUM);

            float iy = cy + CARD_PAD + 22 + 6 + 12;
            for (String item : entry.items) {
                // 小圆点 (5,5) 与文本垂直居中
                float midY = iy + LINE_H / 2F;
                nvg.drawCircle(vg, leftX + 3, midY, 2.5F, ElaraColors.accentDim());
                nvg.drawText(vg, item, leftX + 14, iy + LINE_H / 2F + 11F * 0.42F,
                        ElaraColors.white90(), 12F, Fonts.MEDIUM);
                iy += LINE_H;
            }

            cy += h + CARD_SPACE;
        }
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    private static class VersionEntry {
        final String version;
        final String date;
        final List<String> items;

        VersionEntry(String v, String d, String... its) {
            this.version = v;
            this.date = d;
            this.items = java.util.Arrays.asList(its);
        }
    }
}

package elara.config.gui;

import cc.polyfrost.oneconfig.gui.OneConfigGui;
import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.SearchUtils;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.Elara;
import elara.module.Module;
import elara.module.combat.AimAssist;
import elara.module.combat.AutoAnduril;
import elara.module.combat.AutoHeal;
import elara.module.combat.BlockHit;
import elara.module.combat.Criticals;
import elara.module.combat.Displace;
import elara.module.combat.HitSelect;
import elara.module.combat.Hitflick;
import elara.module.combat.KillAura;
import elara.module.combat.KnockbackDelay;
import elara.module.combat.Reach;
import elara.module.combat.SmartAttack;
import elara.module.combat.SprintReset;
import elara.module.combat.SuperKnockback;
import elara.module.combat.TargetStrafe;
import elara.module.combat.KnockbackLegacy;
import elara.module.combat.Knockback;
import elara.module.combat.Wtap;
import elara.module.exploit.BackTrack;
import elara.module.exploit.Blink;
import elara.module.exploit.BlinkSettings;
import elara.module.exploit.FakeLag;
import elara.module.exploit.FastBow;
import elara.module.exploit.FastPlace;
import elara.module.exploit.LagRange;
import elara.module.exploit.NoHitDelay;
import elara.module.exploit.NoHurtCam;
import elara.module.exploit.NoJumpDelay;
import elara.module.exploit.NoRotate;
import elara.module.exploit.NoSlow;
import elara.module.exploit.ServerLag;
import elara.module.exploit.Timer;
import elara.module.misc.*;
import elara.module.movement.AntiVoid;
import elara.module.movement.Clutch;
import elara.module.movement.Eagle;
import elara.module.movement.Fly;
import elara.module.movement.Jesus;
import elara.module.movement.KeepSprint;
import elara.module.movement.LongJump;
import elara.module.movement.NoFall;
import elara.module.movement.SafeWalk;
import elara.module.movement.Speed;
import elara.module.movement.Sprint;
import elara.module.movement.Stasis;
import elara.module.movement.AutoMLG;
import elara.module.render.Chams;
import elara.module.render.ESP;
import elara.module.render.HUD;
import elara.module.combat.HitBox;
import elara.module.render.Indicators;
import elara.module.render.NameTags;
import elara.module.render.PotionHUD;
import elara.module.render.TargetHUD;
import elara.module.render.Tracers;
import elara.module.render.Trajectories;
import elara.module.render.WaterMark;
import elara.module.render.CombatVisuals;
import elara.module.render.ItemGlow;
import elara.module.render.ShaderESP;
import elara.module.utility.AutoClicker;
import elara.module.utility.AutoTool;
import elara.module.utility.ChestStealer;
import elara.module.utility.GhostHand;
import elara.module.utility.InvManager;
import elara.module.utility.InvWalk;
import elara.module.utility.InventoryClicker;
import elara.module.utility.MoreKB;
import elara.module.utility.Piercing;
import elara.module.utility.Refill;
import elara.module.utility.Spammer;
import elara.module.world.AutoBlockIn;
import elara.module.world.BedBreaker;
import elara.module.world.BedESP;
import elara.module.world.BedTracker;
import elara.module.world.ChestESP;
import elara.module.world.FullBright;
import elara.module.world.ItemESP;
import elara.module.world.Scaffold;
import elara.module.world.SpeedMine;
import elara.module.world.Telly;
import elara.module.world.Xray;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElaraModulesPage extends Page {
   private static final String[] CATEGORIES = new String[]{"Combat", "Movement", "Render", "Utility", "World", "Exploit", "Misc", "Non"};
   private static final Map<Class<? extends Module>, String> MODULE_CATEGORIES = new LinkedHashMap<>();
   private final Map<String, List<ModuleCard>> categoryCards = new LinkedHashMap<>();
   private final ArrayList<BasicButton> categoryButtons = new ArrayList<>();
   private int totalSize = 728;
   private String selectedCategory = "Combat";

   public ElaraModulesPage() {
      super("Modules");
      this.buildCategoryButtons();
      this.buildCards();
   }

   private void buildCards() {
      this.categoryCards.clear();

      for (String cat : CATEGORIES) {
         this.categoryCards.put(cat, new ArrayList<>());
      }

      if (Elara.moduleManager != null) {
         for (Module module : Elara.moduleManager.modules.values()) {
            String category = MODULE_CATEGORIES.get(module.getClass());
            if (category != null) {
               this.categoryCards.get(category).add(new ModuleCard(module));
            }
         }

         for (List<ModuleCard> list : this.categoryCards.values()) {
            list.sort(Comparator.comparing(c -> c.getModule().getName().toLowerCase()));
         }
      }
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

            this.scrollTarget = 0.0F;
            this.scrollAnimation = null;
            this.buildCards();
         });
         this.categoryButtons.add(btn);
      }
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
      int iX = x + 16;
      int iY = y + 72;
      int count = 0;
      String filter = OneConfigGui.INSTANCE == null ? "" : OneConfigGui.INSTANCE.getSearchValue().toLowerCase().trim();
      boolean searching = !filter.isEmpty();
      List<ModuleCard> cards = this.categoryCards.get(this.selectedCategory);
      if (cards == null) {
         cards = new ArrayList<>();
      }

      if (searching) {
         List<ModuleCard> filtered = new ArrayList<>();

         for (String cat : CATEGORIES) {
            List<ModuleCard> catCards = this.categoryCards.get(cat);
            if (catCards != null) {
               for (ModuleCard card : catCards) {
                  if (SearchUtils.isSimilar(card.getModule().getName(), filter)) {
                     filtered.add(card);
                  }
               }
            }
         }

         cards = filtered;
      }

      if (cards != null && !cards.isEmpty()) {
         int rowX = iX;

         for (ModuleCard card : cards) {
            count++;
            if (iY + 135 >= y - this.scroll && iY <= y + 728 - this.scroll) {
               card.draw(vg, rowX, iY, inputHandler);
            }

            rowX += 260;
            if (rowX > x + 796) {
               rowX = iX;
               iY += 135;
            }
         }
      }

      if (count == 0) {
         nanoVGHelper.drawText(vg, "No modules in this category.", x + 16, y + 72, ElaraColors.white60(), 14.0F, Fonts.MEDIUM);
         this.totalSize = 200;
      } else {
         this.totalSize = iY - y + 135;
      }
   }

   public int drawStatic(long vg, int x, int y, InputHandler inputHandler) {
      int iX = x + 16;
      NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
      boolean searching = OneConfigGui.INSTANCE != null && !OneConfigGui.INSTANCE.getSearchValue().trim().isEmpty();

      for (BasicButton btn : this.categoryButtons) {
         if (btn.getWidth() == 0) {
            btn.setWidth((int)(Math.ceil(nanoVGHelper.getTextWidth(vg, btn.getText(), 12.0F, Fonts.MEDIUM) / 8.0F) * 8.0 + 16.0));
         }

         if (searching) {
            btn.setToggled(SearchUtils.isSimilar(btn.getText(), OneConfigGui.INSTANCE.getSearchValue()));
            btn.draw(vg, iX, y + 16, inputHandler);
            btn.setToggled(btn.getText().equals(this.selectedCategory));
         } else {
            btn.draw(vg, iX, y + 16, inputHandler);
         }

         iX += btn.getWidth() + 8;
      }

      return 60;
   }

   public int getMaxScrollHeight() {
      return Math.max(this.totalSize, 728);
   }

   public boolean isBase() {
      return false;
   }

   static {
      MODULE_CATEGORIES.put(AimAssist.class, "Combat");
      MODULE_CATEGORIES.put(AutoClicker.class, "Combat");
      MODULE_CATEGORIES.put(KillAura.class, "Combat");
      MODULE_CATEGORIES.put(Wtap.class, "Combat");
      MODULE_CATEGORIES.put(Knockback.class, "Combat");
      MODULE_CATEGORIES.put(Reach.class, "Combat");
      MODULE_CATEGORIES.put(TargetStrafe.class, "Combat");
      MODULE_CATEGORIES.put(NoHitDelay.class, "Combat");
      MODULE_CATEGORIES.put(AntiFireball.class, "Combat");
      MODULE_CATEGORIES.put(LagRange.class, "Combat");
      MODULE_CATEGORIES.put(HitBox.class, "Combat");
      MODULE_CATEGORIES.put(MoreKB.class, "Combat");
      MODULE_CATEGORIES.put(Refill.class, "Combat");
      MODULE_CATEGORIES.put(HitSelect.class, "Combat");
      MODULE_CATEGORIES.put(BlockHit.class, "Combat");
      MODULE_CATEGORIES.put(Criticals.class, "Combat");
      MODULE_CATEGORIES.put(Hitflick.class, "Combat");
      MODULE_CATEGORIES.put(KnockbackDelay.class, "Combat");
      MODULE_CATEGORIES.put(Piercing.class, "Combat");
      MODULE_CATEGORIES.put(SuperKnockback.class, "Combat");
      MODULE_CATEGORIES.put(SmartAttack.class, "Combat");
      MODULE_CATEGORIES.put(SprintReset.class, "Combat");
      MODULE_CATEGORIES.put(Displace.class, "Combat");
      MODULE_CATEGORIES.put(KnockbackLegacy.class,"Combat");
      MODULE_CATEGORIES.put(AutoMLG.class,"Movement");
      MODULE_CATEGORIES.put(Fly.class, "Movement");
      MODULE_CATEGORIES.put(Speed.class, "Movement");
      MODULE_CATEGORIES.put(LongJump.class, "Movement");
      MODULE_CATEGORIES.put(Sprint.class, "Movement");
      MODULE_CATEGORIES.put(SafeWalk.class, "Movement");
      MODULE_CATEGORIES.put(Jesus.class, "Movement");
      MODULE_CATEGORIES.put(Blink.class, "Movement");
      MODULE_CATEGORIES.put(NoFall.class, "Movement");
      MODULE_CATEGORIES.put(NoSlow.class, "Movement");
      MODULE_CATEGORIES.put(KeepSprint.class, "Movement");
      MODULE_CATEGORIES.put(Clutch.class, "Movement");
      MODULE_CATEGORIES.put(Eagle.class, "Movement");
      MODULE_CATEGORIES.put(NoJumpDelay.class, "Movement");
      MODULE_CATEGORIES.put(AntiVoid.class, "Movement");
      MODULE_CATEGORIES.put(ESP.class, "Render");
      MODULE_CATEGORIES.put(Chams.class, "Render");
      MODULE_CATEGORIES.put(FullBright.class, "Render");
      MODULE_CATEGORIES.put(Tracers.class, "Render");
      MODULE_CATEGORIES.put(NameTags.class, "Render");
      MODULE_CATEGORIES.put(Xray.class, "Render");
      MODULE_CATEGORIES.put(TargetHUD.class, "Render");
      MODULE_CATEGORIES.put(Indicators.class, "Render");
      MODULE_CATEGORIES.put(BedESP.class, "Render");
      MODULE_CATEGORIES.put(ItemESP.class, "Render");
      MODULE_CATEGORIES.put(ViewClip.class, "Render");
      MODULE_CATEGORIES.put(NoHurtCam.class, "Render");
      MODULE_CATEGORIES.put(HUD.class, "Render");
      MODULE_CATEGORIES.put(CombatVisuals.class,"Render");
      MODULE_CATEGORIES.put(ItemGlow.class,"Render");
      MODULE_CATEGORIES.put(ShaderESP.class,"Render");
      MODULE_CATEGORIES.put(GuiModule.class, "Render");
      MODULE_CATEGORIES.put(ChestESP.class, "Render");
      MODULE_CATEGORIES.put(Trajectories.class, "Render");
      MODULE_CATEGORIES.put(PotionHUD.class, "Render");
      MODULE_CATEGORIES.put(WaterMark.class, "Render");
      MODULE_CATEGORIES.put(AutoHeal.class, "Utility");
      MODULE_CATEGORIES.put(AutoTool.class, "Utility");
      MODULE_CATEGORIES.put(ChestStealer.class, "Utility");
      MODULE_CATEGORIES.put(InvManager.class, "Utility");
      MODULE_CATEGORIES.put(InvWalk.class, "Utility");
      MODULE_CATEGORIES.put(SpeedMine.class, "Utility");
      MODULE_CATEGORIES.put(AntiDebuff.class, "Utility");
      MODULE_CATEGORIES.put(InventoryClicker.class, "Utility");
      MODULE_CATEGORIES.put(FastPlace.class, "World");
      MODULE_CATEGORIES.put(Scaffold.class, "World");
      MODULE_CATEGORIES.put(AutoBlockIn.class, "World");
      MODULE_CATEGORIES.put(BedBreaker.class, "World");
      MODULE_CATEGORIES.put(BedTracker.class, "World");
      MODULE_CATEGORIES.put(Telly.class, "World");
      MODULE_CATEGORIES.put(BackTrack.class, "Exploit");
      MODULE_CATEGORIES.put(BlinkSettings.class, "Exploit");
      MODULE_CATEGORIES.put(FakeLag.class, "Exploit");
      MODULE_CATEGORIES.put(GhostHand.class, "Exploit");
      MODULE_CATEGORIES.put(Timer.class, "Exploit");
      MODULE_CATEGORIES.put(FastBow.class, "Exploit");
      MODULE_CATEGORIES.put(Disabler.class, "Exploit");
      MODULE_CATEGORIES.put(Spammer.class, "Misc");
      MODULE_CATEGORIES.put(Teams.class, "Misc");
      MODULE_CATEGORIES.put(NoRotate.class, "Misc");
      MODULE_CATEGORIES.put(NickHider.class, "Misc");
      MODULE_CATEGORIES.put(AntiObbyTrap.class, "Misc");
      MODULE_CATEGORIES.put(AntiObfuscate.class, "Misc");
      MODULE_CATEGORIES.put(AutoAnduril.class, "Misc");
      MODULE_CATEGORIES.put(ClientSpoofer.class, "Misc");
      MODULE_CATEGORIES.put(FlagDetector.class, "Misc");
      MODULE_CATEGORIES.put(ServerLag.class, "Misc");
      MODULE_CATEGORIES.put(Stasis.class, "Misc");
      MODULE_CATEGORIES.put(HackerDetector.class, "Misc");
      MODULE_CATEGORIES.put(AntiBot.class, "Misc");
      MODULE_CATEGORIES.put(MCF.class, "Misc");
   }
}

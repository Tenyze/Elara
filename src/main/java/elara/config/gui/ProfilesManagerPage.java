package elara.config.gui;

import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.text.TextInputField;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Font;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.renderer.scissor.Scissor;
import cc.polyfrost.oneconfig.renderer.scissor.ScissorHelper;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.config.Config;
import elara.config.NotificationHelper;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProfilesManagerPage extends Page {
   private static final int ROW_HEIGHT = 40;
   private static final int MAX_VISIBLE_ROWS = 10;
   private final List<String> profiles = new ArrayList<>();
   private long lastScan = 0L;
   private int selectedProfile = -1;
   private boolean wasMouseDown = false;
   private int totalSize = 728;
   private final TextInputField nameInput;
   private final BasicButton saveBtn;
   private final BasicButton loadBtn;
   private final BasicButton deleteBtn;
   private final BasicButton folderBtn;
   private final BasicButton resetBtn;

   public ProfilesManagerPage() {
      super("Profiles");
      this.scanProfiles();
      this.nameInput = new TextInputField(280, 36, "Profile name", false, false);
      this.nameInput.setInput("default");
      this.saveBtn = new BasicButton(100, 36, "Save", 2, ColorPalette.PRIMARY);
      this.saveBtn.setClickAction(() -> {
         String name = this.getInputName();
         Config config = new Config(name, false);
         config.save();
         NotificationHelper.sendProfileSaved(name);
         this.scanProfiles();
      });
      this.loadBtn = new BasicButton(100, 36, "Load", 2, ColorPalette.PRIMARY);
      this.loadBtn.setClickAction(() -> {
         String selected = this.getSelectedProfile();
         String name = selected != null ? selected : this.getInputName();
         Config config = new Config(name, false);
         config.load();
         this.nameInput.setInput(name);
         NotificationHelper.sendProfileLoaded(name);
      });
      this.deleteBtn = new BasicButton(100, 36, "Delete", 2, ColorPalette.SECONDARY);
      this.deleteBtn.setClickAction(() -> {
         String selected = this.getSelectedProfile();
         if (selected != null && !selected.equals("default")) {
            File f = new File("./config/Elara/" + selected + ".json");
            if (f.exists()) {
               f.delete();
            }

            NotificationHelper.sendProfileDeleted(selected);
            this.scanProfiles();
         }
      });
      this.folderBtn = new BasicButton(120, 36, "Open Folder", 2, ColorPalette.SECONDARY);
      this.folderBtn.setClickAction(() -> {
         try {
            File dir = new File("./config/Elara/");
            if (!dir.exists()) {
               dir.mkdirs();
            }

            if (Desktop.isDesktopSupported()) {
               Desktop.getDesktop().open(dir);
            } else {
               Runtime.getRuntime().exec("explorer.exe \"" + dir.getAbsolutePath() + "\"");
            }
         } catch (Throwable var1) {
         }
      });
      this.resetBtn = new BasicButton(120, 36, "Reset Config", 2, ColorPalette.SECONDARY);
      this.resetBtn.setClickAction(() -> {
         Config.resetAll(false);
         NotificationHelper.sendSuccess("Profiles", "All settings reset to defaults");
      });
   }

   private String getInputName() {
      String input = this.nameInput.getInput();
      return input != null && !input.trim().isEmpty() ? input.trim() : "default";
   }

   public String getSelectedProfile() {
      return this.selectedProfile >= 0 && this.selectedProfile < this.profiles.size() ? this.profiles.get(this.selectedProfile) : null;
   }

   private void scanProfiles() {
      this.profiles.clear();
      File dir = new File("./config/Elara/");
      File[] files;
      if (dir.exists() && dir.isDirectory() && (files = dir.listFiles((d, n) -> n.endsWith(".json"))) != null) {
         for (File f : files) {
            String n2 = f.getName();
            this.profiles.add(n2.substring(0, n2.length() - 5));
         }
      }

      this.profiles.sort(String.CASE_INSENSITIVE_ORDER);
      if (this.selectedProfile >= this.profiles.size()) {
         this.selectedProfile = -1;
      }
   }

   private void refreshIfNeeded() {
      long now = System.currentTimeMillis();
      if (now - this.lastScan > 2000L) {
         this.scanProfiles();
         this.lastScan = now;
      }
   }

   private float centerTextY(float boxY, float boxH, float fontSize, Font font) {
      return boxY + (boxH - fontSize) / 2.0F + fontSize * 0.75F;
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      this.refreshIfNeeded();
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      ScissorHelper scissorHelper = ScissorHelper.INSTANCE;
      int cy = y + 72;
      nvg.drawText(vg, "PROFILES", x + 40, cy, ElaraColors.accentDim(), 11.0F, Fonts.BOLD);
      nvg.drawText(vg, "Configuration Profiles", x + 40, cy + 24, ElaraColors.WHITE, 20.0F, Fonts.BOLD);
      nvg.drawLine(vg, x + 40, cy + 52, x + 960, cy + 52, 1.0F, ElaraColors.GRAY_700);
      cy += 72;
      int listX = x + 40;
      int listW = 400;
      int listH = Math.min(this.profiles.size(), 10) * 40;
      if (this.profiles.isEmpty()) {
         listH = 40;
      }

      nvg.drawText(vg, "SAVED PROFILES", listX, cy - 2, ElaraColors.accentDim(), 10.0F, Fonts.BOLD);
      String countStr = this.profiles.size() + " profiles";
      float countW = nvg.getTextWidth(vg, countStr, 10.0F, Fonts.MEDIUM);
      nvg.drawText(vg, countStr, listX + listW - countW, cy - 2, ElaraColors.white60(), 10.0F, Fonts.BOLD);
      cy += 12;
      if (this.profiles.isEmpty()) {
         nvg.drawRoundedRect(vg, listX, cy, listW, 40.0F, ElaraColors.GRAY_800, 6.0F);
         float emptyY = this.centerTextY(cy, 40.0F, 13.0F, Fonts.MEDIUM);
         float emptyW = nvg.getTextWidth(vg, "No saved profiles", 13.0F, Fonts.MEDIUM);
         nvg.drawText(vg, "No saved profiles", listX + (listW - emptyW) / 2.0F, emptyY, ElaraColors.white60(), 13.0F, Fonts.MEDIUM);
      } else {
         Scissor scissor = scissorHelper.scissor(vg, listX, cy, listW, listH);
         boolean isMouseDown = inputHandler.isMouseDown();
         boolean justClicked = !this.wasMouseDown && isMouseDown;
         this.wasMouseDown = isMouseDown;

         for (int i = 0; i < this.profiles.size(); i++) {
            String profileName = this.profiles.get(i);
            int rowY = cy + i * 40;
            if (rowY + 40 >= cy && rowY <= cy + listH) {
               boolean isHovered = inputHandler.mouseX() >= listX
                  && inputHandler.mouseX() <= listX + listW
                  && inputHandler.mouseY() >= rowY
                  && inputHandler.mouseY() <= rowY + 40 - 2;
               boolean isSelected = i == this.selectedProfile;
               int bgColor = isSelected
                  ? ElaraColors.accent()
                  : (isHovered ? ElaraColors.GRAY_700 : (i % 2 == 0 ? ElaraColors.GRAY_750 : ElaraColors.GRAY_800));
               nvg.drawRoundedRect(vg, listX, rowY, listW, 38.0F, bgColor, 4.0F);
               int textColor = isSelected ? ElaraColors.WHITE : ElaraColors.white90();
               float textY = this.centerTextY(rowY, 38.0F, 14.0F, Fonts.MEDIUM);
               nvg.drawText(vg, profileName, listX + 16, textY, textColor, 14.0F, Fonts.MEDIUM);
               if (isHovered && justClicked) {
                  this.selectedProfile = i;
                  this.nameInput.setInput(profileName);
               }
            }
         }

         scissorHelper.resetScissor(vg, scissor);
      }

      int rightX = x + 480;
      int rightW = 480;
      int rightY = cy;
      nvg.drawText(vg, "ACTIONS", rightX, rightY - 2, ElaraColors.accentDim(), 10.0F, Fonts.BOLD);
      rightY += 12;
      float nameLabelY = this.centerTextY(rightY, 20.0F, 12.0F, Fonts.MEDIUM);
      nvg.drawText(vg, "Profile Name", rightX, nameLabelY, ElaraColors.white60(), 12.0F, Fonts.MEDIUM);
      float var10002 = rightX;
      rightY += 24;
      this.nameInput.draw(vg, var10002, rightY, inputHandler);
      var10002 = rightX;
      rightY += 48;
      this.saveBtn.draw(vg, var10002, rightY, inputHandler);
      this.loadBtn.draw(vg, rightX + 120, rightY, inputHandler);
      this.deleteBtn.draw(vg, rightX + 240, rightY, inputHandler);
      var10002 = rightX;
      rightY += 52;
      this.folderBtn.draw(vg, var10002, rightY, inputHandler);
      this.resetBtn.draw(vg, rightX + 140, rightY, inputHandler);
      rightY += 52;
      float infoY = this.centerTextY(rightY, 20.0F, 12.0F, Fonts.MEDIUM);
      String selected = this.getSelectedProfile();
      if (selected != null) {
         nvg.drawText(vg, "Selected: " + selected, rightX, infoY, ElaraColors.white60(), 12.0F, Fonts.MEDIUM);
      } else {
         nvg.drawText(vg, "Click a profile to select", rightX, infoY, ElaraColors.white60(), 12.0F, Fonts.MEDIUM);
      }

      this.totalSize = Math.max(listH + 100, rightY + 40) + cy - y;
   }

   public void keyTyped(char key, int keyCode) {
      this.nameInput.keyTyped(key, keyCode);
   }

   public int drawStatic(long vg, int x, int y, InputHandler inputHandler) {
      return 0;
   }

   public int getMaxScrollHeight() {
      return Math.max(this.totalSize, 728);
   }

   public boolean isBase() {
      return false;
   }
}

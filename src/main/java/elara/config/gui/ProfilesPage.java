package elara.config.gui;

import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.config.Config;
import elara.config.NotificationHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfilesPage extends Page {
   private static final int WHITE = -1;
   private static final int WHITE_60 = -1711276033;
   private static final int GRAY_300 = -13421773;
   private static final int GRAY_800 = -15066598;
   private final File configDir = new File("./config/Elara/");
   private final List<ProfilesPage.ProfileEntry> entries = new ArrayList<>();
   private final BasicButton createButton = new BasicButton(80, 32, "Create", 2, ColorPalette.PRIMARY);
   private int totalSize = 728;
   private String newProfileName = "";
   private boolean editingNewProfile = false;

   public ProfilesPage() {
      super("Profiles");
      this.loadProfiles();
      this.createButton.setClickAction(() -> {
         if (!this.newProfileName.trim().isEmpty()) {
            this.createProfile(this.newProfileName.trim());
            this.newProfileName = "";
            this.editingNewProfile = false;
         }
      });
   }

   private void loadProfiles() {
      this.entries.clear();
      if (!this.configDir.exists()) {
         this.configDir.mkdirs();
      } else {
         File[] files = this.configDir.listFiles((dir, name) -> name.endsWith(".json"));
         if (files != null) {
            for (File file : files) {
               String name2 = file.getName().replace(".json", "");
               this.entries.add(new ProfilesPage.ProfileEntry(name2, file));
            }

            Collections.sort(this.entries, (a, b) -> a.name.compareToIgnoreCase(b.name));
         }
      }
   }

   private void createProfile(String name) {
      if (!name.contains("/") && !name.contains("\\")) {
         File file = new File(this.configDir, name + ".json");
         if (file.exists()) {
            NotificationHelper.send("Profiles", "Profile already exists!", null);
         } else {
            Config config = new Config(name, true);
            config.save();
            NotificationHelper.send("Profiles", "Created: " + name, null);
            this.loadProfiles();
         }
      }
   }

   private void loadProfile(String name) {
      Config config = new Config(name, false);
      config.load();
      NotificationHelper.send("Profiles", "Loaded: " + name, null);
   }

   private void saveToProfile(String name) {
      Config config = new Config(name, false);
      config.save();
      NotificationHelper.send("Profiles", "Saved to: " + name, null);
   }

   private void deleteProfile(ProfilesPage.ProfileEntry entry) {
      if (entry.file.delete()) {
         NotificationHelper.send("Profiles", "Deleted: " + entry.name, null);
         this.loadProfiles();
      }
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
      int iX = x + 16;
      int iY = y + 72;
      nanoVGHelper.drawText(vg, "Create New Profile:", iX, iY, -1, 14.0F, Fonts.MEDIUM);
      float var10002 = iX;
      iY += 40;
      nanoVGHelper.drawRoundedRect(vg, var10002, iY, 260.0F, 32.0F, -15066598, 8.0F);
      nanoVGHelper.drawText(
         vg,
         this.editingNewProfile ? this.newProfileName : "Enter profile name...",
         iX + 12,
         iY + 25,
         this.editingNewProfile ? -1 : -1711276033,
         14.0F,
         Fonts.MEDIUM
      );
      if (inputHandler.isAreaClicked(iX, iY, 260.0F, 32.0F)) {
         this.editingNewProfile = true;
      }

      this.createButton.draw(vg, iX + 276, iY, inputHandler);
      var10002 = iX;
      iY += 48;
      nanoVGHelper.drawLine(vg, var10002, iY, iX + 796, iY, 2.0F, -13421773);
      float var10003 = iX;
      iY += 24;
      nanoVGHelper.drawText(vg, "Saved Profiles:", var10003, iY, -1, 14.0F, Fonts.MEDIUM);
      iY += 32;
      if (this.entries.isEmpty()) {
         nanoVGHelper.drawText(vg, "No profiles found.", iX, iY, -1711276033, 14.0F, Fonts.MEDIUM);
         this.totalSize = iY - y + 50;
      } else {
         for (ProfilesPage.ProfileEntry entry : this.entries) {
            if (iY + 48 >= y - this.scroll && iY <= y + 728 - this.scroll) {
               nanoVGHelper.drawRoundedRect(vg, iX, iY, 800.0F, 40.0F, -15066598, 8.0F);
               int textX = iX + 12;
               int textY = iY + 25;
               nanoVGHelper.drawText(vg, entry.name, textX, textY, -1, 14.0F, Fonts.MEDIUM);
               int btnX = iX + 800 - 260;
               entry.loadButton.draw(vg, btnX, iY + 4, inputHandler);
               entry.saveButton.draw(vg, btnX + 88, iY + 4, inputHandler);
               entry.deleteButton.draw(vg, btnX + 176, iY + 4, inputHandler);
            }

            iY += 52;
         }

         this.totalSize = iY - y + 50;
      }
   }

   public void keyTyped(char key, int keyCode) {
      if (this.editingNewProfile) {
         if (keyCode == 14 && !this.newProfileName.isEmpty()) {
            this.newProfileName = this.newProfileName.substring(0, this.newProfileName.length() - 1);
         } else if (keyCode != 14 && keyCode != 28 && keyCode != 1 && key >= ' ' && key <= '~') {
            this.newProfileName = this.newProfileName + key;
         }
      }

      super.keyTyped(key, keyCode);
   }

   public int getMaxScrollHeight() {
      return Math.max(this.totalSize, 728);
   }

   public boolean isBase() {
      return false;
   }

   private class ProfileEntry {
      final String name;
      final File file;
      final BasicButton loadButton;
      final BasicButton saveButton;
      final BasicButton deleteButton;

      ProfileEntry(String name, File file) {
         this.name = name;
         this.file = file;
         this.loadButton = new BasicButton(80, 32, "Load", 2, ColorPalette.PRIMARY);
         this.loadButton.setClickAction(() -> ProfilesPage.this.loadProfile(name));
         this.saveButton = new BasicButton(80, 32, "Save", 2, ColorPalette.SECONDARY);
         this.saveButton.setClickAction(() -> ProfilesPage.this.saveToProfile(name));
         this.deleteButton = new BasicButton(80, 32, "Delete", 2, ColorPalette.SECONDARY);
         this.deleteButton.setClickAction(() -> ProfilesPage.this.deleteProfile(this));
      }
   }
}

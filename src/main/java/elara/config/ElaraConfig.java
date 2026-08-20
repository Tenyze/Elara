package elara.config;

import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Page;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.PageLocation;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionCategory;
import cc.polyfrost.oneconfig.config.elements.OptionSubcategory;
import elara.config.gui.AccountManagerPage;
import elara.config.gui.DebugPage;
import elara.config.gui.ElaraHudPage;
import elara.config.gui.ElaraModulesPage;
import elara.config.gui.PotionHud;
import elara.config.gui.SessionInfoHud;
import elara.config.gui.TargetHud;
import elara.config.music.MusicPlayerConfig;
import elara.event.EventManager;
import java.util.LinkedHashMap;

public class ElaraConfig extends cc.polyfrost.oneconfig.config.Config {
   public static ElaraConfig INSTANCE;
   @Page(name = "Modules", description = "Browse all modules", location = PageLocation.TOP)
   public ElaraModulesPage modulesPage;
   @Page(name = "Accounts", description = "Manage Minecraft accounts", location = PageLocation.TOP)
   public AccountManagerPage accountsPage;
   @Page(name = "Profiles", description = "Manage configuration profiles", location = PageLocation.TOP)
   public cc.polyfrost.oneconfig.gui.pages.Page profilesPage;
   @Page(name = "HUD", description = "Elara HUD settings", location = PageLocation.TOP)
   public cc.polyfrost.oneconfig.gui.pages.Page hudPage;
   @Page(name = "Debug", description = "Developer tools and debugging", location = PageLocation.TOP)
   public cc.polyfrost.oneconfig.gui.pages.Page debugPage;
   @HUD(name = "Music HUD", category = "Music")
   public elara.config.gui.MusicHud musicHud;
   @HUD(name = "Potion HUD", category = "ElaraHUD")
   public PotionHud potionHud;
   @HUD(name = "Target HUD", category = "ElaraHUD")
   public TargetHud targetHud;
   @HUD(name = "Session HUD", category = "ElaraHUD")
   public SessionInfoHud sessionHud;

   public ElaraConfig() {
      super(new Mod("Elara", ModType.PVP), "elara/oneconfig.json");

      try {
         this.modulesPage = new ElaraModulesPage();
      } catch (Throwable e) {
         System.err.println("[Elara] ModulesPage init failed: " + e);
      }

      try {
         this.accountsPage = new AccountManagerPage();
      } catch (Throwable e) {
         System.err.println("[Elara] AccountsPage init failed: " + e);
      }

      this.initProfilesPage();
      this.initHudPage();
      this.initMusicSystem();
      this.initDebugPage();
      this.initialize();
      this.hudPage = new ElaraHudPage();
      if (this.potionHud == null) {
         this.potionHud = new PotionHud();
      }

      if (this.targetHud == null) {
         this.targetHud = new TargetHud();
      }

      if (this.musicHud == null) {
         this.musicHud = new elara.config.gui.MusicHud();
      }

      if (this.sessionHud == null) {
         this.sessionHud = new SessionInfoHud();
      }

      try {
         if (this.targetHud != null) {
            EventManager.register(this.targetHud);
         }
      } catch (Throwable e) {
         System.err.println("[Elara] TargetHud register failed: " + e);
      }

      try {
         this.mod.defaultPage.categories.remove("Music");
      } catch (Throwable var9) {
      }

      try {
         this.mod.defaultPage.categories.remove("ElaraHUD");
      } catch (Throwable var8) {
      }

      try {
         OptionCategory homeCat = (OptionCategory)this.mod.defaultPage.categories.get("Home");
         if (homeCat != null) {
            homeCat.subcategories.removeIf(s -> "Profiles".equals(s.getName()));
         }
      } catch (Throwable var7) {
      }

      try {
         this.registerHomeContent();
      } catch (Throwable e) {
         System.err.println("[Elara] Home content init failed: " + e.getMessage());
      }

      try {
         this.registerUpdateLog();
      } catch (Throwable e) {
         System.err.println("[Elara] UpdateLog init failed: " + e.getMessage());
      }

      try {
         LinkedHashMap<String, OptionCategory> cats = this.mod.defaultPage.categories;
         OptionCategory homeCat = cats.remove("Home");
         OptionCategory updateCat = cats.remove("Update");
         OptionCategory generalCat = cats.remove("General");
         LinkedHashMap<String, OptionCategory> reordered = new LinkedHashMap<>();
         if (homeCat != null) {
            reordered.put("Home", homeCat);
         }

         if (updateCat != null) {
            reordered.put("Update", updateCat);
         }

         if (generalCat != null) {
            reordered.put("General", generalCat);
         }

         reordered.putAll(cats);
         cats.clear();
         cats.putAll(reordered);
      } catch (Throwable var5) {
      }
   }

   private void initProfilesPage() {
      try {
         Class<?> pageClass = Class.forName("elara.config.gui.ProfilesManagerPage");
         this.profilesPage = (cc.polyfrost.oneconfig.gui.pages.Page)pageClass.getConstructor().newInstance();
      } catch (Throwable e) {
         System.err.println("[Elara] ProfilesPage init failed: " + e);
      }
   }

   private void initHudPage() {
      try {
         this.hudPage = new ElaraHudPage();
      } catch (Throwable e) {
         System.err.println("[Elara] HudPage init failed: " + e);
         e.printStackTrace();
      }
   }

   private void initMusicSystem() {
      try {
         MusicPlayerConfig.init();
      } catch (Throwable e) {
         System.err.println("[Elara] MusicPlayerConfig init failed: " + e);
      }

      try {
         Class.forName("elara.config.music.MusicPlayerManager").getMethod("init").invoke(null);
      } catch (Throwable e) {
         System.err.println("[Elara] MusicPlayerManager init failed: " + e);
      }
   }

   private void initDebugPage() {
      try {
         this.debugPage = new DebugPage();
      } catch (Throwable e) {
         System.err.println("[Elara] DebugPage init failed: " + e);
      }
   }

   private void registerHomeContent() {
      try {
         Class<?> optClass = Class.forName("elara.config.gui.HomeContentOption");
         Object opt = optClass.getConstructor().newInstance();
         OptionSubcategory sub = ConfigUtils.getSubCategory(this.mod.defaultPage, "Home", "About");
         sub.options.add(0, (BasicOption)opt);
      } catch (Throwable var4) {
      }
   }

   private void registerUpdateLog() {
      try {
         Class<?> optClass = Class.forName("elara.config.gui.UpdateLog");
         Object opt = optClass.getConstructor().newInstance();
         OptionSubcategory sub = ConfigUtils.getSubCategory(this.mod.defaultPage, "Update", "Changelog");
         sub.options.add(0, (BasicOption)opt);
      } catch (Throwable var4) {
      }
   }

   public static void init() {
      if (INSTANCE == null) {
         try {
            INSTANCE = new ElaraConfig();
         } catch (Throwable t) {
            System.err.println("[Elara] Failed to initialize OneConfig: " + t);
            t.printStackTrace();
         }
      }
   }
} 
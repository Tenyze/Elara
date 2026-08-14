package elara.config.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class MusicPlayerConfig {
   public static MusicPlayerConfig INSTANCE;

   // ==================== 配置字段 ====================
   public ApiProvider apiProvider = ApiProvider.MUSIC_163_CN;
   public String customApiUrl = "";
   public LoginMethod loginMethod = LoginMethod.QR_CODE;
   public String phoneNumber = "";
   public String password = "";

   public float defaultVolume = 0.8F;
   public int defaultBitrate = 320000;
   public boolean autoPlayOnStart = false;

   public boolean enableCache = true;
   public int maxCacheSizeMB = 2048;

   public MusicListSource musicListSource = MusicListSource.HOT_SONGS;

   public float windowPosX = 0.0F;
   public float windowPosY = 0.0F;
   public int windowWidth = 960;
   public int windowHeight = 768;

   // ==================== 旧HUD字段（保持兼容） ====================
   public boolean hudShowCover = true;
   public boolean hudShowSpectrum = true;
   public boolean hudShowProgress = true;
   public boolean hudHideWhenNotPlaying = false;
   public float hudScale = 1.0F;
   public float hudPosX = 0.0F;
   public float hudPosY = 0.0F;

   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_FILE = new File("./config/elara/musicplayer/config.json");

   public MusicPlayerConfig() {
      try {
         load();
      } catch (Throwable ignored) {}
   }

   public static void init() {
      if (INSTANCE == null) {
         try {
            INSTANCE = new MusicPlayerConfig();
         } catch (Throwable e) {
            System.err.println("[MusicPlayer] Failed to initialize MusicPlayerConfig: " + e.getMessage());
            e.printStackTrace();
         }
      }
   }

   public void save() {
      try {
         CONFIG_FILE.getParentFile().mkdirs();
         ConfigData data = new ConfigData();
         data.apiProvider = this.apiProvider;
         data.customApiUrl = this.customApiUrl;
         data.loginMethod = this.loginMethod;
         data.phoneNumber = this.phoneNumber;
         data.password = this.password;
         data.defaultVolume = this.defaultVolume;
         data.defaultBitrate = this.defaultBitrate;
         data.enableCache = this.enableCache;
         data.maxCacheSizeMB = this.maxCacheSizeMB;
         data.autoPlayOnStart = this.autoPlayOnStart;
         data.hudShowCover = this.hudShowCover;
         data.hudShowSpectrum = this.hudShowSpectrum;
         data.hudShowProgress = this.hudShowProgress;
         data.hudHideWhenNotPlaying = this.hudHideWhenNotPlaying;
         data.hudScale = this.hudScale;
         data.hudPosX = this.hudPosX;
         data.hudPosY = this.hudPosY;
         data.windowPosX = this.windowPosX;
         data.windowPosY = this.windowPosY;
         data.windowWidth = this.windowWidth;
         data.windowHeight = this.windowHeight;
         data.musicListSource = this.musicListSource;
         try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
         }
      } catch (Throwable e) {
         System.err.println("[MusicPlayer] Failed to save config: " + e.getMessage());
      }
   }

   public void load() {
      if (CONFIG_FILE.exists()) {
         try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
               if (data.apiProvider != null) this.apiProvider = data.apiProvider;
               if (data.customApiUrl != null) this.customApiUrl = data.customApiUrl;
               if (data.loginMethod != null) this.loginMethod = data.loginMethod;
               if (data.phoneNumber != null) this.phoneNumber = data.phoneNumber;
               if (data.password != null) this.password = data.password;
               if (data.defaultVolume != null) this.defaultVolume = data.defaultVolume;
               if (data.defaultBitrate != null) this.defaultBitrate = data.defaultBitrate;
               if (data.enableCache != null) this.enableCache = data.enableCache;
               if (data.maxCacheSizeMB != null) this.maxCacheSizeMB = data.maxCacheSizeMB;
               if (data.autoPlayOnStart != null) this.autoPlayOnStart = data.autoPlayOnStart;
               if (data.hudShowCover != null) this.hudShowCover = data.hudShowCover;
               if (data.hudShowSpectrum != null) this.hudShowSpectrum = data.hudShowSpectrum;
               if (data.hudShowProgress != null) this.hudShowProgress = data.hudShowProgress;
               if (data.hudHideWhenNotPlaying != null) this.hudHideWhenNotPlaying = data.hudHideWhenNotPlaying;
               if (data.hudScale != null) this.hudScale = data.hudScale;
               if (data.hudPosX != null) this.hudPosX = data.hudPosX;
               if (data.hudPosY != null) this.hudPosY = data.hudPosY;
               if (data.windowPosX != null) this.windowPosX = data.windowPosX;
               if (data.windowPosY != null) this.windowPosY = data.windowPosY;
               if (data.windowWidth != null) this.windowWidth = data.windowWidth;
               if (data.windowHeight != null) this.windowHeight = data.windowHeight;
               if (data.musicListSource != null) this.musicListSource = data.musicListSource;
            }
         } catch (Throwable e) {
            System.err.println("[MusicPlayer] Config load warning: " + e.getMessage());
         }
      }
   }

   private static class ConfigData {
      ApiProvider apiProvider;
      String customApiUrl;
      LoginMethod loginMethod;
      String phoneNumber;
      String password;
      Float defaultVolume;
      Integer defaultBitrate;
      Boolean enableCache;
      Integer maxCacheSizeMB;
      Boolean autoPlayOnStart;
      Boolean hudShowCover;
      Boolean hudShowSpectrum;
      Boolean hudShowProgress;
      Boolean hudHideWhenNotPlaying;
      Float hudScale;
      Float hudPosX;
      Float hudPosY;
      Float windowPosX;
      Float windowPosY;
      Integer windowWidth;
      Integer windowHeight;
      MusicListSource musicListSource;
   }

   // ==================== 对外静态访问方法 ====================
   public static String getApiUrl() {
      if (INSTANCE == null) {
         return "http://localhost:3000";
      } else {
         return INSTANCE.apiProvider == ApiProvider.CUSTOM && !INSTANCE.customApiUrl.isEmpty()
               ? INSTANCE.customApiUrl
               : INSTANCE.apiProvider.getDefaultUrl();
      }
   }

   public static float getDefaultVolume() {
      return INSTANCE != null ? INSTANCE.defaultVolume : 0.8F;
   }

   public static int getDefaultBitrate() {
      return INSTANCE != null ? INSTANCE.defaultBitrate : 320000;
   }

   public static boolean isCacheEnabled() {
      return INSTANCE != null && INSTANCE.enableCache;
   }

   public static long getMaxCacheSizeBytes() {
      return INSTANCE != null ? (long) INSTANCE.maxCacheSizeMB * 1024L * 1024L : 2147483648L;
   }

   public static boolean isAutoPlayEnabled() {
      return INSTANCE != null && INSTANCE.autoPlayOnStart;
   }

   public static MusicListSource getMusicListSource() {
      return INSTANCE != null ? INSTANCE.musicListSource : MusicListSource.HOT_SONGS;
   }

   public static void setMusicListSource(MusicListSource source) {
      if (INSTANCE != null && source != null) {
         INSTANCE.musicListSource = source;
         INSTANCE.save();
      }
   }

   public static boolean hudShowCover() {
      return INSTANCE != null && INSTANCE.hudShowCover;
   }

   public static boolean hudShowSpectrum() {
      return INSTANCE != null && INSTANCE.hudShowSpectrum;
   }

   public static boolean hudShowProgress() {
      return INSTANCE != null && INSTANCE.hudShowProgress;
   }

   public static boolean hudHideWhenNotPlaying() {
      return INSTANCE != null && INSTANCE.hudHideWhenNotPlaying;
   }

   public static float hudScale() {
      return INSTANCE != null ? INSTANCE.hudScale : 1.0F;
   }

   public static float hudPosX() {
      return INSTANCE != null ? INSTANCE.hudPosX : 0.0F;
   }

   public static float hudPosY() {
      return INSTANCE != null ? INSTANCE.hudPosY : 0.0F;
   }

   public static void saveHudSettings(
         boolean showCover, boolean showSpectrum, boolean showProgress, boolean hideWhenNotPlaying,
         float scale, float posX, float posY
   ) {
      if (INSTANCE != null) {
         INSTANCE.hudShowCover = showCover;
         INSTANCE.hudShowSpectrum = showSpectrum;
         INSTANCE.hudShowProgress = showProgress;
         INSTANCE.hudHideWhenNotPlaying = hideWhenNotPlaying;
         INSTANCE.hudScale = scale;
         INSTANCE.hudPosX = posX;
         INSTANCE.hudPosY = posY;
         INSTANCE.save();
      }
   }

   public static float windowPosX() {
      return INSTANCE != null ? INSTANCE.windowPosX : 0.0F;
   }

   public static float windowPosY() {
      return INSTANCE != null ? INSTANCE.windowPosY : 0.0F;
   }

   public static int windowWidth() {
      return INSTANCE != null ? INSTANCE.windowWidth : 960;
   }

   public static int windowHeight() {
      return INSTANCE != null ? INSTANCE.windowHeight : 768;
   }

   public static void saveWindowSettings(float posX, float posY, int width, int height) {
      if (INSTANCE != null) {
         INSTANCE.windowPosX = posX;
         INSTANCE.windowPosY = posY;
         INSTANCE.windowWidth = width;
         INSTANCE.windowHeight = height;
         INSTANCE.save();
      }
   }

   // ==================== 枚举 ====================
   public enum ApiProvider {
      TOOLKAL("Toolkal API", "https://api.toolkal.com"),
      NETSTART("NetStart API", "https://apis.netstart.cn/music"),
      MUSIC_163_CN("Music 163 API", "https://api.music.163.cool"),
      NCM_API("NCM API", "https://api.ncmapi.org"),
      LOCAL("Local Host", "http://localhost:3000"),
      CUSTOM("Custom", "");

      private final String displayName;
      private final String defaultUrl;

      ApiProvider(String displayName, String defaultUrl) {
         this.displayName = displayName;
         this.defaultUrl = defaultUrl;
      }

      public String getDefaultUrl() {
         return this.defaultUrl;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   public enum LoginMethod {
      QR_CODE("QR Code"),
      PHONE("Phone Number");

      private final String displayName;

      LoginMethod(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   public enum MusicListSource {
      HOT_SONGS("Hot Songs"),
      PERSONALIZED("Recommended");

      private final String displayName;

      MusicListSource(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }
}
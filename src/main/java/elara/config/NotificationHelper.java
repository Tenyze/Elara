package elara.config;

import cc.polyfrost.oneconfig.renderer.asset.Icon;
import cc.polyfrost.oneconfig.utils.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationHelper {
   private static final Logger logger = LoggerFactory.getLogger(NotificationHelper.class);

   private static final Icon ENABLED_ICON = safeLoadIcon("/assets/elara/icons/enabled.png");
   private static final Icon DISABLED_ICON = safeLoadIcon("/assets/elara/icons/disabled.png");
   private static final Icon SAVE_ICON = safeLoadIcon("/assets/elara/icons/save.png");
   private static final Icon LOAD_ICON = safeLoadIcon("/assets/elara/icons/load.png");
   private static final Icon MUSIC_PLAY_ICON = safeLoadIcon("/assets/elara/icons/Play.png");
   private static final Icon MUSIC_PAUSE_ICON = safeLoadIcon("/assets/elara/icons/Pause.png");
   private static final Icon ACCOUNT_ICON = safeLoadIcon("/assets/elara/icons/Account.png");
   private static final Icon ERROR_ICON = safeLoadIcon("/assets/oneconfig/old-icons/Error.svg");
   private static final Icon WARNING_ICON = safeLoadIcon("/assets/oneconfig/old-icons/Warning.svg");
   private static final Icon SUCCESS_ICON = safeLoadIcon("/assets/oneconfig/old-icons/CheckCircle.svg");
   private static final Icon INFO_ICON = safeLoadIcon("/assets/oneconfig/old-icons/InfoCircle.svg");
   private static final Icon DOWNLOAD_ICON = safeLoadIcon("/assets/oneconfig/old-icons/Download.svg");

   private static boolean oneConfigChecked = false;
   private static boolean oneConfigAvailable = false;

   private NotificationHelper() {}

   private static Icon safeLoadIcon(String path) {
      try {
         return new Icon(path);
      } catch (Throwable t) {
         return null;
      }
   }

   private static boolean isOneConfigAvailable() {
      if (!oneConfigChecked) {
         try {
            Class.forName("cc.polyfrost.oneconfig.utils.Notifications");
            oneConfigAvailable = true;
         } catch (Throwable t) {
            oneConfigAvailable = false;
         }
         oneConfigChecked = true;
      }
      return oneConfigAvailable;
   }

   private static boolean isNotificationEnabled() {
      try {
         return NotificationConfig.INSTANCE != null && NotificationConfig.INSTANCE.enabled;
      } catch (Throwable t) {
         return true;
      }
   }

   private static int getDefaultDuration() {
      try {
         return NotificationConfig.INSTANCE != null ? NotificationConfig.INSTANCE.defaultDuration : 3500;
      } catch (Throwable t) {
         return 3500;
      }
   }

   private static int getShortDuration() {
      try {
         return NotificationConfig.INSTANCE != null ? NotificationConfig.INSTANCE.shortDuration : 2500;
      } catch (Throwable t) {
         return 2500;
      }
   }

   private static boolean shouldShowIcons() {
      try {
         return NotificationConfig.INSTANCE == null || NotificationConfig.INSTANCE.showIcons;
      } catch (Throwable t) {
         return true;
      }
   }

   private static void sendWithDuration(String title, String message, Icon icon, int duration) {
      if (!isOneConfigAvailable()) return;
      if (!isNotificationEnabled()) return;
      Icon finalIcon = shouldShowIcons() ? icon : null;
      try {
         long start = System.currentTimeMillis();
         Notifications.INSTANCE.send(title, message, finalIcon, duration, () -> {
            long elapsed = System.currentTimeMillis() - start;
            float p = Math.min(1.0F, (float) elapsed / duration);
            return (float) (1.0 - Math.pow(1.0F - p, 2.0));
         });
      } catch (Throwable t) {
         logger.debug("Notification send failed: {}", t.getMessage());
      }
   }

   public static void send(String title, String message) {
      sendWithDuration(title, message, null, getDefaultDuration());
   }

   public static void send(String title, String message, Icon icon) {
      sendWithDuration(title, message, icon, getDefaultDuration());
   }

   public static void sendInfo(String title, String message) {
      sendWithDuration(title, message, INFO_ICON, getDefaultDuration());
   }

   public static void sendSuccess(String title, String message) {
      sendWithDuration(title, message, SUCCESS_ICON, getDefaultDuration());
   }

   public static void sendError(String title, String message) {
      sendWithDuration(title, message, ERROR_ICON, getDefaultDuration());
   }

   public static void sendWarning(String title, String message) {
      sendWithDuration(title, message, WARNING_ICON, getDefaultDuration());
   }

   public static void sendModuleToggle(String moduleName, boolean enabled) {
      Icon icon = enabled ? ENABLED_ICON : DISABLED_ICON;
      String status = enabled ? "on" : "off";
      sendWithDuration("Module", " \"" + moduleName + "\" " + status, icon, getDefaultDuration());
   }

   public static void sendEnabledWithProgress(String moduleName) {
      sendWithDuration(moduleName, "Has Been Enabled Now", ENABLED_ICON, getDefaultDuration());
   }

   public static void sendDisabledWithProgress(String moduleName) {
      sendWithDuration(moduleName, "Has Been Disabled Now", DISABLED_ICON, getDefaultDuration());
   }

   public static void sendConfigSaved() {
      sendWithDuration("Config", "Config has been saved.", SAVE_ICON, getShortDuration());
   }

   public static void sendConfigSaved(String configName) {
      sendWithDuration("Config", "Saved: " + configName, SAVE_ICON, getShortDuration());
   }

   public static void sendConfigLoaded() {
      sendWithDuration("Config", "Config has been loaded.", LOAD_ICON, getShortDuration());
   }

   public static void sendConfigLoaded(String configName) {
      sendWithDuration("Config", "Loaded: " + configName, LOAD_ICON, getShortDuration());
   }

   public static void sendProfileSaved(String profileName) {
      sendWithDuration("Profiles", "Saved: " + profileName, SAVE_ICON, getShortDuration());
   }

   public static void sendProfileLoaded(String profileName) {
      sendWithDuration("Profiles", "Loaded: " + profileName, LOAD_ICON, getShortDuration());
   }

   public static void sendProfileDeleted(String profileName) {
      sendWithDuration("Profiles", "Deleted: " + profileName, WARNING_ICON, getShortDuration());
   }

   public static void sendProfileCreated(String profileName) {
      sendWithDuration("Profiles", "Created: " + profileName, SUCCESS_ICON, getShortDuration());
   }

   public static void sendProfileExists(String profileName) {
      sendWithDuration("Profiles", "Profile already exists: " + profileName, WARNING_ICON, getShortDuration());
   }

   public static void sendMusicPlay(String title, String artist) {
      String msg = formatSongMessage(title, artist);
      sendWithDuration("Now Playing", msg, MUSIC_PLAY_ICON, getDefaultDuration());
   }

   public static void sendMusicPause(String title, String artist) {
      String msg = formatSongMessage(title, artist);
      sendWithDuration("Paused", msg, MUSIC_PAUSE_ICON, getShortDuration());
   }

   public static void sendMusicNext(String title, String artist) {
      String msg = formatSongMessage(title, artist);
      sendWithDuration("Up Next", msg, MUSIC_PLAY_ICON, getDefaultDuration());
   }

   public static void sendMusicStop() {
      sendWithDuration("Music", "Playback stopped", MUSIC_PAUSE_ICON, getShortDuration());
   }

   public static void sendMusicError(String error) {
      sendWithDuration("Music Error", error, ERROR_ICON, getDefaultDuration());
   }

   public static void sendDownloadComplete(String songName) {
      sendWithDuration("Download", "Downloaded: " + songName, DOWNLOAD_ICON, getShortDuration());
   }

   public static void sendDownloadError(String error) {
      sendWithDuration("Download", "Failed: " + error, ERROR_ICON, getDefaultDuration());
   }

   public static void sendDownloadCancelled() {
      sendWithDuration("Download", "Download cancelled", WARNING_ICON, getShortDuration());
   }

   public static void sendLoginSuccess(String nickname) {
      String msg = (nickname != null && !nickname.isEmpty()) ? "Welcome, " + nickname + "!" : "Login successful!";
      sendWithDuration("Login", msg, ACCOUNT_ICON, getDefaultDuration());
   }

   public static void sendLoginFailed(String reason) {
      sendWithDuration("Login", "Login failed: " + reason, ERROR_ICON, getDefaultDuration());
   }

   public static void sendLogout() {
      sendWithDuration("Login", "Logged out successfully", ACCOUNT_ICON, getShortDuration());
   }

   public static void sendApiConnected(String platformName) {
      sendWithDuration("API", "Connected to " + platformName, SUCCESS_ICON, getShortDuration());
   }

   public static void sendApiDisconnected(String reason) {
      sendWithDuration("API", "Connection failed: " + reason, ERROR_ICON, getDefaultDuration());
   }

   public static void sendPlatformSwitched(String platformName) {
      sendWithDuration("Music API", "Switched to " + platformName, INFO_ICON, getShortDuration());
   }

   public static void sendCacheCleared() {
      sendWithDuration("Cache", "Music cache cleared", SUCCESS_ICON, getShortDuration());
   }

   public static void sendCacheCleaned(long freedBytes, String formattedSize) {
      sendWithDuration("Cache", "Cleaned " + formattedSize + " of cache", SUCCESS_ICON, getShortDuration());
   }

   private static String formatSongMessage(String title, String artist) {
      if (title == null || title.isEmpty()) return "Unknown track";
      return (artist != null && !artist.isEmpty()) ? title + " — " + artist : title;
   }
}
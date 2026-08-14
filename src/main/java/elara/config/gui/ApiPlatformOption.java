package elara.config.gui;

import cc.polyfrost.oneconfig.gui.elements.config.ConfigDropdown;
import elara.config.music.MusicApiManager;

public class ApiPlatformOption extends ConfigDropdown {
   private final MusicApiManager apiManager;

   public ApiPlatformOption(MusicApiManager apiManager) {
      super(null, null, "Music Platform", "Music streaming service", "Music", "API Settings", 1, getPlatformNames());
      this.apiManager = apiManager;
   }

   private static String[] getPlatformNames() {
      return new String[]{MusicApiManager.Platform.NETEASE_CLOUD.getIcon() + " " + MusicApiManager.Platform.NETEASE_CLOUD.getDisplayName()};
   }

   public Object get() {
      return 0.0F;
   }

   protected void set(Object value) {
      this.triggerListeners();
   }
}

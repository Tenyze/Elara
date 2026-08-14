package elara.config.music;

import cc.polyfrost.oneconfig.gui.OneConfigGui;
import cc.polyfrost.oneconfig.gui.pages.Page;
import elara.config.gui.MusicPlayerPage;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

public class MusicKeyBinding {
   public static KeyBinding openMusicListKey;

   public static void init() {
      openMusicListKey = new KeyBinding("key.musicplayer.open_list", 0, "key.categories.musicplayer");
   }

   @SubscribeEvent
   public void onKeyInput(InputEvent.KeyInputEvent event) {
      if (openMusicListKey != null && openMusicListKey.isPressed()) {
         OneConfigGui.INSTANCE.openPage((Page) new MusicPlayerPage());
      }
   }
}
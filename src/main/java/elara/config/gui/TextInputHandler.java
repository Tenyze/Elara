package elara.config.gui;

import net.minecraftforge.common.MinecraftForge;

public class TextInputHandler {
   private static TextInputHandler instance;

   private TextInputHandler() {
      MinecraftForge.EVENT_BUS.register(this);
   }

   public static void init() {
      if (instance == null) {
         instance = new TextInputHandler();
      }
   }
}

package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.IFocusable;
import cc.polyfrost.oneconfig.libs.universal.UKeyboard;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.module.Module;

public class ModuleOptions {
   public static class HideOption extends BasicOption {
      private final Module module;
      private final BasicButton button;

      public HideOption(Module module) {
         super(null, null, "Hide", "Hide this module", "General", "", 1);
         this.module = module;
         this.button = new BasicButton(64, 32, "", 2, ColorPalette.SECONDARY);
         this.button.setToggleable(true);
         this.button.setToggled(module.isHidden());
         this.button.setClickAction(() -> {
            module.setHidden(!module.isHidden());
            this.button.setToggled(module.isHidden());
         });
      }

      public void draw(long vg, int x, int y, InputHandler inputHandler) {
         NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
         this.button.setToggled(this.module.isHidden());
         nanoVGHelper.drawText(vg, this.name, x, y + 17, this.nameColor, 14.0F, Fonts.MEDIUM);
         this.button.setText(this.module.isHidden() ? "ON" : "OFF");
         this.button.draw(vg, x + 224, y, inputHandler);
      }

      public int getHeight() {
         return 32;
      }
   }

   public static class KeyBindOption extends BasicOption implements IFocusable {
      private final Module module;
      private final BasicButton button;
      private boolean recording = false;

      public KeyBindOption(Module module) {
         super(null, null, "Keybind", "", "General", "", 1);
         this.module = module;
         this.button = new BasicButton(256, 32, "", 2, ColorPalette.SECONDARY);
         this.button.setToggleable(true);
         this.button.setClickAction(() -> {
            if (!this.recording) {
               this.recording = true;
               this.button.setToggled(true);
               module.setKey(0);
            }
         });
      }

      private String getDisplay() {
         int key = this.module.getKey();
         if (key == 0) {
            return "NONE";
         }

         try {
            return Platform.getI18nPlatform().getKeyName(Platform.getInstance().getMinecraftVersion() >= 11300 ? key + 100 : key, -1);
         } catch (Throwable e) {
            return "Key " + key;
         }
      }

      public void draw(long vg, int x, int y, InputHandler inputHandler) {
         NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
         nanoVGHelper.drawText(vg, this.name, x, y + 17, this.nameColor, 14.0F, Fonts.MEDIUM);
         String text;
         if (this.recording) {
            text = "Recording... (ESC to clear)";
         } else {
            text = this.getDisplay();
            if (text.equals("")) {
               text = "NONE";
            }
         }

         this.button.setText(text);
         this.button.draw(vg, x + 224, y, inputHandler);
         nanoVGHelper.setAlpha(vg, 1.0F);
      }

      public void keyTyped(char key, int keyCode) {
         if (this.recording) {
            this.recording = false;
            this.button.setToggled(false);
            if (keyCode == UKeyboard.KEY_ESCAPE) {
               this.module.setKey(0);
            } else {
               this.module.setKey(keyCode);
            }
         }
      }

      public void finishUpAndClose() {
         if (this.recording) {
            this.recording = false;
            this.button.setToggled(false);
         }
      }

      public boolean hasFocus() {
         return this.recording;
      }

      public int getHeight() {
         return 32;
      }
   }

   public static class ToggleOption extends BasicOption {
      private final Module module;
      private final BasicButton button;

      public ToggleOption(Module module) {
         super(null, null, "Enabled", "", "General", "", 1);
         this.module = module;
         this.button = new BasicButton(64, 32, "", 2, ColorPalette.SECONDARY);
         this.button.setToggleable(true);
         this.button.setToggled(module.isEnabled());
         this.button.setClickAction(() -> {
            module.setEnabled(!module.isEnabled());
            this.button.setToggled(module.isEnabled());
         });
      }

      public void draw(long vg, int x, int y, InputHandler inputHandler) {
         NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
         this.button.setToggled(this.module.isEnabled());
         nanoVGHelper.drawText(vg, this.name, x, y + 17, this.nameColor, 14.0F, Fonts.MEDIUM);
         this.button.setText(this.module.isEnabled() ? "ON" : "OFF");
         this.button.draw(vg, x + 224, y, inputHandler);
      }

      public int getHeight() {
         return 32;
      }
   }
}

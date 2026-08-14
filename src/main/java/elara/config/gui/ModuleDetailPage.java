package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionSubcategory;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.scissor.ScissorHelper;
import cc.polyfrost.oneconfig.utils.InputHandler;
import elara.Elara;
import elara.config.ElaraOptions;
import elara.module.Module;
import elara.property.Property;
import java.util.ArrayList;

public class ModuleDetailPage extends Page {
   private final Module module;
   private OptionSubcategory subcategory;
   private int totalSize = 728;
   private static final int TITLE_BAR_H = 48;
   private static final int CONTENT_PAD_BOTTOM = 16;

   public ModuleDetailPage(Module module) {
      super(module.getName());
      this.module = module;
      this.buildSubcategory();
   }

   private void buildSubcategory() {
      this.subcategory = new OptionSubcategory("", "Modules");
      this.subcategory.options.add(new ModuleOptions.ToggleOption(this.module));
      this.subcategory.options.add(new ModuleOptions.KeyBindOption(this.module));
      this.subcategory.options.add(new ModuleOptions.HideOption(this.module));
      ArrayList<Property<?>> properties = Elara.propertyManager.properties.get(this.module.getClass());
      if (properties != null) {
         for (Property<?> property : properties) {
            try {
               BasicOption option = ElaraOptions.create(property);
               if (option != null) {
                  this.subcategory.options.add(option);
               }
            } catch (Throwable var5) {
            }
         }
      }
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      ScissorHelper scissorHelper = ScissorHelper.INSTANCE;
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      int bx = x + 32;
      int by = y + 16;
      int headerX = bx + 16;
      int optionStartX = x + 32;
      int optionStartY = by + 16;
      int contentH = 0;
      if (this.subcategory != null) {
         contentH = this.subcategory.draw(vg, optionStartX, optionStartY, inputHandler);
      }

      scissorHelper.save();
      scissorHelper.clearScissors(vg);
      if (this.subcategory != null) {
         this.subcategory.drawLast(vg, optionStartX, inputHandler);
      }

      scissorHelper.restore(vg);
      this.totalSize = by + 16 + contentH + 16 - y + 16;
   }

   public void finishUpAndClose() {
      if (this.subcategory != null) {
         for (BasicOption option : this.subcategory.options) {
            option.finishUpAndClose();
         }
      }
   }

   public void keyTyped(char key, int keyCode) {
      if (this.subcategory != null) {
         for (BasicOption option : this.subcategory.options) {
            option.keyTyped(key, keyCode);
         }
      }
   }

   public int getMaxScrollHeight() {
      return this.totalSize;
   }

   public Module getModule() {
      return this.module;
   }
}

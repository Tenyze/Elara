package elara.config.gui;

import cc.polyfrost.oneconfig.gui.OneConfigGui;
import cc.polyfrost.oneconfig.gui.animations.ColorAnimation;
import cc.polyfrost.oneconfig.gui.elements.BasicElement;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.renderer.scissor.Scissor;
import cc.polyfrost.oneconfig.renderer.scissor.ScissorHelper;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import cc.polyfrost.oneconfig.utils.color.ColorUtils;
import elara.module.Module;

public class ModuleCard extends BasicElement {
   private final Module module;
   private final ColorAnimation colorFrame = new ColorAnimation(ColorPalette.SECONDARY);
   private final ColorAnimation colorToggle;
   private boolean lastSyncedEnabled;

   public ModuleCard(Module module) {
      super(244, 119, false);
      this.module = module;
      this.colorToggle = new ColorAnimation(module.isEnabled() ? ColorPalette.PRIMARY : ColorPalette.SECONDARY);
      this.toggled = module.isEnabled();
      this.lastSyncedEnabled = module.isEnabled();
   }

   public void draw(long vg, float x, float y, InputHandler inputHandler) {
      super.update(x, y, inputHandler);
      boolean liveEnabled = this.module.isEnabled();
      if (liveEnabled != this.lastSyncedEnabled) {
         this.lastSyncedEnabled = liveEnabled;
         this.toggled = liveEnabled;
         this.colorToggle.setPalette(liveEnabled ? ColorPalette.PRIMARY : ColorPalette.SECONDARY);
      }

      ScissorHelper scissorHelper = ScissorHelper.INSTANCE;
      NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
      String cleanName = this.module.getName();
      Scissor scissor = scissorHelper.scissor(vg, x, y, this.width, this.height);
      boolean isHoveredMain = inputHandler.isAreaHovered(x, y, this.width, 87.0F);
      boolean isHoveredSecondary = inputHandler.isAreaHovered(x, y + 87.0F, this.width, 32.0F);
      nanoVGHelper.drawRoundedRectVaried(
         vg,
         x,
         y,
         this.width,
         87.0F,
         this.colorFrame.getColor(isHoveredMain, isHoveredMain && Platform.getMousePlatform().isButtonDown(0)),
         12.0F,
         12.0F,
         0.0F,
         0.0F
      );
      nanoVGHelper.drawRoundedRectVaried(
         vg,
         x,
         y + 87.0F,
         this.width,
         32.0F,
         this.colorToggle.getColor(isHoveredSecondary, isHoveredSecondary && Platform.getMousePlatform().isButtonDown(0)),
         0.0F,
         0.0F,
         12.0F,
         12.0F
      );
      nanoVGHelper.drawLine(vg, x, y + 86.0F, x + this.width, y + 86.0F, 2.0F, ElaraColors.GRAY_600);
      float textSize = 16.0F;
      float textWidth = nanoVGHelper.getTextWidth(vg, cleanName, textSize, Fonts.MEDIUM);
      if (textWidth > 220.0F) {
         textSize = 14.0F;
         textWidth = nanoVGHelper.getTextWidth(vg, cleanName, textSize, Fonts.MEDIUM);
      }

      if (textWidth > 220.0F) {
         textSize = 12.0F;
      }

      float textX = x + Math.max(0.0F, (244.0F - nanoVGHelper.getTextWidth(vg, cleanName, textSize, Fonts.MEDIUM)) / 2.0F);
      float textY = y + 43.0F + textSize / 2.0F;
      nanoVGHelper.drawText(
         vg, cleanName, textX, textY, ColorUtils.setAlpha(ElaraColors.WHITE, (int)(this.colorFrame.getAlpha() * 255.0F)), textSize, Fonts.MEDIUM
      );
      Scissor scissor2 = scissorHelper.scissor(vg, x, y + 87.0F, this.width, 32.0F);
      nanoVGHelper.drawText(
         vg, cleanName, x + 12.0F, y + 103.0F, ColorUtils.setAlpha(ElaraColors.WHITE, (int)(this.colorToggle.getAlpha() * 255.0F)), 14.0F, Fonts.MEDIUM
      );
      String stateText = this.module.isEnabled() ? "ON" : "OFF";
      float stateWidth = nanoVGHelper.getTextWidth(vg, stateText, 12.0F, Fonts.MEDIUM);
      nanoVGHelper.drawText(
         vg,
         stateText,
         x + this.width - stateWidth - 12.0F,
         y + 103.0F,
         ColorUtils.setAlpha(ElaraColors.WHITE, (int)(this.colorToggle.getAlpha() * 255.0F)),
         12.0F,
         Fonts.MEDIUM
      );
      scissorHelper.resetScissor(vg, scissor2);
      if (this.clicked && isHoveredMain) {
         this.clicked = false;
         ModuleDetailPage detailPage = new ModuleDetailPage(this.module);
         OneConfigGui.INSTANCE.openPage(detailPage);
      }

      if (this.clicked && !isHoveredMain && isHoveredSecondary) {
         this.clicked = false;
         this.module.setEnabled(!this.module.isEnabled());
         this.colorToggle.setPalette(this.module.isEnabled() ? ColorPalette.PRIMARY : ColorPalette.SECONDARY);
      }

      scissorHelper.resetScissor(vg, scissor);
   }

   public Module getModule() {
      return this.module;
   }
}

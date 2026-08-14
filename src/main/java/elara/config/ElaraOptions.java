package elara.config;

import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigColorElement;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigDropdown;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSlider;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSwitch;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigTextBox;
import elara.property.Property;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ColorProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import elara.property.properties.TextProperty;
import java.lang.reflect.Field;

public final class ElaraOptions {
   private ElaraOptions() {
   }

   private static void saveConfig() {
      try {
         ElaraConfig.INSTANCE.save();
      } catch (Exception var1) {
      }
   }

   public static BasicOption create(Property<?> property) {
      if (property instanceof BooleanProperty) {
         return new ElaraOptions.BooleanOption((BooleanProperty)property);
      } else if (property instanceof ModeProperty) {
         return new ElaraOptions.ModeOption((ModeProperty)property);
      } else if (property instanceof FloatProperty) {
         return new ElaraOptions.FloatOption((FloatProperty)property);
      } else if (property instanceof IntProperty) {
         return new ElaraOptions.IntOption((IntProperty)property);
      } else if (property instanceof PercentProperty) {
         return new ElaraOptions.PercentOption((PercentProperty)property);
      } else if (property instanceof ColorProperty) {
         return new ElaraOptions.ColorOption((ColorProperty)property);
      } else {
         return property instanceof TextProperty ? new ElaraOptions.TextOption((TextProperty)property) : null;
      }
   }

   public static class BooleanOption extends ConfigSwitch {
      private final BooleanProperty property;

      public BooleanOption(BooleanProperty property) {
         super(null, null, property.getName(), "", property.getCategory() != null ? property.getCategory() : "General", "", 1);
         this.property = property;
      }

      public Object get() {
         Boolean v = this.property.getValue();
         return v != null ? v : Boolean.FALSE;
      }

      protected void set(Object value) {
         if (value instanceof Boolean) {
            this.property.setValue(value);
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class ColorHolder {
      public OneColor value;

      public ColorHolder(ColorProperty property) {
         int argb = property.getValue();
         int a = argb >> 24 & 0xFF;
         int r = argb >> 16 & 0xFF;
         int g = argb >> 8 & 0xFF;
         int b = argb & 0xFF;
         this.value = new OneColor(r, g, b, a);
      }
   }

   public static class ColorOption extends ConfigColorElement {
      private final ColorProperty property;
      private final ElaraOptions.ColorHolder holder;

      public ColorOption(ColorProperty property) {
         super(
            getValueFieldSafe(),
            new ElaraOptions.ColorHolder(property),
            property.getName(),
            "",
            property.getCategory() != null ? property.getCategory() : "General",
            "",
            1,
            true
         );
         this.property = property;
         this.holder = (ElaraOptions.ColorHolder)this.parent;
      }

      private static Field getValueFieldSafe() {
         try {
            return ElaraOptions.ColorHolder.class.getDeclaredField("value");
         } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
         }
      }

      public Object get() {
         return this.holder.value;
      }

      protected void setColor(OneColor color) {
         super.setColor(color);
         this.syncToProperty();
      }

      protected void set(Object value) {
         if (value instanceof OneColor) {
            this.holder.value = (OneColor)value;
            this.syncToProperty();
         } else if (value instanceof Number) {
            int argb = ((Number)value).intValue();
            int a = argb >> 24 & 0xFF;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            this.holder.value = new OneColor(r, g, b, a);
            this.syncToProperty();
         }

         this.triggerListeners();
      }

      private void syncToProperty() {
         OneColor color = this.holder.value;
         int argb = (color.getAlpha() & 0xFF) << 24 | (color.getRed() & 0xFF) << 16 | (color.getGreen() & 0xFF) << 8 | color.getBlue() & 0xFF;
         this.property.setValue(argb);
         ElaraOptions.saveConfig();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class FloatOption extends ConfigSlider {
      private final FloatProperty property;

      public FloatOption(FloatProperty property) {
         super(
            null,
            null,
            property.getName(),
            "",
            property.getCategory() != null ? property.getCategory() : "General",
            "",
            property.getMinimum(),
            property.getMaximum(),
            0
         );
         this.property = property;
      }

      public Object get() {
         Float v = this.property.getValue();
         return v != null ? v : 0.0F;
      }

      protected void set(Object value) {
         if (value instanceof Number) {
            this.property.setValue(((Number)value).floatValue());
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class IntOption extends ConfigSlider {
      private final IntProperty property;

      public IntOption(IntProperty property) {
         super(
            null,
            null,
            property.getName(),
            "",
            property.getCategory() != null ? property.getCategory() : "General",
            "",
            property.getMinimum().intValue(),
            property.getMaximum().intValue(),
            1
         );
         this.property = property;
      }

      public Object get() {
         Integer v = this.property.getValue();
         return v != null ? v.intValue() : 0.0F;
      }

      protected void set(Object value) {
         if (value instanceof Number) {
            this.property.setValue(((Number)value).intValue());
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class ModeOption extends ConfigDropdown {
      private final ModeProperty property;
      private final String[] modes;

      public ModeOption(ModeProperty property) {
         super(null, null, property.getName(), "", property.getCategory() != null ? property.getCategory() : "General", "", 1, property.getModes());
         this.property = property;
         this.modes = property.getModes();
         int current = property.getValue();
         if (current < 0 || current >= this.modes.length) {
            property.setValue(0);
         }
      }

      public Object get() {
         int val = this.property.getValue();
         if (val < 0 || val >= this.modes.length) {
            val = 0;
            this.property.setValue(val);
         }

         return val;
      }

      protected void set(Object value) {
         if (value instanceof Number) {
            int intVal = ((Number)value).intValue();
            if (intVal >= 0 && intVal < this.modes.length) {
               this.property.setValue(intVal);
            } else {
               this.property.setValue(0);
            }
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class PercentOption extends ConfigSlider {
      private final PercentProperty property;

      public PercentOption(PercentProperty property) {
         super(
            null,
            null,
            property.getName(),
            "",
            property.getCategory() != null ? property.getCategory() : "General",
            "",
            property.getMinimum().intValue(),
            property.getMaximum().intValue(),
            1
         );
         this.property = property;
      }

      public Object get() {
         Integer v = this.property.getValue();
         return v != null ? v.intValue() : 0.0F;
      }

      protected void set(Object value) {
         if (value instanceof Number) {
            this.property.setValue(((Number)value).intValue());
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }

   public static class TextOption extends ConfigTextBox {
      private final TextProperty property;

      public TextOption(TextProperty property) {
         super(null, null, property.getName(), "", property.getCategory() != null ? property.getCategory() : "General", "", 1, "", false, false);
         this.property = property;
      }

      public Object get() {
         String v = this.property.getValue();
         return v != null ? v : "";
      }

      protected void set(Object value) {
         if (value instanceof String) {
            this.property.setValue(value);
         }

         this.triggerListeners();
      }

      public boolean isHidden() {
         return !this.property.isVisible();
      }
   }
}

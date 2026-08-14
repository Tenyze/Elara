package elara.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import elara.Elara;
import elara.mixin.IAccessorMinecraft;
import elara.module.Module;
import elara.property.Property;
import elara.util.ChatUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;

public class Config {
   public static Minecraft mc = Minecraft.getMinecraft();
   public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
   public String name;
   public File file;
   public static String lastConfig;

   public Config(String name, boolean newConfig) {
      this.name = name;
      lastConfig = name;
      if (name.equals("!") || name.equals("default")) {
         this.name = "default";
      }

      this.file = new File("./config/Elara/", String.format("%s.json", this.name));

      try {
         this.file.getParentFile().mkdirs();
         if (newConfig) {
            ((IAccessorMinecraft)mc).getLogger().info(String.format("Created: %s", this.file.getName()));
         }
      } catch (Exception e) {
         ((IAccessorMinecraft)mc).getLogger().error(e.getMessage());
      }
   }

   public void load() {
      try {
         if (!this.file.exists()) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", Elara.clientName, this.file.getName()));
            this.save();
            return;
         }

         JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(this.file)));
         if (parsed == null || !parsed.isJsonObject()) {
            ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", Elara.clientName, this.file.getName()));
            return;
         }

         JsonObject jsonObject = parsed.getAsJsonObject();

         for (Module module : Elara.moduleManager.modules.values()) {
            JsonElement moduleObj = jsonObject.get(module.getName());
            if (moduleObj != null && moduleObj.isJsonObject()) {
               JsonObject object = moduleObj.getAsJsonObject();
               ArrayList<Property<?>> list = Elara.propertyManager.properties.get(module.getClass());
               if (list != null) {
                  for (Property<?> property : list) {
                     if (object.has(property.getName())) {
                        try {
                           property.read(object);
                        } catch (Exception e) {
                           ((IAccessorMinecraft)mc)
                              .getLogger()
                              .warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                        }
                     }
                  }
               }

               if (object.has("toggled")) {
                  JsonElement toggled = object.get("toggled");
                  if (toggled != null && toggled.isJsonPrimitive()) {
                     module.setEnabled(toggled.getAsBoolean());
                  }
               }

               if (object.has("key")) {
                  JsonElement key = object.get("key");
                  if (key != null && key.isJsonPrimitive()) {
                     module.setKey(key.getAsInt());
                  }
               }

               if (object.has("hidden")) {
                  JsonElement hidden = object.get("hidden");
                  if (hidden != null && hidden.isJsonPrimitive()) {
                     module.setHidden(hidden.getAsBoolean());
                  }
               }
            }
         }

         ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", Elara.clientName, this.file.getName()));
      } catch (FileNotFoundException e) {
         ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", Elara.clientName, this.file.getName()));
      } catch (JsonSyntaxException e) {
         ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", Elara.clientName, this.file.getName()));
         ((IAccessorMinecraft)mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
      } catch (Exception e) {
         ((IAccessorMinecraft)mc).getLogger().error("Error loading config: " + e.getMessage());
         ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Elara.clientName, this.file.getName()));
      }
   }

   public void save() {
      try {
         if (!this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         JsonObject object = new JsonObject();

         for (Module module : Elara.moduleManager.modules.values()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("toggled", module.isEnabled());
            moduleObject.addProperty("key", module.getKey());
            moduleObject.addProperty("hidden", module.isHidden());
            ArrayList<Property<?>> list = Elara.propertyManager.properties.get(module.getClass());
            if (list != null) {
               for (Property<?> property : list) {
                  try {
                     property.write(moduleObject);
                  } catch (Exception e) {
                     ((IAccessorMinecraft)mc).getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                  }
               }
            }

            object.add(module.getName(), moduleObject);
         }

         PrintWriter printWriter = new PrintWriter(new FileWriter(this.file));
         printWriter.println(gson.toJson(object));
         printWriter.close();
         ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", Elara.clientName, this.file.getName()));
      } catch (IOException e) {
         ((IAccessorMinecraft)mc).getLogger().error("Error saving config: " + e.getMessage());
         ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Elara.clientName, this.file.getName()));
      }
   }

   /** 重置所有模块和属性为默认值，若deleteFile=true同时删除当前配置文件 */
   public static void resetAll(boolean deleteFile) {
      if (deleteFile) {
         try {
            File f = new File("./config/Elara/default.json");
            if (f.exists()) f.delete();
         } catch (Throwable ignored) {}
      }
      for (Module module : Elara.moduleManager.modules.values()) {
         try {
            module.resetDefaults();
         } catch (Throwable t) {
            ((IAccessorMinecraft)mc).getLogger().error("Error resetting module " + module.getName() + ": " + t.getMessage());
         }
      }
      try {
         if (ElaraConfig.INSTANCE != null) {
            ElaraConfig.INSTANCE.save();
         }
      } catch (Throwable ignored) {}
      lastConfig = "default";
      ChatUtil.sendFormatted(String.format("%sConfig has been reset to defaults&r", Elara.clientName));
   }
}

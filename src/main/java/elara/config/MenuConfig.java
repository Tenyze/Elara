package elara.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public final class MenuConfig {
   private static final File FILE = new File("./config/elara/menu.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static boolean loaded;
   private static int backgroundIndex = 2;

   private MenuConfig() {
   }

   public static synchronized void load() {
      if (!loaded) {
         loaded = true;
         backgroundIndex = 2;
         if (!FILE.exists()) {
            save();
         } else {
            try {
               BufferedReader reader = new BufferedReader(new FileReader(FILE));

               try {
                  JsonElement parsed = new JsonParser().parse(reader);
                  if (parsed != null && parsed.isJsonObject()) {
                     JsonObject object = parsed.getAsJsonObject();
                     if (object.has("backgroundIndex")) {
                        backgroundIndex = clamp(object.get("backgroundIndex").getAsInt());
                     }
                  }
               } catch (Throwable var4) {
                  try {
                     reader.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }

                  throw var4;
               }

               reader.close();
            } catch (Exception ignored) {
               backgroundIndex = 2;
            }
         }
      }
   }

   public static synchronized int getBackgroundIndex() {
      load();
      return clamp(backgroundIndex);
   }

   public static synchronized void setBackgroundIndex(int index) {
      load();
      backgroundIndex = clamp(index);
   }

   public static synchronized void save() {
      try {
         File parent = FILE.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         JsonObject object = new JsonObject();
         object.addProperty("backgroundIndex", clamp(backgroundIndex));
         PrintWriter writer = new PrintWriter(new FileWriter(FILE));

         try {
            writer.println(GSON.toJson(object));
         } catch (Throwable var6) {
            try {
               writer.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         writer.close();
      } catch (IOException var7) {
      }
   }

   private static int clamp(int index) {
      return Math.max(0, Math.min(5, index));
   }
}

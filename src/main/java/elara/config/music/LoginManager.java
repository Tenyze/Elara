package elara.config.music;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class LoginManager {
   private static final File CONFIG_FILE = new File("./config/Elara/music/login.json");
   private final Map<String, String> cookies = new HashMap<>();
   private boolean loggedIn = false;
   private String userId = "";
   private String nickname = "";
   private String avatarUrl = "";

   public LoginManager() {
      this.loadConfig();
   }

   private void loadConfig() {
      if (CONFIG_FILE.exists()) {
         try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8));

            try {
               StringBuilder sb = new StringBuilder();

               String line;
               while ((line = reader.readLine()) != null) {
                  sb.append(line);
               }

               String json = sb.toString();
               if (!json.isEmpty()) {
                  if (json.contains("\"loggedIn\":true")) {
                     this.loggedIn = true;
                  }

                  this.userId = this.extractValue(json, "userId");
                  this.nickname = this.extractValue(json, "nickname");
                  this.avatarUrl = this.extractValue(json, "avatarUrl");
                  int cookiesStart = json.indexOf("\"cookies\":{");
                  int cookiesEnd;
                  if (cookiesStart != -1 && (cookiesEnd = json.indexOf("}", cookiesStart + 11)) != -1) {
                     String cookiesStr = json.substring(cookiesStart + 11, cookiesEnd);

                     for (String pair : cookiesStr.split(",")) {
                        int colon = pair.indexOf(":");
                        if (colon != -1) {
                           String key = pair.substring(0, colon).trim().replace("\"", "");
                           String value = pair.substring(colon + 1).trim().replace("\"", "");
                           this.cookies.put(key, value);
                        }
                     }
                  }
               }
            } catch (Throwable var17) {
               try {
                  reader.close();
               } catch (Throwable var16) {
                  var17.addSuppressed(var16);
               }

               throw var17;
            }

            reader.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   private void saveConfig() {
      try {
         File parent = CONFIG_FILE.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         StringBuilder sb = new StringBuilder();
         sb.append("{");
         sb.append("\"loggedIn\":").append(this.loggedIn).append(",");
         sb.append("\"userId\":\"").append(this.escapeJson(this.userId)).append("\",");
         sb.append("\"nickname\":\"").append(this.escapeJson(this.nickname)).append("\",");
         sb.append("\"avatarUrl\":\"").append(this.escapeJson(this.avatarUrl)).append("\",");
         sb.append("\"cookies\":{");
         boolean first = true;

         for (Entry<String, String> entry : this.cookies.entrySet()) {
            if (!first) {
               sb.append(",");
            }

            sb.append("\"").append(this.escapeJson(entry.getKey())).append("\":");
            sb.append("\"").append(this.escapeJson(entry.getValue())).append("\"");
            first = false;
         }

         sb.append("}");
         sb.append("}");
         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8));

         try {
            writer.write(sb.toString());
         } catch (Throwable var8) {
            try {
               writer.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         writer.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private String extractValue(String json, String key) {
      String search = "\"" + key + "\":\"";
      int start = json.indexOf(search);
      if (start == -1) {
         return "";
      }

      int var6;
      int end = json.indexOf("\"", var6 = start + search.length());
      return end == -1 ? "" : json.substring(var6, end).replace("\\\\", "\\");
   }

   private String escapeJson(String str) {
      return str == null ? "" : str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
   }

   public void updateCookie(String setCookie) {
      if (setCookie != null && !setCookie.isEmpty()) {
         for (String part : setCookie.split(";")) {
            String var10;
            int eq = (var10 = part.trim()).indexOf("=");
            if (eq != -1) {
               String key = var10.substring(0, eq).trim();
               String value = var10.substring(eq + 1).trim();
               if (!key.toLowerCase().contains("httponly")
                  && !key.toLowerCase().contains("secure")
                  && !key.toLowerCase().contains("domain")
                  && !key.toLowerCase().contains("path")) {
                  this.cookies.put(key, value);
               }
            }
         }

         this.saveConfig();
      }
   }

   public String getCookie() {
      if (this.cookies.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder();
      boolean first = true;

      for (Entry<String, String> entry : this.cookies.entrySet()) {
         if (!first) {
            sb.append("; ");
         }

         sb.append(entry.getKey()).append("=").append(entry.getValue());
         first = false;
      }

      return sb.toString();
   }

   public void clearCookie() {
      this.cookies.clear();
      this.saveConfig();
   }

   public void setLoggedIn(boolean loggedIn) {
      this.loggedIn = loggedIn;
      if (!loggedIn) {
         this.userId = "";
         this.nickname = "";
         this.avatarUrl = "";
         this.cookies.clear();
      }

      this.saveConfig();
   }

   public boolean isLoggedIn() {
      return this.loggedIn;
   }

   public String getUserId() {
      return this.userId;
   }

   public void setUserId(String userId) {
      this.userId = userId;
      this.saveConfig();
   }

   public String getNickname() {
      return this.nickname;
   }

   public void setNickname(String nickname) {
      this.nickname = nickname;
      this.saveConfig();
   }

   public String getAvatarUrl() {
      return this.avatarUrl;
   }

   public void setAvatarUrl(String avatarUrl) {
      this.avatarUrl = avatarUrl;
      this.saveConfig();
   }
}

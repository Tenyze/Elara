package elara.config.music;

import cc.polyfrost.oneconfig.renderer.font.Font;
import cc.polyfrost.oneconfig.renderer.font.FontHelper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CjkFontInjector {
   private static volatile boolean injected = false;
   private static volatile byte[] cjkFontData = null;
   private static Method nvgCreateFontMem = null;
   private static Method nvgAddFallbackFont = null;
   private static boolean reflectionInit = false;
   private static final String CJK_FONT_NAME = "elara-cjk";
   private static final String CJK_FONT_RESOURCE = "/assets/elara/fonts/SourceHanSansSC-Regular.otf";
   private static final String[] BASE_FONTS = new String[]{"inter-regular", "inter-bold", "inter-medium", "inter-semibold"};

   public static void tryInject(long vg) {
      if (!injected) {
         try {
            if (!reflectionInit) {
               initReflection();
               reflectionInit = true;
            }

            boolean fontLoaded = false;
            fontLoaded = tryFontHelperBuffer(vg);
            if (!fontLoaded) {
               fontLoaded = tryFontHelperResource(vg);
            }

            if (!fontLoaded && nvgCreateFontMem != null) {
               fontLoaded = tryNvgCreateFontMem(vg);
            }

            if (!fontLoaded) {
               System.err.println("[Elara Unicode] All CJK font loading approaches failed");
               return;
            }

            if (nvgAddFallbackFont == null) {
               System.err.println("[Elara Unicode] nvgAddFallbackFont unavailable — CJK loaded but not linked as fallback");
            } else {
               int linked = 0;

               for (String baseFont : BASE_FONTS) {
                  try {
                     nvgAddFallbackFont.invoke(null, vg, baseFont, "elara-cjk");
                     linked++;
                  } catch (Throwable e) {
                     System.err.println("[Elara Unicode] Fallback link failed for " + baseFont + ": " + e.getMessage());
                  }
               }

               System.out.println("[Elara Unicode] CJK fallback linked for " + linked + "/" + BASE_FONTS.length + " base fonts");
            }

            injected = true;
            System.out.println("[Elara Unicode] CJK font injection complete (Source Han Sans)");
         } catch (Throwable e) {
            System.err.println("[Elara Unicode] Injection error: " + e);
            e.printStackTrace();
         }
      }
   }

   private static boolean tryFontHelperBuffer(long vg) {
      try {
         byte[] data = ensureFontData();
         if (data == null) {
            return false;
         }

         Font cjkFont = new Font("elara-cjk", null);
         ByteBuffer buf = toDirectBuffer(data);
         cjkFont.setBuffer(buf);
         FontHelper fontHelper = FontHelper.INSTANCE;
         if (fontHelper == null) {
            return false;
         }

         fontHelper.loadFont(vg, cjkFont);
         if (cjkFont.isLoaded()) {
            System.out.println("[Elara Unicode] CJK font loaded via FontHelper (buffer)");
            return true;
         }

         System.err.println("[Elara Unicode] FontHelper.loadFont did not mark font as loaded");
      } catch (Throwable e) {
         System.err.println("[Elara Unicode] FontHelper buffer path failed: " + e.getMessage());
      }

      return false;
   }

   private static boolean tryFontHelperResource(long vg) {
      try {
         Font cjkFont = new Font("elara-cjk", "/assets/elara/fonts/SourceHanSansSC-Regular.otf");
         FontHelper fontHelper = FontHelper.INSTANCE;
         if (fontHelper == null) {
            return false;
         }

         fontHelper.loadFont(vg, cjkFont);
         if (cjkFont.isLoaded()) {
            System.out.println("[Elara Unicode] CJK font loaded via FontHelper (resource path)");
            return true;
         }
      } catch (Throwable e) {
         System.err.println("[Elara Unicode] FontHelper resource path failed: " + e.getMessage());
      }

      return false;
   }

   private static boolean tryNvgCreateFontMem(long vg) {
      try {
         byte[] data = ensureFontData();
         if (data == null) {
            return false;
         }

         ByteBuffer buf = toDirectBuffer(data);
         Object result = nvgCreateFontMem.invoke(null, vg, "elara-cjk", buf, 0);
         if (result instanceof Integer && (Integer)result >= 0) {
            System.out.println("[Elara Unicode] CJK font loaded via nvgCreateFontMem (id=" + result + ")");
            return true;
         }

         System.err.println("[Elara Unicode] nvgCreateFontMem returned invalid id: " + result);
      } catch (Throwable e) {
         System.err.println("[Elara Unicode] nvgCreateFontMem failed: " + e.getMessage());
      }

      return false;
   }

   private static byte[] ensureFontData() {
      if (cjkFontData != null && cjkFontData.length > 0) {
         return cjkFontData;
      } else {
         cjkFontData = loadBundledFont();
         if (cjkFontData != null && cjkFontData.length != 0) {
            System.out.println("[Elara Unicode] Bundled CJK font read: " + cjkFontData.length + " bytes");
            return cjkFontData;
         } else {
            System.err.println("[Elara Unicode] Bundled CJK font not available: /assets/elara/fonts/SourceHanSansSC-Regular.otf");
            return null;
         }
      }
   }

   private static ByteBuffer toDirectBuffer(byte[] data) {
      ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
      buf.order(ByteOrder.nativeOrder());
      buf.put(data);
      ((Buffer)buf).flip();
      return buf;
   }

   private static byte[] loadBundledFont() {
      // 1) First: bundled JAR resource
      try {
         InputStream is = CjkFontInjector.class.getResourceAsStream("/assets/elara/fonts/SourceHanSansSC-Regular.otf");

         Object var8;
         label55: {
            byte[] var4;
            try {
               if (is == null) {
                  var8 = null;
                  break label55;
               }

               ByteArrayOutputStream bos = new ByteArrayOutputStream(16777216);
               byte[] tmp = new byte[8192];

               int read;
               while ((read = is.read(tmp)) != -1) {
                  bos.write(tmp, 0, read);
               }

               var4 = bos.toByteArray();
            } catch (Throwable var6) {
               if (is != null) {
                  try {
                     is.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (is != null) {
               is.close();
            }

            if (var4 != null && var4.length > 1024) {
               return var4;
            }
         }

         if (is != null) {
            is.close();
         }
      } catch (Exception e) {
         System.err.println("[Elara Unicode] Failed to read bundled font: " + e.getMessage());
      }

      // 2) Fallback: external filesystem (users can drop font file without rebuilding the JAR)
      try {
         File configFontDir = new File("./config/elara/fonts");
         boolean dirExisted = configFontDir.isDirectory();
         if (!dirExisted) {
            try {
               configFontDir.mkdirs();
            } catch (Throwable ignored) {}
         }

         File[] candidates = new File[] {
                 new File(configFontDir, "SourceHanSansSC-Regular.otf"),
                 new File(configFontDir, "SourceHanSansSC.otf"),
                 new File(configFontDir, "SourceHanSansSC-Regular.ttf"),
                 new File(configFontDir, "SourceHanSansSC.ttf"),
                 new File("./config/elara/SourceHanSansSC-Regular.otf"),
                 new File("./SourceHanSansSC-Regular.otf")
         };

         File chosen = null;
         for (File f : candidates) {
            if (f != null && f.isFile() && f.length() > 1024) {
               chosen = f;
               break;
            }
         }

         if (chosen != null) {
            FileInputStream fis = new FileInputStream(chosen);
            try {
               ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(32L * 1024 * 1024, chosen.length()));
               byte[] tmp = new byte[8192];
               int read;
               while ((read = fis.read(tmp)) != -1) bos.write(tmp, 0, read);
               byte[] data = bos.toByteArray();
               System.out.println("[Elara Unicode] Loaded CJK font from external file: "
                       + chosen.getAbsolutePath() + " (" + data.length + " bytes)");
               return data;
            } finally {
               try { fis.close(); } catch (Throwable ignored) {}
            }
         } else {
            if (configFontDir.isDirectory() && !dirExisted) {
               System.out.println("[Elara Unicode] Font not bundled; external font directory prepared at "
                       + configFontDir.getAbsolutePath()
                       + " — drop SourceHanSansSC-Regular.otf (.ttf also supported) into this folder to enable CJK.");
            }
            return null;
         }
      } catch (Throwable t) {
         System.err.println("[Elara Unicode] External CJK font fallback failed: " + t.getMessage());
         return null;
      }
   }

   private static void initReflection() {
      try {
         Class<?> nanoVGClass = Class.forName("org.lwjgl.nanovg.NanoVG");

         try {
            nvgCreateFontMem = nanoVGClass.getMethod("nvgCreateFontMem", long.class, CharSequence.class, ByteBuffer.class, int.class);
         } catch (NoSuchMethodException e) {
            System.err.println("[Elara Unicode] nvgCreateFontMem(CharSequence) not found");
         }

         try {
            nvgAddFallbackFont = nanoVGClass.getMethod("nvgAddFallbackFont", long.class, CharSequence.class, CharSequence.class);
         } catch (NoSuchMethodException e) {
            System.err.println("[Elara Unicode] nvgAddFallbackFont(CharSequence) not found");
         }

         System.out.println("[Elara Unicode] Reflection ready: createFontMem=" + (nvgCreateFontMem != null) + ", addFallback=" + (nvgAddFallbackFont != null));
      } catch (ClassNotFoundException e) {
         System.err.println("[Elara Unicode] NanoVG class not found: " + e.getMessage());
      } catch (Throwable e) {
         System.err.println("[Elara Unicode] Reflection init failed: " + e);
      }
   }
}

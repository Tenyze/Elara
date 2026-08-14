package elara.config.gui;

import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.renderer.scissor.Scissor;
import cc.polyfrost.oneconfig.renderer.scissor.ScissorHelper;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import cc.polyfrost.oneconfig.utils.color.ColorUtils;
import elara.Elara;
import elara.config.NotificationHelper;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DebugPage extends Page {
   private static final Logger logger = LoggerFactory.getLogger(DebugPage.class);
   private static final int CONTENT_WIDTH = 920;
   private static final int LEFT_PADDING = 20;
   private static final int TOP_MARGIN = 40;

   private static final int LOG_INFO = ColorUtils.getColor(66, 133, 244, 255);
   private static final int LOG_WARN = ColorUtils.getColor(251, 188, 5, 255);
   private static final int LOG_ERROR = ColorUtils.getColor(234, 67, 53, 255);
   private static final int LOG_DEBUG = ColorUtils.getColor(52, 168, 83, 255);

   private final ConcurrentLinkedQueue<LogEntry> logBuffer = new ConcurrentLinkedQueue<>();
   private final AtomicInteger pendingCount = new AtomicInteger(0);
   private static final int MAX_LOG_ENTRIES = 500;

   private final List<String> displayedLogs = new ArrayList<>();
   private final List<Integer> logColors = new ArrayList<>();

   private float logScroll = 0.0F;
   private boolean logAutoScroll = true;
   private float logMaxScroll = 0.0F;
   private int logTotalHeight = 0;
   private float logHScroll = 0.0F;
   private float logHMaxScroll = 0.0F;
   private boolean logHScrollDragging = false;
   private float logHScrollDragStartX = 0.0F;
   private float logHScrollAtDragStart = 0.0F;

   private final transient AtomicInteger notificationTestIndex = new AtomicInteger(0);
   private final BasicButton testNotificationsBtn = new BasicButton(200, 36, "Test All Notifications", 2, ColorPalette.PRIMARY);
   private final BasicButton clearLogBtn = new BasicButton(120, 36, "Clear Log", 2, ColorPalette.SECONDARY);
   private final BasicButton toggleAutoScrollBtn = new BasicButton(140, 36, "Auto Scroll: ON", 2, ColorPalette.SECONDARY);
   private final BasicButton copyLogBtn = new BasicButton(120, 36, "Copy Log", 2, ColorPalette.SECONDARY);
   private final BasicButton openLogsDirBtn = new BasicButton(160, 36, "Open Logs Folder", 2, ColorPalette.SECONDARY);

   private String debugInfo = "";
   private long lastInfoUpdate = 0L;
   private int totalSize = 728;
   private Thread logTailerThread;

   public DebugPage() {
      super("Debug");
      this.setupLogHandler();
      this.testNotificationsBtn.setClickAction(this::testAllNotifications);
      this.clearLogBtn.setClickAction(this::clearLogs);
      this.toggleAutoScrollBtn.setClickAction(this::toggleAutoScroll);
      this.copyLogBtn.setClickAction(this::copyLog);
      this.openLogsDirBtn.setClickAction(this::openLogsDir);
   }

   private void setupLogHandler() {
      this.logTailerThread = new Thread(this::tailLatestLog, "Elara-LogTailer");
      this.logTailerThread.setDaemon(true);
      this.logTailerThread.start();
   }

   private void tailLatestLog() {
      File logFile = new File("./logs/latest.log");
      long lastPosition = 0L;
      boolean initial = true;

      while (!Thread.currentThread().isInterrupted()) {
         try {
            if (!logFile.exists()) {
               Thread.sleep(500L);
               continue;
            }
            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
               long length = raf.length();
               if (initial) {
                  long start = Math.max(0L, length - 500_000L);
                  raf.seek(start);
                  if (start > 0L) {
                     raf.readLine(); // skip partial line
                  }
                  String line;
                  while ((line = raf.readLine()) != null) {
                     this.addRawLog(line);
                  }
                  lastPosition = length;
                  initial = false;
               } else if (length < lastPosition) {
                  raf.seek(0L);
                  String line;
                  while ((line = raf.readLine()) != null) {
                     this.addRawLog(line);
                  }
                  lastPosition = length;
               } else if (length > lastPosition) {
                  raf.seek(lastPosition);
                  String line;
                  while ((line = raf.readLine()) != null) {
                     this.addRawLog(line);
                  }
                  lastPosition = length;
               }
            }
            Thread.sleep(200L);
         } catch (Exception e) {
            try {
               Thread.sleep(1000L);
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               return;
            }
         }
      }
   }

   private void addRawLog(String rawLine) {
      if (rawLine == null) return;
      String line = new String(rawLine.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
      String upper = line.toUpperCase();
      int color = LOG_INFO;
      if (upper.contains("ERROR") || upper.contains("FATAL")) {
         color = LOG_ERROR;
      } else if (upper.contains("WARN")) {
         color = LOG_WARN;
      } else if (upper.contains("DEBUG") || upper.contains("TRACE")) {
         color = LOG_DEBUG;
      }
      this.addLog(line, color);
   }

   private void testAllNotifications() {
      int index = this.notificationTestIndex.getAndIncrement() % 25;
      switch (index) {
         case 0:
            NotificationHelper.sendMusicPlay("Test Song", "Test Artist");
            break;
         case 1:
            NotificationHelper.sendMusicPause("Test Song", "Test Artist");
            break;
         case 2:
            NotificationHelper.sendMusicNext("Next Song", "Next Artist");
            break;
         case 3:
            NotificationHelper.sendMusicStop();
            break;
         case 4:
            NotificationHelper.sendMusicError("Connection timeout");
            break;
         case 5:
            NotificationHelper.sendModuleToggle("Music Player", true);
            break;
         case 6:
            NotificationHelper.sendModuleToggle("Music Player", false);
            break;
         case 7:
            NotificationHelper.sendModuleToggle("Visualizer", true);
            break;
         case 8:
            NotificationHelper.sendModuleToggle("Visualizer", false);
            break;
         case 9:
            NotificationHelper.sendLoginSuccess("ElaraUser");
            break;
         case 10:
            NotificationHelper.sendLoginFailed("Invalid credentials");
            break;
         case 11:
            NotificationHelper.sendLogout();
            break;
         case 12:
            NotificationHelper.sendProfileCreated("Default Profile");
            break;
         case 13:
            NotificationHelper.sendProfileSaved("Default Profile");
            break;
         case 14:
            NotificationHelper.sendProfileLoaded("Default Profile");
            break;
         case 15:
            NotificationHelper.sendProfileDeleted("Default Profile");
            break;
         case 16:
            NotificationHelper.sendProfileExists("Default Profile");
            break;
         case 17:
            NotificationHelper.sendConfigSaved();
            break;
         case 18:
            NotificationHelper.sendConfigLoaded();
            break;
         case 19:
            NotificationHelper.sendDownloadComplete("Song.mp3");
            break;
         case 20:
            NotificationHelper.sendDownloadError("Network error");
            break;
         case 21:
            NotificationHelper.sendDownloadCancelled();
            break;
         case 22:
            NotificationHelper.sendCacheCleared();
            break;
         case 23:
            NotificationHelper.sendApiConnected("QQ Music");
            break;
         case 24:
            NotificationHelper.sendPlatformSwitched("NetEase Music");
            break;
         default:
            break;
      }
   }

   private void clearLogs() {
      this.logBuffer.clear();
      this.pendingCount.set(0);
      this.displayedLogs.clear();
      this.logColors.clear();
      this.logScroll = 0.0F;
      this.logMaxScroll = 0.0F;
      this.logTotalHeight = 0;
      this.logHScroll = 0.0F;
      this.logHMaxScroll = 0.0F;
      this.logHScrollDragging = false;
   }

   private void toggleAutoScroll() {
      this.logAutoScroll = !this.logAutoScroll;
      this.toggleAutoScrollBtn.setText("Auto Scroll: " + (this.logAutoScroll ? "ON" : "OFF"));
   }

   private void copyLog() {
      StringBuilder sb = new StringBuilder();
      for (String log : this.displayedLogs) {
         sb.append(log).append("\n");
      }
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
      NotificationHelper.sendSuccess("Debug", "Log copied to clipboard");
   }

   private void openLogsDir() {
      try {
         File logsDir = new File("./logs");
         if (!logsDir.exists()) logsDir.mkdirs();
         if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(logsDir);
         } else {
            Runtime.getRuntime().exec("explorer.exe \"" + logsDir.getAbsolutePath() + "\"");
         }
      } catch (Exception e) {
         NotificationHelper.sendError("Debug", "Failed to open logs folder");
      }
   }

   private void updateDebugInfo() {
      long now = System.currentTimeMillis();
      if (now - this.lastInfoUpdate < 2000L) return;
      this.lastInfoUpdate = now;

      Minecraft mc = Minecraft.getMinecraft();
      StringBuilder sb = new StringBuilder();
      sb.append("Elara Version: ").append(Elara.version).append("\n");
      sb.append("Minecraft Version: 1.8.9\n");
      sb.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
      sb.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
      sb.append("Memory: ")
              .append(Runtime.getRuntime().freeMemory() / 1024 / 1024)
              .append("MB / ")
              .append(Runtime.getRuntime().totalMemory() / 1024 / 1024)
              .append("MB\n");
      if (mc.thePlayer != null) {
         sb.append("Player: ").append(mc.thePlayer.getName()).append("\n");
         sb.append("Health: ").append(mc.thePlayer.getHealth()).append("\n");
         sb.append("Food: ").append(mc.thePlayer.getFoodStats().getFoodLevel()).append("\n");
      }
      this.debugInfo = sb.toString();
   }

   @Override
   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      int contentX = x + LEFT_PADDING;
      int cy = y + TOP_MARGIN;

      this.updateDebugInfo();
      this.updateLogDisplay();

      nvg.drawText(vg, "DEBUG TOOLS", contentX, cy, ElaraColors.WHITE, 24.0F, Fonts.BOLD);
      nvg.drawText(vg, "Developer tools and debugging utilities", contentX, cy + 32, ElaraColors.white60(), 14.0F, Fonts.MEDIUM);
      nvg.drawLine(vg, contentX, cy + 56, contentX + CONTENT_WIDTH, cy + 56, 1.0F, ElaraColors.GRAY_600);

      cy += 72;
      cy = this.drawNotificationTestSection(vg, contentX, cy, inputHandler);
      cy = this.drawLogConsoleSection(vg, contentX, cy, inputHandler);
      this.totalSize = cy - y;
   }

   private int drawNotificationTestSection(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      nvg.drawText(vg, "NOTIFICATION TEST", x, y, ElaraColors.accent(), 12.0F, Fonts.BOLD);
      nvg.drawRoundedRect(vg, x, y + 20, CONTENT_WIDTH, 60, ElaraColors.GRAY_800, 12.0F);
      this.testNotificationsBtn.draw(vg, x + 16, y + 32, inputHandler);
      nvg.drawText(vg, "Click to cycle through all notification types", x + 232, y + 46, ElaraColors.white60(), 13.0F, Fonts.MEDIUM);
      return y + 88;
   }

   private void updateLogDisplay() {
      while (!this.logBuffer.isEmpty()) {
         LogEntry entry = this.logBuffer.poll();
         this.pendingCount.decrementAndGet();
         this.displayedLogs.add(entry.message);
         this.logColors.add(entry.color);
         if (this.displayedLogs.size() > MAX_LOG_ENTRIES) {
            this.displayedLogs.remove(0);
            this.logColors.remove(0);
         }
      }
      this.logTotalHeight = this.displayedLogs.size() * 18;
      this.logMaxScroll = Math.max(0, this.logTotalHeight - 280);
      if (this.logAutoScroll && this.logMaxScroll > 0.0F) {
         this.logScroll = this.logMaxScroll;
      }
   }

   private int drawLogConsoleSection(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      ScissorHelper scissorHelper = ScissorHelper.INSTANCE;

      nvg.drawText(vg, "MINECRAFT LOG CONSOLE", x, y, ElaraColors.accent(), 12.0F, Fonts.BOLD);
      nvg.drawRoundedRect(vg, x, y + 20, CONTENT_WIDTH, 340, ElaraColors.GRAY_800, 12.0F);

      int consoleX = x + 16;
      int consoleY = y + 48;
      int consoleW = CONTENT_WIDTH - 32;
      int consoleH = 220;
      int textPaddingX = 8;
      int textPaddingY = 8;
      int hScrollBarHeight = 14;
      int vScrollBarWidth = 12;
      int contentW = Math.max(1, consoleW - textPaddingX * 2 - vScrollBarWidth);
      int contentH = Math.max(1, consoleH - textPaddingY * 2 - hScrollBarHeight);
      int contentX = consoleX + textPaddingX;
      int contentY = consoleY + textPaddingY;

      nvg.drawRoundedRect(vg, consoleX, consoleY, consoleW, consoleH, ElaraColors.GRAY_700, 8.0F);

      // measure max width
      float maxLogWidth = 0;
      for (String log : this.displayedLogs) {
         float w = nvg.getTextWidth(vg, log, 12.0F, Fonts.REGULAR);
         if (w > maxLogWidth) maxLogWidth = w;
      }
      this.logHMaxScroll = Math.max(0, maxLogWidth - contentW);
      if (this.logHScroll > this.logHMaxScroll) this.logHScroll = this.logHMaxScroll;
      if (this.logHScroll >= 0 && this.logHScroll <= 3.0F) this.logHScroll = 0.0F;

      boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
      if (shiftDown) {
         double dWheel = inputHandler.getDWheel(true);
         if (dWheel != 0) {
            this.logHScroll += (float) (-dWheel * 0.5);
            this.logHScroll = Math.max(0, Math.min(this.logHMaxScroll, this.logHScroll));
         }
      }

      Scissor scissor = scissorHelper.scissor(vg, contentX, contentY, contentW, contentH);
      int lineHeight = 18;
      int startIdx = (int) (this.logScroll / lineHeight);
      int visibleLines = Math.min(this.displayedLogs.size() - startIdx, contentH / lineHeight);

      for (int i = 0; i < visibleLines; i++) {
         int idx = startIdx + i;
         if (idx >= this.displayedLogs.size()) break;
         String log = this.displayedLogs.get(idx);
         int color = this.logColors.get(idx);
         float ty = contentY + 12 + i * lineHeight;
         if (ty > contentY + contentH) break;
         if (ty < contentY) continue;
         float tx = contentX - this.logHScroll;
         nvg.drawText(vg, log, tx, ty, color, 12.0F, Fonts.REGULAR);
      }

      scissorHelper.resetScissor(vg, scissor);

      if (this.displayedLogs.isEmpty()) {
         nvg.drawText(vg, "No logs yet. Start using the game to see logs here.",
                 contentX, consoleY + consoleH / 2, ElaraColors.white60(), 13.0F, Fonts.MEDIUM);
      }

      // Vertical scrollbar
      int vScrollX = consoleX + consoleW - 10;
      int vScrollY = contentY;
      int vScrollW = 6;
      int vScrollH = contentH;
      float scrollPercent = this.logMaxScroll > 0 ? this.logScroll / this.logMaxScroll : 0;
      float thumbH = Math.max(30, vScrollH * (float) contentH / Math.max(1, this.logTotalHeight));
      float thumbY = vScrollY + (vScrollH - thumbH) * scrollPercent;
      nvg.drawRoundedRect(vg, vScrollX, vScrollY, vScrollW, vScrollH, ElaraColors.GRAY_600, 3.0F);
      nvg.drawRoundedRect(vg, vScrollX, thumbY, vScrollW, thumbH, ElaraColors.accent(), 3.0F);

      boolean vScrollHovered = inputHandler.isAreaHovered(vScrollX - 4, vScrollY, vScrollW + 8, vScrollH);
      if (vScrollHovered && Platform.getMousePlatform().isButtonDown(0)) {
         float mouseY = inputHandler.mouseY() - vScrollY;
         this.logScroll = (mouseY / vScrollH) * this.logMaxScroll;
         this.logScroll = Math.max(0, Math.min(this.logMaxScroll, this.logScroll));
         this.logAutoScroll = false;
         this.toggleAutoScrollBtn.setText("Auto Scroll: OFF");
      }

      // Horizontal scrollbar
      int hScrollX = contentX;
      int hScrollY = consoleY + consoleH - hScrollBarHeight + 4;
      int hScrollW = contentW;
      int hScrollH = 6;
      if (this.logHMaxScroll > 0) {
         float hScrollPercent = this.logHScroll / this.logHMaxScroll;
         float thumbW = Math.max(40, hScrollW * (float) contentW / (contentW + this.logHMaxScroll));
         float thumbX = hScrollX + (hScrollW - thumbW) * hScrollPercent;
         nvg.drawRoundedRect(vg, hScrollX, hScrollY, hScrollW, hScrollH, ElaraColors.GRAY_600, 3.0F);
         nvg.drawRoundedRect(vg, thumbX, hScrollY, thumbW, hScrollH, ElaraColors.accent(), 3.0F);

         boolean hScrollHovered = inputHandler.isAreaHovered(hScrollX, hScrollY - 2, hScrollW, hScrollH + 4);
         boolean mouseDown = Platform.getMousePlatform().isButtonDown(0);
         if (hScrollHovered && mouseDown) {
            float mouseX = inputHandler.mouseX();
            if (!this.logHScrollDragging) {
               this.logHScrollDragging = true;
               this.logHScrollDragStartX = mouseX;
               this.logHScrollAtDragStart = this.logHScroll;
            }
            float trackDelta = mouseX - this.logHScrollDragStartX;
            this.logHScroll = this.logHScrollAtDragStart + (trackDelta / Math.max(1.0F, hScrollW - thumbW)) * this.logHMaxScroll;
            this.logHScroll = Math.max(0, Math.min(this.logHMaxScroll, this.logHScroll));
         } else if (!mouseDown) {
            this.logHScrollDragging = false;
         }

         boolean hTrackClicked = hScrollHovered && inputHandler.isClicked();
         if (hTrackClicked && !this.logHScrollDragging) {
            float mouseX = inputHandler.mouseX();
            if (mouseX < thumbX) this.logHScroll = 0.0F;
            else if (mouseX > thumbX + thumbW) this.logHScroll = this.logHMaxScroll;
         }
      } else {
         this.logHScrollDragging = false;
      }

      int btnY = y + 300;
      this.clearLogBtn.draw(vg, x + 16, btnY, inputHandler);
      this.toggleAutoScrollBtn.draw(vg, x + 144, btnY, inputHandler);
      this.copyLogBtn.draw(vg, x + 300, btnY, inputHandler);
      this.openLogsDirBtn.draw(vg, x + 436, btnY, inputHandler);

      return y + 368;
   }

   @Override
   public int getMaxScrollHeight() {
      return this.totalSize;
   }

   public void addLog(String message, int color) {
      this.logBuffer.add(new LogEntry(message, color));
      int size = this.pendingCount.incrementAndGet();
      while (size > MAX_LOG_ENTRIES) {
         LogEntry dropped = this.logBuffer.poll();
         if (dropped == null) break;
         size = this.pendingCount.decrementAndGet();
      }
   }

   public void addInfo(String message) {
      this.addLog("[INFO] " + message, LOG_INFO);
   }

   public void addWarn(String message) {
      this.addLog("[WARN] " + message, LOG_WARN);
   }

   public void addError(String message) {
      this.addLog("[ERROR] " + message, LOG_ERROR);
   }

   public void addDebug(String message) {
      this.addLog("[DEBUG] " + message, LOG_DEBUG);
   }

   private static class LogEntry {
      final String message;
      final int color;
      LogEntry(String message, int color) {
         this.message = message;
         this.color = color;
      }
   }
}
package elara.config.gui;

import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.text.TextInputField;
import cc.polyfrost.oneconfig.gui.pages.Page;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.asset.Icon;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import elara.config.NotificationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.awt.Desktop;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import me.tenyze.accountmanager.AccountManager;
import me.tenyze.accountmanager.auth.Account;
import me.tenyze.accountmanager.auth.MicrosoftAuth;
import me.tenyze.accountmanager.auth.SessionManager;

public class AccountManagerPage extends Page {
   private static final Minecraft mc = Minecraft.getMinecraft();
   private static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
   private static final String SCOPE = "XboxLive.signin XboxLive.offline_access";
   private static final Icon ACCOUNT_ICON = safeLoadIcon("/assets/elara/icons/Account.png");
   private static final int WHITE_90 = ElaraColors.white90();
   private static final int WHITE_60 = ElaraColors.white60();
   private static final int WHITE_30 = ElaraColors.white30();
   private static final int SECTION_BG = ElaraColors.GRAY_800;
   private static final int ENTRY_BG = ElaraColors.GRAY_750;
   private static final float SECTION_RADIUS = 10.0F;
   private static final float ENTRY_RADIUS = 8.0F;
   private static final int SECTION_PAD = 16;
   private static final int CONTENT_W = 796;

   private final List<AccountEntry> entries = new ArrayList<>();
   private final BasicButton addMicrosoftBtn = new BasicButton(140, 32, "Add Microsoft", 2, ColorPalette.PRIMARY);
   private final BasicButton tokenLoginBtn = new BasicButton(140, 32, "Save Token", 2, ColorPalette.PRIMARY);
   private final TextInputField offlineUsernameInput = new TextInputField(280, 32, "Username", false, false);
   private final TextInputField tokenInputField = new TextInputField(580, 32, "Minecraft Access Token (eyJ...) or Microsoft Refresh Token", false, false);
   private final BasicButton createOfflineBtn = new BasicButton(120, 32, "Create", 2, ColorPalette.SECONDARY);
   private final ExecutorService executor = Executors.newSingleThreadExecutor();
   private boolean isAuthenticating = false;
   private boolean isTokenAuthenticating = false;
   private CompletableFuture<Void> authTask = null;
   private CompletableFuture<Void> tokenAuthTask = null;
   private String authStatus = "";
   private String tokenAuthStatus = "";
   private int totalSize = 728;

   private static Icon safeLoadIcon(String path) {
      try {
         return new Icon(path);
      } catch (Throwable t) {
         return null;
      }
   }

   public AccountManagerPage() {
      super("Accounts");
      this.addMicrosoftBtn.setClickAction(this::startMicrosoftAuth);
      this.tokenLoginBtn.setClickAction(this::startTokenAuth);
      this.createOfflineBtn.setClickAction(this::createOffline);
      this.loadAccounts();
   }

   private void loadAccounts() {
      this.entries.clear();
      for (Account account : AccountManager.accounts) {
         this.entries.add(new AccountEntry(account));
      }
   }

   private void startMicrosoftAuth() {
      if (this.isAuthenticating) return;
      this.isAuthenticating = true;
      this.addMicrosoftBtn.setText("Authenticating...");
      this.authStatus = "Preparing auth link...";
      String state = UUID.randomUUID().toString().substring(0, 8);
      URI authUri = MicrosoftAuth.getMSAuthLink(state);

      if (authUri != null) {
         try {
            Desktop.getDesktop().browse(authUri);
            this.authStatus = "Waiting for login...";
         } catch (Throwable e) {
            this.authStatus = "Failed to open browser";
            NotificationHelper.send("Accounts", "Failed to open browser!", ACCOUNT_ICON);
            this.resetAuthState();
            return;
         }

         AtomicReference<String> refreshToken = new AtomicReference<>("");
         AtomicReference<String> accessToken = new AtomicReference<>("");
         this.authTask = MicrosoftAuth.acquireMSAuthCode(state, this.executor)
                 .thenComposeAsync(msAuthCode -> {
                    this.authStatus = "Acquiring Microsoft access tokens...";
                    return MicrosoftAuth.acquireMSAccessTokens(msAuthCode, this.executor);
                 })
                 .thenComposeAsync(msAccessTokens -> {
                    this.authStatus = "Acquiring Xbox access token...";
                    refreshToken.set(msAccessTokens.get("refresh_token"));
                    return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), this.executor);
                 })
                 .thenComposeAsync(xboxAccessToken -> {
                    this.authStatus = "Acquiring Xbox XSTS token...";
                    return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, this.executor);
                 })
                 .thenComposeAsync(xboxXstsData -> {
                    this.authStatus = "Acquiring Minecraft access token...";
                    return MicrosoftAuth.acquireMCAccessToken(xboxXstsData.get("Token"), xboxXstsData.get("uhs"), this.executor);
                 })
                 .thenComposeAsync(mcToken -> {
                    this.authStatus = "Fetching Minecraft profile...";
                    accessToken.set(mcToken);
                    return MicrosoftAuth.login(mcToken, this.executor);
                 })
                 .thenAccept(session -> {
                    this.authStatus = "Login successful!";
                    this.addAccount(session, refreshToken.get(), accessToken.get());
                    NotificationHelper.send("Accounts", "Logged in as: " + session.getUsername(), ACCOUNT_ICON);
                    this.resetAuthState();
                 })
                 .exceptionally(error -> {
                    this.authStatus = "Auth failed: " + error.getMessage();
                    NotificationHelper.send("Accounts", "Auth failed: " + error.getMessage(), ACCOUNT_ICON);
                    this.resetAuthState();
                    return null;
                 });
      } else {
         this.authStatus = "Failed to generate auth link";
         NotificationHelper.send("Accounts", "Failed to generate auth link!", ACCOUNT_ICON);
         this.resetAuthState();
      }
   }

   private void addAccount(Session session, String refreshToken, String accessToken) {
      Account acc = new Account(refreshToken, accessToken, session.getUsername(), CLIENT_ID, SCOPE);

      for (Account account : AccountManager.accounts) {
         if (acc.getUsername().equals(account.getUsername())) {
            acc.setUnban(account.getUnban());
            AccountManager.accounts.remove(account);
            break;
         }
      }

      AccountManager.accounts.add(acc);
      AccountManager.save();
      SessionManager.set(session);
      this.loadAccounts();
   }

   private void removeAccount(AccountEntry entry) {
      AccountManager.accounts.remove(entry.account);
      AccountManager.save();
      NotificationHelper.send("Accounts", "Removed: " + entry.account.getUsername(), ACCOUNT_ICON);
      this.loadAccounts();
   }

   private void loginAccount(AccountEntry entry) {
      Account account = entry.account;
      if (account.getAccessToken() != null && !account.getAccessToken().isEmpty()) {
         Session session = new Session(account.getUsername(), "", account.getAccessToken(), "MOJANG");
         SessionManager.set(session);
         NotificationHelper.send("Accounts", "Logged in as: " + account.getUsername(), ACCOUNT_ICON);
      } else if (account.getRefreshToken() != null && !account.getRefreshToken().isEmpty()) {
         NotificationHelper.send("Accounts", "Account has no access token!", ACCOUNT_ICON);
      } else {
         Session session = new Session(account.getUsername(), "", "", "OFFLINE");
         SessionManager.set(session);
         NotificationHelper.send("Accounts", "Logged in as: " + account.getUsername() + " (Offline)", ACCOUNT_ICON);
      }
   }

   private void refreshAccountToken(AccountEntry entry) {
      Account account = entry.account;
      if (account.getRefreshToken() == null || account.getRefreshToken().isEmpty()) {
         NotificationHelper.send("Accounts", "Cannot refresh offline account!", ACCOUNT_ICON);
         return;
      }

      entry.refreshing = true;
      entry.refreshButton.setText("Refreshing...");
      MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), this.executor)
              .thenComposeAsync(msAccessTokens -> {
                 account.setRefreshToken(msAccessTokens.get("refresh_token"));
                 return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), this.executor);
              })
              .thenComposeAsync(xboxAccessToken -> MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, this.executor))
              .thenComposeAsync(xboxXstsData -> MicrosoftAuth.acquireMCAccessToken(xboxXstsData.get("Token"), xboxXstsData.get("uhs"), this.executor))
              .thenComposeAsync(mcToken -> {
                 account.setAccessToken(mcToken);
                 return MicrosoftAuth.login(mcToken, this.executor);
              })
              .thenAccept(session -> {
                 account.setUsername(session.getUsername());
                 AccountManager.save();
                 NotificationHelper.send("Accounts", "Refreshed: " + account.getUsername(), ACCOUNT_ICON);
                 entry.refreshing = false;
                 entry.refreshButton.setText("Refresh");
              })
              .exceptionally(error -> {
                 NotificationHelper.send("Accounts", "Refresh failed: " + error.getMessage(), ACCOUNT_ICON);
                 entry.refreshing = false;
                 entry.refreshButton.setText("Refresh");
                 return null;
              });
   }

   private void createOffline() {
      String username = this.offlineUsernameInput.getInput();
      if (username == null || username.trim().isEmpty()) {
         NotificationHelper.send("Accounts", "Please enter a username!", ACCOUNT_ICON);
         return;
      }

      Account acc = new Account("", "", username.trim(), "", "");
      for (Account account : AccountManager.accounts) {
         if (acc.getUsername().equals(account.getUsername())) {
            NotificationHelper.send("Accounts", "Account already exists!", ACCOUNT_ICON);
            return;
         }
      }

      AccountManager.accounts.add(acc);
      AccountManager.save();
      this.loadAccounts();
      NotificationHelper.send("Accounts", "Created account: " + username.trim(), ACCOUNT_ICON);
      this.offlineUsernameInput.setInput("");
   }

   private void startTokenAuth() {
      if (this.isTokenAuthenticating) return;
      String rawToken = this.tokenInputField.getInput();
      if (rawToken == null || rawToken.trim().isEmpty()) {
         NotificationHelper.send("Accounts", "Please enter a token!", ACCOUNT_ICON);
         return;
      }

      String clean = rawToken.trim();
      this.isTokenAuthenticating = true;
      this.tokenLoginBtn.setText("Authenticating...");
      this.tokenAuthStatus = clean.startsWith("eyJ") ? "Using Minecraft Access Token directly..." : "Refreshing Microsoft Session...";
      this.tokenAuthTask = MicrosoftAuth.loginWithToken(clean, this.executor)
              .thenAccept(data -> {
                 String username = data.get("username");
                 String accessToken = data.get("accessToken");
                 String refreshToken = data.get("refreshToken");
                 if (refreshToken == null) refreshToken = "";
                 Session session = new Session(username, formatDashUuid(data.get("uuid")), accessToken, "mojang");
                 this.addAccount(session, refreshToken, accessToken);
                 this.tokenAuthStatus = "Login successful: " + username;
                 NotificationHelper.send("Accounts", "Logged in as: " + username, ACCOUNT_ICON);
                 this.resetTokenAuthState();
                 this.tokenInputField.setInput("");
              })
              .exceptionally(error -> {
                 String msg = error != null ? error.getMessage() : "Unknown error";
                 if (msg == null) msg = "Login failed";
                 this.tokenAuthStatus = "Failed: " + msg;
                 NotificationHelper.send("Accounts", "Auth failed: " + msg, ACCOUNT_ICON);
                 this.resetTokenAuthState();
                 return null;
              });
   }

   private static String formatDashUuid(String uuid) {
      if (uuid == null) return "";
      String u = uuid.trim().replace("-", "");
      return u.length() != 32 ? uuid
              : u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16) + "-" + u.substring(16, 20) + "-" + u.substring(20, 32);
   }

   private void resetTokenAuthState() {
      this.isTokenAuthenticating = false;
      this.tokenLoginBtn.setText("Save Account Token");
      this.tokenAuthTask = null;
   }

   private void resetAuthState() {
      this.isAuthenticating = false;
      this.addMicrosoftBtn.setText("Add Microsoft");
      this.authTask = null;
   }

   private int drawSectionHeader(long vg, NanoVGHelper nvg, String title, int x, int y, int height) {
      nvg.drawRoundedRect(vg, x, y, CONTENT_W, height, SECTION_BG, SECTION_RADIUS);
      nvg.drawText(vg, title, x + 16, y + 16 + 14, WHITE_90, 14.0F, Fonts.MEDIUM);
      return y + 16 + 14 + 16;
   }

   @Override
   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      int iX = x + 32;
      int iY = y + 16;
      nvg.drawText(vg, "Accounts", iX, iY + 20, WHITE_90, 16.0F, Fonts.BOLD);
      iY += 36;

      int lineH = 36;
      int sectionH = 46 + lineH + 16;
      this.drawSectionHeader(vg, nvg, "Currently Logged In", iX, iY, sectionH);
      int contentY = iY + 16 + 14 + 16;

      Session currentSession = SessionManager.get();
      String currentName = currentSession != null ? currentSession.getUsername() : "Not logged in";
      Object sessionTypeObj = null;
      if (currentSession != null) {
         try {
            Method m = Session.class.getMethod("getSessionType");
            sessionTypeObj = m.invoke(currentSession);
         } catch (Throwable ignored) {}
      }

      int textX = iX + 16;
      int rowCenterY = contentY + lineH / 2;
      float nameSize = 14.0F;
      float nameBaseline = rowCenterY - 8 + nameSize * 0.38F;
      nvg.drawText(vg, currentName, textX, nameBaseline, WHITE_90, nameSize, Fonts.BOLD);

      String typeText;
      if (sessionTypeObj == null) {
         typeText = "Offline / Local account";
      } else {
         String s = String.valueOf(sessionTypeObj);
         typeText = s.isEmpty() ? "Offline / Local account" : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase() + " session";
      }
      float typeSize = 12.0F;
      float typeBaseline = rowCenterY + 8 + typeSize * 0.38F;
      nvg.drawText(vg, typeText, textX, typeBaseline, WHITE_60, typeSize, Fonts.MEDIUM);

      iY += sectionH + 10;

      int rowH = 40;
      int contentY2 = this.entries.isEmpty() ? 32 : this.entries.size() * (rowH + 4);
      int sectionH2 = 46 + contentY2 + 16;
      int contentY3 = this.drawSectionHeader(vg, nvg, "Saved Accounts", iX, iY, sectionH2);

      if (this.entries.isEmpty()) {
         nvg.drawText(vg, "No accounts saved.", iX + 16, contentY3 + 14, WHITE_60, 13.0F, Fonts.MEDIUM);
      } else {
         List<AccountEntry> entriesCopy = new ArrayList<>(this.entries);
         int rowY = contentY3;
         for (AccountEntry entry : entriesCopy) {
            int boxY = rowY;
            int boxX = iX + 16;
            int boxW = 764;
            nvg.drawRoundedRect(vg, boxX, boxY, boxW, rowH, ENTRY_BG, ENTRY_RADIUS);

            int nameX = boxX + 12;
            float nameSize2 = 13.0F;
            float nameBaseline2 = boxY + rowH / 2.0F + nameSize2 * 0.38F;
            nvg.drawText(vg, entry.account.getUsername(), nameX, nameBaseline2, WHITE_90, nameSize2, Fonts.MEDIUM);

            int btnW = 120;
            int btnH = 28;
            int btnGap = 8;
            int btnY = boxY + (rowH - btnH) / 2;
            int btnGroupW = btnW * 3 + btnGap * 2;
            int btnX = iX + CONTENT_W - 16 - btnGroupW;

            entry.loginButton.draw(vg, btnX, btnY, inputHandler);
            entry.refreshButton.draw(vg, btnX + btnW + btnGap, btnY, inputHandler);
            entry.removeButton.draw(vg, btnX + (btnW + btnGap) * 2, btnY, inputHandler);

            rowY += rowH + 4;
         }
      }

      iY += sectionH2 + 10;

      int offlineSectionH = 46 + 32 + 16;
      int offlineContentY = this.drawSectionHeader(vg, nvg, "Create Offline Account", iX, iY, offlineSectionH);
      this.offlineUsernameInput.draw(vg, iX + 16, offlineContentY + 2, inputHandler);
      this.createOfflineBtn.draw(vg, iX + 16 + 296, offlineContentY + 2, inputHandler);
      iY += offlineSectionH + 10;

      int msBtnH = 32;
      int msStatusH = this.isAuthenticating ? 28 : 0;
      int msSectionH = 46 + msBtnH + msStatusH + 20;
      int msContentY = this.drawSectionHeader(vg, nvg, "Add Microsoft Account", iX, iY, msSectionH);
      int msBtnY = msContentY + 4;
      this.addMicrosoftBtn.draw(vg, iX + 16, msBtnY, inputHandler);
      if (this.isAuthenticating) {
         int statusY = msBtnY + msBtnH + 10;
         nvg.drawText(vg, "Status: " + this.authStatus, iX + 16, statusY + 12, WHITE_60, 12.0F, Fonts.MEDIUM);
      }
      iY += msSectionH + 10;

      int tokenSectionH = 46 + 12 + 32 + (this.tokenAuthStatus.isEmpty() ? 12 : 32) + 24;
      int tokenContentY = this.drawSectionHeader(vg, nvg, "Save Account Token", iX, iY, tokenSectionH);
      nvg.drawText(vg, "Supports Minecraft Access Token (eyJ...) or Microsoft Refresh Token", iX + 16, tokenContentY + 12, WHITE_60, 12.0F, Fonts.MEDIUM);
      int inputY = tokenContentY + 24;
      int inputW = CONTENT_W - 16 - 16 - 8 - this.tokenLoginBtn.getWidth();
      int btnX = iX + 16 + inputW + 8;
      this.tokenInputField.draw(vg, iX + 16, inputY, inputHandler);
      this.tokenLoginBtn.draw(vg, btnX, inputY, inputHandler);
      if (!this.tokenAuthStatus.isEmpty()) {
         nvg.drawText(vg, "Status: " + this.tokenAuthStatus, iX + 16, inputY + 32 + 14, WHITE_60, 12.0F, Fonts.MEDIUM);
      }
      iY += tokenSectionH + 10;

      this.totalSize = iY - y + 16;
   }

   @Override
   public int getMaxScrollHeight() {
      return Math.max(this.totalSize, 728);
   }

   @Override
   public boolean isBase() {
      return false;
   }

   @Override
   public void keyTyped(char key, int keyCode) {
      this.offlineUsernameInput.keyTyped(key, keyCode);
      this.tokenInputField.keyTyped(key, keyCode);
   }

   private class AccountEntry {
      final Account account;
      final BasicButton loginButton;
      final BasicButton refreshButton;
      final BasicButton removeButton;
      boolean refreshing = false;

      AccountEntry(Account account) {
         this.account = account;
         this.loginButton = new BasicButton(120, 28, "Login", 2, ColorPalette.PRIMARY);
         this.loginButton.setClickAction(() -> AccountManagerPage.this.loginAccount(this));
         this.refreshButton = new BasicButton(120, 28, "Refresh", 2, ColorPalette.SECONDARY);
         this.refreshButton.setClickAction(() -> AccountManagerPage.this.refreshAccountToken(this));
         this.removeButton = new BasicButton(120, 28, "Remove", 2, ColorPalette.SECONDARY);
         this.removeButton.setClickAction(() -> AccountManagerPage.this.removeAccount(this));
      }
   }
}
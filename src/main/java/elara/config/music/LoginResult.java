package elara.config.music;

public class LoginResult {
   private boolean success = false;
   private String message;
   private String nickname;
   private String avatarUrl;
   private String cookie;
   private LoginResult.LoginStatus status = LoginResult.LoginStatus.FAILED;

   public static LoginResult success(String nickname, String avatarUrl, String cookie) {
      LoginResult result = new LoginResult();
      result.success = true;
      result.message = "Login successful";
      result.nickname = nickname;
      result.avatarUrl = avatarUrl;
      result.cookie = cookie;
      result.status = LoginResult.LoginStatus.SUCCESS;
      return result;
   }

   public static LoginResult failed(String message) {
      LoginResult result = new LoginResult();
      result.success = false;
      result.message = message;
      result.status = LoginResult.LoginStatus.FAILED;
      return result;
   }

   public static LoginResult waiting() {
      LoginResult result = new LoginResult();
      result.success = false;
      result.message = LoginResult.LoginStatus.WAITING.getDescription();
      result.status = LoginResult.LoginStatus.WAITING;
      return result;
   }

   public static LoginResult scanned() {
      LoginResult result = new LoginResult();
      result.success = false;
      result.message = LoginResult.LoginStatus.SCANNED.getDescription();
      result.status = LoginResult.LoginStatus.SCANNED;
      return result;
   }

   public static LoginResult timeout() {
      LoginResult result = new LoginResult();
      result.success = false;
      result.message = LoginResult.LoginStatus.TIMEOUT.getDescription();
      result.status = LoginResult.LoginStatus.TIMEOUT;
      return result;
   }

   public static LoginResult expired() {
      LoginResult result = new LoginResult();
      result.success = false;
      result.message = LoginResult.LoginStatus.EXPIRED.getDescription();
      result.status = LoginResult.LoginStatus.EXPIRED;
      return result;
   }

   public boolean isSuccess() {
      return this.success;
   }

   public void setSuccess(boolean success) {
      this.success = success;
   }

   public String getMessage() {
      return this.message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public String getNickname() {
      return this.nickname;
   }

   public void setNickname(String nickname) {
      this.nickname = nickname;
   }

   public String getAvatarUrl() {
      return this.avatarUrl;
   }

   public void setAvatarUrl(String avatarUrl) {
      this.avatarUrl = avatarUrl;
   }

   public String getCookie() {
      return this.cookie;
   }

   public void setCookie(String cookie) {
      this.cookie = cookie;
   }

   public LoginResult.LoginStatus getStatus() {
      return this.status;
   }

   public void setStatus(LoginResult.LoginStatus status) {
      this.status = status;
   }

   @Override
   public String toString() {
      return "LoginResult{success="
         + this.success
         + ", message='"
         + this.message
         + '\''
         + ", nickname='"
         + this.nickname
         + '\''
         + ", status="
         + this.status
         + '}';
   }

   public enum LoginStatus {
      WAITING("Waiting for scan"),
      SCANNED("Scanned, please confirm"),
      SUCCESS("Login successful"),
      FAILED("Login failed"),
      TIMEOUT("Login timeout"),
      EXPIRED("QR code expired");

      private final String description;

      LoginStatus(String description) {
         this.description = description;
      }

      public String getDescription() {
         return this.description;
      }
   }
}

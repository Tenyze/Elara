package elara.config;

public enum AnimationMode {
   VANILLA,
   EXHIBITION,
   ETB,
   SIGMA,
   DORTWARE,
   PLAIN,
   SPIN,
   AVATAR,
   SWONG,
   SWANG,
   SWANK,
   STYLES,
   NUDGE,
   PUNCH,
   JIGSAW,
   SLIDE,
   SWING,
   OLD,
   PUSH,
   DASH,
   SLASH,
   SCALE,
   SWONK,
   STELLA,
   SMALL,
   EDIT,
   RHYS,
   STAB,
   FLOAT,
   REMIX,
   XIV,
   WINTER,
   YAMATO,
   SLIDE_SWING,
   SMALL_PUSH,
   REVERSE,
   INVENT,
   LEAKED,
   AQUA,
   ASTRO,
   FADEAWAY,
   ASTOLFO,
   ASTOLFO_SPIN,
   MOON,
   MOON_PUSH,
   SMOOTH,
   TAP1,
   TAP2,
   SIGMA3,
   SIGMA4,
   elara_1_8,
   elara_SLIDE,
   elara_SWANK,
   elara_SWANG,
   elara_AVATAR,
   elara_JIGSAW;

   public static AnimationMode fromJsonValue(String value) {
      try {
         return valueOf(value.toUpperCase());
      } catch (NullPointerException | IllegalArgumentException var2) {
         return VANILLA;
      }
   }
}

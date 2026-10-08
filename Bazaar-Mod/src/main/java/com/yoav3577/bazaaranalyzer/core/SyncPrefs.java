package com.yoav3577.bazaaranalyzer.core;

/** Cloud sync settings (config/bazaaranalyzer/sync.json): on/off, the GitHub token and the gist the data lives in. */
public record SyncPrefs(boolean enabled, String token, String gistId) {
   public static final SyncPrefs DEFAULT = new SyncPrefs(true, null, null);

   public SyncPrefs {
      token = blank(token) ? null : token.strip();
      gistId = blank(gistId) ? null : gistId.strip();
   }

   /** On, with a token. */
   public boolean on() {
      return this.enabled && this.token != null;
   }

   public SyncPrefs withEnabled(boolean v) {
      return new SyncPrefs(v, this.token, this.gistId);
   }

   /** A new token can belong to another GitHub account, so the gist is looked up again. */
   public SyncPrefs withToken(String t) {
      return new SyncPrefs(this.enabled, t, null);
   }

   public SyncPrefs withGist(String id) {
      return new SyncPrefs(this.enabled, this.token, id);
   }

   /** A classic (ghp_) or fine-grained (github_pat_) GitHub token, pasted with or without spaces around it. */
   public static boolean looksLikeToken(String s) {
      return s != null && s.strip().matches("(ghp|gho|ghu|github_pat)_[A-Za-z0-9_]{20,}");
   }

   public static SyncPrefs fromJson(String json) {
      var o = Cfg.object(json);
      return new SyncPrefs(Cfg.bool(o, "enabled", DEFAULT.enabled), Cfg.str(o, "token", null), Cfg.str(o, "gistId", null));
   }

   public String toJson() {
      return "{\"enabled\":" + this.enabled + ",\"token\":" + Json.str(this.token) + ",\"gistId\":" + Json.str(this.gistId) + "}\n";
   }

   private static boolean blank(String s) {
      return s == null || s.isBlank();
   }
}

package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** The few GitHub gist calls cloud sync needs (https://docs.github.com/rest/gists), with the user's token. */
public final class GistClient {
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
   private final String api;
   private final String token;
   private final String userAgent;

   public GistClient(String api, String token, String userAgent) {
      this.api = api;
      this.token = token;
      this.userAgent = userAgent;
   }

   /** The gist's files; {@code files} is null when it did not change since {@code etag}. */
   public record Snapshot(Map<String, String> files, String etag) {
   }

   /** GitHub answered with an error status (401 bad token, 404 no such gist or no gist access, ...). */
   public static final class Failure extends IOException {
      public final int status;

      Failure(int status, String what) {
         super("HTTP " + status + " for " + what);
         this.status = status;
      }
   }

   /** The id of this account's gist with this description, made (secret, with one readme file) when there is none. */
   public String findOrCreate(String description, String readme, String readmeText) throws IOException, InterruptedException {
      for (int page = 1; page <= 10; page++) {
         JsonArray gists = JsonParser.parseString(this.send("GET", "/gists?per_page=100&page=" + page, null, null).body()).getAsJsonArray();
         for (JsonElement e : gists) {
            JsonObject g = e.getAsJsonObject();
            if (description.equals(str(g, "description"))) {
               return str(g, "id");
            }
         }

         if (gists.size() < 100) {
            break;
         }
      }

      JsonObject body = new JsonObject();
      body.addProperty("description", description);
      body.addProperty("public", false);
      body.add("files", files(Map.of(readme, readmeText)));
      return str(JsonParser.parseString(this.send("POST", "/gists", body.toString(), null).body()).getAsJsonObject(), "id");
   }

   /** Every file of the gist (a file the API cut short is read from its raw url). */
   public Snapshot get(String id, String etag) throws IOException, InterruptedException {
      HttpResponse<String> r = this.send("GET", "/gists/" + id, null, etag);
      if (r.statusCode() == 304) {
         return new Snapshot(null, etag);
      }

      Map<String, String> out = new LinkedHashMap<>();
      JsonObject files = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("files");
      for (Map.Entry<String, JsonElement> e : files.entrySet()) {
         JsonObject f = e.getValue().getAsJsonObject();
         String content = str(f, "content");
         boolean truncated = f.has("truncated") && f.get("truncated").getAsBoolean();
         if (content == null || truncated) {
            content = this.raw(str(f, "raw_url"));
         }

         out.put(e.getKey(), content);
      }

      return new Snapshot(out, r.headers().firstValue("ETag").orElse(null));
   }

   /** Writes these files (added or replaced, the others stay); returns the gist's new ETag. */
   public String patch(String id, Map<String, String> contents) throws IOException, InterruptedException {
      JsonObject body = new JsonObject();
      body.add("files", files(contents));
      return this.send("PATCH", "/gists/" + id, body.toString(), null).headers().firstValue("ETag").orElse(null);
   }

   private String raw(String url) throws IOException, InterruptedException {
      if (url == null) {
         throw new IOException("gist file without content or raw_url");
      }

      HttpResponse<String> r = HTTP.send(this.request(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (r.statusCode() != 200) {
         throw new Failure(r.statusCode(), "a gist file");
      }

      return r.body();
   }

   private HttpResponse<String> send(String method, String path, String body, String etag) throws IOException, InterruptedException {
      HttpRequest.Builder b = this.request(URI.create(this.api + path))
         .header("Accept", "application/vnd.github+json")
         .header("X-GitHub-Api-Version", "2022-11-28")
         .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      if (body != null) {
         b.header("Content-Type", "application/json; charset=utf-8");
      }

      if (etag != null) {
         b.header("If-None-Match", etag);
      }

      HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
      int s = r.statusCode();
      if (s != 200 && s != 201 && s != 304) {
         throw new Failure(s, method + " " + path.replaceAll("\\?.*", ""));
      }

      return r;
   }

   private HttpRequest.Builder request(URI uri) {
      return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + this.token).header("User-Agent", this.userAgent);
   }

   private static JsonObject files(Map<String, String> contents) {
      JsonObject files = new JsonObject();
      for (Map.Entry<String, String> e : contents.entrySet()) {
         JsonObject f = new JsonObject();
         f.addProperty("content", e.getValue());
         files.add(e.getKey(), f);
      }

      return files;
   }

   private static String str(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsString() : null;
   }
}

package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** GistClient against a small fake of the GitHub gist API. */
class GistClientTest {
   private HttpServer server;
   private String base;
   private final Map<String, String> files = new LinkedHashMap<>();
   private final List<String> calls = new ArrayList<>();
   private int version;
   private boolean exists;

   @BeforeEach
   void start() throws IOException {
      this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      this.server.createContext("/", this::handle);
      this.server.start();
      this.base = "http://127.0.0.1:" + this.server.getAddress().getPort();
   }

   @AfterEach
   void stop() {
      this.server.stop(0);
   }

   private void handle(HttpExchange ex) throws IOException {
      String method = ex.getRequestMethod();
      String path = ex.getRequestURI().getPath();
      this.calls.add(method + " " + path);
      if (!"Bearer tok".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
         this.reply(ex, 401, "{}", null);
         return;
      }

      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String etag = "\"v" + this.version + "\"";
      if (method.equals("GET") && path.equals("/gists")) {
         this.reply(ex, 200, this.exists ? "[{\"id\":\"other\",\"description\":\"x\"},{\"id\":\"g1\",\"description\":\"sync\"}]" : "[]", null);
      } else if (method.equals("POST") && path.equals("/gists")) {
         JsonObject o = JsonParser.parseString(body).getAsJsonObject();
         assertEquals(false, o.get("public").getAsBoolean());
         this.exists = true;
         this.putFiles(o);
         this.reply(ex, 201, "{\"id\":\"g1\"}", null);
      } else if (method.equals("GET") && path.equals("/gists/g1")) {
         if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
            this.reply(ex, 304, null, etag);
            return;
         }

         JsonObject out = new JsonObject();
         JsonObject fs = new JsonObject();
         this.files.forEach((name, text) -> {
            JsonObject f = new JsonObject();
            boolean big = text.length() > 20;
            f.addProperty("content", big ? text.substring(0, 20) : text);
            f.addProperty("truncated", big);
            f.addProperty("raw_url", this.base + "/raw/" + name);
            fs.add(name, f);
         });
         out.add("files", fs);
         this.reply(ex, 200, out.toString(), etag);
      } else if (method.equals("GET") && path.startsWith("/raw/")) {
         this.reply(ex, 200, this.files.get(path.substring(5)), null);
      } else if (method.equals("PATCH") && path.equals("/gists/g1")) {
         this.putFiles(JsonParser.parseString(body).getAsJsonObject());
         this.reply(ex, 200, "{}", "\"v" + this.version + "\"");
      } else {
         this.reply(ex, 404, "{}", null);
      }
   }

   private void putFiles(JsonObject o) {
      o.getAsJsonObject("files").entrySet().forEach(e -> this.files.put(e.getKey(), e.getValue().getAsJsonObject().get("content").getAsString()));
      this.version++;
   }

   private void reply(HttpExchange ex, int status, String body, String etag) throws IOException {
      if (etag != null) {
         ex.getResponseHeaders().set("ETag", etag);
      }

      byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(status, status == 304 ? -1 : b.length);
      if (status != 304) {
         ex.getResponseBody().write(b);
      }

      ex.close();
   }

   @Test
   void makesTheGistOnceThenFindsIt() throws Exception {
      GistClient gh = new GistClient(this.base, "tok", "test");
      assertEquals("g1", gh.findOrCreate("sync", "readme.md", "hi"));
      assertEquals("g1", gh.findOrCreate("sync", "readme.md", "hi"));
      assertEquals(List.of("GET /gists", "POST /gists", "GET /gists"), this.calls);
      assertEquals("hi", this.files.get("readme.md"));
   }

   @Test
   void readsWritesAndSkipsAnUnchangedGist() throws Exception {
      GistClient gh = new GistClient(this.base, "tok", "test");
      gh.findOrCreate("sync", "readme.md", "hi");
      String long1 = "{\"ts\":1}\n".repeat(10);
      String tag = gh.patch("g1", Map.of("gem-log-2026-10.jsonl", long1, "capture-2026-10.log", "5\tx\n"));
      GistClient.Snapshot s = gh.get("g1", null);
      // the long file came from its raw url
      assertEquals(long1, s.files().get("gem-log-2026-10.jsonl"));
      assertEquals("5\tx\n", s.files().get("capture-2026-10.log"));
      assertEquals(tag, s.etag());
      assertNull(gh.get("g1", s.etag()).files());
   }

   @Test
   void aRefusedTokenIsA401Failure() {
      GistClient gh = new GistClient(this.base, "bad", "test");
      GistClient.Failure f = assertThrows(GistClient.Failure.class, () -> gh.get("g1", null));
      assertEquals(401, f.status);
   }
}

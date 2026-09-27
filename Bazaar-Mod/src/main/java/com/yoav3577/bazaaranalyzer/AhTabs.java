package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.LinkSettings;
import com.yoav3577.bazaaranalyzer.core.ModPricer;
import com.yoav3577.bazaaranalyzer.core.ModSelector;
import com.yoav3577.bazaaranalyzer.core.Modifier;
import com.yoav3577.bazaaranalyzer.core.PriceBook;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.util.Util;

final class AhTabs {
   private static final int MAX_OFFERS = 20;
   private static final long TAB_GAP_MS = 350L;
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-tabs");
      t.setDaemon(true);
      return t;
   });
   private static final Map<Integer, List<ItemMods>> OFFERS = new LinkedHashMap<>();
   private static int nextOffer = 1;
   private static volatile long lastOpenAt;

   private AhTabs() {
   }

   private static MutableComponent prefix() {
      return Component.literal("[Yoav Addons] ").withStyle(ChatFormatting.GOLD);
   }

   static void say(Component message) {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(message);
         }
      });
   }

   static void say(String text, ChatFormatting color) {
      say(prefix().append(Component.literal(text).withStyle(color)));
   }

   private static synchronized int remember(List<ItemMods> items) {
      int id = nextOffer++;
      OFFERS.put(id, List.copyOf(items));

      while (OFFERS.size() > MAX_OFFERS) {
         OFFERS.remove(OFFERS.keySet().iterator().next());
      }

      return id;
   }

   static synchronized void openOffer(int id) {
      List<ItemMods> items = OFFERS.get(id);
      if (items == null) {
         say("That list is too old to open again.", ChatFormatting.RED);
      } else {
         open(items);
      }
   }

   static void offerAfterCancel(String partner, List<ItemMods> items) {
      if (!items.isEmpty() && ModSettings.get().cancelLink()) {
         int id = remember(items);
         StringBuilder names = new StringBuilder();

         for (ItemMods m : items) {
            names.append(names.length() == 0 ? "" : "\n").append(m.name());
         }

         Component link = Component.literal("[Open on website]")
            .withStyle(
               style -> style.withColor(ChatFormatting.GREEN)
                  .withBold(true)
                  .withUnderlined(true)
                  .withClickEvent(new RunCommand("bazaaranalyzer open " + id))
                  .withHoverEvent(new ShowText(Component.literal(names.toString())))
            );
         say(
            prefix()
               .append(
                  Component.literal(
                        "Trade with " + partner + " cancelled. It had " + items.size() + " Auction House item" + (items.size() == 1 ? "" : "s") + ". "
                     )
                     .withStyle(ChatFormatting.GRAY)
               )
               .append(link)
         );
      }
   }

   static void open(List<ItemMods> items) {
      if (!items.isEmpty()) {
         long now = System.currentTimeMillis();
         if (now - lastOpenAt >= 1500L) {
            lastOpenAt = now;
            List<ItemMods> copy = List.copyOf(items);
            EXEC.execute(
               () -> {
                  try {
                     run(copy);
                  } catch (Throwable var2) {
                     BazaarClient.LOG.warn("Could not open the website tabs", var2);
                     say(
                        "Could not open the tabs (" + (var2.getMessage() == null ? var2.getClass().getSimpleName() : var2.getMessage()) + ").",
                        ChatFormatting.RED
                     );
                  }
               }
            );
         }
      }
   }

   private static void run(List<ItemMods> items) throws InterruptedException {
      SiteFinder.Site site = SiteFinder.find();
      boolean launched = site == null && SiteFinder.launch();
      if (launched) {
         say("Starting the website...", ChatFormatting.GRAY);
         for (int i = 0; i < 30 && site == null; i++) {
            Thread.sleep(500L);
            site = SiteFinder.find();
         }
      }

      if (site == null) {
         int port = ModSettings.get().sitePort();
         say(
            launched
               ? "The website did not start in time. Try again."
               : "The website is not running" + (port == 0 ? "" : " on port " + port + " (set in the mod settings)")
                  + ". Start it once with \"Start Bazaar Analyzer.bat\"; after that the mod starts it for you.",
            ChatFormatting.RED
         );
      } else {
         try {
            PriceData.refreshBazaar();
         } catch (IOException var16) {
            say("Could not load Bazaar prices, so no modifiers are applied.", ChatFormatting.YELLOW);
         }

         PriceBook book = id -> {
            PriceData.Live lv = PriceData.live(id);
            return lv == null ? Double.NaN : lv.instaBuy();
         };
         LinkSettings settings = AhSettings.get();
         List<String> urls = new ArrayList<>();

         for (ItemMods item : items) {
            String tag = AhLink.tag(item);
            if (tag == null) {
               tag = tagByName(item);
            }

            if (tag == null) {
               say("No Auction House match found for " + item.name() + ".", ChatFormatting.YELLOW);
            } else {
               List<Modifier> all = ModSelector.keepKnownEnchants(ModPricer.price(item, book, id -> PriceData.live(id) != null), PriceData.knownEnchants());
               double base = PriceData.lowestBin(tag);
               List<Modifier> chosen = ModSelector.select(all, base, settings);
               String url = AhLink.url(site.base(), tag, chosen);
               urls.add(url);
               debug(item, tag, base, all, chosen, url);
               StringBuilder applied = new StringBuilder();

               for (Modifier m : chosen) {
                  applied.append(applied.length() == 0 ? "" : ", ").append(m.label());
               }

               say(
                  prefix()
                     .append(Component.literal(item.name()).withStyle(ChatFormatting.WHITE))
                     .append(Component.literal(applied.length() == 0 ? " - no modifiers worth applying" : " - " + applied).withStyle(ChatFormatting.GRAY))
               );
            }
         }

         for (int i = 0; i < urls.size(); i++) {
            if (System.getProperty("bazaaranalyzer.dev.noBrowser") != null) {
               BazaarClient.LOG.info("Would open tab: {}", urls.get(i));
            } else {
               Util.getPlatform().openUri(URI.create(urls.get(i)));
            }

            if (i + 1 < urls.size()) {
               Thread.sleep(TAB_GAP_MS);
            }
         }

         if (!urls.isEmpty() && site.version() < SiteFinder.FILTER_VERSION) {
            say("Your website copy is older than v11, so the filters are not applied. Update the website folder.", ChatFormatting.YELLOW);
         }
      }
   }

   private static String tagByName(ItemMods item) {
      try {
         List<PriceData.Choice> hits = PriceData.searchAh(PriceData.searchName(item.name()));
         return hits.isEmpty() ? null : hits.get(0).id();
      } catch (IOException var2) {
         return null;
      }
   }

   static void debug(ItemMods item, String tag, double base, List<Modifier> all, List<Modifier> chosen, String url) {
      StringBuilder sb = new StringBuilder("=== ")
         .append(Instant.now())
         .append("  ")
         .append(item.name())
         .append(" | tag ")
         .append(tag)
         .append(" | lowest BIN ")
         .append(base)
         .append("\n  item: ")
         .append(item)
         .append("\n  worth: ");

      for (Modifier m : all) {
         sb.append(m.label()).append('=').append(Double.isNaN(m.value()) ? "?" : String.valueOf((long)m.value())).append("; ");
      }

      sb.append("\n  applied: ");

      for (Modifier m : chosen) {
         sb.append(m.label()).append("; ");
      }

      sb.append("\n  ").append(url).append('\n');
      append(sb.toString());
   }

   static void append(String text) {
      Path p = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer").resolve("ah-link-debug.log");
      ModIo.submit(() -> {
         try {
            Files.createDirectories(p.getParent());
            if (Files.exists(p) && Files.size(p) > 1000000L) {
               Files.delete(p);
            }

            Files.writeString(p, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException var3) {
            BazaarClient.LOG.warn("Could not write the AH link log", var3);
         }
      });
   }
}

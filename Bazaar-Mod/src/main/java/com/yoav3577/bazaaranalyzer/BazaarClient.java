package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.TradeBook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BazaarClient implements ClientModInitializer {
   public static final String MOD_ID = "bazaaranalyzer";
   public static final Logger LOG = LoggerFactory.getLogger("bazaaranalyzer");
   public static final String VERSION = FabricLoader.getInstance()
      .getModContainer("bazaaranalyzer")
      .map(c -> c.getMetadata().getVersion().getFriendlyString())
      .orElse("dev");
   public static final TradeBook BOOK = new TradeBook(50000);

   public void onInitializeClient() {
      try {
         Capture.init();
      } catch (Throwable var7) {
         LOG.error("Capture mode could not start; the mod keeps running without it", var7);
      }

      try {
         TradeTracker.init();
      } catch (Throwable var6) {
         LOG.error("Trade tracking could not start; the mod keeps running without it", var6);
      }

      try {
         GemWatch.init();
      } catch (Throwable e) {
         LOG.error("Gem removal watch could not start; the lowball tracker only guesses gem sales", e);
      }

      try {
         GraphKey.init();
      } catch (Throwable var5) {
         LOG.error("The price graph key could not be registered; the mod keeps running without it", var5);
      }

      try {
         TradeAhUi.init();
         PriceDebug.init();
         AhCommands.init();
      } catch (Throwable var4) {
         LOG.error("The trade website button could not start; the mod keeps running without it", var4);
      }

      try {
         DurationFill.init();
      } catch (Throwable e) {
         LOG.error("Auto fill time could not start; the mod keeps running without it", e);
      }

      try {
         ItemIcons.init();
      } catch (Throwable var3) {
         LOG.error("Item icons could not start; the graph shows no icons", var3);
      }

      try {
         LocalServer.start();
      } catch (Throwable var2) {
         LOG.error("Local server could not start; the mod keeps running without the site link", var2);
      }

      LOG.info("Yoav Addons {} loaded", VERSION);
   }
}

package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.api.YoavAddonsPlugin;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/** Add-on mods (the "yoavaddons" entrypoint, api.YoavAddonsPlugin) and the api.YoavAddons calls. */
public final class Addons {
   private static List<YoavAddonsPlugin> plugins;

   private Addons() {
   }

   static synchronized List<YoavAddonsPlugin> plugins() {
      if (plugins == null) {
         plugins = new ArrayList<>();
         try {
            plugins.addAll(FabricLoader.getInstance().getEntrypoints("yoavaddons", YoavAddonsPlugin.class));
         } catch (RuntimeException | LinkageError e) {
            BazaarClient.LOG.warn("Could not load the add-on mods' settings", e);
         }
      }

      return plugins;
   }

   public static Screen settingsScreen(Screen parent, String pageId) {
      if (pageId != null) {
         SettingsScreen.wantPage(pageId);
      }

      return new SettingsScreen(parent);
   }

   public static void openSettings(String pageId) {
      if (pageId != null) {
         SettingsScreen.wantPage(pageId);
      }

      AhCommands.openSettingsSoon();
   }
}

package com.yoav3577.bazaaranalyzer.api;

import com.yoav3577.bazaaranalyzer.Addons;
import net.minecraft.client.gui.screens.Screen;

/** For add-on mods: open the Yoav Addons settings, optionally on one of their pages. */
public final class YoavAddons {
   private YoavAddons() {
   }

   /** The settings screen showing this page (an id passed to Settings#page, or null for the last page shown). */
   public static Screen settingsScreen(Screen parent, String pageId) {
      return Addons.settingsScreen(parent, pageId);
   }

   /** Opens the settings on this page on the next tick (safe to call from a command). */
   public static void openSettings(String pageId) {
      Addons.openSettings(pageId);
   }
}

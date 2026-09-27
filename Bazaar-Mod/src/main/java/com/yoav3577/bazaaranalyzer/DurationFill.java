package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;

/**
 * Types the "Auto fill time" number into the sign Hypixel opens from the AH "Auction Duration" menu's Custom Duration
 * button. A sign counts only when it opens within SIGN_MS of that menu closing with no other menu in between.
 */
final class DurationFill {
   private static final String MENU = "Auction Duration";
   private static final long SIGN_MS = 2000L;
   private static long menuClosedAt;

   private DurationFill() {
   }

   static void init() {
      ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
         if (screen instanceof AbstractContainerScreen<?> cs) {
            menuClosedAt = 0L; // another menu opened (e.g. a preset was picked), so the next sign is not the duration one
            if (cs.getTitle().getString().contains(MENU)) {
               ScreenEvents.remove(screen).register(s -> menuClosedAt = System.currentTimeMillis());
            }
         } else if (screen instanceof AbstractSignEditScreen && System.currentTimeMillis() - menuClosedAt < SIGN_MS) {
            menuClosedAt = 0L; // once: a resize re-inits the sign screen
            ModPrefs p = ModSettings.get();
            if (p.fillTime()) {
               String.valueOf(p.fillHours()).chars().forEach(c -> screen.charTyped(new CharacterEvent(c)));
            }
         }
      });
   }
}

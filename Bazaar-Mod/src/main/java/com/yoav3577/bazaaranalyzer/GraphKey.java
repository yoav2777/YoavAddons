package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import java.io.IOException;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class GraphKey {
   private static KeyMapping key;
   private static KeyMapping settingsKey;
   private static Field hoveredSlot;

   private GraphKey() {
   }

   static void init() {
      Category category = Category.register(Identifier.fromNamespaceAndPath("bazaaranalyzer", "main"));
      key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bazaaranalyzer.graph", GLFW.GLFW_KEY_I, category));
      // Unbound by default: the user's modpack already uses most keys. Bind it under Controls > Bazaar Analyzer.
      settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bazaaranalyzer.settings", GLFW.GLFW_KEY_UNKNOWN, category));

      try {
         hoveredSlot = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
         hoveredSlot.setAccessible(true);
      } catch (RuntimeException | ReflectiveOperationException var2) {
         BazaarClient.LOG.warn("Hover-to-graph is unavailable (could not reach the hovered slot)", var2);
      }

      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         while (key.consumeClick()) {
            if (mc.screen == null) {
               PriceScreen.open(mc, PriceScreen.remembered(), null, null);
            }
         }

         while (settingsKey.consumeClick()) {
            if (mc.screen == null) {
               mc.setScreen(new SettingsScreen(null));
            }
         }
      });
      if (DevHarness.active()) {
         DevHarness.init();
      }

      PriceData.async(() -> {
         try {
            PriceData.refreshBazaar();
         } catch (IOException var1) {
         }
      });
      ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
         if (screen instanceof AbstractContainerScreen) {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !key.matches(event) || !openForHovered(mc, s));
         }
      });
   }

   static void setHoveredForDev(Screen screen, Slot slot) throws ReflectiveOperationException {
      hoveredSlot.set(screen, slot);
   }

   static String keyName() {
      return key == null ? "the graph key" : key.getTranslatedKeyMessage().getString();
   }

   static boolean openForHovered(Minecraft mc, Screen screen) {
      try {
         if (hoveredSlot != null && screen instanceof AbstractContainerScreen) {
            Slot slot = (Slot)hoveredSlot.get(screen);
            if (slot != null && slot.hasItem()) {
               ItemStack stack = slot.getItem();
               TradeWindow.RawStack item = ItemReader.read(stack);
               if (item == null) {
                  return false;
               } else {
                  String id = item.id();
                  String query = PriceData.searchName(item.name());
                  if (id != null && PriceData.products().contains(id)) {
                     PriceScreen.open(mc, new PriceData.Choice(PriceData.Kind.BAZAAR, id, PriceData.pretty(id)), null, stack);
                  } else if (id != null && !PriceData.GENERIC_IDS.contains(id)) {
                     PriceScreen.open(mc, new PriceData.Choice(PriceData.Kind.AH, id, item.name()), query, stack);
                  } else {
                     PriceScreen.open(mc, null, query, stack);
                  }

                  return true;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (RuntimeException | ReflectiveOperationException var7) {
         BazaarClient.LOG.warn("Could not open the graph for the hovered item", var7);
         return false;
      }
   }
}

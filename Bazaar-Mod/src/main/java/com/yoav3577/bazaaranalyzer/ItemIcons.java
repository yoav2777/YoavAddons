package com.yoav3577.bazaaranalyzer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

final class ItemIcons {
   private static final int MAX_SEEN = 3000;
   private static final Map<String, ItemStack> SEEN = new ConcurrentHashMap<>();
   private static Method repoGet;
   private static Method flexStack;
   private static boolean skyblockerChecked;
   private static boolean skyblockerUsable;
   private static int ticks;
   private static boolean scanFailureLogged;

   private ItemIcons() {
   }

   static void init() {
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         if (++ticks % 20 == 0) {
            try {
               scan(mc);
            } catch (RuntimeException var2) {
               if (!scanFailureLogged) {
                  scanFailureLogged = true;
                  BazaarClient.LOG.info("Item icon scan failed ({}); continuing without it", var2.getMessage());
               }
            }
         }
      });
   }

   private static void scan(Minecraft mc) {
      if (mc.screen instanceof AbstractContainerScreen<?> cs) {
         for (Slot s : cs.getMenu().slots) {
            remember(s.getItem());
         }
      } else if (mc.player != null) {
         Inventory inv = mc.player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            remember(inv.getItem(i));
         }
      }
   }

   private static void remember(ItemStack stack) {
      String id = ItemReader.skyblockId(stack);
      if (id != null && !PriceData.GENERIC_IDS.contains(id) && (SEEN.size() < MAX_SEEN || SEEN.containsKey(id))) {
         SEEN.putIfAbsent(id, stack.copyWithCount(1));
      }
   }

   private static ItemStack withId(ItemStack stack, String id) {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", id);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      return stack;
   }

   static ItemStack forId(String id) {
      if (id == null) {
         return null;
      } else {
         if (id.equals(System.getProperty("bazaaranalyzer.dev.icon"))) {
            SEEN.computeIfAbsent(id, k -> withId(new ItemStack(Items.DIAMOND), k));
         }

         List<String> names = candidates(id);

         for (String n : names) {
            ItemStack s = SEEN.get(n);
            if (s != null) {
               return s;
            }
         }

         for (String nx : names) {
            ItemStack s = fromSkyblocker(nx);
            if (s != null) {
               SEEN.put(id, s);
               return s;
            }
         }

         return null;
      }
   }

   static List<String> candidates(String id) {
      List<String> c = new ArrayList<>();
      c.add(id);
      if (id.startsWith("PET_") && !id.startsWith("PET_SKIN_")) {
         for (int rarity : new int[]{4, 3, 5, 2, 1, 0}) {
            c.add(id.substring(4) + ";" + rarity);
         }
      } else if (id.startsWith("RUNE_")) {
         c.add(id.substring(5) + "_RUNE;1");
      } else if (id.startsWith("ENCHANTMENT_")) {
         String rest = id.substring("ENCHANTMENT_".length());
         int u = rest.lastIndexOf('_');
         if (u > 0) {
            c.add(rest.substring(0, u) + ";" + rest.substring(u + 1));
         }
      }

      return c;
   }

   private static ItemStack fromSkyblocker(String neuId) {
      try {
         if (!skyblockerChecked) {
            skyblockerChecked = true;
            Class<?> repo = Class.forName("de.hysky.skyblocker.skyblock.itemlist.ItemRepository");
            repoGet = repo.getMethod("getItemStack", String.class);
            flexStack = Class.forName("de.hysky.skyblocker.utils.FlexibleItemStack").getMethod("getStackOrEmpty");
            skyblockerUsable = true;
         }

         if (!skyblockerUsable) {
            return null;
         } else {
            Object flex = repoGet.invoke(null, neuId);
            if (flex == null) {
               return null;
            } else {
               return flexStack.invoke(flex) instanceof ItemStack s && !s.isEmpty() ? s : null;
            }
         }
      } catch (NoSuchMethodException | ClassNotFoundException var4) {
         BazaarClient.LOG.info("Skyblocker's item repository is not available; item icons come from items you have seen");
         return null;
      } catch (RuntimeException | LinkageError | ReflectiveOperationException var5) {
         return null;
      }
   }
}

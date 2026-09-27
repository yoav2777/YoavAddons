package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yoav3577.bazaaranalyzer.core.Appraiser;
import com.yoav3577.bazaaranalyzer.core.Gems;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.Parts;
import com.yoav3577.bazaaranalyzer.core.TradeItem;
import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

final class ItemReader {
   private static final List<String> PET_TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
   private static final String[] COUNT_KEYS = new String[]{"art_of_war_count", "wood_singularity_count", "farming_for_dummies_count", "tuned_transmission"};

   private ItemReader() {
   }

   static String skyblockId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CustomData data = stack.get(DataComponents.CUSTOM_DATA);
         return data == null ? null : data.copyTag().getString("id").orElse(null);
      } else {
         return null;
      }
   }

   static String snbt(ItemStack stack) {
      CustomData data = stack != null && !stack.isEmpty() ? stack.get(DataComponents.CUSTOM_DATA) : null;
      return data == null ? "(no item data)" : data.copyTag().toString();
   }

   static ItemMods mods(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CustomData data = stack.get(DataComponents.CUSTOM_DATA);
         if (data == null) {
            return null;
         } else {
            CompoundTag tag = data.copyTag();
            String id = tag.getString("id").orElse(null);
            if (id == null) {
               return null;
            } else {
               Map<String, Integer> enchants = new LinkedHashMap<>();
               CompoundTag en = tag.getCompoundOrEmpty("enchantments");

               for (String k : en.keySet()) {
                  en.getInt(k).ifPresent(v -> enchants.put(k, v));
               }

               List<String> scrolls = new ArrayList<>();
               ListTag list = tag.getListOrEmpty("ability_scroll");

               for (int i = 0; i < list.size(); i++) {
                  list.getString(i).ifPresent(scrolls::add);
               }

               if (scrolls.isEmpty()) {
                  tag.getString("ability_scroll").ifPresent(s -> {
                     for (String p : s.split(" ")) {
                        if (!p.isBlank()) {
                           scrolls.add(p);
                        }
                     }
                  });
               }

               Map<String, Integer> counts = new LinkedHashMap<>();

               for (String key : COUNT_KEYS) {
                  tag.getInt(key).ifPresent(v -> counts.put(key, v));
               }

               String petType = null;
               String petTier = null;
               String petInfo = tag.getString("petInfo").orElse(null);
               if (petInfo != null) {
                  try {
                     JsonObject o = JsonParser.parseString(petInfo).getAsJsonObject();
                     if (o.has("type")) {
                        petType = o.get("type").getAsString();
                     }

                     if (o.has("tier")) {
                        // the rarity the pet shows (and Coflnet filters by): a Tier Boost held item raises it one step
                        petTier = o.get("tier").getAsString();
                        if (o.has("heldItem") && Appraiser.TIER_BOOST.equals(o.get("heldItem").getAsString())) {
                           int i = PET_TIERS.indexOf(petTier.toUpperCase(Locale.ROOT));
                           petTier = i >= 0 && i + 1 < PET_TIERS.size() ? PET_TIERS.get(i + 1) : petTier;
                        }
                     }
                  } catch (RuntimeException var13) {
                  }
               }

               String rune = tag.getCompoundOrEmpty("runes").keySet().stream().findFirst().orElse(null);
               return new ItemMods(
                  id,
                  stack.getHoverName().getString(),
                  enchants,
                  tag.getString("modifier").orElse(null),
                  tag.getInt("upgrade_level").orElseGet(() -> tag.getInt("dungeon_item_level").orElse(0)),
                  tag.getInt("rarity_upgrades").orElse(0) > 0,
                  tag.getInt("hot_potato_count").orElse(0),
                  flattenGems(tag.getCompoundOrEmpty("gems")),
                  scrolls,
                  tag.getString("power_ability_scroll").orElse(null),
                  counts,
                  petType,
                  petTier,
                  rune
               );
            }
         }
      } else {
         return null;
      }
   }

   static TradeWindow.RawStack read(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CompoundTag tag = null;
         CustomData data = stack.get(DataComponents.CUSTOM_DATA);
         if (data != null) {
            tag = data.copyTag();
         }

         String id = tag == null ? null : tag.getString("id").orElse(null);
         String uuid = tag == null ? null : tag.getString("uuid").orElse(null);
         ItemLore lore = stack.get(DataComponents.LORE);
         List<String> loreLines = lore == null ? List.of() : lore.lines().stream().<String>map(Component::getString).toList();
         return new TradeWindow.RawStack(
            stack.getHoverName().getString(),
            stack.getCount(),
            loreLines,
            id,
            uuid,
            tag == null ? List.of() : Gems.parse(flattenGems(tag.getCompoundOrEmpty("gems"))),
            tag == null ? List.of() : parts(tag)
         );
      } else {
         return null;
      }
   }

   /** The item's SkyBlock data (custom_data), or null. */
   static CompoundTag tag(ItemStack stack) {
      CustomData data = stack != null && !stack.isEmpty() ? stack.get(DataComponents.CUSTOM_DATA) : null;
      return data == null ? null : data.copyTag();
   }

   static List<TradeItem.Gem> gems(CompoundTag tag) {
      return Gems.parse(flattenGems(tag.getCompoundOrEmpty("gems")));
   }

   /** Market ids of the item's removable non-gem parts (Parts.KEYS): plain string keys, rod parts as {part:"..."}, pet item/skin in petInfo. */
   static List<String> parts(CompoundTag tag) {
      Map<String, String> flat = new HashMap<>();

      for (String k : Parts.KEYS) {
         if (!k.startsWith("petInfo.")) {
            tag.getString(k).ifPresent(v -> flat.put(k, v));
            tag.getCompound(k).flatMap(c -> c.getString("part")).ifPresent(v -> flat.put(k, v));
         }
      }

      tag.getString("petInfo").ifPresent(json -> {
         try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            for (String k : new String[]{"heldItem", "skin"}) {
               if (o.has(k) && o.get(k).isJsonPrimitive()) {
                  flat.put("petInfo." + k, o.get(k).getAsString());
               }
            }
         } catch (RuntimeException e) {
         }
      });
      return Parts.ids(flat);
   }

   private static Map<String, String> flattenGems(CompoundTag gems) {
      Map<String, String> flat = new HashMap<>();

      for (String key : gems.keySet()) {
         gems.getString(key).ifPresent(v -> flat.put(key, v));
         gems.getCompound(key).ifPresent(c -> {
            c.getString("quality").ifPresent(q -> flat.put(key, q));
            c.getString("gem").ifPresent(t -> flat.put(key + "_gem", t));
         });
      }

      return flat;
   }
}

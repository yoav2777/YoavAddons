package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/** An item's custom_data NBT as gson JSON (numbers stay numbers, lists become arrays), for the craft engine hook. */
final class NbtJson {
   private NbtJson() {
   }

   static JsonObject of(CompoundTag tag) {
      if (tag == null) {
         return new JsonObject();
      } else {
         JsonElement el = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
         return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
      }
   }
}

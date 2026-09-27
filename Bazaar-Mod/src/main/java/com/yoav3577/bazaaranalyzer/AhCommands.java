package com.yoav3577.bazaaranalyzer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client commands. Settings: /ba, /ya, /yoavaddons, /bazaaranalyzer, /bazaaranalyzer settings, /bazaaranalyzer open.
 * /bazaaranalyzer open &lt;n&gt; re-opens a cancelled trade's AH items on the website.
 */
final class AhCommands {
   static final String[] SETTINGS_ALIASES = {"ba", "ya", "yoavaddons"};
   /** Opened on the next tick: a command runs while chat is still open, and chat closing itself would close it again. */
   private static volatile boolean openSettings;

   private AhCommands() {
   }

   static void init() {
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         if (openSettings) {
            openSettings = false;
            mc.setScreen(new SettingsScreen(null));
         }
      });
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
         dispatcher.register(
            ClientCommands.literal("bazaaranalyzer")
               .executes(c -> settings())
               .then(ClientCommands.literal("settings").executes(c -> settings()))
               .then(ClientCommands.literal("open").executes(c -> settings()).then(ClientCommands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                  AhTabs.openOffer(IntegerArgumentType.getInteger(c, "id"));
                  return 1;
               })))
         );
         for (String alias : SETTINGS_ALIASES) {
            dispatcher.register(ClientCommands.literal(alias).executes(c -> settings()).then(ClientCommands.literal("settings").executes(c -> settings())));
         }
      });
   }

   private static int settings() {
      openSettings = true;
      return 1;
   }

   /** Dev harness and anything else that wants the settings open on the next tick. */
   static void openSettingsSoon() {
      openSettings = true;
   }
}

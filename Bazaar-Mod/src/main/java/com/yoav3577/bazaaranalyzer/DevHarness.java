package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class DevHarness {
   private static final String P = "bazaaranalyzer.dev.";

   private DevHarness() {
   }

   static boolean active() {
      for (String k : System.getProperties().stringPropertyNames()) {
         if (k.startsWith(P)) {
            return true;
         }
      }

      return false;
   }

   static void init() {
      boolean world = System.getProperty(P + "world") != null;
      String bazaar = System.getProperty(P + "openGraph");
      String ah = System.getProperty(P + "openAh");
      String hover = System.getProperty(P + "hover");
      String gestures = System.getProperty(P + "gestures");
      String trade = System.getProperty(P + "trade");
      int[] title = new int[]{0};
      int[] play = new int[]{0};
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         try {
            if (mc.screen instanceof TitleScreen) {
               title[0]++;
               if (world && title[0] == 100) {
                  createWorld(mc);
               } else if (!world && title[0] == 120) {
                  openGraph(mc, bazaar, ah);
               }
            }

            if (world && mc.level != null && mc.player != null) {
               int t = ++play[0];
               if (System.getProperty(P + "settings") != null) {
                  if (t == 200 && mc.player != null) {
                     mc.player.connection.sendUnattendedCommand("bazaaranalyzer settings", null);
                  } else if (System.getProperty(P + "settingsClick") != null) {
                     settingsClick(mc, t);
                  }
               } else if (System.getProperty(P + "modmenu") != null) {
                  if (t == 200) {
                     // Mod Menu is only on the dev runtime classpath, so go through reflection.
                     Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
                     Object has = mm.getMethod("hasConfigScreen", String.class).invoke(null, BazaarClient.MOD_ID);
                     Object screen = mm.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class).invoke(null, BazaarClient.MOD_ID, null);
                     BazaarClient.LOG.info("Dev: Mod Menu hasConfigScreen={} screen={}", has, screen == null ? null : screen.getClass().getName());
                     mc.setScreen((net.minecraft.client.gui.screens.Screen) screen);
                  } else if (t == 240) {
                     shot(mc, "modmenu-config");
                  }
               } else if (System.getProperty(P + "moul") != null) {
                  moul(mc, t);
               } else if (System.getProperty(P + "placeHuman") != null) {
                  placeHuman(mc, t);
               } else if (System.getProperty(P + "placeDrag") != null) {
                  placeDrag(mc, t);
               } else if (System.getProperty(P + "place") != null) {
                  place(mc, t);
               } else if (trade != null) {
                  trade(mc, trade, t);
               } else if (hover != null) {
                  hover(mc, hover, t);
               } else if (t == 200) {
                  String r = System.getProperty(P + "range");
                  if (r != null) {
                     GraphSettings.saveRange(r);
                  }

                  openGraph(mc, bazaar, ah);
               } else if (t == 300 && System.getProperty(P + "shots") != null) {
                  shot(mc, "graph-" + GraphSettings.get().range());
               } else if (System.getProperty(P + "gear") != null && t > 300) {
                  gear(mc, t);
               } else if (t == 320 && gestures != null && mc.screen instanceof PriceScreen ps) {
                  String[] g = gestures.split(",");
                  ps.devGestures(Double.parseDouble(g[0]), Integer.parseInt(g[1]), Integer.parseInt(g[2]));
               }
            }
         } catch (ReflectiveOperationException | RuntimeException var12) {
            BazaarClient.LOG.warn("Developer harness step failed", var12);
         }
      });
   }

   private static ItemStack skyblock(Item base, String name, CompoundTag tag) {
      ItemStack s = new ItemStack(base);
      s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
      return s;
   }

   private static CompoundTag tagWithId(String id) {
      CompoundTag t = new CompoundTag();
      t.putString("id", id);
      return t;
   }

   private static ItemStack fakeHyperion() {
      CompoundTag t = tagWithId("HYPERION");
      t.putString("uuid", "f9b41bbe-7f9f-411a-95fd-18e82a7f350d");
      CompoundTag en = new CompoundTag();
      en.putInt("ultimate_one_for_all", 1);
      en.putInt("ultimate_wise", 5);
      en.putInt("sharpness", 7);
      t.put("enchantments", en);
      ListTag scrolls = new ListTag();
      scrolls.add(StringTag.valueOf("WITHER_SHIELD_SCROLL"));
      scrolls.add(StringTag.valueOf("IMPLOSION_SCROLL"));
      scrolls.add(StringTag.valueOf("SHADOW_WARP_SCROLL"));
      t.put("ability_scroll", scrolls);
      t.putInt("upgrade_level", 7);
      t.putInt("rarity_upgrades", 1);
      t.putString("modifier", "withered");
      CompoundTag gems = new CompoundTag();
      gems.putString("SAPPHIRE_0", "PERFECT");
      gems.putString("COMBAT_0", "PERFECT");
      gems.putString("COMBAT_0_gem", "SAPPHIRE");
      t.put("gems", gems);
      return skyblock(Items.DIAMOND_SWORD, "Withered Hyperion", t);
   }

   private static AbstractContainerScreen<?> fakeTrade(Minecraft mc) {
      Inventory inv = mc.player.getInventory();
      ChestMenu menu = ChestMenu.sixRows(99, inv);
      menu.getSlot(5).set(fakeHyperion());
      menu.getSlot(6).set(skyblock(Items.DIAMOND, "Enchanted Diamond", tagWithId("ENCHANTED_DIAMOND")));
      CompoundTag pet = tagWithId("PET");
      pet.putString("petInfo", "{\"type\":\"GHOUL\",\"tier\":\"EPIC\",\"exp\":0.0}");
      menu.getSlot(14).set(skyblock(Items.PLAYER_HEAD, "[Lvl 1] Ghoul", pet));
      CompoundTag rune = tagWithId("RUNE");
      CompoundTag runes = new CompoundTag();
      runes.putInt("ENDERSNAKE", 1);
      rune.put("runes", runes);
      menu.getSlot(15).set(skyblock(Items.PAPER, "Endersnake Rune I", rune));
      return new ContainerScreen(menu, inv, Component.literal("You" + " ".repeat(18) + "TestPartner"));
   }

   private static void trade(Minecraft mc, String mode, int t) throws ReflectiveOperationException {
      if (t == 150) {
         mc.setScreen(fakeTrade(mc));
      } else if (System.getProperty(P + "corners") != null && t >= 170 && t <= 290 && (t - 170) % 40 == 0
         && mc.screen instanceof AbstractContainerScreen<?> cs) {
         ModPrefs.Corner c = ModPrefs.Corner.values()[(t - 170) / 40];
         ModSettings.live(ModSettings.get().withPosition(c, 6, 6));
         int[] r = TradeAhUi.rect(mc.font, TradeTracker.theirAhCount(), cs.width, cs.height);
         BazaarClient.LOG.info("Dev: corner {} -> button x={} y={} w={} h={} (screen {}x{})", c, r[0], r[1], r[2], r[3], cs.width, cs.height);
      } else if (System.getProperty(P + "corners") != null && t >= 190 && t <= 310 && (t - 190) % 40 == 0) {
         shot(mc, "trade-corner-" + ModSettings.get().corner().name().toLowerCase());
      } else if (System.getProperty(P + "worth") != null && t >= 320) {
         worthShots(mc, t);
      } else if (t == 320 && mc.screen instanceof AbstractContainerScreen<?> cs) {
         if (mode.equals("button")) {
            BazaarClient.LOG.info("Dev: button click handled = {}", TradeAhUi.devClick(mc, cs));
         } else {
            ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal("You cancelled the trade!"), false);
            mc.setScreen(null);
         }
      } else if (t == 460 && mode.equals("cancel") && mc.player != null) {
         mc.player.connection.sendUnattendedCommand("bazaaranalyzer open 1", null);
      } else if (t == 640 && mode.equals("cancel")) {
         mc.setScreen(new ChatScreen("", false));
      }
   }

   /** Clicks every control of each settings page (sliders at 75%), then closes the screen and logs the saved files. */
   private static void settingsClick(Minecraft mc, int t) {
      SettingsScreen.Page[] pages = SettingsScreen.Page.values();
      if (t == 260 + pages.length * 200) {
         for (String f : List.of("graph.json", "mod-settings.json", "link-settings.json")) {
            BazaarClient.LOG.info("Dev: {} = {}", f, String.valueOf(ConfigFiles.read(f)).trim());
         }
      }

      if (!(mc.screen instanceof SettingsScreen ss)) {
         return;
      }

      if (t >= 240 && t < 240 + pages.length * 200) {
         int k = (t - 240) / 200;
         int phase = (t - 240) % 200;
         if (phase == 0) {
            ss.showPage(pages[k]);
         } else if (phase == 20) {
            BazaarClient.LOG.info("Dev: settings page {} shown: {}", pages[k], ss.devDump());
            shot(mc, "settings-" + pages[k].name().toLowerCase() + "-" + mc.getWindow().getGuiScale());
         } else if (phase == 100) {
            ss.devChangeAll();
         } else if (phase == 180) {
            BazaarClient.LOG.info("Dev: settings page {} after clicks: {}", pages[k], ss.devDump());
            shot(mc, "settings-" + pages[k].name().toLowerCase() + "-after-" + mc.getWindow().getGuiScale());
         }
      } else if (t == 240 + pages.length * 200) {
         ss.onClose();
      }
   }

   /**
    * dev.worth=1 (with dev.trade=button): no button click; waits for the worth panel (Coflnet), then screenshots it plain,
    * with the mouse on the worth box and on the craft "!" (trade-worth, trade-worth-hover, trade-craft-hover).
    */
   private static void worthShots(Minecraft mc, int t) throws ReflectiveOperationException {
      TradeWorth.View v = TradeWorth.view();
      if (t % 100 == 0) {
         BazaarClient.LOG.info("Dev: worth view at {} = {}", t, v);
      }

      int[] box = t < 900 ? null : t < 1000 ? TradeAhUi.devWorthBox : t < 1100 ? TradeAhUi.devBangBox : null;
      double s = mc.getWindow().getGuiScale();
      if (box != null) {
         setMouse(mc, (box[0] + box[2] / 2.0) * s, (box[1] + box[3] / 2.0) * s);
      } else if (t >= 1100 && mc.screen instanceof AbstractContainerScreen<?> cs) {
         // price debug tooltips: the Hyperion (slot 5), then the Ghoul pet (slot 14)
         int[] c = slotCenter(cs, t < 1420 ? 5 : 14);
         setMouse(mc, c[0] * s, c[1] * s);
      }

      if (t == 880) {
         shot(mc, "trade-worth");
      } else if (t == 980) {
         shot(mc, "trade-worth-hover");
      } else if (t == 1080) {
         shot(mc, "trade-craft-hover");
         ModSettings.live(ModSettings.get().withPriceDebug(true));
      } else if (t == 1400) {
         shot(mc, "debug-hover-hyperion");
      } else if (t == 1720) {
         shot(mc, "debug-hover-pet");
         BazaarClient.LOG.info("Dev: worth shots done");
      }
   }

   /** Screen (GUI) coordinates of the middle of a container slot (leftPos/topPos are protected). */
   private static int[] slotCenter(AbstractContainerScreen<?> cs, int slot) throws ReflectiveOperationException {
      int[] xy = new int[2];
      String[] names = {"leftPos", "topPos"};
      for (int i = 0; i < 2; i++) {
         java.lang.reflect.Field f = AbstractContainerScreen.class.getDeclaredField(names[i]);
         f.setAccessible(true);
         xy[i] = f.getInt(cs);
      }

      Slot sl = cs.getMenu().getSlot(slot);
      return new int[]{xy[0] + sl.x + 8, xy[1] + sl.y + 8};
   }

   /**
    * dev.moul=1: the MoulConfig-style screens. Settings opened with /ba (About), Price graph page, its Default range
    * dropdown open, Trade window page, a search for "port", then the graph and the button placer (moul-*.png).
    */
   private static void moul(Minecraft mc, int t) throws ReflectiveOperationException {
      if (t == 120) {
         mc.player.connection.sendCommand("ba");
      } else if (t == 160) {
         BazaarClient.LOG.info("Dev: /ba opened {}", mc.screen == null ? "nothing" : mc.screen.getClass().getSimpleName());
         shot(mc, "moul-about");
      } else if (mc.screen instanceof SettingsScreen ss) {
         double s = mc.getWindow().getGuiScale();
         if (t == 180) {
            ss.showPage(SettingsScreen.Page.GRAPH);
         } else if (t == 200) {
            BazaarClient.LOG.info("Dev: {}", ss.devDump());
            shot(mc, "moul-graph");
            double[] c = ss.devControl("Default range");
            if (c != null) {
               ss.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(0, 0)), false);
               setMouse(mc, c[0] * s, (c[1] + 20) * s);
            }
         } else if (t == 230) {
            shot(mc, "moul-dropdown");
            ss.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
         } else if (t == 250) {
            ss.showPage(SettingsScreen.Page.TRADE);
         } else if (t == 270) {
            shot(mc, "moul-trade");
            ss.devSearch("port");
         } else if (t == 310) {
            shot(mc, "moul-search");
            ss.onClose();
            PriceScreen.openFrom(mc, null);
         }
      } else if (t == 380 && mc.screen instanceof PriceScreen) {
         shot(mc, "moul-graph-screen");
         mc.setScreen(new PlaceButtonScreen(null));
      } else if (t == 420) {
         shot(mc, "moul-place");
         mc.setScreen(null);
         BazaarClient.LOG.info("Dev: moul shots done");
      }
   }

   /** Saves a screenshot of the game framebuffer to run/screenshots/<name>.png (works while the window is hidden). */
   static void shot(Minecraft mc, String name) {
      Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> BazaarClient.LOG.info("Dev: screenshot {} -> {}", name, msg.getString()));
   }

   /** Opens the drag-to-place screen and drags the button to near the bottom-right corner. */
   private static void place(Minecraft mc, int t) {
      if (t == 200) {
         mc.setScreen(new PlaceButtonScreen(null));
      } else if (t == 260 && mc.screen instanceof PlaceButtonScreen ps) {
         int[] r = TradeAhUi.rect(mc.font, 3, ps.width, ps.height);
         MouseButtonEvent down = new MouseButtonEvent(r[0] + 4, r[1] + 4, new MouseButtonInfo(0, 0));
         // dev.placeAt=x,y: where the button's top-left should land (default: near the bottom-right corner).
         String at = System.getProperty(P + "placeAt");
         int[] d = at == null ? new int[]{ps.width - 30 - 4, ps.height - 20 - 4} : new int[]{Integer.parseInt(at.split(",")[0]), Integer.parseInt(at.split(",")[1])};
         MouseButtonEvent to = new MouseButtonEvent(d[0] + 4, d[1] + 4, new MouseButtonInfo(0, 0));
         ps.mouseClicked(down, false);
         ps.mouseDragged(to, 0, 0);
         ps.mouseReleased(to);
         BazaarClient.LOG.info("Dev: placed -> {} lands at {}", ModSettings.get().toJson().trim(), java.util.Arrays.toString(TradeAhUi.rect(mc.font, 3, ps.width, ps.height)));
      } else if (t == 240 || t == 280) {
         shot(mc, t == 240 ? "place-before" : "place-after");
      } else if (t == 340 && mc.screen instanceof PlaceButtonScreen ps) {
         ps.onClose();
         BazaarClient.LOG.info("Dev: mod-settings.json = {}", String.valueOf(ConfigFiles.read("mod-settings.json")).trim());
      }
   }

   /** Puts the mouse at a raw window pixel position (MouseHandler.xpos/ypos are private). */
   private static void setMouse(Minecraft mc, double rawX, double rawY) throws ReflectiveOperationException {
      for (String f : new String[]{"xpos", "ypos"}) {
         java.lang.reflect.Field fld = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
         fld.setAccessible(true);
         fld.setDouble(mc.mouseHandler, f.equals("xpos") ? rawX : rawY);
      }
   }

   /** Presses (action 1) or releases (action 0) the left mouse button through MouseHandler's own GLFW callback. */
   private static void mouseButton(Minecraft mc, int action) throws ReflectiveOperationException {
      var m = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
      m.setAccessible(true);
      m.invoke(mc.mouseHandler, mc.getWindow().handle(), new MouseButtonInfo(0, 0), action);
   }

   /** Moves the mouse to a raw window pixel position the way GLFW does, then lets Minecraft dispatch the move/drag. */
   private static void mouseMove(Minecraft mc, double rawX, double rawY) throws ReflectiveOperationException {
      var m = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
      m.setAccessible(true);
      m.invoke(mc.mouseHandler, mc.getWindow().handle(), rawX, rawY);
      mc.mouseHandler.handleAccumulatedMovement();
   }

   /**
    * dev.placeDrag=x,y: like dev.place, but the drag goes through Minecraft's real mouse pipeline (MouseHandler press ->
    * move -> release), which is what the player's mouse does. x,y is where the button's top-left should land.
    */
   private static void placeDrag(Minecraft mc, int t) throws ReflectiveOperationException {
      if (t == 200) {
         mc.setScreen(new PlaceButtonScreen(null));
         return;
      }

      if (!(mc.screen instanceof PlaceButtonScreen ps) || t < 220 || t > 340) {
         return;
      }

      var win = mc.getWindow();
      double sx = (double) win.getWidth() / Math.max(1, win.getGuiScaledWidth());
      double sy = (double) win.getHeight() / Math.max(1, win.getGuiScaledHeight());
      int[] r = TradeAhUi.rect(mc.font, 3, ps.width, ps.height);
      String at = System.getProperty(P + "placeDrag");
      String[] parts = at == null ? new String[0] : at.split(",");
      int tx = parts.length == 2 ? Integer.parseInt(parts[0].trim()) : ps.width - r[2] - 30;
      int ty = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : ps.height - r[3] - 30;
      double startX = r[0] + 4.5;
      double startY = r[1] + 4.5;
      double endX = tx + 4.5;
      double endY = ty + 4.5;
      int steps = 6;
      // Minecraft only dispatches mouse moves while the window has focus; the dev client usually runs in the background.
      java.lang.reflect.Field focused = com.mojang.blaze3d.platform.Window.class.getDeclaredField("focused");
      focused.setAccessible(true);
      focused.setBoolean(win, true);
      if (t == 220) {
         setMouse(mc, startX * sx, startY * sy);
         mouseButton(mc, 1);
         BazaarClient.LOG.info(
            "Dev: drag press at {},{} (button {}), windowActive={}, guiScale={}, screen={}x{}",
            startX, startY, java.util.Arrays.toString(r), mc.isWindowActive(), win.getGuiScale(), ps.width, ps.height
         );
      } else if (t > 220 && t <= 220 + steps * 10 && (t - 220) % 10 == 0) {
         double f = (t - 220) / (steps * 10.0);
         mouseMove(mc, (startX + (endX - startX) * f) * sx, (startY + (endY - startY) * f) * sy);
         BazaarClient.LOG.info("Dev: drag step {} -> button {}", f, java.util.Arrays.toString(ps.devXY()));
      } else if (t == 230 + steps * 10) {
         mouseButton(mc, 0);
         BazaarClient.LOG.info(
            "Dev: drag release -> button {} rect {} prefs {}",
            java.util.Arrays.toString(ps.devXY()), java.util.Arrays.toString(TradeAhUi.rect(mc.font, 3, ps.width, ps.height)), ModSettings.get().toJson().trim()
         );
         shot(mc, "placedrag-after");
      } else if (t == 260 + steps * 10) {
         ps.onClose();
         BazaarClient.LOG.info("Dev: placeDrag done, mod-settings.json = {}", String.valueOf(ConfigFiles.read("mod-settings.json")).trim());
      }
   }

   /** The scaled-GUI -> raw-window pixel factors {sx, sy}. */
   private static double[] rawScale(Minecraft mc) {
      var win = mc.getWindow();
      return new double[]{(double) win.getWidth() / Math.max(1, win.getGuiScaledWidth()), (double) win.getHeight() / Math.max(1, win.getGuiScaledHeight())};
   }

   /** Moves the mouse to a GUI position and presses (action 1) or releases (action 0) the left button, the way a player does. */
   private static void realClick(Minecraft mc, double guiX, double guiY, int action) throws ReflectiveOperationException {
      double[] s = rawScale(mc);
      setMouse(mc, guiX * s[0], guiY * s[1]);
      mouseButton(mc, action);
   }

   /**
    * dev.placeHuman=1: the whole flow a player goes through - settings -> GUI page -> "Drag to place..." -> drag the button
    * onto the middle of the screen (where the Done/Reset buttons are) -> drop it -> pick it up again and drag it away.
    * Every step goes through Minecraft's own mouse pipeline.
    */
   private static void placeHuman(Minecraft mc, int t) throws ReflectiveOperationException {
      if (t == 200) {
         mc.setScreen(new SettingsScreen(null));
         return;
      }

      if (t == 210 && mc.screen instanceof SettingsScreen ss) {
         ss.showPage(SettingsScreen.Page.TRADE);
         return;
      }

      java.lang.reflect.Field focused = com.mojang.blaze3d.platform.Window.class.getDeclaredField("focused");
      focused.setAccessible(true);
      focused.setBoolean(mc.getWindow(), true);
      if (t == 220 && mc.screen instanceof SettingsScreen ss) {
         double[] c = ss.devControl("Place the button");
         if (c != null) {
            BazaarClient.LOG.info("Dev: human clicks [Drag to place...] at {},{}", c[0], c[1]);
            realClick(mc, c[0], c[1], 1);
         }
      } else if (t == 225) {
         mouseButton(mc, 0);
         BazaarClient.LOG.info("Dev: after the click the screen is {}", mc.screen == null ? "null" : mc.screen.getClass().getSimpleName());
      } else if (mc.screen instanceof PlaceButtonScreen ps) {
         // Drag 1: from wherever the button is to the middle of the screen (on top of Done/Reset), in 10 small steps.
         int[] r = TradeAhUi.rect(mc.font, 3, ps.width, ps.height);
         double[] from = {r[0] + 4.5, r[1] + 4.5};
         double[] to = {ps.width / 2.0 - 50, ps.height / 2.0 + 34};
         if (t == 240) {
            realClick(mc, from[0], from[1], 1);
            BazaarClient.LOG.info("Dev: human presses the button at {},{} -> {}", from[0], from[1], java.util.Arrays.toString(ps.devXY()));
         } else if (t > 240 && t <= 260) {
            double f = (t - 240) / 20.0;
            double[] s = rawScale(mc);
            mouseMove(mc, (from[0] + (to[0] - from[0]) * f) * s[0], (from[1] + (to[1] - from[1]) * f) * s[1]);
            if (t % 5 == 0) {
               BazaarClient.LOG.info("Dev: drag1 {} -> {}", f, java.util.Arrays.toString(ps.devXY()));
            }
         } else if (t == 265) {
            mouseButton(mc, 0);
            BazaarClient.LOG.info("Dev: drag1 dropped -> {} prefs {}", java.util.Arrays.toString(ps.devXY()), ModSettings.get().toJson().trim());
            shot(mc, "placehuman-drop1");
         } else if (t == 280) {
            // Drag 2: press the button where it now sits and move it to the top-left. This is where a widget under it would steal the press.
            int[] now = ps.devXY();
            realClick(mc, now[0] + 4.5, now[1] + 4.5, 1);
            BazaarClient.LOG.info("Dev: second press on the button at {},{} -> {}", now[0] + 4.5, now[1] + 4.5, java.util.Arrays.toString(ps.devXY()));
         } else if (t > 280 && t <= 300) {
            double[] s = rawScale(mc);
            int[] now = ps.devXY();
            double f = (t - 280) / 20.0;
            mouseMove(mc, (now[0] + 4.5 - 60 * f) * s[0], (now[1] + 4.5 - 40 * f) * s[1]);
            if (t % 5 == 0) {
               BazaarClient.LOG.info("Dev: drag2 {} -> {}", f, java.util.Arrays.toString(ps.devXY()));
            }
         } else if (t == 305) {
            mouseButton(mc, 0);
            BazaarClient.LOG.info("Dev: drag2 dropped -> {} prefs {}", java.util.Arrays.toString(ps.devXY()), ModSettings.get().toJson().trim());
            shot(mc, "placehuman-drop2");
         } else if (t == 320) {
            ps.onClose();
         }
      } else if (t == 280 || t == 305) {
         BazaarClient.LOG.info("Dev: the place screen is gone, now {}", mc.screen == null ? "null" : mc.screen.getClass().getSimpleName());
      } else if (t == 325 && mc.screen instanceof SettingsScreen ss) {
         ss.onClose();
      } else if (t == 330) {
         BazaarClient.LOG.info("Dev: placeHuman done, mod-settings.json = {}", String.valueOf(ConfigFiles.read("mod-settings.json")).trim());
      }
   }

   /**
    * dev.gear=1: graph open -> gear button -> settings changes the size to 700x300 and Lines to Buy only (like the sliders do) -> Done
    * -> the same graph must now be 700x300 (clamped to the screen); then the mouse is put on the chart for a tooltip screenshot.
    */
   private static void gear(Minecraft mc, int t) throws ReflectiveOperationException {
      if (t == 320 && mc.screen instanceof PriceScreen ps) {
         BazaarClient.LOG.info("Dev: gear before geometry={}", java.util.Arrays.toString(ps.devGeometry()));
         ps.devGear();
      } else if (t == 340 && mc.screen instanceof SettingsScreen) {
         GraphSettings.live(GraphSettings.get().withSize(700, 300).withLines(com.yoav3577.bazaaranalyzer.core.GraphPrefs.Lines.BUY));
      } else if (t == 360 && mc.screen instanceof SettingsScreen ss) {
         ss.onClose();
      } else if (t == 380 && mc.screen instanceof PriceScreen ps) {
         int[] g = ps.devGeometry();
         BazaarClient.LOG.info("Dev: gear after geometry={} screen={}x{}", java.util.Arrays.toString(g), ps.width, ps.height);
      } else if (t > 380 && t < 420 && mc.screen instanceof PriceScreen ps) {
         // Put the mouse on the middle of the chart (every tick, in case real mouse input moves it) for the tooltip.
         int[] g = ps.devGeometry();
         double s = mc.getWindow().getGuiScale();
         for (String f : new String[]{"xpos", "ypos"}) {
            java.lang.reflect.Field fld = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
            fld.setAccessible(true);
            fld.setDouble(mc.mouseHandler, (f.equals("xpos") ? g[4] : g[5]) * s);
         }
      } else if (t == 420) {
         shot(mc, "gear-after");
         BazaarClient.LOG.info("Dev: gear done, graph.json = {}", String.valueOf(ConfigFiles.read("graph.json")).trim());
      }
   }

   private static void createWorld(Minecraft mc) {
      LevelSettings settings = new LevelSettings("BazaarDev", GameType.SURVIVAL, DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
      mc.createWorldOpenFlows()
         .createFreshLevel(
            "BazaarDev" + System.currentTimeMillis() % 100000L,
            settings,
            WorldOptions.testWorldWithRandomSeed(),
            WorldPresets::createFlatWorldDimensions,
            mc.screen
         );
   }

   private static void openGraph(Minecraft mc, String bazaar, String ah) {
      String id = ah != null ? ah : bazaar;
      if (id == null) {
         PriceScreen.open(mc, null, null, null);
      } else {
         PriceScreen.open(mc, new PriceData.Choice(ah != null ? PriceData.Kind.AH : PriceData.Kind.BAZAAR, id, PriceData.pretty(id)), null, null);
      }
   }

   private static void hover(Minecraft mc, String id, int t) throws ReflectiveOperationException {
      if (t == 120) {
         ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
         CompoundTag tag = new CompoundTag();
         tag.putString("id", id);
         stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
         mc.player.getInventory().setItem(9, stack);
         mc.setScreen(new InventoryScreen(mc.player));
      } else if (t == 150 && mc.screen instanceof InventoryScreen inv) {
         for (Slot slot : ((InventoryMenu)inv.getMenu()).slots) {
            if (id.equals(ItemReader.skyblockId(slot.getItem()))) {
               GraphKey.setHoveredForDev(inv, slot);
               GraphKey.openForHovered(mc, inv);
               return;
            }
         }
      }
   }
}

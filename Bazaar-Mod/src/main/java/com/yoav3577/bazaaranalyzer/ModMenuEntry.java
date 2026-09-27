package com.yoav3577.bazaaranalyzer;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "Configure" button. Mod Menu is optional: this class is only loaded through its "modmenu" entrypoint. */
public final class ModMenuEntry implements ModMenuApi {
   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return SettingsScreen::new;
   }
}

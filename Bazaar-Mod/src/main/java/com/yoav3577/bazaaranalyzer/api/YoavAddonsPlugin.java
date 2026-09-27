package com.yoav3577.bazaaranalyzer.api;

/**
 * Lets another mod add its own pages to the Yoav Addons settings screen. Declare the class under the "yoavaddons"
 * entrypoint in that mod's fabric.mod.json; it is only loaded when Yoav Addons is installed.
 */
public interface YoavAddonsPlugin {
   /** Called every time the settings screen builds its options: register pages, then add options to them. */
   void settings(Settings settings);
}

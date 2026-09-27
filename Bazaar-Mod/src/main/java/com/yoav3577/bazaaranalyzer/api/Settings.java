package com.yoav3577.bazaaranalyzer.api;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Adds pages and options to the Yoav Addons settings screen (see YoavAddonsPlugin). Every option is a card with a name,
 * a control and a description; controls call set/onChange at once, so apply changes live and save them in onClose.
 */
public interface Settings {
   /** A category on the left (added after the built-in ones). id is what the other methods and YoavAddons use. */
   void page(String id, String label, String description);

   void info(String page, String name, String text);

   void toggle(String page, String name, String description, Supplier<Boolean> get, Consumer<Boolean> set);

   <T> void choice(String page, String name, String description, List<T> values, Function<T, String> label, Supplier<T> get, Consumer<T> set);

   void slider(String page, String name, String description, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> format);

   void text(String page, String name, String description, String hint, String value, Consumer<String> onChange);

   /** A button; label is read every frame (it can show a state). */
   void button(String page, String name, String description, Supplier<String> label, Runnable run);

   /** Run by the About page's "Reset everything". */
   void onResetAll(Runnable r);

   /** Run when the settings screen closes (save your settings here). */
   void onClose(Runnable r);

   /** Rebuilds the screen's controls, e.g. after a "Reset page" button changed the values they show. */
   void refresh();
}

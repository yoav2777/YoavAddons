package com.yoav3577.bazaaranalyzer.core;

import java.util.regex.Pattern;

public final class TradeChat {
   // Only Hypixel's own shapes: "You cancelled the trade!" / "[RANK] Name cancelled the trade!" (not player chat lines).
   private static final Pattern CANCEL = Pattern.compile("^(?:You|(?:\\[[^\\]]*\\]\\s*)?\\w{1,16}) cancell?ed the trade!$");
   private static final Pattern COMPLETE = Pattern.compile("^Trade completed with .+!$");

   private TradeChat() {
   }

   public static boolean isCancel(String plainLine) {
      return plainLine != null && CANCEL.matcher(plainLine.strip()).matches();
   }

   public static boolean isComplete(String plainLine) {
      return plainLine != null && COMPLETE.matcher(plainLine.strip()).matches();
   }

   /** Verdict from the chat stamps (0 = none): TRUE completed, FALSE cancelled, null undecided. Newer stamp wins. */
   public static Boolean verdict(long cancelAt, long completeAt, long since) {
      boolean c = cancelAt > 0 && cancelAt >= since;
      boolean k = completeAt > 0 && completeAt >= since;
      if (c && k) {
         return cancelAt > completeAt ? Boolean.FALSE : Boolean.TRUE;
      }
      return c ? Boolean.FALSE : k ? Boolean.TRUE : null;
   }
}

package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yoav3577.bazaaranalyzer.core.ModPrefs.Corner;
import org.junit.jupiter.api.Test;

/**
 * The drag-to-place math of PlaceButtonScreen: mouse -> button top-left (ModPrefs.dragXY), drop -> corner + offsets
 * (placedAt), and back to a position (buttonXY). The button must land under the mouse and stay where it was dropped.
 */
class PlaceDragTest {
   // The button and screen of the dev client: "Open 3 AH items on website" on 1280x720 at GUI scale 3.
   private static final int W = 146;
   private static final int H = 16;
   private static final int SW = 427;
   private static final int SH = 240;

   /** Drags a button from its current top-left to a mouse position and drops it there; returns the settings. */
   private static ModPrefs drag(ModPrefs start, int fromX, int fromY, double grabX, double grabY, double toX, double toY) {
      int[] xy = ModPrefs.dragXY(toX, toY, grabX, grabY, W, H, SW, SH);
      assertArrayEquals(new int[]{fromX, fromY}, start.buttonXY(SW, SH, W, H), "the drag starts where the button is");
      return start.placedAt(xy[0], xy[1], W, H, SW, SH);
   }

   @Test
   void theButtonLandsExactlyWhereItWasDropped() {
      // Grabbed 4.5 px in from the top-left of the button at 6,6 and dropped with the mouse at 254.5,124.5.
      ModPrefs p = drag(ModPrefs.DEFAULT, 6, 6, 4.5, 4.5, 254.5, 124.5);
      assertEquals(Corner.BOTTOM_RIGHT, p.corner(), "the nearest corner becomes the anchor");
      assertEquals(31, p.offsetX());
      assertEquals(104, p.offsetY());
      assertArrayEquals(new int[]{250, 120}, p.buttonXY(SW, SH, W, H), "no jump when the drag ends");
      assertArrayEquals(new int[]{250, 120}, ModPrefs.fromJson(p.toJson()).buttonXY(SW, SH, W, H), "and none after a restart");
   }

   @Test
   void everyCornerOfTheScreenKeepsTheDropPoint() {
      int[][] drops = {{10, 8}, {270, 8}, {10, 200}, {281, 224}, {140, 112}};
      Corner[] corners = {Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_LEFT};
      for (int i = 0; i < drops.length; i++) {
         ModPrefs p = ModPrefs.DEFAULT.placedAt(drops[i][0], drops[i][1], W, H, SW, SH);
         assertEquals(corners[i], p.corner(), "anchor for a drop at " + drops[i][0] + "," + drops[i][1]);
         assertArrayEquals(drops[i], p.buttonXY(SW, SH, W, H), "drop at " + drops[i][0] + "," + drops[i][1]);
      }
   }

   @Test
   void theGrabPointStaysUnderTheMouse() {
      // Grabbed near the right end of the button, so a naive "centre it on the mouse" would jump by 100 px.
      double grabX = 120.0;
      double grabY = 12.0;
      for (int mx = 130; mx < 400; mx += 17) {
         int[] xy = ModPrefs.dragXY(mx, 100.0, grabX, grabY, W, H, SW, SH);
         assertEquals(mx - (int) grabX, xy[0], "the button follows the mouse at mouse x " + mx);
         assertEquals(88, xy[1]);
      }
   }

   @Test
   void draggingPastAnEdgeAndBackDoesNotDrift() {
      double grabX = 20.0;
      double grabY = 8.0;
      // Past the right and bottom edges: the button stops at the edge, fully on screen.
      int[] edge = ModPrefs.dragXY(999.0, 999.0, grabX, grabY, W, H, SW, SH);
      assertArrayEquals(new int[]{SW - W, SH - H}, edge);
      // Past the top-left: same, at 0,0 (a negative position would hide the button).
      assertArrayEquals(new int[]{0, 0}, ModPrefs.dragXY(-40.0, -40.0, grabX, grabY, W, H, SW, SH));
      // Back inside: the grab point is under the mouse again, exactly as before the edge.
      assertArrayEquals(new int[]{80, 92}, ModPrefs.dragXY(100.0, 100.0, grabX, grabY, W, H, SW, SH));
   }

   @Test
   void aDropPastTheRightEdgeSitsFlushInTheCorner() {
      // What the user got: dragging to the top-right corner gives top_right with 0 offsets, and the button is on screen.
      ModPrefs p = ModPrefs.DEFAULT.placedAt(ModPrefs.dragXY(500.0, 4.0, 10.0, 4.0, W, H, SW, SH)[0], 0, W, H, SW, SH);
      assertEquals(Corner.TOP_RIGHT, p.corner());
      assertEquals(0, p.offsetX());
      assertEquals(0, p.offsetY());
      assertArrayEquals(new int[]{SW - W, 0}, p.buttonXY(SW, SH, W, H));
   }

   @Test
   void arrowKeysNudgeByOnePixelWithoutChangingTheSpot() {
      ModPrefs p = ModPrefs.DEFAULT.placedAt(250, 120, W, H, SW, SH);
      int[] at = p.buttonXY(SW, SH, W, H);
      for (int i = 0; i < 5; i++) {
         p = p.placedAt(at[0] - 1, at[1] + 1, W, H, SW, SH);
         at = p.buttonXY(SW, SH, W, H);
      }

      assertArrayEquals(new int[]{245, 125}, at, "five nudges = five pixels, no drift from the corner math");
   }

   @Test
   void theSameSpotOnABiggerScreenStaysInItsCorner() {
      // Placed 31/104 from the bottom-right on 427x240; on 854x480 the button keeps that distance from its corner.
      ModPrefs p = ModPrefs.DEFAULT.placedAt(250, 120, W, H, SW, SH);
      assertArrayEquals(new int[]{854 - W - 31, 480 - H - 104}, p.buttonXY(854, 480, W, H));
      // On a screen too small for the offsets, the button is pulled back to the corner instead of off the screen.
      assertArrayEquals(new int[]{0, 0}, p.buttonXY(160, 40, W, H));
   }
}

package com.deepseekharness.app.util;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** HyperOS 窗口几何回归；不代表已在对应 ROM 上执行手势。 */
public final class MainScreenWindowPolicyTest {
  private static final ScreenWindowRegion.Bounds FULL =
      new ScreenWindowRegion.Bounds(0, 0, 1200, 2600);

  private static MainScreenWindowPolicy.Window window(
      int id,
      MainScreenWindowPolicy.Kind kind,
      boolean interactive,
      ScreenWindowRegion.Bounds bounds) {
    return new MainScreenWindowPolicy.Window(0, id, id, kind, interactive, interactive, bounds);
  }

  private static MainScreenWindowPolicy.Window app() {
    return window(1, MainScreenWindowPolicy.Kind.APPLICATION, true, FULL);
  }

  @Test
  public void hyperosTinySystemWindowsDoNotDisableCoordinates() {
    for (var bounds :
        List.of(
            new ScreenWindowRegion.Bounds(0, 200, 1, 201),
            new ScreenWindowRegion.Bounds(0, 0, 1, 1),
            new ScreenWindowRegion.Bounds(400, 400, 402, 402),
            new ScreenWindowRegion.Bounds(400, 400, 401, 404))) {
      var region =
          MainScreenWindowPolicy.region(
              0, List.of(app(), window(2, MainScreenWindowPolicy.Kind.SYSTEM, false, bounds)));
      assertFalse("微小系统窗不应封锁整个画面: " + bounds, region.obscured());
      assertEquals(FULL, region.content());
      assertTrue(region.containsTrajectory(200, 300, 800, 2000, 1));
    }
  }

  @Test
  public void partialGestureEdgesAndBottomChromeLeaveSafeInterior() {
    var left =
        window(
            2,
            MainScreenWindowPolicy.Kind.SYSTEM,
            false,
            new ScreenWindowRegion.Bounds(0, 500, 24, 2200));
    var right =
        window(
            3,
            MainScreenWindowPolicy.Kind.SYSTEM,
            false,
            new ScreenWindowRegion.Bounds(1176, 500, 1200, 2200));
    var bottom =
        window(
            4,
            MainScreenWindowPolicy.Kind.SYSTEM,
            false,
            new ScreenWindowRegion.Bounds(0, 2568, 1200, 2600));
    for (var windows :
        List.of(List.of(app(), left, right, bottom), List.of(app(), bottom, right, left))) {
      var region = MainScreenWindowPolicy.region(0, windows);
      assertFalse(region.obscured());
      assertEquals(new ScreenWindowRegion.Bounds(24, 0, 1176, 2568), region.content());
      assertTrue(region.containsTrajectory(200, 300, 800, 2000, 1));
      assertFalse(region.containsTrajectory(10, 600, 800, 2000, 1));
      assertFalse(region.containsTrajectory(200, 300, 800, 2580, 1));
    }
  }

  @Test
  public void realOverlaysAndInteractiveTinyWindowsRemainBlocked() {
    for (var kind : MainScreenWindowPolicy.Kind.values()) {
      var region =
          MainScreenWindowPolicy.region(
              0,
              List.of(
                  app(),
                  window(2, kind, false, new ScreenWindowRegion.Bounds(400, 400, 403, 403))));
      assertTrue(region.obscured());
      assertFalse(region.containsTrajectory(200, 300, 800, 2000, 1));
    }
    for (var kind :
        List.of(
            MainScreenWindowPolicy.Kind.APPLICATION,
            MainScreenWindowPolicy.Kind.UNSUPPORTED,
            MainScreenWindowPolicy.Kind.SYSTEM)) {
      var region =
          MainScreenWindowPolicy.region(
              0,
              List.of(
                  app(), window(2, kind, true, new ScreenWindowRegion.Bounds(400, 400, 401, 401))));
      assertTrue(region.obscured());
    }
  }

  @Test
  public void tinyNonSystemOverlaysAreNotExempt() {
    for (var kind :
        List.of(
            MainScreenWindowPolicy.Kind.APPLICATION,
            MainScreenWindowPolicy.Kind.UNSUPPORTED,
            MainScreenWindowPolicy.Kind.INPUT_METHOD)) {
      assertTrue(
          MainScreenWindowPolicy.region(
                  0,
                  List.of(
                      app(),
                      window(2, kind, false, new ScreenWindowRegion.Bounds(400, 400, 401, 401))))
              .obscured());
    }
  }

  @Test
  public void missingGeometryAndFullScreenSystemCoverRemainBlocked() {
    for (var bounds :
        new ScreenWindowRegion.Bounds[] {
          null,
          new ScreenWindowRegion.Bounds(0, 0, 0, 0),
          FULL,
          new ScreenWindowRegion.Bounds(
              Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        }) {
      assertTrue(
          MainScreenWindowPolicy.region(
                  0, List.of(app(), window(2, MainScreenWindowPolicy.Kind.SYSTEM, false, bounds)))
              .obscured());
    }
  }
}

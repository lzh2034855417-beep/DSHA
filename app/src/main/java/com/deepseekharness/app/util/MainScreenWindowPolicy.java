package com.deepseekharness.app.util;

import java.util.List;

/** Physical display policy. Virtual displays retain their separate application-window policy. */
public final class MainScreenWindowPolicy {
  private MainScreenWindowPolicy() {}

  public enum Kind {
    APPLICATION,
    SYSTEM,
    INPUT_METHOD,
    UNSUPPORTED
  }

  public record Window(
      int display,
      int id,
      int layer,
      Kind kind,
      boolean focused,
      boolean active,
      ScreenWindowRegion.Bounds bounds) {}

  public record Selection(int index, String diagnostic) {}

  /** IME is an occluder of the focused editor, never an excuse to choose a background app. */
  public static int select(List<Window> windows) {
    return selection(windows).index;
  }

  public static Selection selection(List<Window> windows) {
    int selected = -1, layer = Integer.MIN_VALUE;
    boolean ambiguous = false;
    for (int i = 0; i < windows.size(); i++) {
      Window w = windows.get(i);
      if (w.display != 0 || w.id < 0 || !w.focused && !w.active || w.kind == Kind.INPUT_METHOD)
        continue;
      if (selected < 0 || w.layer > layer) {
        selected = i;
        layer = w.layer;
        ambiguous = false;
      } else if (w.layer == layer) ambiguous = true;
    }
    if (ambiguous) return new Selection(-1, "AMBIGUOUS_INTERACTIVE_WINDOWS");
    if (selected < 0) return new Selection(-1, "NO_FOCUSED_OR_ACTIVE_WINDOW");
    Window w = windows.get(selected);
    if (w.kind != Kind.APPLICATION && w.kind != Kind.SYSTEM)
      return new Selection(-1, "UNSUPPORTED_INTERACTIVE_WINDOW");
    return new Selection(selected, "READABLE");
  }

  /** 裁掉系统边缘窗占用的区域；内部真实遮挡仍拒绝坐标。 */
  public static ScreenWindowRegion region(int selected, List<Window> windows) {
    Window target = windows.get(selected);
    var original = target.bounds;
    if (original == null || !original.valid()) return null;
    var content = original;
    // HyperOS 的占位系统窗可能只有 1×1；不能因此封锁整张截图。
    // 边缘手势条也可能只覆盖边缘的一部分，保守裁掉整条对应边带。
    for (Window other : windows) {
      if (other.display != 0 || other.id == target.id || other.layer <= target.layer) continue;
      var cover = other.bounds;
      if (cover == null || !cover.valid() || tinySystemPlaceholder(other)) continue;
      if (!content.intersects(cover)) continue;
      boolean edgeChrome =
          other.kind == Kind.INPUT_METHOD
              || other.kind == Kind.SYSTEM && !other.focused && !other.active;
      if (edgeChrome) content = clipEdge(content, cover);
    }
    // 所有裁剪完成后再查遮挡，避免窗口枚举顺序使已被裁掉的窗口仍封锁坐标。
    boolean obscured = false;
    for (Window other : windows) {
      if (other.display != 0 || other.id == target.id || other.layer <= target.layer) continue;
      if (tinySystemPlaceholder(other)) continue;
      var cover = other.bounds;
      if (cover == null || !cover.valid() || content.intersects(cover)) obscured = true;
    }
    return new ScreenWindowRegion(original, content, obscured);
  }

  private static boolean tinySystemPlaceholder(Window window) {
    return window.kind == Kind.SYSTEM
        && !window.focused
        && !window.active
        && window.bounds != null
        && window.bounds.valid()
        && area(window.bounds) <= 4;
  }

  private static long area(ScreenWindowRegion.Bounds bounds) {
    // int 坐标跨度可能超过 2^31；饱和计算避免溢出被当成微小窗口。
    long width = (long) bounds.right() - bounds.left();
    long height = (long) bounds.bottom() - bounds.top();
    return width > Long.MAX_VALUE / height ? Long.MAX_VALUE : width * height;
  }

  private static ScreenWindowRegion.Bounds clipEdge(
      ScreenWindowRegion.Bounds content, ScreenWindowRegion.Bounds cover) {
    ScreenWindowRegion.Bounds best = null;
    var candidates =
        new ScreenWindowRegion.Bounds[] {
          cover.top() <= content.top()
              ? new ScreenWindowRegion.Bounds(
                  content.left(), cover.bottom(), content.right(), content.bottom())
              : null,
          cover.bottom() >= content.bottom()
              ? new ScreenWindowRegion.Bounds(
                  content.left(), content.top(), content.right(), cover.top())
              : null,
          cover.left() <= content.left()
              ? new ScreenWindowRegion.Bounds(
                  cover.right(), content.top(), content.right(), content.bottom())
              : null,
          cover.right() >= content.right()
              ? new ScreenWindowRegion.Bounds(
                  content.left(), content.top(), cover.left(), content.bottom())
              : null
        };
    for (var candidate : candidates) {
      if (candidate != null && candidate.valid() && (best == null || area(candidate) > area(best)))
        best = candidate;
    }
    return best == null ? content : best;
  }
}

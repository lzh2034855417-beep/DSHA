package com.deepseekharness.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** ls/du 的本地候选合同：保留枚举，拒绝显式跟随子链接及无法识别的选项。 */
public final class DirectoryReadPaths {
  public record Read(List<String> paths, boolean recursive) {}

  private static final String LS_FLAGS = "ZgoACFHLNRSUXabcdfhikl@mnpqrstux1!~";
  private static final String DU_FLAGS = "hmlcaHkKLsxb";
  private static final Set<String> LS_LONG_FLAGS =
      Set.of("full-time", "show-control-chars", "group-directories-first", "help", "version");

  private DirectoryReadPaths() {}

  public static Read inspect(List<String> argv) {
    if (argv == null || argv.isEmpty()) throw invalid();
    boolean ls = "ls".equals(argv.get(0));
    if (!ls && !"du".equals(argv.get(0))) throw invalid();
    for (String arg : argv) if (arg == null || arg.indexOf('\0') >= 0) throw invalid();
    List<String> paths = new ArrayList<>();
    boolean options = true, recursive = !ls;
    for (int i = 1; i < argv.size(); i++) {
      String arg = argv.get(i);
      if (options && arg.equals("--")) options = false;
      else if (options && arg.startsWith("--")) {
        int equals = arg.indexOf('=');
        String name = arg.substring(2, equals < 0 ? arg.length() : equals);
        if (ls && name.equals("color")) continue;
        if (equals < 0
            && (ls ? LS_LONG_FLAGS.contains(name) : Set.of("help", "version").contains(name)))
          continue;
        if (!ls || !(name.equals("sort") || name.equals("block-size"))) throw invalid();
        String value;
        if (equals >= 0) value = arg.substring(equals + 1);
        else {
          if (++i >= argv.size()) throw invalid();
          value = argv.get(i);
        }
        if (name.equals("block-size") && !value.matches("[0-9]+")) throw invalid();
      } else if (options && arg.startsWith("-") && !arg.equals("-")) {
        for (int j = 1; j < arg.length(); j++) {
          char flag = arg.charAt(j);
          if (flag == 'L')
            throw new IllegalArgumentException("DIRECTORY_SYMLINK_FOLLOW_UNVERIFIED");
          if (ls && flag == 'R') recursive = true;
          if ((ls ? LS_FLAGS : DU_FLAGS).indexOf(flag) >= 0) continue;
          if (flag != (ls ? 'w' : 'd')) throw invalid();
          String value;
          if (j + 1 < arg.length()) value = arg.substring(j + 1);
          else {
            if (++i >= argv.size()) throw invalid();
            value = argv.get(i);
          }
          if (!value.matches("[0-9]+")) throw invalid();
          break;
        }
      } else paths.add(arg);
    }
    if (paths.isEmpty()) paths.add(".");
    return new Read(List.copyOf(paths), recursive);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("DIRECTORY_READ_ARGUMENTS_UNVERIFIED");
  }
}

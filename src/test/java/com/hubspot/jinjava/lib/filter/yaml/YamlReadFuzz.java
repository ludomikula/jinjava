package com.hubspot.jinjava.lib.filter.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded, reproducible YAML text generator: random nested maps/lists rendered in mixed styles from a hostile pool,
 * optional long-line padding that places a surrogate pair across SnakeYAML's 1025-char read boundaries, then random
 * textual mutations so a large share of inputs is invalid YAML. input(i) depends only on (seed, i).
 */
final class YamlReadFuzz {

  static final int MAX_DEPTH = 6;
  static final char MARK = '\uE000'; // placeholder replaced by padding+emoji after rendering
  static final String EMO = "\uD83D\uDE00";
  static final String[] POOL = {
    "a",
    "b",
    "key",
    "k1",
    "x y",
    "",
    " ",
    "\u0085",
    "\u2028",
    "\u2029",
    "\ufeff",
    EMO,
    "🚀x",
    "\t",
    "\r\n",
    "\n",
    "\r",
    "'",
    "\"",
    "#",
    " #",
    ": ",
    "- ",
    ":",
    "-",
    "?",
    "? ",
    "[",
    "]",
    "{",
    "}",
    ",",
    "|",
    ">",
    "&a ",
    "*a",
    "!",
    "!!str ",
    "!!int ",
    "!!binary ",
    "\\",
    "yes",
    "no",
    "on",
    "off",
    "Yes",
    "NO",
    "y",
    "N",
    "true",
    "False",
    "~",
    "null",
    "Null",
    "0755",
    "0o17",
    "0x1F",
    "0b101",
    "1_000",
    "1:30",
    "190:20:30",
    ".inf",
    "-.inf",
    ".NaN",
    "2001-12-14",
    "2001-12-14 21:59:43.10 -5",
    "<<",
    "=",
    "+12",
    "-0",
    "007",
    "08",
    "1.5",
    "1e3",
    "6.8523015e+5",
    ".5",
    "1.",
    "9223372036854775807",
    "9223372036854775808",
    "-9223372036854775809",
    "123456789012345678901234567890123456789012345678901234567890",
    "3.14159265358979323846264338327950288419716939937510",
    "1e400",
    "-0.0",
    "0x7FFFFFFF",
    "0xFFFFFFFFFFFFFFFFFF",
    "é",
    "\u00a0",
    "\u0000",
    "\u0007",
    "\u007F",
    "\uD83D",
    "\uDE00",
  };
  static final String[] ESC_DQ = {
    "\\n",
    "\\t",
    "\\\"",
    "\\\\",
    "\\u0085",
    "\\u2028",
    "\\u2029",
    "\\uFEFF",
    "\\x41",
    "\\N",
    "\\_",
    "\\L",
    "\\P",
    "\\/",
    "\\U0001F600",
    "\\e",
    "\\0",
  };

  private final long seed;

  YamlReadFuzz(long seed) {
    this.seed = seed;
  }

  /** Deterministic input number i. */
  String input(long i) {
    Random r = new Random(seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + 12345);
    StringBuilder sb = new StringBuilder();
    int docs = r.nextInt(25) == 0 ? 2 + r.nextInt(2) : 1;
    for (int d = 0; d < docs; d++) {
      if (d > 0 || r.nextInt(20) == 0) sb
        .append(r.nextBoolean() ? "---" : "--- ")
        .append(r.nextInt(6) == 0 ? "\n" : "");
      if (
        sb.length() > 0 &&
        sb.charAt(sb.length() - 1) != '\n' &&
        sb.charAt(sb.length() - 1) != ' '
      ) sb.append('\n');
      Object root = tree(r, 0);
      sb.append(renderTop(r, root));
      if (r.nextInt(30) == 0) sb.append("...\n");
    }
    String text = sb.toString();
    text = pad(r, text);
    if (r.nextInt(10) < 4) {
      int n = 1 + r.nextInt(3);
      for (int k = 0; k < n; k++) text = mutate(r, text);
    }
    return text;
  }

  // -------------------------------------------------------------- tree: Map<String key, node> as List<Object[]>, List<Object>, Scalar
  record Scalar(String s, int kind) {} // kind 0 = text, 1 = typed literal (int/decimal), 2 = empty

  @SuppressWarnings("unchecked")
  private Object tree(Random r, int depth) {
    int c = r.nextInt(10);
    if (depth >= MAX_DEPTH || c < 4) return scalar(r);
    if (c < 7) {
      List<Object[]> m = new ArrayList<>();
      int n = r.nextInt(5);
      for (int i = 0; i < n; i++) m.add(
        new Object[] { new Scalar(key(r), 0), tree(r, depth + 1) }
      );
      return m;
    }
    List<Object> l = new ArrayList<>();
    int n = r.nextInt(5);
    for (int i = 0; i < n; i++) l.add(tree(r, depth + 1));
    return l;
  }

  private String key(Random r) {
    return r.nextInt(3) == 0
      ? "k" + r.nextInt(4)
      : tok(r) + (r.nextInt(4) == 0 ? tok(r) : "");
  }

  private String tok(Random r) {
    return POOL[r.nextInt(POOL.length)];
  }

  private Scalar scalar(Random r) {
    int c = r.nextInt(20);
    if (c == 0) return new Scalar("", 2);
    if (c == 1) return new Scalar("x" + MARK + "y", 0); // long-line/surrogate-boundary marker
    if (c == 2) return new Scalar("a".repeat(900 + r.nextInt(2400)), 0);
    if (c == 3) return new Scalar(Integer.toString(r.nextInt()), 1);
    if (c == 4) return new Scalar(Long.toString(r.nextLong()), 1);
    if (c == 5) return new Scalar(
      new java.math.BigInteger(70 + r.nextInt(100), r).toString(),
      1
    );
    if (c == 6) return new Scalar(r.nextDouble() * 1e6 + "", 1);
    if (c == 7) return new Scalar(
      "1".repeat(20 + r.nextInt(60)) + "." + "9".repeat(5 + r.nextInt(40)),
      1
    );
    StringBuilder sb = new StringBuilder(tok(r));
    int extra = r.nextInt(3);
    for (int i = 0; i < extra; i++) sb.append(r.nextBoolean() ? " " : "").append(tok(r));
    return new Scalar(sb.toString(), 0);
  }

  // -------------------------------------------------------------- rendering
  private String renderTop(Random r, Object n) {
    return renderBlock(r, n, 0);
  }

  /** returns {inlineText, trailingBlockLines}; block styles return header inline and indented lines trailing. */
  private String[] scalarBlockOrInline(Random r, Scalar s, int indent, boolean flow) {
    if (s.kind() != 0) return new String[] { s.s(), "" };
    String v = s.s();
    int st = r.nextInt(12);
    if (!flow && st == 0) { // literal
      String ind = " ".repeat(indent + 2);
      StringBuilder body = new StringBuilder();
      for (String line : v.split("\n", -1)) body
        .append(ind)
        .append(line.replace("\r", ""))
        .append('\n');
      return new String[] {
        "|" + (r.nextInt(8) == 0 ? "-" : r.nextInt(8) == 0 ? "+" : ""),
        body.toString(),
      };
    }
    if (!flow && st == 1) { // folded
      String ind = " ".repeat(indent + 2);
      return new String[] { ">", ind + v.replace("\n", " ").replace("\r", " ") + "\n" };
    }
    if (st == 2 || st == 3) return new String[] { "'" + v.replace("'", "''") + "'", "" };
    if (st == 4 || st == 5) return new String[] { dq(r, v), "" };
    boolean forcePlain = r.nextInt(25) == 0;
    if (v.isEmpty()) return new String[] { "", "" };
    if (forcePlain || plainSafe(v, flow)) return new String[] { v, "" };
    return new String[] {
      r.nextBoolean() ? "'" + v.replace("'", "''") + "'" : dq(r, v),
      "",
    };
  }

  private String dq(Random r, String v) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < v.length(); i++) {
      char ch = v.charAt(i);
      if (ch == '"') sb.append("\\\""); else if (ch == '\\') sb.append("\\\\"); else if (
        ch == '\n'
      ) sb.append("\\n"); else if (ch == '\t') sb.append(
        r.nextBoolean() ? "\\t" : "\t"
      ); else if (ch == '\r') sb.append("\\r"); else sb.append(ch);
      if (r.nextInt(40) == 0) sb.append(ESC_DQ[r.nextInt(ESC_DQ.length)]);
    }
    return sb.append('"').toString();
  }

  private boolean plainSafe(String v, boolean flow) {
    if (
      v.isEmpty() || v.charAt(0) == ' ' || v.charAt(v.length() - 1) == ' '
    ) return false;
    char f = v.charAt(0);
    if (
      "-?:,[]{}#&*!|>'\"%@`".indexOf(f) >= 0 &&
      !(f == '-' && v.length() > 1 && v.charAt(1) != ' ')
    ) return false;
    if (
      v.contains(": ") ||
      v.contains(" #") ||
      v.endsWith(":") ||
      v.contains("\n") ||
      v.contains("\r") ||
      v.contains("\t")
    ) return false;
    if (
      flow &&
      (v.contains(",") ||
        v.contains("[") ||
        v.contains("]") ||
        v.contains("{") ||
        v.contains("}"))
    ) return false;
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c < 0x20 || c == 0x7F || (c >= 0xD800 && c <= 0xDFFF)) return false;
    }
    return (
      !v.equals("---") &&
      !v.equals("...") &&
      !v.startsWith("--- ") &&
      !v.startsWith("... ")
    );
  }

  @SuppressWarnings("unchecked")
  private String renderBlock(Random r, Object n, int indent) {
    String ind = " ".repeat(indent);
    StringBuilder sb = new StringBuilder();
    if (n instanceof Scalar s) {
      String[] t = scalarBlockOrInline(r, s, indent, false);
      return ind + t[0] + "\n" + t[1];
    }
    if (n instanceof List<?> l && !l.isEmpty() && !(l.get(0) instanceof Object[])) {
      if (r.nextInt(6) == 0) return ind + flow(r, n) + "\n";
      for (Object c : (List<Object>) l) {
        String child = renderBlock(r, c, indent + 2);
        // compact "- " form: replace the child's first indentation by the dash
        sb.append(ind).append("- ").append(child.substring(indent + 2));
      }
      return sb.toString();
    }
    List<Object[]> m = (List<Object[]>) n;
    if (m.isEmpty()) return ind + (r.nextBoolean() ? "{}" : "[]") + "\n";
    if (r.nextInt(6) == 0) return ind + flow(r, n) + "\n";
    for (Object[] e : m) {
      Scalar k = (Scalar) e[0];
      String ks = keyText(r, k.s());
      Object v = e[1];
      if (v instanceof Scalar s) {
        String[] t = scalarBlockOrInline(r, s, indent, false);
        sb
          .append(ind)
          .append(ks)
          .append(':')
          .append(t[0].isEmpty() ? "" : " " + t[0])
          .append('\n')
          .append(t[1]);
      } else {
        boolean seq =
          v instanceof List<?> ll && !ll.isEmpty() && !(ll.get(0) instanceof Object[]);
        sb
          .append(ind)
          .append(ks)
          .append(":\n")
          .append(renderBlock(r, v, seq && r.nextInt(4) == 0 ? indent : indent + 2));
      }
    }
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private String flow(Random r, Object n) {
    if (n instanceof Scalar s) return scalarBlockOrInline(r, s, 0, true)[0];
    if (n instanceof List<?> l && (l.isEmpty() || !(l.get(0) instanceof Object[]))) {
      StringBuilder sb = new StringBuilder("[");
      String sep = "";
      for (Object c : (List<Object>) l) {
        sb.append(sep).append(flow(r, c));
        sep = ", ";
      }
      return sb.append(']').toString();
    }
    StringBuilder sb = new StringBuilder("{");
    String sep = "";
    for (Object[] e : (List<Object[]>) n) {
      String v = flow(r, e[1]);
      sb.append(sep).append(keyText(r, ((Scalar) e[0]).s())).append(": ").append(v);
      sep = ", ";
    }
    return sb.append('}').toString();
  }

  private String keyText(Random r, String k) {
    int c = r.nextInt(6);
    if (c == 0) return "'" + k.replace("'", "''") + "'";
    if (c == 1) return dq(r, k);
    if (k.isEmpty()) return "''";
    if (plainSafe(k, true) || r.nextInt(25) == 0) return k;
    return "'" + k.replace("'", "''") + "'";
  }

  // -------------------------------------------------------------- long-line padding at the 1025 read boundaries
  private String pad(Random r, String text) {
    int at = text.indexOf(MARK);
    if (at < 0) {
      if (r.nextInt(12) != 0) return text;
      // leading comment pushing the doc across the boundary, with a surrogate pair placed on the boundary
      int k = 1 + r.nextInt(4);
      int target = k * 1025 - 1 + (r.nextInt(5) - 2); // high surrogate at/near the last char of read chunk k
      return "# " + "c".repeat(Math.max(0, target - 2)) + EMO + "\n" + text;
    }
    // replace the first marker by padding such that the emoji's high surrogate lands at/near a chunk end
    int k = 1 + r.nextInt(4);
    int target = k * 1025 - 1 + (r.nextInt(5) - 2);
    int n = Math.max(0, target - at);
    text = text.substring(0, at) + "a".repeat(n) + EMO + text.substring(at + 1);
    return text.replace(String.valueOf(MARK), "m");
  }

  // -------------------------------------------------------------- mutations
  private String mutate(Random r, String t) {
    if (t.isEmpty()) return tok(r);
    int p = r.nextInt(t.length() + 1);
    switch (r.nextInt(3)) {
      case 0:
        return t.substring(0, p) + tok(r) + t.substring(p);
      case 1:
        {
          if (p >= t.length()) return t;
          return (
            t.substring(0, p) + t.substring(Math.min(t.length(), p + 1 + r.nextInt(2)))
          );
        }
      default:
        {
          if (p >= t.length()) return t;
          return t.substring(0, p) + tok(r) + t.substring(p + 1);
        }
    }
  }
}

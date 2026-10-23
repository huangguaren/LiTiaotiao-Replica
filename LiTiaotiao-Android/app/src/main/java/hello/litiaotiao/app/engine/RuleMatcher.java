package hello.litiaotiao.app.engine;

import java.util.regex.Pattern;

/**
 * 规则匹配语义（与官方一致）：
 *   +foo 开头 / -foo 结尾 / =foo 完全 / 默认模糊包含 / &amp; 连接多个条件（且）。
 * 匹配目标的优先级：节点 text（或 contentDescription）优先，其次 resource id。
 */
public final class RuleMatcher {
    private static final Pattern BOUNDS =
            Pattern.compile("^\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*(?:,\\s*[01]\\s*)?$");

    private RuleMatcher() {}

    public static boolean isBounds(String value) {
        return value != null && BOUNDS.matcher(value).matches();
    }

    public static boolean matchCondition(String cond, NodeSnapshot n) {
        if (cond == null || n == null) return false;
        // 先尝试主文本，再尝试资源 id（同一字段内处理 & 组合）
        if (n.text != null && !n.text.isEmpty() && matchField(cond, n.text)) return true;
        if (n.desc != null && !n.desc.isEmpty() && matchField(cond, n.desc)) return true;
        if (n.resId != null && !n.resId.isEmpty() && matchField(cond, n.resId)) return true;
        return false;
    }

    private static boolean matchField(String cond, String value) {
        int amp = cond.indexOf('&');
        if (amp >= 0) {
            for (String sub : cond.split("&")) {
                if (sub.isEmpty()) continue;
                if (!matchOne(sub, value)) return false;
            }
            return true;
        }
        return matchOne(cond, value);
    }

    private static boolean matchOne(String cond, String value) {
        if (cond.isEmpty()) return false;
        char c = cond.charAt(0);
        if (c == '=') return value.equals(cond.substring(1));
        if (c == '+') return value.startsWith(cond.substring(1));
        if (c == '-') return value.endsWith(cond.substring(1));
        return value.contains(cond); // 模糊包含；前导 '|' 等按字面文本处理
    }

    public static int[] parseBounds(String value) {
        String[] parts = value.trim().split(",");
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) out[i] = Integer.parseInt(parts[i].trim());
        return out;
    }
}

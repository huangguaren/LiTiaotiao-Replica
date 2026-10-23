package hello.litiaotiao.app.util;

/** 规则文件的键 = Java String.hashCode(包名)，与原版一致。 */
public final class JavaHash {
    private JavaHash() {}

    public static int hash(String packageName) {
        if (packageName == null) return 0;
        return packageName.hashCode();
    }
}

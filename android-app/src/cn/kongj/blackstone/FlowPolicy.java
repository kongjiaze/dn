package cn.kongj.blackstone;

/** Decisions shared by the runtime and host tests, with no Android dependency. */
public final class FlowPolicy {
    public static final int[][] SKILLS = {
        {1960,650}, {2125,650}, {2280,650}, {2440,650},
        {2010,890}, {2190,805}, {2420,810}, {1950,1090},
        {1725,1290}, {1900,1290}
    };
    public static void validateRange(int start, int end) {
        if (start < 1 || end > 6 || start > end)
            throw new IllegalArgumentException("请选择角色1至6，起始不能超过结束");
    }
    public static int roleY(int role) {
        validateRange(role, role);
        return 450 + (role - 2) * 192;
    }
    public static boolean canEnter(int fatigue) {
        if (fatigue < 0) throw new IllegalArgumentException("疲劳值未知");
        return fatigue >= 100;
    }
    public static void verifyConsumption(int before, int after) {
        if (before < 100 || after < 0 || after != before - 100)
            throw new IllegalStateException("进入副本后的疲劳变化不符，停止");
    }
}

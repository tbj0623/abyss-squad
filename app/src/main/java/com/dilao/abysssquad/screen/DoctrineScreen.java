package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Meta;
import com.dilao.abysssquad.Ui;

/**
 * 天赋牌（Doctrine）选择界面，1440×810。
 *
 * <p>18 张牌分 6 组（对应 6 流派），<b>每局只能装备 1 张</b>；额外提供「不装备」选项。
 *
 * <p><b>数据源唯一</b>：全部读 {@link Meta}，本类不维护任何本地副本。
 * 分组不按"下标区间"假设，而是逐张读 {@code Meta.DOCTRINE_GROUP[i]} 动态分桶 ——
 * 这保证了 {@code click()} 返回的下标永远是 Meta 的真实下标。
 * （注意 {@code DOCTRINE_GROUP} 并不是按 0,0,0,1,1,1... 排列的，
 * 任何 {@code i = g*3+col} 的下标推算都会错位。）
 *
 * <p><b>布局</b>：6 组排成 2 行 × 3 列的大组块，每个组块内部纵向排该组的 3 张牌。
 * 组块位置由「组序号」决定，牌由「动态分桶」决定，两者解耦。
 *
 * <p><b>动作码</b>
 * <ul>
 *   <li>{@code 14000 + index} — 装备第 index 张牌（0..17，真实下标）</li>
 *   <li>{@link #A_NONE} — 卸下 / 不装备</li>
 *   <li>{@link Ui#A_BACK} — 返回</li>
 *   <li>{@link Ui#A_CONFIRM} — 确认</li>
 * </ul>
 * 未解锁的牌仍注册命中并返回动作码，由调用方（GameView → Meta.selectDoctrine）判定失败，
 * 同时本页给红色闪烁反馈。
 */
public final class DoctrineScreen {

    /** 装备牌的动作码基址：{@code 14000 + index}。 */
    public static final int A_SELECT_BASE = 14000;
    /** 「不装备」的动作码：14099，与牌区间 14000..14017 隔开，避免将来扩牌时撞号。 */
    public static final int A_NONE = 14099;

    public int hit = -1;

    private float px = -1, py = -1;
    private int denyId = -1;
    private long denyNanos = 0L;

    // ── 2 行 × 3 列的组块布局 ──────────────────────────────────
    //  横向：60 + 3*408 + 2*24 = 1332，右边距 108
    //  纵向：190 + 2*(246+12) = 706，底部按钮 y=728 起，不重叠
    private static final float BLOCK_X = 60f;
    private static final float BLOCK_Y = 186f;
    private static final float BLOCK_W = 408f;
    private static final float BLOCK_H = 246f;
    private static final float BLOCK_GAP_X = 24f;
    private static final float BLOCK_GAP_Y = 12f;

    private static final float CARD_INSET_X = 10f;
    private static final float CARD_TOP = 34f;      // 组标题占 34
    private static final float CARD_H = 64f;
    private static final float CARD_GAP = 4f;

    public DoctrineScreen() {}

    /**
     * 兼容旧接线：注入 Meta 实例。
     * <p>本类现在所有 {@code draw} 重载都直接接收 {@code Meta} 参数，
     * 这个方法仅为 {@code GameView.drawDoctrine()} 里遗留的 {@code doctrineScreen.bind(meta)}
     * 保留；注入后会作为 3 参数 {@code draw(c,ui,time)} 的默认数据源。
     */
    public void bind(Meta metaInstance) {
        this.bound = metaInstance;
    }

    /** bind() 注入的 Meta，供 3 参数 draw 使用。 */
    private Meta bound;

    public void pointer(float x, float y) { px = x; py = y; }

    /**
     * 返回动作码。节点下标一律是 Meta 的真实下标。
     */
    public int click(int id) {
        hit = id;
        if (id == Ui.A_BACK) return Ui.A_BACK;
        if (id == Ui.A_CONFIRM) return Ui.A_CONFIRM;
        if (id == A_NONE) return A_NONE;
        if (id >= A_SELECT_BASE && id < A_SELECT_BASE + Meta.DOCTRINE_COUNT) return id;
        return -1;
    }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Meta meta) {
        if (c == null || ui == null) return;
        ui.clearHits();
        hit = -1;

        drawBackdrop(c, ui, time);
        drawHeader(c, ui, meta);
        drawBlocks(c, ui, meta);
        drawFooter(c, ui, meta);

        hit = ui.hitAt(px, py);
    }

    /** 兼容只有 3 参数的调用点：优先用 {@link #bind(Meta)} 注入的实例，None 则视为未解锁。 */
    public void draw(Canvas c, Ui ui, float time) {
        draw(c, ui, time, bound);
    }

    // ── 背景 ────────────────────────────────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time) {
        ui.box(c, 0, 0, 1440, 810, 0, 0x77070d11);
        for (int i = 0; i < 20; i++) {
            float x = 80 + (i * 181) % 1290;
            float y = 100 + (i * 227) % 640;
            float a = .09f + .06f * (float) Math.sin(time * .8f + i * 1.6f);
            ui.circle(c, x, y, 1.3f + (i % 3) * .6f, ui.alpha(Ui.VIOLET, (int) (a * 255)));
        }
    }

    // ── 顶部：当前装备 ──────────────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, Meta meta) {
        ui.box(c, 0, 0, 1440, 150, 0, 0xCC0b141b);
        ui.divider(c, 60, 142, 1320);

        ui.text(c, "远征 · 天赋牌", 60, 36, 12.5f, ui.alpha(Ui.MUTED, 225), false);
        ui.box(c, 60, 48, 56, 3, 1.5f, ui.alpha(Ui.VIOLET, 220));
        ui.text(c, "天 赋 牌", 60, 92, 34, Ui.WHITE, true);
        ui.text(c, "DOCTRINE  ·  RUN MODIFIER", 60, 118, 10.5f, ui.alpha(Ui.VIOLET, 175), false);

        int sel = selected(meta);
        String cur = sel < 0 ? "未装备" : Meta.doctrineName(sel);
        int accent = sel < 0 ? Ui.MUTED : groupColor(Meta.doctrineGroup(sel));
        ui.centerSmall(c, "当前装备", 720, 38, 11.5f, ui.alpha(Ui.MUTED, 210));
        ui.center(c, cur, 720, 76, ui.fit(cur, 560, 32, 19), accent, true);
        ui.centerSmall(c, sel < 0 ? "不装备任何天赋牌 · 标准规则开局"
                        : "每局只能装备 1 张 · 可在本页随时更换",
                720, 102, 11.5f, ui.alpha(Ui.MUTED, 205));

        int unlocked = unlockedCount(meta);
        ui.right(c, "已解锁 " + unlocked + " / " + Meta.DOCTRINE_COUNT, 1380, 38, 12.5f,
                ui.alpha(Ui.WHITE, 225), true);
        ui.bar(c, 1148, 52, 232, 12,
                unlocked / (float) Meta.DOCTRINE_COUNT, Ui.VIOLET);

        int fresh = unlockableCount(meta);
        if (fresh > 0) {
            ui.right(c, "★ " + fresh + " 张可解锁", 1380, 92, 11,
                    ui.alpha(Ui.GOLD, 230), true);
        }
    }

    // ── 6 组组块（按 DOCTRINE_GROUP 动态分桶）──────────────────
    private void drawBlocks(Canvas c, Ui ui, Meta meta) {
        for (int g = 0; g < Meta.DOCTRINE_GROUPS; g++) {
            int[] ids = Meta.doctrineGroupMembers(g);
            if (ids.length == 0) continue;
            int bx = g % 3, by = g / 3;
            float x = BLOCK_X + bx * (BLOCK_W + BLOCK_GAP_X);
            float y = BLOCK_Y + by * (BLOCK_H + BLOCK_GAP_Y);
            int gc = groupColor(g);

            ui.panel(c, x, y, BLOCK_W, BLOCK_H, 14, 0x99101c23, ui.alpha(gc, 90));
            ui.box(c, x + 14, y + 10, 4, 14, 2, ui.alpha(gc, 220));
            ui.schoolIcon(c, x + 32, y + 17, 16, g);
            ui.text(c, Meta.doctrineGroupName(g), x + 46, y + 23, 13, ui.alpha(gc, 235), true);
            ui.right(c, ids.length + " 张", x + BLOCK_W - 12, y + 23, 10,
                    ui.alpha(Ui.MUTED, 170), false);

            for (int k = 0; k < ids.length; k++) {
                float cy = y + CARD_TOP + k * (CARD_H + CARD_GAP);
                card(c, ui, meta, ids[k], x + CARD_INSET_X, cy,
                        BLOCK_W - CARD_INSET_X * 2, CARD_H, gc);
            }
        }
    }

    private void card(Canvas c, Ui ui, Meta meta, int i, float x, float y, float w, float h, int gc) {
        boolean unlocked = unlocked(meta, i);
        boolean sel = selected(meta) == i;
        boolean denied = i == denyId && System.nanoTime() - denyNanos < 800_000_000L;
        String name = Meta.doctrineName(i);
        String desc = Meta.doctrineDesc(i);

        if (!unlocked) {
            // 未解锁：灰显 + 显示带进度的解锁条件，但**仍然注册命中**给反馈
            ui.card(c, x, y, w, h, denied ? Ui.RED : Ui.EDGE, false, false);
            ui.box(c, x + 1, y + 1, w - 2, h - 2, 12, 0x55070d11);
            ui.ring(c, x + 14, y + 16, 5.5f, ui.alpha(Ui.MUTED, 120), 1.3f);
            ui.text(c, name, x + 26, y + 21, ui.fit(name, w - 92, 13, 9.5f),
                    ui.alpha(Ui.WHITE, 145), true);
            String hint = meta != null ? requirementProgress(meta, i)
                    : Meta.DOCTRINE_UNLOCK_DESC[i];
            ui.text(c, hint, x + 26, y + 40, ui.fit(hint, w - 46, 10, 7.5f),
                    ui.alpha(denied ? Ui.RED : Ui.MUTED, denied ? 220 : 135), false);
            ui.right(c, "未解锁", x + w - 10, y + 21, 9.5f, ui.alpha(Ui.MUTED, 120), false);
            if (denied) ui.stroke(c, x + 1, y + 1, w - 2, h - 2, 12, ui.alpha(Ui.RED, 170), 1.5f);
            ui.hit(A_SELECT_BASE + i, x, y, w, h);
            return;
        }

        // 已解锁：可选中
        ui.selectCard(c, A_SELECT_BASE + i, x, y, w, h, name, desc, sel, denied ? Ui.RED : gc);
        // selectCard 内部已 ui.hit(...)，不重复登记

        // 已装备标记
        if (sel) {
            ui.diamond(c, x + w - 16, y + h - 14, 6, Ui.MINT);
        }
        // 组别小徽记
        ui.schoolIcon(c, x + w - 16, y + 13, 13, Meta.doctrineGroup(i));
    }

    // ── 底部 ────────────────────────────────────────────────────
    private void drawFooter(Canvas c, Ui ui, Meta meta) {
        float y = 728, bh = 66;
        int sel = selected(meta);
        boolean none = sel < 0;

        ui.button(c, Ui.A_BACK, 60, y, 220, bh, "返回", false, true);
        ui.button(c, A_NONE, 300, y, 210, bh, none ? "不装备 ✓" : "不装备", !none, true);

        String mid = none ? "当前：不装备任何天赋牌" : "当前：" + Meta.doctrineName(sel);
        ui.center(c, mid, 810, y + 28, 13.5f,
                none ? ui.alpha(Ui.MUTED, 215) : ui.alpha(Ui.WHITE, 225), false);
        ui.centerSmall(c, "每局只能装备 1 张，开局时生效", 810, y + 50, 11,
                ui.alpha(Ui.MUTED, 180));

        ui.button(c, Ui.A_CONFIRM, 1120, y, 260, bh, "确认", true, true);
    }

    // ── 取值 ────────────────────────────────────────────────────
    //  Meta 的 doctrine* 方法声明为包私有（com.dilao.abysssquad），本类在 .screen 子包，
    //  编译期不可见，因此统一走反射；方法名/签名变动时 try/catch 降级，不会崩。
    private static Object metaCall(Meta m, String name, Class<?>[] sig, Object... args) {
        if (m == null) return null;
        try {
            java.lang.reflect.Method mm = m.getClass().getDeclaredMethod(name, sig);
            mm.setAccessible(true);
            return mm.invoke(m, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int selected(Meta m) {
        Object o = metaCall(m, "selectedDoctrine", new Class<?>[0]);
        if (o instanceof Integer) {
            int d = (Integer) o;
            return (d >= 0 && d < Meta.DOCTRINE_COUNT) ? d : Meta.DOCTRINE_NONE;
        }
        return Meta.DOCTRINE_NONE;
    }

    private static int unlockedCount(Meta m) {
        Object o = metaCall(m, "doctrineUnlockedCount", new Class<?>[0]);
        if (o instanceof Integer) {
            return Math.max(0, Math.min(Meta.DOCTRINE_COUNT, (Integer) o));
        }
        // 无 Meta：按"默认解锁"口径统计，避免整页显示 0/18
        int n = 0;
        for (int i = 0; i < Meta.DOCTRINE_COUNT; i++) {
            if (Meta.DOCTRINE_REQ_TYPE[i] == Meta.REQ_NONE) n++;
        }
        return n;
    }

    private static boolean unlocked(Meta m, int i) {
        Object o = metaCall(m, "doctrineUnlocked", new Class<?>[]{int.class}, i);
        if (o instanceof Boolean) return (Boolean) o;
        // 无 Meta：与 Meta.normalizeDoctrine 一致 —— REQ_NONE 的牌默认解锁
        return i >= 0 && i < Meta.DOCTRINE_COUNT && Meta.DOCTRINE_REQ_TYPE[i] == Meta.REQ_NONE;
    }

    private static int unlockableCount(Meta m) {
        Object o = metaCall(m, "doctrineUnlockableCount", new Class<?>[0]);
        return o instanceof Integer ? Math.max(0, (Integer) o) : 0;
    }

    private static String requirementProgress(Meta m, int i) {
        Object o = metaCall(m, "doctrineRequirementProgress", new Class<?>[]{int.class}, i);
        if (o instanceof String && !((String) o).isEmpty()) return (String) o;
        return i >= 0 && i < Meta.DOCTRINE_UNLOCK_DESC.length ? Meta.DOCTRINE_UNLOCK_DESC[i] : "";
    }

    private static int groupColor(int g) {
        try {
            return Ui.SCHOOL_COLOR[Math.floorMod(g, Ui.SCHOOL_COLOR.length)];
        } catch (Throwable t) {
            return Ui.VIOLET;
        }
    }

    /** 由调用方（GameView）在点击失败时调用，触发红色闪烁反馈。 */
    public void deny(int index) {
        denyId = index;
        denyNanos = System.nanoTime();
    }

    /** 当前是否有瞬时反馈在显示（供调用方决定是否重绘）。 */
    public boolean animating() {
        return denyId >= 0 && System.nanoTime() - denyNanos < 800_000_000L;
    }
}

package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/** 商店：三件遗物商品，支持重摆与离开。 */
public final class ShopScreen {
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Shader backdrop;

    private static final float CARD_W = 400, CARD_H = 498, CARD_Y = 152;

    public ShopScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        if (c == null || ui == null || g == null) return;
        ui.clearHits();
        hit = -1;

        drawBackdrop(c, ui, time);
        drawHeader(c, ui, time, g, canLead);
        for (int i = 0; i < 3; i++) drawGoods(c, ui, time, g, i, canLead);
        drawFooter(c, ui, g, canLead);
        hit = ui.hitAt(px, py);
    }

    /** 购买 {@code 3000 + i}，重摆 {@link Ui#A_REROLL}，离开 {@link Ui#A_LEAVE}。 */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id >= 3000 && id <= 3002) return id;
        if (id == Ui.A_REROLL) return Ui.A_REROLL;
        if (id == Ui.A_LEAVE) return Ui.A_LEAVE;
        return -1;
    }

    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ── 背景 ────────────────────────────────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time) {
        if (backdrop == null) {
            backdrop = new LinearGradient(0, 0, 0, 810, 0xff1d1912, 0xff0a0d10, Shader.TileMode.CLAMP);
        }
        paint.setShader(backdrop);
        rect.set(0, 0, 1440, 810);
        c.drawRect(rect, paint);
        paint.setShader(null);
        // 吊灯暖光
        ui.glow(c, 720, 40, 520, 0x22eac487);
        ui.glow(c, 720, 40, 260, 0x22eac487);
        for (int i = 0; i < 18; i++) {
            float x = 80 + (i * 173) % 1300;
            float y = 120 + (i * 191) % 620;
            ui.circle(c, x, y, 1.2f + (i % 2), 0x18eac487);
        }
    }

    // ── 顶部 ────────────────────────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        ui.box(c, 0, 0, 1440, 158, 0, 0xD90b141b);
        ui.divider(c, 64, 150, 1312);

        ui.text(c, "交 易 驿 站", 64, 62, 34, Ui.WHITE, true);
        ui.box(c, 64, 76, 62, 3, 1.5f, ui.alpha(Ui.GOLD, 220));

        String greet = greet(time, g);
        ui.text(c, greet, 64, 112, 15, ui.alpha(Ui.WHITE, 210), false);
        ui.text(c, "价格随构筑折扣变化 · 重摆需要星尘", 64, 136, 11.5f, ui.alpha(Ui.MUTED, 190), false);

        // 星尘余额板
        float rx = 1376;
        ui.right(c, "星尘", rx, 44, 11.5f, ui.alpha(Ui.MUTED, 205), false);
        ui.right(c, Integer.toString(Math.max(0, g.coins)), rx, 90, 38, Ui.GOLD, true);
        if (g.coins < 30) {
            ui.right(c, "余额偏低", rx, 120, 11.5f, Ui.RED, false);
        } else {
            ui.right(c, "可购入 " + Math.min(3, g.coins / 30) + " 件", rx, 120, 11.5f,
                    ui.alpha(Ui.MUTED, 200), false);
        }
        if (!canLead) {
            ui.right(c, "等待队长交易", rx, 140, 11.5f, Ui.BLUE, false);
        }
    }

    private String greet(float time, Game g) {
        int phase = (int) (time / 6f) % 4;
        switch (phase) {
            case 0: return "「星尘够亮的东西才会卖个好价。」";
            case 1: return "「你身上有深渊的味道，我喜欢。」";
            case 2: return "「别碰最上面那排，那是我自己的。」";
            default: return "「买完就走，别在店里流血。」";
        }
    }

    // ── 商品卡 ──────────────────────────────────────────────────
    private void drawGoods(Canvas c, Ui ui, float time, Game g, int i, boolean canLead) {
        float totalW = CARD_W * 3 + 48 * 2;
        float x = (1440 - totalW) * .5f + i * (CARD_W + 48);
        float y = CARD_Y;
        int id = g.shopOffers[i];
        int price = g.shopPrices[i];
        boolean sold = g.shopSold[i] || id < 0 || price < 0;

        int accent = Ui.EDGE, rarityColor = Ui.MUTED;
        int school = 0, rarity = 0, base = 0;
        String name = "已售出", desc = "", flavor = "", upgrade = "";
        if (!sold) {
            int idx = Math.max(0, Math.min(Relics.COUNT - 1, id));
            school = Math.max(0, Math.min(Content.SCHOOL_COUNT - 1, Relics.SCHOOL[idx]));
            rarity = Math.max(0, Math.min(3, Relics.RARITY[idx]));
            accent = Content.SCHOOL_COLOR[school];
            rarityColor = ui.rarityColor(rarity);
            name = Relics.NAMES[idx];
            desc = Relics.DESC[idx];
            flavor = Relics.FLAVOR[idx];
            upgrade = Relics.UPGRADE_HINT[idx];
            base = Relics.COST[idx];
        }

        boolean afford = !sold && g.coins >= price;
        boolean hover = !sold && canLead && afford
                && (inside(x, y, CARD_W, CARD_H) || pressed(3000 + i));
        boolean usable = !sold && canLead && afford;

        ui.card(c, x, y, CARD_W, CARD_H, accent, false, hover);
        if (!sold && canLead) ui.hit(3000 + i, x, y, CARD_W, CARD_H);
        if (hover) ui.box(c, x + 1, y + 1, 5, CARD_H - 2, 2.5f, ui.alpha(accent, 200));

        if (sold) {
            ui.gradientRect(c, x + 1, y + 1, CARD_W - 2, 118, 15, 0x14ffffff, 0x00000000);
            ui.center(c, "已 售 出", x + CARD_W * .5f, y + 120, 30, ui.alpha(Ui.MUTED, 120), true);
            ui.centerSmall(c, "SOLD OUT", x + CARD_W * .5f, y + 150, 10, ui.alpha(Ui.MUTED, 90));
            ui.locked(c, x + 28, y + 196, CARD_W - 56, CARD_H - 316, "该货位已空");
            ui.button(c, 3000 + i, x + 32, y + CARD_H - 100, CARD_W - 64, 88, "已售出", false, false);
            return;
        }

        ui.gradientRect(c, x + 1, y + 1, CARD_W - 2, 120, 15, ui.alpha(accent, 30), 0x00000000);
        ui.pill(c, x + 22, y + 22, 78, Content.RARITY_NAMES[rarity], rarityColor);
        ui.pill(c, x + 112, y + 22, 88, Content.SCHOOL_NAMES[school], accent);
        ui.right(c, "货位 " + (i + 1), x + CARD_W - 22, y + 40, 11, ui.alpha(Ui.MUTED, 165), false);

        float iconCx = x + CARD_W * .5f, iconCy = y + 108;
        ui.glow(c, iconCx, iconCy, 88, ui.alpha(accent, 52));
        ui.relic(c, iconCx, iconCy, 112, Math.max(0, Math.min(Relics.COUNT - 1, id)), accent, rarityColor);

        // 名字行（基线）：名字 + 英文/索引副标题
        float nameSize = ui.fit(name, CARD_W - 56, 27, 18);
        ui.center(c, name, x + CARD_W * .5f, y + 200, nameSize, Ui.WHITE, true);
        ui.centerSmall(c, Content.SCHOOL_EN[school] + "  ·  " + Relics.KEY[Math.max(0,
                Math.min(Relics.COUNT - 1, id))], x + CARD_W * .5f, y + 220, 9.5f, ui.alpha(accent, 170));

        // 描述区：y+244 起；风味行只在空间足够时补
        float descTop = y + 244;
        ui.wrap(c, desc, x + 28, descTop, CARD_W - 56, 12.5f, ui.alpha(Ui.WHITE, 226));
        float descH = ui.wrapHeight(desc, CARD_W - 56, 12.5f);
        float dividerY = y + CARD_H - 170;
        float flavorY = descTop + descH + 10;
        float flavorH = ui.wrapHeight(flavor, CARD_W - 56, 11f);
        if (flavorY + flavorH < dividerY - 14) {
            ui.wrap(c, flavor, x + 28, flavorY, CARD_W - 56, 11f, ui.alpha(Ui.MUTED, 172));
        }

        // 价格区（分隔线下方：左侧折扣原价，右侧现价 + 单位）
        ui.divider(c, x + 26, dividerY, CARD_W - 52);
        float priceBase = dividerY + 30;
        boolean discounted = price < base;
        if (discounted) {
            String old = base + " 星尘";
            float ow = ui.measure(old, 13, false);
            float ox = x + 28;
            ui.text(c, old, ox, priceBase, 13, ui.alpha(Ui.MUTED, 150), false);
            ui.line(c, ox, priceBase - 5, ox + ow, priceBase - 5, ui.alpha(Ui.RED, 195), 1.6f);
            ui.pill(c, ox + ow + 10, priceBase - 20, 58, "折扣", Ui.MINT);
        }
        String priceStr = Integer.toString(price);
        float priceSize = ui.fit(priceStr, 76, 28, 17);
        float priceRight = x + CARD_W - 34;
        ui.right(c, "星尘", priceRight, priceBase, 11.5f, ui.alpha(Ui.MUTED, 225), false);
        float unitW = ui.measure("星尘", 11.5f, false);
        ui.right(c, priceStr, priceRight - unitW - 10, priceBase, priceSize,
                afford ? Ui.GOLD : Ui.RED, true);

        // 升级提示：价格与按钮之间的空隙（卡片高 498，按钮起点 y+398）
        ui.centerSmall(c, upgrade, x + CARD_W * .5f, priceBase + 22, 10.5f, ui.alpha(Ui.MUTED, 165));

        String label = afford ? "购 买" : "星尘不足";
        ui.button(c, 3000 + i, x + 32, y + CARD_H - 100, CARD_W - 64, 88, label, afford, usable);
    }

    // ── 底部 ────────────────────────────────────────────────────
    private void drawFooter(Canvas c, Ui ui, Game g, boolean canLead) {
        float y = 676, bh = 88;
        // v2.3：硬上限 3 次已去掉，改为「越刷越贵」。价格必须问 Game（唯一真相源）——
        // 以前这里直接读 Balance.rerollPrice，而真正扣费的那份逻辑在 Game.rerollShop，
        // 两处各写一份就会漂移（职业标价那次已经演示过这个失败模式）。
        int price = g.shopRerollPrice();
        boolean free = price <= 0;
        boolean afford = free || g.coins >= price;
        boolean canReroll = canLead && afford;

        String label = free ? "重 摆  ·  免费"
                : "重 摆  ·  " + price + " 星尘" + (afford ? "" : "  ·  星尘不足");
        ui.button(c, Ui.A_REROLL, 64, y, 340, bh, label, false, canReroll);
        ui.centerSmall(c, "已重摆 " + Math.max(0, g.shopRerolls) + " 次 · 越刷越贵", 234, y + bh + 22, 11.5f,
                ui.alpha(Ui.MUTED, 185));

        // 中间提示
        String tip = "购买后遗物立即生效 · 商店可随时离开";
        ui.center(c, tip, 720, y + 40, 13, ui.alpha(Ui.MUTED, 210), false);
        ui.centerSmall(c, "背包中的流派共鸣会改变后续价格", 720, y + 64, 11,
                ui.alpha(Ui.MUTED, 155));

        ui.button(c, Ui.A_LEAVE, 1036, y, 340, bh, "离开商店", true, canLead);
        ui.centerSmall(c, canLead ? "继续前往下一站" : "等待队长离开", 1206, y + bh + 22, 11.5f,
                canLead ? ui.alpha(Ui.MUTED, 185) : Ui.BLUE);
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}

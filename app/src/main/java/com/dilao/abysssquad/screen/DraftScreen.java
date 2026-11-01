package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/** 星光馈赠：三选一遗物 / 武器进化。 */
public final class DraftScreen {
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Shader backdrop;

    private static final float CARD_W = 380, CARD_H = 470, GAP = 40;
    private static final float ROW_Y = 180;

    public DraftScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, Game.Player p, boolean canLead) {
        if (c == null || ui == null || g == null) return;
        ui.clearHits();
        hit = -1;

        drawBackdrop(c, ui, time);
        drawHeader(c, ui, g, p, canLead);
        if (p != null) {
            for (int i = 0; i < 3; i++) drawCard(c, ui, time, g, p, i, canLead);
            drawSchoolRow(c, ui, g, p);
        } else {
            ui.center(c, "等待本地玩家数据", 720, 400, 22, ui.alpha(Ui.MUTED, 200), false);
        }
        drawActions(c, ui, g, p, canLead);
        hit = ui.hitAt(px, py);
    }

    /** 选卡 {@code 2000 + i}，重掷 {@link Ui#A_REROLL}，切进化 {@link Ui#A_EVOLVE}，构筑 {@link Ui#A_GUIDE}。 */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id >= 2000 && id <= 2002) return id;
        if (id == Ui.A_REROLL) return Ui.A_REROLL;
        if (id == Ui.A_EVOLVE) return Ui.A_EVOLVE;
        if (id == Ui.A_GUIDE) return Ui.A_GUIDE;
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
            backdrop = new LinearGradient(0, 0, 0, 810, 0xff101e28, 0xff070f14, Shader.TileMode.CLAMP);
        }
        paint.setShader(backdrop);
        rect.set(0, 0, 1440, 810);
        c.drawRect(rect, paint);
        paint.setShader(null);
        // 星尘微光
        for (int i = 0; i < 26; i++) {
            float x = 60 + (i * 137) % 1320;
            float y = 60 + (i * 211) % 700;
            float a = .12f + .10f * (float) Math.sin(time * 1.3f + i);
            ui.circle(c, x, y, 1.6f + (i % 3), ui.alpha(Ui.MINT, (int) (a * 255)));
        }
        ui.gradientRect(c, 0, 0, 1440, 150, 0, 0x000b141b, 0x000b141b);
    }

    // ── 标题 ────────────────────────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, Game g, Game.Player p, boolean canLead) {
        ui.box(c, 0, 0, 1440, 158, 0, 0xCC0b141b);
        ui.center(c, "星 光 馈 赠", 720, 62, 40, Ui.WHITE, true);
        ui.centerSmall(c, "STELLAR BOON", 720, 90, 11, ui.alpha(Ui.GOLD, 170));

        String sub;
        if (g.rewardsLeft > 1) {
            sub = "本层还可选择 " + g.rewardsLeft + " 次  ·  选择一项即刻生效";
        } else {
            sub = "选择一项遗物，或切换到本职业的武器进化";
        }
        ui.centerSmall(c, sub, 720, 118, 14, ui.alpha(Ui.MUTED, 225));

        if (p != null) {
            int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));
            float pw = 210, px0 = 40, py0 = 26;
            ui.panel(c, px0, py0, pw, 104, 14, 0xCC14232C, ui.alpha(Ui.EDGE, 200));
            ui.schoolIcon(c, px0 + 34, py0 + 36, 26, Content.SCHOOL[role]);
            ui.text(c, Content.ROLES[role], px0 + 58, py0 + 32, 17, Ui.WHITE, true);
            ui.text(c, Content.SCHOOL_NAMES[Content.SCHOOL[role]] + " · " + Content.ROLE_TITLE[role],
                    px0 + 58, py0 + 54, 11.5f, ui.alpha(Ui.MUTED, 220), false);
            ui.text(c, "等级 " + Math.max(1, g.level), px0 + 22, py0 + 84, 12.5f, Ui.MINT, true);
            ui.text(c, "遗物 " + g.relicCount(p), px0 + 108, py0 + 84, 12.5f, Ui.GOLD, true);
        }

        ui.right(c, "星尘 " + Math.max(0, g.coins), 1400, 46, 20, Ui.GOLD, true);
        ui.right(c, canLead ? "请选择" : "等待队长选择", 1400, 76, 12.5f,
                canLead ? ui.alpha(Ui.MUTED, 220) : Ui.BLUE, false);
        ui.right(c, "重掷 " + Math.max(0, g.rerolls) + " 次", 1400, 104, 11.5f,
                ui.alpha(Ui.MUTED, 190), false);

        float totalW = CARD_W * 3 + GAP * 2;
        ui.divider(c, (1440 - totalW) * .5f, 160, totalW);
    }

    // ── 卡片 ────────────────────────────────────────────────────
    private void drawCard(Canvas c, Ui ui, float time, Game g, Game.Player p, int i, boolean canLead) {
        float x = (1440 - (CARD_W * 3 + GAP * 2)) * .5f + i * (CARD_W + GAP);
        float y = ROW_Y;
        int evo = p.evolveOffer[i];
        int relic = p.offers[i];
        boolean evolution = evo >= 0;
        boolean available = evolution || relic >= 0;
        boolean chosen = p.chosen >= 0;
        boolean picked = p.chosen == i;
        boolean hover = available && canLead && !chosen
                && (inside(x, y, CARD_W, CARD_H) || pressed(2000 + i));
        boolean usable = available && canLead && !chosen;

        int accent;
        int rarity = 0;
        String name;
        String flavor;
        String desc;
        int school = -1;
        if (evolution) {
            int idx = Math.max(0, Math.min(Evolutions.COUNT - 1, evo));
            rarity = Math.max(0, Math.min(3, Evolutions.TIER[idx]));
            accent = Ui.GOLD;
            name = Evolutions.NAMES[idx];
            flavor = Evolutions.FLAVOR[idx];
            desc = Evolutions.DESC[idx];
            school = Content.SCHOOL[Math.max(0, Math.min(Content.ROLES.length - 1, p.role))];
        } else if (available) {
            int idx = Math.max(0, Math.min(Relics.COUNT - 1, relic));
            rarity = Math.max(0, Math.min(3, Relics.RARITY[idx]));
            school = Math.max(0, Math.min(Content.SCHOOL_COUNT - 1, Relics.SCHOOL[idx]));
            accent = Content.SCHOOL_COLOR[school];
            name = Relics.NAMES[idx];
            flavor = Relics.FLAVOR[idx];
            desc = Relics.DESC[idx];
        } else {
            rarity = 0;
            accent = Ui.EDGE;
            name = "空缺";
            flavor = "本次馈赠没有更多选项。";
            desc = "你可以在铁匠铺完成武器进化。";
        }

        ui.card(c, x, y, CARD_W, CARD_H, accent, picked || hover, hover);
        if (usable) ui.hit(2000 + i, x, y, CARD_W, CARD_H);
        if (picked) {
            ui.stroke(c, x - 4, y - 4, CARD_W + 8, CARD_H + 8, 18, ui.alpha(accent, 120), 2f);
        }

        // 顶部色带
        int bandTop = ui.alpha(accent, 34), bandBot = 0x00000000;
        ui.gradientRect(c, x + 1, y + 1, CARD_W - 2, 118, 15, bandTop, bandBot);

        // 稀有度标签
        String rarityName = Content.RARITY_NAMES[rarity];
        int rarityColor = ui.rarityColor(rarity);
        if (evolution) {
            ui.pill(c, x + 22, y + 22, 112, "武器进化", Ui.GOLD);
            ui.pill(c, x + 146, y + 22, 78, rarityName, rarityColor);
        } else {
            ui.pill(c, x + 22, y + 22, 78, rarityName, rarityColor);
            if (available) {
                ui.pill(c, x + 112, y + 22, 88, Content.SCHOOL_NAMES[school], accent);
            }
        }

        // 图标
        float iconCx = x + CARD_W * .5f, iconCy = y + 114;
        ui.glow(c, iconCx, iconCy, available ? 80 : 58, ui.alpha(accent, available ? 56 : 22));
        if (evolution) {
            ui.circle(c, iconCx, iconCy, 54, 0xCC0b141b);
            ui.ring(c, iconCx, iconCy, 54, ui.alpha(Ui.GOLD, 200), 2.4f);
            ui.ring(c, iconCx, iconCy, 45, ui.alpha(Ui.GOLD, 70), 1.2f);
            ui.icon(c, iconCx, iconCy, 50, 3, Ui.GOLD);
        } else if (available) {
            ui.relic(c, iconCx, iconCy, 110, Math.max(0, Math.min(Relics.COUNT - 1, relic)),
                    accent, rarityColor);
        } else {
            ui.circle(c, iconCx, iconCy, 50, 0x66101c23);
            ui.locked(c, x + 22, y + 92, CARD_W - 44, CARD_H - 230, "尚无选项");
        }

        // 名字
        float nameSize = ui.fit(name, CARD_W - 56, 27, 18);
        ui.center(c, name, x + CARD_W * .5f, y + 200, nameSize, available ? Ui.WHITE : Ui.MUTED, true);
        if (available) {
            ui.centerSmall(c, evolution ? "EVOLUTION" : Content.SCHOOL_EN[school],
                    x + CARD_W * .5f, y + 222, 9.5f, ui.alpha(accent, 180));
        }

        // 描述
        float descTop = y + 246;
        float limit = y + CARD_H - 156;
        if (available) {
            float size = 13.5f;
            ui.wrap(c, desc, x + 32, descTop, CARD_W - 64, size, ui.alpha(Ui.WHITE, 226));
            float h = ui.wrapHeight(desc, CARD_W - 64, size);
            float fy = descTop + h + 14;
            float fh = ui.wrapHeight(flavor, CARD_W - 64, 12);
            if (fy + fh <= limit) {
                ui.wrap(c, flavor, x + 32, fy, CARD_W - 64, 12, ui.alpha(Ui.MUTED, 178));
            }
        }

        // 持有层数 / 需求
        float infoY = y + CARD_H - 146;
        ui.divider(c, x + 26, infoY - 16, CARD_W - 52);
        if (evolution) {
            int idx = Math.max(0, Math.min(Evolutions.COUNT - 1, evo));
            int req = Evolutions.REQ_RELIC[idx];
            String reqText = req < 0 ? "无需前置遗物" : "需要遗物 · " + Relics.NAMES[req];
            ui.centerSmall(c, reqText, x + CARD_W * .5f, infoY, 11.5f,
                    req < 0 ? ui.alpha(Ui.MUTED, 205) : Ui.GOLD);
            ui.centerSmall(c, "第 " + Evolutions.TIER[idx] + " 阶形态  ·  替换当前武器表现",
                    x + CARD_W * .5f, infoY + 22, 10.5f, ui.alpha(Ui.MUTED, 170));
        } else if (available) {
            int idx = Math.max(0, Math.min(Relics.COUNT - 1, relic));
            int cap = Math.max(1, Relics.CAP[idx]);
            int held = Math.max(0, p.relics[idx]);
            ui.centerSmall(c, "已持有 " + Math.min(held, cap) + " 层 / 上限 " + cap,
                    x + CARD_W * .5f, infoY, 11.5f, held >= cap ? Ui.GOLD : ui.alpha(Ui.WHITE, 220));
            ui.centerSmall(c, Relics.UPGRADE_HINT[idx], x + CARD_W * .5f, infoY + 22, 10.5f,
                    ui.alpha(Ui.MUTED, 165));
        }

        // 选择按钮（可点击区高 88）
        float bw = CARD_W - 64, bh = 88, bx = x + 32, by = y + CARD_H - 96;
        String label = picked ? "已选择" : chosen ? (canLead ? "本层已选" : "已选择") : "选 择";
        ui.button(c, 2000 + i, bx, by, bw, bh, label, !evolution && usable, usable);

        if (!canLead && available && !chosen) {
            ui.locked(c, x + 24, y + 200, CARD_W - 48, 40, "等待队长选择");
        }
    }

    // ── 流派共鸣行 ──────────────────────────────────────────────
    private void drawSchoolRow(Canvas c, Ui ui, Game g, Game.Player p) {
        float x = 110, y = 656, w = 1220;
        ui.panel(c, x, y, w, 40, 12, 0x99101c23, ui.alpha(Ui.EDGE, 140));
        ui.text(c, "流派共鸣", x + 20, y + 26, 11.5f, ui.alpha(Ui.MUTED, 200), false);
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            int n = g.schoolCount(p, s);
            float cx = x + 92 + s * 150;
            int color = Content.SCHOOL_COLOR[s];
            ui.schoolIcon(c, cx, y + 20, 16, s);
            ui.text(c, Content.SCHOOL_NAMES[s], cx + 14, y + 25, 12,
                    n >= 3 ? Ui.WHITE : ui.alpha(Ui.MUTED, 215), n >= 3);
            ui.text(c, n + " / 3", cx + 62, y + 25, 11,
                    n >= 3 ? Ui.MINT : ui.alpha(Ui.MUTED, 170), n >= 3);
            if (n >= 3) ui.glow(c, cx + 30, y + 20, 26, ui.alpha(color, 60));
        }
        ui.right(c, "共鸣在 3 层时激活", x + w - 20, y + 26, 11, ui.alpha(Ui.MUTED, 175), false);
    }

    // ── 底部操作 ────────────────────────────────────────────────
    private void drawActions(Canvas c, Ui ui, Game g, Game.Player p, boolean canLead) {
        // 用 Game 的权威判定，而不是「这条线还没进化过」。
        // 旧写法只要有三条未进化线就点亮按钮，于是 Lv3 的玩家点开会看到
        // 一个拒绝他的进化界面（TIER2 的前置遗物还没拿到）。
        // v2.3 进化改成真三选一之后，这里还必须与 switchToEvolution 的收集规则一致。
        boolean hasEvolution = g != null && g.evolutionChoices(p) > 0;

        // 底部操作行：y=706..794（88 高），说明文字统一放在按钮「内侧」，不再压到共鸣条
        float bh = 88, by = 706;
        // 重掷（按钮内第二行由 button 只渲染单行，标签里带上次数）
        boolean canReroll = canLead && (p == null || p.chosen < 0) && g.rerolls > 0
                && (g.scouted > 0 || g.coins >= Balance.RELIC_REROLL_COST);
        String cost = g.scouted > 0 ? "免费" : g.coins >= Balance.RELIC_REROLL_COST
                ? Balance.RELIC_REROLL_COST + " 星尘" : "星尘不足";
        String rerollLabel = "重掷 · " + cost + " · 剩 " + Math.max(0, g.rerolls) + " 次";
        ui.button(c, Ui.A_REROLL, 92, by, 330, bh, rerollLabel, false, canReroll);

        // 查看构筑
        ui.ghost(c, Ui.A_GUIDE, 442, by, 180, bh, "查看构筑");

        // 武器进化
        String evoLabel = hasEvolution
                ? "武器进化 · 已解锁 " + Math.max(0, p == null ? 0 : p.evolutionsTaken) + "/3"
                : "进化线已满";
        ui.button(c, Ui.A_EVOLVE, 1000, by, 348, bh, evoLabel, false, canLead && hasEvolution);
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}

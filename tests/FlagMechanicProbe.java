import com.dilao.core.*;

/**
 * v2.0 标志位机制验证：证明每个 F_* 标志位接上后能产生**可测量的战斗差异**。
 * <p>方法：同一 seed 下，对照组不授予机制、实验组强制写入该标志位，
 * 跑固定帧数后比较「造成伤害 / 回血 / 敌人减速帧数 / 击杀数 / 位移」。
 * <p><b>几何很关键</b>：光环半径 92、地雷半径 46，木桩必须放在相应范围内，
 * 否则测到的是"打不到"而不是"没实现"。本探针为每个机制单独设定木桩位置。
 */
public final class FlagMechanicProbe {

    static final int FRAMES = 240;   // 8 秒战斗
    static int FAILED;

    public static void main(String[] args) {
        report("F_AURA 光环持续伤害", 0);
        report("F_LIFESTEAL 命中吸血", 1);
        report("F_SLOWFIELD 减速领域", 2);
        report("F_MINE 周期地雷", 3);
        report("F_SPLITSHOT 分裂弹", 4);
        report("F_CHARGE 蓄力", 5);
        report("F_KNOCKBACK 击退", 6);
        report("F_TELEPORT 击杀瞬移", 7);
        report("F_EXECUTE 处决", 8);

        System.out.println();
        System.out.println(FAILED == 0 ? "ALL FLAG MECHANICS MEASURABLE"
                : "SOME MECHANICS HAVE NO MEASURABLE EFFECT");
        if (FAILED > 0) System.exit(1);
    }

    static void report(String name, int kind) {
        Result base = run(kind, false);
        Result buff = run(kind, true);
        float diff;
        String metric;

        switch (kind) {
            case 1:   // 吸血：回血量
                diff = buff.hpGained - base.hpGained;
                metric = "回血 " + f(base.hpGained) + " -> " + f(buff.hpGained);
                break;
            case 2:   // 减速领域：敌人被减速帧数
                diff = buff.slowFrames - base.slowFrames;
                metric = "减速帧 " + f(base.slowFrames) + " -> " + f(buff.slowFrames);
                break;
            case 6:   // 击退：位移（被推开）
                diff = buff.travel - base.travel;
                metric = "击退位移 " + f(base.travel) + " -> " + f(buff.travel);
                break;
            case 7:   // 瞬移：击杀数
                diff = buff.kills - base.kills;
                metric = "击杀 " + f(base.kills) + " -> " + f(buff.kills);
                break;
            case 8:   // 处决：低血木桩的击杀速度
                diff = buff.kills - base.kills;
                metric = "处决击杀 " + f(base.kills) + " -> " + f(buff.kills);
                break;
            default:  // 其余看伤害
                diff = buff.damageDealt - base.damageDealt;
                metric = "伤害 " + f(base.damageDealt) + " -> " + f(buff.damageDealt);
                break;
        }

        if (diff > .01f) {
            System.out.println("PASS " + name + "  " + metric);
        } else {
            System.out.println("FAIL " + name + "  " + metric + "  (diff=" + f(diff) + ")");
            FAILED++;
        }
    }

    static String f(float v) {
        return String.format("%.1f", v);
    }

    static final class Result {
        float damageDealt, hpGained, slowFrames, travel, kills;
    }

    /**
     * 每个机制一套合适的场景。
     * <ul>
     *   <li>光环/地雷：木桩贴脸（距离 40），确保在半径内</li>
     *   <li>瞬移/处决：多个低血敌人，保证会死</li>
     *   <li>击退：木桩在中距离，观察被推开</li>
     * </ul>
     */
    static Result run(int kind, boolean enable) {
        Game g = new Game(20260202L);
        Game.Player p = g.addPlayer(1, "probe", 0);
        g.contract = 1;
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.selectRoute(0);
        g.spawnClock = 1e9f;
        g.blocks.clear();
        g.props.clear();
        g.hazards.clear();
        g.enemies.clear();
        g.restored();

        p.x = 400; p.y = 310;
        p.attack = 0; p.damage = 40; p.interval = .25f; p.crit = 0f;
        p.hp = 500; p.maxHp = 1000;

        float dist;
        float hp;
        int count;
        switch (kind) {
            case 0:  dist = 40f;  hp = 2_000_000f; count = 1; break;  // 光环：贴脸
            case 3:  dist = 40f;  hp = 2_000_000f; count = 1; break;  // 地雷：贴脸
            case 7:  dist = 90f;  hp = 60f;        count = 6; break;  // 瞬移：多个脆皮
            case 8:  dist = 90f;  hp = 200_000f;   count = 1; break;  // 处决：高血木桩（血量低时触发）
            case 6:  dist = 120f; hp = 2_000_000f; count = 1; break;  // 击退：中距离
            default: dist = 120f; hp = 2_000_000f; count = 1; break;
        }

        for (int i = 0; i < count; i++) {
            Game.Enemy e = new Game.Enemy();
            e.id = g.allocateId();
            e.type = 0;
            e.behavior = Game.B_CHASE;
            e.x = p.x + dist;
            e.y = p.y + (i - (count - 1) / 2f) * 34f;
            e.r = 20;
            e.speed = 0;
            e.cooldown = 1e9f;
            e.telegraph = 1e9f;
            e.stage = -1;
            e.maxHp = e.hp = hp;
            if (kind == 8) e.hp = e.maxHp * .10f;   // 已在处决阈值内
            g.enemies.add(e);
        }

        if (enable) applyMechanic(p, kind);

        float hp0 = p.hp;
        float x0 = p.x, y0 = p.y;
        Result r = new Result();
        for (int i = 0; i < FRAMES; i++) {
            if (p.hp < p.maxHp * .5f) p.hp = p.maxHp * .5f;
            float before = totalEnemyHp(g);
            g.input(p.id, 0, 0, 1, 0, false, 0, 0);
            g.update(Game.STEP);
            r.damageDealt += Math.max(0f, before - totalEnemyHp(g));
            for (Game.Enemy e : g.enemies) if (e.slow > 0f) r.slowFrames += 1f;
        }
        r.hpGained = Math.max(0f, p.hp - hp0);
        r.travel = Game.distance(p.x, p.y, x0, y0);
        r.kills = g.enemies.isEmpty() ? count : (count - g.enemies.size());
        return r;
    }

    static float totalEnemyHp(Game g) {
        float sum = 0f;
        for (Game.Enemy e : g.enemies) sum += Math.max(0f, e.hp);
        return sum;
    }

    /** 直接写入机制字段（绕过遗物抽取，保证对照组干净）。 */
    static void applyMechanic(Game.Player p, int kind) {
        switch (kind) {
            case 0: p.auraCount = 1; break;
            case 1: p.lifeSteal = 3; break;
            case 2: p.slowFieldCount = 1; break;
            case 3: p.mineCount = 1; break;
            case 4: p.splitShot = 1; break;
            case 5: p.chargeCount = 1; break;
            case 6: p.knockback = 2; break;
            case 7: p.flags |= Content.F_TELEPORT; break;
            case 8: p.execute = 3; break;
            default: break;
        }
    }
}

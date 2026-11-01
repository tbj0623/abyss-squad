import com.dilao.core.*;

/**
 * 武器形态（weaponForm）回归测试。
 *
 * <p><b>为什么需要这个测试</b>：{@code Game.fire()} 里早就有三种形态的分支
 * （弹道 +1 且单发 -32% / 单发 +36% / 攻速与散布变化），{@code Codec} 也序列化了
 * 这个字段——但全项目原本<b>只有一次写入</b>（{@code p.weaponForm = 0}），
 * 三种形态永远只有第 0 种可达。这是"消费点齐全、产生路径缺失"的休眠系统，
 * 与 Boss 冲锋、affix 4 是同一类缺陷，只是更难被发现：代码里到处都能搜到 weaponForm。
 *
 * <p>本套验证：
 * <ol>
 *   <li>三种形态都能被选到，且选择后真的改变开火参数</li>
 *   <li>「标准枪管」也算一次主动选择（{@code formChosen}），不会被反复要求重选</li>
 *   <li>形态之间确实互不相同（不是三个同名分支）</li>
 *   <li>非法形态号被拒绝，且不改变状态</li>
 * </ol>
 */
public final class WeaponFormTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 武器形态回归 ===");
        System.out.println();

        reachable();
        System.out.println();
        changesFireOutput();
        System.out.println();
        standardStillCountsAsChoice();
        System.out.println();
        rejectsInvalid();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static Game armory() {
        Game g = new Game(4242L);
        Game.Player p = g.addPlayer(1, "p", 0);
        g.contract = 0;
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        g.phase = Game.ARMORY;
        return g;
    }

    /**
     * 从铁匠铺一路推进到真正的战斗房。
     * <p>铁匠铺之后可能是商店 / 事件 / 营地等相位，必须逐个处理（当前相位没有
     * "跳过" API，就按 UI 会做的操作把它走完），直到进入 FIGHT 为止。
     */
    static Game toFight(Game g) {
        for (int i = 0; i < 200 && g.phase != Game.FIGHT; i++) {
            switch (g.phase) {
                case Game.MAP:    g.selectRoute(0); break;
                case Game.EVENT:  g.resolveEvent(1); g.continueEvent(); break;
                case Game.SHOP:   g.leaveShop(); break;
                case Game.CAMP:   g.camp(0); break;
                case Game.ARMORY: g.evolveWeapon(0); break;
                case Game.SHRINE: g.shrine(1); break;
                case Game.CHOICE: g.choose(1, 0, 0); break;
                default:          g.update(1f / 60f); break;
            }
        }
        return g;
    }

    // ── 1. 三种形态都可达 ─────────────────────────────────────────
    static void reachable() {
        boolean[] ok = new boolean[Game.FORM_COUNT];
        int[] declared = new int[Game.FORM_COUNT];
        for (int f = 0; f < Game.FORM_COUNT; f++) {
            Game g = armory();
            ok[f] = g.chooseWeaponForm(f);
            declared[f] = g.players.get(0).weaponForm;
        }
        boolean all = true;
        for (int f = 0; f < Game.FORM_COUNT; f++) if (!ok[f]) all = false;
        check("三种形态都可被选择", all, "accepted=" + java.util.Arrays.toString(ok));
        check("形态号被如实写入 Player.weaponForm",
                declared[0] == 0 && declared[1] == 1 && declared[2] == 2,
                java.util.Arrays.toString(declared));
        check("形态表三档都有文案",
                Game.FORM_NAME.length == Game.FORM_COUNT
                        && Game.FORM_DESC.length == Game.FORM_COUNT
                        && Game.FORM_TAG.length == Game.FORM_COUNT,
                "count=" + Game.FORM_COUNT);
    }

    // ── 2. 形态真的改变开火产出 ───────────────────────────────────
    static void changesFireOutput() {
        // 直接观察 fire() 的产出：把玩家放到场地中央、朝固定方向、关闭其它随机源，
        // 跑满一个完整射击周期，统计"单轮首发伤害"与"单轮弹道数"。
        // 这两个数正是 Game.fire() 里 weaponForm 三个分支唯一改的东西。
        String[] prints = new String[Game.FORM_COUNT];
        float[] firstDmg = new float[Game.FORM_COUNT];
        int[] firstCount = new int[Game.FORM_COUNT];
        for (int f = 0; f < Game.FORM_COUNT; f++) {
            Game g = armory();
            g.chooseWeaponForm(f);
            g.phase = Game.ARMORY;
            g.evolveWeapon(0);
            toFight(g);
            Game.Player p = g.players.get(0);
            p.invuln = 1e9f;
            p.x = 560; p.y = 310;
            g.shots.clear();
            g.enemies.clear();

            // 一帧内触发开火：attack<=0 且朝固定方向手动瞄准。
            p.attack = 0f;
            p.ay = -1f; p.ax = 0f; p.manual = 1f;
            // update 里 player 会被 AI 推动，这里用最小步长只跑一帧。
            g.update(1f / 60f);

            int count = 0;
            float dmg = -1f;
            for (Game.Shot s : g.shots) {
                if (s.enemy) continue;
                if (dmg < 0f) dmg = s.damage;
                count++;
            }
            firstDmg[f] = dmg;
            firstCount[f] = count;
            prints[f] = "form" + f + " 弹道=" + count + " 单发=" + String.format("%.1f", dmg);
        }

        boolean measured = true;
        for (int f = 0; f < Game.FORM_COUNT; f++) if (firstCount[f] <= 0 || firstDmg[f] <= 0f) measured = false;
        check("三种形态都真的开了火（否则下面两条无意义）", measured,
                java.util.Arrays.toString(prints));

        // 注意：每条比较都必须以 measured 为前置——否则 firstDmg 全为 -1 时，
        // 「-1 < -0.95」这类比较会恒真，测试会假装通过。这是本套最容易写错的地方。
        // 形态 1「连发机匣」：弹道 +1 且单发更轻。
        check("形态 1 单轮弹道数多于形态 0", measured && firstCount[1] > firstCount[0],
                "form0=" + firstCount[0] + " form1=" + firstCount[1]);
        check("形态 1 单发伤害低于形态 0", measured && firstDmg[1] < firstDmg[0] * .95f,
                String.format("form0=%.1f form1=%.1f", firstDmg[0], firstDmg[1]));
        // 形态 2「重击膛线」：单发更重。
        check("形态 2 单发伤害高于形态 0", measured && firstDmg[2] > firstDmg[0] * 1.05f,
                String.format("form0=%.1f form2=%.1f", firstDmg[0], firstDmg[2]));
    }

    // ── 3. 选「标准枪管」也算选过 ─────────────────────────────────
    static void standardStillCountsAsChoice() {
        Game g = armory();
        Game.Player p = g.players.get(0);
        check("未选形态时铁匠铺会提示选择", g.needsWeaponForm(), "before any choice");
        g.chooseWeaponForm(0);                  // 主动选标准
        check("选过标准后不再被要求重选",
                !g.needsWeaponForm() && p.formChosen && p.weaponForm == 0,
                "formChosen=" + p.formChosen + " form=" + p.weaponForm);
    }

    // ── 4. 非法输入被拒绝且不改变状态 ─────────────────────────────
    static void rejectsInvalid() {
        Game g = armory();
        Game.Player p = g.players.get(0);
        int before = p.weaponForm;
        boolean r1 = g.chooseWeaponForm(-1);
        boolean r2 = g.chooseWeaponForm(Game.FORM_COUNT);
        boolean r3 = g.chooseWeaponForm(999);
        check("非法形态号全部被拒绝", !r1 && !r2 && !r3,
                "(-1," + Game.FORM_COUNT + ",999) → " + r1 + "/" + r2 + "/" + r3);
        check("拒绝后状态未改变", p.weaponForm == before && !p.formChosen,
                "form=" + p.weaponForm + " formChosen=" + p.formChosen);

        Game g2 = armory();
        g2.phase = Game.FIGHT;
        check("非铁匠铺相位无法选形态", !g2.chooseWeaponForm(1), "phase=FIGHT");
    }
}

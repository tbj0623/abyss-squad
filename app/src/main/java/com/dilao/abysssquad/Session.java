package com.dilao.abysssquad;

import android.util.Log;

import com.dilao.core.*;
import java.net.*;
import java.util.*;

/**
 * 会话层：单机直接在本地跑权威模拟；联机时本地只是客户端，服务端持有唯一战局。
 * <p>元进度加成在这里注入 Game，保证"局外成长"与"局内规则"解耦。
 */
final class Session implements AutoCloseable {
    private static final String TAG = "AbyssSession";
    // ── P1-11：定步长累加器的三个阈值 ────────────────────────────
    /** 单次调用最多推进多少步（≈0.267s）：先保住这一帧的绘制预算。 */
    private static final int MAX_STEPS_PER_CALL = 8;
    /** 单次调用额外的追帧上限（≈1s 模拟）：积压再大也不让一帧长得离谱。 */
    private static final int MAX_CATCHUP_STEPS = 30;
    /** 积压硬上限（秒）：超过它就不再"等下一帧"，而是当场按定步追帧。 */
    private static final float MAX_BACKLOG = 1f;
    /** 明显异常阈值（秒）：只有进程被挂起过（切后台/断点）才丢弃，且必须记录。 */
    private static final float DROP_BACKLOG = 5f;

    /**
     * 当前渲染用的战局。
     * <p><b>单机</b>：本对象拥有它，且只在渲染线程上推进（{@link #update(float)}）。
     * <p><b>联机</b>：它指向 {@link GameClient#snapshot} 发布的最新解码产物。
     * 该对象由客户端读线程整帧 {@code Codec.decode()} 得到，解码完成后才用 volatile
     * 换引用（P1-16），所以绘制路径只会看到"上一份完整快照"或"这一份完整快照"，
     * 不存在字段级撕裂，也不需要锁或每帧拷贝。
     * <p>因此联机时<b>任何代码都不得写它的字段</b>——包括 pactMods / notice / phase。
     * 需要改状态一律走 {@link #action(int, int)} 等服务端指令。
     */
    volatile Game game;
    volatile GameClient client;
    volatile GameServer server;
    volatile String status = "", address = "";
    volatile boolean host, closed;
    int localId = 1;
    private float accumulator;
    /** 最近一次因异常积压被丢弃的步数（0 = 从未丢弃），供日志/排查。 */
    private int droppedSteps;
    private Meta meta;

    void bindMeta(Meta m) { this.meta = m; }

    /** 单机开局：注入元进度加成后启动。 */
    void solo(int role, int mode, int contract) {
        long seed = mode == Game.MODE_DAILY ? dailySeed() : System.nanoTime();
        Game g = new Game(seed);
        g.addPlayer(1, "你", role);
        g.contract = contract;
        applyMetaBoosts(g);
        // 天赋牌：必须在 startSolo 之前写入，beginRun() 会一次性应用开局类钩子
        // （开局遗物 / 星尘 / 重掷 / 最大生命 / 每层护盾），之后再写就来不及了。
        applyDoctrine(g);
        // v2.0 P0-1：applyMetaBoosts 直接写过裸面板字段，必须先捕获成成长增量，
        // 否则 startSolo / 第一件遗物触发的 recompute 会把整棵元进度树清零。
        g.applyMetaBoostedStats();
        g.startSolo(1, mode);
        game = g;
    }

    /**
     * 把当前装备的天赋牌交给 core。
     *
     * <p>core 侧不引用 {@code Meta}（不变式 5），所以这里只传"牌号 + 主/副钩子 id + 参数"。
     * 未装备时传 {@code DOCTRINE_NONE}，等价于把整组字段清空。
     */
    private void applyDoctrine(Game g) {
        Meta m = meta;
        if (g == null) return;
        int card = m == null ? Meta.DOCTRINE_NONE : m.selectedDoctrine();
        if (card < 0) {
            g.applyDoctrine(Meta.DOCTRINE_NONE, 0, 0, 0f, 0f, 0f);
            return;
        }
        g.applyDoctrine(card, m.doctrineHook(), m.doctrineSideHook(),
                m.doctrineParam(), m.doctrineParam2(), m.doctrineSideParam());
    }

    /** 从存档恢复：保存时已包含加成，只需重新绑定。 */
    void restore(Game loaded) {
        game = loaded;
        localId = loaded.captain;
    }

    private void applyMetaBoosts(Game g) {
        Meta m = meta;
        if (m == null) return;
        g.freeReviveCount = m.freeRevives();
        g.pactUnlocked = m.pactUnlocked;
        g.coins += m.startingCoins();
        g.rerolls += m.startingRerolls();
        for (int i = 0; i < Content.SCHOOL_COUNT; i++) g.schoolBonus[i] = m.schoolBonus(i);
        for (Game.Player p : g.players) {
            p.maxHp += m.bonusHp();
            p.hp = p.maxHp;
            p.damage *= 1 + m.bonusDamage();
            p.speed *= 1 + m.bonusSpeed();
            p.xpBonus += m.bonusXp();
            p.crit = Math.min(.75f, p.crit + m.bonusCrit());
            p.magnet += m.bonusMagnet();
            // 写 dashGrow 而不是直接乘 dashFactor：后者会被 recompute 用遗物乘区
            // 重算覆盖（甚至自乘），元进度很快就会消失。
            p.dashGrow *= 1 - m.bonusDashCd();
            p.discount += Math.round(m.shopDiscount() * 4);
            // ── v2.2：元进度的第二组节点 ────────────────────────────
            // 这 7 个 getter 此前全项目零消费（合计 35 级、3150 星尘，
            // 占整棵树 22.9%）；xpBonus/bonusCoin 则被 recompute 无条件覆盖。
            // 现在写进独立的 meta* 字段，recompute 与战斗代码都会读它们。
            p.metaArmor = m.bonusArmor();
            p.metaRegen = m.bonusRegen();
            p.metaAtkSpeed = m.bonusAtkSpeed();
            p.metaRange = m.bonusRange();
            p.metaCoin = m.bonusCoin();
            p.metaXp = m.bonusXp();
            p.metaEvent = m.eventBonus();
            p.metaRelicSlots = m.relicSlots();
        }
        // 开局自带遗物（元进度节点）
        int free = m.startingRelics();
        if (free > 0 && !g.players.isEmpty()) {
            Game.Player p = g.players.get(0);
            for (int i = 0; i < free; i++) {
                int id = g.random.nextInt(Relics.COUNT);
                for (int tries = 0; tries < 40 && p.relics[id] >= Relics.CAP[id]; tries++)
                    id = g.random.nextInt(Relics.COUNT);
                g.grantRelic(p, id);
            }
        }
    }

    static long dailySeed() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return Long.parseLong(f.format(new Date()));
    }

    void host(int role) {
        host = true;
        status = "正在创建房间…";
        new Thread(() -> {
            try {
                GameServer s = new GameServer(Codec.PORT, System.nanoTime());
                server = s;
                if (closed) { s.close(); return; }
                // ── 天赋牌：房主的牌必须写进服务端那份权威 Game ────────────
                // 联机时本地只是客户端，开局的 beginRun() 在 GameServer 的模拟线程上跑，
                // 因此这里在还处于 LOBBY 时把牌写进去（Git 约束：不改 GameServer 源码）。
                // 判定与写入必须在同一个锁里：GameServer 的 tick 也持同一把锁，
                // 锁外判定会和"房主点了开始"竞争，写成开局后的半途状态。
                Game hostGame = s.snapshot();
                if (hostGame != null) {
                    synchronized (hostGame) {
                        if (hostGame.phase == Game.LOBBY) applyDoctrine(hostGame);
                    }
                }
                address = addresses() + ":" + s.port();
                GameClient c = new GameClient("127.0.0.1", s.port(), s.hostKey, "队长", role);
                client = c;
                if (closed) c.close();
            } catch (Exception e) {
                status = "建房失败：端口可能已被占用";
            }
        }, "abyss-host").start();
    }

    void join(String ip, int port, String name, int role) {
        address = ip + ":" + port;
        client = new GameClient(ip, port, "", name, role);
    }

    void update(float dt) {
        GameClient c = client;
        if (c != null) {
            // 联机时本地不跑模拟，只取服务端权威快照。
            // 只读一次 volatile 引用：旧的 `game = c.snapshot` 每帧直接指向
            // 解码产物，见 P1-16 的交接说明（core 侧已经改成"解码到新对象再发布"）。
            Game g = c.snapshot;
            game = g;
            localId = c.id;
            status = c.status;
            // 联机→单机的切换不能带着旧的积压：那份时间属于上一局/服务端。
            accumulator = 0f;
            return;
        }
        if (game == null) return;
        if (!Float.isFinite(dt) || dt <= 0f) return;    // 暂停帧：不推进，也不清空已积累的时间

        // ── P1-11：定步长累加器，绝不因为卡顿而"少跑时间" ──────────────
        // 旧实现是 accumulator = min(.12, accumulator + dt)，而且 guard=8 之后
        // 直接把剩余积压留在原地、下一帧又被 .12 截断 —— 8~16 分钟的确定性模拟
        // 会比真实时间"变慢"，且联机/单机的推进量因此分叉。
        // 现在：dt 全额累加；步数只限制"单次调用"，余量原样带到下一帧。
        accumulator += dt;

        // 唯一的丢弃分支：只有进程被真正挂起过（切后台、调试断点、系统休眠）
        // 才会积到这种量级。回到前台后原地狂奔 5 秒以上既不像话也不安全，
        // 所以这里显式丢弃并写日志 —— 绝不静默。
        if (accumulator > DROP_BACKLOG) {
            int lost = (int) (accumulator / Game.STEP);
            accumulator = MAX_BACKLOG;   // 保留 1 秒可追的量
            droppedSteps += lost;
            Log.w(TAG, "模拟积压 " + lost + " 步（> " + DROP_BACKLOG
                    + "s，疑似切后台）已丢弃；累计丢弃 " + droppedSteps + " 步");
        }

        // 正常帧：最多 MAX_STEPS_PER_CALL 步，保住本帧的绘制预算；
        // 积压超过 MAX_BACKLOG 时切到"追帧预算"：多跑几步把积压按定步消化掉。
        // 两条路径都只调用 game.update(Game.STEP)，步长与步序完全一致，
        // 因此"同一输入序列 → 同一状态"的确定性不变（输入由 GameView 在调用前下发，
        // 多步帧里每一步看到的都是同一份锁存输入，与旧实现语义相同）。
        int budget = accumulator >= MAX_BACKLOG ? MAX_CATCHUP_STEPS : MAX_STEPS_PER_CALL;
        int steps = 0;
        while (accumulator >= Game.STEP && steps < budget) {
            game.update(Game.STEP);
            accumulator -= Game.STEP;
            steps++;
        }
        // 余量留在 accumulator 里带入下一帧：一次追不完就下一帧继续追，
        // 最终一定收敛到 0，而不是像旧实现那样被截断丢掉。
        if (accumulator < 0f) accumulator = 0f;   // 浮点减法下溢保护
    }

    boolean online() { return host || client != null; }

    void input(float dx, float dy, float ax, float ay, boolean manual, int dash, int skill) {
        GameClient c = client;
        if (c != null) c.input(dx, dy, ax, ay, manual, dash, skill);
        else if (game != null) game.input(localId, dx, dy, ax, ay, manual, dash, skill);
    }

    void start() { if (client != null) client.start(); }

    void choose(int n, int vote) {
        if (client != null) client.choose(n, vote);
        else if (game != null) game.choose(localId, n, vote);
    }

    void ultimate() {
        if (client != null) client.ultimate();
        else if (game != null) game.ultimate(localId);
    }

    void switchToEvolution() {
        if (client != null) client.evolution();
        else if (game != null) game.switchToEvolution(localId);
    }

    /** action 常量见 {@link GameServer} 的 op 5 分支。 */
    void action(int action, int value) {
        if (client != null) { client.action(action, value); return; }
        if (game == null) return;
        switch (action) {
            case 0: game.selectRoute(value); break;
            case 1: game.resolveEvent(value); break;
            case 2: game.continueEvent(); break;
            case 3: game.buyShop(value); break;
            case 4: game.rerollShop(); break;
            case 5: game.leaveShop(); break;
            case 6: game.camp(value); break;
            case 7: game.evolveWeapon(value); break;
            case 8: game.shrine(value); break;
            default: break;
        }
    }

    void reroll() {
        if (client != null) client.reroll();
        else if (game != null) game.rerollDraft(localId);
    }

    @Override public void close() {
        closed = true;
        if (client != null) client.close();
        if (server != null) server.close();
    }

    /** 局域网地址：优先无线网卡。 */
    private static String addresses() {
        try {
            List<String> candidates = new ArrayList<>();
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String nm = n.getName();
                        if (nm.startsWith("wlan") || nm.startsWith("ap")) return a.getHostAddress();
                        candidates.add(a.getHostAddress());
                    }
                }
            }
            if (!candidates.isEmpty()) return candidates.get(0);
        } catch (Exception ignored) {}
        return "请查看 Wi-Fi 设置中的 IP";
    }
}

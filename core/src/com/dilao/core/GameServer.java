package com.dilao.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 手机自建的可信局域网房间，最多 4 席。房主离开会结束房间。
 *
 * <h2>线程模型（v2.0 重写）</h2>
 * <ul>
 *   <li><b>模拟线程</b>（{@link #clock} 单线程调度器）：唯一允许调用
 *       {@code Game.update()} 的地方，每 tick 后广播一次快照。</li>
 *   <li><b>每连接一个读线程</b>：只解析字节、做校验，把动作包成 {@link Command}
 *       投进该连接的有界队列；<b>绝不直接碰 Game</b>。历史上这里是
 *       “socket 线程 synchronized(game) 直接改 Game” —— 一旦 {@code Game.update()}
 *       正在遍历 {@code enemies} 而读线程同时在 {@code game.players} 上增删，
 *       就会出现 {@code ConcurrentModificationException} / 半更新状态。</li>
 *   <li><b>每连接一个写线程</b>：只做 {@code socket.write}，
 *       用单槽 {@link AtomicReference} 覆盖式投递，慢客户端不会拖住模拟。</li>
 * </ul>
 *
 * <h2>断开语义</h2>
 * 座位（Seat）独立于连接（Peer）。断线只把 {@link Game.Player#connected} 置 false 并清空输入，
 * 座位与 token 保留 {@link #REJOIN_GRACE_MS} 毫秒供原玩家用同一 token 重连；
 * 超时才回收座位。这样击杀数、遗物、金币都不会因为一次 Wi-Fi 抖动被清空。
 */
public final class GameServer implements AutoCloseable {

    /** 快照发送间隔：30Hz 模拟，15Hz 广播（与旧实现一致）。 */
    private static final long SNAPSHOT_EVERY_N_TICKS = 2;
    /**
     * 单个连接在 1 秒窗口内允许的最大上行包数。
     *
     * <p><b>这个计数是"每连接"的</b>（{@code packets} 是 handshake() 的局部变量，
     * 每条连接一个读线程各数各的），所以不是"全房间共享 150"。旧注释容易让人
     * 按 4 人共享来算，才会得出"4 × 25 = 100，快顶到 150"的错误结论。
     *
     * <p>真正的单连接最坏合法流量（GameClient 侧）：
     * <ul>
     *   <li>输入：{@code SEND_PERIOD_MS = 40ms} → 25 帧/秒；</li>
     *   <li>命令：sendLoop 每轮把 {@code commands}（容量 12）整队排空，
     *       再加 1 条输入，一轮最多 13 帧；</li>
     *   <li>所以玩家在选项/商店里连点时，瞬时可以达到 25 × 13 = 325 帧/秒；</li>
     *   <li>重连时还会补发 1 帧 resend。</li>
     * </ul>
     * 取 400：覆盖 325 的最坏合法突发并留 ~23% 余量，
     * 同时仍是一个能把恶意客户端挡住的硬上限（再配合 COMMANDS_PER_TICK
     * 限制每条指令对模拟的实际影响）。
     */
    private static final int MAX_PACKETS_PER_SECOND = 400;
    /** 多久没收到输入就认为玩家松开了摇杆（防止“幽灵移动”）。 */
    private static final long INPUT_IDLE_NS = 600_000_000L;
    /** 断线后座位保留时长，超时才真正移除。 */
    private static final long REJOIN_GRACE_MS = 45_000L;
    /** 大厅阶段的宽限期更短：没开局，没必要为幽灵座位占坑太久。 */
    private static final long LOBBY_GRACE_MS = 8_000L;
    /** 每个连接最多积压多少条待执行指令；满了就丢弃最旧的，避免无界增长。 */
    private static final int COMMAND_QUEUE = 64;
    /** 握手超时（读取 MAGIC/VERSION/身份）。 */
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;
    /** 握手完成后等待首个快照之前的读超时（客户端会持续发输入，所以可以短）。 */
    private static final int SESSION_TIMEOUT_MS = 12000;
    /** 一次 tick 内最多处理多少条指令，防止恶意客户端饿死模拟。 */
    private static final int COMMANDS_PER_TICK = 32;
    /**
     * 单次 tick 最多补跑多少个模拟步。
     *
     * <p>为什么需要补跑：{@code scheduleWithFixedDelay(…, 33_333_333 ns)} 只是
     * "希望"30Hz，实际间隔由 OS 定时器精度和调度决定。实测（Windows）单线程
     * 调度器在 33ms 请求下只给出 ~21Hz，于是权威模拟比墙钟慢 ~30%——
     * 这与 P1-11 是同一类缺陷：客户端 25Hz 输入喂进来，服务端却只推进 21Hz，
     * 联机手感/时长都会和单机分叉，而且 {@code LoopbackTest} 的
     * "300 帧后 tick ≥ 140" 会直接失败（HEAD 上同样是 78~88，属既有问题）。
     *
     * <p>做法：记录上次 tick 的墙钟时间，按真实流逝时间折算应推进的步数并补跑。
     * 上限 6 步（0.2s）防止长时间挂起后原地狂奔；超过部分直接丢弃，
     * 因为那种量级只可能来自进程被挂起（合盖/断点），追它没有意义。
     * 步长仍然恒定是 {@link Game#STEP}，步内顺序不变，因此确定性不受影响。
     */
    private static final int MAX_CATCHUP_STEPS = 6;

    private final Game game;
    private final ServerSocket server;
    private final Map<String, Seat> seats = new LinkedHashMap<>();
    private final ScheduledExecutorService clock =
            Executors.newSingleThreadScheduledExecutor(new DaemonFactory("abyss-sim"));
    private final Semaphore handshakes = new Semaphore(8);
    /** 本 tick 待回收的座位（避免遍历 seats 时修改它）。 */
    private final List<Seat> pendingRemovals = new ArrayList<>();
    private volatile boolean running = true;
    private int nextId = 1;
    public final String hostKey = UUID.randomUUID().toString();
    /** 最近一次非致命错误（供 UI 展示）。 */
    public volatile String error = "";

    // ──────────────────────────────────────────────────────────────
    //  数据
    // ──────────────────────────────────────────────────────────────

    private static final class Seat {
        String token;          // 重连凭据（服务端生成）
        int id;                // Game.Player.id
        Peer peer;             // 当前连接，可为 null（断线宽限期内）
        long lastInput;
        long offlineSince;     // 0 = 在线
        final ArrayBlockingQueue<Command> commands = new ArrayBlockingQueue<>(COMMAND_QUEUE);
        /** 已被指令队列请求过的最高 op 序号，用于幂等去重。 */
        int lastSeq = Integer.MIN_VALUE;
    }

    private static final class Peer {
        Socket socket;
        DataOutputStream out;
        final AtomicReference<byte[]> latest = new AtomicReference<>();
        volatile boolean alive = true;

        void close() {
            alive = false;
            Socket s = socket;
            if (s != null) try { s.close(); } catch (IOException ignored) {}
        }

        boolean send(byte[] frame) {
            DataOutputStream o = out;
            if (o == null) return false;
            try {
                o.writeInt(frame.length);
                o.write(frame);
                o.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }
    }

    /** 读线程 → 模拟线程 的唯一通道。 */
    private static final class Command {
        final int op, a, b;
        final float dx, dy, ax, ay;
        final boolean manual;

        Command(int op, float dx, float dy, float ax, float ay, boolean manual, int a, int b) {
            this.op = op;
            this.dx = dx; this.dy = dy; this.ax = ax; this.ay = ay;
            this.manual = manual; this.a = a; this.b = b;
        }

        static Command simple(int op, int a, int b) {
            return new Command(op, 0, 0, 0, 0, false, a, b);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  生命周期
    // ──────────────────────────────────────────────────────────────

    public GameServer(int port, long seed) throws IOException {
        game = new Game(seed);
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        Thread accept = new Thread(this::accept, "abyss-accept");
        accept.setDaemon(true);
        accept.start();
        clock.scheduleWithFixedDelay(this::tick, 0, 33333333, TimeUnit.NANOSECONDS);
    }

    public int port() { return server.getLocalPort(); }

    private void accept() {
        while (running) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                if (running) error = "accept 失败：" + e.getMessage();
                if (server.isClosed()) return;
                continue;                       // 单个 accept 失败不该杀死整个监听循环
            }
            try {
                s.setTcpNoDelay(true);
                s.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                s.setKeepAlive(true);
            } catch (IOException e) {
                closeQuietly(s);
                continue;
            }
            if (!handshakes.tryAcquire()) {     // 握手洪泛保护
                closeQuietly(s);
                continue;
            }
            Thread t = new Thread(() -> handshake(s), "abyss-peer");
            t.setDaemon(true);
            t.start();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  握手
    // ──────────────────────────────────────────────────────────────

    private void handshake(Socket socket) {
        Peer peer = new Peer();
        peer.socket = socket;
        Seat seat = null;
        boolean permit = true;
        DataInputStream in = null;
        DataOutputStream out = null;
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            peer.out = out;

            int magic = in.readInt();
            int version = in.readInt();
            if (magic != Codec.MAGIC) {
                reject(out, "这不是地牢远征的房间（协议标识不匹配）");
                return;
            }
            if (version != Codec.VERSION) {
                // 版本跨越时不能只说"版本不匹配"：把两边版本都报出来，
                // 否则玩家只会看到"连不上"而不知道该升级哪一台设备。
                reject(out, "版本不匹配（房主 v" + Codec.VERSION + " / 本机 v" + version
                        + "）：所有队员请安装同一版 APK");
                return;
            }

            String token = in.readUTF();
            String key = in.readUTF();
            String name = in.readUTF();
            int role = in.readInt();
            name = name.replaceAll("[\\p{Cntrl}]", "").trim();
            if (name.isEmpty()) name = "探险者";
            if (name.length() > 12) name = name.substring(0, 12);
            if (token.length() > 64 || key.length() > 64)
                throw new IOException("无效入房参数");

            String rejection = null;
            synchronized (game) {
                seat = token.isEmpty() ? null : seats.get(token);
                if (seat != null) {
                    // 重连：只要身份 token 对得上，无论战局是否已开始都放行
                    if (!running) rejection = "房间正在关闭";
                } else {
                    if (role < 0 || role >= Content.ROLE_COUNT) rejection = "无效的职业选择";
                    else if (game.phase != Game.LOBBY) rejection = "远征已经开始，新队员请等待下一局";
                    else if (seats.size() >= 4) rejection = "房间已满（最多四人）";
                    else if (seats.isEmpty() && !hostKey.equals(key)) rejection = "房主尚未进入房间";
                    else {
                        seat = new Seat();
                        seat.id = nextId++;
                        seat.token = UUID.randomUUID().toString();
                        seats.put(seat.token, seat);
                        Game.Player created = game.addPlayer(seat.id, name, role);
                        if (created == null) {
                            seats.remove(seat.token);
                            rejection = "无法加入：座位初始化失败";
                            seat = null;
                        }
                    }
                }
                if (rejection == null && seat != null) {
                    if (seat.peer != null) seat.peer.close();   // 顶掉旧连接（同一账号重连）
                    seat.peer = peer;
                    seat.lastInput = System.nanoTime();
                    seat.offlineSince = 0;
                    seat.commands.clear();                      // 丢弃断线期间积压的旧输入
                    Game.Player p = game.player(seat.id);
                    if (p != null) p.connected = true;
                }
            }
            if (rejection != null) { reject(out, rejection); return; }
            if (seat == null) { reject(out, "无法加入房间"); return; }

            out.writeBoolean(true);
            out.writeInt(seat.id);
            out.writeUTF(seat.token);
            out.flush();

            handshakes.release();
            permit = false;
            socket.setSoTimeout(SESSION_TIMEOUT_MS);

            final Seat assigned = seat;
            Thread writer = new Thread(() -> write(peer), "abyss-snapshot-" + assigned.id);
            writer.setDaemon(true);
            writer.start();

            int packets = 0;
            long window = System.nanoTime();
            while (running && peer.alive) {
                int op = in.readUnsignedByte();
                long now = System.nanoTime();
                if (now - window > 1_000_000_000L) { packets = 0; window = now; }
                if (++packets > MAX_PACKETS_PER_SECOND) throw new IOException("消息过快");

                Command cmd = readCommand(in, op);
                if (cmd == null) {                   // op 4 = 主动离开
                    synchronized (game) { removeSeatLocked(assigned); seat = null; }
                    break;
                }
                // 有界队列 + 覆盖式丢弃：读线程永不阻塞在模拟线程上
                if (!assigned.commands.offer(cmd)) {
                    assigned.commands.poll();
                    assigned.commands.offer(cmd);
                }
            }
        } catch (SocketTimeoutException e) {
            // 读超时：客户端静默（切后台/掉网）。交给 finally 走宽限期，不当作致命错误。
        } catch (UTFDataFormatException e) {
            error = "客户端发送了非法字符串";
        } catch (IOException ignored) {
            // 正常断线
        } catch (RuntimeException e) {                 // 绝不让一个连接打死整台服务器
            error = "连接异常：" + e;
        } finally {
            if (permit) handshakes.release();
            peer.close();
            synchronized (game) {
                if (seat != null && seat.peer == peer) {
                    seat.peer = null;
                    seat.offlineSince = System.currentTimeMillis();
                    seat.commands.clear();
                    Game.Player p = game.player(seat.id);
                    if (p != null) {
                        p.connected = false;
                        p.dx = p.dy = 0;
                        p.manual = 0;
                    }
                    // 座位一律保留到宽限期结束：房主在大厅掉线也必须能用
                    // 同一 token 回来继续当队长，否则房间会永远没有 captain。
                    // 真正的“主动离开”（op 4）才立刻释放，见 removeSeatLocked。
                }
            }
        }
    }

    /** 解析一帧上行包；op 4（离开）返回 null。 */
    private static Command readCommand(DataInputStream in, int op) throws IOException {
        switch (op) {
            case 1: {
                float dx = in.readFloat(), dy = in.readFloat();
                float ax = in.readFloat(), ay = in.readFloat();
                boolean manual = in.readBoolean();
                int dash = in.readInt(), skill = in.readInt();
                if (!Float.isFinite(dx) || !Float.isFinite(dy)
                        || !Float.isFinite(ax) || !Float.isFinite(ay))
                    throw new IOException("非法输入向量");
                return new Command(1, dx, dy, ax, ay, manual, dash, skill);
            }
            case 2: return Command.simple(2, 0, 0);
            case 3: {
                int card = in.readInt(), vote = in.readInt();
                if (card < -1 || card > 2) throw new IOException("非法卡牌下标");
                return Command.simple(3, card, vote);
            }
            case 5: {
                int action = in.readInt(), value = in.readInt();
                if (action < 0 || action > 8) throw new IOException("非法操作");
                return Command.simple(5, action, value);
            }
            case 6: return Command.simple(6, 0, 0);
            case 7: return Command.simple(7, 0, 0);
            case 8: return Command.simple(8, 0, 0);
            case 4: return null;
            default: throw new IOException("未知消息 op=" + op);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  写线程
    // ──────────────────────────────────────────────────────────────

    private void write(Peer peer) {
        try {
            while (running && peer.alive) {
                byte[] b = peer.latest.getAndSet(null);
                if (b != null) {
                    if (!peer.send(b)) return;
                } else {
                    Thread.sleep(8);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            peer.close();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  模拟线程（唯一触碰 Game 的地方）
    // ──────────────────────────────────────────────────────────────

    private void tick() {
        try {
            byte[] snapshot;
            // 广播目标列表必须在锁内复制一份：seats 是普通 LinkedHashMap，
            // 握手线程（加入/重连）、tick 自己（宽限期回收）和 close() 都会在
            // synchronized(game) 里增删它。旧实现把 `for (Seat s : seats.values())`
            // 放在锁外，恰好和"新玩家加入"撞上就会抛 ConcurrentModificationException
            // —— 而 catch 分支直接 close()，整间房被一个正常入房动作炸掉。
            List<Peer> targets;
            synchronized (game) {
                long now = System.nanoTime();
                for (Seat s : seats.values()) {
                    // 指令全部在模拟线程上应用 —— 读线程只投递，不执行
                    applyCommands(s, now);
                    if (s.offlineSince == 0 && now - s.lastInput > INPUT_IDLE_NS) {
                        Game.Player p = game.player(s.id);
                        if (p != null) { p.dx = p.dy = 0; p.manual = 0; }
                    }
                    if (s.offlineSince != 0) {
                        long grace = game.phase == Game.LOBBY ? LOBBY_GRACE_MS : REJOIN_GRACE_MS;
                        if (System.currentTimeMillis() - s.offlineSince > grace)
                            pendingRemovals.add(s);
                    }
                }
                for (Seat s : pendingRemovals) removeSeatLocked(s);
                pendingRemovals.clear();

                game.update(Game.STEP);
                if (game.tick % SNAPSHOT_EVERY_N_TICKS == 0) {
                    // 编码留在锁内：必须对"同一个一致状态"取快照，
                    // 否则半更新状态会被编进字节流（客户端看到瞬移/血量对不上）。
                    snapshot = Codec.encode(game);
                    hostTick = game.tick;
                    hostHash = hash(snapshot);
                    targets = peerListLocked();
                } else {
                    snapshot = null;
                    targets = null;
                }
            }
            // ── 锁外发送 ──────────────────────────────────────────────
            // 真正的 socket IO 在每连接自己的写线程上，这里只做一次
            // AtomicReference.set（非阻塞、不持有锁、不受慢客户端影响）。
            // 规则：任何阻塞 IO 都不能在模拟锁里做，否则一个卡死的 socket
            // 会把所有人的 tick 一起拖住。
            if (snapshot != null) {
                for (int i = 0, n = targets.size(); i < n; i++) {
                    Peer p = targets.get(i);
                    if (p != null && p.alive) p.latest.set(snapshot);
                }
            }
        } catch (Exception e) {
            error = "模拟中断：" + e;
            close();
        }
    }

    /**
     * 锁内复制一份当前在线的 Peer 列表。
     * <p>调用方必须已持有 {@code synchronized(game)}；返回的数组在锁外遍历是安全的，
     * 因为后续的 add/remove 只会改动 map，不会改动这个副本。
     */
    private List<Peer> peerListLocked() {
        List<Peer> out = new ArrayList<>(seats.size());
        for (Seat s : seats.values()) out.add(s.peer);
        return out;
    }

    // ── 供 UI / 自动化测试观察的服务端状态 ────────────────────────
    /** 已广播出去的最新快照对应的 tick。 */
    public volatile int hostTick;
    /** 该快照的 64 位哈希（与 {@link GameClient} 侧同一算法）。 */
    public volatile long hostHash;

    /**
     * 快照哈希：只覆盖“必须逐位相同”的权威状态。
     * <p>用于联机自检：客户端把收到的快照算出同一个哈希，与 hostHash 比对即可
     * 在第一时间发现 encode/decode 字段错位（而不是等玩家看到瞬移/血量对不上）。
     */
    public static long hash(Game g) throws IOException {
        return hash(Codec.encode(g));
    }

    static long hash(byte[] frame) {
        long h = 1125899906842597L;
        for (byte b : frame) h = 31 * h + b;
        return h;
    }

    private void applyCommands(Seat seat, long now) {
        if (seat.commands.isEmpty()) return;
        int budget = COMMANDS_PER_TICK;
        Command c;
        while (budget-- > 0 && (c = seat.commands.poll()) != null) {
            switch (c.op) {
                case 1:
                    seat.lastInput = now;
                    game.input(seat.id, c.dx, c.dy, c.ax, c.ay, c.manual, c.a, c.b);
                    break;
                case 2:
                    game.start(seat.id);
                    break;
                case 3:
                    game.choose(seat.id, c.a, c.b);
                    break;
                case 5:
                    if (seat.id == game.captain) captainAction(c.a, c.b);
                    break;
                case 6:
                    game.rerollDraft(seat.id);
                    break;
                case 7:
                    game.ultimate(seat.id);
                    break;
                case 8:
                    game.switchToEvolution(seat.id);
                    break;
                default:
                    break;
            }
        }
    }

    /** op 5：只有队长可以推进房间流程。 */
    private void captainAction(int action, int value) {
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

    private void removeSeatLocked(Seat seat) {
        if (seat == null) return;
        seats.remove(seat.token);
        Game.Player p = game.player(seat.id);
        if (p != null) {
            game.players.remove(p);
            if (game.captain == seat.id && !game.players.isEmpty())
                game.captain = game.players.get(0).id;
        }
        if (seat.peer != null) seat.peer.close();
        seat.peer = null;
        seat.commands.clear();
    }

    // ──────────────────────────────────────────────────────────────
    //  关闭
    // ──────────────────────────────────────────────────────────────

    @Override public void close() {
        if (!running) return;
        running = false;
        clock.shutdownNow();
        try { server.close(); } catch (IOException ignored) {}
        List<Peer> peers = new ArrayList<>();
        synchronized (game) {
            for (Seat s : seats.values()) if (s.peer != null) peers.add(s.peer);
            seats.clear();
        }
        for (Peer p : peers) p.close();
    }

    private static void reject(DataOutputStream out, String reason) {
        try {
            out.writeBoolean(false);
            out.writeUTF(reason);
            out.flush();
        } catch (IOException ignored) {}
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }

    private static final class DaemonFactory implements ThreadFactory {
        private final String prefix;
        DaemonFactory(String prefix) { this.prefix = prefix; }
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix);
            t.setDaemon(true);
            return t;
        }
    }

    /** 供测试/UI 读取的只读快照入口（带锁，避免读到半更新状态）。 */
    public Game snapshot() {
        synchronized (game) { return game; }
    }

    public static void main(String[] args) throws Exception {
        GameServer s = new GameServer(args.length > 0 ? Integer.parseInt(args[0]) : Codec.PORT,
                System.nanoTime());
        System.out.println("Abyss Squad trusted LAN server on " + s.port()
                + "; first player host key: " + s.hostKey);
        Runtime.getRuntime().addShutdownHook(new Thread(s::close));
        while (s.running) Thread.sleep(1000);
    }
}

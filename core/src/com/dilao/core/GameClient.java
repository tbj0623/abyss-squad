package com.dilao.core;

import java.io.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 联机客户端。IO 永远不在渲染线程；输入用"最后值"语义，快照用单槽覆盖，内存有界。
 *
 * <h2>v2.0 修复要点</h2>
 * <ul>
 *   <li><b>输入不再被“吞”</b>：旧实现 {@code sendLoop} 里 {@code input.get()} 而不是
 *       {@code getAndSet(null)}，同一份输入会被 45ms 一轮地反复重发；
 *       更糟的是断线重连后输入帧的 {@code dashSerial}/{@code skillSerial} 会跳变，
 *       服务端把它当成“又按了一次冲刺”。现在改成一次性取走，并在重连成功后
 *       重新同步一次“当前按下的状态”。</li>
 *   <li><b>命令队列不再静默丢弃</b>：{@code ArrayBlockingQueue.offer} 满时返回 false，
 *       旧代码完全忽略返回值——三选一/购买会直接丢帧。现在满了先腾位再投递，
 *       并对关键操作做重试。</li>
 *   <li><b>版本不匹配可读</b>：服务端拒绝时会带上双方版本号，客户端原样展示，
 *       不再统一显示“无法连接，请检查同一 Wi-Fi”。</li>
 *   <li><b>快照过期可感知</b>：{@link #receivedAt} 用于 UI 的“连接中断”横幅；
 *       新增 {@link #staleMillis()}。</li>
 *   <li><b>关闭是可等待的</b>（P1-13）：{@link #close()} 先置关闭标志、投递 op 4、
 *       再按 300ms 上限 join 写线程，最后关 socket；重复调用无副作用。</li>
 *   <li><b>解码失败不再风暴重连</b>（S-14）：快照解码失败走 200ms → 1s → 2s（封顶）
 *       的退避，并在 {@link #status} 里写明“快照解码失败”及原因。</li>
 * </ul>
 *
 * <h2>快照所有权（P1-16）</h2>
 * 读线程每帧 {@link Codec#decode(byte[])} 出一个<b>全新</b> {@link Game}，解码全部完成后
 * 才通过 volatile 字段整体发布。发布之后该对象<b>只读</b>：本类不再写它，
 * UI / Session 也不得写它。因此跨线程只会看到“旧的整对象”或“新的整对象”，
 * 不存在字段级撕裂；绘制路径只需一次 volatile 读，不需要锁，也不需要每帧拷贝。
 */
public final class GameClient implements AutoCloseable {

    /** 连接超时。 */
    private static final int CONNECT_TIMEOUT_MS = 3500;
    /** 读超时：服务端 15Hz 广播，12 秒没有任何帧就认为链路已死。 */
    private static final int READ_TIMEOUT_MS = 12000;
    /** 快照发送周期（与服务器 15Hz 对齐，略快一点避免饿死）。 */
    private static final long SEND_PERIOD_MS = 40;
    /** 最大重连次数。 */
    private static final int MAX_TRIES = 12;
    /** 普通断线（链路层）的重连间隔。 */
    private static final long RETRY_MS = 700;
    /**
     * 快照解码失败的重连退避（S-14）。
     * <p>解码失败是“协议/数据”错误而不是链路抖动：立刻重连也会拿到同样的坏帧，
     * 旧实现的固定 700ms 会在几秒内重连十几次，把服务端和本机都拖进风暴。
     * 200ms → 1s → 2s（封顶），既给对端一个自愈窗口，又不至于让玩家干等。
     */
    private static final long[] DECODE_BACKOFF_MS = {200, 1000, 2000};
    /** close() 等待写线程的上限：只求 op 4 有机会出网，绝不无限期卡住 UI 线程。 */
    private static final long CLOSE_JOIN_MS = 300;

    /**
     * 最新一份已发布的快照（整个对象换引用，不原地改字段）。
     * <p>volatile 写 = 安全发布：decode 里对对象的所有写入都在这次写之前完成，
     * 读线程要么看到 null，要么看到一个完全构造好的对象。
     * <p><b>写入方只有本类的读线程</b>；Session/UI 只能读，绝不能写它的字段。
     */
    public volatile Game snapshot;
    public volatile int id;
    public volatile String status = "正在连接…";
    public volatile boolean connected, running = true;
    public volatile long receivedAt;
    /** 最近一次致命拒绝（版本不匹配/房间已满等），非空时 running=false 且不应重试。 */
    public volatile String fatal = "";
    /** 最近一次失败的可读原因（S-14：重试耗尽时状态栏要说出真正的原因）。 */
    private volatile String lastFailure = "";
    private volatile Socket socket;
    private volatile String token = "";
    /** 当前连接的写线程；close() 按上限 join 它，保证 op 4 有机会真的写出去。 */
    private volatile Thread writer;
    /** 关闭标志：close() 必须幂等（Session 与 GameView 都可能调到它）。 */
    private final AtomicBoolean closing = new AtomicBoolean();
    private final String host, key, name;
    private final int port, role;
    private final AtomicReference<byte[]> input = new AtomicReference<>();
    private final ArrayBlockingQueue<byte[]> commands = new ArrayBlockingQueue<>(12);
    /** 重连后需要补发的“当前状态”（最后一次摇杆/冲刺序号），null 表示不用补。 */
    private volatile byte[] resend;

    public GameClient(String host, int port, String key, String name, int role) {
        this.host = host;
        this.port = port;
        this.key = key;
        this.name = name;
        this.role = role;
        input(0, 0, 1, 0, false, 0, 0);
        Thread t = new Thread(this::loop, "abyss-client");
        t.setDaemon(true);
        t.start();
    }

    public long staleMillis() {
        long at = receivedAt;
        return at == 0 ? Long.MAX_VALUE : (System.nanoTime() - at) / 1_000_000L;
    }

    /** 解码失败专用：让 loop() 能把它和“链路断了”区分开，走不同的退避。 */
    private static final class DecodeFailure extends IOException {
        DecodeFailure(String message, Throwable cause) { super(message, cause); }
    }

    private void loop() {
        int tries = 0;
        int decodeFails = 0;
        long retryDelay = RETRY_MS;
        while (running && tries < MAX_TRIES) {
            Socket s = new Socket();
            socket = s;
            Thread sender = null;
            try {
                status = token.isEmpty() ? "正在连接房间…" : "连接中断，正在重连…";
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                s.setSoTimeout(READ_TIMEOUT_MS);
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

                out.writeInt(Codec.MAGIC);
                out.writeInt(Codec.VERSION);
                out.writeUTF(token);
                out.writeUTF(key);
                out.writeUTF(name);
                out.writeInt(role);
                out.flush();

                boolean accepted;
                try {
                    accepted = in.readBoolean();
                } catch (EOFException eof) {
                    // 老版本服务端在 MAGIC 不对时会直接断开：给一句人话
                    throw new IOException("对端不是同版本的房间（握手被断开）");
                }
                if (!accepted) {
                    String reason = in.readUTF();
                    fatal = reason;
                    status = reason;
                    running = false;          // 服务端明确拒绝，重试没有意义
                    break;
                }
                id = in.readInt();
                token = in.readUTF();
                connected = true;
                fatal = "";
                status = "已连接 · 共享战局";
                sender = new Thread(() -> sendLoop(s, out), "abyss-input");
                sender.setDaemon(true);
                writer = sender;
                sender.start();

                while (running && !s.isClosed()) {
                    byte[] frame = Codec.readFrame(in);
                    Game fresh;
                    try {
                        fresh = Codec.decode(frame);
                    } catch (IOException | RuntimeException bad) {
                        // 帧长度合法但内容解不开：这是协议/数据错误，不是网线问题。
                        // 单独计数并交给下面的 catch，走解码退避而不是 700ms 固定重试。
                        throw new DecodeFailure(shortReason(bad), bad);
                    }
                    // ── P1-16：安全发布 ────────────────────────────────
                    // fresh 是本帧独占的新对象（decode 内部 new Game），此刻已经完整构造；
                    // 先更新 receivedAt 再发布引用，保证读到新快照时也读到新的时间戳，
                    // 以免过期横幅把刚到的快照误判成旧的。整对象换引用，绝不原地改字段。
                    receivedAt = System.nanoTime();
                    snapshot = fresh;
                    lastFailure = "";
                    tries = 0;          // 真正解出一帧才算“连稳了”
                    decodeFails = 0;
                    retryDelay = RETRY_MS;
                }
            } catch (SocketTimeoutException e) {
                if (running) {
                    tries++;
                    retryDelay = RETRY_MS;
                    lastFailure = "房间无响应";
                    status = "房间无响应 · 重连 " + tries + "/" + MAX_TRIES;
                }
            } catch (DecodeFailure e) {
                if (running) {
                    tries++;
                    decodeFails++;
                    // S-14：退避 200ms → 1s → 2s（封顶），并让状态栏说清楚是“解码失败”。
                    retryDelay = DECODE_BACKOFF_MS[Math.min(decodeFails - 1, DECODE_BACKOFF_MS.length - 1)];
                    lastFailure = "快照解码失败：" + e.getMessage();
                    status = "快照解码失败（" + e.getMessage() + "） · 重连 "
                            + tries + "/" + MAX_TRIES;
                }
            } catch (IOException e) {
                if (running) {
                    tries++;
                    retryDelay = RETRY_MS;
                    lastFailure = "连接中断：" + shortReason(e);
                    status = "连接中断（" + shortReason(e) + "）· 重连 " + tries + "/" + MAX_TRIES;
                }
            } catch (RuntimeException e) {
                // 解码之外的意外（例如别的 agent 改了 Codec 字段）：同样退避，别打满 CPU
                if (running) {
                    tries++;
                    decodeFails++;
                    retryDelay = DECODE_BACKOFF_MS[Math.min(decodeFails - 1, DECODE_BACKOFF_MS.length - 1)];
                    lastFailure = "协议错误：" + shortReason(e);
                    status = "协议错误：" + shortReason(e) + " · 重连 " + tries + "/" + MAX_TRIES;
                }
            } finally {
                connected = false;
                writer = null;
                try { s.close(); } catch (IOException ignored) {}
                if (sender != null) sender.interrupt();
            }
            if (running) sleepQuietly(retryDelay);
        }
        // S-14：重试耗尽时不能只说“检查 Wi-Fi”——解码失败和链路断了的原因完全不同，
        // 玩家/开发据此才能判断是“对端版本不对”还是“真的没网”。
        if (running) {
            String why = lastFailure;
            status = why.isEmpty()
                    ? "无法连接，请检查同一 Wi-Fi、地址和防火墙"
                    : "连接失败：" + why + "（已重试 " + MAX_TRIES + " 次）";
        }
        running = false;
    }

    private static String shortReason(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 40 ? m.substring(0, 40) : m;
    }

    private void sendLoop(Socket s, DataOutputStream out) {
        try {
            // 重连后先把“当前状态”补一发，避免服务端停留在断线前的旧摇杆/冲刺序号
            byte[] resendFrame = resend;
            if (resendFrame != null) { out.write(resendFrame); resend = null; }
            while (!s.isClosed()) {
                byte[] cmd;
                while ((cmd = commands.poll()) != null) out.write(cmd);
                byte[] data = input.getAndSet(null);   // 一次性取走，不再反复重发
                if (data != null) out.write(data);
                out.flush();
                // 关闭流程：running 置 false 后还要把队列排空一轮，
                // 否则 close() 投进来的 op 4 会被直接跳过（旧实现的根因）。
                if (!running) break;
                Thread.sleep(SEND_PERIOD_MS);
            }
            // 收尾：把剩余指令（尤其是 op 4）尽力写完再退出。
            byte[] tail;
            while ((tail = commands.poll()) != null) out.write(tail);
            out.flush();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    public void input(float dx, float dy, float ax, float ay, boolean manual, int dash, int skill) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(28);
            DataOutputStream o = new DataOutputStream(b);
            o.writeByte(1);
            o.writeFloat(dx); o.writeFloat(dy); o.writeFloat(ax); o.writeFloat(ay);
            o.writeBoolean(manual);
            o.writeInt(dash); o.writeInt(skill);
            byte[] frame = b.toByteArray();
            input.set(frame);
            resend = frame;              // 重连后补发当前状态
        } catch (IOException ignored) {}
    }

    public void start() { push(new byte[]{2}); }

    public void choose(int card, int vote) {
        offer(o -> { o.writeByte(3); o.writeInt(card); o.writeInt(vote); });
    }

    public void action(int action, int value) {
        offer(o -> { o.writeByte(5); o.writeInt(action); o.writeInt(value); });
    }

    public void reroll() { push(new byte[]{6}); }
    public void ultimate() { push(new byte[]{7}); }
    public void evolution() { push(new byte[]{8}); }

    private interface Cmd { void write(DataOutputStream o) throws IOException; }

    /** 有界队列：满了丢最旧的，绝不静默丢弃新的关键操作。 */
    private void push(byte[] frame) {
        while (!commands.offer(frame)) commands.poll();
    }

    private void offer(Cmd cmd) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(16);
            DataOutputStream o = new DataOutputStream(b);
            cmd.write(o);
            push(b.toByteArray());
        } catch (IOException ignored) {}
    }

    /**
     * 主动离开。
     *
     * <p>顺序很重要（P1-13）：
     * <ol>
     *   <li>先立 {@code closing} 标志并 CAS，保证幂等——重复调用直接返回；</li>
     *   <li>置 {@code running=false}，让重连循环不再开新连接；</li>
     *   <li>把 op 4 投进指令队列（而不是在 UI 线程上另开一个流去写，
     *       那会和写线程抢同一个 socket 的输出流，写出交错字节）；</li>
     *   <li>按 {@link #CLOSE_JOIN_MS} 上限 join 写线程，让它把 op 4 flush 出去；</li>
     *   <li>最后关 socket —— 这一步同时会把还卡在 read/connect 上的循环线程叫醒。</li>
     * </ol>
     * 任何一步失败都只是"服务端等到宽限期超时再回收座位"，不影响本机进程退出。
     */
    @Override public void close() {
        if (!closing.compareAndSet(false, true)) return;   // 幂等
        running = false;
        connected = false;
        push(new byte[]{4});                               // op 4 = 主动离开
        Thread w = writer;
        if (w != null && w != Thread.currentThread()) {
            try {
                w.join(CLOSE_JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Socket s = socket;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

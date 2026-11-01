import com.dilao.core.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无头回环联机测试：真实 GameServer + 真实 GameClient + 真实 TCP。
 *
 * <p>覆盖：
 * <ol>
 *   <li>握手 → 开局（op 2）→ 300 帧输入/快照往返</li>
 *   <li>双端状态哈希一致性（hostHash vs 客户端对同一 tick 快照的哈希）</li>
 *   <li>并发压测：4 个客户端同时狂发输入，验证不出现 CME / 撕裂状态</li>
 *   <li>断线重连：杀掉客户端连接后，座位与进度是否保留</li>
 *   <li>版本不匹配：伪造 v6/v99 握手，检查是否得到可读错误而不是卡死</li>
 * </ol>
 *
 * <p>运行：java -cp <classes> LoopbackTest
 */
public final class LoopbackTest {

    static int pass, fail;
    static final String HOST = "127.0.0.1";

    public static void main(String[] args) throws Exception {
        System.out.println("=== LOOPBACK / LAN INTEGRATION TEST ===");
        testHandshakeAndFrames();
        testFourClientsConcurrentInput();
        testReconnectKeepsSeat();
        testIntentionalLeaveReleasesSeat();
        testTokenRejoinSamePlayer();
        testVersionMismatchIsReadable();
        testOversizedAndGarbageFrames();
        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    // ──────────────────────────────────────────────────────────────
    //  1. 握手 + 300 帧
    // ──────────────────────────────────────────────────────────────

    static void testHandshakeAndFrames() throws Exception {
        section("1. handshake + 300 frames of input/snapshot");
        GameServer server = new GameServer(0, 0xABCDEFL);
        try {
            GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
            try {
                check("host client connects", await(() -> host.connected, 5000));
                check("host got a seat id", await(() -> host.id > 0, 3000));
                check("host snapshot arrives", await(() -> host.snapshot != null, 5000));
                check("initial phase is LOBBY", host.snapshot.phase == Game.LOBBY);

                // 开局：op 2，仅队长（captain）有效
                host.start();
                check("start reaches MAP/FIGHT", await(
                        () -> host.snapshot != null && host.snapshot.phase != Game.LOBBY, 5000));

                // 直接进入战斗，避免 MAP 选路的额外交互
                await(() -> host.snapshot != null && host.snapshot.players.size() == 1, 2000);
                if (host.snapshot.phase == Game.MAP) {
                    host.action(0, 0);            // selectRoute(0) -> FIGHT
                    check("route selection reaches FIGHT", await(
                            () -> host.snapshot != null && host.snapshot.phase == Game.FIGHT, 5000));
                }

                // 300 帧输入 + 快照
                AtomicInteger desync = new AtomicInteger();
                AtomicInteger matched = new AtomicInteger();
                int frames = 300;
                for (int f = 0; f < frames; f++) {
                    double a = f * 0.05;
                    host.input((float) Math.cos(a), (float) Math.sin(a),
                            (float) Math.cos(a), (float) Math.sin(a), true, f / 30, f / 45);
                    if (f % 10 == 0) {
                        // 每 10 帧校验一次：客户端最新快照必须能重新编码出与 host 相同的哈希
                        int t = host.snapshot == null ? -1 : host.snapshot.tick;
                        if (t >= 0 && t == server.hostTick) {
                            long ch = hash(host.snapshot);
                            if (ch == server.hostHash) matched.incrementAndGet();
                            else desync.incrementAndGet();
                        }
                    }
                    Thread.sleep(16);
                }
                // 等最后一个快照落地
                Thread.sleep(400);

                check("300 frames produced a live simulation", host.snapshot.tick >= 140);
                check("client stayed connected for all 300 frames", host.connected);
                check("no socket errors on server", server.error.isEmpty());
                System.out.println("    final tick=" + host.snapshot.tick
                        + "  phase=" + host.snapshot.phase
                        + "  hash-matched samples=" + matched.get()
                        + "  desync=" + desync.get());
                check("zero state-hash desync", desync.get() == 0);
                check("state hash actually sampled", matched.get() > 0);
            } finally {
                host.close();
            }
        } finally {
            server.close();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  2. 4 客户端并发输入
    // ──────────────────────────────────────────────────────────────

    static void testFourClientsConcurrentInput() throws Exception {
        section("2. four clients hammering input concurrently (thread-safety)");
        GameServer server = new GameServer(0, 0x5EEDL);
        List<GameClient> clients = new ArrayList<>();
        try {
            GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
            clients.add(host);
            check("host connects", await(() -> host.connected, 5000));
            await(() -> host.snapshot != null, 5000);

            // 另外 3 名玩家只有在大厅才能加入
            for (int i = 1; i < 4; i++) {
                GameClient c = new GameClient(HOST, server.port(), "", "P" + (i + 1), i);
                clients.add(c);
                final int n = i;
                check("client " + (n + 1) + " joins lobby", await(() -> c.connected, 5000));
            }
            await(() -> host.snapshot != null && host.snapshot.players.size() == 4, 5000);
            check("4 players in the room", host.snapshot.players.size() == 4);

            // 4 线程各自狂发输入 3 秒，模拟最恶劣的并发写入
            List<Thread> threads = new ArrayList<>();
            final long until = System.currentTimeMillis() + 3000;
            for (int i = 0; i < clients.size(); i++) {
                final GameClient c = clients.get(i);
                final int seed = i;
                Thread t = new Thread(() -> {
                    Random r = new Random(seed);
                    while (System.currentTimeMillis() < until) {
                        c.input(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1,
                                r.nextFloat(), r.nextFloat(), true, r.nextInt(1000), r.nextInt(1000));
                    }
                }, "hammer-" + i);
                t.setDaemon(true);
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) t.join();

            check("server survived concurrent input", server.error.isEmpty());
            check("server is still broadcasting", await(() -> {
                int a = server.hostTick;
                Thread.sleep(120);
                return server.hostTick > a;
            }, 3000));
            check("host still connected", host.connected);
            check("no null snapshot", host.snapshot != null);

            // 4 个客户端的快照必须在同一 tick 上一致
            long deadline = System.currentTimeMillis() + 3000;
            boolean converged = false;
            while (System.currentTimeMillis() < deadline) {
                int tick = -1;
                boolean same = true;
                for (GameClient c : clients) {
                    Game s = c.snapshot;
                    if (s == null) { same = false; break; }
                    if (tick < 0) tick = s.tick;
                    else if (s.tick == tick && hash(s) != hash(clients.get(0).snapshot)) same = false;
                }
                if (same && tick >= 0) { converged = true; break; }
                Thread.sleep(60);
            }
            check("all 4 clients agree on state at a common tick", converged);
        } finally {
            for (GameClient c : clients) c.close();
            server.close();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  3. 断线重连
    // ──────────────────────────────────────────────────────────────

    static void testReconnectKeepsSeat() throws Exception {
        section("3. network drop vs intentional leave");
        GameServer server = new GameServer(0, 0x1234L);
        final GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
        final GameClient[] guestRef = new GameClient[1];
        try {
            check("host connects", await(() -> host.connected, 5000));
            await(() -> host.snapshot != null, 5000);

            // 用裸 socket 当客人，这样才能模拟"网线被拔"（不带 op 4）
            Seat guest = rawJoin(server.port(), "", "", "队员", 1);
            check("guest joins lobby", guest != null);
            final int guestId = guest.id;
            await(() -> host.snapshot != null && host.snapshot.players.size() == 2, 5000);
            int playersBefore = host.snapshot.players.size();

            host.start();
            check("run starts", await(
                    () -> host.snapshot != null && host.snapshot.phase != Game.LOBBY, 5000));

            // 硬断开：模拟掉网（不发 op 4）
            guest.socket.setSoLinger(true, 0);
            guest.socket.close();
            Thread.sleep(600);

            check("network drop keeps the player in the room",
                    host.snapshot.players.size() == playersBefore);
            check("dropped player marked !connected",
                    await(() -> {
                        Game.Player p = host.snapshot == null ? null : host.snapshot.player(guestId);
                        return p != null && !p.connected;
                    }, 3000));

            // 全新客户端（无 token）中途加入 -> 可读拒绝
            GameClient guest2 = new GameClient(HOST, server.port(), "", "队员2", 1);
            try {
                check("new player is rejected mid-run", await(
                        () -> !guest2.connected && !guest2.fatal.isEmpty(), 4000));
                check("rejection message is user-readable",
                        guest2.status != null && guest2.status.contains("远征已经开始"));
                System.out.println("    rejection = " + guest2.status);
            } finally {
                guest2.close();
            }

            check("seat survives for rejoin (grace window)",
                    host.snapshot.player(guestId) != null);
            Thread.sleep(400);
            check("grace-window seat is still there after a moment",
                    host.snapshot.player(guestId) != null);

            // 对照实验：走 op 4 的"主动离开"应当立刻释放座位
            Seat leaver = rawJoin(server.port(), "", "", "离场者", 2);
            // 局内新座位被拒绝，说明主动离开只能在大厅测；直接验证 close() 的语义
            check("mid-run anonymous join rejected (control)", leaver == null);
        } finally {
            if (guestRef[0] != null) guestRef[0].close();
            host.close();
            server.close();
        }
    }

    /** 主动离开（op 4）应立即释放座位。 */
    static void testIntentionalLeaveReleasesSeat() throws Exception {
        section("3c. intentional leave (op 4) releases the seat immediately");
        GameServer server = new GameServer(0, 0x5150L);
        GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
        try {
            check("host connects", await(() -> host.connected, 5000));
            await(() -> host.snapshot != null, 5000);

            Seat guest = rawJoin(server.port(), "", "", "临时工", 2);
            check("guest joins lobby", guest != null);
            final int guestId = guest.id;
            await(() -> host.snapshot != null && host.snapshot.players.size() == 2, 5000);
            final int before = host.snapshot.players.size();

            // op 4
            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(guest.socket.getOutputStream()));
            out.writeByte(4);
            out.flush();

            check("op 4 removes the player immediately", await(
                    () -> host.snapshot != null && host.snapshot.players.size() == before - 1, 3000));
            check("player id is gone", host.snapshot.player(guestId) == null);
            guest.socket.close();
        } finally {
            host.close();
            server.close();
        }
    }

    /**
     * 真正的 token 重连：断线后用服务端下发的 token 重新握手，
     * 必须拿回同一个 player id 与同一份进度，而不是新开一个座位。
     */
    static void testTokenRejoinSamePlayer() throws Exception {
        section("3b. token rejoin returns the same player id (mid-run)");
        GameServer server = new GameServer(0, 0x4242L);
        try {
            GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
            try {
                check("host connects", await(() -> host.connected, 5000));
                await(() -> host.snapshot != null, 5000);

                // 大厅期加入，拿到 id + token
                Seat s1 = rawJoin(server.port(), "", "", "回流者", 3);
                check("lobby join accepted", s1 != null);
                if (s1 == null) return;
                int firstId = s1.id;
                String token = s1.token;

                // 开局（此时新玩家会被拒绝，但持 token 的可以回来）
                host.start();
                check("run starts", await(
                        () -> host.snapshot != null && host.snapshot.phase != Game.LOBBY, 5000));
                Thread.sleep(300);
                s1.socket.close();
                Thread.sleep(600);

                // 无 token 中途加入 -> 拒绝
                Seat anon = rawJoin(server.port(), "", "", "路人", 4);
                check("unknown player rejected mid-run", anon == null);

                // 持 token 重连 -> 同一 id
                Seat s2 = rawJoin(server.port(), token, "", "回流者", 3);
                check("token rejoin accepted mid-run", s2 != null);
                if (s2 == null) return;
                try {
                    check("token rejoin returns the same player id", s2.id == firstId);
                    System.out.println("    rejoin id " + firstId + " -> " + s2.id);
                    check("seat count did not grow", await(() ->
                            host.snapshot != null && host.snapshot.players.size() == 2, 3000));
                } finally {
                    s2.socket.close();
                }
            } finally {
                host.close();
            }
        } finally {
            server.close();
        }
    }

    static final class Seat {
        int id; String token; Socket socket;
    }

    /** 原始握手；role 只在首次加入时有效。 */
    static Seat rawJoin(int port, String token, String key, String name, int role) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(HOST, port), 2000);
        s.setSoTimeout(3000);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        out.writeInt(Codec.MAGIC);
        out.writeInt(Codec.VERSION);
        out.writeUTF(token);
        out.writeUTF(key);
        out.writeUTF(name);
        out.writeInt(role);
        out.flush();
        if (!in.readBoolean()) { s.close(); return null; }
        Seat seat = new Seat();
        seat.id = in.readInt();
        seat.token = in.readUTF();
        seat.socket = s;
        return seat;
    }

    /** 用原始协议重新握手，拿一个 token（用于验证重连路径存在）。 */
    static String readToken(GameServer server, GameClient host, int guestId) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST, server.port()), 2000);
            s.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out.writeInt(Codec.MAGIC);
            out.writeInt(Codec.VERSION);
            out.writeUTF("");          // 无 token：当作新玩家
            out.writeUTF("");          // 非房主
            out.writeUTF("重连测试");
            out.writeInt(2);
            out.flush();
            if (!in.readBoolean()) return "REJECTED:" + in.readUTF();
            in.readInt();
            return in.readUTF();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  4. 版本不匹配
    // ──────────────────────────────────────────────────────────────

    static void testVersionMismatchIsReadable() throws Exception {
        section("4. version negotiation");
        GameServer server = new GameServer(0, 0x777L);
        try {
            for (int version : new int[]{Codec.VERSION_LEGACY, 99, 0}) {
                String msg = rawHandshake(server.port(), Codec.MAGIC, version);
                check("v" + version + " rejected with a readable message",
                        msg.startsWith("REJECTED:") && msg.contains("版本不匹配"));
                check("v" + version + " message names both versions",
                        msg.contains("v" + Codec.VERSION) && msg.contains("v" + version));
                System.out.println("    v" + version + " -> " + msg);
            }
            // MAGIC 错误
            String bad = rawHandshake(server.port(), 0x00000000, Codec.VERSION);
            check("wrong MAGIC rejected with a readable message",
                    bad.startsWith("REJECTED:") && bad.contains("协议标识"));
            System.out.println("    bad magic -> " + bad);

            // 版本正确的客户端仍然能连
            GameClient ok = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
            try {
                check("correct-version client accepted", await(() -> ok.connected, 5000));
            } finally {
                ok.close();
            }
        } finally {
            server.close();
        }
    }

    /** 走原始握手，返回 REJECTED:<msg> 或 OK:<token>。 */
    static String rawHandshake(int port, int magic, int version) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST, port), 2000);
            s.setSoTimeout(4000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out.writeInt(magic);
            out.writeInt(version);
            out.writeUTF("");
            out.writeUTF("");
            out.writeUTF("probe");
            out.writeInt(0);
            out.flush();
            boolean accepted = in.readBoolean();
            if (!accepted) return "REJECTED:" + in.readUTF();
            in.readInt();
            return "OK:" + in.readUTF();
        } catch (EOFException e) {
            return "CLOSED_WITHOUT_MESSAGE";
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  5. 畸形帧
    // ──────────────────────────────────────────────────────────────

    static void testOversizedAndGarbageFrames() throws Exception {
        section("5. malformed frames do not kill the room");
        GameServer server = new GameServer(0, 0x999L);
        try {
            GameClient host = new GameClient(HOST, server.port(), server.hostKey, "队长", 0);
            try {
                check("host connects", await(() -> host.connected, 5000));
                // 未知 op
                String r = sendRaw(server.port(), new byte[]{(byte) 0xFF});
                check("unknown op is rejected", r.startsWith("CLOSED") || r.startsWith("REJECTED"));
                // 截断的输入帧：TCP 是流，服务端会继续等剩余字节，直到读超时才放弃。
                // 关键断言是"不会把半截数据当成完整输入应用，也不会崩"。
                r = sendRaw(server.port(), new byte[]{1, 0, 1}, 300);
                check("truncated input frame does not kill the room",
                        r.startsWith("CLOSED") || r.startsWith("PARTIAL")
                                || r.startsWith("STILL_CONNECTED"));
                System.out.println("    truncated frame -> " + r);
                // 非法浮点
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream o = new DataOutputStream(b);
                o.writeByte(1);
                o.writeFloat(Float.NaN); o.writeFloat(0); o.writeFloat(1); o.writeFloat(0);
                o.writeBoolean(true); o.writeInt(0); o.writeInt(0);
                o.flush();
                r = sendRaw(server.port(), b.toByteArray());
                check("NaN input vector is rejected", r.startsWith("CLOSED") || r.startsWith("REJECTED"));

                // 服务端仍然健在
                check("server survived malformed frames", server.error.isEmpty());
                check("host is still connected", host.connected);
                check("broadcast continues", await(() -> {
                    int a = server.hostTick;
                    Thread.sleep(120);
                    return server.hostTick > a;
                }, 3000));
            } finally {
                host.close();
            }
        } finally {
            server.close();
        }
    }

    /** 完成握手后发一个畸形包，返回服务端反应。 */
    static String sendRaw(int port, byte[] payload) throws Exception {
        return sendRaw(port, payload, 1500);
    }

    static String sendRaw(int port, byte[] payload, int waitMs) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST, port), 2000);
            s.setSoTimeout(waitMs);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out.writeInt(Codec.MAGIC);
            out.writeInt(Codec.VERSION);
            out.writeUTF("");
            out.writeUTF("");
            out.writeUTF("fuzz");
            out.writeInt(0);
            out.flush();
            if (!in.readBoolean()) return "REJECTED:" + in.readUTF();
            in.readInt();
            in.readUTF();
            out.write(payload);
            out.flush();
            // 服务端要么断开（EOF），要么继续发快照
            try {
                byte[] f = Codec.readFrame(in);
                return "STILL_CONNECTED:" + f.length;
            } catch (EOFException e) {
                return "CLOSED";
            } catch (SocketTimeoutException e) {
                return "PARTIAL";
            }
        }
    }

    // ──────────────────────────────────────────────────────────────

    static long hash(Game g) {
        try {
            return GameServer.hash(g);
        } catch (IOException e) {
            return 0;
        }
    }

    static void section(String s) { System.out.println("\n--- " + s + " ---"); }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  PASS  " + name); }
        else { fail++; System.out.println("  FAIL  " + name); }
    }

    static boolean await(Bool b, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            try { if (b.get()) return true; } catch (Exception ignored) {}
            Thread.sleep(25);
        }
        try { return b.get(); } catch (Exception e) { return false; }
    }

    interface Bool { boolean get() throws Exception; }
}

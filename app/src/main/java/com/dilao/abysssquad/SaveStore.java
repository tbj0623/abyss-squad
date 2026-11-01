package com.dilao.abysssquad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import com.dilao.core.Checkpoint;
import com.dilao.core.Codec;
import com.dilao.core.Content;
import com.dilao.core.Game;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.zip.CRC32;

/**
 * 局内存档槽（单机远征）。
 *
 * <p>只负责"一局未完成的远征"：元进度在 {@link Meta}，两者互不影响。
 * <p>磁盘格式：{@link AtomicFile} + {@link Checkpoint} 的 CRC 信封。
 * 文件为 {@code expedition-v5.sav}；启动时按 v5 → v4 → v3 顺序尝试读取，
 * 兼容旧档（{@code Checkpoint.unpack} 内部兼容旧 Codec 版本）。
 * <p>所有写操作在单线程池里执行，主线程不阻塞；切后台时用 {@link #saveNow(Game)}。
 */
final class SaveStore {

    // 文件名（契约：v5）
    static final String FILE_V5 = "expedition-v5.sav";
    static final String FILE_V4 = "expedition-v4.sav";
    static final String FILE_V3 = "expedition-v3.sav";

    static final String MIGRATION_PREFS = "save-migration";
    static final String KEY_MIGRATED_V5 = "v5-started";

    /** 存档字节上限：与 {@code Checkpoint.unpack} 的信封校验保持一致。 */
    private static final int MAX_FILE = Codec.LIMIT + 24;

    private final AtomicFile file;
    private final File dir;
    private final SharedPreferences migration;
    private final ExecutorService io;
    /** 最近一次解包成功的内存快照，避免 UI 反复读盘。 */
    private volatile byte[] last;
    /** 给 UI 展示的简短错误说明；正常时为空串。 */
    volatile String error = "";

    SaveStore(Context context) {
        File base = context.getFilesDir();
        dir = base != null ? base : context.getCacheDir();
        migration = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
        file = new AtomicFile(new File(dir, FILE_V5));
        io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "abyss-save-io");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
        loadFromDisk();
    }

    // ══════════════════════════════════════════════════════════════
    //  读取 / 迁移
    // ══════════════════════════════════════════════════════════════

    /**
     * 依次尝试 v5 → v4 → v3。v5 存在但损坏时不静默回退到更老的档，
     * 以免把玩家更旧的进度当成"最近进度"呈现；只报明确错误。
     */
    private void loadFromDisk() {
        error = "";
        File v5 = new File(dir, FILE_V5);
        if (v5.exists()) {
            byte[] data = readFile(v5, "v5", true);
            if (data == null) return;   // error 已由 readFile 设置
            last = data;
            return;
        }
        // v5 不存在：按 v4 → v3 顺序把旧档升级到 v5。
        String[] legacy = {FILE_V4, FILE_V3};
        for (String name : legacy) {
            File f = new File(dir, name);
            if (!f.exists()) continue;
            byte[] data = readFile(f, name, true);
            if (data == null) continue;          // 该代损坏，继续尝试更老的一代
            Game decoded = decodeAny(data);
            if (decoded == null) {
                // 旧信封自校验通过，但核心解码器不认这个 Codec 版本：保留文件，报明确错误。
                error = "旧版本存档（" + name + "）无法迁移：当前游戏只兼容 v5 局内格式；"
                        + "文件已保留，可开始新远征";
                return;
            }
            last = data;
            // 升级：重新以 v5 信封/格式落盘，成功后删除旧档。
            if (persist(repack(decoded))) {
                try {
                    if (f.delete()) migration.edit().putBoolean(KEY_MIGRATED_V5, true).apply();
                } catch (Throwable ignored) {}
            }
            return;
        }
    }

    /**
     * 读取并校验一个存档文件；失败时设置具体错误并返回 null。
     * <p>同时识别两代信封：v5（魔数 0x41535633）与 v2/v3/v4（魔数 0x41535632）。
     * 旧信封只在"升级"路径上被 {@link #readLegacy} 解析，v5 槽位只认 v5。
     */
    private byte[] readFile(File f, String tag) {
        return readFile(f, tag, false);
    }

    private byte[] readFile(File f, String tag, boolean allowLegacy) {
        if (!f.isFile() || !f.canRead()) {
            error = "存档无法读取（" + tag + "）：文件权限不足或已被移动";
            return null;
        }
        long length = f.length();
        if (length <= 0) {
            error = "存档为空（" + tag + "）：可能在写入过程中被中断，已保留原文件";
            return null;
        }
        if (length > MAX_FILE) {
            error = "存档过大（" + tag + "）：" + length + " 字节，超过上限 " + MAX_FILE;
            return null;
        }
        byte[] data;
        try {
            AtomicFile source = new AtomicFile(f);
            data = source.readFully();
        } catch (IOException e) {
            error = "存档读取失败（" + tag + "）：" + reason(e);
            return null;
        } catch (Throwable t) {
            error = "存档读取失败（" + tag + "）：存储不可访问";
            return null;
        }
        if (data == null || data.length == 0) {
            error = "存档为空（" + tag + "）：可能在写入过程中被中断";
            return null;
        }
        try {
            // 用核心解码器做完整校验：CRC、版本、字段范围一次过。
            if (allowLegacy && envelopeOf(data) == ENVELOPE_LEGACY) unpackLegacy(data);
            else Checkpoint.unpack(data);
        } catch (IOException e) {
            error = describeUnpackFailure(e, tag);
            return null;
        } catch (Throwable t) {
            error = "存档校验失败（" + tag + "）：数据格式无法解析；原文件已保留";
            return null;
        }
        return data;
    }

    // ── 旧档信封 ────────────────────────────────────────────────
    private static final int ENVELOPE_V5 = 0x41535633;
    private static final int ENVELOPE_LEGACY = 0x41535632;

    private static int envelopeOf(byte[] data) {
        if (data == null || data.length < 4) return 0;
        return (data[0] & 0xFF) << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | (data[3] & 0xFF);
    }

    /**
     * v2/v3/v4 信封（MAGIC 0x41535632，16 字节头）的自校验。
     * <p>只做长度 + CRC 校验，不调用 {@code Checkpoint.unpack}（它只认 v5 魔数）。
     * 真正的 Codec 解码在 {@link #load()} 里做，失败时会有明确错误。
     */
    private static void unpackLegacy(byte[] data) throws IOException {
        if (data.length > MAX_FILE) throw new IOException("save too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != ENVELOPE_LEGACY) throw new IOException("unknown save format");
        int length = in.readInt();
        if (length < 0 || length > Codec.LIMIT || length != data.length - 16)
            throw new IOException("truncated save");
        long checksum = in.readLong();
        byte[] state = new byte[length];
        in.readFully(state);
        CRC32 crc = new CRC32();
        crc.update(state);
        if (crc.getValue() != checksum) throw new IOException("save checksum failed");
    }

    /** 把核心层抛出的英文异常映射成能直接给玩家看的中文说明。 */
    private static String describeUnpackFailure(IOException e, String tag) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.US);
        if (m.contains("checksum")) return "存档校验失败（" + tag + "）：CRC 不匹配，文件可能被截断；原文件已保留";
        if (m.contains("truncated")) return "存档不完整（" + tag + "）：长度字段与实际字节数不一致；原文件已保留";
        if (m.contains("unknown")) return "存档版本不兼容（" + tag + "）：不是本游戏的存档或版本过旧";
        if (m.contains("version")) return "存档版本不兼容（" + tag + "）：请更新游戏后再继续";
        if (m.contains("too large")) return "存档过大（" + tag + "）：内容超出安全上限";
        if (m.contains("solo checkpoint")) return "存档内容非法（" + tag + "）：不是有效的单机远征存档";
        return "存档校验失败（" + tag + "）：" + e.getMessage() + "；原文件已保留";
    }

    /** 是否有可继续的远征。 */
    boolean hasSave() { return last != null; }

    /** 兼容旧调用点。 */
    boolean available() { return hasSave(); }

    /** 反序列化为可玩的战局；失败返回 null 并保留具体错误。 */
    Game load() {
        byte[] data = last;
        if (data == null) return null;
        Game g = decodeAny(data);
        if (g != null) {
            error = "";
            return g;
        }
        last = null;
        return null;
    }

    /** 按信封魔数选择解码器；任何异常都翻译成 {@link #error} 并返回 null。 */
    private Game decodeAny(byte[] data) {
        try {
            if (envelopeOf(data) == ENVELOPE_V5) return Checkpoint.unpack(data);
            unpackLegacy(data);                       // 先做旧信封的 CRC 自校验
            return Codec.decode(stateOf(data));       // 再由核心 Codec 解码局内状态
        } catch (IOException e) {
            error = describeUnpackFailure(e, "load") + "；原文件已保留，可开始新远征";
            return null;
        } catch (Throwable t) {
            error = "存档无法恢复：数据格式无法解析；原文件已保留";
            return null;
        }
    }

    /** 从已校验的信封里取出状态字节。 */
    private static byte[] stateOf(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        in.readInt();
        int length = in.readInt();
        in.readLong();
        byte[] state = new byte[length];
        in.readFully(state);
        return state;
    }

    /** 把任意已解码的战局重新打包成 v5 信封。 */
    private static byte[] repack(Game g) {
        try {
            return Checkpoint.pack(g);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 存档摘要（不需要完整解码也能展示"第几站"）。
     *
     * <p><b>纯函数</b>：只读 {@link #last}，不触碰 {@code last} / {@code error}。
     * 旧实现转调 {@link #load()}，而 {@code load()} 在解码失败时会把 {@code last} 置 null——
     * 于是仅仅为了画一行文案，一份损坏的存档就被从内存里抹掉了（后续 {@code hasSave()}
     * 变成 false，玩家看到"没有可继续的远征"，文件却还在盘上）。现在解码只在本地变量里做。
     */
    String describe() {
        byte[] data = last;
        if (data == null) return "";
        Game g;
        try {
            if (envelopeOf(data) == ENVELOPE_V5) {
                g = Checkpoint.unpack(data);
            } else {
                unpackLegacy(data);             // 旧信封先做 CRC 自校验，和 load() 一致
                g = Codec.decode(stateOf(data));
            }
        } catch (Throwable t) {
            // 与旧实现的可观测结果一致（load() 失败时旧代码也返回空串），
            // 区别只在于：这里不再顺手把 last 抹成 null。
            return "";
        }
        if (g == null) return "";
        try {
            Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
            int role = p == null ? 0 : Math.max(0, Math.min(Content.ROLE_COUNT - 1, p.role));
            int mode = Math.max(0, Math.min(3, g.mode));
            return Content.ROLES[role] + " · " + Content.MODE_NAMES[mode]
                    + " · 第 " + (g.roomsVisited + 1) + " 站";
        } catch (Throwable t) {
            return "未知进度";
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  写入
    // ══════════════════════════════════════════════════════════════

    /** 异步保存当前战局（主线程不阻塞）。非单机或已结束的战局会被忽略。 */
    void save(Game game) {
        if (!isSavable(game)) return;
        final byte[] data;
        try {
            data = Checkpoint.pack(game);
        } catch (IOException e) {
            error = "当前进度无法打包：" + reason(e);
            return;
        } catch (Throwable t) {
            error = "当前进度无法保存：数据异常";
            return;
        }
        last = data;
        try {
            io.execute(new Runnable() {
                @Override public void run() { persist(data); }
            });
        } catch (RejectedExecutionException e) {
            persist(data);   // 已 close，尽量同步写一次
        } catch (Throwable t) {
            error = "存档写入失败：无法调度写盘任务";
        }
    }

    /** 同步保存（App 切后台、Activity 即将销毁时使用）。返回是否写入成功。 */
    boolean saveNow(Game game) {
        if (!isSavable(game)) return false;
        final byte[] data;
        try {
            data = Checkpoint.pack(game);
        } catch (IOException e) {
            error = "当前进度无法打包：" + reason(e);
            return false;
        } catch (Throwable t) {
            error = "当前进度无法保存：数据异常";
            return false;
        }
        last = data;
        return persist(data);
    }

    private static boolean isSavable(Game game) {
        return game != null
                && game.soloRun
                && game.phase != Game.LOBBY
                && game.phase != Game.WON
                && game.phase != Game.LOST;
    }

    /** 真正落盘。调用方保证 data 非空。返回是否成功。 */
    private boolean persist(byte[] data) {
        if (data == null || data.length == 0) {
            error = "存档写入失败：没有可写入的数据";
            return false;
        }
        if (data.length > MAX_FILE) {
            error = "存档写入失败：数据 " + data.length + " 字节超过上限 " + MAX_FILE;
            return false;
        }
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data);
            file.finishWrite(out);
            try {
                migration.edit().putBoolean(KEY_MIGRATED_V5, true).apply();
            } catch (Throwable ignored) {}
            error = "";
            return true;
        } catch (IOException e) {
            if (out != null) {
                try {
                    file.failWrite(out);
                } catch (Throwable ignored) {}
            }
            error = describeWriteFailure(e);
            return false;
        } catch (Throwable t) {
            if (out != null) {
                try {
                    file.failWrite(out);
                } catch (Throwable ignored) {}
            }
            error = "存档写入失败：存储不可写";
            return false;
        }
    }

    private static String describeWriteFailure(IOException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.US);
        if (m.contains("no space") || m.contains("enospc")) return "存档写入失败：设备存储空间不足";
        if (m.contains("permission") || m.contains("eacces")) return "存档写入失败：没有存储权限";
        if (m.contains("read-only") || m.contains("erofs")) return "存档写入失败：存储为只读状态";
        if (m.contains("too large")) return "存档写入失败：数据超过上限";
        return "存档写入失败：请检查可用空间（" + (e.getMessage() == null ? "IO 错误" : e.getMessage()) + "）";
    }

    private static String reason(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // ══════════════════════════════════════════════════════════════
    //  清理 / 生命周期
    // ══════════════════════════════════════════════════════════════

    /** 删除当前存档（一局结束或玩家主动放弃）。异步、不阻塞主线程。 */
    void clear() {
        last = null;
        error = "";
        try {
            migration.edit().putBoolean(KEY_MIGRATED_V5, true).apply();
        } catch (Throwable ignored) {}
        try {
            io.execute(new Runnable() {
                @Override public void run() { deleteQuietly(); }
            });
        } catch (RejectedExecutionException e) {
            deleteQuietly();
        } catch (Throwable ignored) {}
    }

    /**
     * 设置页「重置存档」专用：同步删掉局内存档槽里的<b>全部</b>文件，等待在途写入完成，
     * 并把内存状态一并归零。返回是否全部删干净。
     *
     * <p>与 {@link #clear()} 的区别（重置必须和"本局结束"区分开）：
     * <ul>
     *   <li><b>同步</b>：重置后玩家会立刻回到菜单点「继续」，异步删除会留下一个
     *       能被读回来的窗口；这里先 flush 掉在途写盘，再删，再 flush 一次兜住
     *       {@code clear()} 已排队的删除任务。</li>
     *   <li><b>全量</b>：不只删 v5，还删 v4/v3 旧档与 {@link AtomicFile} 的
     *       {@code .bak} / {@code .new} 临时文件。旧实现只删 v5 基文件，
     *       重置后重启 {@link #loadFromDisk()} 会把 v4/v3 重新迁移成 v5——
     *       玩家重置完发现存档又回来了。</li>
     *   <li><b>刷新 error</b>：删不掉时给玩家一句可读的原因，而不是静默成功。</li>
     * </ul>
     *
     * @return true 表示磁盘与内存都已清空；false 表示仍有文件残留（原因见 {@link #error}）
     */
    public boolean clearSavedRun() {
        // 1) 等在途写盘落地：否则一个排队中的 persist() 会在我们的删除之后又写出文件。
        flush();
        last = null;

        StringBuilder leftovers = new StringBuilder();
        for (File f : saveFiles()) {
            deleteFile(f, leftovers);       // 失败原因由 deleteFile 写进 leftovers
        }
        // 2) clear() 可能刚好排了一个删除任务在队列里；再 flush 一次让它跑完，
        //    免得它在我们返回之后才执行（此时文件已经不存在，删除是幂等的，只是求稳）。
        flush();

        boolean ok = leftovers.length() == 0;
        if (ok) {
            // 迁移标记置位：重置后不应再尝试从旧档恢复任何东西。
            try {
                migration.edit().putBoolean(KEY_MIGRATED_V5, true).commit();
            } catch (Throwable ignored) {}
            error = "";
        } else {
            error = "局内存档删除失败：" + leftovers;
        }
        try {
            // 桩实现（JVM 单测）里 Log 会抛 Stub!，这里不能让它破坏"删干净"的返回值语义
            android.util.Log.i("AbyssSave", "clearSavedRun ok=" + ok + " error=\"" + error + "\"");
        } catch (Throwable ignored) {}
        return ok;
    }

    /**
     * 存档槽里可能存在的全部文件：v5 基文件 + AtomicFile 的两类临时件，
     * 以及 v4/v3 两代旧档（含各自的临时件）。
     */
    private File[] saveFiles() {
        java.util.List<File> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        // 以 v5 的基文件所在目录为准（就是构造时选定的 dir；dir 理论上恒非 null，
        // 但基文件路径不依赖它，用它派生更稳）。
        File base = file.getBaseFile();
        File baseDir = base != null ? base.getParentFile() : dir;
        String[] names = {FILE_V5, FILE_V4, FILE_V3};
        for (String name : names) {
            File f = new File(baseDir, name);
            if (seen.add(f.getAbsolutePath())) out.add(f);
            // AtomicFile 的伴生文件：.bak 是 finishWrite 失败后留下的旧内容，.new 是写了一半的
            File bak = new File(f.getPath() + ".bak");
            if (seen.add(bak.getAbsolutePath())) out.add(bak);
            File tmp = new File(f.getPath() + ".new");
            if (seen.add(tmp.getAbsolutePath())) out.add(tmp);
        }
        if (base != null && seen.add(base.getAbsolutePath())) out.add(base);
        return out.toArray(new File[0]);
    }

    /**
     * 删一个文件；不存在视为成功。失败时把可读原因追加到 {@code leftovers} 并返回 false。
     */
    private static boolean deleteFile(File f, StringBuilder leftovers) {
        try {
            if (!f.exists()) return true;
            if (f.delete()) return true;
            if (!f.exists()) return true;      // 并发删除：已经没了也算成功
            if (leftovers.length() > 0) leftovers.append("、");
            leftovers.append(f.getName()).append("（无法删除）");
            return false;
        } catch (Throwable t) {
            if (leftovers.length() > 0) leftovers.append("、");
            leftovers.append(f.getName()).append("（").append(reason(t)).append("）");
            return false;
        }
    }

    private void deleteQuietly() {
        try {
            file.delete();
        } catch (Throwable ignored) {}
    }

    /** 等待队列中的写盘完成（测试用，最多 2 秒）。 */
    void flush() {
        try {
            io.submit(new Runnable() {
                @Override public void run() {}
            }).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {}
    }

    void close() {
        try {
            io.shutdown();
        } catch (Throwable ignored) {}
    }

    /** 当前存档文件（测试/诊断用）。 */
    File file() { return file.getBaseFile(); }
}

package com.dilao.core;

import java.io.*;
import java.util.zip.CRC32;

/** 带校验和的存档信封。写入必须原子；内容不含任何凭据。 */
public final class Checkpoint {
    private Checkpoint() {}

    private static final int MAGIC = 0x41535633;   // v5 信封

    public static byte[] pack(Game g) throws IOException {
        if (!g.soloRun || g.phase == Game.LOBBY || g.phase == Game.WON || g.phase == Game.LOST)
            throw new IOException("not an active solo run");
        byte[] state = Codec.encode(g);
        CRC32 crc = new CRC32();
        crc.update(state);
        ByteArrayOutputStream b = new ByteArrayOutputStream(state.length + 24);
        DataOutputStream out = new DataOutputStream(b);
        out.writeInt(MAGIC);
        out.writeInt(state.length);
        out.writeLong(crc.getValue());
        out.write(state);
        out.flush();
        return b.toByteArray();
    }

    public static Game unpack(byte[] data) throws IOException {
        if (data == null || data.length > Codec.LIMIT + 24) throw new IOException("save too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != MAGIC) throw new IOException("unknown save format");
        int length = in.readInt();
        if (length < 0 || length > Codec.LIMIT || length != data.length - 16)
            throw new IOException("truncated save");
        long checksum = in.readLong();
        byte[] state = new byte[length];
        in.readFully(state);
        CRC32 crc = new CRC32();
        crc.update(state);
        if (crc.getValue() != checksum) throw new IOException("save checksum failed");
        Game g = Codec.decode(state);
        if (!g.soloRun || g.players.size() != 1
                || g.phase == Game.WON || g.phase == Game.LOST)
            throw new IOException("invalid solo checkpoint");
        return g;
    }

    /** 存档摘要，用于"继续远征"卡片展示，不需要完整解码。 */
    public static String describe(byte[] data) {
        try {
            Game g = unpack(data);
            Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
            String role = p == null ? "探险者" : Content.ROLES[Math.max(0, Math.min(Content.ROLE_COUNT - 1, p.role))];
            String mode = Content.MODE_NAMES[Math.max(0, Math.min(3, g.mode))];
            return role + " · " + mode + " · 第 " + (g.roomsVisited + 1) + " 站";
        } catch (Exception e) {
            return "未知进度";
        }
    }
}

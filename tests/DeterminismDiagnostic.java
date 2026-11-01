import com.dilao.core.*;
import java.util.Arrays;
import java.io.IOException;

/** Reproduce the first frame where checkpoint continuation diverges. */
public final class DeterminismDiagnostic {
    public static void main(String[] args) throws Exception {
        Game a = CoreTests.scriptedRun(424242L, 5, 1200);
        for (Game.Player p : a.players) a.recompute(p);
        a.restored();
        Game b = Checkpoint.unpack(Checkpoint.pack(a));
        for (java.lang.reflect.Field f : Game.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object va = f.get(a), vb = f.get(b);
            if (!va.equals(vb)) System.out.println("BEFORE hidden field " + f.getName() + "=" + va + "/" + vb);
        }
        Game.Player pa = a.players.get(0), pb = b.players.get(0);
        for (java.lang.reflect.Field f : Game.Player.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object va = f.get(pa), vb = f.get(pb);
            if (!va.equals(vb)) System.out.println("BEFORE PLAYER field " + f.getName() + "=" + va + "/" + vb);
        }
        if (!Arrays.equals(a.shopUsed,b.shopUsed)) System.out.println("BEFORE shopUsed=" + Arrays.toString(a.shopUsed) + "/" + Arrays.toString(b.shopUsed));
        for (int i = 0; i < 1200; i++) {
            CoreTests.scriptedBot(a, i);
            CoreTests.scriptedBot(b, i);
            String diff = CoreTests.diff(a, b);
            byte[] aa = CoreTests.bytes(a);
            byte[] bb = CoreTests.bytes(b);
            if (diff != null || !Arrays.equals(aa, bb)) {
                System.out.printf("first difference at i=%d phase=%d/%d tick=%d/%d diff=%s byteOffset=%d bytes=%d/%d%n",
                        i, a.phase, b.phase, a.tick, b.tick, diff, Arrays.mismatch(aa,bb), aa.length, bb.length);
                System.out.printf("RNG=%d/%d kills=%d/%d enemies=%d/%d shots=%d/%d effects=%d/%d spawned=%d/%d coins=%d/%d%n",
                        a.random.state(), b.random.state(), a.kills,b.kills,a.enemies.size(),b.enemies.size(),
                        a.shots.size(),b.shots.size(),a.effects.size(),b.effects.size(),a.spawned,b.spawned,a.coins,b.coins);
                for (java.lang.reflect.Field field : Game.class.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()) continue;
                    field.setAccessible(true);
                    Object va = field.get(a), vb = field.get(b);
                    if (!va.equals(vb)) System.out.println("AFTER GAME " + field.getName() + "=" + va + "/" + vb);
                }
                for (java.lang.reflect.Field field : Game.Player.class.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()) continue;
                    field.setAccessible(true);
                    Object va = field.get(a.players.get(0)), vb = field.get(b.players.get(0));
                    if (!va.equals(vb)) System.out.println("AFTER PLAYER " + field.getName() + "=" + va + "/" + vb);
                }
                for (int j=0;j<Math.min(Math.min(a.shots.size(),b.shots.size()),4);j++) {
                    Game.Shot sa=a.shots.get(j),sb=b.shots.get(j);
                    System.out.printf("SHOT %d ids=%d/%d x=%f/%f y=%f/%f life=%f/%f hit=%d/%d%n",j,sa.id,sb.id,
                        sa.x,sb.x,sa.y,sb.y,sa.life,sb.life,sa.hitCount,sb.hitCount);
                }
                listDiff("enemies", a.enemies, b.enemies);
                listDiff("shots", a.shots, b.shots);
                listDiff("gems", a.gems, b.gems);
                listDiff("effects", a.effects, b.effects);
                listDiff("blocks", a.blocks, b.blocks);
                listDiff("props", a.props, b.props);
                listDiff("hazards", a.hazards, b.hazards);
                listDiff("turrets", a.turrets, b.turrets);
                listDiff("minions", a.minions, b.minions);
                throw new AssertionError("Checkpoint continuation diverged; details printed above");
            }
        }
        System.out.println("DETERMINISTIC");
    }

    static void listDiff(String label, java.util.List<?> a, java.util.List<?> b) throws Exception {
        for (int j=0;j<Math.min(a.size(),b.size());j++) {
            Object x=a.get(j), y=b.get(j);
            for (java.lang.reflect.Field field : x.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object va=field.get(x), vb=field.get(y);
                if (!java.util.Objects.deepEquals(va,vb)) {
                    System.out.println("AFTER " + label + "["+j+"]."+field.getName()+"="+va+"/"+vb);
                    return;
                }
            }
        }
    }
}

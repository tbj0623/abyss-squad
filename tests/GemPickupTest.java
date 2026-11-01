import com.dilao.core.Evolutions;
import com.dilao.core.Game;

import java.lang.reflect.Method;

/**
 * 回归：拾取经验 -> 升级回血 -> M14-7 愈光之环杀敌 -> 新宝石入队。
 * 旧版 updateGems 在 Iterator.remove() 处并发修改崩溃。
 */
public final class GemPickupTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(Evolutions.mechanic(7) == 14, "测试前提：进化7应是愈光之环");
        Game g = new Game(440617L);
        Game.Player p = g.addPlayer(1, "治疗者", 2);
        p.x = 560f;
        p.y = 310f;
        p.hp = Math.max(1f, p.maxHp - 35f);
        p.evolved[7] = true;
        p.mechanicMask |= 1 << 14;
        p.healBurstCd = 0f;

        Game.Enemy enemy = new Game.Enemy();
        enemy.id = g.allocateId();
        enemy.type = 0;
        enemy.x = p.x + 22f;
        enemy.y = p.y;
        enemy.hp = enemy.maxHp = 1f;
        enemy.r = 10f;
        g.enemies.add(enemy);

        Game.Gem levelGem = new Game.Gem();
        levelGem.id = g.allocateId();
        levelGem.x = p.x;
        levelGem.y = p.y;
        levelGem.value = g.xpGoal + 1f;
        g.gems.add(levelGem);

        Game.Gem smallGem = new Game.Gem();
        smallGem.id = g.allocateId();
        smallGem.x = p.x;
        smallGem.y = p.y;
        smallGem.value = 2f;
        g.gems.add(smallGem);

        Method update = Game.class.getDeclaredMethod("updateGems", float.class);
        update.setAccessible(true);
        update.invoke(g, 1f / 60f);

        check(g.level >= 2, "拾取的经验应正常升级");
        check(enemy.hp <= 0f, "升级治疗应该触发愈光光爆击杀敌人");
        check(g.kills >= 1, "光爆击杀应正常结算");
        check(!g.gems.contains(levelGem) && !g.gems.contains(smallGem),
                "本帧两颗旧宝石都应被拾取");
        check(!g.gems.isEmpty(), "光爆击杀后新掉落的宝石不得被旧迭代器吞掉");
        System.out.println("PASS GemPickupTest: heal-burst spawned gem without ConcurrentModificationException");
    }
}

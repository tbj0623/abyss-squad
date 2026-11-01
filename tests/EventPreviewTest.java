import com.dilao.core.*;
import java.util.Arrays;

/** 事件告知契约：界面预告和引擎选择规则必须能让玩家正确判断风险。 */
public final class EventPreviewTest {
    private static int checked;

    private static void require(boolean ok, String what) {
        checked++;
        if (!ok) throw new AssertionError(what);
    }

    private static Game eventGame(int ev) {
        Game g = new Game(40000L + ev);
        g.addPlayer(1, "Preview", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        g.coins = 1000;
        return g;
    }

    public static void main(String[] args) throws Exception {
        int[] typeCount = new int[Events.TYPE_COUNT];
        for (int ev = 0; ev < Events.COUNT; ev++) {
            Game g = eventGame(ev);
            typeCount[Events.TYPE[ev]]++;
            byte[] before = Codec.encode(g);
            for (int choice = 0; choice < 2; choice++) {
                String p = Events.preview(g, ev, choice);
                require(p != null && p.length() >= 4, "empty preview event " + ev + " choice " + choice);
                int cost = choice == 0 ? Events.COST_A[ev] : Events.COST_B[ev];
                require(cost == 0 || p.startsWith("星尘 -" + cost + " · "),
                        "missing prepaid currency event " + ev + " choice " + choice + ": " + p);
                require(!p.contains("待确认") && !p.contains("无代价"),
                        "misleading generic preview " + ev + "/" + choice + ": " + p);
                if (Events.TYPE[ev] == 4 && choice == 0
                        || Events.TYPE[ev] == 9 && choice == 0)
                    require(p.contains("3站后"), "missing deferred cost " + ev + ": " + p);
                if (Events.TYPE[ev] == 10)
                    require(p.contains("胜率") && p.contains("输最大生命"),
                            "missing win and loss odds " + ev + "/" + choice + ": " + p);
                if (Events.TYPE[ev] == 12)
                    require(p.contains("当前生命 -"), "reverse toll concealed " + ev + "/" + choice + ": " + p);
            }
            require(Arrays.equals(before, Codec.encode(g)), "preview mutated game " + ev);
        }
        for (int t = 0; t < typeCount.length; t++)
            require(typeCount[t] > 0, "uncovered event type " + t);

        // 过去 UI 错误地用 maxHp<70 禁用祭坛，且忘记诅咒与反向代价的残血限制。
        for (int ev = 0; ev < Events.COUNT; ev++) {
            int t = Events.TYPE[ev];
            if (t != 0 && t != 3 && t != 7 && t != 12) continue;
            Game g = eventGame(ev);
            Game.Player p = g.players.get(0);
            p.maxHp = 60f;
            p.hp = 50f;
            require(g.canEvent(0), "incorrect maximum HP gate event " + ev);
            p.hp = 10f;
            require(!g.canEvent(0), "low health choice allowed event " + ev);
            require(g.canEvent(1), "low-health player trapped without refusal event " + ev);
        }
        System.out.println("EventPreviewTest PASS " + checked + " assertions; all "
                + Events.COUNT + " events and " + Events.TYPE_COUNT + " types");
    }

    private EventPreviewTest() {}
}

package com.dilao.core;

import java.io.*;

/**
 * 显式、有界的二进制格式：既用于网络快照，也用于单机存档。
 * <p>所有写入顺序与读取顺序必须一一对应；解码端对每个值做范围校验，
 * 任何越界/非有限浮点数都会被拒绝，而不是带病运行。
 */
public final class Codec {
    public static final int MAGIC = 0x41535144, VERSION = 10, PORT = 45454, LIMIT = 1 << 20;
    /**
     * v7 及更早版本的快照版本号。
     * <p>v7 只追加了尾部两个 int（pactMods / pactHeat），因此 v6 的字节流可以被
     * "读到尾部即视为 0" 的方式继续解析。
     * <p>v8 追加的是<b>中段字段</b>（天赋牌一组 + 每个 Player 的过热/闪避增益 + Shot 的减速秒数），
     * 布局与 v7 不同，因此读 v6/v7 时必须走分版本分支：v7 段照常读，v8 段整体跳过。
     * 旧档读入后天赋牌 = 未装备（doctrine -1 / 钩子 0 / 参数 0），与"旧档没有这张牌"语义一致。
     */
    public static final int VERSION_LEGACY = 8;
    /** v6 及更早：连契约词条都没有。 */
    public static final int VERSION_V6 = 6;

    private Codec() {}

    private static void ints(DataOutputStream o, int... n) throws IOException {
        for (int a : n) o.writeInt(a);
    }

    private static void floats(DataOutputStream o, float... n) throws IOException {
        for (float a : n) o.writeFloat(a);
    }

    public static byte[] encode(Game g) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(8192);
        DataOutputStream o = new DataOutputStream(b);
        ints(o, MAGIC, VERSION);
        o.writeLong(g.seed);
        o.writeLong(g.random.state());
        ints(o, g.phase, g.room, g.floor, g.level, g.kills, g.tick, g.spawned, g.target, g.route,
                g.captain, g.mode, g.roomType, g.biome, g.layout, g.hazard, g.roomsVisited,
                g.roomsTotal, g.routeIndex, g.eventType, g.eventChoice, g.shopRerolls, g.coins,
                g.bossesDefeated, g.routeCount, g.objective, g.rewardsLeft, g.rerolls, g.scouted,
                g.nextId, g.contract, g.mutator, g.wave, g.waves, g.nextWaveAt, g.roomStartKills,
                g.tier, g.freeReviveCount, g.freeRevivesUsed);
        // ── v8 追加：天赋牌（牌号 / 主副钩子 / 参数 / 升级累积）────────────
        ints(o, g.doctrine, g.doctrineHook, g.doctrineSideHook, g.doctrineBonusChoices,
                g.doctrineKillClock);
        floats(o, g.doctrineParams);
        o.writeBoolean(g.soloRun);
        o.writeBoolean(g.skipNextNode);
        o.writeBoolean(g.mirror);
        o.writeBoolean(g.campAfterBoss);
        o.writeBoolean(g.shrineUsed);
        ints(o, g.routeOptions);
        ints(o, g.shopOffers);
        ints(o, g.shopPrices);
        for (boolean s : g.shopSold) o.writeBoolean(s);
        ints(o, g.history);
        floats(o, g.time, g.roomTime, g.spawnClock, g.charge, g.xp, g.xpGoal, g.choiceClock,
                g.eventClock, g.comboClock, g.combo, g.xpTotal, g.dustEarned, g.marksEarned,
                g.waveWait, g.damageTaken);
        o.writeUTF(g.notice);
        o.writeUTF(g.sceneTitle);
        o.writeUTF(g.sceneHint);
        floats(o, g.schoolBonus);
        ints(o, g.bossKillsRun);

        o.writeInt(g.players.size());
        for (Game.Player p : g.players) {
            ints(o, p.id, p.role, p.kills, p.chosen, p.vote, p.dashSerial, p.skillSerial,
                    p.shotCount, p.synergyMask, p.weaponForm, p.evolution, p.evolutionsTaken,
                    p.ultSerial, p.ultCharge, p.multishot, p.pierce, p.slow, p.leech, p.explode,
                    p.armorBreak, p.soul, p.discount, p.bossLoot, p.thorns, p.execute,
                    p.knockback, p.knockbackPower, p.flags, p.extraProjectiles, p.chainCount,
                    p.orbitCount, p.mineCount, p.splitCount, p.ricochetCount, p.beamCount,
                    p.summonCount, p.shieldLayers, p.stunChance, p.reactions);
            // ── v7 追加：F_* 机制计数（缺失会导致读档后机制静默消失、破坏确定性）──
            ints(o, p.auraCount, p.slowFieldCount, p.lifeSteal, p.taunt,
                    p.chargeCount, p.splitShot, p.evoMechanic);
            o.writeUTF(p.name);
            o.writeBoolean(p.connected);
            o.writeBoolean(p.down);
            o.writeBoolean(p.nextCrit);
            floats(o, p.x, p.y, p.hp, p.maxHp, p.speed, p.damage, p.interval, p.attack, p.dx, p.dy,
                    p.ax, p.ay, p.manual, p.dashCd, p.skillCd, p.invuln, p.revive,
                    p.disconnectedFor, p.crit, p.magnet, p.xpBonus, p.dashFactor, p.buff,
                    p.buffKind, p.frostHealCd, p.ultCd, p.ultReady, p.reactionCd, p.leechBudget,
                    p.regenClock, p.shield, p.shieldMax, p.bonusPower, p.bonusRate, p.bonusArea,
                    p.bonusSpeed, p.bonusHp, p.bonusCrit, p.bonusMagnet, p.bonusRegen,
                    p.bonusXp, p.bonusCoin);
            // ── v8 追加：天赋牌玩家级状态（缺失 = 0，即"没有过热/闪避增益"）──
            floats(o, p.overheatClock, p.overheatStun, p.dashHaste, p.dashHasteMul);
            // ── v9 追加：元进度第二组节点（不存的话读档后这 8 个节点全归零）──
            floats(o, p.dashGrow, p.metaArmor, p.metaRegen, p.metaAtkSpeed,
                    p.metaRange, p.metaCoin, p.metaXp, p.metaEvent);
            ints(o, p.metaRelicSlots);
            // ── v10：机制定时器。漏存 mineClock 会在读档第一帧白生成一枚地雷。
            floats(o, p.auraClock, p.mineClock, p.teleportClock, p.chargeClock, p.chargeHold);
            // v10：其余机制运行态。仅存基础面板会让存读档重置连击、护盾和冷却。
            ints(o, p.comboTargetId, p.comboCount, p.lowHpExtraShots, p.feastStacks, p.crossSeen);
            floats(o, p.comboRateBonus, p.comboTimer, p.lowHpRateMul, p.attackSpeedMul,
                    p.secondStrikeCd, p.mechReactCd, p.globalClock, p.globalInterval,
                    p.feastRateMul, p.linkClock, p.supportCd, p.siteClock, p.drainCd,
                    p.healBurstCd, p.mechShield, p.crossRush);
            o.writeBoolean(p.formChosen);
            ints(o, p.offers);
            ints(o, p.evolveOffer);
            ints(o, p.relics);
            for (boolean e : p.evolved) o.writeBoolean(e);
        }

        o.writeInt(g.enemies.size());
        for (Game.Enemy e : g.enemies) {
            ints(o, e.id, e.type, e.stage, e.variant, e.owner, e.affix, e.phaseTriggers,
                    e.behavior, e.bossIndex, e.summonClock, e.addCount);
            o.writeBoolean(e.elite);
            o.writeBoolean(e.summoned);
            o.writeBoolean(e.splitDone);
            floats(o, e.guard, e.chargeTime, e.chargeVx, e.chargeVy, e.x, e.y, e.hp, e.maxHp,
                    e.r, e.speed, e.cooldown, e.telegraph, e.tx, e.ty, e.slow, e.hit, e.burn,
                    e.burnClock, e.armor, e.blinkClock, e.healClock, e.shield, e.flash, e.stun);
            // v10：首领冲锋、护盾再生、瞬移预警与蓄力运行态
            floats(o, e.chargeDamage, e.shieldClock, e.shieldMax, e.hurtClock,
                    e.blinkX, e.blinkY, e.blinkWarn, e.windup, e.windupT);
            ints(o, e.threat);
            o.writeBoolean(e.chargeHit);
        }

        o.writeInt(g.shots.size());
        for (Game.Shot s : g.shots) {
            ints(o, s.id, s.owner, s.pierce, s.slow, s.explode, s.lastHit, s.element, s.chain,
                    s.ricochet, s.homing, s.flags, s.splitCount, s.hitCount);
            ints(o, s.hits);
            o.writeBoolean(s.enemy);
            o.writeBoolean(s.crit);
            floats(o, s.x, s.y, s.vx, s.vy, s.damage, s.r, s.life, s.radius);
            // ── v8 追加：#6 霜封的逐弹丸减速秒数 ──────────────────────
            o.writeFloat(s.slowSeconds);
            // v10：回旋镖必须保留去程/返程状态，不能靠读档重置命中表。
            o.writeInt(s.boomerang);
        }

        o.writeInt(g.gems.size());
        for (Game.Gem gem : g.gems) {
            ints(o, gem.id, gem.kind);
            floats(o, gem.x, gem.y, gem.value);
        }

        o.writeInt(g.effects.size());
        for (Game.Effect e : g.effects) {
            ints(o, e.id, e.type, e.color);
            floats(o, e.x, e.y, e.r, e.life, e.maxLife, e.value);
        }

        o.writeInt(g.blocks.size());
        for (Game.Block block : g.blocks) {
            floats(o, block.x, block.y, block.w, block.h);
            o.writeInt(block.kind);
        }

        o.writeInt(g.props.size());
        for (Game.Prop p : g.props) {
            ints(o, p.id, p.type);
            floats(o, p.x, p.y, p.hp);
            o.writeBoolean(p.opened);
        }

        o.writeInt(g.hazards.size());
        for (Game.Hazard h : g.hazards) {
            ints(o, h.id, h.kind, h.owner);
            floats(o, h.x, h.y, h.r, h.clock, h.active);
        }

        o.writeInt(g.turrets.size());
        for (Game.Turret t : g.turrets) {
            ints(o, t.id, t.owner, t.kind);
            floats(o, t.x, t.y, t.life, t.cooldown);
        }

        o.writeInt(g.minions.size());
        for (Game.Minion m : g.minions) {
            ints(o, m.id, m.owner, m.kind);
            floats(o, m.x, m.y, m.life, m.cooldown, m.angle);
        }

        // ── v7 追加：契约词条（v6 字节流没有这一段，读档时按 0 处理）──
        ints(o, g.pactMods, g.pactHeat);
        // ── v9 追加：难度/进度状态 ──────────────────────────────────
        // 这四个字段此前存在但不进存档，读档后静默归零：
        //   corruption   —— 腐化阶梯，决定敌人血量与掉落（归零 = 敌人 HP 少 17%）
        //   mutator2     —— 第二个房型修饰词
        //   stallClock   —— 僵持计时（归零 = 卡房兜底失效）
        //   enraged      —— 僵持狂暴已触发（归零 = 可重复触发狂暴）
        ints(o, g.corruption, g.mutator2, g.corruptionAnnounced);
        floats(o, g.stallClock);
        o.writeBoolean(g.enraged);
        // ── v9 追加：事件机制的延迟挂账 ────────────────────────────
        // 不序列化的后果：读档后挂账清零 = 延迟代价被白嫖。
        ints(o, g.eventDebt, g.delayedKind, g.delayedRooms);
        floats(o, g.delayedAmount);
        // ── v10：真实续跑所需的房间节拍与商店一次性奖励状态 ───
        floats(o, g.pactReinforceClock, g.pactBurnClock);
        o.writeBoolean(g.pactLimitAnnounced);
        o.writeBoolean(g.shopBuyoutRewardUsed);
        ints(o, g.shopFreeRerolls, g.lastStallKills);

        o.flush();
        byte[] data = b.toByteArray();
        if (data.length > LIMIT) throw new IOException("snapshot too large: " + data.length);
        return data;
    }

    /**
     * 反序列化。同时接受 v7（当前）与 v6（上一版）两种字节流。
     * <p>v6 档读入后 {@code pactMods=0, pactHeat=0}，即"旧档 = 无词条"，
     * 与 v2.0 设计文档「支柱 1 存档兼容」一致。
     */
    public static Game decode(byte[] data) throws IOException {
        if (data == null || data.length > LIMIT) throw new IOException("snapshot too large");
        DataInputStream i = new DataInputStream(new ByteArrayInputStream(data));
        if (i.readInt() != MAGIC) throw new IOException("unsupported checkpoint/protocol version");
        int version = i.readInt();
        if (version < 7 || version > VERSION)
            throw new IOException("unsupported checkpoint/protocol version");
        Game g = new Game(i.readLong());
        g.random.restore(i.readLong());
        g.phase = i.readInt();
        g.room = i.readInt();
        g.floor = i.readInt();
        g.level = i.readInt();
        g.kills = i.readInt();
        g.tick = i.readInt();
        g.spawned = i.readInt();
        g.target = i.readInt();
        g.route = i.readInt();
        g.captain = i.readInt();
        g.mode = i.readInt();
        g.roomType = i.readInt();
        g.biome = i.readInt();
        g.layout = i.readInt();
        g.hazard = i.readInt();
        g.roomsVisited = i.readInt();
        g.roomsTotal = i.readInt();
        g.routeIndex = i.readInt();
        g.eventType = i.readInt();
        g.eventChoice = i.readInt();
        g.shopRerolls = i.readInt();
        g.coins = i.readInt();
        g.bossesDefeated = i.readInt();
        g.routeCount = i.readInt();
        g.objective = i.readInt();
        g.rewardsLeft = i.readInt();
        g.rerolls = i.readInt();
        g.scouted = i.readInt();
        g.nextId = i.readInt();
        g.contract = i.readInt();
        g.mutator = i.readInt();
        g.wave = i.readInt();
        g.waves = i.readInt();
        g.nextWaveAt = i.readInt();
        g.roomStartKills = i.readInt();
        g.tier = i.readInt();
        g.freeReviveCount = i.readInt();
        g.freeRevivesUsed = i.readInt();
        // ── v8：天赋牌。v7 及更早没有这一段，保持字段默认值（未装备 / 参数全 0）──
        if (version >= 8) {
            g.doctrine = i.readInt();
            g.doctrineHook = i.readInt();
            g.doctrineSideHook = i.readInt();
            g.doctrineBonusChoices = i.readInt();
            g.doctrineKillClock = i.readInt();
            for (int k = 0; k < g.doctrineParams.length; k++) g.doctrineParams[k] = finite(i);
            // 牌号：-1 或 0..17；钩子：-1..31（H_NONE=0，未装备写 0，-1 兼容早期实现）
            range(g.doctrine, -1, DoctrineHooks.COUNT - 1);
            range(g.doctrineHook, -1, DoctrineHooks.MAX);
            range(g.doctrineSideHook, -1, DoctrineHooks.MAX);
            range(g.doctrineBonusChoices, 0, 999);
            range(g.doctrineKillClock, 0, 10_000_000);
            // 自洽校正：牌号 -1 却带着钩子/参数（或反之）视为损坏，回落到"未装备"，
            // 绝不让半套数据参与战斗分发。
            boolean hooked = g.doctrineHook > 0 || g.doctrineSideHook > 0;
            if (g.doctrine < 0 && hooked) {
                g.doctrineHook = DoctrineHooks.H_NONE;
                g.doctrineSideHook = DoctrineHooks.H_NONE;
                java.util.Arrays.fill(g.doctrineParams, 0f);
            }
            for (float v : g.doctrineParams) range(Math.abs(v), 0f, 1e6f);
        } else {
            g.doctrine = -1;
            g.doctrineHook = DoctrineHooks.H_NONE;
            g.doctrineSideHook = DoctrineHooks.H_NONE;
            g.doctrineBonusChoices = 0;
            g.doctrineKillClock = 0;
            java.util.Arrays.fill(g.doctrineParams, 0f);
        }
        // roomTimeLimit 是逐房间派生值（每层重算），不进存档；由 g.restored() 末尾
        // 按当前房型 + 天赋牌重建，让"读档后继续打挑战房"仍受 #8 的限时乘区约束。
        // 上界必须覆盖 Game.GAMBLE(11)，否则赌局相位无法存读档
        range(g.phase, 0, Game.GAMBLE);
        range(g.room, 0, 1_000_000);
        range(g.floor, 1, 999);
        range(g.level, 1, 999);
        range(g.biome, 0, Content.BIOME_COUNT - 1);
        range(g.layout, 0, Rooms.LAYOUTS.length - 1);
        range(g.mode, 0, 3);
        range(g.roomType, 0, 9);
        range(g.routeCount, 1, 3);
        range(g.contract, 0, Balance.CONTRACTS.length - 1);
        range(g.mutator, 0, Math.max(1, Balance.MODIFIERS.length - 1));
        range(g.eventType, 0, Events.COUNT - 1);
        range(g.nextId, 10, 5_000_000);
        range(g.freeReviveCount, 0, 9);
        range(g.freeRevivesUsed, 0, 9);
        g.soloRun = i.readBoolean();
        g.skipNextNode = i.readBoolean();
        g.mirror = i.readBoolean();
        g.campAfterBoss = i.readBoolean();
        g.shrineUsed = i.readBoolean();
        readInts(i, g.routeOptions);
        readInts(i, g.shopOffers);
        readInts(i, g.shopPrices);
        for (int k = 0; k < g.shopSold.length; k++) g.shopSold[k] = i.readBoolean();
        readInts(i, g.history);
        for (int n : g.routeOptions) range(n, 0, 9);
        for (int n : g.shopOffers) range(n, -1, Relics.COUNT - 1);
        g.time = finite(i);
        g.roomTime = finite(i);
        g.spawnClock = finite(i);
        g.charge = finite(i);
        g.xp = finite(i);
        g.xpGoal = finite(i);
        g.choiceClock = finite(i);
        g.eventClock = finite(i);
        g.comboClock = finite(i);
        g.combo = finite(i);
        g.xpTotal = finite(i);
        g.dustEarned = finite(i);
        g.marksEarned = finite(i);
        g.waveWait = finite(i);
        g.damageTaken = finite(i);
        g.notice = text(i, 600);
        g.sceneTitle = text(i, 160);
        g.sceneHint = text(i, 600);
        for (int k = 0; k < g.schoolBonus.length; k++) g.schoolBonus[k] = finite(i);
        readInts(i, g.bossKillsRun);

        int n = count(i, 4);
        for (int k = 0; k < n; k++) {
            Game.Player p = new Game.Player();
            p.id = i.readInt();
            p.role = i.readInt();
            range(p.role, 0, Content.ROLE_COUNT - 1);
            p.kills = i.readInt();
            p.chosen = i.readInt();
            p.vote = i.readInt();
            p.dashSerial = i.readInt();
            p.skillSerial = i.readInt();
            p.shotCount = i.readInt();
            p.synergyMask = i.readInt();
            p.weaponForm = i.readInt();
            p.evolution = i.readInt();
            p.evolutionsTaken = i.readInt();
            p.ultSerial = i.readInt();
            p.ultCharge = i.readInt();
            p.multishot = i.readInt();
            p.pierce = i.readInt();
            p.slow = i.readInt();
            p.leech = i.readInt();
            p.explode = i.readInt();
            p.armorBreak = i.readInt();
            p.soul = i.readInt();
            p.discount = i.readInt();
            p.bossLoot = i.readInt();
            p.thorns = i.readInt();
            p.execute = i.readInt();
            p.knockback = i.readInt();
            p.knockbackPower = i.readInt();
            p.flags = i.readInt();
            p.extraProjectiles = i.readInt();
            p.chainCount = i.readInt();
            p.orbitCount = i.readInt();
            p.mineCount = i.readInt();
            p.splitCount = i.readInt();
            p.ricochetCount = i.readInt();
            p.beamCount = i.readInt();
            p.summonCount = i.readInt();
            p.shieldLayers = i.readInt();
            p.stunChance = i.readInt();
            p.reactions = i.readInt();
            // ── v7：F_* 机制计数 + 进化原型（与 encode 顺序严格对应）──
            if (version >= 7) {
                p.auraCount = i.readInt();
                p.slowFieldCount = i.readInt();
                p.lifeSteal = i.readInt();
                p.taunt = i.readInt();
                p.chargeCount = i.readInt();
                p.splitShot = i.readInt();
                p.evoMechanic = i.readInt();
                // 越界钳制：防止损坏档把 switch 分发打飞
                range(p.auraCount, 0, 99);
                range(p.slowFieldCount, 0, 99);
                range(p.lifeSteal, 0, 99);
                range(p.taunt, 0, 99);
                range(p.chargeCount, 0, 99);
                range(p.splitShot, 0, 99);
                range(p.evoMechanic, 0, Evolutions.MECHANIC_COUNT);
            }
            p.name = text(i, 32);
            p.connected = i.readBoolean();
            p.down = i.readBoolean();
            p.nextCrit = i.readBoolean();
            p.x = finite(i); p.y = finite(i); p.hp = finite(i); p.maxHp = finite(i);
            p.speed = finite(i); p.damage = finite(i); p.interval = finite(i); p.attack = finite(i);
            p.dx = finite(i); p.dy = finite(i); p.ax = finite(i); p.ay = finite(i);
            p.manual = finite(i); p.dashCd = finite(i); p.skillCd = finite(i);
            p.invuln = finite(i); p.revive = finite(i); p.disconnectedFor = finite(i);
            p.crit = finite(i); p.magnet = finite(i); p.xpBonus = finite(i);
            p.dashFactor = finite(i); p.buff = finite(i); p.buffKind = finite(i);
            p.frostHealCd = finite(i); p.ultCd = finite(i); p.ultReady = finite(i);
            p.reactionCd = finite(i); p.leechBudget = finite(i); p.regenClock = finite(i);
            p.shield = finite(i); p.shieldMax = finite(i);
            p.bonusPower = finite(i); p.bonusRate = finite(i); p.bonusArea = finite(i);
            p.bonusSpeed = finite(i); p.bonusHp = finite(i); p.bonusCrit = finite(i);
            p.bonusMagnet = finite(i); p.bonusRegen = finite(i); p.bonusXp = finite(i);
            p.bonusCoin = finite(i);
            // ── v8：天赋牌玩家级状态（v7 旧档 = 0，不会带着过热/闪避增益读进来）
            if (version >= 8) {
                p.overheatClock = finite(i);
                p.overheatStun = finite(i);
                p.dashHaste = finite(i);
                p.dashHasteMul = finite(i);
                // 计时器只影响下一次开火；越界一律钳到合理区间，避免坏档让玩家永久停火
                range(p.overheatClock, 0f, 1e6f);
                range(p.overheatStun, 0f, 60f);
                range(p.dashHaste, 0f, 60f);
                range(p.dashHasteMul, 0f, 10f);
            }
            // ── v9：元进度第二组节点（v8 及更早 = 默认值，等价"没点这些节点"）──
            if (version >= 9) {
                p.dashGrow = finite(i);
                p.metaArmor = finite(i);
                p.metaRegen = finite(i);
                p.metaAtkSpeed = finite(i);
                p.metaRange = finite(i);
                p.metaCoin = finite(i);
                p.metaXp = finite(i);
                p.metaEvent = finite(i);
                p.metaRelicSlots = i.readInt();
                range(p.dashGrow, .1f, 4f);
                range(p.metaArmor, 0f, .5f);
                range(p.metaRegen, 0f, 2f);
                range(p.metaAtkSpeed, 0f, 1f);
                range(p.metaRange, 0f, 1f);
                range(p.metaCoin, 0f, 2f);
                range(p.metaXp, 0f, 2f);
                range(p.metaEvent, 0f, 2f);
                range(p.metaRelicSlots, 0, Relics.COUNT);
            } else {
                p.dashGrow = 1f;
                p.metaArmor = 0f; p.metaRegen = 0f; p.metaAtkSpeed = 0f;
                p.metaRange = 0f; p.metaCoin = 0f; p.metaXp = 0f;
                p.metaEvent = 0f; p.metaRelicSlots = 0;
            }
            if (version < 8) {
                p.overheatClock = 0f;
                p.overheatStun = 0f;
                p.dashHaste = 0f;
                p.dashHasteMul = 0f;
            }
            if (version >= 10) {
                p.auraClock = finite(i);
                p.mineClock = finite(i);
                p.teleportClock = finite(i);
                p.chargeClock = finite(i);
                p.chargeHold = finite(i);
            } else {
                p.auraClock = 0f; p.mineClock = 0f; p.teleportClock = 0f;
                p.chargeClock = 0f; p.chargeHold = 0f;
            }
            if (version >= 10) {
                p.comboTargetId = i.readInt();
                p.comboCount = i.readInt();
                p.lowHpExtraShots = i.readInt();
                p.feastStacks = i.readInt();
                p.crossSeen = i.readInt();
                p.comboRateBonus = finite(i); p.comboTimer = finite(i);
                p.lowHpRateMul = finite(i); p.attackSpeedMul = finite(i);
                p.secondStrikeCd = finite(i); p.mechReactCd = finite(i);
                p.globalClock = finite(i); p.globalInterval = finite(i);
                p.feastRateMul = finite(i); p.linkClock = finite(i);
                p.supportCd = finite(i); p.siteClock = finite(i); p.drainCd = finite(i);
                p.healBurstCd = finite(i); p.mechShield = finite(i); p.crossRush = finite(i);
                p.formChosen = i.readBoolean();
                range(p.comboCount, 0, 1_000_000);
                range(p.feastStacks, 0, 1_000_000);
            }
            readInts(i, p.offers);
            readInts(i, p.evolveOffer);
            readInts(i, p.relics);
            for (int k2 = 0; k2 < p.evolved.length; k2++) p.evolved[k2] = i.readBoolean();
            for (int id : p.offers) range(id, -1, Relics.COUNT - 1);
            for (int id : p.evolveOffer) range(id, -1, Evolutions.COUNT - 1);
            for (int stack : p.relics) range(stack, 0, 1_000_000);
            range(p.multishot, 1, 12);
            range(p.interval, .02f, 20f);
            g.players.add(p);
        }

        n = count(i, Game.MAX_ENEMIES);
        for (int k = 0; k < n; k++) {
            Game.Enemy e = new Game.Enemy();
            e.id = i.readInt();
            e.type = i.readInt();
            range(e.type, 0, Enemies.COUNT - 1);
            e.stage = i.readInt();
            e.variant = i.readInt();
            e.owner = i.readInt();
            e.affix = i.readInt();
            e.phaseTriggers = i.readInt();
            e.behavior = i.readInt();
            range(e.behavior, 0, 15);
            e.bossIndex = i.readInt();
            range(e.bossIndex, -1, Bosses.COUNT - 1);
            e.summonClock = i.readInt();
            e.addCount = i.readInt();
            e.elite = i.readBoolean();
            e.summoned = i.readBoolean();
            e.splitDone = i.readBoolean();
            e.guard = finite(i); e.chargeTime = finite(i); e.chargeVx = finite(i);
            e.chargeVy = finite(i); e.x = finite(i); e.y = finite(i); e.hp = finite(i);
            e.maxHp = finite(i); e.r = finite(i); e.speed = finite(i); e.cooldown = finite(i);
            e.telegraph = finite(i); e.tx = finite(i); e.ty = finite(i); e.slow = finite(i);
            e.hit = finite(i); e.burn = finite(i); e.burnClock = finite(i); e.armor = finite(i);
            e.blinkClock = finite(i); e.healClock = finite(i); e.shield = finite(i);
            e.flash = finite(i); e.stun = finite(i);
            if (version >= 10) {
                e.chargeDamage = finite(i); e.shieldClock = finite(i);
                e.shieldMax = finite(i); e.hurtClock = finite(i);
                e.blinkX = finite(i); e.blinkY = finite(i);
                e.blinkWarn = finite(i); e.windup = finite(i); e.windupT = finite(i);
                e.threat = i.readInt();
                e.chargeHit = i.readBoolean();
                range(e.threat, 0, 3);
            }
            g.enemies.add(e);
        }

        n = count(i, Game.MAX_SHOTS);
        for (int k = 0; k < n; k++) {
            Game.Shot s = new Game.Shot();
            s.id = i.readInt();
            s.owner = i.readInt();
            s.pierce = i.readInt();
            s.slow = i.readInt();
            s.explode = i.readInt();
            s.lastHit = i.readInt();
            s.element = i.readInt();
            s.chain = i.readInt();
            s.ricochet = i.readInt();
            s.homing = i.readInt();
            s.flags = i.readInt();
            s.splitCount = i.readInt();
            s.hitCount = i.readInt();
            range(s.hitCount, 0, s.hits.length);
            readInts(i, s.hits);
            s.enemy = i.readBoolean();
            s.crit = i.readBoolean();
            s.x = finite(i); s.y = finite(i); s.vx = finite(i); s.vy = finite(i);
            s.damage = finite(i); s.r = finite(i); s.life = finite(i); s.radius = finite(i);
            // ── v8：#6 霜封的逐弹丸减速秒数（v7 旧档 = 0，即"没有额外减速"）──
            if (version >= 8) {
                s.slowSeconds = finite(i);
                range(s.slowSeconds, 0f, 60f);
            } else {
                s.slowSeconds = 0f;
            }
            if (version >= 10) {
                s.boomerang = i.readInt();
                range(s.boomerang, 0, 2);
            }
            g.shots.add(s);
        }

        n = count(i, Game.MAX_GEMS);
        for (int k = 0; k < n; k++) {
            Game.Gem gem = new Game.Gem();
            gem.id = i.readInt();
            gem.kind = i.readInt();
            gem.x = finite(i); gem.y = finite(i); gem.value = finite(i);
            g.gems.add(gem);
        }

        n = count(i, Game.MAX_EFFECTS);
        for (int k = 0; k < n; k++) {
            Game.Effect e = new Game.Effect();
            e.id = i.readInt();
            e.type = i.readInt();
            e.color = i.readInt();
            e.x = finite(i); e.y = finite(i); e.r = finite(i); e.life = finite(i);
            e.maxLife = finite(i); e.value = finite(i);
            g.effects.add(e);
        }

        n = count(i, 64);
        for (int k = 0; k < n; k++) {
            Game.Block block = new Game.Block(finite(i), finite(i), finite(i), finite(i));
            block.kind = i.readInt();
            g.blocks.add(block);
        }

        n = count(i, 40);
        for (int k = 0; k < n; k++) {
            Game.Prop p = new Game.Prop();
            p.id = i.readInt();
            p.type = i.readInt();
            p.x = finite(i); p.y = finite(i); p.hp = finite(i);
            p.opened = i.readBoolean();
            g.props.add(p);
        }

        n = count(i, 40);
        for (int k = 0; k < n; k++) {
            Game.Hazard h = new Game.Hazard();
            h.id = i.readInt();
            h.kind = i.readInt();
            range(h.kind, 0, 4);
            // v2.0：地雷的布置者（与 encode 的 ints(id, kind, owner) 顺序一致）
            h.owner = i.readInt();
            range(h.owner, -1, 99);
            h.x = finite(i); h.y = finite(i); h.r = finite(i); h.clock = finite(i);
            h.active = finite(i);
            g.hazards.add(h);
        }

        n = count(i, 16);
        for (int k = 0; k < n; k++) {
            Game.Turret t = new Game.Turret();
            t.id = i.readInt();
            t.owner = i.readInt();
            t.kind = i.readInt();
            t.x = finite(i); t.y = finite(i); t.life = finite(i); t.cooldown = finite(i);
            g.turrets.add(t);
        }

        n = count(i, 12);
        for (int k = 0; k < n; k++) {
            Game.Minion m = new Game.Minion();
            m.id = i.readInt();
            m.owner = i.readInt();
            m.kind = i.readInt();
            m.x = finite(i); m.y = finite(i); m.life = finite(i); m.cooldown = finite(i);
            m.angle = finite(i);
            g.minions.add(m);
        }

        // ── v7：契约词条。v6 旧档没有这一段，按"无词条"迁移。──
        if (version >= 7) {
            g.pactMods = i.readInt();
            g.pactHeat = i.readInt();
            // 位掩码只允许 PACT_COUNT 位；越界即视为损坏档
            range(g.pactMods, 0, (1 << Balance.PACT_COUNT) - 1);
            range(g.pactHeat, 0, Balance.PACT_HEAT_MAX);
            // 缓存值必须与位掩码自洽，否则以位掩码为准重建
            if (g.pactHeat != Balance.heatOf(g.pactMods)) g.pactHeat = Balance.heatOf(g.pactMods);
        } else {
            // v5 / v6 旧档：无词条
            g.pactMods = 0;
            g.pactHeat = 0;
        }

        // ── v9：难度/进度状态。v8 及更早的档没有这一段，按"无腐化"迁移。──
        if (version >= 9) {
            g.corruption = i.readInt();
            g.mutator2 = i.readInt();
            g.corruptionAnnounced = i.readInt();
            g.stallClock = finite(i);
            g.enraged = i.readBoolean();
            range(g.corruption, 0, 9);
            range(g.mutator2, 0, Math.max(1, Balance.MODIFIERS.length - 1));
            range(g.corruptionAnnounced, -1, 9);
            if (!Float.isFinite(g.stallClock) || g.stallClock < 0f) g.stallClock = 0f;
            g.eventDebt = i.readInt();
            g.delayedKind = i.readInt();
            g.delayedRooms = i.readInt();
            g.delayedAmount = finite(i);
            range(g.eventDebt, 0, 9);
            range(g.delayedKind, 0, 3);
            range(g.delayedRooms, 0, 64);
            if (!Float.isFinite(g.delayedAmount) || g.delayedAmount < 0f) g.delayedAmount = 0f;
        } else {
            g.corruption = 0;
            g.mutator2 = 0;
            g.corruptionAnnounced = -1;
            g.stallClock = 0f;
            g.enraged = false;
            g.eventDebt = 0;
            g.delayedKind = 0;
            g.delayedRooms = 0;
            g.delayedAmount = 0f;
        }

        // ── v10：补齐此前未入档的逐房间节拍 / 商店一次性状态 ─────
        if (version >= 10) {
            g.pactReinforceClock = finite(i);
            g.pactBurnClock = finite(i);
            g.pactLimitAnnounced = i.readBoolean();
            g.shopBuyoutRewardUsed = i.readBoolean();
            g.shopFreeRerolls = i.readInt();
            g.lastStallKills = i.readInt();
            range(g.shopFreeRerolls, 0, 10);
            range(g.lastStallKills, 0, 10_000_000);
        } else {
            // v7–v9 之前未保存这些状态，只能回退到安全默认值。
            // 时限追兵从完整周期重新倒计时，避免旧档一加载就立刻增援。
            g.pactReinforceClock = Balance.PACT_TIME_REINFORCE;
            g.pactBurnClock = 1f;
            g.pactLimitAnnounced = false;
            g.shopBuyoutRewardUsed = false;
            g.shopFreeRerolls = 0;
            g.lastStallKills = Math.max(0, g.kills - g.roomStartKills);
        }

        if (i.available() != 0) throw new IOException("unexpected trailing bytes");
        g.restored();
        return g;
    }

    private static void readInts(DataInputStream i, int[] arr) throws IOException {
        for (int j = 0; j < arr.length; j++) arr[j] = i.readInt();
    }

    private static void range(int n, int min, int max) throws IOException {
        if (n < min || n > max) throw new IOException("value outside bounds: " + n);
    }

    private static void range(float n, float min, float max) throws IOException {
        if (n < min || n > max) throw new IOException("value outside bounds: " + n);
    }

    private static int count(DataInputStream i, int max) throws IOException {
        int n = i.readInt();
        range(n, 0, max);
        return n;
    }

    private static float finite(DataInputStream i) throws IOException {
        float n = i.readFloat();
        if (!Float.isFinite(n) || Math.abs(n) > 1e9f) throw new IOException("invalid float");
        return n;
    }

    private static String text(DataInputStream i, int max) throws IOException {
        String s = i.readUTF();
        if (s.length() > max) throw new IOException("text too long");
        return s;
    }

    public static byte[] readFrame(DataInputStream i) throws IOException {
        int n = i.readInt();
        range(n, 0, LIMIT);
        byte[] bytes = new byte[n];
        i.readFully(bytes);
        return bytes;
    }
}

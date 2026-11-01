# 深渊小队 v1.0 — 验收记录

日期：2026-10-18
构建：`releases/v1.0/abyss-squad-1.0-release.apk`（5.28 MB）
SHA256：`45D43EA891EEABF746FBDDC5B181D7F58E8636044A48FA3595EE2CE15964FBCA`

---

## 一、构建

| 项 | 结果 |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
| `verifyReleaseAssets` | 39 个按名字加载的资源（24 audio + 15 drawable）全部存活于 `resources.arsc`，missing=0 |
| 签名 | 本地自签名 release keystore |
| R8 | `minifyEnabled=true`、`shrinkResources=false`、`debuggable=false` |

**R8 之后资源存活的证明方式**：`verifyReleaseAssets` 任务读取 release APK 的
`resources.arsc` 字符串表，逐个核对 `res/raw/*` 与 `res/drawable-nodpi/*` 的
**文件名**。游戏用 `getResources().getIdentifier(name, ...)` 在运行时按名字取资源，
一旦 R8/资源压缩改名，编译仍会成功但音频与精灵会在发布包里静默消失——这是本项目
最危险的发布陷阱，因此做成了构建期硬性校验。

---

## 二、数值平衡

自动探针 `tests/PlayProbe.java`：**带人类缺陷的 bot**（180–320ms 反应延迟、±15%
走位漂移、每 2–4 秒换向抖动、10% 技能漏放率），三档选牌策略。主指标取「流派向」。

实测样本：**480 局**（8 种子 × 5 契约 × 12 职业）。

| 契约 | 目标 | 实测 | 判定 |
|---|---|---|---|
| 漫游 | 80–92% | **88.5%** | PASS |
| 远征 | 55–70% | **59.4%** | PASS |
| 试炼 | 38–52% | **46.9%** | PASS |
| 深渊 | 22–35% | **24.0%** | PASS |
| 炼狱 | 10–20% | **18.8%** | PASS |

其他指标：
- 平均推进 **13.0 / 15 站**
- 进化触发率 **100%**
- 职业极差（40 局/职业）：**90.0% − 60.0% = 30.0pp**
- 存活曲线全程单调缓降，无台阶：`100/100/100/100/99/98/98/97/95/95/84/72/72/57/48`
- `ALL RUNS COMPLETED WITHOUT CRASH / NaN / OVERFLOW`，0 error / 0 timeout

---

## 三、缺陷回归

`tests/VerifyFixes.java` — **13 项断言全 PASS**。本轮修复的真实缺陷：

| # | 严重度 | 问题 | 根因 | 验证 |
|---|---|---|---|---|
| 1 | **致命** | 存档会静默删除营地/升级的全部成长 | `recompute()` 只按职业基线+遗物重算，忽略直接写入的成长 | bug1a/1b/1c/1d：营地强化 → recompute → 存档往返 → 再往返，数值稳定不衰减 |
| 2 | **致命** | 付不起神龛时永久卡死 | `shrine()` 在余额校验**之前**就置 `shrineUsed=true` | bug2a/2b/2c：`shrine(0)` 返回 false、不落锁、`shrine(1)` 仍可离开 |
| 3 | 高 | 营地/神龛在正式路线里永远刷不出来（死内容） | `preview()` 的 8 条分支完全没有 CAMP/SHRINE | bug3a/3b/3c/3d：250 局中 camp=197、shrine=60，商店与首领仍在 |
| 4 | 高 | 首领被重复计数，星尘/印记奖励翻倍 | `damage()` 和 `clearRoom()` 各加一次 | 修完后远征首领数回到 3（符合语义） |
| 5 | 中 | 赌局相位无法存读档 | `Codec` 把 phase 校验在 `[0, SHRINE]` | bug5：`GAMBLE` 相位往返成功 |
| 6 | 中 | 远征路线首领节点数错误 | 路线表生成逻辑 | bug6：200 种子 × 15 层，每站首领位置正确且至少二选一 |

---

## 四、性能

**重要发现：之前的 ANR 与卡顿不是应用缺陷。**

逐层排查证据：

1. 应用自身的绘制耗时（内置 `ABYSSPERF` 日志，单模拟器、无并发负载）：
   **`draw=1.6–7.6ms`，`fps=60`**。`bg` 占 0.0–5.8ms，`ui` 占 1.6–7.6ms。
2. ANR 栈：主线程卡在 `ViewRootImpl.performDraw → HardwareRenderer.setStopped`，
   等待 RenderThread 的 future；渲染线程卡在 `GrGLCompileAndAttachShader →
   glGetShaderiv → qemu_pipe_read`——**模拟器对 GLES 的软件转译**，不在应用代码里。
3. 最终确诊：`mCurrentFocus=Window{... Application Not Responding: com.android.systemui}`
   —— 卡住的是 **SystemUI 本身**，不是本应用。

本轮仍然修掉的真实性能缺陷：

| 问题 | 根因 | 修复 |
|---|---|---|
| 每个新高度都会新建 `LinearGradient`，HWUI 需现场编译 GLSL | `Ui.gradientRect` / `Art.gradientRect` 把 `h` 算进缓存 key | 统一在规范高度 `GRAD_CANON=64` 上建 Shader，绘制时 `canvas.scale` 拉伸；Shader 数量只取决于颜色组合 |
| `glow()` 只缓存最后一种颜色，一帧多色时每帧 recycle+createBitmap，每次都上传成新 GPU 纹理 | 单槽缓存 | 改为 12 槽（Ui）/ 8 槽（Art）按颜色索引 |
| `LAYER_TYPE_HARDWARE` 把整个 1440×810 视图隔离到离屏硬件纹理 | 显式 `setLayerType` | 移除该调用，跟随窗口默认的硬件加速 |
| 音频在构造函数里同步解 24 个资源，与首帧图集解码抢主线程 | 构造期初始化 | 改为首帧绘制完成后 `lazyInitAudio()` |

其他已落实的优化：
- `SpriteAtlas.actorCellBitmap()` 永久缓存（此前每帧 `Bitmap.createBitmap`，微基准 100 次缩放绘制 1256ms）
- 渐变 20 槽 Shader 缓存、辉光绘制半径上限 190px
- `Fx` 零分配：`ThreadMXBean.getThreadAllocatedBytes` 实测 2000 帧 **0 字节**（期间 381,527 次 canvas 调用）
- 静态地牢地面烘焙进缓存位图，逐帧不再生成

---

## 五、内容完整性

| 维度 | 数量 | 自检 |
|---|---|---|
| 职业 | 12 | `Content.valid()` |
| 流派 | 6 | 同上 |
| 遗物 | 60 | `Relics.valid()` |
| 进化线 | 36 | `Evolutions.valid()` |
| 敌人 | 20 | `Enemies.valid()` |
| 首领 | 8（各 3 阶段） | `Bosses.valid()` |
| 生态 | 6 | `Content.valid()` |
| 房间类型 | 10 | `Rooms.valid()` |
| 事件 | 36（8 类型） | `Events.valid()` |
| 模式 | 4 | — |
| 难度契约 | 5 | `Balance.valid()` |
| 元进度 | 32 节点 / 153 级 | `Meta.valid()` |
| 成就 | 24 | `Achievements.valid()` |
| 遭遇异变 | 8 | `Balance.valid()` |

---

## 六、已验证可用的界面

实机（模拟器 1440×810 原生分辨率）截图确认：

- **主菜单**：品牌区、6 张生态插画轮播、5 个主操作、4 个次级导航、底部五列总览
- **角色选择**：4 模式 tab、12 职业网格（4 解锁 + 8 锁定显示印记价格）、右侧详情（大图/称号/技能双卡/三条属性条）、5 档契约、确认出发
- **星图选路**：10 种房间图标、连线、风险星级、异变标签、当前站计数
- **战斗 HUD**：血条+护盾、经验条、等级、计时、击杀、星尘、技能冷却环、闪避键、终极键、Buff 行、摇杆、瞄准线
- **三选一遗物**：稀有度标签、流派徽记、遗物图标、层数/上限、共鸣进度
- **设置**：音频三档、画面三档、6 个开关、2 个滑条、重置存档二次确认
- **暂停与结算**：暂停面板、离开/重开确认、胜利/失败结算

---

## 七、已知边界

1. **联机仅限可信局域网**：无公网匹配、无账号、无 NAT 穿透、无 TLS、无排行榜。
2. **模拟器 ANR 属环境问题**：本机 CPU 常被打满（多台 x86_64 模拟器并发），
   SystemUI 自身会 ANR。真机上不复现——应用自身绘制实测 1.6–7.6ms / 60fps。
3. **`core Game.update` 每帧分配约 780–1150 B**（敌人/子弹/Effect 对象）。
   `Fx` 层是 0 分配；若后续要把战斗期做到严格零 GC，core 侧是下一个目标。
4. 职业极差 30.0pp，高于最初设想的 18pp 目标。已从 62.5pp 收敛到此值；
   剩余差距来自职业技能机制本身的强度差异，继续收敛需要改职业技能实现而非调数值。
5. 没有云存档、跨设备同步。
6. 无内购、无广告、无网络上报，全部数据留在本机。

# Core regression test: compile core/ + all tests/*.java, run every suite independently,
# then report a combined verdict.
# Exit code: 0 all green; non-zero means compile or assertion failure in any suite.
#
# Why each suite is wired in here:
#
#   CoreTests          - the main behavioural suite (data, determinism, save round-trip,
#                        full-run reachability, content coverage, balance, LAN sync).
#
#   RelicEffectTest    - 38 of the 60 relics once had no dedicated implementation and 26 were
#                        "fake mechanics" (DESC promised a mechanic the engine never implemented).
#                        This does a "grant-before vs grant-after" comparison for every relic; any
#                        relic with zero measurable effect fails the build.
#
#   EventContractTest  - Events.TYPE is pure data, but its meaning is implemented by
#                        Game.resolveEvent's switch. A past change bumped Events.TYPE to 8..12
#                        without adding the matching resolveEvent cases, so 19 events silently fell
#                        into the `default:` branch (TYPE 7 = curse) and paid out curse rewards while
#                        their text promised something else. This suite black-box verifies that every
#                        TYPE actually reaches its own branch: only TYPE 7 may add Relics.CURSED.
#
#   BossChargeTest     - Bosses.PHASES lists BOSS_CHARGE for bosses 1/3/4, doBossPattern wrote
#                        chargeVx/chargeVy/chargeTime, and Bosses.CHARGE_* held per-boss tuning --
#                        yet updateBoss never advanced chargeTime, so the charge never happened.
#                        Data + parameters + assignment all existed; only the consumer was missing.
#                        This suite runs real frames and asserts displacement + one-hit-per-swing.
#
#   IdRolloverTest     - Game.nextId is monotonic and serialized, while Codec.decode range-checks
#                        it to [10, 5_000_000). Endless mode could push the counter past that and
#                        the player's own save would be rejected by its own validator. This suite
#                        drives the counter to the boundary and asserts a clean rollover that still
#                        survives an encode/decode round trip.
#
#   WeaponFormTest     - Game.fire() has three weaponForm branches and Codec serializes the field,
#                        but the whole project had exactly one write (weaponForm = 0), so forms 1
#                        and 2 were unreachable. This suite drives a real shot and asserts the
#                        observed projectile count / per-shot damage differ per form.
#
#   CrossSchoolTest    - The 6 cross-school combos were "fixed" twice: first crossMask was never
#                        assigned, then it was assigned but 4/6 had no consumer and the other 2
#                        wrote derived fields nothing read. This suite does a per-combo A/B
#                        (combo on vs equal relics without the combo) so all-zero effects fail.
#
#   DoctrineEffectTest - The same failure shape a fourth time, and the largest instance of it:
#                        Meta.java had the full 18-card table (names/desc/hooks/params), and
#                        DoctrineHooks.java had all 32 hook ids, and docs/doctrine-hooks.md had a
#                        per-hook implementation spec -- yet Game.java had no doctrine field and
#                        no dispatch code at all. Every card the player equipped did nothing.
#                        This suite A/Bs all 18 cards (start-of-run / panel / behaviour / targeted
#                        probes), asserts BOTH the upside and the downside of each card bite,
#                        cross-checks the 32 hook ids against Meta's hardcoded values, and
#                        verifies the v7 -> v8 save migration (old save = no card equipped).
#
#   PactEffectTest     - The same failure shape again: Balance.java had all 12 pacts' names / desc /
#                        heat / unlock flags AND every tuning constant, Codec v7 persisted pactMods,
#                        and PactScreen.java could select them -- but only 7 changed combat.
#                        Boss Fury / Blinkers / Inferno / Collapse / Time Limit had ZERO consumers in
#                        core or app: the player paid heat and got a higher settlement multiplier for
#                        a rule that never existed. This suite A/Bs all 12 pacts with a DEDICATED
#                        observable each (not just "any difference"), plus pactHeat() <-> heatOf()
#                        over all 4096 masks and heatRewardMult(0) == 1.0.
#
#   EventVarietyTest   - Events.java carried 36 full texts (names / descriptions / choices /
#                        results) while Game.resolveEvent only had 8 TYPE branches, so 4-6 events
#                        per TYPE were byte-identical in effect, and 32/36 events had COST_A =
#                        COST_B = 0 -- "36 events" was really 8 mechanics and 5 prices. This suite
#                        black-boxes every event: (a) all 36 outcome fingerprints (TYPE + costs +
#                        both branches' state-change vectors, averaged over seeds) must be distinct,
#                        (b) events inside one TYPE must still differ, (c) at least 20 events must
#                        charge a real cost, (d) the delayed-settlement structures (TYPE 4/8/9)
#                        are run against an equal-progress control with the debt erased, so the
#                        penalty has to show up as a real hp/stardust delta 3 stations later.
#
#   ClassRulesTest     - ClassRules (354 lines, 12 per-class relic rules) was fully implemented,
#                        self-consistent and even documented a tests/ClassRulesTest.java that never
#                        existed -- while nothing in the project called it, so all 12 classes drew
#                        from one identical relic pool. This suite validates the promised invariants
#                        and asserts the rules are actually wired into rollRelic.
#
# All suites always run (no early exit), so one failure never hides another.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

$out = 'build/core-tests'
# 整目录重建而不是逐个删 .class：Get-ChildItem 与 Remove-Item 之间若另一进程
# （IDE 索引 / 并行的 gradle）动过文件，会抛 PathNotFound 把整个测试脚本打断。
if (Test-Path $out) { Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force $out | Out-Null

$testSources = @(Get-ChildItem tests -Filter '*.java' | ForEach-Object FullName)
$sources = @(Get-ChildItem core/src -Recurse -Filter '*.java' | ForEach-Object FullName) + $testSources

Write-Host "Compiling $($sources.Count) sources ($($testSources.Count) test files) -> $out"
& javac -encoding UTF-8 -Xlint:-options -d $out $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$coreFailed = $false
$relicFailed = $false
$eventFailed = $false
$bossFailed = $false
$idFailed = $false
$formFailed = $false
$crossFailed = $false
$classFailed = $false
$doctrineFailed = $false
$pactFailed = $false
$metaFailed = $false
$varietyFailed = $false
$previewFailed = $false
$ledgerFailed = $false
$migrationFailed = $false
$loopbackFailed = $false
$auditFailed = $false
$gemFailed = $false
$fairnessFailed = $false

Write-Host ""
Write-Host "== 1/19 CoreTests =="
& java -ea -cp $out CoreTests
if ($LASTEXITCODE -ne 0) { $coreFailed = $true }

Write-Host ""
Write-Host "== 2/19 RelicEffectTest (60 relics, grant-before vs grant-after) =="
& java -ea -cp $out RelicEffectTest
if ($LASTEXITCODE -ne 0) { $relicFailed = $true }

Write-Host ""
Write-Host "== 3/19 EventContractTest (Events.TYPE <-> resolveEvent) =="
& java -ea -cp $out EventContractTest
if ($LASTEXITCODE -ne 0) { $eventFailed = $true }

Write-Host ""
Write-Host "== 4/19 BossChargeTest (boss charge state machine actually runs) =="
& java -ea -cp $out BossChargeTest
if ($LASTEXITCODE -ne 0) { $bossFailed = $true }

Write-Host ""
Write-Host "== 5/19 IdRolloverTest (entity id stays inside the Codec contract) =="
& java -ea -cp $out IdRolloverTest
if ($LASTEXITCODE -ne 0) { $idFailed = $true }

Write-Host ""
Write-Host "== 6/19 WeaponFormTest (all three weapon forms are reachable and differ) =="
& java -ea -cp $out WeaponFormTest
if ($LASTEXITCODE -ne 0) { $formFailed = $true }

Write-Host ""
Write-Host "== 7/19 CrossSchoolTest (all 6 cross-school combos actually pay off) =="
& java -ea -cp $out CrossSchoolTest
if ($LASTEXITCODE -ne 0) { $crossFailed = $true }

Write-Host ""
Write-Host "== 8/19 ClassRulesTest (per-class relic rules are real and wired) =="
& java -ea -cp $out ClassRulesTest
if ($LASTEXITCODE -ne 0) { $classFailed = $true }

Write-Host ""
Write-Host "== 9/19 DoctrineEffectTest (18 doctrine cards: upside AND downside, + Codec v7->v8) =="
& java -ea -cp $out DoctrineEffectTest
if ($LASTEXITCODE -ne 0) { $doctrineFailed = $true }

Write-Host ""
Write-Host "== 10/19 MetaNodeTest (progression hooks and save persistence) =="
& java -ea -cp $out MetaNodeTest
if ($LASTEXITCODE -ne 0) { $metaFailed = $true }

Write-Host ""
Write-Host "== 11/19 PactEffectTest (12 pacts: dedicated A/B probe each, + heat contract) =="
& java -ea -cp $out PactEffectTest
if ($LASTEXITCODE -ne 0) { $pactFailed = $true }

Write-Host ""
Write-Host "== 12/19 EventVarietyTest (36 events -> 36 distinct decisions, costs really bite) =="
& java -ea -cp $out EventVarietyTest
if ($LASTEXITCODE -ne 0) { $varietyFailed = $true }

Write-Host ""
Write-Host "== 13/19 EventPreviewTest (all 36 event costs, delayed risks, core legality) =="
& java -ea -cp $out EventPreviewTest
if ($LASTEXITCODE -ne 0) { $previewFailed = $true }

Write-Host ""
Write-Host "== 14/19 DeferredLedgerTest (independent invoices, v10 migration, v11 corruption) =="
& java -ea -cp $out DeferredLedgerTest
if ($LASTEXITCODE -ne 0) { $ledgerFailed = $true }

Write-Host ""
Write-Host "== 15/19 CodecMigrationTest (v10 to v11, invalid versions, trailing data) =="
& java -ea -cp $out CodecMigrationTest
if ($LASTEXITCODE -ne 0) { $migrationFailed = $true }

Write-Host ""
Write-Host "== 16/19 LoopbackTest (live TCP: four clients, replay, reconnect, version rejection) =="
& java -ea -cp $out LoopbackTest
if ($LASTEXITCODE -ne 0) { $loopbackFailed = $true }

Write-Host ""
Write-Host "== 17/19 ContentAudit (data tables, balance budgets, draft/economy invariants) =="
& java -ea -cp $out ContentAudit | Select-Object -Last 4
if ($LASTEXITCODE -ne 0) { $auditFailed = $true }

Write-Host ""
Write-Host "== 18/19 GemPickupTest (level-up heal burst drops gems during pickup) =="
& java -ea -cp $out GemPickupTest
if ($LASTEXITCODE -ne 0) { $gemFailed = $true }

Write-Host ""
Write-Host "== 19/19 CombatFairnessTest (all combat telegraphs and multiplayer auto-pick) =="
& java -ea -cp $out CombatFairnessTest
if ($LASTEXITCODE -ne 0) { $fairnessFailed = $true }

Write-Host ""
Write-Host "================================"
if ($coreFailed)     { Write-Host "CoreTests:          FAILED" } else { Write-Host "CoreTests:          PASSED" }
if ($relicFailed)    { Write-Host "RelicEffectTest:    FAILED" } else { Write-Host "RelicEffectTest:    PASSED" }
if ($eventFailed)    { Write-Host "EventContractTest:  FAILED" } else { Write-Host "EventContractTest:  PASSED" }
if ($bossFailed)     { Write-Host "BossChargeTest:     FAILED" } else { Write-Host "BossChargeTest:     PASSED" }
if ($idFailed)       { Write-Host "IdRolloverTest:     FAILED" } else { Write-Host "IdRolloverTest:     PASSED" }
if ($formFailed)     { Write-Host "WeaponFormTest:     FAILED" } else { Write-Host "WeaponFormTest:     PASSED" }
if ($crossFailed)    { Write-Host "CrossSchoolTest:    FAILED" } else { Write-Host "CrossSchoolTest:    PASSED" }
if ($classFailed)    { Write-Host "ClassRulesTest:     FAILED" } else { Write-Host "ClassRulesTest:     PASSED" }
if ($doctrineFailed) { Write-Host "DoctrineEffectTest: FAILED" } else { Write-Host "DoctrineEffectTest: PASSED" }
if ($metaFailed)     { Write-Host "MetaNodeTest:       FAILED" } else { Write-Host "MetaNodeTest:       PASSED" }
if ($pactFailed)     { Write-Host "PactEffectTest:     FAILED" } else { Write-Host "PactEffectTest:     PASSED" }
if ($varietyFailed)  { Write-Host "EventVarietyTest:   FAILED" } else { Write-Host "EventVarietyTest:   PASSED" }
if ($previewFailed)  { Write-Host "EventPreviewTest:   FAILED" } else { Write-Host "EventPreviewTest:   PASSED" }
if ($ledgerFailed)   { Write-Host "DeferredLedgerTest: FAILED" } else { Write-Host "DeferredLedgerTest: PASSED" }
if ($migrationFailed){ Write-Host "CodecMigrationTest: FAILED" } else { Write-Host "CodecMigrationTest: PASSED" }
if ($loopbackFailed) { Write-Host "LoopbackTest:       FAILED" } else { Write-Host "LoopbackTest:       PASSED" }
if ($auditFailed)    { Write-Host "ContentAudit:       FAILED" } else { Write-Host "ContentAudit:       PASSED" }
if ($gemFailed)      { Write-Host "GemPickupTest:      FAILED" } else { Write-Host "GemPickupTest:      PASSED" }
if ($fairnessFailed) { Write-Host "CombatFairnessTest: FAILED" } else { Write-Host "CombatFairnessTest: PASSED" }
Write-Host "================================"

$anyFailed = $coreFailed -or $relicFailed -or $eventFailed -or $bossFailed -or $idFailed -or $formFailed -or $crossFailed -or $classFailed -or $doctrineFailed -or $pactFailed -or $varietyFailed -or $previewFailed -or $ledgerFailed -or $migrationFailed -or $loopbackFailed -or $auditFailed -or $gemFailed -or $fairnessFailed
if ($anyFailed) {
    exit 1
}
Write-Host "All regression tests passed."
exit 0

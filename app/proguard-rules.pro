# ══════════════════════════════════════════════════════════════════════════
#  Abyss Squad / 深渊小队 — R8 keep rules
#
#  这个游戏的关键约束：美术与音频**按字符串名**在运行时查找资源：
#      getResources().getIdentifier("music_menu", "raw", pkg)
#      getResources().getIdentifier("actors_v4", "drawable", pkg)
#  一旦资源被"优化"成 a.png / b.ogg，getIdentifier 就返回 0 ——
#  表现是"编译通过、进游戏静音 + 方框"，是最容易漏掉的发布事故。
#
#  资源侧的防线不是本文件，而是 build.gradle 里的 shrinkResources false：
#  AGP 只有在开启资源压缩时才会执行 -adaptresourcefilenames 重命名。
#  R8 本身不改资源文件名。verifyReleaseAssets 任务会在打包后逐项核对
#  res/raw 与 res/drawable-nodpi 的条目名，任何改名都会让构建失败。
# ══════════════════════════════════════════════════════════════════════════

# 1) 屏幕类通过 Class.forName("com.dilao.abysssquad.screen.Xxx").newInstance()
#    反射实例化；pointer(float,float) / click(int) 也是反射调用的，
#    还有 getField("hit")。R8 看不到这些引用，必须整类保留且不混淆。
-keep class com.dilao.abysssquad.screen.** { *; }

# 3) 顶层表现类被 Activity 直接持有；保守保留成员（允许优化，不阻止内联）。
-keep,allowoptimization class com.dilao.abysssquad.GameView { *; }

# 4) 元进度节点表的字段被 JSON 键名直接读取，保留内部类成员名。
-keepclassmembers class com.dilao.abysssquad.Meta$* { *; }

# 5) 通用：保留行号与源文件名，崩溃栈可读（发布版排障必需）。
#    注意不能加 LocalVariableTable，否则 APK 会大一圈且没有实际收益。
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod

# 6) 去掉调试日志：发布版不打印 v/d 日志。
#    注意：这不影响 adb logcat 里系统/框架打印的音频解码行，
#    发布验证正是靠 SoundPool/MediaPlayer 的框架日志确认音频加载成功。
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

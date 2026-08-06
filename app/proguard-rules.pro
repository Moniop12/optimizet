-keep class rikka.shizuku.** { *; }
-keep class com.monai.optimizer.IShellUserService { *; }
# Class service Shizuku DIINSTANSIASI via reflection oleh Shizuku (ComponentName
# memakai nama class PERSIS dari package optimizer) — R8 HARUS keep nama+anggota.
-keep class com.monai.optimizer.optimizer.ShellUserService { *; }
-keep class * extends rikka.shizuku.ShizukuProvider { *; }
# Shizuku starts the UserService in another process by class name.
-keep class io.github.pedropasinn.mobilepad.screen.ScreenControlService { *; }
-keep class io.github.pedropasinn.mobilepad.IScreenControl { *; }
-keep class io.github.pedropasinn.mobilepad.IScreenControl$* { *; }

# Shizuku and the hidden-API helper use reflection internally.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.**
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

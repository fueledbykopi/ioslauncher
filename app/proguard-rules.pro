# Keep Activity & Service entry points
-keep class com.iospring.launcher.MainActivity { *; }
-keep class com.iospring.launcher.ui.SettingsActivity { *; }
-keep class com.iospring.launcher.ui.IosLockScreenActivity { *; }
-keep class com.iospring.launcher.service.BadgeNotificationService { *; }
-keep class com.iospring.launcher.service.LauncherKeepAliveService { *; }
-keep class com.iospring.launcher.service.ScreenLockAccessibilityService { *; }

# Keep data models used for JSON serialization
-keep class com.iospring.launcher.model.** { *; }

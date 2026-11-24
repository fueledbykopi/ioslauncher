# Keep MainActivity, SettingsActivity, and NotificationListenerService entry points
-keep class com.iospring.launcher.MainActivity { *; }
-keep class com.iospring.launcher.ui.SettingsActivity { *; }
-keep class com.iospring.launcher.service.BadgeNotificationService { *; }

# Keep data models used for JSON serialization
-keep class com.iospring.launcher.model.** { *; }

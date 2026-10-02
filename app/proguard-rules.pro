# ClockDesk ProGuard / R8 Rules

# Preserve stack traces for CrashActivity & bug reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all XML-instantiated custom Views and ViewGroups
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keep class com.nxd1frnt.clockdesk2.ui.view.** { *; }
-keep class com.github.skydoves.colorpickerview.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.nxd1frnt.clockdesk2.network.MyAppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# DeskConnect Models and Network Protocol
-keep class com.nxd1frnt.clockdesk2.connect.model.** { *; }
-keepclassmembers class com.nxd1frnt.clockdesk2.connect.model.** { *; }

# Plugin contracts and Smart Chips (AIDL / reflection / external intent IPC)
-keep interface com.nxd1frnt.clockdesk2.smartchips.** { *; }
-keep class com.nxd1frnt.clockdesk2.smartchips.** { *; }
-keep interface com.nxd1frnt.clockdesk2.music.** { *; }
-keep class com.nxd1frnt.clockdesk2.music.** { *; }

# AndroidX Preferences
-keep class androidx.preference.** { *; }
-dontwarn androidx.preference.**
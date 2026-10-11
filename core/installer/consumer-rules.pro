# Shizuku instantiates the user service by class name via reflection, and the
# class must stay in the APK with its constructor.
-keep class com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstallService { *; }
-keepclassmembers class com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstallService {
    public <init>(...);
}

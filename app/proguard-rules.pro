# ─── Base ──────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ─── Room ──────────────────────────────────────────────────────────────────────
# Entities, DAOs and TypeConverters must not be renamed — Room uses reflection.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
# ─── Room TypeConverters (method-level annotation) ─────────────────────────────
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# ─── App Enums ─────────────────────────────────────────────────────────────────
# Keep all enum values() and valueOf() — used by Room TypeConverters and JSON parsing.
-keepclassmembers enum com.scanner.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── App Data Classes (JSON serialization via JSONObject / metadata field) ─────
# Models.kt and Entities.kt are accessed by name in repository/DB code.
-keep class com.scanner.app.data.** { *; }
-keep class com.scanner.app.util.WardrivingEntry { *; }

# ─── osmdroid ──────────────────────────────────────────────────────────────────
# osmdroid loads tile providers and overlay classes by reflection.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ─── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ─── Kotlin Serialization (safe-guard, not actively used yet) ─────────────────
-dontwarn kotlinx.serialization.**

# ─── Accompanist / Compose Permissions ────────────────────────────────────────
-dontwarn com.google.accompanist.**

# ─── Compose (R8 rules are bundled in the Compose BOM; these are extras) ──────
# Keep Compose runtime internals that R8 might strip aggressively.
-keep class androidx.compose.runtime.** { *; }

# ─── Retrofit / OkHttp (not used, suppress warnings from transitive deps) ─────
-dontwarn okhttp3.**
-dontwarn retrofit2.**

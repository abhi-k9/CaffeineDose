# No reflection nor serialization: the default optimized rules (and the ones bundled with the libraries) are enough.

# Strip verbose and debug logs from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

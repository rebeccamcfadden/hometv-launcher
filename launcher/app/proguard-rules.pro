# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# The catalogue holds its listeners weakly (Catalog.addListener), so each screen keeps its own in a field named
# onCatalog. R8 drops a field that is written but never read, which let the listener be collected and the screens
# stop hearing that titles filled in (seen in 06-states A: the skeletons stayed after the details arrived).
-keepclassmembers class com.gathin.tvhome.** {
    java.lang.Runnable onCatalog;
}

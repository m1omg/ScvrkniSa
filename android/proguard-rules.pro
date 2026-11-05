# jump3r: only the low-level encoder is used. Its command-line and javax.sound
# convenience classes reference APIs Android does not have.
-dontwarn javax.sound.sampled.**
-dontwarn java.beans.**
-keep class de.sciss.jump3r.mp3.** { *; }
-keep class de.sciss.jump3r.mpg.** { *; }

# Translations are loaded by name through ResourceBundle.
-keepclassmembers class io.github.m1omg.shrink.core.i18n.** { *; }

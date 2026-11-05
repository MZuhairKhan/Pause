# Add project-specific ProGuard rules here.
# See https://developer.android.com/build/shrink-code

# ACRA loads its plugins through ServiceLoader, which R8 cannot trace from the call site. The
# crash interaction is listed in META-INF/services, so keep it with its no-argument constructor.
-keep class io.github.mzuhairkhan.pause.CrashInteraction { <init>(); }

# Copyright (c) 2026 Shahid Ansari. All rights reserved.
# Proprietary software. Copying, modification, rebuilding or redistribution
# is not permitted without written permission. See LICENSE.
# Add project specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Rules for NewPipeExtractor (YouTube/YouTube Music extraction provider).
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

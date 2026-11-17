# Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.

# SQLCipher native code resolves these classes by name through JNI, so R8 must not rename or strip them.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

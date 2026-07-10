# JNI-bound libraries: class and member names are referenced from native code,
# so they must survive shrinking and obfuscation. Everything else (Hilt, Room,
# AndroidX, coroutines, GMS) ships consumer rules in its own AAR — no manual
# keeps needed, and app code is covered by the rules KSP generates.

# secp256k1 (fr.acinq JNI bindings)
-keep class fr.acinq.secp256k1.** { *; }

# libsignal — Native.java resolves Java classes/methods by name from Rust
-keep class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**

# SQLCipher JNI
-keep class net.sqlcipher.** { *; }

# WebRTC — native code invokes Java callbacks by name
-keep class org.webrtc.** { *; }

# Room type converters call Enum.valueOf reflectively
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Standard Serializable contract
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

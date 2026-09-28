# SQLCipher accesses these classes from native code.
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

# Do not emit source file or line metadata from release builds.
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature

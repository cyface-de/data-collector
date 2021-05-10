-verbose

# Preserving `java` modules via the configuration in `libs/build.gradle`
# - If the `java.lang` class is not found, make sure your local `java.home` and `java.version` are set correctly.
#   E.g. by reinstalling `sudo apt install openjdk-11-jdk`

# Preserve all public classes, and their public and protected fields and
# methods.

-keep 								public class * {
        								public protected *;
    								}
# Preserve parameter names
-keepparameternames

# Preserve the special static methods that are required in all enumeration
# classes.

-keepclassmembers,allowoptimization enum * {
        								public static **[] values();
        								public static ** valueOf(java.lang.String);
    								}

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
# You can comment this out if your library doesn't use serialization.
# If your code contains serializable classes that have to be backward
# compatible, please refer to the manual.

-keepnames class * implements java.io.Serializable

-keepclassmembers 					class * implements java.io.Serializable {
        								static final long serialVersionUID;
                                        private static final java.io.ObjectStreamField[] serialPersistentFields;
                                        !static !transient <fields>;
                                        !private <fields>;
                                        !private <methods>;
       									private void writeObject(java.io.ObjectOutputStream);
        								private void readObject(java.io.ObjectInputStream);
        								java.lang.Object writeReplace();
        								java.lang.Object readResolve();
    								}

# The following three settings are necessary to use Java 8 Inline Lambda Expressions
# See: https://sourceforge.net/p/proguard/bugs/545/

-keepclassmembers 					class * {
    									private static synthetic java.lang.Object $deserializeLambda$(java.lang.invoke.SerializedLambda);
									}

-keepclassmembernames 				class * {
    									private static synthetic *** lambda$*(...);
									}

-adaptclassstrings 					com.example.Test

# This is required to be able to use JDBC Drivers.

-keep 								class * implements java.sql.Driver

-optimizationpasses					3
-repackageclasses 					de.cyface
-keepattributes 					Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-dontskipnonpubliclibraryclasses

#!/bin/sh
# Produit build/libs/better-mca-<version>-universal.jar (Fabric) à partir du jar Fabric standard.
# Prérequis : ./gradlew build déjà fait. Utilise ASM du cache Gradle.
cd "$(dirname "$0")/.." || exit 1
V=$(grep '^mod_version' gradle.properties | cut -d= -f2)
G=$HOME/.gradle/caches/modules-2/files-2.1/org.ow2.asm
CP="$(cygpath -w $G/asm/9.9.1/*/asm-9.9.1.jar);$(cygpath -w $G/asm-commons/9.9.1/*/asm-commons-9.9.1.jar);$(cygpath -w $G/asm-tree/9.9.1/*/asm-tree-9.9.1.jar)"
java -cp "$CP" tools/RelocateMca.java "build/libs/better-mca-$V.jar" "build/libs/better-mca-$V-universal.jar" fabric

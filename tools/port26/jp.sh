#!/bin/sh
J=/c/Users/user/.jdks/jdk-25.0.5+8/bin/javap
CP='C:\Users\user\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar;C:\Users\user\Documents\mca 26.2\mca-fabric-8.1.11+26.2.jar'
"$J" -cp "$CP" "$@"

package com.maitreyoshi.mcavie.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Vérifie au démarrage que le jar MCA est la version Fabric seule (classes en net.mca.*).
 * Le jar « universal » de MCA déplace ses classes sous fabric.net.mca.*,
 * ce qui casse tous nos mixins avec une erreur peu claire.
 */
public class McaJarCheckPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader cl = McaJarCheckPlugin.class.getClassLoader();
        if (cl.getResource("net/fabricmc/loader/api/FabricLoader.class") == null) {
            return; // pas sous Fabric (Forge) : le jar est déjà relocalisé par son propre build
        }
        // Ce littéral est réécrit (fabric/...) dans la variante « universal » de Better MCA.
        if (cl.getResource("net/mca/entity/VillagerEntityMCA.class") != null) {
            return;
        }
        throw new IllegalStateException(
                "Better MCA: Minecraft Comes Alive (MCA Reborn) 7.6.26 for Minecraft 1.20.1 was not found or is not compatible."
                        + " Please install the MCA Reborn file from CurseForge (7.6.26+1.20.1).");
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}

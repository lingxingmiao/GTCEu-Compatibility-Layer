package com.gtceu.compat.core.asm;

import gregtech.api.GregTechAPI;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.ItemMaterialInfo;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.item.ItemStack;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime helpers backing the 2.8 API bridges injected by ASM.
 *
 * <p>Kept in Java (rather than hand-written bytecode) wherever the bridge needs
 * real iteration/collections; the transformers only emit a small stub that
 * delegates here.
 */
public final class OreDictCompatAux {

    private OreDictCompatAux() {}

    /**
     * 2.8 {@code OreDictUnifier.getAllItemInfos()} rebuilt on 2.9's
     * {@code RecyclingManager}: {@code ItemAndMetadata -> ItemStack} and
     * {@code RecyclingData -> ItemMaterialInfo}.
     */
    public static List<Map.Entry<ItemStack, ItemMaterialInfo>> getAllItemInfos() {
        List<Map.Entry<ItemStack, ItemMaterialInfo>> out = new ArrayList<>();
        RecyclingManager manager = GregTechAPI.RECYCLING_MANAGER;
        if (manager == null) return out;
        try {
            manager.iterate((ItemAndMetadata key, RecyclingData data) -> {
                if (key == null || data == null) return;
                ItemStack stack = key.toItemStack();
                if (stack == null || stack.getItem() == null) return;
                ItemMaterialInfo info = new ItemMaterialInfo(data.getMaterials());
                out.add(new AbstractMap.SimpleEntry<>(stack, info));
            });
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] getAllItemInfos rebuild failed: " + t);
        }
        return out;
    }
}

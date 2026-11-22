/*
 * Derived from GregTech CE: Unofficial 2.8.10 (https://github.com/GregTechCEu/GregTech).
 * Copyright (c) the GregTech CE: Unofficial contributors.
 * Licensed under the GNU Lesser General Public License, version 3.0.
 * See LICENSE-GTCEu-LGPL-3.0.txt and LICENSE-NOTICE.md in the repository root.
 *
 * Modifications made by this project are documented in LICENSE-NOTICE.md.
 */
package gregtech.api.pattern;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class PatternError {

    protected BlockWorldState worldState;

    public void setWorldState(BlockWorldState worldState) {
        this.worldState = worldState;
    }

    public World getWorld() {
        return worldState.getWorld();
    }

    public BlockPos getPos() {
        return worldState.getPos();
    }

    public List<List<ItemStack>> getCandidates() {
        TraceabilityPredicate predicate = worldState.predicate;
        List<List<ItemStack>> candidates = new ArrayList<>();
        for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
            candidates.add(common.getCandidates());
        }
        for (TraceabilityPredicate.SimplePredicate limited : predicate.limited) {
            candidates.add(limited.getCandidates());
        }
        return candidates;
    }

    @SideOnly(Side.CLIENT)
    public String getErrorInfo() {
        List<List<ItemStack>> candidates = getCandidates();
        StringBuilder builder = new StringBuilder();
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) {
                builder.append(candidate.get(0).getDisplayName());
                builder.append(", ");
            }
        }
        builder.append("...");
        return I18n.format("gregtech.multiblock.pattern.error", builder.toString(), worldState.pos);
    }
}

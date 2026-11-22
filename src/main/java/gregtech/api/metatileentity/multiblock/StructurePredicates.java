package gregtech.api.metatileentity.multiblock;

import gregtech.api.block.VariantActiveBlock;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.util.BlockInfo;
import gregtech.common.blocks.MetaBlocks;
import gregtech.api.unification.material.Material;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * GE-2.9 compatibility: GTCEu 2.8's static structure-predicate factory methods
 * ({@code MultiblockControllerBase.states/abilities/frames/blocks/air/any/
 * heatingCoils/metaTileEntities/tilePredicate}) were removed in GTQT 2.9.
 *
 * <p>2.8-compiled addons still emit {@code invokestatic MultiblockControllerBase.states(...)}.
 * {@code AddonPackageRemapTransformer} redirects those calls to this class, which
 * provides the identical implementations built on the vendored 2.8
 * {@link TraceabilityPredicate} / {@link BlockWorldState} (also supplied by this
 * compatibility layer).
 *
 * <p>Method names and descriptors are kept EXACTLY as in 2.8 so a plain owner
 * rewrite is sufficient. All referenced GTCEu classes still exist in GTQT 2.9.
 */
@SuppressWarnings("unused")
public final class StructurePredicates {

    private StructurePredicates() {}

    public static TraceabilityPredicate tilePredicate(
            BiFunction<BlockWorldState, MetaTileEntity, Boolean> predicate,
            Supplier<BlockInfo[]> candidates) {
        return new TraceabilityPredicate(blockWorldState -> {
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity)) return false;
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (predicate.apply(blockWorldState, metaTileEntity)) {
                if (metaTileEntity instanceof IMultiblockPart) {
                    Set<IMultiblockPart> partsFound = blockWorldState.getMatchContext()
                            .getOrCreate("MultiblockParts", HashSet::new);
                    partsFound.add((IMultiblockPart) metaTileEntity);
                }
                return true;
            }
            return false;
        }, candidates);
    }

    public static TraceabilityPredicate metaTileEntities(MetaTileEntity... metaTileEntities) {
        ResourceLocation[] ids = Arrays.stream(metaTileEntities).filter(Objects::nonNull)
                .map(tile -> tile.metaTileEntityId).toArray(ResourceLocation[]::new);
        return tilePredicate((state, tile) -> ArrayUtils.contains(ids, tile.metaTileEntityId),
                getCandidates(metaTileEntities));
    }

    private static Supplier<BlockInfo[]> getCandidates(MetaTileEntity... metaTileEntities) {
        return () -> Arrays.stream(metaTileEntities).filter(Objects::nonNull).map(tile -> {
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            holder.setMetaTileEntity(tile);
            holder.getMetaTileEntity().onPlacement();
            holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
            return new BlockInfo(MetaBlocks.MACHINE.getDefaultState(), holder);
        }).toArray(BlockInfo[]::new);
    }

    private static Supplier<BlockInfo[]> getCandidates(IBlockState... allowedStates) {
        return () -> Arrays.stream(allowedStates).map(state -> new BlockInfo(state, null))
                .toArray(BlockInfo[]::new);
    }

    public static TraceabilityPredicate abilities(MultiblockAbility<?>... allowedAbilities) {
        return tilePredicate((state, tile) -> tile instanceof IMultiblockAbilityPart<?> &&
                        ArrayUtils.contains(allowedAbilities, ((IMultiblockAbilityPart<?>) tile).getAbility()),
                getCandidates(Arrays.stream(allowedAbilities)
                        .flatMap(ability -> MultiblockAbility.REGISTRY.get(ability).stream())
                        .toArray(MetaTileEntity[]::new)));
    }

    public static TraceabilityPredicate states(IBlockState... allowedStates) {
        return new TraceabilityPredicate(blockWorldState -> {
            IBlockState state = blockWorldState.getBlockState();
            if (state.getBlock() instanceof VariantActiveBlock) {
                blockWorldState.getMatchContext().getOrPut("VABlock", new LinkedList<>())
                        .add(blockWorldState.getPos());
            }
            return ArrayUtils.contains(allowedStates, state);
        }, getCandidates(allowedStates));
    }

    public static TraceabilityPredicate frames(Material... frameMaterials) {
        return states(Arrays.stream(frameMaterials).map(m -> MetaBlocks.FRAMES.get(m).getBlock(m))
                .toArray(IBlockState[]::new))
                .or(new TraceabilityPredicate(blockWorldState -> {
                    TileEntity tileEntity = blockWorldState.getTileEntity();
                    if (!(tileEntity instanceof IPipeTile)) return false;
                    IPipeTile<?, ?> pipeTile = (IPipeTile<?, ?>) tileEntity;
                    return ArrayUtils.contains(frameMaterials, pipeTile.getFrameMaterial());
                }));
    }

    public static TraceabilityPredicate blocks(Block... block) {
        return new TraceabilityPredicate(
                blockWorldState -> ArrayUtils.contains(block, blockWorldState.getBlockState().getBlock()),
                getCandidates(Arrays.stream(block).map(Block::getDefaultState).toArray(IBlockState[]::new)));
    }

    public static TraceabilityPredicate air() {
        return TraceabilityPredicate.AIR;
    }

    public static TraceabilityPredicate any() {
        return TraceabilityPredicate.ANY;
    }

    public static TraceabilityPredicate heatingCoils() {
        return TraceabilityPredicate.HEATING_COILS.get();
    }
}

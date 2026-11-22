package gregtech.common.covers.filter;

import net.minecraft.item.ItemStack;

/**
 * GTCEu 2.8 compatibility stub for {@code FilterTypeRegistry}, removed in GTQT 2.9.
 *
 * <p>2.8 allowed mods to register custom item/fluid filter types shown in the
 * cover filter GUI. 2.9 restructured filters to {@code BaseFilter} subclasses
 * with no runtime type registry, so there is nowhere to plug a 2.8 custom type.
 *
 * <p>ZBGT 0.16.2 calls these two methods at cover-registration time; without the
 * class the whole mod fails with NoClassDefFoundError. Registering a no-op keeps
 * ZBGT's covers, machines and recipes working — only its two *custom filter
 * types* are unavailable (they would appear in the cover filter GUI of 2.8).
 */
@SuppressWarnings("unused")
public final class FilterTypeRegistry {

    private FilterTypeRegistry() {}

    /** 2.8: register a custom item filter type (id, class, display stack). */
    public static void registerItemFilter(int id, Class<?> filterClass, ItemStack stack) {
        // no-op: 2.9 has no runtime filter-type registry
    }

    /** 2.8: register a custom fluid filter type (id, class, display stack). */
    public static void registerFluidFilter(int id, Class<?> filterClass, ItemStack stack) {
        // no-op
    }
}

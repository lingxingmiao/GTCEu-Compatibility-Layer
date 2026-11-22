package gregtech.common.covers.filter;

import gregtech.api.gui.Widget;
import gregtech.api.util.IDirtyNotifiable;

import net.minecraft.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ItemFilterWrapper {

    private static final Object MATCH_RESULT_TRUE = new Object();
    private final IDirtyNotifiable dirtyNotifiable;
    private boolean isBlacklistFilter = false;
    private int maxStackSize = 1;
    private ItemFilter currentItemFilter;
    private Runnable onFilterInstanceChange;

    public ItemFilterWrapper(IDirtyNotifiable dirtyNotifiable) {
        this.dirtyNotifiable = dirtyNotifiable;
    }

    /**
     * 2.8 compatibility shell: the 2.8-only WidgetGroupItemFilter UI is not shipped by the compatibility layer
     * (GTQT 2.9 deleted the legacy widget, and the legacy widget group hierarchy needs the JEI API that this jar
     * does not compile against). The filter object itself stays fully functional for logic/NBT paths.
     */
    public void initUI(int y, Consumer<Widget> widgetGroup) {}

    public void blacklistUI(int y, Consumer<Widget> widgetGroup, BooleanSupplier showBlacklistButton) {}

    public void setItemFilter(ItemFilter itemFilter) {
        this.currentItemFilter = itemFilter;
        if (currentItemFilter != null) {
            currentItemFilter.setDirtyNotifiable(dirtyNotifiable);
        }
        if (onFilterInstanceChange != null) {
            this.onFilterInstanceChange.run();
        }
    }

    public ItemFilter getItemFilter() {
        return currentItemFilter;
    }

    public void setOnFilterInstanceChange(Runnable onFilterInstanceChange) {
        this.onFilterInstanceChange = onFilterInstanceChange;
    }

    public void onFilterInstanceChange() {
        if (currentItemFilter != null) {
            currentItemFilter.setMaxStackSize(getInternalMaxStackSize());
        }
        dirtyNotifiable.markAsDirty();
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        onFilterInstanceChange();
        dirtyNotifiable.markAsDirty();
    }

    public void setBlacklistFilter(boolean blacklistFilter) {
        isBlacklistFilter = blacklistFilter;
        onFilterInstanceChange();
        dirtyNotifiable.markAsDirty();
    }

    public boolean isBlacklistFilter() {
        return isBlacklistFilter;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    private int getInternalMaxStackSize() {
        if (isBlacklistFilter()) {
            return 1;
        } else {
            return getMaxStackSize();
        }
    }

    public boolean showGlobalTransferLimitSlider() {
        return isBlacklistFilter() || currentItemFilter == null || currentItemFilter.showGlobalTransferLimitSlider();
    }

    public int getSlotTransferLimit(Object matchSlot, int globalTransferLimit) {
        if (isBlacklistFilter() || currentItemFilter == null) {
            return globalTransferLimit;
        }
        return currentItemFilter.getSlotTransferLimit(matchSlot, globalTransferLimit);
    }

    public Object matchItemStack(ItemStack itemStack) {
        Object originalResult;
        if (currentItemFilter == null) {
            originalResult = MATCH_RESULT_TRUE;
        } else {
            originalResult = currentItemFilter.matchItemStack(itemStack);
        }
        if (isBlacklistFilter()) {
            originalResult = originalResult == null ? MATCH_RESULT_TRUE : null;
        }
        return originalResult;
    }

    public Object matchItemStack(ItemStack itemStack, boolean whitelist) {
        Object originalResult;
        if (currentItemFilter == null) {
            originalResult = MATCH_RESULT_TRUE;
        } else {
            originalResult = currentItemFilter.matchItemStack(itemStack);
        }
        if (!whitelist) {
            originalResult = originalResult == null ? MATCH_RESULT_TRUE : null;
        }
        return originalResult;
    }

    public boolean testItemStack(ItemStack itemStack) {
        return matchItemStack(itemStack) != null;
    }
}

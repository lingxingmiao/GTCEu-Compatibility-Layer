/*
 * Derived from GregTech CE: Unofficial 2.8.10 (https://github.com/GregTechCEu/GregTech).
 * Copyright (c) the GregTech CE: Unofficial contributors.
 * Licensed under the GNU Lesser General Public License, version 3.0.
 * See LICENSE-GTCEu-LGPL-3.0.txt and LICENSE-NOTICE.md in the repository root.
 *
 * Modifications made by this project are documented in LICENSE-NOTICE.md.
 */
package gregtech.common.covers.filter;

import gregtech.api.gui.Widget;
import gregtech.api.util.IDirtyNotifiable;

import net.minecraftforge.fluids.FluidStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FluidFilterWrapper {

    private final IDirtyNotifiable dirtyNotifiable;
    private boolean isBlacklistFilter = false;
    private FluidFilter currentFluidFilter;
    private Supplier<Boolean> showTipSupplier;
    private int maxSize = 1000;

    public FluidFilterWrapper(IDirtyNotifiable dirtyNotifiable, int maxSize) {
        this.dirtyNotifiable = dirtyNotifiable;
        this.maxSize = maxSize;
    }

    public FluidFilterWrapper(IDirtyNotifiable dirtyNotifiable) {
        this.dirtyNotifiable = dirtyNotifiable;
    }

    /**
     * 2.8 compatibility shell: see ItemFilterWrapper#initUI - the legacy widget group is a 2.8-only class that
     * GTQT 2.9 removed, so the UI body is intentionally empty here.
     */
    public void initUI(int y, Consumer<Widget> widgetGroup) {}

    public void blacklistUI(int y, Consumer<Widget> widgetGroup, BooleanSupplier showBlacklistButton) {}

    public void setFluidFilter(FluidFilter fluidFilter) {
        this.currentFluidFilter = fluidFilter;
        if (currentFluidFilter != null) {
            currentFluidFilter.setDirtyNotifiable(dirtyNotifiable);
            currentFluidFilter.setMaxConfigurableFluidSize(maxSize);
        }
    }

    private Supplier<Boolean> shouldShowTip() {
        return showTipSupplier;
    }

    protected void setTipSupplier(Supplier<Boolean> supplier) {
        this.showTipSupplier = supplier;
    }

    public FluidFilter getFluidFilter() {
        return currentFluidFilter;
    }

    public void onFilterInstanceChange() {
        dirtyNotifiable.markAsDirty();
    }

    public void setBlacklistFilter(boolean blacklistFilter) {
        isBlacklistFilter = blacklistFilter;
        dirtyNotifiable.markAsDirty();
    }

    public boolean isBlacklistFilter() {
        return isBlacklistFilter;
    }

    public boolean testFluidStack(FluidStack fluidStack, boolean whitelist) {
        boolean result = true;
        if (currentFluidFilter != null) {
            result = currentFluidFilter.testFluid(fluidStack);
        }
        if (!whitelist) {
            result = !result;
        }
        return result;
    }

    public boolean testFluidStack(FluidStack fluidStack) {
        boolean result = true;
        if (currentFluidFilter != null) {
            result = currentFluidFilter.testFluid(fluidStack);
        }
        if (isBlacklistFilter) {
            result = !result;
        }
        return result;
    }
}

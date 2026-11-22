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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Consumer;

public abstract class FluidFilter {

    private IDirtyNotifiable dirtyNotifiable;
    boolean showTip;

    public abstract boolean testFluid(FluidStack fluidStack);

    public abstract int getFluidTransferLimit(FluidStack fluidStack);

    public abstract int getMaxOccupiedHeight();

    public abstract void initUI(Consumer<Widget> widgetGroup);

    public abstract void writeToNBT(NBTTagCompound tagCompound);

    public abstract void readFromNBT(NBTTagCompound tagCompound);

    public final void setDirtyNotifiable(IDirtyNotifiable dirtyNotifiable) {
        this.dirtyNotifiable = dirtyNotifiable;
    }

    public abstract void configureFilterTanks(int amount);

    public abstract void setMaxConfigurableFluidSize(int maxStackSize);

    public final void markDirty() {
        if (dirtyNotifiable != null) {
            dirtyNotifiable.markAsDirty();
        }
    }
}

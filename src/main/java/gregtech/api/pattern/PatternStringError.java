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

public class PatternStringError extends PatternError {

    public final String translateKey;

    public PatternStringError(String translateKey) {
        this.translateKey = translateKey;
    }

    @Override
    public String getErrorInfo() {
        return I18n.format(translateKey);
    }
}

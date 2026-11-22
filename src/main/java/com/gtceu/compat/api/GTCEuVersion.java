package com.gtceu.compat.api;

/**
 * Enum representing supported GTCEu versions for compatibility.
 */
public enum GTCEuVersion {

    /**
     * GTCEu version 2.8.x
     */
    V2_8("2.8", "2.8.x"),

    /**
     * GTCEu version 2.9.x
     */
    V2_9("2.9", "2.9.x"),

    /**
     * Unknown or unsupported version
     */
    UNKNOWN("unknown", "Unknown");

    private final String majorVersion;
    private final String displayName;

    GTCEuVersion(String majorVersion, String displayName) {
        this.majorVersion = majorVersion;
        this.displayName = displayName;
    }

    /**
     * Gets the major version string (e.g., "2.8", "2.9").
     * @return The major version string.
     */
    public String getMajorVersion() {
        return majorVersion;
    }

    /**
     * Gets the display name for this version.
     * @return The display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this version is a valid (known) version.
     * @return true if this is a known version, false if unknown.
     */
    public boolean isValid() {
        return this != UNKNOWN;
    }

    /**
     * Checks if this version is 2.8.x.
     * @return true if this is version 2.8.x.
     */
    public boolean isV28() {
        return this == V2_8;
    }

    /**
     * Checks if this version is 2.9.x.
     * @return true if this is version 2.9.x.
     */
    public boolean isV29() {
        return this == V2_9;
    }

    /**
     * Gets the version enum from a version string.
     * @param versionString The version string to parse.
     * @return The corresponding GTCEuVersion, or UNKNOWN if not recognized.
     */
    public static GTCEuVersion fromString(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            return UNKNOWN;
        }

        // Parse version string (e.g., "2.8.10", "2.9.0")
        if (versionString.startsWith("2.8")) {
            return V2_8;
        } else if (versionString.startsWith("2.9")) {
            return V2_9;
        }

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

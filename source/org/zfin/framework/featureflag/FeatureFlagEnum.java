package org.zfin.framework.featureflag;


import java.util.NoSuchElementException;

public enum FeatureFlagEnum {
    JBROWSE("jBrowse", false),
    CURATOR_JOB_POSTING("Curator Job Posting", true),
    USE_NAVIGATION_COUNTER("Show Navigation Counter", false),
    SHOW_ALLIANCE_DATA("Show Alliance Data", false),
    USE_UNIVERSAL_ANALYTICS("Use Universal Analytics", true),
    USE_GA4_ANALYTICS("Use GA4 Analytics", false);


    private final String name;
    private final boolean enabledByDefault;

    FeatureFlagEnum(String name, boolean enabledByDefault) {
        this.name = name;
        this.enabledByDefault = enabledByDefault;
    }

    public String getName() {
        return name;
    }
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public static FeatureFlagEnum getFlag(String flag) throws NoSuchElementException {
        for (FeatureFlagEnum t : values()) {
            if (t.toString().equals(flag))
                return t;
        }
        throw new NoSuchElementException("No such flag: " + flag );
    }

    public static FeatureFlagEnum getFlagByName(String flag) throws NoSuchElementException {
        for (FeatureFlagEnum t : values()) {
            if (t.getName().equals(flag))
                return t;
        }
        throw new NoSuchElementException("No flag named " + flag + " found.");
    }
}

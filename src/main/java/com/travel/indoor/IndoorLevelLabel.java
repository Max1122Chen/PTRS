package com.travel.indoor;

/**
 * OSM {@code level} 展示：常见约定 level=0 为地面，对用户展示为「1层」。
 */
public final class IndoorLevelLabel
{

    private IndoorLevelLabel()
    {
    }

    public static String displayLabel(String osmLevel)
    {
        return displayLabel(osmLevel, null);
    }

    public static String displayLabel(String osmLevel, String fallbackLabel)
    {
        if (osmLevel == null || osmLevel.isBlank())
        {
            return fallbackLabel == null || fallbackLabel.isBlank() ? "1层" : fallbackLabel;
        }
        try
        {
            int n = (int) Math.floor(Double.parseDouble(osmLevel.trim()));
            return (n + 1) + "层";
        }
        catch (NumberFormatException ex)
        {
            if (fallbackLabel != null && !fallbackLabel.isBlank())
            {
                return fallbackLabel;
            }
            return osmLevel.trim() + "层";
        }
    }
}

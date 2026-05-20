package com.travel.indoor;

/**
 * 室内节点地理距离（WGS84，米）。用于走廊等水平边；竖向边仍由配置覆盖。
 */
public final class IndoorGeo
{

    private IndoorGeo()
    {
    }

    public static boolean hasCoordinates(IndoorNodeRecord n)
    {
        return n != null && n.getLatitude() != null && n.getLongitude() != null
            && Double.isFinite(n.getLatitude()) && Double.isFinite(n.getLongitude());
    }

    /**
     * Haversine 球面距离，米。
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2)
    {
        final double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    public static double edgeLengthMeters(IndoorNodeRecord a, IndoorNodeRecord b)
    {
        if (!hasCoordinates(a) || !hasCoordinates(b))
        {
            return -1;
        }
        return haversineMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }
}

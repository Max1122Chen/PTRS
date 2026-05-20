package com.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 室内导航配置（FR-004-5）。
 */
@Component
@ConfigurationProperties(prefix = "app.indoor")
public class IndoorProperties
{

    /**
     * 电梯/楼梯边的等效距离（米），用于 Dijkstra 与走廊边权统一量纲。
     */
    private double verticalEdgeDistanceMeters = 10.0;

    /**
     * dev-seed 室内 JSON 目录（相对 classpath:dev-seed）。
     */
    private String seedSubdir = "indoor";

    public double getVerticalEdgeDistanceMeters()
    {
        return verticalEdgeDistanceMeters;
    }

    public void setVerticalEdgeDistanceMeters(double verticalEdgeDistanceMeters)
    {
        this.verticalEdgeDistanceMeters = verticalEdgeDistanceMeters;
    }

    public String getSeedSubdir()
    {
        return seedSubdir;
    }

    public void setSeedSubdir(String seedSubdir)
    {
        this.seedSubdir = seedSubdir;
    }
}

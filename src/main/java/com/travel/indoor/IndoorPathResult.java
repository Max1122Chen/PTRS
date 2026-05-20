package com.travel.indoor;

import java.util.ArrayList;
import java.util.List;

/**
 * 室内路径规划结果。
 */
public class IndoorPathResult
{

    private List<Long> path = new ArrayList<>();

    private double distanceMeters;

    private double timeSec;

    private List<IndoorPathSegment> segments = new ArrayList<>();

    private List<String> instructions = new ArrayList<>();

    public List<Long> getPath()
    {
        return path;
    }

    public void setPath(List<Long> path)
    {
        this.path = path == null ? new ArrayList<>() : path;
    }

    public double getDistanceMeters()
    {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters)
    {
        this.distanceMeters = distanceMeters;
    }

    public double getTimeSec()
    {
        return timeSec;
    }

    public void setTimeSec(double timeSec)
    {
        this.timeSec = timeSec;
    }

    public List<IndoorPathSegment> getSegments()
    {
        return segments;
    }

    public void setSegments(List<IndoorPathSegment> segments)
    {
        this.segments = segments == null ? new ArrayList<>() : segments;
    }

    public List<String> getInstructions()
    {
        return instructions;
    }

    public void setInstructions(List<String> instructions)
    {
        this.instructions = instructions == null ? new ArrayList<>() : instructions;
    }
}

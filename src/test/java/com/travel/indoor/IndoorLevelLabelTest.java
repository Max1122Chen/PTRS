package com.travel.indoor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndoorLevelLabelTest
{

    @Test
    void osmLevelZeroShouldDisplayAsFirstFloor()
    {
        assertEquals("1层", IndoorLevelLabel.displayLabel("0"));
        assertEquals("2层", IndoorLevelLabel.displayLabel("1"));
    }
}

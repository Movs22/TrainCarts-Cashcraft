package com.bergerkiller.bukkit.tc;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.utils.StationParser;


public class CashcraftTest {

    @Test
    public void testColorConversion() {
    	String[] colors = {"$Red","$CV Herigate Rail","$HS1","$HS2","$SVR","$GAR"};
    	for(String color: colors) {
    		System.out.println(color + " = " + StationParser.convertColor( color));
    	}
    }
    
    @Test
    public void testStationAnnouncement() {
    	String[] colors = {"BGO>1"};
    	for(String color: colors) {
    		System.out.println(color + " = " + StationParser.parseMetro(color, StationParser.convertColor( "$Blue")));
    	}
    	String[] rails = {"BGO>1"};
    	for(String color: rails) {
    		System.out.println(color + " = " + StationParser.parseRail(color, StationParser.convertColor( "$Blue")));
    	}
    }
    
}

package com.bergerkiller.bukkit.tc.signactions;

import java.util.HashMap;
import java.util.Map;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionReverse extends SignAction {
	
	public Boolean hasRerouted = false;
	Map<MinecartGroup,String> dests = new HashMap<MinecartGroup,String>();
    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("plat");
    }
     
    
    public static String parseStation(String string) {
		string = string.replaceAll("\\$A", "Ailsbury").replaceAll("\\$C", "Cashvillage").replaceAll("\\$F", "Fernhill").replaceAll("\\$H", "Hemstead").replaceAll("\\$N", "New Arbridge");
		string = string.replaceAll("\\$Rd", "Road").replaceAll("\\$P", "Park");
		string = string.replaceAll("\\$Q", "Quarter").replaceAll("\\$S", "Shopping Centre").replaceAll("\\$R", "Racecourse");
		string = string.replaceAll("\\$S", "South").replaceAll("\\$N", "North").replaceAll("\\$E", "East").replaceAll("\\$W", "West");
		return string;
    }
    int RouteIndex = 0;
    @Override
    public void execute(SignActionEvent info) {
    	/*
    	if(dests.get(info.getGroup()) != null && info.isAction(SignActionType.GROUP_ENTER)) {
    		
    	}*/
        
    }

    /**
     * Gets the next destination name that should be set for a single Minecart, given an event.
     * If null is returned, then no destination should be set.
     * 
     * @param cart The cart to compute the next destination for
     * @param info Event information
     * @return next destination to set, empty to clear, null to do nothing
     */
    @Override
    public String getNextDestination(PathNode info, MinecartGroup group) {
        // Parse new destination to set. If empty, returns null (set nothing)
    	if(dests.get(group) != null) return dests.get(group);
    	RouteIndex += 1;
    	if(info.getLine(5).length() < 1) return null;
    	String[] newDestinations ={info.getLine(4),info.getLine(5)};
        String newDestination = newDestinations[0];
        String DestStr = info.getLine(3);
        String PlatStr = info.getLine(6);
        if(RouteIndex > DestStr.length() - 1) {
        	RouteIndex = RouteIndex % PlatStr.length();
        }
        if(DestStr.charAt(RouteIndex) == '1') {
        	newDestination = newDestinations[0];
        } else {
        	newDestination = newDestinations[1];
        }
        if (newDestination.isEmpty()) {
            newDestination = null;
        }
        newDestination = parseStation(newDestination);
        dests.put(group, newDestination + " - " + (DestStr.charAt(RouteIndex)) );
        return newDestination + " - " + PlatStr.charAt(RouteIndex);
    }
    
    @Override
    public String getNextDestinationPredict(PathNode info, MinecartGroup group, int index) {
        // Parse new destination to set. If empty, returns null (set nothing)
    	if(info.getLine(5).length() < 1) return null;
    	String[] newDestinations ={info.getLine(4),info.getLine(5)};
        String newDestination = newDestinations[0];
        String DestStr = info.getLine(3);
        String PlatStr = info.getLine(6);
        int RouteIndex2 = RouteIndex + index;
        if(RouteIndex2 > PlatStr.length() - 1) {
        	RouteIndex2 = RouteIndex2 % PlatStr.length();
        }
        if(DestStr.charAt(RouteIndex2) == '1') {
        	newDestination = newDestinations[0];
        } else {
        	newDestination = newDestinations[1];
        }
        if (newDestination.isEmpty()) {
            newDestination = null;
        }
        newDestination = parseStation(newDestination);
        return newDestination + " - " + PlatStr.charAt(RouteIndex2);
    }
    
    @Override
    public boolean isFDest() {
    	return true;
    }
    
    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_ROUTE)
                .setName("train destination")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Destination");

        if (event.isTrainSign()) {
            opt.setDescription("set a train destination.");
        } else if (event.isCartSign()) {
            opt.setDescription("set a cart destination and the next destination to set once it is reached");
        } else if (event.isRCSign()) {
            opt.setDescription("set the destination on a remote train");
        }
        return opt.handle(event.getPlayer());
    }

    @Override
    public String getRailDestinationName(SignActionEvent info) {
        return null;
    }
    
}

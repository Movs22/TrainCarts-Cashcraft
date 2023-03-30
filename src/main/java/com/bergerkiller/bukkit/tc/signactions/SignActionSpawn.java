package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup.CenterMode;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.StationParser;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

public class SignActionSpawn extends SignAction {
	public static Boolean hasRerouted = false;
	@Override
	public Boolean isSpawner() {
		return true;
	}
	
    @Override
    public boolean match(SignActionEvent info) {
        return SpawnSign.isValid(info);
    }

    @Override
    public boolean canSupportFakeSign(SignActionEvent info) {
        // If no auto-spawning logic is used, then it is supported
        // The auto-spawning logic requires a real sign to exist for it to work...
        return SpawnSign.SpawnOptions.fromEvent(info).autoSpawnInterval == 0;
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }

        // Find and parse the spawn sign
        SpawnSign sign = info.getTrainCarts().getSpawnSignManager().create(info);
        if (sign.isActive()) {
        	sign.resetSpawnTime();
            sign.spawn(info);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!SignBuildOptions.create()
                .setPermission(Permission.BUILD_SPAWNER)
                .setName("train spawner")
                .setDescription("spawn trains on the tracks above when powered by redstone")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Spawner")
                .handle(event.getPlayer()))
        {
            return false;
        }

        // Create a new spawn sign by parsing the event
        SpawnSign sign = event.getTrainCarts().getSpawnSignManager().create(event);

        // When an interval is specified, check permission for it.
        // No permission? Cancel the building of the sign.
        if (sign.hasInterval() && !Permission.SPAWNER_AUTOMATIC.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use automatic signs")) {
            sign.remove();
            return false;
        }

        // Check for all minecart types specified, whether the player has permission for it.
        // No permission? Cancel the building of the sign.
        if (!sign.getSpawnableGroup().checkSpawnPermissions(event.getPlayer())) {
            sign.remove();
            return false;
        }

        // Success!
        if (sign.hasInterval()) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "This spawner will automatically spawn trains every " + Util.getTimeString(sign.getInterval()) + " while powered");
        }

        return true;
    }
    public static int convertSecs(String time) {
    	if(time == "" || time.contains("-")) time = "00:00:00";
    	String[] t = time.split(":");
    	if(Integer.parseInt(t[0]) < 0 || Integer.parseInt(t[1]) < 0 || Integer.parseInt(t[2]) < 0) return 0;
		return Integer.parseInt(t[0])*60*60 + Integer.parseInt(t[1])*60 + Integer.parseInt(t[2]);	
    }
    
    public static String parseSecs(int time) {
    	if(time < 1) return "00:00:00";
    	int h = time / 60 / 60;
    	int m = time / 60 % 60;
    	int s = time % 60;
    	String h1 = String.valueOf(h);
    	String m1 = String.valueOf(m);
    	String s1 = String.valueOf(s);
    	if(s < 10) s1 = "0" + "" + String.valueOf(s);
    	if(m < 10) m1 = "0" + "" + String.valueOf(m);
    	return h1 + ":" + m1 + ":" + s1;
    }
    
    public static int platIndex1 = 0;
    public static int platIndex2 = 0;

    public static int RouteIndex = 0;
    public static class SpawnSignValues {
    	public String name;
    	public int RouteIndex = 0;
    	public int platIndex1 = 0;
    	public int platIndex2 = 0;
    	public List<String> dests = new ArrayList<String>();
    	public List<String> dests2 = new ArrayList<String>();
    	public SpawnSignValues() {
    		this.name = null;
    	}
    	public void SaveValues(int R, int p1, int p2) {
    		this.RouteIndex = R;
    		this.platIndex1 = p1;
    		this.platIndex2 = p2;
    	}
    	
    	public void SaveDests(List<String> a, List<String> b) {
    		this.dests = a;
    		this.dests2 = b;
    	}
    }
    
    public static final Map<SpawnSign, SpawnSignValues> signs = new HashMap<>();

    @Override
    public void destroy(SignActionEvent info) {
        info.getTrainCarts().getSpawnSignManager().remove(info);
    }
    
    // only called from spawn sign
    public static SpawnableGroup.SpawnLocationList spawn(SpawnSign spawnSign, SignActionEvent info) throws IndexOutOfBoundsException {
    	
    	if(info.getExtraLinesBelow().length < 1) return null;
    	SpawnSignValues values;
    	if(signs.get(spawnSign) == null) {
    		signs.putIfAbsent(spawnSign, new SpawnSignValues());
    		values = new SpawnSignValues();
    	} else {
    		values = signs.get(spawnSign);
    	}
    	RouteIndex = values.RouteIndex;
    	platIndex1 = values.platIndex1;
    	platIndex2 = values.platIndex2;
    	String destStr = info.getExtraLinesBelow()[2];
    	if(destStr.equalsIgnoreCase("") || destStr.length() < 1) return null;
    	String destination = info.getExtraLinesBelow()[0];
    	destination = info.getExtraLinesBelow()[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)];
    	destination = StationParser.parseStation(destination);
        if ((info.isTrainSign() || info.isCartSign()) && info.hasRails()) {
            SpawnableGroup spawnable = spawnSign.getSpawnableGroup();
            if (spawnable.getMembers().isEmpty()) {
                return null;
            }

            // Find the movement direction vector on the rails
            // This, and the inverted vector, are the two directions in which can be spawned
            Vector railDirection;
            {
                RailState state = RailState.getSpawnState(info.getRailPiece());
                railDirection = state.motionVector();
            }

            // Figure out a preferred direction to spawn into, and whether to allow centering or not
            // This is defined by:
            // - Watched directions ([train:right]), which disables centering
            // - Which block face of the sign is powered, which disables centering
            // - Facing of the sign if no direction is set, which enables centering
            boolean isBothDirections;
            boolean useCentering;
            Vector spawnDirection;
            {
                boolean spawnA = info.isWatchedDirection(railDirection.clone().multiply(-1.0));
                boolean spawnB = info.isWatchedDirection(railDirection);
                if (isBothDirections = (spawnA && spawnB)) {
                    // Decide using redstone power if both directions are watched
                    BlockFace face = Util.vecToFace(railDirection, false);
                    spawnA = info.isPowered(face);
                    spawnB = info.isPowered(face.getOppositeFace());
                }

                if (spawnA && !spawnB) {
                    // Definitively into spawn direction A
                    spawnDirection = railDirection;
                    useCentering = false;
                } else if (!spawnA && spawnB) {
                    // Definitively into spawn direction B
                    spawnDirection = railDirection.clone().multiply(-1.0);
                    useCentering = false;
                } else {
                    // No particular direction is decided
                    // Center the train and spawn relative right of the sign
                    if (FaceUtil.isVertical(Util.vecToFace(railDirection, false))) {
                        // Vertical rails, launch downwards
                        if (railDirection.getY() < 0.0) {
                            spawnDirection = railDirection;
                        } else {
                            spawnDirection = railDirection.clone().multiply(-1.0);
                        }
                    } else {
                        // Horizontal rails, launch most relative right of the sign facing
                        Vector facingDir = FaceUtil.faceToVector(FaceUtil.rotate(info.getFacing(), -2));
                        if (railDirection.dot(facingDir) >= 0.0) {
                            spawnDirection = railDirection;
                        } else {
                            spawnDirection = railDirection.clone().multiply(-1.0);
                        }
                    }
                    useCentering = true;
                }
            }

            // If a center mode is defined in the declared spawned train, then adjust the
            // centering rule accordingly.
            if (spawnable.getCenterMode() == CenterMode.MIDDLE) {
                useCentering = true;
            } else if (spawnable.getCenterMode() == CenterMode.LEFT || spawnable.getCenterMode() == CenterMode.RIGHT) {
                useCentering = false;
            }

            // If CenterMode is LEFT, then we use the REVERSE spawn mode instead of DEFAULT
            // This places the head close to the sign, rather than the tail
            SpawnableGroup.SpawnMode directionalSpawnMode = SpawnableGroup.SpawnMode.DEFAULT;
            if (spawnable.getCenterMode() == CenterMode.LEFT) {
                directionalSpawnMode = SpawnableGroup.SpawnMode.REVERSE;
            }

            // Attempt spawning the train in priority of operations
            SpawnableGroup.SpawnLocationList spawnLocations = null;
            if (useCentering) {
                // First try spawning it centered, facing in the suggested spawn direction
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, SpawnableGroup.SpawnMode.CENTER);

                // If this hits a dead-end, in particular with single-cart spawns, try the opposite direction
                if (spawnLocations != null && !spawnLocations.can_move) {
                    Vector opposite = spawnDirection.clone().multiply(-1.0);
                    SpawnableGroup.SpawnLocationList spawnOpposite = spawnable.findSpawnLocations(
                            info.getRailPiece(), opposite, SpawnableGroup.SpawnMode.CENTER);

                    if (spawnOpposite != null && spawnOpposite.can_move) {
                        spawnDirection = opposite;
                        spawnLocations = spawnOpposite;
                    }
                }
            }

            // First try the suggested direction
            if (spawnLocations == null) {
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, directionalSpawnMode);
            }

            // Try opposite direction if not possible
            // If movement into this direction is not possible, and both directions
            // can be spawned (watched directions), also try other direction.
            // If that direction can be moved into, then use that one instead.
            if (spawnLocations == null || (!spawnLocations.can_move && isBothDirections)) {
                Vector opposite = spawnDirection.clone().multiply(-1.0);
                SpawnableGroup.SpawnLocationList spawnOpposite = spawnable.findSpawnLocations(
                        info.getRailPiece(), opposite, directionalSpawnMode);

                if (spawnOpposite != null && (spawnLocations == null || spawnOpposite.can_move)) {
                    spawnDirection = opposite;
                    spawnLocations = spawnOpposite;
                }
            }

            // If still not possible, try centered if we had not tried yet, just in case
            if (spawnLocations == null && !useCentering) {
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, SpawnableGroup.SpawnMode.CENTER);
            }

            // If still no spawn locations could be found, fail
            if (spawnLocations == null) {
                return null; // Failed
            }

            // Load the chunks first
            spawnLocations.loadChunks();

            // Check that the area isn't occupied by another train
            if (spawnLocations.isOccupied()) {
                return null; // Occupied
            }

            // Spawn and launch
            MinecartGroup group = spawnable.spawn(spawnLocations);
            double spawnForce = spawnSign.getSpawnForce();
            if (group != null && spawnForce != 0.0) {
                Vector headDirection = spawnLocations.locations.get(spawnLocations.locations.size()-1).forward;
                BlockFace launchDirection = Util.vecToFace(headDirection, false);

                // Negative spawn force launches in reverse
                if (spawnForce < 0.0) {
                    launchDirection = launchDirection.getOppositeFace();
                    spawnForce = -spawnForce;
                }

                group.head().getActions().addActionLaunch(launchDirection, 2, spawnForce);
            }
            
            /*
             * 
             */
            PathWorld world = info.getTrainCarts().getPathProvider().getWorld(info.getWorld());
            PathNode spawn = world.getNodeAtRail(info.getRails());
            PathNode dest = world.getNodeByName(destination);
            if(dest == null || dest.location == null) {
            	dest = world.getNodeByName(destination + "~1");
            	destination = destination + "~1";
            }
            if(dest == null && hasRerouted == false) {
            	world.rerouteAll();
            	//hasRerouted = true;
            	group.destroy();
            	return null;
            } 
            if(dest == null) {
            	group.destroy();
            	return null;
            }
            if((spawn == null || spawn.getLine(6).length() == 0) && hasRerouted == false) {
            	world.rerouteAll();
            	//hasRerouted = true;
            	group.destroy();
            	return null;
            }
            
            if((spawn == null || spawn.getLine(6).length() == 0) ) {
            	group.destroy();
            	return null;
            }
            if(info.getLine(3).equalsIgnoreCase("")) {
            	ArrivalSigns.timeCalcStart(spawn.getSign().trackedSign.signBlock, group.head(), false, 3, true);
            }
            String ns = "";
            int time = convertSecs(spawn.getLine(3));
            PathConnection[] route = spawn.findRoute(dest);
            TrainProperties properties = group.getProperties();
            int pst = time;
            int platIndex = 0;
            for(PathConnection node : route) {
            	if(node.destination == null) {
            		if(node.destination== null && hasRerouted == false) {
                    	world.rerouteAll();
                    	hasRerouted = true;
                    	group.destroy();
                    	return null;
                    } 
                    if(node.destination == null) {
                    	group.destroy();
                    	return null;
                    }
            	};
            	if(node.destination.isFDest() && node.destination.getLine(1).equalsIgnoreCase("plat")) {
            		if(destination.split("~")[0].equals(StationParser.parseStation(info.getExtraLinesBelow()[0])))  {
            			platIndex = platIndex1;
            		} else {
            			platIndex = platIndex2;
            		}
            		String RdestStr = node.destination.getLine(3);
            		String RplatStr = node.destination.getLine(6);
            		String Fdestination;
            		if(platIndex > RdestStr.length() - 1) {
            			platIndex = platIndex % RdestStr.length();
            		}
            		if(RdestStr.charAt(platIndex) == '1') {
            			Fdestination = node.destination.getLine(4);
                    } else {
                    	Fdestination = node.destination.getLine(5);
                    }
            		destination = StationParser.parseStation(destination);
            		destination = destination.split("~")[0] + "~" + RplatStr.charAt(platIndex);
            		
            		properties.setDestIndex( (destStr.charAt(RouteIndex) == '1' ? 0 : 1)  );
            		properties.setDestination(destination);
            		
            		properties.setFDestIndex(RdestStr.charAt(platIndex));
            		PathNode ReverseDest = world.getNodeByName(destination);
            		if(ReverseDest == null) {
            			group.getTrainCarts().getLogger().log(Level.WARNING, "[Train_Carts] Failed to find node " + destination + ". Got " + ReverseDest);
            			group.destroy();
            			return null;
            		}
            		properties.setFDestination(Fdestination);
            		PathNode FinalDest = world.getNodeByName(Fdestination);
            		if(FinalDest == null || FinalDest.location == null) {
            			FinalDest = world.getNodeByName(Fdestination + "~1");
            		}
                    if(FinalDest == null || FinalDest.location == null) {
                    	group.getTrainCarts().getLogger().log(Level.WARNING, "[Train_Carts] Failed to find node " + Fdestination + ". Got " + FinalDest);
                    	group.destroy();
                    	return null;
                    } 
                    ns = Fdestination;
                    //Adds stations from Spawn > Dest and triggers the PIS on its way
                    PathConnection[] route2 = spawn.findRoute(ReverseDest);
                    for(PathConnection node2 : route2) {
                    	if(node2.destination == null) continue;
                    	if(node2.destination.getLine(1).equalsIgnoreCase("closed")) {
                    		properties.addStation(node2.destination.getName().split("~")[0], false);
                    	} else {
                    		if(node2.destination.isStation()) {
                        		properties.addStation(node2.destination.getName().split("~")[0], true, node2.destination.getLine(7).split("#")[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)]);
                    			int l = 4 + (destStr.charAt(RouteIndex) == '1' ? 0 : 1);       
                        		time = time + convertSecs( node2.destination.getLine(l)) + Integer.parseInt(node2.destination.getLine(1).split(" ")[2]);
                        		if(node2.destination.getLine(1).contains("T") && !ns.equals("")) {
                            		//ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign , group.head(), node2.destination.getLine(7).split("#")[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)], parseSecs(pst), true, ns);
                            	} else {
                            		//ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign, group.head(), node2.destination.getLine(7).split("#")[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)], parseSecs(pst), true, destination);
                            	}
                        		pst = time;
                    		}
                    	}
                    }
                    
                    //Adds stations from Dest > FinalDest and triggers the PIS on its way.
                    PathConnection[] Rroute = ReverseDest.findRoute(FinalDest);
                    for(PathConnection node2 : Rroute) {
                    		if(node2.destination.isStation() && !node2.destination.getLine(1).equalsIgnoreCase("closed")) {
                    			properties.addFStation(node2.destination.getName().split("~")[0], true, node2.destination.getLine(7).split("#")[0]);
                    			int l = 4;       
                        		time = time + convertSecs( node2.destination.getLine(l)) + Integer.parseInt(node2.destination.getLine(1).split(" ")[2]);
                            	//ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign , group.head(), node2.destination.getLine(7).split("#")[0], parseSecs(pst), true, ns);
                        		pst = time;
                    		}
                    }
            		platIndex += 1;
            		if(platIndex > node.destination.getLine(3).length() - 1) {
            			platIndex = platIndex % node.destination.getLine(3).length();
            		}
            		if(destination.split("~")[0].equals(StationParser.parseStation(info.getExtraLinesBelow()[0])))  {
            			platIndex1 = platIndex;
            		} else {
            			platIndex2 = platIndex;
            		}
            	}
            }
            RouteIndex += 1;
            if(RouteIndex > spawn.getLine(6).length() - 1) {
        		RouteIndex = RouteIndex % spawn.getLine(6).length();
        	}
        	values.SaveValues(RouteIndex, platIndex1, RouteIndex);
        	values.RouteIndex = RouteIndex;
        	values.platIndex1 = platIndex1;
        	values.platIndex2 = platIndex2;
        	signs.put(spawnSign, values);
        	
            //checks the next trains to spawn and their routes (and triggers the PIS)
            /*PathConnection[] Froute;
            PathNode Fspawn;
            PathNode Fdest;*/
            //TODO:
            //Fix incorrect timings being added
            //First station is getting the last train arrivals (i.e. 9:XX) instead of the first train arrivals
            //TODO: FIXED
            
            /*for(int i = 1; i < spawn.getLine(6).length(); i++) {
            	if(RouteIndex > spawn.getLine(6).length() - 1) {
            		RouteIndex = RouteIndex % spawn.getLine(6).length();
            	}
            	destination = info.getExtraLinesBelow()[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)];
            	destination = StationParser.parseStation(destination);
            	time = convertSecs(spawn.getLine(3)) + i*30;
                Fdest = world.getNodeByName(destination + "~1");
                Froute = spawn.findRoute(Fdest);
                for(PathConnection node : Froute) {
                	if(node.destination == null) continue;
                	if(node.destination.isFDest() && node.destination.getLine(1).equalsIgnoreCase("plat")) {
                		if(destination.split("~")[0].equals(StationParser.parseStation(info.getExtraLinesBelow()[0])))  {
                			platIndex = platIndex1;
                		} else {
                			platIndex = platIndex2;
                		}
                		String RdestStr = node.destination.getLine(3);
                		String RplatStr = node.destination.getLine(6);
                		String Fdestination;
                		if(RdestStr.charAt(platIndex) == '1') {
                			Fdestination = node.destination.getLine(4);
                        } else {
                        	Fdestination = node.destination.getLine(5);
                        }
                		destination = StationParser.parseStation(destination);
                		destination = destination.split("~")[0] + "~" + RplatStr.charAt(platIndex);
                		PathNode ReverseDest = world.getNodeByName(destination);
                		PathNode FinalDest = world.getNodeByName(Fdestination);
                		if(FinalDest == null || FinalDest.location == null) {
                			FinalDest = world.getNodeByName(Fdestination + "~1");	
                		}
                        if(FinalDest == null || FinalDest.location == null) {
                        	System.out.println("Failed to find node " + Fdestination + "~1");
                        	break;
                        } 
                        ns = Fdestination;
                        pst = time;
                        //Adds stations from Spawn > Dest and triggers the PIS on its way
                        PathConnection[] route2 = spawn.findRoute(ReverseDest);
                        for(PathConnection node2 : route2) {
                        	if(node2.destination == null) continue;
                        	if(node2.destination.getLine(1).equalsIgnoreCase("closed")) {
                        	} else {
                        		if(node2.destination.isStation()) {
                        			int l = 4 + (destStr.charAt(RouteIndex) == '1' ? 0 : 1);       
                            		time = time + convertSecs( node2.destination.getLine(l)) + Integer.parseInt(node2.destination.getLine(1).split(" ")[2]);
                            		if(node2.destination.getLine(1).contains("T") && !ns.equals("")) {
                                		ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign , group.head(), node2.destination.getLine(7).split("#")[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)], parseSecs(pst), true, ns);
                                	} else {
                                		ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign, group.head(), node2.destination.getLine(7).split("#")[(destStr.charAt(RouteIndex) == '1' ? 0 : 1)], parseSecs(pst), true, destination);
                                	}
                            		pst = time;
                        		}
                        	}
                        }
                        
                        //Adds stations from Dest > FinalDest and triggers the PIS on its way.
                        PathConnection[] Rroute = ReverseDest.findRoute(FinalDest);
                        for(PathConnection node2 : Rroute) {
                        		if(node2.destination.isStation() && !node2.destination.getLine(1).equalsIgnoreCase("closed")) {
                        			int l = 4;       
                            		time = time + convertSecs( node2.destination.getLine(l)) + Integer.parseInt(node2.destination.getLine(1).split(" ")[2]);
                                	ArrivalSigns.trigger(node2.destination.getSign().trackedSign.sign , group.head(), node2.destination.getLine(7).split("#")[0], parseSecs(pst), true, ns);
                            		pst = time;
                        		}
                        }
                		platIndex += 1;
                		if(platIndex > node.destination.getLine(3).length() - 1) {
                			platIndex = platIndex % node.destination.getLine(3).length();
                		}
                		if(destination.split("~")[0].equals(StationParser.parseStation(info.getExtraLinesBelow()[0])))  {
                			platIndex1 = platIndex;
                		} else {
                			platIndex2 = platIndex;
                		}
                	}
                }
                RouteIndex += 1;
            }*/
            return spawnLocations;
        }
        return null;
    }

        
    /**
     * Gets the Minecart spawn positions into a certain direction.
     * The first location is always the startLoc Location.
     * With atCenter is true, the first cart spawned will be positioned at the start location,
     * even if that width clips through other blocks. When false, it will be spawned at an offset away
     * to make sure the cart edge does not clip past startLoc.
     *
     * @param startLoc position to start spawning from
     * @param atCenter whether the first spawn position is the startLoc (true), or an offset away (false)
     * @param directionFace of spawning
     * @param types of spawnable members to spawn
     * @return spawn locations list. Number of locations may be less than the number of types.
     * @deprecated There are now methods for this in {@link SpawnableGroup}
     */
    @Deprecated
    public static List<Location> getSpawnPositions(Location startLoc, boolean atCenter, BlockFace directionFace, List<SpawnableMember> types) {
        return getSpawnPositions(startLoc, atCenter, FaceUtil.faceToVector(directionFace), types);
    }

    /**
     * Gets the Minecart spawn positions into a certain direction.
     * The first location is always the startLoc Location.
     * With atCenter is true, the first cart spawned will be positioned at the start location,
     * even if that width clips through other blocks. When false, it will be spawned at an offset away
     * to make sure the cart edge does not clip past startLoc.
     * 
     * @param startLoc position to start spawning from
     * @param atCenter whether the first spawn position is the startLoc (true), or an offset away (false)
     * @param direction of spawning
     * @param types of spawnable members to spawn
     * @return spawn locations list. Number of locations may be less than the number of types.
     * @deprecated There are now methods for this in {@link SpawnableGroup}
     */
    @Deprecated
    public static List<Location> getSpawnPositions(Location startLoc, boolean atCenter, Vector direction, List<SpawnableMember> types) {
        List<Location> result = new ArrayList<Location>(types.size());
        if (atCenter && types.size() == 1) {
            // Single-minecart spawning logic
            // Require there to be one extra free rail in the direction we are spawning
            if (MinecartMemberStore.getAt(startLoc) == null) {
                TrackWalkingPoint walker = new TrackWalkingPoint(startLoc, direction);
                Location firstPos = walker.state.positionLocation();
                walker.skipFirst();
                if (walker.moveFull()) {
                    result.add(firstPos);
                }
            }
        } else {
            // Multiple-minecart spawning logic
            TrackWalkingPoint walker = new TrackWalkingPoint(startLoc, direction);
            walker.skipFirst();
            for (int i = 0; i < types.size(); i++) {
                SpawnableMember type = types.get(i);
                if (atCenter && i == 0) {
                    if (!walker.move(0.0)) {
                        break;
                    }
                } else {
                    if (!walker.move(0.5 * type.getLength() - (i == 0 ? 0.5 : 0.0))) {
                        break;
                    }
                }
                result.add(walker.state.positionLocation());
                if ((i == types.size() - 1) || !walker.move(0.5 * type.getLength() + TCConfig.cartDistanceGap)) {
                    break;
                }
            }
        }
        return result;
    }
}
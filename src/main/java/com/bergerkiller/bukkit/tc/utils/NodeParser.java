package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.World;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;

public class NodeParser {
	
	public PathNode getNode(String s, World w, TrainCarts p) {
		PathWorld d = p.getPathProvider().getWorld(w);
		if(d == null) return null;
		PathNode n = d.getNodeByName(s);
		if(n == null) return null;
		return n;
	}
}
package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.StationParser;

public class SignActionStationC extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("closed");
	}
	

	@Override
	public String getStationName(SignActionEvent info) {
		String station;
		station = (StationParser.parseStation(info.getLine(3)).equalsIgnoreCase("")) ? null : StationParser.parseStation(info.getLine(3));
		return station;
	}	

	@Override
	public Boolean isStation() {
		return true;
	}
	
	@Override
	public void execute(SignActionEvent info) {
		
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		return SignBuildOptions.create().setPermission(Permission.BUILD_STATION).setName("station (closed)")
				.setDescription("mark a station as closed so trains dont stop there").setTraincartsWIKIHelp("TrainCarts/Signs/Station")
				.handle(event.getPlayer());
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}
}

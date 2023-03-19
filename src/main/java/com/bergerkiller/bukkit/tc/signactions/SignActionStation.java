package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Station;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitStationRouting;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.StationParser;
import com.bergerkiller.bukkit.tc.utils.TimeDurationFormat;
import com.google.gson.stream.JsonReader;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class SignActionStation extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("station") && info.getMode() != SignActionMode.NONE;
	}



	public static void announce(SignActionEvent info, MinecartGroup group, String message, String color, Boolean play,
			Boolean raw) {
		for (MinecartMember<?> member : group) {
			announce(info, member, message, color, play, raw);
		}
	}

	public static void announce(SignActionEvent info, MinecartMember<?> member, String message, String color,
			Boolean play, Boolean raw) {
		if(message == null) return;
		for (Player player : member.getEntity().getPlayerPassengers()) {
			color = StationParser.convertColor(color);
			if (raw) {
				player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ComponentSerializer.parse(message.toString()));
			} else {
				player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
						ComponentSerializer.parse("{\"text\":\"" + message + "\", \"color\":\"" + color + "\"}"));
			}
			if (play) {
				player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 10, 1);
				new java.util.Timer().schedule(new java.util.TimerTask() {
					@Override
					public void run() {
						player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 10, (float) 0.85);
					}
				}, 300);
			}
		}
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

		if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE)) {
			return;
		}
		if (!info.hasRails() || !info.hasGroup() || info.getGroup().isEmpty()) {
			return;
		}
		if(info.getExtraLinesBelow().length < 4) return;
		MinecartGroup group = info.getGroup();
		Station station = new Station(info);
		if (info.isAction(SignActionType.GROUP_LEAVE)) {
			if(station.isTerminus() && !info.getGroup().getProperties().getDestination().equalsIgnoreCase(info.getGroup().getProperties().getFDestination()) && info.getGroup().getProperties().getFDestination() != null) {
				info.getGroup().getProperties().loadNext();
			}
			if (info.getGroup().getActions().isWaitAction()) {
				info.getGroup().getActions().clear();
			}
			if(!info.getExtraLinesBelow()[0].equalsIgnoreCase("") && !info.getExtraLinesBelow()[1].equalsIgnoreCase("")) {
				if(info.getGroup().getProperties().getStations().toArray().length > 0) {
					announce(info, info.getGroup(), "This is a " + info.getLine(2).replaceAll("\\$", "") + " Line service to " + info.getGroup().getProperties().getDestination().split("~")[0] + ".", StationParser.convertColor(info.getLine(2)), true, false);
					if(info.getGroup().getProperties().checkNextStation() == false) {
						Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
							announce(info, info.getGroup(), "The next station is closed.", StationParser.convertColor(info.getLine(2)), true, false);
						}, 60L);
					}
					Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
						announce(info, info.getGroup(), "The next station is " + info.getGroup().getProperties().getNextStation(true) + ".", StationParser.convertColor(info.getLine(2)), true, false);
					}, info.getGroup().getProperties().checkNextStation() == true ? 60L : 120L);
				}
			}
			info.setLevers(false);
			return;
		}
		if (info.getFacing() != info.getGroup().head().getDirection().getOppositeFace()) return;
		
		// Check if not already targeting
		
		if(!info.getGroup().getProperties().getStations().contains(StationParser.parseStation(info.getLine(3).split("~")[0]))) return;
		if (info.isAction(SignActionType.GROUP_ENTER) && info.getFacing() == info.getGroup().head().getDirection().getOppositeFace()) {
			ArrivalSigns.trigger(info.getSign(), info.getMember(), info.getExtraLinesBelow()[3].split("#")[group.getProperties().getDestIndex()], "-" + (station.getDelay()/1000 + 0.35), true, null, true);
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				//ArrivalSigns.timeCalcStartMDest(info.getSign().getBlock(), info.getMember(), info.getGroup().getProperties().getDestIndex(), 4, 5, true);
				if(info.getExtraLinesBelow()[0].equalsIgnoreCase("") || info.getExtraLinesBelow()[1].equalsIgnoreCase("")) {
	            	ArrivalSigns.timeCalcStartMDest(info.getSign().getBlock(), info.getMember(), info.getGroup().getProperties().getDestIndex(), 4, 5, true);
	            }
				if(group.getProperties().getFDestination() != null) {
					ArrivalSigns.trigger(info.getSign(), info.getMember(), group.getProperties().getNextStationVar(true), info.getExtraLinesBelow()[info.getGroup().getProperties().getDestIndex()], true, "[KEEP]", true);
				} else {
					ArrivalSigns.trigger(info.getSign(), info.getMember(), group.getProperties().getNextStationVar(true), info.getExtraLinesBelow()[info.getGroup().getProperties().getDestIndex()], true, "[KEEP]", true);
				}
			}, station.getDelay()/50);
			info.getGroup().getProperties().stop(StationParser.parseStation(info.getLine(3)).split("~")[0]);
			announce(info, info.getGroup(), "This station is " + StationParser.parseStation(info.getLine(3).split("~")[0]) + ".", info.getLine(2),
					true, false);
			Long t = 50L;
			if(info.getGroup().getProperties().getStations().toArray().length  < 1 || info.getGroup().getProperties().getDestination().split("~")[0].equalsIgnoreCase(StationParser.parseStation(info.getLine(3).split("~")[0])) ) {
				Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
					announce(info, info.getGroup(), "This train terminates here. Please take all your belongings when leaving the train.",
							StationParser.convertColor(info.getLine(2)), false, false);
				}, t);
				t += 50L;
			}
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				announce(info, info.getGroup(), "Mind the gap between the train and the platform.",
						StationParser.convertColor(info.getLine(2)), false, false);
			}, t);
			t += 50L;
			if (StationParser.parseMetro(info.getExtraLinesBelow()[2], info.getLine(2)) != null && StationParser.parseMetro(info.getExtraLinesBelow()[2], info.getLine(2)) != "[]") {
				Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
					announce(info, info.getGroup(),
							StationParser.parseMetro(info.getExtraLinesBelow()[2], StationParser.convertColor(info.getLine(2))),
							StationParser.convertColor(info.getLine(2)), false, true);
				}, t);
				t += 50L;
			} 
			if (StationParser.parseRail(info.getExtraLinesBelow()[2], info.getLine(2)) != null && StationParser.parseMetro(info.getExtraLinesBelow()[2], info.getLine(2)) != "[]") {
				Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
						announce(info, info.getGroup(),
								StationParser.parseRail(info.getExtraLinesBelow()[2], StationParser.convertColor(info.getLine(2))),
								StationParser.convertColor(info.getLine(2)), false, true);
				}, t);
			}
		}

		// What do we do?
		if (station.getInstruction() == null) {
			// Clear actions, but only if requested to do so because of a redstone change
			if (info.isAction(SignActionType.REDSTONE_CHANGE)) {
				if (info.getGroup().getActions().isCurrentActionTag(station.getTag())) {
					info.getGroup().getActions().clear();
				}
			}
		} else if (station.getInstruction() == BlockFace.SELF) {
			MinecartMember<?> centerMember = station.getCenterPositionCart();
			// Do not allow redstone changes to center a launching train
			if (info.isAction(SignActionType.REDSTONE_CHANGE)
					&& (centerMember.isMovementControlled() || info.getGroup().isMoving())) {
				return;
			}

			// This erases all previous (station/launch) actions scheduled for the train
			// It allows this station to fully redefine what the train should be doing
			group.getActions().launchReset();

			// Train is waiting on top of the station sign indefinitely, with no
			// end-condition
			if (!station.isAutoRouting() && station.getNextDirection() == Direction.NONE) {
				station.centerTrain();
				station.waitTrain(Long.MAX_VALUE);
				return;
			}

			// If auto-routing, perform auto-routing checks and such
			// All this logic runs one tick delayed, because it is possible a destination
			// sign
			// sits on the same block as this station sign. We want the destination sign
			// logic
			// to execute before the station does routing, otherwise this can go wrong.
			// It also makes it much easier to wait for path finding to finish, or for
			// a (valid) destination to be set on the train.
			if (station.isAutoRouting()) {
				// If there's a delay, wait for that delay and toggle levers, but do
				// not toggle levers back up after the delay times out. This is because
				// the actual station routing logic may want to hold the train for longer.
				if (station.hasDelay()) {
					station.centerTrain();
					station.waitTrainKeepLeversDown(station.getDelay());
				}

				// All the station auto-routing logic occurs in this action, which may spawn
				// new actions such as centering the train or launching it again.
				group.getActions()
						.addAction(new GroupActionWaitStationRouting(station, info.getRailPiece(), station.hasDelay()))
						.addTag(station.getTag());
				return;
			}

			// Order the train to center prior to launching again if not launching into the
			// same
			// direction the train is already moving. Respect any set delay on the sign.
			// Levers are automatically toggled as part of waiting
			BlockFace trainDirection = station.getNextDirectionFace();
			if (station.hasDelay()) {
				station.centerTrain();
				station.waitTrain(station.getDelay());
			} else if (!info.getMember().isDirectionTo(trainDirection)) {
				station.centerTrain();
			}

			// Launch into the direction
			station.launchTo(trainDirection);
		} else {
			// Launch
			group.getActions().launchReset();

			if (station.hasDelay()
					|| (group.head().isMoving() && !info.getMember().isDirectionTo(station.getInstruction()))) {
				// Reversing or has delay, need to center it in the middle first
				station.centerTrain();
			}
			if (station.hasDelay()) {
				station.waitTrain(station.getDelay());
			}
			station.launchTo(station.getInstruction());
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		return SignBuildOptions.create().setPermission(Permission.BUILD_STATION).setName("station")
				.setDescription("stop, wait and launch trains").setTraincartsWIKIHelp("TrainCarts/Signs/Station")
				.handle(event.getPlayer());
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}
}

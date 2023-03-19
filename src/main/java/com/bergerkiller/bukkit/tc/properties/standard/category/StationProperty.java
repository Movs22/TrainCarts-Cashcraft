package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.List;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Quoted;

/**
 * The current destination a train is going for. May also update the
 * {@link StandardProperties#DESTINATION_ROUTE_INDEX} when new destinations
 * are set.
 */
public final class StationProperty implements ICartProperty<String> {

    @CommandTargetTrain
    @CommandMethod("train station none")
    @CommandDescription("Clears the destination set for a train")
    private void commandClearProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        commandSetProperty(sender, properties, "");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("destination")
    @CommandMethod("train station add <station>")
    @CommandDescription("Add's a station to the train's route.")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Quoted @Argument(value="station") String destination
    ) {
        properties.addStation(destination, true);
        commandGetProperty(sender, properties);
    }
    
    @PropertyCheckPermission("destination")
    @CommandMethod("train station stop <station>")
    @CommandDescription("Add's a station to the train's route.")
    private void commandRemoveProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Quoted @Argument(value="station") String destination
    ) {
        properties.stop(destination);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train station")
    @CommandDescription("Displays the current destination set for the train")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getStations() != null && properties.getStations().toArray().length > 0) {
        	String stations = "";
        	for(String s : properties.getStations()) {
        		stations = properties.getStations().contains(s) ? stations + ChatColor.GREEN + s + ChatColor.WHITE : stations + ChatColor.GREEN + s + ChatColor.WHITE;
        		if(properties.getStations().indexOf(s) < properties.getStations().toArray().length - 2) {
        			stations = stations + ", ";
        		} else {
        			stations = stations + ".";
        		}
        	}
            sender.sendMessage(ChatColor.YELLOW + "Train calls at: "
                    + ChatColor.WHITE + stations);
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train calls at: "
                    + ChatColor.RED + "None");
        }
    }
    
    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_DESTINATION.has(sender);
    }

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "Stations", String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, "Stations", value);
    }

    
}

package com.bergerkiller.bukkit.tc.controller.status;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.events.MutexZoneConflictEvent;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlot;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlotType;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

/**
 * A single action or behavior the train is currently doing
 * 
 * @see {@link TrainStatusProvider}
 */
public interface TrainStatus {

    /**
     * Gets a message summary about this status. This is what is sent in chat
     * when requesting an overview.
     *
     * @return Message
     */
    String getMessage();

    /**
     * Gets a message summary about this status. This is what is sent in chat
     * when requesting an overview. Chat styling like hover tooltips and clickable
     * links can be included. By default calls {@link #getMessage()} and formats it
     * to a ChatText.
     *
     * @return ChatText Message
     */
    default ChatText getChatMessage() {
        return ChatText.fromMessage(getMessage());
    }

    /**
     * Train is launching to a new speed
     */
    public static final class Launching implements TrainStatus {
        private final double targetSpeed;
        private final double targetSpeedLimit;
        private final LauncherConfig config;

        public Launching(double targetSpeed, double targetSpeedLimit, LauncherConfig config) {
            this.targetSpeed = targetSpeed;
            this.targetSpeedLimit = targetSpeedLimit;
            this.config = config;
        }

        public double getTargetSpeed() {
            return this.targetSpeed;
        }

        public double getTargetSpeedLimit() {
            return this.targetSpeedLimit;
        }

        public LauncherConfig getConfig() {
            return this.config;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Launching to a speed of ").append(ChatColor.WHITE);
            if (Double.isNaN(this.targetSpeedLimit) || this.targetSpeed <= this.targetSpeedLimit) {
                str.append(DebugToolUtil.formatNumber(this.targetSpeed)).append("b/t");
            } else {
                str.append(DebugToolUtil.formatNumber(this.targetSpeedLimit)).append("b/t");
                str.append(ChatColor.YELLOW).append(" (").append(ChatColor.WHITE).append('+');
                str.append(DebugToolUtil.formatNumber(this.targetSpeed - this.targetSpeedLimit));
                str.append(ChatColor.YELLOW).append(" energy)");
            }
            str.append(ChatColor.YELLOW);
            if (this.config.hasDuration()) {
                str.append(" for ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getDuration()))
                   .append(ChatColor.YELLOW).append(" ticks");
            } else if (this.config.hasDistance()) {
                str.append(" over a distance of ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getDistance()))
                   .append(ChatColor.YELLOW).append(" blocks");
            } else if (this.config.hasAcceleration()) {
                str.append(" at ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getAcceleration()))
                   .append(ChatColor.YELLOW).append("b/t/t");
            }
            return str.toString();
        }
    }

    /**
     * Train is immobile, waiting for a certain condition to occur
     */
    public static interface Waiting extends TrainStatus {
    }

    /**
     * Waiting forever (at a station sign, etc.)
     */
    public static final class WaitingForever implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Waiting forever for an external trigger";
        }
    }

    /**
     * Waiting for a period of time, on a timer (station usually)
     */
    public static final class WaitingForDuration implements Waiting {
        private final long durationMillis;

        public WaitingForDuration(long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public long getDuration() {
            return this.durationMillis;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.RED).append("Waiting for a time of ").append(ChatColor.WHITE);
            double timeSeconds = (double) this.durationMillis / 1000.0;
            int timeMinutes = (int) timeSeconds / 60;
            if (timeMinutes > 0) {
                timeSeconds -= timeMinutes * 60;
                str.append(timeMinutes).append(" minutes");
                str.append(ChatColor.RED).append(" and ").append(ChatColor.WHITE);
                str.append((int) timeSeconds).append(" seconds");
            } else {
                str.append(timeSeconds).append(" seconds");
            }
            return str.toString();
        }
    }

    /**
     * Waiting for the pathfinding module to finish calculation the new routes
     */
    public static final class WaitingForRouting implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Waiting for path finding router to finish";
        }
    }

    /**
     * Train is not moving, and is therefore waiting, because the speed limit is set to 0
     */
    public static final class WaitingZeroSpeedLimit implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Waiting because the speed limit is set to " + ChatColor.RED + "zero";
        }
    }

    /**
     * Waiting for a configured wait delay to elapse
     */
    public static final class WaitingForDelay implements Waiting {
        private final double durationSeconds;

        public WaitingForDelay(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Waiting for configured delay, " + DebugToolUtil.formatNumber(durationSeconds) +
                    " seconds remaining";
        }
    }

    /**
     * Train is waiting for a train up ahead to move away. This is the case when
     * waiting at a waiter sign, or when a train is standing still up ahead and
     * a wait distance is set.
     */
    public static final class WaitingForTrain implements Waiting {
        private final MinecartMember<?> member;
        private final double distance;

        public WaitingForTrain(MinecartMember<?> member, double distance) {
            this.member = member;
            this.distance = distance;
        }

        public MinecartMember<?> getMember() {
            return this.member;
        }

        public double getDistance() {
            return this.distance;
        }

        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Waiting for train " +
                    ChatColor.RED + this.member.getGroup().getProperties().getTrainName() +
                    ChatColor.YELLOW + " which is " +
                    ChatColor.WHITE + DebugToolUtil.formatNumber(this.distance) +
                    ChatColor.YELLOW + " blocks up ahead";
        }
    }

    /**
     * Train is waiting for a mutex zone that is currently occupied by another train.
     */
    public static final class WaitingForMutexZone implements Waiting {
        private final MutexZone zone;

        public WaitingForMutexZone(MutexZone zone) {
            this.zone = zone;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Waiting for mutex zone");
            OfflineBlock pos = zone.signBlock;
            if (!zone.slot.isAnonymous()) {
                str.append(" ").append(ChatColor.RED).append(zone.slot.getNameWithoutWorldUUID());
            }
            str.append(ChatColor.YELLOW).append(" at sign ");
            str.append(ChatColor.RED);
            str.append(pos.getX()).append("/").append(pos.getY()).append("/").append(pos.getZ());

            List<MinecartGroup> groups = zone.slot.getCurrentGroups();
            if (!groups.isEmpty()) {
                str.append(ChatColor.YELLOW).append(" currently occupied by ");
                str.append(ChatColor.RED);
                for (int i = 0; i < groups.size(); i++) {
                    if (i > 0) {
                        str.append(", ");
                    }
                    str.append(groups.get(i).getProperties().getTrainName());
                }
            }

            return str.toString();
        }
    }

    /**
     * Train is moving and approaching/following another train up ahead
     */
    public static final class FollowingTrain implements TrainStatus {
        private final MinecartMember<?> member;
        private final double distance;
        private final double speed;

        public FollowingTrain(MinecartMember<?> member, double distance, double speed) {
            this.member = member;
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW);
            if (member.getForce() > 1e-4) {
                str.append("Following train ");
            } else {
                str.append("Approaching train ");
            }
            str.append(ChatColor.WHITE).append(this.member.getGroup().getProperties().getTrainName());
            str.append(ChatColor.YELLOW).append(" at a speed of ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.speed)).append("b/t");
            str.append(ChatColor.YELLOW + " which is ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.distance));
            str.append(ChatColor.YELLOW).append(" blocks up ahead");
            return str.toString();
        }
    }

    public static final class ApproachingMutexZone implements TrainStatus {
        private final MutexZone zone;
        private final double distance;
        private final double speed;

        public ApproachingMutexZone(MutexZone zone, double distance, double speed) {
            this.zone = zone;
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Approaching mutex zone ");
            OfflineBlock pos = zone.signBlock;
            if (!zone.slot.isAnonymous()) {
                str.append(ChatColor.RED).append(zone.slot.getName());
                str.append(ChatColor.YELLOW).append(" at ");
            }
            str.append(ChatColor.WHITE);
            str.append(pos.getX()).append("/").append(pos.getY()).append("/").append(pos.getZ());

            str.append(ChatColor.YELLOW).append(", ").append(ChatColor.WHITE);
            str.append(DebugToolUtil.formatNumber(this.distance)).append(ChatColor.YELLOW).append(" blocks ahead");

            List<MinecartGroup> groups = zone.slot.getCurrentGroups();
            if (!groups.isEmpty()) {
                str.append(", currently occupied by ");
                str.append(ChatColor.RED);
                for (int i = 0; i < groups.size(); i++) {
                    if (i > 0) {
                        str.append(", ");
                    }
                    str.append(groups.get(i).getProperties().getTrainName());
                }
                str.append(ChatColor.YELLOW);
            }

            str.append(", slowed down to a speed of ").append(ChatColor.WHITE);
            str.append(DebugToolUtil.formatNumber(this.speed)).append("b/t");

            return str.toString();
        }
    }

    public static final class WaitingAtRailBlock implements TrainStatus {
        private final RailPiece rail;

        public WaitingAtRailBlock(RailPiece rail) {
            this.rail = rail;
        }

        @Override
        public String getMessage() {
            Block block = rail.block();
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Waiting at rail ");
            str.append(ChatColor.RED).append(block.getX())
                                     .append("/").append(block.getY())
                                     .append("/").append(block.getZ());
            return str.toString();
        }
    }

    public static final class ApproachingRailSpeedTrap implements TrainStatus {
        private final RailPiece rail;
        private final double distance;
        private final double speedLimit;

        public ApproachingRailSpeedTrap(RailPiece rail, double distance, double speedLimit) {
            this.rail = rail;
            this.distance = distance;
            this.speedLimit = speedLimit;
        }

        @Override
        public String getMessage() {
            Block block = rail.block();
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Approaching speed trap at rail ");
            str.append(ChatColor.WHITE).append(block.getX())
                                     .append("/").append(block.getY())
                                     .append("/").append(block.getZ());
            str.append(ChatColor.YELLOW).append(" of speed ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.speedLimit));
            str.append(ChatColor.YELLOW).append(" which is ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.distance));
            str.append(ChatColor.YELLOW).append(" blocks away");
            return str.toString();
        }
    }

    public static final class KeepingChunksLoaded implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Is keeping chunks " + ChatColor.GREEN + "loaded";
        }
    }

    /**
     * Speed is 0 because an entity maxspeed of 0 was set
     */
    public static final class NotMovingSpeedLimited implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Has zero velocity: not moving because something imposed a speed limit";
        }
    }

    public static final class NotMoving implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Has zero velocity: is not moving";
        }
    }

    public static final class Moving implements TrainStatus {
        private final double speed;

        public Moving(double speed) {
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            return ChatColor.GREEN + "Is moving at " + ChatColor.WHITE +
                    DebugToolUtil.formatNumber(this.speed) + "b/t";
        }
    }

    public static final class Derailed implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Is (partially) derailed";
        }
    }

    public static final class EnteredMutexZone implements TrainStatus {
        private final MutexZoneSlot slot;
        private final List<MutexZone> zones;
        private final MutexZoneSlot.EnteredGroup group;

        public EnteredMutexZone(MutexZoneSlot slot, List<MutexZone> zones, MutexZoneSlot.EnteredGroup group) {
            this.slot = slot;
            this.zones = zones;
            this.group = group;
        }

        private boolean isSmart() {
            for (MutexZone zone : zones) {
                if (zone.type == MutexZoneSlotType.SMART) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.GREEN);
            if (group != null && !group.hardEnter) {
                str.append("Approaching");
            } else {
                str.append("Entered");
            }
            if (isSmart()) {
                str.append(" smart");
            }
            str.append(" mutex zone");
            if (!slot.isAnonymous()) {
                str.append(" ").append(ChatColor.WHITE).append(slot.getNameWithoutWorldUUID());
            }

            {
                boolean first = true;
                str.append(ChatColor.GREEN).append(" at signs ");
                for (MutexZone zone : zones) {
                    if (first) {
                        first = false;
                    } else {
                        str.append(ChatColor.GREEN).append(", ");
                    }
                    OfflineBlock pos = zone.signBlock;
                    str.append(ChatColor.WHITE);
                    str.append("[").append(pos.getX()).append("/").append(pos.getY())
                       .append("/").append(pos.getZ()).append("]");
                }
            }

            return str.toString();
        }

        @Override
        public ChatText getChatMessage() {
            // Base info
            ChatText text = ChatText.fromMessage(this.getMessage() + ChatColor.GREEN + " - ");

            // Add a clickable link to view entered rail blocks
            if (group != null) {
                StringBuilder str = new StringBuilder();
                str.append("Full Name: ").append(slot.getName()).append("\r\n");
                str.append("Active: ").append(group.active).append("\r\n");
                str.append("Entered Mutex: ").append(group.hardEnter).append("\r\n");
                str.append("Distance To Mutex: ").append(group.distanceToMutex).append("\r\n");

                str.append("Mutex Signs:\r\n");
                for (MutexZone zone : zones) {
                    OfflineBlock pos = zone.signBlock;
                    str.append("  [").append(pos.getX()).append("/").append(pos.getY())
                       .append("/").append(pos.getZ()).append("]\r\n");
                }

                if (group.active) {
                    str.append("Locked Rail Blocks:\r\n");
                    for (MutexZoneSlot.RailSlot slot : group.getLastPath()) {
                        str.append("  ");
                        slot.debugPrint(str);
                        str.append("\r\n");
                    }
                } else {
                    List<MutexZoneSlot.RailSlot> rails = group.getLastPath();
                    if (rails.isEmpty()) {
                        str.append("Waiting for Rail: Unknown\r\n");
                    } else {
                        str.append("Path taken through Mutex:\r\n");
                        for (int i = 0; i < rails.size() - 1; i++) {
                            MutexZoneSlot.RailSlot slot = rails.get(i);
                            str.append("  ");
                            slot.debugPrint(str);
                            str.append("\r\n");
                        }
                        str.append("Waiting for Rail: ");
                        rails.get(rails.size() - 1).debugPrint(str);
                        str.append("\r\n");
                    }
                }

                MutexZoneConflictEvent conflict = group.getConflict();
                if (conflict != null) {
                    IntVector3 rail = conflict.getRailPosition();
                    str.append("Mutex Zone Conflict occurred:\r\n");
                    str.append("  train: ")
                       .append(conflict.getGroupCrossed().getProperties().getTrainName())
                       .append("\r\n");
                    str.append("  rail: [").append(rail.x).append("/").append(rail.y)
                       .append("/").append(rail.z).append("]\r\n"); 
                }

                ChatText clickable = ChatText.fromClickableContent(ChatColor.WHITE.toString() +
                        ChatColor.UNDERLINE + "Copy Details", str.toString());
                clickable.setHoverText("> Click to copy to your clipboard <");
                text.append(clickable);
            }

            return text;
        }
    }
}

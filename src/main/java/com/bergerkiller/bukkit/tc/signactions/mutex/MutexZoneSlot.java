package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.MutexZoneConflictEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.WorldRailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.statements.Statement;

/**
 * A slot where a group can be put that was granted access to move through.
 * Named mutex zones can share the same (named) slot
 */
public class MutexZoneSlot {
    private static final int TICK_DELAY_CLEAR_AUTOMATIC = 6; // Tick delay until a group is fully cleared from a mutex (and lever toggles up)
    private final String name;
    private final List<EnteredGroup> entered = new ArrayList<>(2);
    private List<MutexZone> zones;
    private List<String> statements;
    private int tickLastHardEntered = 0;

    protected MutexZoneSlot(String name) {
        this.name = name;
        this.zones = Collections.emptyList();
        this.statements = Collections.emptyList();
    }

    public String getName() {
        return this.name;
    }

    public String getNameWithoutWorldUUID() {
        if (!zones.isEmpty()) {
            String uuid_str = zones.get(0).signBlock.getWorldUUID().toString() + "_";
            if (name.startsWith(uuid_str)) {
                return name.substring(uuid_str.length());
            }
        }
        return name;
    }

    public boolean isAnonymous() {
        return this.name.isEmpty();
    }

    protected MutexZoneSlot addZone(MutexZone zone) {
        if (this.zones.isEmpty()) {
            this.zones = Collections.singletonList(zone);
        } else {
            this.zones = new ArrayList<MutexZone>(this.zones);
            this.zones.add(zone);
        }
        this.refreshStatements();
        return this;
    }

    public void removeZone(MutexZone zone) {
        if (this.zones.size() == 1 && this.zones.get(0) == zone) {
            this.zones = Collections.emptyList();
            this.statements = Collections.emptyList();
        } else if (this.zones.size() > 1) {
            this.zones.remove(zone);
            this.refreshStatements();
        }
    }

    public boolean hasZones() {
        return !this.zones.isEmpty();
    }

    /**
     * Gets a full list of statement rules for this mutex zone. Each sign
     * can add statements to this list as they join the mutex zone name.
     * 
     * @return statements
     */
    public List<String> getStatements() {
        return this.statements;
    }

    private void refreshStatements() {
        this.statements = this.zones.stream()
                .sorted((z0, z1) -> z0.signBlock.getPosition().compareTo(z1.signBlock.getPosition()))
                .map(z -> z.statement)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Called every tick to refresh mutex zones that have a group inside.
     * If a group leaves a zone, this eventually releases that group again.
     * 
     * @param trainWaiting Whether this is called while a train is waiting for the mutex
     */
    public void onTick() {
        if (!entered.isEmpty()) {
            Iterator<EnteredGroup> iter = entered.iterator();
            boolean hasHardEnteredGroup = false;
            boolean trainsHaveLeft = false;
            while (iter.hasNext()) {
                EnteredGroup enteredGroup = iter.next();
                if (!enteredGroup.refresh()) {
                    iter.remove();
                    trainsHaveLeft = true;
                } else if (enteredGroup.hardEnter) {
                    hasHardEnteredGroup = true;
                }
            }
            if (trainsHaveLeft && !hasHardEnteredGroup) {
                this.setLevers(false);
            }
        }
    }

    /**
     * Looks up the EnteredGroup of a MinecartGroup, if it had
     * (tried to) enter in the recent past.
     *
     * @param group
     * @return entered group
     */
    public EnteredGroup findEntered(MinecartGroup group) {
        for (EnteredGroup entered : this.entered) {
            if (entered.group == group) {
                return entered;
            }
        }
        return null;
    }

    /**
     * Starts tracking the group for this zone. Must call {@link EnteredGroup#enterRail(boolean, IntVector3)}
     * or {@link EnteredGroup#enterZone(boolean)} afterwards, which might result in the group
     * being untracked again when rejected.
     *
     * @param group MinecartGroup to track
     * @param distanceToMutex Distance from the front of the group to this mutex zone
     * @return EnteredGroup of this group
     */
    public EnteredGroup track(MinecartGroup group, double distanceToMutex) {
        int nowTicks = group.getObstacleTracker().getTickCounter();

        // Verify using statements whether the group is even considered
        {
            List<String> statements = this.getStatements();
            if (!statements.isEmpty()) {
                SignActionEvent signEvent = null;
                if (this.zones.size() == 1) {
                    Block signBlock = this.zones.get(0).getSignBlock();
                    if (signBlock != null && MaterialUtil.ISSIGN.get(signBlock)) {
                        signEvent = new SignActionEvent(signBlock, group);
                        signEvent.setAction(SignActionType.GROUP_ENTER);
                    }
                }
                if (!Statement.hasMultiple(group, this.getStatements(), signEvent)) {
                    // If previously tracked, release the train
                    boolean wasGroupHardEntered = false;
                    boolean hasHardEnteredGroup = false;
                    for (Iterator<EnteredGroup> iter = this.entered.iterator(); iter.hasNext();) {
                        EnteredGroup entered = iter.next();
                        if (entered.group == group) {
                            iter.remove();
                            wasGroupHardEntered = entered.hardEnter;
                        } else if (entered.hardEnter) {
                            hasHardEnteredGroup = true;
                        }
                    }
                    if (wasGroupHardEntered && !hasHardEnteredGroup) {
                        this.setLevers(false);
                    }

                    // Ignored!
                    return new IgnoredEnteredGroup(group, distanceToMutex, nowTicks);
                }
            }
        }

        // Find existing
        for (EnteredGroup enteredGroup : this.entered) {
            if (enteredGroup.group == group) {
                enteredGroup.deactivateByOtherGroups();
                enteredGroup.probeTick = nowTicks;
                if (enteredGroup.active) {
                    enteredGroup.distanceToMutex = Math.min(enteredGroup.distanceToMutex, distanceToMutex);
                } else {
                    enteredGroup.active = true;
                    enteredGroup.distanceToMutex = distanceToMutex;
                }
                return enteredGroup;
            }
        }

        EnteredGroup enteredGroup = new EnteredGroup(group, distanceToMutex, nowTicks);
        this.entered.add(enteredGroup);
        return enteredGroup;
    }

    private void setLevers(boolean down) {
        for (MutexZone zone : this.zones) {
            zone.setLevers(down);
        }
    }

    /**
     * Gets the group that currently is inside this zone. If not null, it
     * means no other group can enter it.
     *
     * @return current groups that activated this mutex zone
     */
    public List<MinecartGroup> getCurrentGroups() {
        if (this.entered.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<MinecartGroup> result = new ArrayList<>(this.entered.size());
            for (EnteredGroup enteredGroup : this.entered) {
                if (enteredGroup.active && enteredGroup.hardEnter) {
                    result.add(enteredGroup.group);
                }
            }
            return result;
        }
    }

    /**
     * Gets the group that is expected to enter this mutex zone very soon
     *
     * @return prospective groups
     */
    public List<MinecartGroup> getProspectiveGroups() {
        if (this.entered.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<MinecartGroup> result = new ArrayList<>(this.entered.size());
            for (EnteredGroup enteredGroup : this.entered) {
                if (enteredGroup.active) {
                    result.add(enteredGroup.group);
                }
            }
            return result;
        }
    }

    /**
     * The result of trying to enter a mutex
     */
    public static enum EnterResult {
        /** The group does not match conditions to enter/be seen by the mutex zone */
        IGNORED(false, false),
        /** The mutex zone was entered so far */
        SUCCESS(false, false),
        /** A path in the mutex just changed and both trains are inside at the same time! */
        CONFLICT(false, true),
        /** A previous conflict is still ongoing */
        CONFLICT_ONGOING(false, true),
        /** The mutex zone is occupied and the train cannot enter it */
        OCCUPIED(true, false),
        /**
         * The mutex zone is occupied and the train cannot enter it. But the slot does
         * want to receive all rail blocks that lie within the zone.
         * This is important to resolve the order in which multiple trains gain access.
         */
        OCCUPIED_DISCOVER(true, false);

        private final boolean occupied;
        private final boolean conflict;

        private EnterResult(boolean occupied, boolean conflict) {
            this.occupied = occupied;
            this.conflict = conflict;
        }

        /**
         * The result indicates that the rail is occupied, and the train should stop
         * moving.
         *
         * @return True if the result indicates occupied track
         */
        public boolean isOccupied() {
            return occupied;
        }

        /**
         * The result indicates a hard conflict occurred. This happens when two trains
         * are inside a (smart) mutex zone at the same time. This can occur when predicted
         * paths change suddenly (smart mutex) or when a train is placed/spawned inside
         * the mutex zone.
         *
         * @return True if a hard conflict occurred
         */
        public boolean isConflict() {
            return conflict;
        }
    }

    /**
     * Tracks the information of a single train that has entered a mutex zone
     */
    public class EnteredGroup {
        /** The MinecartGroup represented */
        public final MinecartGroup group;
        /** Whether the group has actually entered the mutex zone (true) or is about to (false) */
        public boolean hardEnter = false;
        /** Whether the group is scheduled to go into the mutex zone next, or is already */
        public boolean active = true;
        /** Distance from the front of the train to where this slot was first encountered */
        public double distanceToMutex;
        /**
         * Tick timestamp when the group last 'found' the mutex zone, updating its state
         * This tick timestamp is also stored inside occupiedRails to check whether rails are ahead of
         * the group
         */
        private int probeTick;
        /** Tracks the tick when this entered group was created. Resolves hard-hard conflicts */
        private final int creationTick;
        /** Tracks the tick when this entered group last failed to enter the mutex, and returned OCCUPIED */
        public int occupiedTick;
        /** The rail coordinates locked by the group that have positions within the mutex */
        private final RailSlotMap occupiedRails = new RailSlotMap();
        /** If a mutex conflict occurred, stores the event details of the conflict */
        private MutexZoneConflictEvent conflict = null;
        /**
         * Other entered groups that should be de-activated if this entered group is given green light
         * to move again. Automatically cleared when this group itself is deactivated anyway.
         */
        private final ArrayList<EnteredGroup> otherGroupsToDeactivate = new ArrayList<>(2);
        /**
         * Inverse of otherGroupsToDeactivate
         */
        private final ArrayList<EnteredGroup> groupsDeactivatingMe = new ArrayList<>(2);
        private IntVector3 groupsDeactivatingMeConflictRail = null;

        public EnteredGroup(MinecartGroup group, double distanceToMutex, int nowTicks) {
            this.group = group;
            this.probeTick = this.creationTick = nowTicks;
            this.occupiedTick = nowTicks; // Not set
            this.distanceToMutex = distanceToMutex;
        }

        private void deactivate(IntVector3 conflictRail) {
            this.active = false;
            this.occupiedRails.clearConflict(conflictRail);
            this.occupiedTick = this.probeTick;
            if (!this.otherGroupsToDeactivate.isEmpty()) {
                for (EnteredGroup group : this.otherGroupsToDeactivate) {
                    group.groupsDeactivatingMe.remove(this);
                    if (group.groupsDeactivatingMe.isEmpty()) {
                        group.groupsDeactivatingMeConflictRail = null;
                    }
                }
                this.otherGroupsToDeactivate.clear();
            }
        }

        private void deactivateByOtherGroups() {
            if (!this.groupsDeactivatingMe.isEmpty()) {
                for (EnteredGroup g : this.groupsDeactivatingMe) {
                    g.otherGroupsToDeactivate.remove(this);
                }
                this.groupsDeactivatingMe.clear();
                this.deactivate(this.groupsDeactivatingMeConflictRail);
                this.groupsDeactivatingMeConflictRail = null;
            }
        }

        private void deactivateOtherGroup(EnteredGroup otherGroup, IntVector3 conflictRail) {
            if (!this.otherGroupsToDeactivate.contains(otherGroup)) {
                this.otherGroupsToDeactivate.add(otherGroup);
                otherGroup.groupsDeactivatingMe.add(this);
                otherGroup.groupsDeactivatingMeConflictRail = conflictRail;
            }
        }

        /**
         * Gets whether this group has fully entered the mutex. This is the case for non-smart
         * mutexes which lock everything.
         *
         * @return True if fully occupied
         */
        public boolean isOccupiedFully() {
            return occupiedRails.isFullLocking();
        }

        /**
         * Gets the last (attempted) path taking through mutex zones that use this
         * mutex zone slot. If not entered, the last element stores the conflicting
         * rail block.
         *
         * @return last path taken.
         */
        public List<RailSlot> getLastPath() {
            return occupiedRails.getLastPath();
        }

        /**
         * If last {@link #enter(MutexZoneSlotType, IntVector3, boolean)} result was
         * {@link EnterResult#CONFLICT}, then this method returns the details
         * about that conflict.
         *
         * @return conflict event details
         */
        public MutexZoneConflictEvent getConflict() {
            return conflict;
        }

        /**
         * Tries to enter this mutex zone slot as this train.
         *
         * @param type What type of mutex zone is entered. Changes slot behavior.
         * @param hard Whether this is a hard-enter (train wants to enter the zone)
         * @param railBlock The rail block the group tries to enter currently. Is registered.
         * @return Enter Result
         */
        public EnterResult enter(MutexZoneSlotType type, IntVector3 railBlock, boolean hard) {
            // If true, returns SUCCESS_DELAY instead of SUCCESS to avoid trouble
            // We need one full tick to decide what train is allowed to go next
            // When this is the case, no train can ever hard-enter the mutex
            EnterResult successResult = EnterResult.SUCCESS;
            if (this.wasOccupiedLastTick()) {
                successResult = (this.conflict != null) ? EnterResult.CONFLICT_ONGOING
                                                        : EnterResult.OCCUPIED_DISCOVER;
            }

            {
                boolean wasFullyLocked = occupiedRails.isFullLocking();

                // Make sure to register the rail at all times so we have this information
                // This is important when resolving the order of restoring trains when a train
                // leaves the mutex zone, and the zone contains smart mutexes.
                boolean addedNewSlot = this.occupiedRails.add(type, railBlock, this.probeTick);

                // If already occupied fully a previous tick/previous update, and this was not
                // cancelled by deactivate(), then we can skip all the expensive logic down below.
                // The train is in, it's going to stay that way.
                if (wasFullyLocked && hard == this.hardEnter && this.conflict == null) {
                    return successResult;
                }

                if (type == MutexZoneSlotType.SMART) {
                    // If already occupied previously at the same hardness level, ignore all below logic
                    // We can safely occupy it again without changing any of the logic.
                    if (!addedNewSlot && hard == this.hardEnter && this.conflict == null) {
                        return successResult;
                    }
                }
            }

            // Remove all soft-entered groups that share rails in common (or if null, any and all)
            // If we find another group that already hard-entered the mutex, cancel.
            for (EnteredGroup enteredGroup : MutexZoneSlot.this.entered) {
                if (enteredGroup == this) {
                    continue;
                }
                if (!enteredGroup.active) {
                    // If this inactive group has been waiting longer than this group, and it's path
                    // intersects with a block this group uses, cancel. This other group has priority
                    // in this case.

                    // We do want two trains that can cross at the same time to cross, rather than a
                    // slower approach which follows true enter order. For this, we track how long ago
                    // the mutex gave green light to enter. If this was recently, then we ignore
                    // these checks temporarily.
                    if (enteredGroup.age() > this.age() &&
                        tickLastHardEntered < (this.serverTickLastProbed() + 5) &&
                        (this.creationTick == this.probeTick || this.wasOccupiedLastTick()) &&
                        enteredGroup.containsVerify(railBlock)
                    ) {
                        this.hardEnter = false;
                        this.deactivate(railBlock);
                        return EnterResult.OCCUPIED;
                    }

                    continue;
                }
                if (!enteredGroup.containsVerify(railBlock)) {
                    continue;
                }

                // If the entered group contains this entered group to be de-activated, de-activate ourselves first.
                if (hard) {
                    // If hard-entering and previous group entered softly, revoke the previous soft slot
                    if (!enteredGroup.hardEnter && this.age() > enteredGroup.age()) {
                        deactivateOtherGroup(enteredGroup, railBlock);
                        continue;
                    }

                    // If we have an existing group that hard-entered the mutex before, then we've got a problem.
                    // The trajectory on the rails likely changed so there's now a collision
                    // We can't do much about it, but locking the train now it's inside the mutex would be
                    // a bad idea, too. Therefore, update the rails, but do nothing more.
                    // If we hard-entered, but this entered group was only just tracked, disallow the hard enter.
                    boolean hadConflict = (this.conflict != null);
                    if (hadConflict || this.creationTick == this.probeTick || !this.wasOccupiedLastTick()) {
                        this.conflict = new MutexZoneConflictEvent(this.group, enteredGroup.group, MutexZoneSlot.this, railBlock);
                        this.occupiedTick = this.probeTick;
                        return hadConflict ? EnterResult.CONFLICT_ONGOING : EnterResult.CONFLICT;
                    }
                }

                // This train is in violation and loses its entered slot privileges.
                // Depending on what type of train is occupying the zone, slow the train down
                // completely (HARD) or approach the zone carefully (SOFT)
                this.hardEnter = false;
                this.deactivate(railBlock);
                return EnterResult.OCCUPIED;
            }

            // Clear to go - update the existing group or add a new one
            if (hard && successResult == EnterResult.SUCCESS && !this.hardEnter) {
                this.hardEnter = true;
                tickLastHardEntered = CommonUtil.getServerTicks();
                setLevers(true);
            }

            // If it's okay again, reset any conflict groups we detected before
            // Also reset previous rails, so it actually checks those rails a second time
            if (successResult == EnterResult.SUCCESS && this.conflict != null) {
                this.conflict = null;
                this.occupiedRails.clearOldRails(probeTick);
            }

            return successResult;
        }

        /**
         * Gets the age of this entered group. This is for how many ticks this group has
         * been inside or waiting for the mutex.
         *
         * @return Age in ticks
         */
        public int age() {
            return group.getObstacleTracker().getTickCounter() - this.creationTick;
        }

        public int serverTickLastProbed() {
            return CommonUtil.getServerTicks() + probeTick - group.getObstacleTracker().getTickCounter();
        }

        private boolean wasOccupiedLastTick() {
            return (this.probeTick - this.occupiedTick) <= 1;
        }

        private boolean containsVerify(IntVector3 rail) {
            int nowTicks = probeTick;
            return occupiedRails.isFullyLockedVerify(group, nowTicks) ||
                   occupiedRails.isSmartLockedVerify(group, nowTicks, rail);
        }

        private boolean refresh() {
            // If group unloads or is deleted weirdly, clean it up right away
            if (group.isUnloaded() || !MinecartGroupStore.getGroups().contains(group)) {
                return false;
            }
            // If checked recently keep it around for now
            int nowTicks = group.getObstacleTracker().getTickCounter();
            if ((nowTicks - probeTick) < TICK_DELAY_CLEAR_AUTOMATIC) {
                return true;
            }
            // Remove rail blocks that are no longer occupied by this group
            // If all rails are gone, that means the train is no longer using the slot at all
            if (!occupiedRails.verifyHasRailsUsedByGroup(group)) {
                return false;
            }
            // Still active
            probeTick = nowTicks;
            return true;
        }
    }

    private final class IgnoredEnteredGroup extends EnteredGroup {

        public IgnoredEnteredGroup(MinecartGroup group, double distanceToMutex, int nowTicks) {
            super(group, distanceToMutex, nowTicks);
        }

        @Override
        public EnterResult enter(MutexZoneSlotType type, IntVector3 railBlock, boolean hard) {
            return EnterResult.IGNORED;
        }
    }

    /**
     * Stores the rail blocks within a mutex zone that have been occupied by a train.
     * Includes logic to clean this up again once a train is no longer using it.
     */
    private static final class RailSlotMap {
        private static final Map<IntVector3, RailSlot> INITIAL_RAILS = Collections.emptyMap();
        /**
         * Rails gathered in realtime. Will store the conflicting rails if the mutex
         * could not be entered last time. Is reset every tick / enter attempt.
         */
        private final LinkedHashMap<IntVector3, RailSlot> railsLive = new LinkedHashMap<>();
        /** Those rail slots added to rails which are normal mutex slots ('full' locking) */
        private final ArrayList<RailSlot> railsFull = new ArrayList<>();
        /** Currently occupied rails */
        private Map<IntVector3, RailSlot> rails = INITIAL_RAILS;
        /** Last conflicting rail block */
        private RailSlot conflict = null;

        /**
         * Gets whether any of the rail slots mapped require locking the full mutex slot
         *
         * @return True if fully locked
         */
        public boolean isFullLocking() {
            return !railsFull.isEmpty();
        }

        /**
         * Gets the last rails that were visited to enter a mutex zone slot, successful or not.
         * When successful, it contains all the rails that are locked. When unsuccessful,
         * it shows the path that was attempted with the last slot containing the conflicting
         * rail block.
         *
         * @return last visited path
         */
        public List<RailSlot> getLastPath() {
            ArrayList<RailSlot> result = new ArrayList<>(railsLive.values());
            if (conflict != null) {
                result.add(conflict);
            }
            return result;
        }

        public void clearConflict(IntVector3 conflictRail) {
            RailSlot prevConflict = this.conflict;
            rails = INITIAL_RAILS;
            railsFull.clear();
            conflict = railsLive.remove(conflictRail);
            if (conflict == null) {
                conflict = prevConflict;
            }
        }

        /**
         * Removes all stored rail blocks older than the nowTicks specified
         *
         * @param nowTicks
         */
        public void clearOldRails(int nowTicks) {
            for (Iterator<RailSlot> iter = this.rails.values().iterator(); iter.hasNext();) {
                RailSlot slot = iter.next();
                if (slot.ticksLastProbed < nowTicks) {
                    onSlotRemoved(slot);
                    iter.remove();
                }
            }
        }

        public boolean add(MutexZoneSlotType type, IntVector3 railBlock, int nowTicks) {
            Map<IntVector3, RailSlot> currRails = this.rails;
            if (currRails == INITIAL_RAILS) {
                rails = currRails = railsLive;
                currRails.clear();
                conflict = null;
            }
            RailSlot slot = currRails.computeIfAbsent(railBlock, RailSlot::new);
            boolean added = slot.isNew;
            boolean wasFullLocking = slot.isFullLocking();
            slot.probe(type, nowTicks);
            if (!wasFullLocking && slot.isFullLocking()) {
                railsFull.add(slot);
            }
            return added;
        }

        @SuppressWarnings("unused")
        public boolean remove(IntVector3 railBlock) {
            Map<IntVector3, RailSlot> rails = this.rails;
            if (rails.isEmpty()) {
                return false;
            }
            RailSlot slot = rails.remove(railBlock);
            if (slot == null) {
                return false;
            }
            onSlotRemoved(slot);
            return true;
        }

        public boolean isFullyLockedVerify(MinecartGroup group, int nowTicks) {
            List<RailSlot> railsFull = this.railsFull;
            if (!railsFull.isEmpty()) {
                for (Iterator<RailSlot> iter = this.railsFull.iterator(); iter.hasNext();) {
                    RailSlot slot = iter.next();
                    if (nowTicks == slot.ticksLastProbed() || isRailUsedByGroup(slot.rail(), group)) {
                        return true;
                    } else {
                        // No longer used, release this particular rail block. Might release the entire mutex.
                        railsLive.remove(slot.rail());
                        iter.remove();
                    }
                }
            }
            return false;
        }

        public boolean isSmartLockedVerify(MinecartGroup group, int nowTicks, IntVector3 rail) {
            RailSlot slot = rails.get(rail);
            if (slot == null) {
                return false; // Not stored
            } else if (nowTicks == slot.ticksLastProbed()) {
                return true; // Updated this tick, no need to verify
            }

            // Verify that, at this rail, it really does still store the group
            if (group.isEmpty() || group.isUnloaded()) {
                // Check group is even still there, or it might get stuck on this
                //TODO: Make clean this up, if this happens at all...
                return false;
            }

            // Verify that this particular rail block is still actually used by this group
            // This detects when the tail of the train leaves particular track
            // We can use the rail lookup cache to figure this out really efficiently, because
            // members register themselves on the rail piece they occupy.
            if (isRailUsedByGroup(rail, group)) {
                return true;
            }

            // Omit the rails, no longer occupied
            onSlotRemoved(slot);
            rails.remove(rail);
            return false;
        }

        public boolean verifyHasRailsUsedByGroup(MinecartGroup group) {
            Iterator<RailSlot> iter = rails.values().iterator();
            while (iter.hasNext()) {
                RailSlot slot = iter.next();
                if (isRailUsedByGroup(slot.rail(), group)) {
                    // Note: no use clearing other rails. If somebody cares, it'll be
                    //       cleaned up automatically anyway.
                    return true;
                } else {
                    onSlotRemoved(slot);
                    iter.remove();
                }
            }
            return false;
        }

        private void onSlotRemoved(RailSlot slot) {
            if (slot.isFullLocking()) {
                railsFull.remove(slot);
            }
        }
        
        /**
         * Checks whether a particular rail block is used by a MinecartGroup
         *
         * @param rail Rail block coordinates
         * @param group Group to find
         * @return True if the group is currently using/on top of the rail block specified
         */
        private static boolean isRailUsedByGroup(IntVector3 rail, MinecartGroup group) {
            if (group.isEmpty() || group.isUnloaded()) {
                return false;
            }

            WorldRailLookup railLookup = group.head().railLookup();
            for (RailLookup.CachedRailPiece railPiece : railLookup.lookupCachedRailPieces(
                    railLookup.getOfflineWorld().getBlockAt(rail))
            ) {
                for (MinecartMember<?> member : railPiece.cachedMembers()) {
                    if (member.isUnloaded() || member.getEntity().isRemoved()) {
                        continue; // Skip
                    }
                    if (member.getGroup() == group) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * A single occupied rail within the influence of a (smart) mutex zone.
     * Stores the tick timestamp when the rail slot was locked, and whether
     * this was a 'full' lock or not.<br>
     * <br>
     * Externally the rail block and whether it was (attempted) to be locked
     * as a smart mutex is stored.
     */
    public static final class RailSlot {
        /** Rail block coordinates */
        private final IntVector3 rail;
        /** The type of mutex zone slot behavior that locked this rail slot. */
        private MutexZoneSlotType type;
        /** Tick timestamp when this rail slot was last probed */
        private int ticksLastProbed;
        /** Whether this rail slot was just now created (internal logic) */
        private boolean isNew;

        public RailSlot(IntVector3 rail) {
            this.rail = rail;
            this.ticksLastProbed = 0;
            this.type = MutexZoneSlotType.SMART;
            this.isNew = true;
        }

        private void probe(MutexZoneSlotType type, int nowTicks) {
            if (type == MutexZoneSlotType.NORMAL) {
                this.type = type;
            }
            this.ticksLastProbed = nowTicks;
            this.isNew = false;
        }

        /**
         * Rail block coordinates
         *
         * @return rail block
         */
        public IntVector3 rail() {
            return this.rail;
        }

        /**
         * Gets the type of mutex zone slot behavior that (tried to) lock this
         * particular rail block.
         *
         * @return Mutex zone slot type
         */
        public MutexZoneSlotType type() {
            return this.type;
        }

        /**
         * Gets whether this rail is part of the 'full' mutex lock, which locks the entire
         * slot so no other train can enter at all.
         *
         * @return True if this rail locks fully
         */
        public boolean isFullLocking() {
            return this.type == MutexZoneSlotType.NORMAL;
        }

        /**
         * Gets the tick timestamp of when this rail block was last updated. This is when
         * the train actively probes this rail block and tells it to be entered.
         *
         * @return tick timestamp of last rail probe
         */
        public int ticksLastProbed() {
            return this.ticksLastProbed;
        }

        public void debugPrint(StringBuilder str) {
            str.append("[").append(rail.x).append("/").append(rail.y)
               .append("/").append(rail.z).append("]");
            str.append(" ").append(type.name());
        }
    }
}

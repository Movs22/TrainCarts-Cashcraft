package com.bergerkiller.bukkit.tc.storage;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunch;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A class containing an array of Minecart Members
 * Also adds functions to handle multiple members at once
 * Also adds functions to write and load from/to file
 */
public class OfflineGroup {
    public final LongHashSet chunks;
    public final LongHashSet loadedChunks;
    public OfflineMember[] members;
    public String name;
    public OfflineWorld world;
    private boolean loaded;
    public boolean isBeingRemoved = false;

    public OfflineGroup(MinecartGroup group) {
        this(group.size());
        for (int i = 0; i < members.length; i++) {
            this.members[i] = new OfflineMember(this, group.get(i));
        }
        this.name = group.getProperties().getTrainName();
        this.world = OfflineWorld.of(group.getWorld());
        this.loaded = false;
        if (group.getActions().getCurrentAction() instanceof MemberActionLaunch) {
            double vel = ((MemberActionLaunch) group.getActions().getCurrentAction()).getTargetVelocity();
            for (OfflineMember member : this.members) {
                member.setVelocity(vel);
            }
        }
        this.genChunks();
    }

    private OfflineGroup(int memberCount) {
        this.members = new OfflineMember[memberCount];
        // Obtain an average of the amount of elements to store for chunks
        // Assume that each member adds 5 chunks every 10 carts
        final int chunkCount = 25 + (int) ((double) (5 / 10) * (double) memberCount);
        this.chunks = new LongHashSet(chunkCount);
        this.loadedChunks = new LongHashSet(chunkCount);
        this.loaded = false;
    }

    public static OfflineGroup readFrom(DataInputStream stream) throws IOException {
        OfflineGroup wg = new OfflineGroup(stream.readInt());
        for (int i = 0; i < wg.members.length; i++) {
            wg.members[i] = OfflineMember.readFrom(wg, stream);
        }
        wg.name = stream.readUTF();
        wg.genChunks();
        return wg;
    }

    /**
     * Gets whether this offline group has been loaded into the server
     * as a MinecartGroup. Also returns true if all members of this group
     * were missing and the offline group was purged. If true, this
     * offline group no longer exists in the offline group manager.
     *
     * @return True if loaded as group
     */
    public boolean isLoadedAsGroup() {
        return this.loaded;
    }

    public boolean isMoving() {
        for (OfflineMember member : members) {
            if (member.isMoving()) {
                return true;
            }
        }
        return false;
    }

    public boolean testFullyLoaded() {
        // When being removed (asynchronously) pretend the group isn't loaded in yet
        // This stalls any restoring action
        if (this.isBeingRemoved) {
            return false;
        }

        return this.loadedChunks.size() == this.chunks.size();
    }

    protected boolean updateLoadedChunks(OfflineGroupMap offlineMap) {
        this.loadedChunks.clear();

        World world = this.world.getLoadedWorld();
        if (world != null && offlineMap.canRestoreGroups()) {
            final LongIterator iter = this.chunks.longIterator();
            while (iter.hasNext()) {
                long chunk = iter.next();
                if (WorldUtil.isChunkEntitiesLoaded(world, MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk))) {
                    this.loadedChunks.add(chunk);
                }
            }
            if (OfflineGroupManager.lastUnloadChunk != null) {
                this.loadedChunks.remove(OfflineGroupManager.lastUnloadChunk);
            }
            return this.testFullyLoaded();
        } else {
            return false;
        }
    }

    public void genChunks() {
        this.chunks.clear();
        for (OfflineMember wm : this.members) {
            for (int x = wm.cx - 2; x <= wm.cx + 2; x++) {
                for (int z = wm.cz - 2; z <= wm.cz + 2; z++) {
                    this.chunks.add(MathUtil.longHashToLong(x, z));
                }
            }
        }
    }

    /**
     * Forces all chunks used by this group to become loaded, asynchronously
     *
     * @param world World the group is on
     * @return List of forced chunks, keep these around to allow all chunks to load
     */
    public List<ForcedChunk> forceLoadChunks(World world) {
        List<ForcedChunk> chunks = new ArrayList<>();
        final LongIterator iter = this.chunks.longIterator();
        while (iter.hasNext()) {
            long chunk = iter.next();
            chunks.add(WorldUtil.forceChunkLoaded(world, MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk)));
        }
        return chunks;
    }

    /**
     * Tries to find all Minecarts based on their UID and creates a new group
     *
     * @param traincarts TrainCarts plugin instance
     * @param world to find the Minecarts in
     * @return An array of Minecarts
     */
    public MinecartGroup create(TrainCarts traincarts, World world) {
        ArrayList<MinecartMember<?>> rval = new ArrayList<>(this.members.length);
        int missingNo = 0;
        int cx = 0, cz = 0;
        for (OfflineMember member : this.members) {
            MinecartMember<?> mm = member.create(traincarts, world);
            if (mm != null) {
                rval.add(mm);
            } else {
                missingNo++;
                cx = member.cx;
                cz = member.cz;
            }
        }
        if (missingNo > 0) {
            traincarts.log(Level.WARNING, missingNo + " carts of group '" + this.name + "' " +
                    "are missing near chunk [" + cx + ", " + cz + "]! (externally edited?)");
        }
        this.loaded = true;
        if (rval.isEmpty()) {
            TrainPropertiesStore.remove(this.name);
            return null;
        }
        // Is a new group needed?
        return MinecartGroup.create(this.name, rval.toArray(new MinecartMember[0]));
    }

    /*
     * Read and write functions used internally
     */
    public void writeTo(DataOutputStream stream) throws IOException {
        stream.writeInt(members.length);
        for (OfflineMember member : members) {
            member.writeTo(stream);
        }
        stream.writeUTF(this.name);
    }
}
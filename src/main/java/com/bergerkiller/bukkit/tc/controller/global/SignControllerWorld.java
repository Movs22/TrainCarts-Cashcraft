package com.bergerkiller.bukkit.tc.controller.global;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkNeighbourList;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateListener;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateTracker;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.tc.controller.global.SignController.Entry;
import com.bergerkiller.bukkit.tc.utils.LongBlockCoordinates;

/**
 * Tracks the locations of signs and handles the redstone activation of them. Provides
 * an efficient lookup for use by the rail cache. A controller instance exists per
 * world and is tightly coupled with the rail lookup cache.
 */
public class SignControllerWorld {
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private final SignController controller;
    private final World world;
    private final OfflineWorld offlineWorld;
    private final LongHashMap<List<SignController.Entry>> signsByChunk = new LongHashMap<>();
    private final LongHashMap<SignController.Entry[]> signsByNeighbouringBlock = new LongHashMap<>();
    private final ChunkFutureProvider chunkFutureProvider;
    private boolean needsInitialization;

    SignControllerWorld(SignController controller) {
        this.controller = controller;
        this.world = null;
        this.offlineWorld = OfflineWorld.NONE;
        this.chunkFutureProvider = null;
        this.needsInitialization = true;
    }

    SignControllerWorld(SignController controller, World world) {
        this.controller = controller;
        this.world = world;
        this.offlineWorld = OfflineWorld.of(world);
        this.chunkFutureProvider = ChunkFutureProvider.of(controller.getPlugin());
        this.needsInitialization = true;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean isValid() {
        return this.offlineWorld.getLoadedWorld() == this.world;
    }

    /**
     * Gets whether this sign controller is enabled. If the World is disabled in Traincarts config,
     * this will return false to indicate no processing should occur.
     *
     * @return True if enabled, False if disabled
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * If this World Sign Controller has not yet been initialized, because of using
     * {@link SignController#forWorldSkipInitialization(World)}, performs that initialization now.
     * If this controller was already initialized once, this method does nothing.
     */
    public void initialize() {
        if (this.needsInitialization) {
            this.needsInitialization = false;
            if (this.isEnabled()) {
                for (Chunk chunk : this.world.getLoadedChunks()) {
                    this.loadChunk(chunk);
                }
            }
        }
    }

    /**
     * Looks up the signs that exist at, or neighbouring, the specified block.
     *
     * @param block
     * @return Entries nearby
     */
    public SignController.Entry[] findNearby(Block block) {
        return findNearby(LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Looks up the signs that exist at, or neighbouring, the specified block.
     *
     * @param blockCoordinatesKey Key created using {@link LongBlockCoordinates#map(int, int, int)}
     * @return Entries nearby
     */
    public SignController.Entry[] findNearby(long blockCoordinatesKey) {
        return signsByNeighbouringBlock.getOrDefault(blockCoordinatesKey, SignController.Entry.NO_ENTRIES);
    }

    /**
     * Looks up the entry for a specific sign
     *
     * @param signBlock
     * @return Entry if found, null otherwise
     */
    public SignController.Entry findForSign(Block signBlock) {
        for (Entry entry : findNearby(signBlock)) {
            if (entry.sign.getBlock().equals(signBlock)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Calls a function on all signs at or neighbouring a specified block.
     * Before it calls the handler, verifies the sign still truly exists.
     *
     * @param block
     * @param handler
     */
    public void forEachNearbyVerify(Block block, Consumer<SignController.Entry> handler) {
        for (SignController.Entry entry : this.findNearby(block)) {
            if (verifyEntry(entry)) {
                handler.accept(entry);
            } else {
                removeInvalidEntry(entry);
            }
        }
    }

    /**
     * Queries a sign column of signs starting at a Block, into the direction
     * specified. This is used to find the signs below/at a rail block.
     * Before it calls the handler, verifies the sign still truly exists.
     * Passes the tracked sign details, which has been verified to exist.
     *
     * @param block Column start block
     * @param direction Column direction
     * @param handler Handler accepting the sign
     */
    public void forEachSignInColumn(Block block, BlockFace direction, Consumer<SignChangeTracker> handler) {
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        int checkBorder = FaceUtil.isVertical(direction) ? 0 : 1;
        if (!checkMayHaveSignsNearby(bx, by, bz, checkBorder)) {
            return;
        }

        long key = LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ());
        LongUnaryOperator shift = LongBlockCoordinates.shiftOperator(direction);
        int steps = 0;
        while (true) {
            boolean foundSigns = false;
            for (SignController.Entry entry : this.findNearby(key)) {
                if (verifySignColumnSlice(key, direction, entry)) {
                    foundSigns = true;
                    handler.accept(entry.sign);
                }
            }

            // If no signs found this step, and we've moved too far, stop
            if (!foundSigns && steps > 1) {
                break;
            }

            // Next block
            key = shift.applyAsLong(key);
            steps++;

            // Also increment bx/bz and load chunks when searching sideways (rare)
            // TODO: Optimize this? Not worth the hassle as it's not really used.
            if (!FaceUtil.isVertical(direction)) {
                bx += direction.getModX();
                bz += direction.getModZ();
                if (!checkMayHaveSignsNearby(bx, by, bz, checkBorder)) {
                    break;
                }
            }
        }
    }

    /**
     * Checks for a single block of a rail sign column, whether there are wall signs attached
     * to the column. Uses this cache, and verifies the signs truly do exist. For directions
     * other than up and down, also verifies the sign isn't attached to the block in the same
     * direction as the column.
     *
     * @param block Column start block
     * @param direction Column direction
     * @return True if there are wall signs attached to this block, False if not
     */
    public boolean hasSignsAroundColumn(Block block, BlockFace direction) {
        int checkBorder = FaceUtil.isVertical(direction) ? 0 : 1;
        if (!checkMayHaveSignsNearby(block.getX(), block.getY(), block.getZ(), checkBorder)) {
            return false;
        }

        long key = LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ());
        for (SignController.Entry entry : this.findNearby(key)) {
            if (verifySignColumnSlice(key, direction, entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether at a block position, signs might be nearby.
     * Chunks that need to be checked are loaded (sync) as needed.
     *
     * @param x Search X-position
     * @param y Search Y-position
     * @param z Search Z-position
     * @param border X/Z chunk border check distance
     * @return True if signs might be nearby. False if there definitely are no signs.
     */
    private boolean checkMayHaveSignsNearby(int x, int y, int z, int border) {
        boolean result;
        int cx = x >> 4;
        int cz = z >> 4;
        result = checkChunkMayHaveSigns(cx, cz, x, y, z);

        int bx = x & 0xF;
        if (bx <= border) {
            result |= checkChunkMayHaveSigns(cx - 1, cz, x, y, z);
        } else if (bx >= (15-border)) {
            result |= checkChunkMayHaveSigns(cx + 1, cz, x, y, z);
        }

        int bz = z & 0xF;
        if (bz <= border) {
            result |= checkChunkMayHaveSigns(cx, cz - 1, x, y, z);
        } else if (bz >= (15-border)) {
            result |= checkChunkMayHaveSigns(cx, cz + 1, x, y, z);
        }
        return result;
    }

    /**
     * Checks whether a Chunk position has a particular sign nearby a given x/y/z.
     * This checks whether that could be the case, and may also return true when
     * this isn't certain.
     * Will (sync) load the chunk if no sign information is available yet.
     *
     * @param cx Chunk X-coordinate
     * @param cz Chunk Z-coordinate
     * @param x Search X-position
     * @param y Search Y-position
     * @param z Search Z-position
     * @return True if signs might be nearby
     */
    private boolean checkChunkMayHaveSigns(int cx, int cz, int x, int y, int z) {
        // Find signs in this chunk. Load chunk if no data about it is loaded in yet.
        List<SignController.Entry> signsAtChunk;
        {
            long key = MathUtil.longHashToLong(cx, cz);
            if ((signsAtChunk = this.signsByChunk.get(key)) == null) {
                world.getChunkAt(cx, cz);
                if ((signsAtChunk = this.signsByChunk.get(key)) == null) {
                    // Weird! This case probably never happens.
                    return false;
                }
            }
        }

        // Check sign count
        // When there's no signs, skip creating an iterator and fail instantly
        // When there's too many signs, omit searching for them as that would be unneededly slow
        {
            int count = signsAtChunk.size();
            if (count == 0) {
                return false;
            } else if (count > 20) {
                return true;
            }
        }

        // Check whether any signs are neighbouring this x/y/z
        for (SignController.Entry entry : signsAtChunk) {
            Block b = entry.getBlock();
            if (Math.abs(b.getX() - x) <= 2 &&
                Math.abs(b.getY() - y) <= 2 &&
                Math.abs(b.getZ() - z) <= 2
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifies whether a particular sign is a part of a rail sign column, or not.
     * Also checks the sign actually exists still.
     *
     * @param key Block coordinate key
     * @param direction Sign column direction
     * @param entry Current sign entry
     * @return True if the sign is a part of the column slice, False if not
     */
    private boolean verifySignColumnSlice(long key, BlockFace direction, SignController.Entry entry) {
        // Find relative direction the sign is at
        BlockFace offset = LongBlockCoordinates.findDirection(entry.blockKey, key);
        if (offset == null || offset == direction || offset == direction.getOppositeFace()) {
            return false;
        }

        // Check sign still exists
        if (!verifyEntry(entry)) {
            removeInvalidEntry(entry);
            return false;
        }

        // Retrieve BlockData. Check attached face is correct, or that it is a sign post with SELF
        BlockData blockData = entry.sign.getBlockData();
        if (blockData.isType(SIGN_POST_TYPE)) {
            if (offset != BlockFace.SELF)
                return false;
        } else if (blockData.isType(WALL_SIGN_TYPE)) {
            BlockFace facing = blockData.getAttachedFace();
            if (facing != offset && facing != direction.getOppositeFace())
                return false;
        } else {
            // Doesn't map to either legacy wall or sign post type
            // Assume it's not a sign at all and remove it
            this.removeInvalidEntry(entry);
            return false;
        }

        return true;
    }

    /**
     * Checks the 6 blocks neighbouring a particular block for the placement of a new sign
     * not yet known to this controller through the usual events.
     *
     * @param around
     */
    public void detectNewSigns(Block around) {
        long blockKey = LongBlockCoordinates.map(around);
        Entry[] nearby = findNearby(blockKey);
        LongBlockCoordinates.forAllBlockSidesAndSelf(blockKey, (face, key) -> {
            // Check not already in the nearby mapping
            for (Entry e : nearby) {
                if (e.blockKey == key) {
                    return;
                }
            }

            // If chunk is actually loaded, check if there is an actual sign here
            int bx = around.getX() + face.getModX();
            int by = around.getY() + face.getModY();
            int bz = around.getZ() + face.getModZ();
            Chunk chunk = WorldUtil.getChunk(world, bx >> 4, bz >> 4);
            if (chunk == null) {
                return;
            }
            if (!MaterialUtil.ISSIGN.get(ChunkUtil.getBlockData(chunk, bx, by, bz))) {
                return;
            }

            // Missing entry! Add one now, but do so next tick in case text isn't loaded onto it yet.
            final Block potentialSign = around.getRelative(face);
            new Task(controller.getPlugin()) {
                @Override
                public void run() {
                    if (MaterialUtil.ISSIGN.get(potentialSign)) {
                        addSign(potentialSign, true);
                    }
                }
            }.start();
        });
    }

    /**
     * Starts tracking a newly placed sign. Initializes power state, but does not fire any events.
     * If the sign was already tracked, returns the existing entry instead. If the sign could not
     * be found at this block, returns null.
     *
     * @param signBlock Block of the sign that was placed
     * @param handleLoadChange Whether to hande the loaded-change event of the sign that is added.
     *                         Should be false if this logic already occurs elsewhere.
     * @return entry for the sign, null if not a sign
     */
    public SignController.Entry addSign(Block signBlock, boolean handleLoadChange) {
        // Find/activate an existing sign
        SignController.Entry existing = this.findForSign(signBlock);
        if (existing != null) {
            if (verifyEntry(existing)) {
                controller.activateEntry(existing, true, handleLoadChange);
                return existing;
            } else {
                removeInvalidEntry(existing);
                existing = null;
            }
        }

        // Add a new one. Lines of text might be wiped initially.
        Sign sign = BlockUtil.getSign(signBlock);
        if (sign == null) {
            return null;
        } else {
            return createNewSign(sign, handleLoadChange);
        }
    }

    /**
     * Initializes a new sign that was just placed in the world. Not called when signs are
     * loaded in as new chunks/worlds load in.
     *
     * @param sign Sign
     * @param handleLoadChange Whether to hande the loaded-change event of the sign that is added.
     *                         Should be false if this logic already occurs elsewhere.
     * @return Entry created for this new sign
     */
    private SignController.Entry createNewSign(Sign sign, boolean handleLoadChange) {
        // Create entry. Add it to by-chunk and by-block mapping.
        Block signBlock = sign.getBlock();
        SignController.Entry entry = this.controller.createEntry(sign,
                this,
                LongBlockCoordinates.map(signBlock.getX(), signBlock.getY(), signBlock.getZ()),
                MathUtil.longHashToLong(MathUtil.toChunk(signBlock.getX()),
                                        MathUtil.toChunk(signBlock.getZ())));
        {
            List<SignController.Entry> atChunk = this.signsByChunk.get(entry.chunkKey);
            if (atChunk == null || atChunk.isEmpty()) {
                atChunk = new ArrayList<>();
                this.signsByChunk.put(entry.chunkKey, atChunk);
            }
            atChunk.add(entry);
        }

        entry.blocks.forAllBlocks(entry, this::addChunkByBlockEntry);

        this.controller.activateEntry(entry, true, handleLoadChange);

        return entry;
    }

    /**
     * Refreshes the signs that exist in a particular chunk. This is used for the debug
     * command, and for use by external plugins that modify/place signs in weird ways.
     *
     * @param chunk Chunk
     * @return Information about the number of signs added/removed thanks to refreshing
     */
    public RefreshResult refreshInChunk(Chunk chunk) {
        long chunkKey = MathUtil.longHashToLong(chunk.getX(), chunk.getZ());

        // Verify existence of signs we already had. Remove if missing.
        int numRemoved = 0;
        {
            List<SignController.Entry> atChunk = this.signsByChunk.get(chunkKey);
            if (atChunk != null && !atChunk.isEmpty()) {
                for (Iterator<SignController.Entry> iter = atChunk.iterator(); iter.hasNext();) {
                    SignController.Entry entry = iter.next();
                    if (!verifyEntry(entry)) {
                        // Remove loaded sign information
                        iter.remove();
                        entry.blocks.forAllBlocks(entry, this::removeChunkByBlockEntry);

                        // Remove from the offline signs cache as well
                        controller.getPlugin().getOfflineSigns().removeAll(entry.sign.getBlock());
                        numRemoved++;
                    }
                }
            }
        }

        // Try to add signs we didn't already have
        int numAdded = 0;
        for (BlockState blockState : this.getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                Block signBlock = blockState.getBlock();
                SignController.Entry existing = this.findForSign(signBlock);
                if (existing != null) {
                    controller.activateEntry(existing);
                    continue;
                }

                this.createNewSign((Sign) blockState, true);
                numAdded++;
            }
        }

        return new RefreshResult(numAdded, numRemoved);
    }

    /**
     * Removes all data cached/stored in this World
     */
    void clear() {
        for (List<SignController.Entry> atChunk : this.signsByChunk.values()) {
            atChunk.forEach(SignController.Entry::remove);
        }
        this.signsByChunk.clear();
        this.signsByNeighbouringBlock.clear();
    }

    /**
     * Called once a chunk and all it's 8 neighbouring chunks are loaded.
     * This should activate any previously added signs in this chunk.
     *
     * @param chunk
     */
    private void activateSignsInChunk(Chunk chunk) {
        // Use verifyEntry which updates the sign state, important when activating
        changeActiveForEntriesInChunk(chunk, true, SignControllerWorld::verifyEntry, this.controller::activateEntry);
    }

    /**
     * Called once a chunk, or one of it's 8 neighbouring chunks, unloads.
     * This should de-activate any previously activated signs.
     * It does not yet remove the signs (but this may have already happened).
     *
     * @param chunk
     */
    private void deactivateSignsInChunk(Chunk chunk) {
        // Use isRemoved() because we just want to know whether the sign is there to avoid NPE
        changeActiveForEntriesInChunk(chunk, false, e -> !e.sign.isRemoved(), this.controller::deactivateEntry);
    }

    private void changeActiveForEntriesInChunk(
            Chunk chunk,
            boolean activating,
            Predicate<SignController.Entry> verify,
            Consumer<SignController.Entry> handler
    ) {
        List<SignController.Entry> entries = this.signsByChunk.get(chunk.getX(), chunk.getZ());
        if (entries == null || entries.isEmpty()) {
            return;
        }

        int retryLimit = 100;
        while (true) {
            // Check that any of the entries need activating/de-activating at all
            {
                boolean hasEntriesToHandle = false;
                for (SignController.Entry entry : entries) {
                    if (entry.activated != activating) {
                        hasEntriesToHandle = true;
                        break;
                    }
                }
                if (!hasEntriesToHandle) {
                    break;
                }
            }

            // Prevent a crash if anything goes wrong here
            if (--retryLimit == 0) {
                controller.getPlugin().log(Level.SEVERE, "Infinite loop " +
                        (activating ? "activating" : "de-activating") +
                        " signs in chunk [" + chunk.getX() + "/" + chunk.getZ() + "]. Signs:");
                for (SignController.Entry entry : entries) {
                    controller.getPlugin().log(Level.SEVERE, "- at " + entry.sign.getBlock());
                }
                break;
            }

            // De-activate or activate all entries
            // We might find some signs become invalid - remove from original list.
            // Risks concurrent modification if SignAction loadedChanged modifies this,
            // so iterate a copy.
            for (SignController.Entry entry : new ArrayList<>(entries)) {
                if (entry.activated != activating) {
                    if (verify.test(entry)) {
                        // Callbacks
                        handler.accept(entry);
                    } else {
                        // Sign is gone. Remove it.
                        entries.remove(entry);
                        entry.blocks.forAllBlocks(entry, this::removeChunkByBlockEntry);
                    }
                }
            }
        }
    }

    /**
     * Adds data about signs stored in a particular chunk
     *
     * @param chunk
     */
    void loadChunk(Chunk chunk) {
        // If this sign cache hasn't been initialized with all loaded chunks yet, don't do anything here
        if (this.needsInitialization) {
            return;
        }

        long chunkKey = MathUtil.longHashToLong(chunk.getX(), chunk.getZ());

        // Skip if already added. Might be some edge conditions during world load...
        if (this.signsByChunk.contains(chunkKey)) {
            return;
        }

        List<SignController.Entry> entriesAtChunk = Collections.emptyList();
        for (BlockState blockState : getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                SignController.Entry entry = this.controller.createEntry((Sign) blockState,
                        this,
                        LongBlockCoordinates.map(blockState.getX(), blockState.getY(), blockState.getZ()),
                        chunkKey);
                if (entriesAtChunk.isEmpty()) {
                    entriesAtChunk = new ArrayList<>();
                }
                entriesAtChunk.add(entry);
                entry.blocks.forAllBlocks(entry, this::addChunkByBlockEntry);
            }
        }
        this.signsByChunk.put(chunkKey, entriesAtChunk);

        // Once all this chunk's neighbours are loaded as well, initialize the initial power state of the sign
        this.chunkFutureProvider.trackNeighboursLoaded(chunk, ChunkNeighbourList.neighboursOf(chunk, 1), new ChunkStateListener() {
            @Override
            public void onRegistered(ChunkStateTracker tracker) {
                if (tracker.isLoaded()) {
                    onLoaded(tracker);
                }
            }

            @Override
            public void onCancelled(ChunkStateTracker tracker) {
            }

            @Override
            public void onLoaded(ChunkStateTracker tracker) {
                activateSignsInChunk(tracker.getChunk());
            }

            @Override
            public void onUnloaded(ChunkStateTracker tracker) {
                deactivateSignsInChunk(tracker.getChunk());
            }
        });
    }

    private Collection<BlockState> getBlockStatesSafe(Chunk chunk) {
        try {
            return WorldUtil.getBlockStates(chunk);
        } catch (Throwable t) {
            this.controller.getPlugin().getLogger().log(Level.SEVERE, "Error reading sign block states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
            return Collections.emptyList();
        }
    }

    private void addChunkByBlockEntry(final SignController.Entry entry, long key) {
        this.signsByNeighbouringBlock.merge(key, entry.singletonArray, (a, b) -> {
            int len = a.length;
            SignController.Entry[] result = Arrays.copyOf(a, len + 1);
            result[len] = entry;
            return result;
        });
    }

    /**
     * Orders to delete cached sign information about a particular chunk
     *
     * @param chunk
     */
    void unloadChunk(Chunk chunk) {
        // If this sign cache hasn't been initialized with all loaded chunks yet, don't do anything here
        if (this.needsInitialization) {
            return;
        }

        List<SignController.Entry> atChunk = this.signsByChunk.remove(chunk.getX(), chunk.getZ());
        if (atChunk != null && !atChunk.isEmpty()) {
            // Remove all entries from the by-neighbour-block mapping
            for (SignController.Entry entry : atChunk) {
                // De-activate first, if it was activated still
                if (entry.activated && !entry.sign.isRemoved()) {
                    this.controller.deactivateEntry(entry);
                }

                entry.blocks.forAllBlocks(entry, (e, key) -> removeChunkByBlockEntry(e, key, true));
            }
        }
    }

    static boolean verifyEntry(SignController.Entry entry) {
        entry.sign.update();
        if (entry.sign.isRemoved()) {
            return false;
        }
        if (entry.sign.getAttachedFace() != entry.blocks.getAttachedFace()) {
            entry.blocks.forAllBlocks(entry, entry.world::removeChunkByBlockEntry);
            entry.blocks = SignBlocksAround.of(entry.sign.getAttachedFace());
            entry.blocks.forAllBlocks(entry, entry.world::addChunkByBlockEntry);
        }
        return true;
    }

    void removeInvalidEntry(SignController.Entry entry) {
        // Remove entry from by-chunk mapping
        List<SignController.Entry> atChunk = this.signsByChunk.get(entry.chunkKey);
        if (atChunk != null && atChunk.remove(entry) && atChunk.isEmpty()) {
            this.signsByChunk.remove(entry.chunkKey);
        }

        // Remove entry from by-block mapping
        entry.blocks.forAllBlocks(entry, this::removeChunkByBlockEntry);
    }

    private void removeChunkByBlockEntry(SignController.Entry entry, long key) {
        removeChunkByBlockEntry(entry, key, false);
    }

    private void removeChunkByBlockEntry(SignController.Entry entry, long key, boolean purgeAllInSameChunk) {
        SignController.Entry[] entries = this.signsByNeighbouringBlock.remove(key);
        if (entries == null || (purgeAllInSameChunk && LongBlockCoordinates.getChunkEdgeDistance(key) >= 2)) {
            return;
        }

        // Slow method - we have to remove entries at chunk boundaries carefully, so we don't touch
        // entries that refer to signs in neighbouring (still loaded!) chunks.
        SignController.Entry[] newEntries = SignController.Entry.NO_ENTRIES;
        int numNewEntries = 0;
        int len = entries.length;
        while (--len >= 0) {
            SignController.Entry e = entries[len];
            if (e != entry && (!purgeAllInSameChunk || e.chunkKey != entry.chunkKey)) {
                newEntries = Arrays.copyOf(newEntries, numNewEntries + 1);
                newEntries[numNewEntries] = e;
                numNewEntries++;
            }
        }

        // Put if there are entries to keep
        if (numNewEntries > 0) {
            this.signsByNeighbouringBlock.put(key, newEntries);
        }
    }

    static class SignControllerWorldDisabled extends SignControllerWorld {

        SignControllerWorldDisabled(SignController controller, World world) {
            super(controller, world);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public SignController.Entry[] findNearby(Block block) {
            return SignController.Entry.NO_ENTRIES;
        }

        @Override
        public SignController.Entry addSign(Block signBlock, boolean handleLoadChange) {
            return null;
        }

        @Override
        public RefreshResult refreshInChunk(Chunk chunk) {
            return RefreshResult.NONE;
        }

        @Override
        void loadChunk(Chunk chunk) {}

        @Override
        void unloadChunk(Chunk chunk) {}
    }

    public static class RefreshResult {
        public static final RefreshResult NONE = new RefreshResult(0, 0);
        public final int numAdded, numRemoved;

        public RefreshResult(int numAdded, int numRemoved) {
            this.numAdded = numAdded;
            this.numRemoved = numRemoved;
        }

        public RefreshResult add(RefreshResult other) {
            return new RefreshResult(this.numAdded + other.numAdded,
                                     this.numRemoved + other.numRemoved);
        }
    }
}

package com.bergerkiller.bukkit.tc.chest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.BasicConfiguration;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.google.common.io.ByteStreams;

public class TrainChestItemUtil {
    private static final String IDENTIFIER = "Traincarts.chest";
    private static final String TITLE = "Traincarts Chest";

    public static ItemStack createItem() {
        ItemStack item = ItemUtil.createItem(Material.ENDER_CHEST, 1);
        item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
        tag.putValue("plugin", TrainCarts.plugin.getName());
        tag.putValue("identifier", IDENTIFIER);
        tag.putValue("name", "");
        tag.putValue("parsed", false);
        tag.putValue("locked", false);
        tag.putValue("HideFlags", 1);
        updateTitle(item);
        return item;
    }

    private static void updateTitle(ItemStack item) {
        String displayTitle = TITLE;
        String name = getName(item);
        if (name.isEmpty() && !isEmpty(item) && ItemUtil.getMetaTag(item).getValue("parsed", false)) {
            name = ItemUtil.getMetaTag(item).getValue("config", "");
        }
        if (!name.isEmpty()) {
            displayTitle += " (" + name + ")";
        }
        ItemUtil.setDisplayName(item, displayTitle);

        ItemUtil.clearLoreNames(item);
        if (isEmpty(item)) {
            ItemUtil.addLoreChatText(item, ChatText.fromMessage(ChatColor.RED + "Empty"));
        } else if (isFiniteSpawns(item)) {
            ItemUtil.addLoreChatText(item, ChatText.fromMessage(ChatColor.BLUE + "Single-use"));
        } else {
            ItemUtil.addLoreChatText(item, ChatText.fromMessage(ChatColor.DARK_PURPLE + "Infinite uses"));
        }
        double speed = getSpeed(item);
        if (speed > 0.0) {
            ItemUtil.addLoreChatText(item, ChatText.fromMessage(ChatColor.YELLOW + "Speed " + DebugToolUtil.formatNumber(speed) + "b/t"));
        }
        if (isLocked(item)) {
            ItemUtil.addLoreChatText(item, ChatText.fromMessage(ChatColor.RED + "Locked"));
        }
    }

    public static boolean isItem(ItemStack item) {
        if (!ItemUtil.isEmpty(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, false);
            if (tag != null) {
                return IDENTIFIER.equals(tag.getValue("identifier", ""));
            }
        }
        return false;
    }

    public static void setFiniteSpawns(ItemStack item, boolean finite) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("finite", finite);
            updateTitle(item);
        }
    }

    public static void setLocked(ItemStack item, boolean locked) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("locked", locked);
            updateTitle(item);
        }
    }

    public static void setSpeed(ItemStack item, double speed) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("speed", speed);
            updateTitle(item);
        }
    }

    public static boolean isLocked(ItemStack item) {
        return isItem(item) && ItemUtil.getMetaTag(item).getValue("locked", false);
    }

    public static boolean isFiniteSpawns(ItemStack item) {
        return isItem(item) && ItemUtil.getMetaTag(item).getValue("finite", false);
    }

    public static double getSpeed(ItemStack item) {
        return isItem(item) ? ItemUtil.getMetaTag(item).getValue("speed", 0.0) : 0.0;
    }

    public static void setName(ItemStack item, String name) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("name", name);
            updateTitle(item);
        }
    }

    public static String getName(ItemStack item) {
        return isItem(item) ? ItemUtil.getMetaTag(item).getValue("name", "") : "";
    }

    public static void clear(ItemStack item) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).remove("config");
            updateTitle(item);
        }
    }

    public static boolean isEmpty(ItemStack item) {
        return isItem(item) && !ItemUtil.getMetaTag(item).containsKey("config");
    }

    public static void playSoundStore(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_CONTRACT, 0.4f, 1.5f);
    }

    public static void playSoundSpawn(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_EXTEND, 0.4f, 1.5f);
    }

    public static void store(ItemStack item, String spawnPattern) {
        if (isItem(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
            tag.putValue("config", spawnPattern);
            tag.putValue("parsed", true);
            updateTitle(item);
        }
    }

    public static void store(ItemStack item, MinecartGroup group) {
        if (group != null) {
            store(item, group.saveConfig());            
        }
    }

    public static void store(ItemStack item, ConfigurationNode config) {
        if (isItem(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);

            byte[] compressed = new byte[0];
            try {
                byte[] uncompressed = config.toString().getBytes("UTF-8");
                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(uncompressed.length)) {
                    try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                        zipStream.write(uncompressed);
                    }
                    compressed = byteStream.toByteArray();
                }
            } catch (Throwable t) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE, "Unhandled error saving item details to config", t);
            }
            tag.putValue("config", compressed);
            tag.putValue("parsed", false);
            updateTitle(item);
        }
    }

    /**
     * Gets the spawnable group stored in the configuration of an item.
     * Supports both when the configuration itself, as when the train name is
     * referenced.
     *
     * @param plugin TrainCarts plugin instance
     * @param item Input train chest item
     * @return group configured in the item. Is null if the item is not a train
     *         chest item, or is empty.
     */
    public static SpawnableGroup getSpawnableGroup(TrainCarts plugin, ItemStack item) {
        if (!isItem(item)) {
            return null;
        }
        if (isEmpty(item)) {
            return null;
        }

        // Attempt parsing the Item's configuration into a SpawnableGroup
        SpawnableGroup group;
        if (ItemUtil.getMetaTag(item).getValue("parsed", false)) {
            group = SpawnableGroup.parse(plugin, ItemUtil.getMetaTag(item).getValue("config", ""));
        } else {
            BasicConfiguration basicConfig = new BasicConfiguration();
            try {
                byte[] uncompressed = new byte[0];
                byte[] compressed = ItemUtil.getMetaTag(item).getValue("config", new byte[0]);
                if (compressed != null && compressed.length > 0) {
                    try (ByteArrayInputStream inByteStream = new ByteArrayInputStream(compressed)) {
                        try (GZIPInputStream zipStream = new GZIPInputStream(inByteStream)) {
                            uncompressed = ByteStreams.toByteArray(zipStream);
                        }
                    }
                }
                basicConfig.loadFromStream(new ByteArrayInputStream(uncompressed));
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Unhandled IO error parsing train chest configuration", ex);
                return null;
            }
            group = SpawnableGroup.fromConfig(plugin, basicConfig);
        }
        if (group.getMembers().isEmpty()) {
            return null;
        }

        return group;
    }

    public static SpawnResult spawnAtBlock(SpawnableGroup group, Player player, Block clickedBlock, double initialSpeed) {
        if (group == null) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Check clicked rails Block is actually a rail
        BlockFace orientation = FaceUtil.getDirection(player.getEyeLocation().getDirection());
        RailType clickedRailType = RailType.getType(clickedBlock);
        if (clickedRailType == RailType.NONE) {
            return SpawnResult.FAIL_NORAIL;
        }
        Location spawnLoc = clickedRailType.getSpawnLocation(clickedBlock, orientation);
        if (spawnLoc == null) {
            return SpawnResult.FAIL_NORAIL;
        }

        // Compute movement direction on the clicked rails using rail state
        Vector spawnDirection;
        {
            RailState state = new RailState();
            state.setRailPiece(RailPiece.create(clickedRailType, clickedBlock));
            state.setPosition(Position.fromTo(spawnLoc, spawnLoc));
            state.setMotionVector(spawnLoc.getDirection());
            state.initEnterDirection();
            state.loadRailLogic().getPath().move(state, 0.0);
            spawnDirection = state.position().getMotion();
            if (state.position().motDot(player.getEyeLocation().getDirection()) < 0.0) {
                spawnDirection.multiply(-1.0);
            }
        }

        // Find locations to spawn at
        SpawnableGroup.SpawnLocationList locationList = group.findSpawnLocations(spawnLoc, spawnDirection, SpawnableGroup.SpawnMode.DEFAULT);
        if (locationList == null) {
            return SpawnResult.FAIL_RAILTOOSHORT; // Not enough spawn positions on the rails
        }
        if (locationList.locations.size() < group.getMembers().size()) {
            return SpawnResult.FAIL_RAILTOOSHORT; // Not enough spawn positions on the rails
        }

        // Prepare chunks
        locationList.loadChunks();

        // Verify spawn area is clear of trains before spawning
        if (locationList.isOccupied()) {
            return SpawnResult.FAIL_BLOCKED; // Occupied
        }

        // Spawn.
        MinecartGroup spawnedGroup = group.spawn(locationList);
        if (initialSpeed > 0.0) {
            spawnedGroup.setForwardForce(initialSpeed);
        }
        if (spawnedGroup != null && !spawnedGroup.isEmpty()) {
            CartProperties.setEditing(player, spawnedGroup.tail().getProperties());
        }
        return SpawnResult.SUCCESS;
    }

    public static SpawnResult spawnLookingAt(SpawnableGroup group, Player player, Location eyeLocation, double initialSpeed) {
        // Clicked in the air. Perform a raytrace hit-test to find
        // possible rails in range of where the player clicked.
        // No need to make this complicated, we only need to get close
        // to the actual rail. Once we find the block within the rail is found,
        // then we can do finetuning to find exactly where on the rail
        // was clicked.
        final double reach = 5.0;
        final int steps = 100;
        final Vector step = eyeLocation.getDirection().multiply(reach / (double) steps);

        RailState bestState = null;
        {
            Location pos = eyeLocation.clone();
            RailState tmp = new RailState();
            tmp.setRailPiece(RailPiece.createWorldPlaceholder(eyeLocation.getWorld()));
            double bestDistanceSq = (2.0 * 2.0); // at most 2 blocks away from where the player is looking
            for (int n = 0; n < steps; n++) {
                pos.add(step);
                tmp.position().setLocation(pos);

                if (!RailType.loadRailInformation(tmp)) {
                    continue;
                }

                tmp.loadRailLogic().getPath().move(tmp, 0.0);
                double dist_sq = tmp.position().distanceSquared(pos);
                if (dist_sq < bestDistanceSq) {
                    bestDistanceSq = dist_sq;
                    bestState = tmp.clone();
                }
            }
        }

        if (bestState == null) {
            return SpawnResult.FAIL_NORAIL_LOOK;
        } else {
            // Reverse direction to align how the player is looking
            if (bestState.position().motDot(step) < 0.0) {
                bestState.position().invertMotion();
            }
            bestState.initEnterDirection();

            // Try spawning
            return spawnAtState(group, player, bestState, initialSpeed);
        }
    }

    public static SpawnResult spawnAtState(SpawnableGroup group, Player player, RailState state, double initialSpeed) {
        if (group == null) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Find locations to spawn at
        SpawnableGroup.SpawnLocationList locationList = group.findSpawnLocations(state, SpawnableGroup.SpawnMode.DEFAULT);
        if (locationList == null) {
            return SpawnResult.FAIL_RAILTOOSHORT; // Not enough spawn positions on the rails
        }
        if (locationList.locations.size() < group.getMembers().size()) {
            return SpawnResult.FAIL_RAILTOOSHORT; // Not enough spawn positions on the rails
        }

        // Prepare chunks
        locationList.loadChunks();

        // Verify spawn area is clear of trains before spawning
        if (locationList.isOccupied()) {
            return SpawnResult.FAIL_BLOCKED; // Occupied by another train
        }

        // Spawn.
        MinecartGroup spawnedGroup = group.spawn(locationList);
        if (initialSpeed > 0.0) {
            spawnedGroup.setForwardForce(initialSpeed);
        }
        if (spawnedGroup != null && !spawnedGroup.isEmpty()) {
            CartProperties.setEditing(player, spawnedGroup.tail().getProperties());
        }
        return SpawnResult.SUCCESS;
    }

    public static enum SpawnResult {
        SUCCESS(Localization.CHEST_SPAWN_SUCCESS),
        FAIL_EMPTY(Localization.CHEST_SPAWN_EMPTY),
        FAIL_NORAIL(Localization.CHEST_SPAWN_NORAIL),
        FAIL_NORAIL_LOOK(Localization.CHEST_SPAWN_NORAIL_LOOK),
        FAIL_RAILTOOSHORT(Localization.CHEST_SPAWN_RAILTOOSHORT),
        FAIL_BLOCKED(Localization.CHEST_SPAWN_BLOCKED),
        FAIL_NO_PERM(null);

        private final Localization locale;

        private SpawnResult(Localization locale) {
            this.locale = locale;
        }

        public boolean hasMessage() {
            return this.locale != null;
        }

        public Localization getLocale() {
            return this.locale;
        }
    }
}

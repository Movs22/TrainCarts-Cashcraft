package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.category.*;
import com.bergerkiller.bukkit.tc.properties.standard.type.CartLockOrientation;

/**
 * All standard TrainCarts built-in train and cart properties
 */
public class StandardProperties {

    public static final ModelProperty MODEL = new ModelProperty();
    public static final DestinationProperty DESTINATION = new DestinationProperty();
    public static final StationProperty STATIONS = new StationProperty();
    public static final DestinationRouteProperty DESTINATION_ROUTE = new DestinationRouteProperty();
    public static final DestinationRouteProperty.IndexProperty DESTINATION_ROUTE_INDEX = new DestinationRouteProperty.IndexProperty();
    public static final TagSetProperty TAGS = new TagSetProperty();
    public static final ExitOffsetProperty EXIT_OFFSET = new ExitOffsetProperty();
    public static final TicketSetProperty TICKETS = new TicketSetProperty();
    public static final KeepChunksLoadedProperty KEEP_CHUNKS_LOADED = new KeepChunksLoadedProperty();
    public static final BankingOptionsProperty BANKING = new BankingOptionsProperty();
    public static final SlowdownProperty SLOWDOWN = new SlowdownProperty();
    public static final CollisionProperty COLLISION = new CollisionProperty();
    public static final PlayerEnterProperty ALLOW_PLAYER_ENTER = new PlayerEnterProperty();
    public static final PlayerExitProperty ALLOW_PLAYER_EXIT = new PlayerExitProperty();
    public static final PlayerEnterAndExitProperty ALLOW_PLAYER_ENTER_AND_EXIT = new PlayerEnterAndExitProperty();
    public static final GravityProperty GRAVITY = new GravityProperty();
    public static final FrictionProperty FRICTION = new FrictionProperty();
    public static final SpeedLimitProperty SPEEDLIMIT = new SpeedLimitProperty();
    public static final TrainNameFormatProperty TRAIN_NAME_FORMAT = new TrainNameFormatProperty();
    public static final OnlyOwnersCanEnterProperty ONLY_OWNERS_CAN_ENTER = new OnlyOwnersCanEnterProperty();
    public static final PickUpItemsProperty PICK_UP_ITEMS = new PickUpItemsProperty();
    public static final SoundEnabledProperty SOUND_ENABLED = new SoundEnabledProperty();
    public static final InvincibleProperty INVINCIBLE = new InvincibleProperty();
    public static final AllowPlayerTakeProperty ALLOW_PLAYER_TAKE = new AllowPlayerTakeProperty();
    public static final SpawnItemDropsProperty SPAWN_ITEM_DROPS = new SpawnItemDropsProperty();
    public static final DisplayNameProperty DISPLAY_NAME = new DisplayNameProperty();
    public static final AllowManualMobMovementProperty ALLOW_MOB_MANUAL_MOVEMENT = new AllowManualMobMovementProperty();
    public static final AllowManualPlayerMovementProperty ALLOW_PLAYER_MANUAL_MOVEMENT = new AllowManualPlayerMovementProperty();
    public static final OwnerSetProperty OWNERS = new OwnerSetProperty();
    public static final OwnerPermissionSet OWNER_PERMISSIONS = new OwnerPermissionSet();
    public static final BreakBlocksProperty BLOCK_BREAK_TYPES = new BreakBlocksProperty();
    public static final RealtimePhysicsProperty REALTIME_PHYSICS = new RealtimePhysicsProperty();
    public static final EnterMessageProperty ENTER_MESSAGE = new EnterMessageProperty();

    public static final ICartProperty<String> DRIVE_SOUND = new ICartProperty<String>() {

        @PropertyParser("drivesound|driveeffect")
        public String parseSound(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "driveSound", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "driveSound", value);
        }
    };

    public static final ICartProperty<String> DESTINATION_LAST_PATH_NODE = new ICartProperty<String>() {
        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "lastPathNode", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "lastPathNode", value);
        }
    };

    public static final ITrainProperty<String> KILL_MESSAGE = new ITrainProperty<String>() {

        @PropertyParser("killmessage")
        public String parseMessage(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "killMessage", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "killMessage", value);
        }
    };

    public static final ITrainProperty<Boolean> SUFFOCATION = new ITrainProperty<Boolean>() {

        @PropertyParser("suffocation")
        public boolean parseSuffocate(PropertyParseContext<Boolean> parser) {
            return parser.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "suffocation", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "suffocation", value);
        }
    };

    public static final ITrainProperty<Boolean> REQUIRE_POWERED_MINECART = new ITrainProperty<Boolean>() {

        @PropertyParser("requirepoweredminecart|requirepowered")
        public boolean parseRequirePowered(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public boolean hasPermission(CommandSender sender, String name) {
            return Permission.PROPERTY_REQUIREPOWEREDCART.has(sender);
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "requirePoweredMinecart", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "requirePoweredMinecart", value);
        }
    };

    public static final ITrainProperty<Double> COLLISION_DAMAGE = new ITrainProperty<Double>() {
        private final Double DEFAULT = 1.0;

        @PropertyParser("collisiondamage")
        public double parseDamage(PropertyParseContext<Double> context) {
            return context.inputDouble();
        }

        @Override
        public Double getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "collisionDamage", double.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
            Util.setConfigOptional(config, "collisionDamage", value);
        }
    };

    /**
     * Configures train behavior for waiting on obstacles on the track ahead
     */
    public static final WaitOptionsProperty WAIT = new WaitOptionsProperty();

    /**
     * The persistent data stored for the sign skip feature of carts and trains.
     * This property is used internally by the SignSkipOptions class.
     */
    public static final SignSkipOptionsProperty SIGN_SKIP = new SignSkipOptionsProperty();

    /**
     * Applies default train properties from configuration by name to the train.
     * Only used to make this available as a property.
     */
    public static final DefaultConfigSyntheticProperty DEFAULT_CONFIG = new DefaultConfigSyntheticProperty();

    /**
     * When the orientation to spawn a train with is locked, this property stores per cart the orientation
     * the cart should have. This makes sure that when the train is saved again in the future, the train
     * isn't reversed when it was saved while moving backwards.<br>
     * <br>
     * Internal use only.
     */
    public static final ICartProperty<CartLockOrientation> LOCK_ORIENTATION_FLIPPED = new ICartProperty<CartLockOrientation>() {
        @Override
        public CartLockOrientation getDefault() {
            return CartLockOrientation.NONE;
        }

        @Override
        public Optional<CartLockOrientation> readFromConfig(ConfigurationNode config) {
            Boolean flipped = config.get("flippedAtSave", Boolean.class, null);
            return (flipped == null)
                    ? Optional.empty()
                    : Optional.of(CartLockOrientation.locked(flipped.booleanValue()));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<CartLockOrientation> value) {
            CartLockOrientation ori;
            if (!value.isPresent() || (ori = value.get()) == CartLockOrientation.NONE) {
                config.remove("flippedAtSave");
            } else {
                config.set("flippedAtSave", ori.isFlipped());
            }
        }
    };

    /**
     * Updates the saved configuration of a single cart so that its orientation is flipped 180 degrees.
     * Used when reversing a saved train.
     *
     * @param cartConfig Configuration of the cart
     */
    public static void reverseSavedCart(ConfigurationNode cartConfig) {
        cartConfig.set("flipped", !cartConfig.get("flipped", false));

        CartLockOrientation ori = LOCK_ORIENTATION_FLIPPED.readFromConfig(cartConfig).orElse(CartLockOrientation.NONE);
        if (ori != CartLockOrientation.NONE) {
            LOCK_ORIENTATION_FLIPPED.writeToConfig(cartConfig,
                    Optional.of(CartLockOrientation.locked(!ori.isFlipped())));
        }
    }
}

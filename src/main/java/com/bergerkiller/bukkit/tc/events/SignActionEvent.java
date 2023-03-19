package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirection;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeRegular;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

public class SignActionEvent extends Event implements Cancellable, TrainCarts.Provider {
    private static final HandlerList handlers = new HandlerList();

    private final TrackedSign sign;
    private final String lowerSecondCleanedLine;
    private RailEnterDirection[] enterDirections;
    private SignActionType actionType;
    private BlockFace raildirection = null;
    private MinecartMember<?> member = null;
    private MinecartGroup group = null;
    private RailState overrideMemberEnterState = null;
    private boolean memberchecked = false;
    private boolean cancelled = false;

    public SignActionEvent(Block signblock, MinecartMember<?> member) {
        this(signblock);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, RailPiece rail, MinecartMember<?> member) {
        this(signblock, rail);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, MinecartGroup group) {
        this(signblock);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(TrackedSign trackedSign, MinecartMember<?> member) {
        this(trackedSign);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(TrackedSign trackedSign, MinecartGroup group) {
        this(trackedSign);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, RailPiece rail, MinecartGroup group) {
        this(signblock, rail);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(final Block signBlock) {
        this(TrackedSign.forRealSign(signBlock, null));
    }

    public SignActionEvent(final Block signblock, RailPiece rail) {
        this(TrackedSign.forRealSign(signblock, rail));
    }

    public SignActionEvent(final Block signblock, final Sign sign, RailPiece rail) {
        this(TrackedSign.forRealSign(sign, signblock, rail));
    }

    public SignActionEvent(TrackedSign sign) {
        if (sign == null) {
            throw new IllegalArgumentException("Tracked sign is null");
        }
        this.sign = sign;
        this.actionType = SignActionType.NONE;
        this.lowerSecondCleanedLine = Util.cleanSignLine(sign.sign.getLine(1)).toLowerCase(Locale.ENGLISH);
        if (this.sign.getHeader().isLegacyConverted() && this.sign.getHeader().isValid()) {
            this.setLine(0, this.sign.getHeader().toString());
        }
        this.enterDirections = null;
    }

    @Override
    public TrainCarts getTrainCarts() {
        //TODO: Actually store a reference to traincarts somewhere
        //      Right now there is no good way without altering the constructor
        return TrainCarts.plugin;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Sets whether levers connected to this Sign are toggled
     *
     * @param down state to set to
     */
    public void setLevers(boolean down) {
        this.getTrackedSign().setOutput(down);
    }

    /**
     * Gets whether this sign is used with track that is vertically-aligned.
     * 
     * @return True if this sign is used with a vertically-aligned track
     */
    public boolean isRailsVertical() {
        if (!this.hasRails()) {
            return false;
        }

        BlockFace signDirection = this.getFacing().getOppositeFace();
        RailState state = new RailState();
        state.setRailPiece(this.getRailPiece());
        state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
        state.position().setMotion(signDirection);
        state.initEnterDirection();
        state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
        //TODO: This could be done more efficiently!
        return FaceUtil.isVertical(Util.vecToFace(state.position().getMotion(), false));
    }

    /**
     * Overrides the output of {@link #getCartEnterDirection()}, {@link #getCartEnterFace()}
     * and {@link #getCartEnterState()} to return the information from the rail state specified.
     * This is important when the member is nowhere near the sign to calculate this automatically.
     *
     * @param enterState RailState to set
     */
    public void overrideCartEnterState(RailState enterState) {
        this.overrideMemberEnterState = enterState;
    }

    /**
     * Gets the direction vector of the cart upon entering the rails
     * that triggered this sign. If no cart exists, it defaults to the activating direction
     * of the sign (facing or watched directions).
     * 
     * @return enter direction vector
     */
    public Vector getCartEnterDirection() {
        // The expected
        {
            RailState state = this.getCartEnterState();
            if (state != null) {
                return state.enterDirection();
            }
        }

        // Find the facing direction from watched directions, or sign orientation
        BlockFace signDirection;
        if (this.getWatchedDirections().length > 0) {
            signDirection = this.getWatchedDirections()[0];
        } else {
            signDirection = this.getFacing().getOppositeFace();
        }

        // Snap sign direction to the rails, if a rails exists
        if (this.hasRails()) {
            RailState state = new RailState();
            state.setRailPiece(this.getRailPiece());
            state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
            state.position().setMotion(signDirection);
            state.initEnterDirection();
            state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
            return state.position().getMotion();
        }

        return FaceUtil.faceToVector(signDirection);
    }

    /**
     * Gets the direction Block Face of the cart upon entering the rails
     * that triggered this sign. If no cart exists, it defaults to the activating direction
     * of the sign (facing or watched directions).
     * 
     * @return enter direction face
     */
    public BlockFace getCartEnterFace() {
        // The expected
        {
            RailState state = this.getCartEnterState();
            if (state != null) {
                return state.enterFace();
            }
        }

        // Find the facing direction from watched directions, or sign orientation
        BlockFace signDirection;
        if (this.getWatchedDirections().length > 0) {
            signDirection = this.getWatchedDirections()[0];
        } else {
            signDirection = this.getFacing().getOppositeFace();
        }

        // Snap sign direction to the rails, if a rails exists
        if (this.hasRails()) {
            RailState state = new RailState();
            state.setRailPiece(this.getRailPiece());
            state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
            state.position().setMotion(signDirection);
            state.initEnterDirection();
            state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
            state.initEnterDirection();
            return state.enterFace();
        }

        return signDirection;
    }

    /**
     * Gets the RailState of the cart that activated this sign at the moment of activating it.
     * This information can be used to deduce whether or not the sign should be activated based
     * on enter directions.<br>
     * <br>
     * May return null if this information is not available.
     *
     * @return RailState of the cart that activated this sign, upon activating it.
     *         Returns null if this sign lacks rails, or no member was involved in activating
     *         it.
     */
    public RailState getCartEnterState() {
        // If enter state is overrided, set it without computing anything.
        {
            RailState state;
            if ((state = this.overrideMemberEnterState) != null) {
                return state;
            }
        }

        if (this.hasRails() && this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            RailPiece railPiece = this.getRailPiece();
            for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                if (rail.member == this.member && rail.state.railPiece().equals(railPiece)) {
                    return rail.state;
                }
            }
        }

        return null;
    }

    /**
     * Gets the direction a minecart has above the rails of this Sign.
     *
     * @return cart direction
     * @deprecated Use {@link #getCartEnterFace} instead
     */
    @Deprecated
    public BlockFace getCartDirection() {
        return this.getCartEnterFace();
    }

    /* ============================= Deprecated BlockFace Junctions =============================== */

    /**
     * Sets the rails above this sign to connect with the from and to directions<br>
     * If the cart has to be reversed, that is done
     *
     * @param from direction
     * @param to   direction
     * @deprecated No longer limited to BlockFace directions, use junctions instead
     */
    @Deprecated
    public void setRailsFromTo(BlockFace from, BlockFace to) {
        setRailsFromTo(findJunction(from), findJunction(to));
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction to the direction specified
     *
     * @param to direction
     * @deprecated No longer limited to BlockFace directions, use junctions instead
     */
    @Deprecated
    public void setRailsTo(BlockFace to) {
        setRailsTo(findJunction(to));
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction into a direction specified<br>
     * Relative directions, like left and right, are relative to the sign direction
     *
     * @param direction to set the rails to
     * @deprecated No longer limited to BlockFace directions, use junctions instead
     */
    @Deprecated
    public void setRailsTo(Direction direction) {
        setRailsTo(findJunction(direction));
    }

    /* ===================================================================================== */

    /**
     * Gets a list of valid junctions that can be taken on the rails block of this sign
     * 
     * @return junctions
     */
    public List<RailJunction> getJunctions() {
        RailPiece piece = this.getRailPiece();
        if (piece.isNone()) {
            return Collections.emptyList();
        } else {
            return piece.type().getJunctions(piece.block());
        }
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by name
     * 
     * @param junctionName
     * @return junction, null if not found
     */
    public RailJunction findJunction(String junctionName) {
        // Match the junction by name exactly
        for (RailJunction junc : getJunctions()) {
            if (junc.name().equals(junctionName)) {
                return junc;
            }
        }

        // Attempt parsing the junctionName into a Direction statement
        // This includes special handling for continue/reverse, which uses cart direction
        final String dirText = junctionName.toLowerCase(Locale.ENGLISH);
        BlockFace enterFace = this.getCartEnterFace();
        if (LogicUtil.contains(dirText, "c", "continue")) {
            return findJunction(Direction.fromFace(enterFace));
        } else if (LogicUtil.contains(dirText, "i", "rev", "reverse", "inverse")) {
            return findJunction(Direction.fromFace(enterFace.getOppositeFace()));
        } else {
            return findJunction(Direction.parse(dirText));
        }
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by face direction
     * 
     * @param face
     * @return junction, null if not found
     */
    public RailJunction findJunction(BlockFace face) {
        return RailJunction.findBest(getJunctions(), FaceUtil.faceToVector(face)).orElse(null);
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by a
     * Direction statement. This also handles logic such as sign-relative left, right and forward.
     * 
     * @param direction
     * @return junction, null if not found
     */
    public RailJunction findJunction(Direction direction) {
        if (direction == Direction.NONE || direction == null) {
            return null;
        }
        BlockFace to = direction.getDirection(this.getFacing());
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            // Only do this crap for switched vanilla rails. It completely confuses TC-Coasters!
            if (this.getRailType() instanceof RailTypeRegular && !this.isConnectedRails(to)) {
                to = Direction.FORWARD.getDirection(this.getFacing());
            }
        }

        return findJunction(to);
    }

    /**
     * Gets the rail junction from which the rails of this sign were entered.
     * This is used when switching rails to select the 'from' junction.
     * 
     * @return rail junction
     */
    public RailJunction getEnterJunction() {
        if (this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            TrackedRail memberRail = null;
            if (this.hasRails()) {
                Block rails = this.getRails();
                for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                    if (rail.member == this.member && rail.state.railBlock().equals(rails)) {
                        memberRail = rail;
                        break;
                    }
                }
            }

            // Ask the minecart itself alternatively
            if (memberRail == null) {
                memberRail = this.member.getRailTracker().getRail();
            }

            // Compute the position at the start of the rail's path by walking 'back'
            RailPath.Position pos;
            {
                RailState tmp = memberRail.state.cloneAndInvertMotion();
                memberRail.getPath().move(tmp, Double.MAX_VALUE);
                pos = tmp.position();
            }

            // Find the junction closest to this start position
            double min_dist = Double.MAX_VALUE;
            RailJunction best_junc = null;
            for (RailJunction junc : memberRail.state.railType().getJunctions(memberRail.state.railBlock())) {
                if (junc.position().relative) {
                    pos.makeRelative(memberRail.state.railBlock());
                } else {
                    pos.makeAbsolute(memberRail.state.railBlock());
                }
                double dist_sq = junc.position().distanceSquared(pos);
                if (dist_sq < min_dist) {
                    min_dist = dist_sq;
                    best_junc = junc;
                }
            }
            return best_junc;
        }

        //TODO: Do we NEED a fallback?
        return null;
    }

    public void setRailsTo(String toJunctionName) {
        setRailsFromTo(getEnterJunction(), findJunction(toJunctionName));
    }

    public void setRailsTo(RailJunction toJunction) {
        setRailsFromTo(getEnterJunction(), toJunction);
    }

    public void setRailsFromTo(String fromJunctionName, String toJunctionName) {
        setRailsFromTo(findJunction(fromJunctionName), findJunction(toJunctionName));
    }

    public void setRailsFromTo(RailJunction fromJunction, String toJunctionName) {
        setRailsFromTo(fromJunction, findJunction(toJunctionName));
    }

    public void setRailsFromTo(RailJunction fromJunction, RailJunction toJunction) {
        if (!this.hasRails() || fromJunction == null || toJunction == null) {
            return;
        }

        RailPiece rail = this.sign.getRail();

        // If from and to are the same, the train is launched back towards where it came
        // In this special case, select another junction part of the path as the from
        // and launch the train backwards
        if (fromJunction.name().equals(toJunction.name())) {
            // Pick any other junction that is not equal to 'to'
            // Prefer junctions that have already been selected (assert from rail path)
            RailState state = RailState.getSpawnState(rail);
            RailPath path = state.loadRailLogic().getPath();
            RailPath.Position p0 = path.getStartPosition();
            RailPath.Position p1 = path.getEndPosition();
            double min_dist = Double.MAX_VALUE;
            for (RailJunction junc : rail.getJunctions()) {
                if (junc.name().equals(fromJunction.name())) {
                    continue;
                }
                if (junc.position().relative) {
                    p0.makeRelative(rail.block());
                    p1.makeRelative(rail.block());
                } else {
                    p0.makeAbsolute(rail.block());
                    p1.makeAbsolute(rail.block());
                }
                double dist_sq = Math.min(p0.distanceSquared(junc.position()),
                                          p1.distanceSquared(junc.position()));
                if (dist_sq < min_dist) {
                    min_dist = dist_sq;
                    fromJunction = junc;
                }
            }

            // Switch it
            rail.switchJunction(fromJunction, toJunction);

            // Launch train into the opposite direction, if required
            if (this.hasMember()) {
                // Break this cart from the train if needed
                MinecartGroup group = this.member.getGroup();
                if (group != null) {
                    group.getActions().clear();
                    group.split(this.member.getIndex());
                }
                group = this.member.getGroup();
                if (group != null) {
                    group.reverse();
                }
            }

            return;
        }

        // All the switching logic under normal conditions happens here
        rail.switchJunction(fromJunction, toJunction);
    }

    /**
     * Gets the action represented by this event
     *
     * @return Event action type
     */
    public SignActionType getAction() {
        return this.actionType;
    }

    /**
     * Sets the action represented by this event
     *
     * @param type to set to
     * @return This sign action event
     */
    public SignActionEvent setAction(SignActionType type) {
        this.actionType = type;
        return this;
    }

    /**
     * Checks whether one of the types specified equal the action of this event
     *
     * @param types to check against
     * @return True if one of the types is the action, False if not
     */
    public boolean isAction(SignActionType... types) {
        return LogicUtil.contains(this.actionType, types);
    }

    /**
     * Checks whether a rails with a minecart on it is available above this sign
     *
     * @return True if available, False if not
     */
    public boolean hasRailedMember() {
        return this.hasRails() && this.hasMember();
    }

    /**
     * Obtains the header of this sign containing relevant properties that are contained
     * on the first line of a TrainCarts sign.
     * 
     * @return sign header
     */
    public SignActionHeader getHeader() {
        return this.sign.getHeader();
    }

    /**
     * Checks whether power reading is inverted for this Sign
     *
     * @return True if it is inverted, False if not
     * @deprecated Use the properties in {@link #getHeader()} instead
     */
    @Deprecated
    public boolean isPowerInverted() {
        return getHeader().isInverted();
    }

    /**
     * Checks whether power reading always returns on for this Sign
     *
     * @return True if the power is always on, False if not
     * @deprecated Use the properties in {@link #getHeader()} instead
     */
    @Deprecated
    public boolean isPowerAlwaysOn() {
        return getHeader().isAlwaysOn();
    }

    public PowerState getPower(BlockFace from) {
        return this.sign.getPower(from);
    }

    public boolean isPowered(BlockFace from) {
        if (this.sign.getHeader().isAlwaysOff()) {
            return false;
        }
        return this.sign.getHeader().isAlwaysOn() || this.sign.getHeader().isInverted() != this.getPower(from).hasPower();
    }

    /**
     * Gets whether this Sign is powered according to the sign rules.
     * <ul>
     * <li>If this is a REDSTONE_ON event, true is returned</li>
     * <li>If this is a REDSTONE_OFF event, false is returned</li>
     * <li>If the sign header indicates always-on, true is returned all the time</li>
     * <li>If the sign header indicates power inversion, true is returned when no Redstone power exists</li>
     * <li>If the sign header indicates a rising/falling edge trigger, true is returned for the appropriate
     * REDSTONE_ON/REDSTONE_OFF event only</li>
     * <li>For other cases (default), true is returned when Redstone power exists to this sign</li>
     * </ul>
     * 
     * @return True if the sign is powered, False if not
     */
    public boolean isPowered() {
        SignActionHeader header = this.sign.getHeader();
        if (header.isAlwaysOff()) {
            return false;
        }
        if (this.actionType == SignActionType.REDSTONE_ON) {
            return true;
        }
        if (header.onPowerRising() || header.onPowerFalling()) {
            return false; // Only redstone transition changes can power the sign temporarily
        }
        if (this.actionType == SignActionType.REDSTONE_OFF) {
            return false;
        }
        return header.isAlwaysOn() || this.isPoweredRaw(header.isInverted());
    }

    /**
     * Checks if this sign is powered, ignoring settings on the sign.
     *
     * @param invert True to invert the power as a result, False to get the normal result
     * @return True if powered when not inverted, or not powered and inverted
     * @deprecated Use {@link PowerState#isSignPowered(signBlock, inverted)} instead
     */
    @Deprecated
    public boolean isPoweredRaw(boolean invert) {
        if (invert) {
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                if (this.sign.getPower(face) == PowerState.ON) {
                    return false;
                }
            }
            return true;
        } else {
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                if (this.sign.getPower(face).hasPower()) return true;
            }
            return false;
        }
    }

    public boolean isPoweredFacing() {
        return this.actionType == SignActionType.REDSTONE_ON || (this.isFacing() && this.isPowered());
    }

    public TrackedSign getTrackedSign() {
        return this.sign;
    }

    public Block getBlock() {
        return this.sign.signBlock;
    }

    public Block getAttachedBlock() {
        return this.sign.getAttachedBlock();
    }

    /**
     * Gets the RailPiece linked with this sign. Returns {@link RailPiece#NONE} if this
     * sign has no rails linked with it.
     *
     * @return RailPiece if found or set, NONE if not found
     */
    public RailPiece getRailPiece() {
        return this.sign.getRail();
    }

    public RailType getRailType() {
        return getRailPiece().type();
    }

    public Block getRails() {
        return getRailPiece().block();
    }

    public World getWorld() {
        return this.sign.signBlock.getWorld();
    }

    public boolean hasRails() {
        return !this.getRailPiece().isNone();
    }

    /**
     * Gets a 'rail direction', which only makes sense for vanilla rails.
     *
     * @return direction of the rails (BlockFace), for example, 'north-east' or 'up'
     * @deprecated BlockFace offers too little resolution
     */
    @Deprecated
    public BlockFace getRailDirection() {
        RailPiece rail = this.getRailPiece();
        if (rail.isNone()) return null;
        if (this.raildirection == null) {
            this.raildirection = rail.type().getDirection(rail.block());
        }
        return this.raildirection;
    }

    /**
     * Gets the center location of the rails where the minecart is centered at the rails
     *
     * @return Center location
     */
    public Location getCenterLocation() {
        RailPiece railPiece = this.getRailPiece();
        if (railPiece.isNone()) return null;
        return railPiece.type().getSpawnLocation(railPiece.block(), this.getFacing());
    }

    /**
     * Gets the Location of the rails
     *
     * @return Rail location, or null if there are no rails
     */
    public Location getRailLocation() {
        RailPiece rail = this.sign.getRail();
        if (rail.isNone()) return null;
        return rail.block().getLocation().add(0.5, 0, 0.5);
    }

    public Location getLocation() {
        return this.sign.signBlock.getLocation();
    }

    public BlockFace getFacing() {
        return this.sign.getFacing();
    }

    /**
     * Checks whether the minecart that caused this event is facing the sign correctly.
     * More exactly, it checks whether the recent motion of the train matches the
     * enter direction rules specified on the sign. If the train isn't moving,
     * always returns true.<br>
     * <br>
     * If no train activated this event, always returns false.
     *
     * @return True if the minecart is able to activate this sign, False if not
     * @see #isEnterActivated()
     */
    public boolean isFacing() {
        MinecartMember<?> member = this.getMember();
        if (member == null) {
            return false;
        }
        if (!member.isMoving()) {
            return true;
        }
        return this.isEnterActivated();
    }

    /**
     * Gets the sign associated with this sign action event
     *
     * @return Sign
     */
    public Sign getSign() {
        return this.sign.sign;
    }

    /**
     * Searches for additional signs below this sign that extend the number of lines on
     * this sign. These extra signs may not match other types of sign actions.
     *
     * @return Extra lines on signs below this sign. Returns an empty array of there are
     *         none.
     */
    public String[] getExtraLinesBelow() {
        return this.sign.getExtraLines();
    }

    /**
     * Checks if rails at the offset specified are connected to the rails at this sign
     *
     * @param direction to connect to
     * @return True if connected, False if not
     */
    public boolean isConnectedRails(BlockFace direction) {
        return Util.isConnectedRails(this.getRailPiece(), direction);
    }

    /**
     * Gets a collection of all Minecart Groups this sign remote controls
     *
     * @return Remotely controlled groups
     */
    public Collection<MinecartGroup> getRCTrainGroups() {
        return MinecartGroup.matchAll(this.getRCName());
    }

    /**
     * Gets a collection of all Minecart Group train properties this sign remotely controls
     *
     * @return Train properties of remotely controlled groups (unmodifiable)
     */
    public Collection<TrainProperties> getRCTrainProperties() {
        return TrainProperties.matchAll(this.getRCName());
    }

    /**
     * Gets the remote-controlled train name format used on this sign
     *
     * @return Remote control name, or null if this is not a RC sign
     */
    public String getRCName() {
        if (this.isRCSign()) {
            return this.sign.getHeader().getRemoteName();
        } else {
            return null;
        }
    }

    /**
     * Gets or finds the minecart associated with this sign right now<br>
     * Will find a possible minecart on rails above this sign
     * if none was specified while creating this event
     *
     * @return Minecart Member
     */
    public MinecartMember<?> getMember() {
        if (this.member == null) {
            if (!this.memberchecked) {
                this.member = this.hasRails() ? MinecartMemberStore.getAt(this.getRailPiece().block()) : null;
                this.memberchecked = true;
            }
            if (this.member == null && this.group != null && !this.group.isEmpty()) {
                if (this.actionType == SignActionType.GROUP_LEAVE) {
                    this.member = this.group.tail();
                } else {
                    // Get the Minecart in the group that contains this sign
                    for (MinecartMember<?> member : this.group) {
                        if (member.getSignTracker().containsSign(this.sign)) {
                            this.member = member;
                            break;
                        }
                    }
                    // Fallback: use head
                    if (this.member == null) {
                        this.member = this.group.head();
                    }
                }
            }
        }
        if (this.member == null || !this.member.isInteractable()) {
            return null;
        }
        return this.member;
    }

    /**
     * Sets the minecart associated with this event, overriding any previous members and groups.
     * <b>This should not be called while handling the event</b>
     *
     * @param member Member to set to
     */
    public void setMember(MinecartMember<?> member) {
        this.member = member;
        this.memberchecked = true;
        this.group = member.getGroup();
    }

    /**
     * Sets the train associated with this event, overriding any previous member and/or groups.
     * <b>This should not be called while handling the event</b>
     *
     * @param group Group to set to
     */
    public void setGroup(MinecartGroup group) {
        this.member = null;
        this.memberchecked = true;
        this.group = group;
    }

    /**
     * Checks whether a minecart is associated with this event
     *
     * @return True if a member is available, False if not
     */
    public boolean hasMember() {
        return this.getMember() != null;
    }

    /**
     * Gets whether the watched directions of this Sign are defined on the first line.
     * If this returns True, user-specified watched directions are used.
     * If this returns False, environment-specific watched directions are used.
     *
     * @return True if defined, False if not
     */
    public boolean isWatchedDirectionsDefined() {
        return this.getHeader().hasEnterDirections();
    }

    /**
     * Gets the directions minecarts have to move to be detected by this sign
     *
     * @return Watched directions
     */
    public BlockFace[] getWatchedDirections() {
        return RailEnterDirection.toFacesOnly(this.getEnterDirections());
    }

    /**
     * Gets the directions that activate this sign, according to the trigger direction
     * rules on the sign, or a default fallback behavior.
     *
     * @return Rail enter directions
     */
    public RailEnterDirection[] getEnterDirections() {
        // Lazy initialization here
        if (this.enterDirections == null) {
            // Find out what directions are watched by this sign
            if (this.sign.getHeader().hasEnterDirections()) {
                // From first line header ([train:left] -> blockface[] for left)
                this.enterDirections = this.sign.getHeader().getEnterDirections(
                        this.getRailPiece(), this.getFacing().getOppositeFace());
            } else if (TCConfig.trainsCheckSignFacing) {
                // Ask rails, the RailType NONE also handled this function, so no NPE here
                BlockFace[] faces = this.getRailPiece().type().getSignTriggerDirections(
                        this.getRailPiece().block(), this.getBlock(), this.getFacing());
                this.enterDirections = new RailEnterDirection[faces.length];
                for (int i = 0; i < faces.length; i++) {
                    this.enterDirections[i] = RailEnterDirection.toFace(faces[i]);
                }
            } else {
                // Always
                this.enterDirections = RailEnterDirection.ALL;
            }
        }
        return this.enterDirections;
    }

    /**
     * Gets whether the specified RailState would activate this sign according to
     * the sign trigger direction rules.
     *
     * @param state RailState of the train
     * @return True if it would activate this sign
     * @see #isEnterActivated()
     */
    public boolean isEnterActivated(RailState state) {
        for (RailEnterDirection dir : this.getEnterDirections()) {
            if (dir.match(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether the train that activated this sign will actually activate this
     * sign according to the sign trigger direction rules.<br>
     * <br>
     * Can only be used while handling group/member enter/leave events,
     * or while computing path finding/prediction.
     *
     * @return True if it would activate this sign
     * @see #isEnterActivated(RailState)
     */
    public boolean isEnterActivated() {
        RailState state = this.getCartEnterState();
        return state != null && isEnterActivated(state);
    }

    /**
     * Gets an array of possible directions in which spawner and teleporter signs can lay down trains
     * 
     * @return spawn directions
     */
    public BlockFace[] getSpawnDirections() {
        BlockFace[] watched = this.getWatchedDirections();
        BlockFace[] spawndirs = new BlockFace[watched.length];
        for (int i = 0; i < spawndirs.length; i++) {
            spawndirs[i] = watched[i].getOppositeFace();
        }
        return spawndirs;
    }

    /**
     * Checks if a given BlockFace direction is watched by this sign
     *
     * @param direction to check
     * @return True if watched, False otherwise
     */
    public boolean isWatchedDirection(BlockFace direction) {
        return LogicUtil.contains(RailEnterDirection.toFace(direction), this.getEnterDirections());
    }

    /**
     * Checks if a given movement direction is watched by this sign.
     * When this returns true, the sign should be activated.
     * 
     * @param direction to check
     * @return True if watched, False otherwise
     */
    public boolean isWatchedDirection(Vector direction) {
        for (RailEnterDirection dir : this.getEnterDirections()) {
            if (dir.motionDot(direction) > 0.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the Minecart Group that is associated with this event
     *
     * @return Minecart group
     */
    public MinecartGroup getGroup() {
        if (this.group != null) {
            return this.group;
        }
        MinecartMember<?> mm = this.getMember();
        return mm == null ? null : mm.getGroup();
    }

    /**
     * Checks whether a minecart group is associated with this event
     *
     * @return True if a group is available, False if not
     */
    public boolean hasGroup() {
        return this.getGroup() != null;
    }

    /**
     * Gets all the Minecart Members this sign (based on RC/train/cart type) is working on
     *
     * @return all Minecart Members being worked on
     */
    @SuppressWarnings("unchecked")
    public Collection<MinecartMember<?>> getMembers() {
        if (isTrainSign()) {
            return hasGroup() ? getGroup() : Collections.EMPTY_LIST;
        } else if (isCartSign()) {
            return hasMember() ? Collections.singletonList(getMember()) : Collections.EMPTY_LIST;
        } else if (isRCSign()) {
            ArrayList<MinecartMember<?>> members = new ArrayList<>();
            for (MinecartGroup group : getRCTrainGroups()) {
                members.addAll(group);
            }
            return members;
        }
        return Collections.EMPTY_LIST;
    }

    public String getLine(int index) {
        return Util.cleanSignLine(this.sign.getLine(index));
    }

    public String[] getLines() {
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = Util.cleanSignLine(this.sign.getLine(i));
        }
        return lines;
    }

    public void setLine(int index, String line) {
        this.sign.setLine(index, line);
    }

    /**
     * Gets the sign mode of this TrainCarts sign
     * 
     * @return Sign mode
     */
    public SignActionMode getMode() {
        return this.getHeader().getMode();
    }

    public boolean isCartSign() {
        return this.getHeader().isCart();
    }

    public boolean isTrainSign() {
        return this.getHeader().isTrain();
    }

    public boolean isRCSign() {
        return this.getHeader().isRC();
    }

    /**
     * Checks whether a given line starts with any of the text types specified
     *
     * @param line      number to check, 0 - 3
     * @param texttypes to check against
     * @return True if the line starts with any of the specified types, False if not
     */
    public boolean isLine(int line, String... texttypes) {
        String linetext = this.getLine(line).toLowerCase(Locale.ENGLISH);
        for (String type : texttypes) {
            if (linetext.startsWith(type)) return true;
        }
        return false;
    }

    /**
     * Checks the second line of this sign to see if it starts with one of the sign types specified.
     * Case is ignored, so sign types should be specified in lower-case.
     *
     * @param signtypes to check against
     * @return True if the first line starts with any of the types AND the sign has a valid mode, False if not
     */
    public boolean isType(String... signtypes) {
        if (getHeader().isValid()) {
            String s = this.lowerSecondCleanedLine;
            for (String type : signtypes) {
                if (s.startsWith(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        Block signBlock = this.sign.signBlock;
        String text = "{ block=[" + signBlock.getX() + "," + signBlock.getY() + "," + signBlock.getZ() + "]";
        text += ", action=" + this.actionType;
        text += ", watched=[";
        for (int i = 0; i < this.getWatchedDirections().length; i++) {
            if (i > 0) text += ",";
            text += this.getWatchedDirections()[i].name();
        }
        text += "]";
        if (this.sign == null) {
            text += " }";
        } else {
            text += ", lines=";
            String[] lines = this.getLines();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0 && lines[i].length() > 0) text += " ";
                text += lines[i];
            }
            text += " }";
        }
        return text;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}

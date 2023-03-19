package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartRideable;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.InteractionResult;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

public class MinecartMemberRideable extends MinecartMember<CommonMinecartRideable> {
    private List<Entity> oldPassengers = new ArrayList<Entity>();

    public MinecartMemberRideable(TrainCarts plugin) {
        super(plugin);
    }

    @Override
    public InteractionResult onInteractBy(HumanEntity interacter, HumanHand hand) {
        // Note: humans can technically sneak too! But Bukkit has no method for it in the API.
        if ((interacter instanceof Player) && ((Player) interacter).isSneaking()) {
            return InteractionResult.PASS;
        }

        // Is there a seat available to add a player?
        if (this.getAvailableSeatCount(interacter) == 0) {
            return InteractionResult.PASS;
        }

        // If already a passenger, all we do here is try to swap seats
        // If the player goes to the same seat he is already in, nothing happens
        if (this.entity.isPassenger(interacter)) {
            // If playerexit or playerenter are false, do not allow switching seats
            TrainProperties tprop = this.getGroup().getProperties();
            if (!tprop.getPlayersExit() || !tprop.getPlayersEnter()) {
                return InteractionResult.PASS;
            }

            // Ask attachments if changing seats is possible
            if (this.getAttachments().changeSeatsLookingAt(interacter)) {
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        }

        // Find the new seat the player will probably have after clicking on this member
        // This can fail if there is no seat the entity can enter (permission-wise)
        CartAttachmentSeat new_seat = this.getAttachments().findNewSeatForEntity(interacter);
        if (new_seat == null) {
            return InteractionResult.PASS;
        }

        // If previously inside another member, handle a seat change event instead
        MinecartMember<?> previous = MinecartMemberStore.getFromEntity(interacter.getVehicle());
        if (previous != null) {
            // Find the seat this interacting entity should be in right now
            // This could be the same as newSeat if the player is clicking rapidly - suppress those
            CartAttachmentSeat old_seat = previous.getAttachments().findSeat(interacter);
            if (old_seat == new_seat) {
                return InteractionResult.PASS;
            }

            return AttachmentControllerMember.handleSeatChange(interacter, old_seat, new_seat, true)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else {
            return AttachmentControllerMember.handleSeatChange(interacter, null, new_seat, true)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.oldPassengers.clear();
        this.oldPassengers.addAll(this.entity.getPassengers());
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (TCConfig.activatorEjectEnabled) {
            this.eject();
        }
    }

    @Override
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove();

        // Detect changes in passengers; call onPropertiesChanged() when that happens
        List<Entity> newPassengers = this.entity.getPassengers();
        if (!this.oldPassengers.equals(newPassengers)) {
            this.oldPassengers.clear();
            this.oldPassengers.addAll(newPassengers);
            this.onPropertiesChanged();
        }

        // This was a temporary hotfix for the passengers teleporting out of minecarts all the time
        // This bug is now fixed, and thus this hotfix is no longer needed
        // In case of re-occurance, uncomment this piece of code
        // Note: this DOES cause plugins like Lift and LaunchMe to fail! (they replace network controllers too)
        /*
        if (oldPassenger != currentPassenger) {
            // This was a temporary hotfix for the passengers teleporting out of minecarts all the time
            // This bug is now fixed, and thus this hotfix is no longer needed
            // In case of re-occurance, uncomment this piece of code
            // Note: this DOES cause plugins like Lift and LaunchMe to fail! (they replace network controllers too)
			if (currentPassenger != null) {
				CommonEntity<?> entity = CommonEntity.get(currentPassenger);
				if (!(entity.getNetworkController() instanceof MinecartPassengerNetwork)) {
					entity.setNetworkController(new MinecartPassengerNetwork());
				}
			} else if (oldPassenger != null) {
				CommonEntity<?> entity = CommonEntity.get(oldPassenger);
				if (entity.getNetworkController() instanceof MinecartPassengerNetwork) {
					entity.setNetworkController(new DefaultEntityNetworkController());
				}
			}
            oldPassenger = currentPassenger;
        }
        */
    }
}

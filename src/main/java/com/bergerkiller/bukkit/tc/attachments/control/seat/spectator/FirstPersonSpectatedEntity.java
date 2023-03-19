package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity;

/**
 * A type of entity that can be spectated, that has a particular appearance
 * when the player views himself in third-person (F5) view.
 */
public abstract class FirstPersonSpectatedEntity {
    protected final CartAttachmentSeat seat;
    protected final FirstPersonViewSpectator view;
    protected final AttachmentViewer player;

    public FirstPersonSpectatedEntity(CartAttachmentSeat seat, FirstPersonViewSpectator view, AttachmentViewer player) {
        this.seat = seat;
        this.view = view;
        this.player = player;
    }

    /**
     * Spawns whatever entity needs to be spectated, and starts spectating that entity
     *
     * @param eyeTransform Transformation matrix defining the position and orientation of
     *                     the camera/eye view.
     */
    public abstract void start(Matrix4x4 eyeTransform);

    /**
     * Stops spectating and despawns the entity/others used for spectating
     */
    public abstract void stop();

    /**
     * Updates the first-person view
     *
     * @param eyeTransform Transformation matrix defining the position and orientation of
     *                     the camera/eye view.
     */
    public abstract void updatePosition(Matrix4x4 eyeTransform);

    public abstract void syncPosition(boolean absolute);

    public abstract VirtualEntity getCurrentEntity();

    public static FirstPersonSpectatedEntity create(CartAttachmentSeat seat, FirstPersonViewSpectator view, AttachmentViewer player) {
        // In these two modes the actual player is made invisible
        if (view.getLiveMode() == FirstPersonViewMode.INVISIBLE ||
            view.getLiveMode() == FirstPersonViewMode.THIRD_P)
        {
            return new FirstPersonSpectatedEntityInvisible(seat, view, player);
        }

        // View through a floating armorstand. Head will be slightly above where the
        // camera view is.
        // Disabled: is now integrated into the player spectator mode
        //if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
        //    return new FirstPersonSpectatedEntityHead(seat, view, vmc);
        //}

        // Spectates a standing player
        if (seat.seated.getDisplayMode() == SeatedEntity.DisplayMode.STANDING) {
            return new FirstPersonSpectatedEntityPlayerStanding(seat, view, player);
        }

        // Default mode of showing the player itself
        return new FirstPersonSpectatedEntityPlayerSitting(seat, view, player);
    }
}

package com.bergerkiller.bukkit.tc.controller.global;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.collections.FastIdentityHashMap;

/**
 * Stores a mapping from players to their packet queues.
 * Automatically purges the queues when players log off.
 */
public class PacketQueueMap {
    private final FastIdentityHashMap<Player, PacketQueue> queues = new FastIdentityHashMap<>();
    private final List<PacketQueue> queuesList = new ArrayList<>();

    /**
     * Gets the PacketQueue to be used for sending packets to a Player
     *
     * @param player
     * @return PacketQueue
     */
    public synchronized PacketQueue getQueue(Player player) {
        PacketQueue queue = queues.get(player);
        if (queue == null) {
            if (player.isOnline()) {
                queue = PacketQueue.create(player);
                queues.put(player, queue);
                queuesList.add(queue);
            } else {
                queue = PacketQueue.createNoOp(player);
            }
        }
        return queue;
    }

    /**
     * Removes a PacketQueue created for a Player, if any
     *
     * @param player
     */
    public synchronized void remove(Player player) {
        PacketQueue queue = queues.remove(player);
        if (queue != null) {
            queuesList.remove(queue);
            queue.abort();
        }
    }

    /**
     * Calls {@link PacketQueue#sync()} on all queues active right now
     */
    public synchronized void syncAll() {
        queuesList.forEach(PacketQueue::sync);
    }
}

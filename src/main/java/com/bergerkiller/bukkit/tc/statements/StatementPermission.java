package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import org.bukkit.entity.Player;

public class StatementPermission extends Statement {

    @Override
    public boolean match(String text) {
        return false;
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("pm") || text.equals("perm");
    }

    @Override
    public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
        for (MinecartMember<?> member : group) {
            if (!handleArray(member, text, event)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
        if (member.getEntity().hasPlayerPassenger()) {
            for (Player player : member.getEntity().getPlayerPassengers()) {
                for (String perm : text) {
                    if (!player.hasPermission(perm)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

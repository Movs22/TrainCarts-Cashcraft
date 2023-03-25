package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;


public class SignActionPSD extends SignAction {

	
	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("psd");
	}
	public void place(SignActionEvent sign, int x, int y, int z, Material block, int doors, BlockFace facing, String offset) {
		/*
		 * Changes relative X Y Z to be the same even if the sign's facing is different
		 * (X = left/right, Z = front/back)
		 */
		if (facing == BlockFace.EAST) {
			int x1 = -z;
			int z1 = x;
			x = x1;
			z = z1;
		}
		if (facing == BlockFace.SOUTH) {
			int x1 = -x;
			int z1 = -z;
			x = x1;
			z = z1;
		}
		if (facing == BlockFace.WEST) {
			int x1 = z;
			int z1 = -x;
			x = x1;
			z = z1;
		}
		if(offset != "" && offset.split("/").length == 3 ) {
			x = x + Integer.parseInt(offset.split("/")[0]);
			y = y + Integer.parseInt(offset.split("/")[1]);
			z = z + Integer.parseInt(offset.split("/")[2]);
		}

		Block b = sign.getBlock().getRelative(x, y, z);
		b.setType(block);
		if (doors < 1)
			return;
		for (int i = 0; i < doors; i++) {
			if (facing == BlockFace.EAST) {
				x += 3;
			}
			if (facing == BlockFace.SOUTH) {
				z += 3;
			}
			if (facing == BlockFace.WEST) {
				x -= 3;
			}
			if (facing == BlockFace.NORTH) {
				z -= 3;
			}
			b = sign.getBlock().getRelative(x, y, z);
			b.setType(block);
		}

	}

	public void chime(SignActionEvent info, int x, int y, int z, int doors) {
		/*
		 * Changes relative X Y Z to be the same even if the sign's facing is different
		 * (X = left/right, Z = front/back)
		 */
		BlockFace facing = info.getFacing();
		if (facing == BlockFace.EAST) {
			int x1 = -z;
			int z1 = x;
			x = x1;
			z = z1;
		}
		if (facing == BlockFace.SOUTH) {
			int x1 = -x;
			int z1 = -z;
			x = x1;
			z = z1;
		}
		if (facing == BlockFace.WEST) {
			int x1 = z;
			int z1 = -x;
			x = x1;
			z = z1;
		}
		Block b = info.getBlock().getRelative(x, y, z);
		if (doors < 1)
			return;
		for (int i = 0; i < doors; i++) {
			if (facing == BlockFace.EAST) {
				x += 3;
			}
			if (facing == BlockFace.SOUTH) {
				z += 3;
			}
			if (facing == BlockFace.WEST) {
				x -= 3;
			}
			if (facing == BlockFace.NORTH) {
				z -= 3;
			}
			b = info.getBlock().getRelative(x, y, z);
			info.getWorld().playSound(b.getLocation(), "minecraft:block.note_block.chime", 1, 1);
		}

	}
	public int chimes;
	public void blink(int doors, SignActionEvent info) {
		String offset = info.getExtraLinesBelow()[0];
		chimes = 0;
		place(info, 2, 1, 2, Material.OCHRE_FROGLIGHT, doors, info.getFacing(), offset);
		chime(info, 2, 1, 4, doors);
		Bukkit.getScheduler().runTaskTimer(info.getTrainCarts(), (task) -> {
			place(info, 2, 1, 2, Material.GRAY_CONCRETE, doors, info.getFacing(), offset);
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				place(info, 2, 1, 2, Material.OCHRE_FROGLIGHT, doors, info.getFacing(), offset);
				chime(info, 2, 1, 4, doors);
			}, 2L);
			chimes += 4;
			if(chimes > 20) {
				task.cancel();
			}
		}, 2L, 4L);
		
	}
	
	@Override
	public void execute(SignActionEvent info) {
		if (!info.isPowered())
			return;
		if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER)) {
			String offset = info.getExtraLinesBelow()[0];
			if (!info.hasRailedMember())
				return;
			String doors = info.getLine(2);
			String duration = info.getLine(3);
			String[] stop = info.getExtraLinesBelow();
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				place(info, 2, 1, 2, Material.VERDANT_FROGLIGHT, Integer.parseInt(doors), info.getFacing(), offset);
				place(info, 2, 0, 1, Material.REDSTONE_TORCH, Integer.parseInt(doors) - 1, info.getFacing(), offset);
			}, 16L);
			
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				blink(Integer.parseInt(doors), info);
				
	        }, new Long(Integer.parseInt(duration)*20-30) + 16L);
			
			Bukkit.getScheduler().runTaskLater(info.getTrainCarts(), () -> {
				place(info, 2, 1, 2, Material.PEARLESCENT_FROGLIGHT, Integer.parseInt(doors), info.getFacing(), offset);
				place(info, 2, 0, 1, Material.AIR, Integer.parseInt(doors) - 1, info.getFacing(), offset);
	        }, new Long(Integer.parseInt(duration)*20-2) + 16L);


		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		SignBuildOptions opt = SignBuildOptions.create().setPermission(Permission.BUILD_PSD).setName("PSD")
				.setTraincartsWIKIHelp("TrainCarts/Signs");

		opt.setDescription("tells a train to open/close the PSD doors on arrival/departure");
		return opt.handle(event.getPlayer());
	}

}

/**
 * This file is part of LWC (https://github.com/Hidendra/LWC)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.griefcraft.listeners;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.griefcraft.scripting.Module;
import com.griefcraft.scripting.Module.Result;
import com.griefcraft.scripting.ModuleLoader.Event;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ContainerBlock;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class LWCPlayerListener extends PlayerListener {

    /**
     * The plugin instance
     */
    private LWCPlugin plugin;

    public LWCPlayerListener(LWCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!LWC.ENABLED) {
            return;
        }

        Player player = event.getPlayer();
        Item item = event.getItemDrop();
        ItemStack itemStack = item.getItemStack();

        Result result = plugin.getLWC().getModuleLoader().dispatchEvent(Event.DROP_ITEM, player, item, itemStack);

        if (result == Result.CANCEL) {
            event.setCancelled(true);
        }
    }
    
    @Override
    public void onPlayerChat(PlayerChatEvent event) {
    	if(!plugin.getLWC().getConfiguration().getBoolean("core.filterunlock", true)) {
    		return;
    	}
    	
    	/**
    	 * We want to block messages starting with cunlock incase someone screws up /cunlock password.
    	 */
    	String message = event.getMessage();
    	
    	if(message.startsWith("cunlock")) {
    		event.setCancelled(true);
    	}
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!LWC.ENABLED) {
            return;
        }

        LWC lwc = plugin.getLWC();
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        Location location = clickedBlock.getLocation();
        
        CraftWorld craftWorld = (CraftWorld) clickedBlock.getWorld();
        Block block = new CraftBlock((CraftChunk) craftWorld.getChunkAt(location), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        block.setTypeId(craftWorld.getBlockTypeIdAt(location));

        Material material = block.getType();

        /*
           * Prevent players with lwc.blockinventories from opening inventories
           */
        if (block.getState() instanceof ContainerBlock) {
            if (!lwc.hasPermission(player, "lwc.protect") && lwc.hasPermission(player, "lwc.deny") && !lwc.isAdmin(player) && !lwc.isMod(player)) {
                lwc.sendLocale(player, "protection.interact.error.blocked");
                event.setCancelled(true);
                return;
            }
        }

        List<String> actions = lwc.getMemoryDatabase().getActions(player.getName());
        Protection protection = lwc.findProtection(block);
        Module.Result result = Module.Result.CANCEL;
        boolean canAccess = lwc.canAccessProtection(player, protection);
        boolean canAdmin = lwc.canAdminProtection(player, protection);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            boolean ignoreLeftClick = Boolean.parseBoolean(lwc.resolveProtectionConfiguration(material, "ignoreLeftClick"));

            if (ignoreLeftClick) {
                return;
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            boolean ignoreRightClick = Boolean.parseBoolean(lwc.resolveProtectionConfiguration(material, "ignoreRightClick"));

            if (ignoreRightClick) {
                return;
            }
        }

        if (protection != null) {
            result = lwc.getModuleLoader().dispatchEvent(Event.INTERACT_PROTECTION, player, protection, actions, canAccess, canAdmin);
        } else {
            result = lwc.getModuleLoader().dispatchEvent(Event.INTERACT_BLOCK, player, block, actions);
        }

        if (result == Module.Result.ALLOW) {
            return;
        }

        if (result == Module.Result.DEFAULT) {
            lwc.enforceAccess(player, block);
        }

        if (!canAccess || result == Module.Result.CANCEL) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!LWC.ENABLED) {
            return;
        }

        LWC lwc = plugin.getLWC();
        String player = event.getPlayer().getName();

        lwc.getMemoryDatabase().unregisterPlayer(player);
        lwc.getMemoryDatabase().unregisterUnlock(player);
        lwc.getMemoryDatabase().unregisterPendingLock(player);
        lwc.getMemoryDatabase().unregisterAllActions(player);
        lwc.getMemoryDatabase().unregisterAllModes(player);
    }

}

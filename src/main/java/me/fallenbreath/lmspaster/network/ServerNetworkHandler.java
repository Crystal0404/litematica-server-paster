/*
 * This file is part of the Litematica Server Paster project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
 *
 * Litematica Server Paster is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Litematica Server Paster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Litematica Server Paster.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.fallenbreath.lmspaster.network;

import me.fallenbreath.lmspaster.LitematicaServerPasterMod;
import me.fallenbreath.lmspaster.mixins.ServerPlayNetworkHandlerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

public class ServerNetworkHandler
{
	private static final Map<ServerPlayNetworkHandler, StringBuilder> VERY_LONG_CHATS = new WeakHashMap<>();

	private static Optional<StringBuilder> getVeryLongChatBuilder(ServerPlayerEntity player)
	{
		return Optional.ofNullable(VERY_LONG_CHATS.get(player.networkHandler));
	}

	public static void handleClientPacket(LmsPasterPayload payload, ServerPlayerEntity player)
	{
		String playerName = player.getName().getString();
		int id = payload.getPacketId();
		CompoundTag nbt = payload.getNbt();
		switch (id)
		{
			case Network.C2S.HI:
				String clientModVersion = nbt.getString("mod_version");
				LitematicaServerPasterMod.LOGGER.info("Player {} connected with {} @ {}", playerName, LitematicaServerPasterMod.MOD_NAME, clientModVersion);
				player.networkHandler.sendPacket(Network.S2C.packet(Network.S2C.HI, nbt2 -> {
					nbt2.putString("mod_version", LitematicaServerPasterMod.VERSION);
				}));
				player.networkHandler.sendPacket(Network.S2C.packet(Network.S2C.ACCEPT_PACKETS, nbt2 -> {
					nbt2.putIntArray("ids", Network.C2S.ALL_PACKET_IDS);
				}));
				break;

			case Network.C2S.CHAT:
				LitematicaServerPasterMod.LOGGER.debug("Received chat from player {}", playerName);
				String message = nbt.getString("chat");
				triggerCommand(player, playerName, message);
				break;

			case Network.C2S.VERY_LONG_CHAT_START:
				LitematicaServerPasterMod.LOGGER.debug("Received VERY_LONG_CHAT_START from player {}", playerName);
				VERY_LONG_CHATS.put(player.networkHandler, new StringBuilder());
				break;

			case Network.C2S.VERY_LONG_CHAT_CONTENT:
				String segment = nbt.getString("segment");
				LitematicaServerPasterMod.LOGGER.debug("Received VERY_LONG_CHAT_CONTENT from player {} with length {}", playerName, segment.length());
				getVeryLongChatBuilder(player).ifPresent(builder -> builder.append(segment));
				break;

			case Network.C2S.VERY_LONG_CHAT_END:
				LitematicaServerPasterMod.LOGGER.debug("Received VERY_LONG_CHAT_END from player {}", playerName);
				getVeryLongChatBuilder(player).ifPresent(builder ->triggerCommand(player, playerName, builder.toString()));
				VERY_LONG_CHATS.remove(player.networkHandler);
				break;
		}
	}

	private static void triggerCommand(ServerPlayerEntity player, String playerName, String command)
	{
		if (command.isEmpty())
		{
			LitematicaServerPasterMod.LOGGER.warn("Player {} sent an empty command", playerName);
		}
		else
		{
			LitematicaServerPasterMod.LOGGER.debug("Player {} is sending a command with length {}", playerName, command.length());
			Objects.requireNonNull(player.getServer()).execute(
					//#if MC >= 11900
					//$$ () -> player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command)
					//#else
					() -> ((ServerPlayNetworkHandlerAccessor)player.networkHandler).invokeExecuteCommand(command)
					//#endif
			);
		}
	}
}

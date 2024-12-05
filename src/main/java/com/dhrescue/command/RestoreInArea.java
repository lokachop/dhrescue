package com.dhrescue.command;

import com.dhrescue.networking.SetBlockPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class RestoreInArea {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("dhrescue_restorearea")
                    .then(argument("minXIn", IntegerArgumentType.integer())
                    .then(argument("minYIn", IntegerArgumentType.integer())
                    .then(argument("minZIn", IntegerArgumentType.integer())

                    .then(argument("maxXIn", IntegerArgumentType.integer())
                    .then(argument("maxYIn", IntegerArgumentType.integer())
                    .then(argument("maxZIn", IntegerArgumentType.integer())
                    .executes(context -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null)
                        {
                            System.out.println("No MC");
                            return 0;
                        }

                        ClientPlayerEntity player = mc.player;
                        if (player == null)
                        {
                            System.out.println("No Client");
                            return 0;
                        }

                        if (!DhApi.Delayed.worldProxy.worldLoaded())
                        {
                            System.out.println("No World loaded");
                            return 0;
                        }
                        IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                        if (levelWrapper == null)
                        {
                            System.out.println("No Level Wrapper");
                            return 0;
                        }

                        int minXIn = IntegerArgumentType.getInteger(context, "minXIn");
                        int minYIn = IntegerArgumentType.getInteger(context, "minYIn");
                        int minZIn = IntegerArgumentType.getInteger(context, "minZIn");

                        int maxXIn = IntegerArgumentType.getInteger(context, "maxXIn");
                        int maxYIn = IntegerArgumentType.getInteger(context, "maxYIn");
                        int maxZIn = IntegerArgumentType.getInteger(context, "maxZIn");

                        int minX = Math.min(minXIn, maxXIn);
                        int minY = Math.min(minYIn, maxYIn);
                        int minZ = Math.min(minZIn, maxZIn);

                        int maxX = Math.max(minXIn, maxXIn);
                        int maxY = Math.max(minYIn, maxYIn);
                        int maxZ = Math.max(minZIn, maxZIn);

                        String message = "(" + minX + ", " + minY + ", " + minZ + ")" + "(" + maxX + ", " + maxY + ", " + maxZ + ")";
                        player.sendMessage(Text.of(message), false);

                        IDhApiTerrainDataCache terrainCache = DhApi.Delayed.terrainRepo.getSoftCache();

                        for (int x = minX; x < maxX; x++) {
                            for (int y = minY; y < maxY; y++) {
                                for (int z = minZ; z < maxZ; z++) {
                                    DhApiResult<DhApiTerrainDataPoint> queryResult = DhApi.Delayed.terrainRepo.getSingleDataPointAtBlockPos(levelWrapper, x, y, z, terrainCache);

                                    BlockPos currBlockPos = new BlockPos(x, y, z - 1);
                                    if(queryResult.payload == null) {
                                        continue;
                                    }

                                    String bID = queryResult.payload.blockStateWrapper.getSerialString();
                                    ClientPlayNetworking.send(new SetBlockPayload(currBlockPos, bID));
                                }
                            }
                        }

                        player.sendMessage(Text.of("Executed correctly!"), false);
                        return 1;
            }))))))));
        });
    }
}

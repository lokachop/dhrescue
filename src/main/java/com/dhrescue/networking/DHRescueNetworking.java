package com.dhrescue.networking;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DHRescueNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger("dhrescue_network");
    public static final Identifier SET_BLOCK_PACKET_ID = Identifier.of("dhrescue", "set_block");

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SetBlockPayload.ID, SetBlockPayload.CODEC);
    }


    public static void register_SV()  {
        LOGGER.info("SV registered!");


        // can you please work 🙏🙏🙏🙏
        ServerPlayNetworking.registerGlobalReceiver(SetBlockPayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            server.execute(() -> {
                BlockPos thePos = payload.blockPos();
                String theID = payload.blockID();

                IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                if (levelWrapper == null)
                {
                    return;
                }

                try {
                    IDhApiBlockStateWrapper blockStateDh = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(theID, levelWrapper);
                    BlockState bState = (BlockState)blockStateDh.getWrappedMcObject();

                    // now set the blocks on the world
                    server.getOverworld().setBlockState(thePos, bState);
                } catch(Exception e) {
                    // /dhrescue_restorearea 1684 63 -708 1654 107 -771

                    BlockState bState = Blocks.AIR.getDefaultState();
                    server.getOverworld().setBlockState(thePos, bState);
                }


            });
        });
    }
}

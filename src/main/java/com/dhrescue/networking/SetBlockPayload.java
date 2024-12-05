package com.dhrescue.networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record SetBlockPayload(BlockPos blockPos, String blockID) implements CustomPayload {
    public static final CustomPayload.Id<SetBlockPayload> ID = new CustomPayload.Id<>(DHRescueNetworking.SET_BLOCK_PACKET_ID);

    // should you need to send more data, add the appropriate record parameters and change your codec
    public static final PacketCodec<RegistryByteBuf, SetBlockPayload> CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, SetBlockPayload::blockPos,
        PacketCodecs.STRING, SetBlockPayload::blockID,
        SetBlockPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

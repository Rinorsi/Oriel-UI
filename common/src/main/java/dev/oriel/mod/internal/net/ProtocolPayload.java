package dev.oriel.mod.internal.net;


import dev.oriel.mod.internal.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A version-specific negotiated capability. Never sent: incompatible codecs are rejected before any page packet. */
public record ProtocolPayload() implements CustomPacketPayload {
    public static final Type<ProtocolPayload> TYPE = new Type<>(Constants.id("protocol/" + OrielNetwork.protocolVersion()));
    public static final StreamCodec<ByteBuf, ProtocolPayload> STREAM_CODEC = StreamCodec.unit(new ProtocolPayload());

    @Override public Type<ProtocolPayload> type() { return TYPE; }
}

package emu.grasscutter.net.packet;

import com.google.protobuf.GeneratedMessageV3;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.utils.Crypto;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class BasePacket {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int const1 = 17767; // 0x4567
    private static final int const2 = -30293; // 0x89ab

    private int opcode;
    private boolean shouldBuildHeader = false;

    private byte[] header;
    private byte[] data;

    // Encryption
    private boolean useDispatchKey;
    public boolean shouldEncrypt = true;

    public BasePacket(int opcode) {
        this.opcode = opcode;
    }

    public BasePacket(int opcode, int clientSequence) {
        this.opcode = opcode;
        this.buildHeader(clientSequence);
    }

    public BasePacket(int opcode, boolean buildHeader) {
        this.opcode = opcode;
        this.shouldBuildHeader = buildHeader;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public boolean useDispatchKey() {
        return useDispatchKey;
    }

    public void setUseDispatchKey(boolean useDispatchKey) {
        this.useDispatchKey = useDispatchKey;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    public boolean shouldBuildHeader() {
        return shouldBuildHeader;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setData(GeneratedMessageV3 proto) {
        this.data = proto.toByteArray();
    }

    @SuppressWarnings("rawtypes")
    public void setData(GeneratedMessageV3.Builder proto) {
        this.data = proto.build().toByteArray();
    }

    public BasePacket buildHeader(int clientSequence) {
        if (this.getHeader() != null && clientSequence == 0) {
            return this;
        }
        setHeader(PacketHead.newBuilder().setClientSequenceId(clientSequence).setSentMs(System.currentTimeMillis()).build().toByteArray());
        return this;
    }

    public ByteBuf build() {
        byte[] header = this.header;
        if (header == null) {
            this.header = header = EMPTY_BYTES;
        }
        byte[] data = this.data;
        if (data == null) {
            this.data = data = EMPTY_BYTES;
        }
        int size = 2 + 2 + 2 + 4 + header.length + data.length + 2;
        ByteBuf buf = ByteBufAllocator.DEFAULT.ioBuffer(size);
        buf.writeShort(const1);
        buf.writeShort(opcode);
        buf.writeShort(header.length);
        buf.writeInt(data.length);
        buf.writeBytes(header);
        buf.writeBytes(data);
        buf.writeShort(const2);

        if (this.shouldEncrypt) {
            Crypto.xor(buf, this.useDispatchKey() ? Crypto.DISPATCH_KEY : Crypto.ENCRYPT_KEY);
        }

        return buf;
    }
}

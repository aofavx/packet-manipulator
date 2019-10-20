/*_##########################################################################
  _##
  _##  Copyright (C) 2011-2015  Pcap4J.org
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

import android.annotation.SuppressLint;
import android.graphics.Color;

import static org.pcap4j.util.ByteArrays.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.pcap4j.packet.namednumber.ArpHardwareType;
import org.pcap4j.packet.namednumber.ArpOperation;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.util.ByteArrays;
import org.pcap4j.util.MacAddress;

import me.itay.packetmanipulator.display.PacketDissector;
import me.itay.packetmanipulator.display.PacketEntry;

/**
 * @author Kaito Yamada
 * @since pcap4j 0.9.1
 */
public final class ArpPacket extends AbstractPacket implements PacketDissector {

    /** */
    private static final long serialVersionUID = -7754807127571498700L;

    private final ArpHeader header;

    /**
     * A static factory method. This method validates the arguments by {@link
     * ByteArrays#validateBounds(byte[], int, int)}, which may throw exceptions undocumented here.
     *
     * @param rawData rawData
     * @param offset offset
     * @param length length
     * @return a new ArpPacket object.
     * @throws IllegalRawDataException if parsing the raw data fails.
     */
    public static ArpPacket newPacket(byte[] rawData, int offset, int length)
            throws IllegalRawDataException {
        ByteArrays.validateBounds(rawData, offset, length);
        return new ArpPacket(rawData, offset, length);
    }

    private ArpPacket(byte[] rawData, int offset, int length) throws IllegalRawDataException {
        this.header = new ArpHeader(rawData, offset, length);
    }

    private ArpPacket(Builder builder) {
        if (builder == null
                || builder.hardwareType == null
                || builder.protocolType == null
                || builder.operation == null
                || builder.srcHardwareAddr == null
                || builder.srcProtocolAddr == null
                || builder.dstHardwareAddr == null
                || builder.dstProtocolAddr == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("builder: ")
                    .append(builder)
                    .append(" builder.hardwareType: ")
                    .append(builder.hardwareType)
                    .append(" builder.protocolType: ")
                    .append(builder.protocolType)
                    .append(" builder.operation: ")
                    .append(builder.operation)
                    .append(" builder.srcHardwareAddr: ")
                    .append(builder.srcHardwareAddr)
                    .append(" builder.srcProtocolAddr: ")
                    .append(builder.srcProtocolAddr)
                    .append(" builder.dstHardwareAddr: ")
                    .append(builder.dstHardwareAddr)
                    .append(" builder.dstProtocolAddr: ")
                    .append(builder.dstProtocolAddr);
            throw new NullPointerException(sb.toString());
        }

        this.header = new ArpHeader(builder);
    }

    @Override
    public ArpHeader getHeader() {
        return header;
    }

    @Override
    public Builder getBuilder() {
        return new Builder(this);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public boolean dissect(PacketEntry entry) {
        ArpHeader header = getHeader();

        // set the protocol
        if(header.getOperation() == ArpOperation.REQUEST_REVERSE ||
           header.getOperation() == ArpOperation.REPLY_REVERSE) {
            entry.protocol = "RARP";
        } else if(header.getOperation() == ArpOperation.DRARP_ERROR ||
                  header.getOperation() == ArpOperation.DRARP_REPLY ||
                  header.getOperation() == ArpOperation.DRARP_REQUEST) {
            entry.protocol = "DRARP";
        } else if(header.getOperation() == ArpOperation.INARP_REQUEST ||
                  header.getOperation() == ArpOperation.INARP_REPLY) {
            entry.protocol = "Inverse ARP";
        } else if(header.getOperation() == ArpOperation.MARS_GROUPLIST_REPLY ||
                header.getOperation() == ArpOperation.MARS_GROUPLIST_REQUEST ||
                header.getOperation() == ArpOperation.MARS_JOIN ||
                header.getOperation() == ArpOperation.MARS_LEAVE ||
                header.getOperation() == ArpOperation.MARS_MSERV ||
                header.getOperation() == ArpOperation.MARS_UNSERV ||
                header.getOperation() == ArpOperation.MARS_SJOIN ||
                header.getOperation() == ArpOperation.MARS_SLEAVE ||
                header.getOperation() == ArpOperation.MARS_NAK ||
                header.getOperation() == ArpOperation.MARS_MULTI ||
                header.getOperation() == ArpOperation.MARS_REDIRECT_MAP ||
                header.getOperation() == ArpOperation.MARS_REQUEST) {
            entry.protocol = "MARS";
        } else if(header.getOperation() == ArpOperation.MAPOS_UNARP) {
            entry.protocol = "MAPOS";
        }else {
            entry.protocol = "ARP";
        }

        boolean is_gratuitous = false;
        boolean is_announcement = false;
        boolean is_probe = false;

        if((header.getOperation() == ArpOperation.REQUEST || header.getOperation() == ArpOperation.REPLY) &&
            header.getSrcProtocolAddr().equals(header.getDstProtocolAddr())) {
            is_gratuitous = true;

            if(header.getOperation() == ArpOperation.REQUEST &&
               header.getDstHardwareAddr().equals(MacAddress.getByAddress(new byte[6]))) {
                is_announcement = true;
            }
        }else {
            if(header.getOperation() == ArpOperation.REQUEST &&
               header.getDstHardwareAddr().equals(MacAddress.getByAddress(new byte[6])) &&
               Arrays.equals(header.getSrcProtocolAddr().getAddress(), new byte[header.getProtocolAddrLengthAsInt()])) {
                is_probe = true;
            }
        }

        if(header.getOperation() == ArpOperation.REQUEST) {
            if(is_gratuitous) {
                if(is_announcement) {
                    entry.info = String.format("ARP Announcement for %s",
                            header.getDstProtocolAddr().getHostAddress());
                }else {
                    entry.info = String.format("Gratuitous ARP for %s (Request)",
                            header.getDstProtocolAddr().getHostAddress());
                }
            }else if(is_probe) {
                entry.info = String.format("Who has %s? (ARP Prove)",
                        header.getSrcProtocolAddr().getHostAddress());
            }else {
                entry.info = String.format("Who has %s? Tell %s",
                        header.getSrcProtocolAddr().getHostAddress(),
                        header.getDstProtocolAddr().getHostAddress());
            }

        } else if(header.getOperation() == ArpOperation.REPLY) {
            if (is_gratuitous) {
                entry.info = String.format("Gratuitous ARP for %s (Reply)",
                        header.getSrcProtocolAddr().getHostAddress());
            } else {
                entry.info = String.format("%s is at %s",
                        header.getSrcProtocolAddr().getHostAddress(),
                        header.getSrcHardwareAddr());
            }

        } else if(header.getOperation() == ArpOperation.REQUEST_REVERSE ||
                  header.getOperation() == ArpOperation.INARP_REQUEST ||
                  header.getOperation() == ArpOperation.DRARP_REQUEST) {

            entry.info = String.format("Who is %s? Tell %s",
                    header.getDstHardwareAddr(),
                    header.getSrcHardwareAddr());

        } else if(header.getOperation() == ArpOperation.REPLY_REVERSE ||
                  header.getOperation() == ArpOperation.DRARP_REPLY) {

            entry.info = String.format("%s is at %s",
                    header.getDstHardwareAddr(),
                    header.getDstProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.DRARP_ERROR) {
            entry.info = "DRARP Error";

        } else if(header.getOperation() == ArpOperation.INARP_REPLY) {
            entry.info = String.format("%s is at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.ARP_NAK) {
            entry.info = "ARP NAK";

        } else if(header.getOperation() == ArpOperation.MARS_REQUEST) {
            entry.info = String.format("MARS request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_MULTI) {
            entry.info = String.format("MARS MULTI request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_MSERV) {
            entry.info = String.format("MARS MSERV request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_JOIN) {
            entry.info = String.format("MARS JOIN request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_LEAVE) {
            entry.info = String.format("MARS LEAVE from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_NAK) {
            entry.info = String.format("MARS NAK from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_UNSERV) {
            entry.info = String.format("MARS UNSERV request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_SJOIN) {
            entry.info = String.format("MARS SJOIN request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_SLEAVE) {
            entry.info = String.format("MARS SLEAVE from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_GROUPLIST_REQUEST) {
            entry.info = String.format("MARS grouplist request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_GROUPLIST_REPLY) {
            entry.info = String.format("MARS grouplist reply from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MARS_REDIRECT_MAP) {
            entry.info = String.format("MARS redirect map from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.MAPOS_UNARP) {
            entry.info = String.format("MAPOS UNARP request from %s at %s",
                    header.getSrcHardwareAddr(),
                    header.getSrcProtocolAddr().getHostAddress());

        } else if(header.getOperation() == ArpOperation.OP_EXP1) {
            entry.info = String.format("Experimental 1 ( opcode %d )", header.getOperation().value());

        } else if(header.getOperation() == ArpOperation.OP_EXP2) {
            entry.info = String.format("Experimental 2 ( opcode %d )", header.getOperation().value());

        } else if(header.getOperation().value() == 0 || header.getOperation().value() == 65535) {
            entry.info = String.format("Reserved opcode %d", header.getOperation().value());

        }else {
            entry.info = String.format("Unknown ARP opcode 0x%04x", header.getOperation().value());
        }

        return false;
    }

    /** @author Kaito Yamada */
    public static final class Builder extends AbstractBuilder {

        private ArpHardwareType hardwareType;
        private EtherType protocolType;
        private byte hardwareAddrLength;
        private byte protocolAddrLength;
        private ArpOperation operation;
        private MacAddress srcHardwareAddr;
        private InetAddress srcProtocolAddr;
        private MacAddress dstHardwareAddr;
        private InetAddress dstProtocolAddr;

        /** */
        public Builder() {}

        private Builder(ArpPacket packet) {
            this.hardwareType = packet.header.hardwareType;
            this.protocolType = packet.header.protocolType;
            this.hardwareAddrLength = packet.header.hardwareAddrLength;
            this.protocolAddrLength = packet.header.protocolAddrLength;
            this.operation = packet.header.operation;
            this.srcHardwareAddr = packet.header.srcHardwareAddr;
            this.srcProtocolAddr = packet.header.srcProtocolAddr;
            this.dstHardwareAddr = packet.header.dstHardwareAddr;
            this.dstProtocolAddr = packet.header.dstProtocolAddr;
        }

        /**
         * @param hardwareType hardwareType
         * @return this Builder object for method chaining.
         */
        public Builder hardwareType(ArpHardwareType hardwareType) {
            this.hardwareType = hardwareType;
            return this;
        }

        /**
         * @param protocolType protocolType
         * @return this Builder object for method chaining.
         */
        public Builder protocolType(EtherType protocolType) {
            this.protocolType = protocolType;
            return this;
        }

        /**
         * @param hardwareAddrLength hardwareAddrLength
         * @return this Builder object for method chaining.
         */
        public Builder hardwareAddrLength(byte hardwareAddrLength) {
            this.hardwareAddrLength = hardwareAddrLength;
            return this;
        }

        /**
         * @param protocolAddrLength protocolAddrLength
         * @return this Builder object for method chaining.
         */
        public Builder protocolAddrLength(byte protocolAddrLength) {
            this.protocolAddrLength = protocolAddrLength;
            return this;
        }

        /**
         * @param operation operation
         * @return this Builder object for method chaining.
         */
        public Builder operation(ArpOperation operation) {
            this.operation = operation;
            return this;
        }

        /**
         * @param srcHardwareAddr srcHardwareAddr
         * @return this Builder object for method chaining.
         */
        public Builder srcHardwareAddr(MacAddress srcHardwareAddr) {
            this.srcHardwareAddr = srcHardwareAddr;
            return this;
        }

        /**
         * @param srcProtocolAddr srcProtocolAddr
         * @return this Builder object for method chaining.
         */
        public Builder srcProtocolAddr(InetAddress srcProtocolAddr) {
            this.srcProtocolAddr = srcProtocolAddr;
            return this;
        }

        /**
         * @param dstHardwareAddr dstHardwareAddr
         * @return this Builder object for method chaining.
         */
        public Builder dstHardwareAddr(MacAddress dstHardwareAddr) {
            this.dstHardwareAddr = dstHardwareAddr;
            return this;
        }

        /**
         * @param dstProtocolAddr dstProtocolAddr
         * @return this Builder object for method chaining.
         */
        public Builder dstProtocolAddr(InetAddress dstProtocolAddr) {
            this.dstProtocolAddr = dstProtocolAddr;
            return this;
        }

        @Override
        public ArpPacket build() {
            return new ArpPacket(this);
        }
    }

    /**
     * @author Kaito Yamada
     * @version pcap4j 0.9.1
     */
    public static final class ArpHeader extends AbstractHeader {

        /*
         *  0                            15
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |         Hardware Type         |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |         Protocol Type         |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |  HW Addr Len  |Proto Addr Len |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |         Operation             |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |    Src Hardware Address       |
         * +                               +
         * |                               |
         * +                               +
         * |                               |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |    Src Protocol Address       |
         * +                               |
         * |                               |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |    Dst Hardware Address       |
         * +                               +
         * |                               |
         * +                               +
         * |                               |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |    Dst Protocol Address       |
         * +                               |
         * |                               |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        /** */
        private static final long serialVersionUID = -6744946002881067732L;

        private static final int HARDWARE_TYPE_OFFSET = 0;
        private static final int HARDWARE_TYPE_SIZE = SHORT_SIZE_IN_BYTES;
        private static final int PROTOCOL_TYPE_OFFSET = HARDWARE_TYPE_OFFSET + HARDWARE_TYPE_SIZE;
        private static final int PROTOCOL_TYPE_SIZE = SHORT_SIZE_IN_BYTES;
        private static final int HW_ADDR_LENGTH_OFFSET = PROTOCOL_TYPE_OFFSET + PROTOCOL_TYPE_SIZE;
        private static final int HW_ADDR_LENGTH_SIZE = BYTE_SIZE_IN_BYTES;
        private static final int PROTO_ADDR_LENGTH_OFFSET = HW_ADDR_LENGTH_OFFSET + HW_ADDR_LENGTH_SIZE;
        private static final int PROTO_ADDR_LENGTH_SIZE = BYTE_SIZE_IN_BYTES;
        private static final int OPERATION_OFFSET = PROTO_ADDR_LENGTH_OFFSET + PROTO_ADDR_LENGTH_SIZE;
        private static final int OPERATION_SIZE = SHORT_SIZE_IN_BYTES;
        private static final int SRC_HARDWARE_ADDR_OFFSET = OPERATION_OFFSET + OPERATION_SIZE;
        private static final int SRC_HARDWARE_ADDR_SIZE = MacAddress.SIZE_IN_BYTES;
        private static final int SRC_PROTOCOL_ADDR_OFFSET =
                SRC_HARDWARE_ADDR_OFFSET + SRC_HARDWARE_ADDR_SIZE;
        private static final int SRC_PROTOCOL_ADDR_SIZE = INET4_ADDRESS_SIZE_IN_BYTES;
        private static final int DST_HARDWARE_ADDR_OFFSET =
                SRC_PROTOCOL_ADDR_OFFSET + SRC_PROTOCOL_ADDR_SIZE;
        private static final int DST_HARDWARE_ADDR_SIZE = MacAddress.SIZE_IN_BYTES;
        private static final int DST_PROTOCOL_ADDR_OFFSET =
                DST_HARDWARE_ADDR_OFFSET + DST_HARDWARE_ADDR_SIZE;
        private static final int DST_PROTOCOL_ADDR_SIZE = INET4_ADDRESS_SIZE_IN_BYTES;
        private static final int ARP_HEADER_SIZE = DST_PROTOCOL_ADDR_OFFSET + DST_PROTOCOL_ADDR_SIZE;

        private final ArpHardwareType hardwareType;
        private final EtherType protocolType;
        private final byte hardwareAddrLength;
        private final byte protocolAddrLength;
        private final ArpOperation operation;
        private final MacAddress srcHardwareAddr;
        private final InetAddress srcProtocolAddr;
        private final MacAddress dstHardwareAddr;
        private final InetAddress dstProtocolAddr;

        private ArpHeader(byte[] rawData, int offset, int length) throws IllegalRawDataException {
            if (length < ARP_HEADER_SIZE) {
                StringBuilder sb = new StringBuilder(200);
                sb.append("The data is too short to build an ARP header(")
                        .append(ARP_HEADER_SIZE)
                        .append(" bytes). data: ")
                        .append(ByteArrays.toHexString(rawData, " "))
                        .append(", offset: ")
                        .append(offset)
                        .append(", length: ")
                        .append(length);
                throw new IllegalRawDataException(sb.toString());
            }

            this.hardwareType =
                    ArpHardwareType.getInstance(ByteArrays.getShort(rawData, HARDWARE_TYPE_OFFSET + offset));
            this.protocolType =
                    EtherType.getInstance(ByteArrays.getShort(rawData, PROTOCOL_TYPE_OFFSET + offset));
            this.hardwareAddrLength = ByteArrays.getByte(rawData, HW_ADDR_LENGTH_OFFSET + offset);
            this.protocolAddrLength = ByteArrays.getByte(rawData, PROTO_ADDR_LENGTH_OFFSET + offset);
            this.operation =
                    ArpOperation.getInstance(ByteArrays.getShort(rawData, OPERATION_OFFSET + offset));
            this.srcHardwareAddr = ByteArrays.getMacAddress(rawData, SRC_HARDWARE_ADDR_OFFSET + offset);
            this.srcProtocolAddr = ByteArrays.getInet4Address(rawData, SRC_PROTOCOL_ADDR_OFFSET + offset);
            this.dstHardwareAddr = ByteArrays.getMacAddress(rawData, DST_HARDWARE_ADDR_OFFSET + offset);
            this.dstProtocolAddr = ByteArrays.getInet4Address(rawData, DST_PROTOCOL_ADDR_OFFSET + offset);
        }

        private ArpHeader(Builder builder) {
            this.hardwareType = builder.hardwareType;
            this.protocolType = builder.protocolType;
            this.hardwareAddrLength = builder.hardwareAddrLength;
            this.protocolAddrLength = builder.protocolAddrLength;
            this.operation = builder.operation;
            this.srcHardwareAddr = builder.srcHardwareAddr;
            this.srcProtocolAddr = builder.srcProtocolAddr;
            this.dstHardwareAddr = builder.dstHardwareAddr;
            this.dstProtocolAddr = builder.dstProtocolAddr;
        }

        /** @return hardwareType */
        public ArpHardwareType getHardwareType() {
            return hardwareType;
        }

        /** @return protocolType */
        public EtherType getProtocolType() {
            return protocolType;
        }

        /** @return hardwareAddrLength */
        public byte getHardwareAddrLength() {
            return hardwareAddrLength;
        }

        /** @return hardwareAddrLength */
        public int getHardwareAddrLengthAsInt() {
            return 0xFF & hardwareAddrLength;
        }

        /** @return protocolAddrLength */
        public byte getProtocolAddrLength() {
            return protocolAddrLength;
        }

        /** @return protocolAddrLength */
        public int getProtocolAddrLengthAsInt() {
            return 0xFF & protocolAddrLength;
        }

        /** @return operation */
        public ArpOperation getOperation() {
            return operation;
        }

        /** @return srcHardwareAddr */
        public MacAddress getSrcHardwareAddr() {
            return srcHardwareAddr;
        }

        /** @return srcProtocolAddr */
        public InetAddress getSrcProtocolAddr() {
            return srcProtocolAddr;
        }

        /** @return dstHardwareAddr */
        public MacAddress getDstHardwareAddr() {
            return dstHardwareAddr;
        }

        /** @return dstProtocolAddr */
        public InetAddress getDstProtocolAddr() {
            return dstProtocolAddr;
        }

        @Override
        protected List<byte[]> getRawFields() {
            List<byte[]> rawFields = new ArrayList<byte[]>();
            rawFields.add(ByteArrays.toByteArray(hardwareType.value()));
            rawFields.add(ByteArrays.toByteArray(protocolType.value()));
            rawFields.add(ByteArrays.toByteArray(hardwareAddrLength));
            rawFields.add(ByteArrays.toByteArray(protocolAddrLength));
            rawFields.add(ByteArrays.toByteArray(operation.value()));
            rawFields.add(ByteArrays.toByteArray(srcHardwareAddr));
            rawFields.add(ByteArrays.toByteArray(srcProtocolAddr));
            rawFields.add(ByteArrays.toByteArray(dstHardwareAddr));
            rawFields.add(ByteArrays.toByteArray(dstProtocolAddr));
            return rawFields;
        }

        @Override
        public int length() {
            return ARP_HEADER_SIZE;
        }

        @Override
        protected String buildString() {
            StringBuilder sb = new StringBuilder();
            String ls = System.getProperty("line.separator");

            sb.append("[ARP Header (").append(length()).append(" bytes)]").append(ls);
            sb.append("  Hardware type: ").append(hardwareType).append(ls);
            sb.append("  Protocol type: ").append(protocolType).append(ls);
            sb.append("  Hardware address length: ")
                    .append(getHardwareAddrLengthAsInt())
                    .append(" [bytes]")
                    .append(ls);
            sb.append("  Protocol address length: ")
                    .append(getProtocolAddrLengthAsInt())
                    .append(" [bytes]")
                    .append(ls);
            sb.append("  Operation: ").append(operation).append(ls);
            sb.append("  Source hardware address: ").append(srcHardwareAddr).append(ls);
            sb.append("  Source protocol address: ").append(srcProtocolAddr).append(ls);
            sb.append("  Destination hardware address: ").append(dstHardwareAddr).append(ls);
            sb.append("  Destination protocol address: ").append(dstProtocolAddr).append(ls);

            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!this.getClass().isInstance(obj)) {
                return false;
            }

            ArpHeader other = (ArpHeader) obj;
            return operation.equals(other.getOperation())
                    && srcHardwareAddr.equals(other.srcHardwareAddr)
                    && srcProtocolAddr.equals(other.srcProtocolAddr)
                    && dstHardwareAddr.equals(other.dstHardwareAddr)
                    && dstProtocolAddr.equals(other.dstProtocolAddr)
                    && hardwareType.equals(other.hardwareType)
                    && protocolType.equals(other.protocolType)
                    && hardwareAddrLength == other.hardwareAddrLength
                    && protocolAddrLength == other.protocolAddrLength;
        }

        @Override
        protected int calcHashCode() {
            int result = 17;
            result = 31 * result + hardwareType.hashCode();
            result = 31 * result + protocolType.hashCode();
            result = 31 * result + hardwareAddrLength;
            result = 31 * result + protocolAddrLength;
            result = 31 * result + operation.hashCode();
            result = 31 * result + srcHardwareAddr.hashCode();
            result = 31 * result + srcProtocolAddr.hashCode();
            result = 31 * result + dstHardwareAddr.hashCode();
            result = 31 * result + dstProtocolAddr.hashCode();
            return result;
        }
    }
}

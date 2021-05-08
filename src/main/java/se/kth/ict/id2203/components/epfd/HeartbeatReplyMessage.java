package se.kth.ict.id2203.components.epfd;

import se.kth.ict.id2203.ports.pp2p.Pp2pDeliver;
import se.sics.kompics.address.Address;

public class HeartbeatReplyMessage extends Pp2pDeliver {

    private static final long serialVersionUID = -7678165393077733049L;
    private final int seqNum;

    public HeartbeatReplyMessage(Address source, int seqNum) {
        super(source);
        this.seqNum = seqNum;
    }

    public int getSeqNum() {
        return seqNum;
    }
}

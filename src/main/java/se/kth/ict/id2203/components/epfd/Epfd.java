package se.kth.ict.id2203.components.epfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.ict.id2203.ports.epfd.EventuallyPerfectFailureDetector;
import se.kth.ict.id2203.ports.epfd.Restore;
import se.kth.ict.id2203.ports.epfd.Suspect;
import se.kth.ict.id2203.ports.pp2p.PerfectPointToPointLink;
import se.kth.ict.id2203.ports.pp2p.Pp2pSend;
import se.sics.kompics.*;
import se.sics.kompics.address.Address;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

import java.util.HashSet;

public class Epfd extends ComponentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(Epfd.class);
    private int seqNum;
    private long delay;
    private final long deltaDelay;
    private final HashSet<Address> alive;
    private final HashSet<Address> suspected;
    private final HashSet<Address> allAddress;
    private final Address self;

    private final Positive<Timer> timer = requires(Timer.class);
    private final Negative<EventuallyPerfectFailureDetector> eventuallyPerfectFailureDetector = provides(EventuallyPerfectFailureDetector.class);
    private final Positive<PerfectPointToPointLink> perfectPointToPointLink = requires(PerfectPointToPointLink.class);

    private Handler<Start> startTimerHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            startTimer(delay);
        }
    };

    private Handler<CheckTimeout> checkTimeoutHandler = new Handler<CheckTimeout>() {
        @Override
        public void handle(CheckTimeout event) {
            for (Address address : alive) {
                if (suspected.contains(address)) {
                    delay += deltaDelay;
                    logger.info("Delay " + delay + " incremented by " + deltaDelay);
                    break;
                }
            }
            seqNum++;

            for (Address address : allAddress) {
                if ((!alive.contains(address)) && (!suspected.contains(address))) {
                    suspected.add(address);
                    trigger(new Suspect(address), eventuallyPerfectFailureDetector);
                } else if ((alive.contains(address)) && (suspected.contains(address))) {
                    suspected.remove(address);
                    trigger(new Restore(address), eventuallyPerfectFailureDetector);
                }
                trigger(new Pp2pSend(address, new HeartbeatRequestMessage(self, seqNum)), perfectPointToPointLink);
            }
            alive.clear();
            startTimer(delay);
        }
    };

    private Handler<HeartbeatRequestMessage> heartbeatRequestMessageHandler = new Handler<HeartbeatRequestMessage>() {
        @Override
        public void handle(HeartbeatRequestMessage event) {
            trigger(new Pp2pSend(event.getSource(), new HeartbeatReplyMessage(self, event.getSeqNum())), perfectPointToPointLink);
        }
    };

    private Handler<HeartbeatReplyMessage> heartbeatReplyMessageHandler = new Handler<HeartbeatReplyMessage>() {
        @Override
        public void handle(HeartbeatReplyMessage event) {
            if ((event.getSeqNum() == seqNum) || (suspected.contains(event.getSource()))) {
                alive.add(event.getSource());
            }
        }
    };

    public Epfd(EpfdInit init) {
        seqNum = 0;
        alive = new HashSet<>(init.getAllAddresses());
        suspected = new HashSet<>();
        delay = init.getInitialDelay();
        deltaDelay = init.getDeltaDelay();
        allAddress = new HashSet<>(init.getAllAddresses());
        self = init.getSelfAddress();

        subscribe(startTimerHandler, control);
        subscribe(checkTimeoutHandler, timer);
        subscribe(heartbeatRequestMessageHandler, perfectPointToPointLink);
        subscribe(heartbeatReplyMessageHandler, perfectPointToPointLink);
    }

    private void startTimer(long delay) {
        ScheduleTimeout timeout = new ScheduleTimeout(delay);
        timeout.setTimeoutEvent(new CheckTimeout(timeout));
        trigger(timeout, timer);
    }
}
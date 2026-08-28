package es.idynamicsax.ostris.core;
import java.util.concurrent.atomic.AtomicLong;
/** In-memory reference semantics; production allocation is the locked community row. */
public final class CommunitySequence {private final AtomicLong next;public CommunitySequence(long first){if(first<1)throw new ProtocolException("INVALID_SEQUENCE","Sequence starts at one or later");next=new AtomicLong(first);}public long allocate(){return next.getAndIncrement();}}

package es.idynamicsax.ostris.service;
import java.time.Instant; import java.util.UUID;
public record CommitReceipt(UUID transactionId,long communitySequence,String protocolDigest,Instant committedAt){}

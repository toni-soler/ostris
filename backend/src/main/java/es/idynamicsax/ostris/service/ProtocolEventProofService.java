package es.idynamicsax.ostris.service;
import java.util.UUID;
public interface ProtocolEventProofService { void record(UUID tenantId, UUID communityId, UUID transactionId, String protocolDigest); }

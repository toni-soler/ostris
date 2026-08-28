# ADR-014 — RESTITUTION reference and authorization paths

Status: Accepted

## Decision

RESTITUTION v0.1 has two exclusive paths without a redundant `restitutionMode` field. Voluntary restitution has no ResolutionBasis or GovernanceAuthorization and forbids `finalFindingId`/`disputeResolutionId` as mandate references. It uses Account Authorization.

Imposed restitution requires GovernanceAuthorization and exactly one typed equality-bound reference: FINAL_FINDING uses `finalFindingId`; FINAL_DISPUTE_RESOLUTION uses `disputeResolutionId`. The reference ID equals `resolutionBasis.id`; the alternative field is absent. Path, basis, references and entries are immutable signed proposal intent. Mixed evidence and replay across paths reject.

REVERSAL and SETTLEMENT do not overload a Finding reference: their economic references remain distinct from an optional adjudicated GovernanceAuthorization basis, so their current wire fields remain unchanged.

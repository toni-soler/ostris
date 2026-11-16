# Controller ownership on the public application surface

## Why this exists

osTRIS's public application surface (Discovery/Provisioning) lets an external
application create a community/unit, activate a participant, and manage the
credentials bound to a participant's controller ("add this browser as a
signer", "revoke that device"). Those credential operations are a **personal**
action: a credential bound to a controller can authorize that controller's
account at threshold evaluation, so whoever can add a credential to a
controller can sign for the account it controls.

### The fault (class of problem)

A `ProtocolController` originally recorded only its tenant and community. osTRIS
had **no record of which authenticated IDAX user a controller belongs to**. The
provisioning surface therefore authorised credential add/revoke on a
tenant-wide capability alone (`OSTRIS_CREDENTIAL_MANAGE`). Two properties made
that insufficient as the authoritative boundary:

- the capability is tenant-wide: every holder satisfies it for **every**
  controller in the tenant, not only their own;
- a platform superuser satisfies every capability unconditionally.

The only per-user ownership check lived in the **consuming application** (STIR's
device service always passes its own participant's controller id). Because the
public surface is reachable directly, a caller could bypass that application and
operate on another participant's controller. This is an authorisation-model
gap, not a cryptographic one: osTRIS still never signs on anyone's behalf and
never holds a private key — but it was letting the wrong authenticated user
attach *their own* key to someone else's controller.

> The step-by-step reproduction is kept in the consuming application's private
> security record while the gap is open in deployed runtimes; it is not
> published here.

## The fix (authoritative, in osTRIS)

Ownership is now a fact osTRIS itself records and enforces, at the authoritative
economic service — never only in a consumer or a proxy, and never waved through
for a capability holder or a superuser.

1. **The controller carries its owner.** `protocol_controller.owner_user_id`
   (with `owner_established_by` / `owner_established_sequence` provenance,
   all-or-nothing) is set when the participant is activated, to the
   authenticated caller who activated it. (`V5__controller_ownership.sql`.)

2. **Every credential mutation checks it.** `ProvisioningService.addCredential`
   and `revokeCredential` resolve the controller's recorded owner and require it
   to equal the authenticated caller. The check is independent of the permission
   layer and of superuser status — a tenant-wide capability, or being a
   platform superuser, is **not** authority over another person's personal
   controller. Denials are `403 CONTROLLER_NOT_OWNED`.

3. **No ambiguous migration.** osTRIS never recorded who created a pre-existing
   controller, and a pre-existing binding could itself be a product of the gap.
   There is therefore **no automatic backfill**: pre-existing controllers keep
   `owner_user_id = NULL` and are **fail-closed** for credential management
   (`403 CONTROLLER_OWNERSHIP_UNASSIGNED`). Re-establishing ownership of a
   pre-existing controller is a deliberate out-of-band operator action, not
   something driven by whoever asks first. A governed re-attestation contract
   for pre-existing controllers is a documented open question, deliberately not
   improvised here (it mirrors the fail-closed stance of controller recovery).

   The migration is **verifiable**: afterwards every controller is in exactly
   one of two states, confirmable with one query —
   `SELECT owner_user_id IS NOT NULL AS owned, count(*) FROM
   ostris.protocol_controller GROUP BY 1;` — pre-existing rows `owned=false`
   (locked), every controller created from now on `owned=true` from its first
   moment.

## What ownership is NOT

- It is **not** a recovery path. A participant who has lost every device cannot
  authenticate their controller and so cannot use this surface; recovery stays
  the consuming application's fail-closed controller-replacement flow.
- It does **not** replace a signature. Authorising a transaction still requires
  the account's own Ed25519 signature; ownership only governs *who may change
  the set of credentials* a controller trusts.
- Reads (discovery of a controller's credential list) remain permission-gated;
  tightening read scope to the owner is a possible follow-up hardening, separate
  from this authorisation fix.

## Boundaries preserved

This lives entirely in osTRIS. It introduces no STIR/FFM/FIAT/fee semantics, and
it does not move the STIR↔osTRIS boundary: STIR still reaches osTRIS over the
same public application API with the caller's own token, and osTRIS is now the
authoritative place the ownership question is answered.

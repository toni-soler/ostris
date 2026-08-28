# osTRIS Core v0.1 normative test vectors

The suite includes both `OSTRIS:TX:AUTH:V1` account authorization and `OSTRIS:TX:GOVAUTH:V1` governed authorization, closed typed governed-economic policy decisions, exclusive RESTITUTION reference paths and the fixed special-account purpose matrix. All embedded private seeds are TEST ONLY and must never be used in production.

`core-v0.1.json` is normative. All private key material is public test material:

**TEST ONLY — DO NOT USE IN PRODUCTION.**

Run:

```powershell
.venv\Scripts\python.exe reference\python\verify_vectors.py
& '<node-path>\node.exe' reference\node\verify-vectors.mjs
```

Both verifiers consume `core-v0.1.json` and `protocol-event-proof-v1.json`. The suite digest is SHA-256 of RFC 8785 JCS for `{"core":<core-with-empty-expectedSuiteSha256>,"protocolEventProof":<proof-with-empty-expectedSuiteSha256>}`. Both must report the same combined digest and zero failures. Reference code is explanatory/non-normative; the specification and expected vector values prevail.

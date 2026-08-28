# Reference vector verifiers

These scripts are conformance aids, not an osTRIS implementation. They have no database, HTTP/API, services, wallets or product domain runtime.

- Python verifies the complete normative suite using `rfc8785` and `cryptography`.
- Node independently verifies the same suite using `canonicalize`, built-in `crypto` and BigInt.

Install/run from repository root:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r reference\python\requirements.txt
.venv\Scripts\python.exe reference\python\verify_vectors.py

pnpm install --dir reference\node --ignore-scripts
node reference\node\verify-vectors.mjs
```

Expected combined Core + ProtocolEventProof v1 suite digest:

```text
c06a044828c466506611195299d2f49a8d6f5db97ce29922dcb7e435525473c9
```

All embedded private material is **TEST ONLY — DO NOT USE IN PRODUCTION**.

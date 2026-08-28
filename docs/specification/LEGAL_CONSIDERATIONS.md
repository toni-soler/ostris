# Legal and Regulatory Considerations

Este documento identifica revisión necesaria; no es asesoría jurídica ni concluye clasificación.

## Ámbitos

- GDPR: roles controller/processor, base jurídica, minimización, DPIA, derechos, transferencias, retención y breach response;
- fiscalidad de barter/servicios y obligaciones de facturación/registro;
- clasificación de mutual credit y posible regulación financiera/pagos según diseño y jurisdicción;
- protección de consumidores, información precontractual, cancelación y dispute resolution;
- platform regulation para The Market y moderación/intermediación;
- reglas de asociaciones/cooperativas y validez de gobernanza/firma;
- AML/KYC/sanciones cuando modelo, escala, convertibilidad o jurisdicción lo activen;
- competencia, accesibilidad, menores, laboral y seguros según comunidad/recursos.

## Decisiones técnicas prudentes

- PII off-ledger, minimizada y con retención separada del journal;
- no prometer convertibilidad ni estabilidad;
- plataforma non-custodial de fiat/cripto y platform-not-party como principio, sujeto a conducta real;
- reglas/versiones, consentimiento/autorización, recibos y vías de disputa auditables;
- exports y correcciones que no falsifiquen historia;
- configuración por jurisdicción sin erosionar invariantes.

## Preguntas para counsel antes de piloto

¿Quién opera/controla cada comunidad? ¿La unidad es transferible/federada/convertible? ¿Quién concede crédito y absorbe default? ¿Qué actividad económica se permite? ¿Qué PII y evidencias son necesarias? ¿The Market interviene en precio, ejecución o disputa? ¿Qué reporting y tributación corresponden?

La afirmación “no es dinero, por tanto no está regulado” queda expresamente rechazada.

## Erasure versus community risk retention

Social reset and removal of a public profile do not answer whether minimal private records may be retained to prevent repeated default, sanction evasion or credit reset abuse. Before production, counsel and a DPIA must establish:

- controller roles and lawful basis for identity matching and risk history;
- which KYC evidence remains only at the provider and which references osTRIS retains;
- purpose-specific retention by signal, open case, confirmed finding, default, sanction and appeal;
- deletion, pseudonymization, restriction and key-destruction procedures;
- participant access, correction and appeal rights, including false identity matches;
- safeguards against function creep, public disclosure and cross-community/global blacklisting;
- treatment of accounting/legal retention when a participant profile is erased.

Shared directors, address or ownership do not by themselves establish organizational continuity. Provider matches and community decisions need reviewable evidence.

**LEGAL REVIEW REQUIRED before production.**

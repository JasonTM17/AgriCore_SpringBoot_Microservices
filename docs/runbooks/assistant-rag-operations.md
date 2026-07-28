# Assistant RAG operations

## Scope and safe defaults

The Assistant is authenticated and read-only. Provider `none` and curated RAG
disabled are the defaults (`ASSISTANT_PROVIDER=none`,
`ASSISTANT_RAG_ENABLED=false`). RAG retrieves only curated records from the
assistant-owned PostgreSQL database through JDBC term-indexed retrieval; it is
not a vector database. It does not expose arbitrary URL fetch, user document
upload, embeddings, or cross-service database reads. When tools are enabled,
Farm evidence is collected with authenticated read requests only after the
conversation's farm access has been authorized.

Provider credentials are deployment inputs. Set `ASSISTANT_PROVIDER_API_KEY`
only through an ignored local `.env`, a secret manager, or a Kubernetes Secret;
never add it to Git, Helm values, logs, or incident evidence. The chart selects
the Secret name/key with `assistant.providerSecretName` and
`assistant.providerApiKeyKey`.

## Configuration and limits

| Environment variable | Default | Enforced range or behavior |
|---|---:|---|
| `ASSISTANT_RAG_ENABLED` | `false` | Disabled retrieval returns no knowledge facts. |
| `ASSISTANT_RAG_MAX_RESULTS` | `4` | 1–4 results. |
| `ASSISTANT_RAG_MAX_QUERY_TERMS` | `12` | 1–20 normalized query terms. |
| `ASSISTANT_RAG_MAX_EXCERPT_CHARACTERS` | `220` | 80–240 characters per cited excerpt. |
| `ASSISTANT_RAG_QUERY_TIMEOUT` | `PT2S` | Positive and at most 10 seconds. |

The service validates these bounds when RAG is enabled. It normalizes and
deduplicates query terms, uses prepared SQL, returns at most the configured
number of citations, and keeps farm facts plus RAG facts within the existing
25-fact evidence ceiling.

When the request budget is enabled (the default), its Redis-backed reservation
tracks request and token limits by user and client IP. A Redis error denies the
request; the budget does not fail open. Retrieved farm and knowledge facts are
untrusted reference data, and output safety rejects unknown citations and a
completed evidence-backed response without a citation.

## Curated knowledge lifecycle

`assistant_knowledge_chunks` holds curated entries with stable `source_key`,
`source_uri`, positive `knowledge_version`, and `enabled` state. Its companion
`assistant_knowledge_terms` table indexes weighted normalized terms. Only
enabled chunks are eligible for retrieval; citations carry the recorded source
URI. The repository seeds the initial knowledge through Flyway migration V5.

Treat a knowledge update as a reviewed database/migration change:

1. Assign a stable `source_key`, source URI, and incremented knowledge version.
2. Review text for correctness, access sensitivity, and citation destination.
3. Test with RAG disabled first, then use the compatible-image rollout in the
   [deployment guide](../deployment-guide.md#assistant-rag-rollout-safety).
4. Keep the prior database backup until the rollout and rollback window close.

The application does not automatically classify or redact chunk content. Do not
curate credentials, tokens, personal data, private attachments, or farm data
that a caller would not otherwise be authorized to read. Conversation, audit,
and generation-event cleanup settings apply to their respective persisted
records; the cleanup implementation does not delete knowledge chunks. Define
knowledge retention and backup handling with the deployment's data policy.

## Failure handling and rollback

- With provider `none`, capability reporting is unavailable and generation work
  returns the safe `AI_PROVIDER_UNAVAILABLE` outcome rather than contacting a
  provider. A configured provider missing required configuration is reported as
  `AI_PROVIDER_CONFIGURATION_MISSING`.
- Disabled RAG yields no retrieval facts. A query with no eligible terms/chunks
  yields `RAG_NO_MATCH`. If retrieval is unavailable after authorized farm facts
  were collected, the service keeps those facts as partial evidence with
  `RAG_DEPENDENCY_UNAVAILABLE`; without usable evidence, the generation follows
  its normal unavailable outcome.
- Do not downgrade an Assistant binary that predates the V5 `KNOWLEDGE` evidence
  format without the drain, backup, and evidence-neutralization procedure in the
  [deployment guide](../deployment-guide.md#database-change-and-rollback).
- To stop retrieval on a compatible image, set `ASSISTANT_RAG_ENABLED=false`
  (or `assistant.ragEnabled=false` in Helm) and roll out normally. This stops
  new retrieval; it does not erase existing conversation evidence or curated
  records.

## Verification

Before enabling RAG in an operator environment, verify the compatible Assistant
image, completed V5 migration, healthy readiness, selected provider/secret
availability, and reviewed RAG bounds. Then submit an authorized conversation
that can cite a known enabled `source_key`, and record only non-secret outcome
codes, migration status, image digest, and citation identifiers in the rollout
evidence.

## References

- [Assistant boundary ADR](../adr/0009-persisted-assistant-boundary.md)
- [Assistant database provisioning](assistant-database-provisioning.md)
- [Deployment guide](../deployment-guide.md)
- [Assistant OpenAPI contract](../../contracts/openapi/assistant-service.v1.yaml)

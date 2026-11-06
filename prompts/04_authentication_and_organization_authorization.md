# Prompt 04: Authentication and organization authorization

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Spring Security JWT resource-server validation including issuer, expiry, audience and permissions. Map verified issuer+subject to local users/roles in Borrower/Custodian. Obtain organization scope from trusted identity/registration, not a request body. Define trader, custodian-operator and administrator roles plus service scopes without hard-coding broad admin access.

Use OAuth2 client credentials for service calls; distinguish service authority from delegated end-user scope. A valid service token does not automatically authorize arbitrary organization data. Ensure Borrower can only query agreements/contracts belonging to its borrower organization; Custodian operator is restricted to assigned mandate/organization scope.

Market and Calculation use service authorization and scoped admin ingestion routes. No password copying, in-memory production users or committed client secrets. The skeleton app.service-auth settings require actual client registration/token acquisition wiring here; they are not implemented by configuration alone.

Test borrower A requesting borrower B's valid references, tampered org fields, wrong audience, expired JWT, forbidden service scope and authorized staff operations. Document local development identity setup separately from production configuration.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.

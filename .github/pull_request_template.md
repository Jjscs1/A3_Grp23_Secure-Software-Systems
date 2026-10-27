\## What does this change?



<!-- Start with the SEC number and link the issue, e.g. "SEC-16: ... refs #16" -->



\## Security checklist

\- \[ ] No secrets, keys, `.env` or `\*.pem` files added (gitleaks must pass)

\- \[ ] Tests added or updated, including at least one attack / refusal case

\- \[ ] Any new dependency is explained here, and the OSV dependency scan passes

\- \[ ] Semgrep and CodeQL findings are fixed, not suppressed without a written reason

\- \[ ] Privileged actions call `AuditLogger.record` (SEC-17)


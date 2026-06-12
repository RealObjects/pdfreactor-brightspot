# Security policy

## Reporting a vulnerability

Please report suspected vulnerabilities in this plugin privately to
RealObjects at **support@realobjects.com** (or via this repository's
private vulnerability reporting, if enabled) rather than opening a
public issue. Include the plugin version/commit, a description, and
reproduction steps where possible.

For vulnerabilities in the PDFreactor product itself, contact
[RealObjects support](https://www.pdfreactor.com/support/).

## Deployment notes

- The PDFreactor Web Service is an unauthenticated conversion service
  that fetches URLs; do not expose it beyond the network segment that
  needs it (the bundled dev compose file binds it to loopback).
- Connection settings (`pdfreactor/serviceUrl`, `pdfreactor/apiKey`)
  are deploy-time only by design — they are not editable or readable in
  the CMS UI.
- Never commit license keys; the repository's `.gitignore` covers the
  common file names (`licensekey*.xml`, `license*.txt`).

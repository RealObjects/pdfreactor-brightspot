# Contributing

## Prerequisites

- **JDK 21** to run Gradle (the build itself targets Java 11 bytecode).
  Newer JDKs are not supported by the pinned Gradle 8.6 — its Groovy
  cannot compile build scripts on JDK 25.
- **Docker** for the end-to-end tests and the local CMS stack.
- No local Gradle needed — use the committed wrapper (`./gradlew`).

If `java -version` is not a JDK 21 by default, point Gradle at one
explicitly, e.g.:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

## Repository layout

- `plugin/` — the distributable plugin (what consumers depend on).
- `core/` + `web/` — a development-harness Brightspot app (sample
  `Article` type, seed endpoints) used for manual work and the UI test
  suite. Not part of the plugin artifact.

## Building and testing

Three test suites, each with its own role:

| Suite | Command | Needs | Covers |
| --- | --- | --- | --- |
| Unit | `./gradlew build` | nothing | configuration assembly, precedence/merge logic, error-policy branching, cache keys, record behavior (in-memory DB) |
| End-to-end | `./gradlew :plugin:e2eTest` | Docker | the real PDFreactor service via Testcontainers: conversions, pagination, diagnostics, fail-closed aborts, PDF/A |
| UI (Playwright) | `./gradlew :plugin:uiTest` | the running compose stack | the real editorial UI: preview, widget, publish round-trip, settings form, health widget |

Both Docker-based suites adapt to an unlicensed (evaluation-mode)
PDFreactor service: license-dependent assertions switch to the
documented evaluation behavior (watermark carrier pages in the preview;
Generate/publish still store output, just watermarked) where it differs.
A licensed service exercises the full set — CI runs in evaluation mode.

`./gradlew build` (unit + checkstyle) must be green for every change.
Run `e2eTest` when touching the service/conversion layer, and `uiTest`
when touching anything the editor sees. After upgrading the Brightspot
BOM, re-verify the skin-coupling inventory documented on
`PdfPreviewType` and run the UI suite (see the
[integration guide](docs/integration-guide.md#version-compatibility)).

### Local CMS stack

```sh
./gradlew build           # builds web/build/libs/web.war
docker compose up -d      # http://localhost/cms
```

The PDFreactor service runs in evaluation mode (watermarked) out of the
box; to run licensed, copy `docker-compose.override.yml.example` to
`docker-compose.override.yml` and put your license at the repo root as
`licensekey.xml` (gitignored — never commit license keys).

Seed sample data by opening `http://localhost/cms/_seed-ui-test` — it
returns the test user's name and a freshly generated password, plus
edit-page links. The endpoint is double-gated: it refuses to run in
production mode and additionally requires the
`pdfreactor/uiTestSeedEnabled=true` setting (already set in
`docker-context.properties` for this stack).

After changing only application code, redeploy with
`docker compose restart tomcat` (the WAR is bind-mounted).

## Conventions

- **One coherent unit of work per commit**, with a message describing
  that unit. Don't batch unrelated changes.
- Treat Brightspot/Dari and PDFreactor APIs as facts to verify against
  their documentation or sources — only documented mechanisms are used.
- **No deprecated APIs** when a non-deprecated alternative exists.
- Comments describe behavior, self-contained — no references to
  internal plans, tickets, or people.
- Code style is enforced by Checkstyle (runs in `./gradlew build`).

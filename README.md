<img src="plugin/src/main/resources/com/realobjects/brightspot/pdfreactor/web/pdfreactor-logo.svg" alt="PDFreactor" width="96" align="right">

# PDFreactor for Brightspot

Print-grade PDF generation for [Brightspot CMS](https://www.brightspot.com/),
powered by [RealObjects PDFreactor](https://www.pdfreactor.com/). Editors
preview their content as a paged PDF right in the CMS, generate and download
PDFs on demand, and get them regenerated automatically on publish — with the
typographic fidelity, CSS Paged Media support, and PDF/A / PDF/UA / PDF/X
conformance PDFreactor is built for.

## Features

- **Editor PDF preview** — a preview-pane mode that renders the current
  draft as the finished, paged PDF, with refresh, share, and an optional
  schedule-date control (preview the content as of a future date).
- **On-demand generation** — a right-rail widget with **Show PDF**,
  Download, and Convert again; generated PDFs are stored and cached, and
  served while content and configuration are unchanged.
- **Publish automation** — publishing marked content generates its PDF in
  the background, with a three-level opt-in (developer marker, per-site
  admin toggle, per-document editor checkbox) and failure notifications.
- **Per-site configuration** — global defaults with per-site overrides in
  Sites & Settings, layered with clear precedence and "currently in
  effect" hints, plus a raw-configuration JSON escape hatch for the full
  PDFreactor Configuration API.
- **ICC color management & conformance** — reusable, validated ICC
  profile records, output intents, automatic color conversion, and
  PDF/A / PDF/UA / PDF/X conformance with optional validation.
- **Document metadata, features & viewer preferences** — creator/
  subject/keywords, bookmarks/links/accessibility tags, and PDF viewer
  presentation defaults, configurable down to the individual document.
- **Operations** — a dashboard health widget with live status and
  license-state detection, publish-failure notifications, surfaced
  conversion logs, and admin-gated debug/inspectable troubleshooting
  builds.

## Requirements

- Brightspot CMS — developed and verified against `brightspot-bom`
  **5.0.2.4** (Java 11 target)
- A **PDFreactor 12.6 Web Service** instance (e.g. the official
  [`realobjects/pdfreactor`](https://hub.docker.com/r/realobjects/pdfreactor)
  Docker image); without a license it runs in evaluation mode
  (watermarked output)
- JDK 21 to build (the build targets Java 11 bytecode)

## Getting started

Follow the **[quick start](docs/quick-start.md)** — about 15 minutes from
zero to a PDF preview, including a `docker run` for PDFreactor, three
ways to wire the plugin into your project, and the one required setting.

## Documentation

| You are… | Start with |
| --- | --- |
| A **Brightspot administrator** configuring and operating the plugin | [Admin guide](docs/admin-guide.md) |
| A **developer** integrating the plugin into a project | [Quick start](docs/quick-start.md), then the [integration guide](docs/integration-guide.md) |
| A **template developer** styling the print output | [Template guide](docs/template-guide.md) |

Shared by all three: the [configuration reference](docs/configuration.md)
(every setting, the precedence model), the
[PDFreactor concepts](docs/pdfreactor-concepts.md) glossary, and the
[architecture overview](docs/architecture.md).

## Repository layout

- `plugin/` — the distributable plugin
  (`com.realobjects.brightspot:pdfreactor-brightspot`). This is what you
  consume.
- `core/` + `web/` — a development harness and runnable example: a
  minimal Brightspot app with a sample article type wired to the plugin,
  used by the UI test suite and the quick start's "see it without your
  own project" path. Not part of the plugin artifact.

Build and test setup, the three test suites, and contribution
conventions are described in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) — Copyright (c) RealObjects GmbH.

**Logo & trademark notice:** the PDFreactor logo
(`plugin/src/main/resources/com/realobjects/brightspot/pdfreactor/web/pdfreactor-logo.svg`)
is RealObjects' own artwork — Copyright (c) RealObjects GmbH, all rights
reserved. It is **not** covered by the MIT grant and may not be used
outside this plugin without RealObjects' permission. PDFreactor® is a
product of RealObjects GmbH.

## PDFreactor licenses & support

PDFreactor itself is a commercial product; licenses and trials are
available from [RealObjects](https://www.pdfreactor.com/buy/), and
product support via [pdfreactor.com/support](https://www.pdfreactor.com/support/).
For questions about this plugin, use this repository's issue tracker.

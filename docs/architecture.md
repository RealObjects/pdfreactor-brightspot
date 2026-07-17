# Architecture

How the plugin is built: design principles, the layer model, a glossary
of the Brightspot/Dari concepts it builds on, and the testing strategy.
For using the plugin, see the [integration guide](integration-guide.md)
and the [configuration reference](configuration.md).

## Design principles

1. **Deployment-agnostic.** PDFreactor is an external Web Service
   reached over a configurable endpoint; all configuration is read
   through Dari `Settings`; background generation runs through a
   host-pinned `Task`. The same artifact works self-managed and on
   managed hosting alike.
2. **The View System emits markup; print styling is explicit.**
   PDFreactor consumes HTML produced by the View System. Brightspot
   themes deliver CSS as separate artifacts, so the plugin never
   assumes rendered markup arrives styled for print — print CSS is
   guaranteed explicitly, primarily by injecting user stylesheets into
   the conversion.
3. **The finished PDF is the preview.** Browsers render PDF natively in
   an iframe, so there is no separate preview artifact — only one
   conversion path, whose output the editor sees.
4. **Brightspot-idiomatic extension points only.** Provider interface +
   config + ViewModel annotation + preview type + marker interface +
   `Modification` — the established platform plugin shape. Only
   documented platform mechanisms are used; where the Tool's private
   front-end internals had to be coupled (the preview chrome), every
   coupling point is documented in source as an upgrade checklist (the
   `SKIN COUPLING INVENTORY` on `PdfPreviewType`).
5. **Fail closed on broken resources where output persists, lenient
   where editors iterate.** Stored or published PDFs must never contain
   broken resources; the editor preview, by contrast, always renders and
   surfaces problems as diagnostics. Unlicensed watermarks do not block
   any path — an evaluation-mode service produces watermarked output
   everywhere, surfaced by the health widget and preview banner.

## Architecture layers

### 1. Service & config provider

- **`PdfReactorService`** — the provider interface: `renderHtml(html,
  options)` for finished HTML and `renderContent(content, options)`,
  which renders content through the View System first.
  **`DefaultPdfReactorService`** talks to the PDFreactor Web Service
  through the official Java client — synchronous `convert`, or
  `convertAsync` plus progress polling — attaching a content observer
  so missing resources and connection failures are reported, and
  translating failures into `PdfReactorException` carrying
  `PdfDiagnostics`. Health and license probes use a separate
  short-timeout client instance so they never interfere with running
  conversions.
- **`PdfReactorConfig`** — the configuration provider interface.
  **`SettingsPdfReactorConfig`** reads everything from Dari `Settings`
  under the `pdfreactor/` prefix; **`PdfReactorSiteSettings`** (a
  `Modification<SiteSettings>`) adds the global and per-site UI;
  **`SitePdfReactorConfig`** layers a site's values over the global
  config; **`PerArticlePdfReactorConfig`** layers a document's override
  fields on top; the **`PdfReactorConfigs`** factory resolves the
  effective chain for a content object. **`RawConfiguration`**
  deep-merges the configuration-JSON escape hatch, after which
  plugin-owned fields are re-enforced. See the
  [precedence model](configuration.md#the-precedence-model).
- **`@DefaultPdfReactorConfiguration`** — per-ViewModel defaults. The
  PDFreactor client deliberately has no page-geometry properties —
  paper size, margins, and header/footer are CSS Paged Media concerns —
  so the annotation's geometry values are compiled into a generated
  `@page` rule injected as a user stylesheet (`PageCssBuilder`).

### 2. Rendering pipeline

Rendering is server-side, by the Brightspot View System; PDFreactor is
not a template engine and only ever receives finished HTML. The plugin
obtains that HTML through an **`HtmlSource`** abstraction:

- **`InRequestHtmlSource`** (editor preview and tool actions): renders
  within the current request via `PageFilter.renderObject`, captured
  into a writer behind a response wrapper that shields the live Tool
  response from status/header/cookie mutations. Draft data never needs
  a fetchable URL.
- **`PermalinkHtmlSource`** (background publish task): fetches the
  published permalink over HTTP — optionally via
  `pdfreactor/internalRenderBaseUrl` — with redirect/scheme policy, a
  size cap, and charset handling; the PDF's base URL is always the
  public permalink so relative resources resolve to hosts PDFreactor
  can reach.

Resource resolution follows from Brightspot's URL conventions: images
and theme assets are emitted as absolute URLs (image service / CDN),
which PDFreactor fetches itself — requiring egress from the PDFreactor
host to those origins. ICC profiles deliberately do **not** follow this
pattern: their bytes are resolved server-side and embedded in the
conversion request.

### 3. Editor experience

- **`PdfPreviewType extends IFramePreviewType`** adds the PDF preview
  pane: the preview iframe receives `application/pdf` streamed by
  **`PdfPreviewPage`**, which loads the draft `Preview` record, renders
  in-request, converts with relaxed error policies, and surfaces
  non-fatal diagnostics as a themed banner in the Tool DOM above the
  PDF. Header controls: refresh (manual re-conversion — automatic
  re-render per keystroke would be wasteful), the standard share
  link, and an opt-in schedule-date control that re-renders the
  content as of a future date through an in-page `PreviewDatabase`
  override.
- **`GeneratePdfWidget extends ContentEditWidget`** (right rail):
  **Show PDF** (opens the published content's PDF, converting on first
  use), Download, and a CSRF-protected POST **Convert again**, plus the
  stored-PDF status line (date, page count, size) and the
  publish-failure banner.
- **`PdfGeneratePage`** converts fail-closed on broken resources (but
  not on license — an unlicensed service stores watermarked output),
  stores bytes as a `StorageItem`, records a **`GeneratedPdf`** cache
  entry, and streams the result. Access is authorization-checked per content (site scope
  and type permission), and downloads go by record id — storage URLs
  are never exposed.

### 4. Publish automation

A three-level opt-in — developer (the `HasPdfRendering` marker on the
type), administrator (per-site/global disable toggle), editor (the
per-document "Generate PDF on publish" checkbox, on by default) — all
evaluated in a `Modification`'s `afterSave`, which filters to visible
(published) revisions via `State#isVisible`. Qualifying publishes
enqueue a host-pinned `Task` (one node in a cluster) that renders,
converts fail-closed on broken resources (but not on license — an
unlicensed service archives watermarked output), stores, stamps the
content's status fields, and on failure publishes a
`PdfPublishFailureTopic` notification. The task
is bounded by a small shared executor (`pdfreactor/publishConcurrency`)
so bulk republishes cannot stampede the service; a supersede guard
drops a conversion when a newer revision already owns generation; and
every failure class surfaces — there is no silent-failure path.

### 5. Output & caching

PDF bytes persist as `StorageItem`s referenced from **`GeneratedPdf`**
records with a unique cache key: content id + revision (update date) +
a hash of output-affecting options + a **config fingerprint** covering
every config-sourced value that changes output (stylesheets,
conformance, ICC bytes, metadata, merged configuration JSON …). The
canonical key encoding is collision-proof (prefixed, length-delimited
fields; null distinct from `"null"`). Stored generations per content
are bounded by `pdfreactor/generatedPdfRetention`.

### 6. Observability

The **`PdfReactorHealthWidget`** dashboard widget shows service health
(UP + version / DOWN + reason) and the license state
(licensed / evaluation / unknown, from a cached background license
probe). Probes are non-blocking (background refresh, stale-while-
revalidate, short probe timeout) and the widget self-polls while the
dashboard is open. Service health and per-conversion resource failures
are deliberately distinct signals — they have different remediations.

### 7. Packaging

A single-module library (`plugin/`, package root
`com.realobjects.brightspot.pdfreactor`) following Brightspot's
platform-extension convention, consumed as a plain dependency. The
PDFreactor Java client resolves from Maven Central; Brightspot
artifacts from the public Brightspot Artifactory. `core/` + `web/` in
this repository are a development harness and runnable example, not
part of the plugin artifact.

## Glossary — Brightspot / Dari stack

Covers the Brightspot/Dari stack only; generic web/Java tooling and
PDFreactor's own API are documented elsewhere (see the
[concepts page](pdfreactor-concepts.md) for the PDFreactor side).

**Platform**

- **Brightspot** — the enterprise Java CMS this plugin integrates into;
  runs as a Java web application inside Tomcat.
- **Dari** — Brightspot's underlying data/persistence framework: how
  content types are stored, queried, versioned, and rendered.
- **Brightspot Cloud** — the vendor-managed hosting variant; contrast
  *self-managed*, where the customer runs the infrastructure.

**Data framework (Dari)**

- **Content** — base class for editorial content types.
- **Model / View / ViewModel** — Brightspot's MVVM rendering split: the
  Model is content data, the ViewModel reshapes it, the View (template)
  emits markup.
- **StorageItem** — a stored binary file (image, PDF, font) backed by
  S3/GCS/filesystem, carrying a URL. Generated PDFs are StorageItems.
- **Modification\<T\>** — attaches extra fields and behavior to an
  existing type without editing its source; the basis for the plugin's
  opt-in integration.
- **Lifecycle hooks** — callbacks around saving (`beforeSave`,
  `onValidate`, `afterSave`, …); the plugin reacts to `afterSave`.
- **Task / host-pinned** — Dari's background-job system; host-pinning
  runs a job on exactly one node in a cluster.
- **Settings** — Dari's configuration mechanism (`context.xml`,
  properties files, environment), read via the `Settings` class.
- **Marker interface** — an empty interface a type implements to opt
  into behavior; here `HasPdfRendering`.

**View & theming**

- **Theme / Styleguide** — a theme bundles layout (Handlebars),
  styling (CSS from Less), and behavior (JS); Styleguide is the
  webpack-based front-end dev environment that builds it.
- **Handlebars (`.hbs`)** — the standard View System template engine;
  JSP via `@Renderer.Path` is the legacy path; JSON views serve
  headless delivery.

**Editorial UI**

- **Preview System / IFramePreviewType** — the framework for live
  previews in the editor; `IFramePreviewType` renders an endpoint's
  response in an iframe (the base of the PDF preview).
- **Preview (record)** — carries the draft data the preview endpoint
  renders.
- **The Tool / ToolPage / widget** — Brightspot's editorial UI, its
  server endpoints, and edit-form panels (the PDF widget is a
  `ContentEditWidget`).
- **Sites & Settings / multi-site** — the admin area for global and
  per-site configuration; one instance can host multiple sites with
  separate configuration.
- **Notification / Topic** — Brightspot's pub/sub framework; the
  plugin's publish-failure notification is a `ToolTopic`.

**Images & assets**

- **DIMS / image-size system** — Brightspot's on-demand image
  resize service and the named-size mechanism emitting `src`/`srcset`
  URLs; relevant because PDFreactor fetches those URLs.

**Build**

- **`com.psddev` / `com.brightspot`** — the Brightspot artifact
  namespaces; versions are pinned by the `brightspot-bom` BOM, built
  with the `com.brightspot.gradle` plugin, served from the public
  Brightspot Artifactory (`artifactory.psdops.com/public`).

## Testing strategy

Three layers, all in this repository:

1. **Unit tests** (`plugin/src/test`, in `./gradlew build`): JUnit 5 +
   Mockito + AssertJ. Pure logic and client-boundary assembly against a
   mocked PDFreactor client — configuration assembly, precedence and
   JSON-merge behavior, error-policy branching, generated `@page` CSS,
   async polling, diagnostics extraction, cache-key encoding. Dari
   record behavior runs on an in-memory test database
   (`com.psddev:test-db`); HTTP-fetch logic against a local test HTTP
   server.
2. **End-to-end tests** (`plugin/src/e2eTest`,
   `./gradlew :plugin:e2eTest`; needs Docker): Testcontainers runs the
   real `realobjects/pdfreactor:12.7.0` image and exercises the service
   for real — health/version, sync + async conversion, pagination,
   stylesheet injection, diagnostics, fail-closed aborts, PDF/A output.
   Without a license file the suite runs in evaluation mode and
   license-sensitive assertions adapt.
3. **In-CMS UI tests** (`plugin/src/uiTest`,
   `./gradlew :plugin:uiTest`; needs the running docker-compose stack):
   Playwright for Java drives the real editorial UI — preview streams
   PDF, header controls, refresh/share/schedule-date, the generate
   widget round-trip including caching and CSRF behavior, diagnostics
   banners, publish round-trip, health widget, settings clusters and
   field notes, the ICC picker. This layer exists because the Tool's
   front-end contract is undocumented — regressions there are invisible
   to the lower layers.

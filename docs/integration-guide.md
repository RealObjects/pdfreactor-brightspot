# Integration guide

For Brightspot developers integrating the plugin into a project: module
wiring, opting content types in, per-type defaults, the programmatic
API, storage/caching behavior, and operational hooks. The
[quick start](quick-start.md) is the condensed version of the first
half; the [configuration reference](configuration.md) holds the
exhaustive setting tables this guide links into.

## Dependency setup

The plugin is distributed as source: clone
`https://github.com/RealObjects/pdfreactor-brightspot.git` and build
with the committed wrapper (`./gradlew :plugin:build`). Building needs
JDK 21 (the build targets Java 11 bytecode); see
[CONTRIBUTING.md](../CONTRIBUTING.md).

The plugin's own dependencies resolve from two anonymous repositories:
`mavenCentral()` (the PDFreactor Java client,
`com.pdfreactor.webservice:pdfreactor-java-client:12.6.0`) and the
public Brightspot Artifactory (`https://artifactory.psdops.com/public`)
your project already uses. Your consuming project needs `mavenCentral()`
in its repositories; nothing else is special.

### Path A — `publishToMavenLocal` + `mavenLocal()`

```sh
./gradlew :plugin:publishToMavenLocal
```

publishes `com.realobjects.brightspot:pdfreactor-brightspot:1.0.0-SNAPSHOT`
into `~/.m2/repository`. The POM is self-contained: it carries the
concrete resolved version of every dependency, so no BOM import is
needed on your side. Consume it:

```gradle
// build.gradle
repositories {
    mavenLocal()
    mavenCentral()
    // ... your existing Brightspot repositories
}

dependencies {
    api 'com.realobjects.brightspot:pdfreactor-brightspot:1.0.0-SNAPSHOT'
}
```

### Path B — Gradle composite build

Reference the checkout from your `settings.gradle` so you always
compile against the sources you cloned. The substitution must be
declared explicitly — Gradle's automatic substitution matches the
included build's *project* coordinates
(`com.realobjects.brightspot:plugin`), not the published artifactId:

```gradle
// settings.gradle
includeBuild('../pdfreactor-brightspot') {
    dependencySubstitution {
        substitute module('com.realobjects.brightspot:pdfreactor-brightspot') using project(':plugin')
    }
}
```

```gradle
// build.gradle — no version needed; the substitution supplies the project
dependencies {
    api 'com.realobjects.brightspot:pdfreactor-brightspot'
}
```

The plugin's Brightspot dependencies are declared without versions
(your project's Brightspot BOM supplies them, as it does for the
project's own Brightspot dependencies).

### Path C — vendored JAR

Build once, copy `plugin/build/libs/plugin-1.0.0-SNAPSHOT.jar` into
your project, and depend on the file:

```gradle
// build.gradle
dependencies {
    api files('libs/plugin-1.0.0-SNAPSHOT.jar')
}
```

A file dependency carries no transitive metadata. The Brightspot
artifacts are on your classpath anyway, but the PDFreactor client is
not — declare it explicitly:

```gradle
    api 'com.pdfreactor.webservice:pdfreactor-java-client:12.6.0'
```

### Version compatibility

Developed and verified against `brightspot-bom` **5.0.2.4**
(`com.brightspot.gradle` 3.8.1, Java 11 target) and **PDFreactor 12.6**
(client and Web Service versions should match).

One caveat deserves its own paragraph: the **editor PDF preview bar**
integrates with Brightspot's v5 Tool skin at a level the platform does
not expose a public extension point for. The coupling points are
documented in the `SKIN COUPLING INVENTORY` Javadoc on
`PdfPreviewType`, pinned to the verified BOM version. After any
Brightspot/BOM upgrade, re-verify that inventory, run the Playwright UI
suite (`./gradlew :plugin:uiTest`), and check the preview bar visually
before shipping.

## Opting content types in

Implement the `HasPdfRendering` marker interface
(`com.realobjects.brightspot.pdfreactor.HasPdfRendering`) on a content
type:

```java
public class Article extends Content implements Directory.Item, HasPdfRendering {
```

The type must be renderable through the View System (a ViewModel /
template producing a full HTML page) — that is what gets converted.

Marked types gain:

- the **PDF preview** option in the content preview pane;
- the right-rail **PDF widget** (Show PDF / Download / Convert again,
  plus the stored-PDF status line);
- a **PDFreactor cluster** on the edit form: the "Generate PDF on
  publish" toggle, the schedule-date preview opt-in, per-document
  overrides (metadata / features / viewer preferences), read-only
  generation status, and — when an admin enables the gate — the
  debug/inspectable troubleshooting toggles (see the
  [field reference](configuration.md#per-document-editor-fields-haspdfrenderingdata));
- **publish automation**: publishing generates a PDF in the background.

Publish automation is a three-level opt-in, each level independent:

1. **Developer:** the `HasPdfRendering` marker (per type).
2. **Administrator:** per-site enablement — the "Disable Publish
   Automation" kill switch in Sites & Settings (global or per site).
3. **Editor:** the per-document "Generate PDF on publish" checkbox,
   checked by default.

## Per-type defaults: `@DefaultPdfReactorConfiguration`

Annotate the type's ViewModel (preferred — the class the View System
resolves for rendering) to give every PDF of that type its page
geometry and defaults:

```java
@DefaultPdfReactorConfiguration(
        paperSize = "A4",
        margin = "20mm",
        headerContent = "\"Acme Quarterly\"",
        footerContent = "counter(page) \" / \" counter(pages)",
        userStyleSheetUris = {"/styles/print.css"},
        javaScript = DefaultPdfReactorConfiguration.JavaScript.DISABLED)
public class ArticleViewModel extends ViewModel<Article> implements PageEntryView {
```

All 11 attributes are tabulated in the
[configuration reference](configuration.md#per-content-type-defaults-defaultpdfreactorconfiguration).
Two need calling out:

- `javaScript` is a **tri-state enum** (`DEFAULT` / `ENABLED` /
  `DISABLED`): `DEFAULT` means "no opinion", inheriting the site/global
  setting (ultimately on). An enum is used instead of a boolean so
  "unset" stays representable.
- `conformance`'s default (`PDF`) means **unset** — it inherits the
  configured conformance and cannot force plain PDF over a site-level
  PDF/A. To get plain PDF, leave the site/global conformance unset.

The annotation seeds the per-call options: anything you set explicitly
on a `PdfRenderOptions` for a call wins over the annotation.

## Programmatic API

Entry point: `PdfReactorService`. Construct
`new DefaultPdfReactorService()` to read the standard layered
configuration, or pass a `PdfReactorConfig` for an explicit
configuration source (e.g. `PdfReactorConfigs.forContent(content)` for
content-scoped per-site/per-article resolution).

```java
PdfReactorService service = new DefaultPdfReactorService();

// Convert content (renders it through its ViewModel first):
PdfResult result = service.renderContent(article,
        PdfRenderOptions.builder()
                .title("Quarterly report")
                .failOnMissingResources(true)
                .build());

// Or convert HTML you already have:
PdfResult fromHtml = service.renderHtml("<html>…</html>", null);

byte[] pdf = result.getDocument();
int pages = result.getNumberOfPages();
PdfDiagnostics diagnostics = result.getDiagnostics(); // non-fatal problems
```

`PdfRenderOptions` is an immutable builder type; the
[full option list](configuration.md#programmatic-options-pdfrenderoptions)
is in the reference. `PdfRenderOptions.fromAnnotated(viewModelClass)`
gives you a builder pre-seeded from the annotation when you want
annotation defaults plus targeted overrides.

### Error handling

Conversions throw `PdfReactorException` (unchecked) on failure —
service unreachable, conversion aborted, invalid configuration. The
exception carries `PdfDiagnostics`; `PdfProblemReport.of(exception)`
turns it into an editor-presentable report:

```java
try {
    PdfResult result = service.renderContent(article, options);
} catch (PdfReactorException e) {
    PdfProblemReport report = PdfProblemReport.of(e);
    report.getKind();     // MISSING_RESOURCE, LICENSE, SERVICE, CONFIG, …
    report.getDetails();  // de-duplicated human-readable lines
    report.getLogText();  // the PDFreactor conversion log (capped)
}
```

The plugin's own surfaces set the error policies per path: previews are
**lenient** (missing resources are reported as diagnostics, the editor
still sees a PDF), while publish and on-demand generation **fail
closed** (`failOnMissingResources(true)`) so no broken PDF is ever
stored or published. License problems always fail closed. Follow the
same pattern in your own calls.

## Storage & caching

On-demand and publish-generated PDFs are stored as `GeneratedPdf`
records (the bytes go to Brightspot's default storage). The cache key
combines:

- the content id and its update date (a re-edit invalidates),
- the render options hash (annotation + per-call),
- a **configuration fingerprint** of every config-sourced
  output-affecting value (stylesheets, conformance, color/ICC bytes,
  metadata/features/viewer values, merged configuration JSON…),

so any change that would alter the output produces a new key, and a
stored PDF is served only while content and configuration are
unchanged. Storage paths key on the same value.

Retention: `pdfreactor/generatedPdfRetention` (default 20) bounds the
stored generations per content item; older records and their files are
pruned. Stored PDFs are streamed through a download-by-id Tool endpoint
that re-checks the current user's access to the content — direct
storage URLs are never handed out.

Debug/inspectable troubleshooting builds bypass all of this: they are
converted fresh, served inline, and never cached, stored, stamped, or
published.

## Operations

- **Health widget** (`PdfReactor Service` dashboard widget): shows
  UP + service version or a red DOWN line, plus the license state.
  Admins add it via the standard dashboard editor. It self-polls (~30 s)
  and stays non-blocking — probes run in the background with a short
  `pdfreactor/healthTimeoutMillis` (default 3 s).
- **License state**: a cached background probe reports
  `licensed` / `evaluation` / `unknown` on the widget; previews show an
  informational banner in evaluation mode.
- **Publish-failure notifications**: failures of publish-triggered
  generation publish a `PdfPublishFailureTopic` notification (content,
  site, error detail). Users subscribe via Brightspot's standard
  notification settings. The failure (with the PDFreactor conversion
  log) also shows on the edit form and the PDF widget.
- **Timeouts and concurrency**: client/conversion timeouts and the
  publish fan-out bound (`pdfreactor/publishConcurrency`, default 3) are
  deploy-time settings — see the
  [reference](configuration.md#connection--operation).
- **Conversion log**: failure surfaces include the PDFreactor log
  excerpt; raise `pdfreactor/logLevel` (default `WARN`) when chasing a
  rendering problem, and use the per-document debug/inspectable builds
  for deep inspection (admin-gated; see the
  [admin guide](admin-guide.md#troubleshooting)).

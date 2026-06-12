# Configuration reference

This is the complete configuration reference for the PDFreactor for
Brightspot plugin. It serves all three audiences — Brightspot
administrators, integrating developers, and template developers — as the
shared source of truth for every setting, its precedence, and its merge
behavior. Task-oriented walkthroughs live in the
[admin guide](admin-guide.md), the
[integration guide](integration-guide.md), and the
[template guide](template-guide.md); this document is the table to come
back to.

Verified against plugin version 1.0.0-SNAPSHOT, PDFreactor 12.6
(Web Service), and `brightspot-bom` 5.0.2.4.

---

## The precedence model

Configuration is layered. A value set at a higher-precedence layer wins;
an unset value falls through to the next layer down:

```
1. Per-call PdfRenderOptions        (programmatic; explicit options win
                                     over annotation-seeded ones)
2. @DefaultPdfReactorConfiguration  (per content type / ViewModel —
                                     seeds the per-call options)
3. Per-document editor overrides    (the PDFreactor cluster on the
                                     content edit form — document
                                     metadata, document features,
                                     viewer preferences)
4. Per-site Sites & Settings UI     (overrides 5–6 for that site)
5. Global Sites & Settings UI       (overrides 6 where set)
6. Deploy-time Dari Settings        (pdfreactor/* keys — the baseline)
   … and built-in defaults beneath everything.
```

Three things always hold, regardless of layering:

- **Plugin-enforced fields always win.** The document HTML itself, the
  resource-diagnostics observer, the per-path error policies (preview is
  lenient, publish fails closed), and timeouts are owned by the plugin
  and cannot be overridden — not even by raw configuration JSON.
- **Connection settings are deploy-time only.** `pdfreactor/serviceUrl`
  and `pdfreactor/apiKey` have no UI fields, by design: a CMS user must
  not be able to read credentials out of a form or repoint conversions at
  another service. They are set in Tomcat `context.xml` / Dari settings
  at deploy time.
- **`pdfreactor/validateConformance` is global-only.** There is no UI
  field and no per-document override for conformance validation.

### Merge semantics per value kind

| Kind | Behavior across layers |
| --- | --- |
| Scalars (strings, booleans, enums) | **Replace.** Set = override, unset/blank = inherit. Tri-state checkboxes (unset/on/off) follow the same rule: only an explicitly set value overrides. |
| User stylesheet lists | **Append.** Stylesheets configured at lower layers are kept; higher layers add to the list (the deploy-time/site list first, then annotation `userStyleSheetUris`, then per-call `addStyleSheet`). |
| `configurationJson` | **Deep-merge.** JSON objects merge recursively; arrays and scalars replace. Layer order among JSON sources: global → site → per-view annotation → per-call. After the merge, every plugin/UI-owned field is re-enforced — see below. |

### The raw-configuration escape hatch

`configurationJson` (deploy-time key, global/site UI field, annotation
attribute, and per-call option) accepts a raw PDFreactor `Configuration`
object as JSON and can set any property of the
[PDFreactor Configuration API](https://www.pdfreactor.com/product/webservice/doc/java.html)
that no form field owns.

A canonical use: PDFreactor's
[URL rewrites](https://www.pdfreactor.com/product/doc_html/manual-lib.html#urlRewrites)
(`urlRewriteSettings`), which map resource URLs the service cannot
resolve (e.g. a CMS public host of `localhost` seen from a PDFreactor
container) to service-reachable ones before fetching — the dev harness
in this repository ships such a rule in `docker-context.properties`.

It cannot win against the forms: after the JSON layers are deep-merged
onto the assembled configuration, the plugin re-applies every
plugin/UI-owned value on top — the document, the content observer, the
error policies, conformance, color management, JavaScript processing,
title/author, document metadata, document features, viewer preferences,
and the configured stylesheets (pass-through stylesheets are appended
after them, not instead of them). A value set through any PDFreactor
form — global, per-site, or on an individual document — therefore always
beats the JSON; the JSON only fills in properties no form controls.

A top-level `licenseKey` placed in pass-through JSON is redacted from
the DEBUG-level configuration echo in the logs.

---

## Deploy-time settings (`pdfreactor/*` Dari Settings keys)

Set these in the consuming app's Dari settings (commonly Tomcat
`context.xml` environment entries or a context-properties file). Exactly
**one key is required**: `pdfreactor/serviceUrl`. Everything else has a
usable default.

All 32 keys, grouped as in `SettingsPdfReactorConfig`:

### Connection & operation

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/serviceUrl` | String | — (**required**) | Base URL of the PDFreactor Web Service, e.g. `http://pdfreactor:9423/service/rest`. |
| `pdfreactor/apiKey` | String | unset | API key sent with each conversion, if the service enforces one. |
| `pdfreactor/licenseKey` | String | unset | License key content sent with each conversion — the CMS-side alternative to installing the license on the service ([licensing](https://www.pdfreactor.com/product/doc_html/manual-lib.html#LicenseKey)). |
| `pdfreactor/clientTimeoutMillis` | int | `60000` | HTTP timeout of the conversion client. |
| `pdfreactor/healthTimeoutMillis` | int | `3000` | HTTP timeout of the health/license probes (keeps the dashboard responsive when the service hangs). |
| `pdfreactor/conversionTimeoutSeconds` | int | `300` | Server-side conversion timeout. |
| `pdfreactor/asyncPollIntervalMillis` | long | `1000` | Poll interval when converting asynchronously. |
| `pdfreactor/asyncDefault` | boolean | `false` | Whether conversions run asynchronously by default. |
| `pdfreactor/publishConcurrency` | int | `3` | Maximum concurrent publish-triggered conversions; additional publishes queue. |

### Rendering

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/baseUrl` | String | unset | Base URL PDFreactor resolves relative URLs against. |
| `pdfreactor/defaultUserStyleSheetUris` | String (comma-separated) | unset | User stylesheets (typically print CSS) injected into every conversion ([user style sheets](https://www.pdfreactor.com/product/doc_html/manual-lib.html#userStyleSheets)). |
| `pdfreactor/javaScriptEnabled` | Boolean | unset (= **on**) | Whether page JavaScript runs during conversion ([JavaScript](https://www.pdfreactor.com/product/doc_html/manual-lib.html#JavaScript)). |
| `pdfreactor/logLevel` | String (`Configuration.LogLevel` name) | `WARN` | PDFreactor conversion log level. |
| `pdfreactor/configurationJson` | String (JSON) | unset | Raw-configuration escape hatch, global layer (see above). |
| `pdfreactor/internalRenderBaseUrl` | String | unset | Internal HTTP base URL the publish automation fetches permalinks from (the background task has no request context); the public base URL is still used inside the PDF. |

### Color & conformance

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/conformance` | String (`Configuration.Conformance` name) | unset (= plain PDF) | Output conformance profile — PDF/A, PDF/UA, PDF/X variants ([conformance](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFAConformance)). |
| `pdfreactor/validateConformance` | Boolean | unset (= off) | Validate the produced document against the conformance profile; violations fail the conversion ([validation](https://www.pdfreactor.com/product/doc_html/manual-lib.html#conformance-validation)). Global-only. |
| `pdfreactor/outputIntentIdentifier` | String | unset | Output-intent identifier embedded into PDF/A / PDF/X output ([output intent](https://www.pdfreactor.com/product/doc_html/manual-lib.html#OutputIntent)). |
| `pdfreactor/outputIntentProfileUri` | String (URI, incl. `classpath:`) | unset | ICC profile for the output intent; resolved server-side and embedded in the request. |
| `pdfreactor/cmykIccProfileUri` | String (URI, incl. `classpath:`) | unset | CMYK ICC profile for color conversion; resolved server-side. |
| `pdfreactor/colorConversionEnabled` | Boolean | unset | Enable automatic color conversion ([color space conversion](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ColorSpaceConversion)). |
| `pdfreactor/colorConversionIntent` | String (`Configuration.ColorConversionIntent` name) | unset | Color-conversion rendering intent. Deploy-time / JSON only — deliberately no UI field. |

### Document metadata & features

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/creator` | String | unset | Default document Creator (PDF metadata). |
| `pdfreactor/subject` | String | unset | Default document Subject. |
| `pdfreactor/keywords` | String (comma-separated) | unset | Default document Keywords. |
| `pdfreactor/addBookmarks` | Boolean | unset | Build a bookmark outline ([bookmarks](https://www.pdfreactor.com/product/doc_html/manual-lib.html#Bookmarks)). |
| `pdfreactor/addLinks` | Boolean | unset | Keep hyperlinks clickable ([links](https://www.pdfreactor.com/product/doc_html/manual-lib.html#Links)). |
| `pdfreactor/addTags` | Boolean | unset | Produce a tagged (accessible) PDF ([tagged PDF](https://www.pdfreactor.com/product/doc_html/manual-lib.html#TaggedPDF)). |

### Viewer preferences

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/viewerPageLayout` | String (`PdfViewerPageLayout` name) | unset | Initial page layout when the PDF opens in a viewer ([viewer preferences](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ViewerPreferences)). |
| `pdfreactor/viewerFitWindow` | Boolean | unset | Resize the viewer window to the first page. |
| `pdfreactor/viewerDisplayDocTitle` | Boolean | unset | Show the document title (not the file name) in the viewer title bar. |

### Storage

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `pdfreactor/generatedPdfRetention` | int | `20` | How many stored PDFs to keep per content item; older ones (record + file) are pruned. `0` or negative disables pruning. |

> The dev harness in this repository additionally reads
> `pdfreactor/uiTestSeedEnabled` (opt-in for the `/cms/_seed-ui-test`
> bootstrap endpoint). That is a harness setting, not part of the
> plugin — see [CONTRIBUTING.md](../CONTRIBUTING.md).

---

## Global & per-site UI fields (Sites & Settings)

One modification (`PdfReactorSiteSettings`) serves both levels: edit the
global record (Admin → Sites & Settings, CMS tab) for site-wide
defaults, or a site's own Settings record to override them for that
site. All fields live in a single **PDFreactor** cluster, grouped by
section headings. Every inheritable field shows the explanation and —
when left blank — the value **currently in effect (inherited)**.

**19 fields** on `PdfReactorSiteSettings`:

| Field (display name) | Type | Section | Meaning |
| --- | --- | --- | --- |
| Base Url | text | (top) | Overrides the base URL for resolving relative links and assets. |
| Default User Style Sheet Uris | list of text | (top) | User stylesheets injected into every conversion. When non-empty, **replaces** the inherited list for this scope. |
| Conformance | dropdown | (top) | Output conformance profile (PDF/A…, PDF/UA, PDF/X…). |
| Enable JavaScript Processing | toggle | (top) | Whether page JavaScript runs during conversion (default in effect: on). |
| Creator | text | Document Metadata | Default Creator metadata. |
| Subject | text | Document Metadata | Default Subject metadata. |
| Keywords | text | Document Metadata | Default Keywords metadata (comma-separated). |
| Add Bookmarks | toggle | Document Features | Bookmark outline. |
| Add Links | toggle | Document Features | Clickable hyperlinks. |
| Add Tags | toggle | Document Features | Tagged (accessible) PDF. |
| Viewer Page Layout | dropdown | Viewer Preferences | Initial page layout in the PDF viewer. |
| Viewer: Fit Window | toggle | Viewer Preferences | Fit the viewer window to the first page. |
| Viewer: Display Document Title | toggle | Viewer Preferences | Title bar shows the document title. |
| Output Intent Identifier | text | Color Management | Output-intent identifier for PDF/A / PDF/X. |
| Output Intent ICC Profile | ICC Profile reference | Color Management | Output-intent profile (embedded into the PDF). |
| CMYK ICC Profile | ICC Profile reference | Color Management | CMYK profile for color conversion. |
| Enable Automatic Color Conversion | toggle | Color Management | PDFreactor color conversion on/off. |
| Configuration Json | JSON editor | Advanced | Raw-configuration escape hatch, site/global layer. Validated on save. |
| Allow debug/inspectable PDF builds | toggle | Advanced | Administrator gate for the per-document troubleshooting toggles. |

Additionally, `PdfReactorPublishSettings` contributes the
publish-automation kill switch (same cluster): **Disable Publish
Automation** — when checked for a site (or globally), publishing content
on that site generates no PDFs, regardless of per-document settings.

Notes:

- **Service URL and API key are deliberately absent** from the UI —
  deploy-time only (see the precedence section).
- **Color Conversion Intent has no UI field** — deploy-time / JSON only.
- Per-site values follow the merge semantics above: scalars replace,
  the stylesheet list replaces the inherited list when non-empty,
  `configurationJson` deep-merges over the global JSON.

---

## Per-content-type defaults: `@DefaultPdfReactorConfiguration`

Place the annotation on the content type's ViewModel (or the content
class itself) to give every PDF of that type its page geometry and
type-specific defaults. The annotation seeds the per-call options, so
explicit `PdfRenderOptions` win over it. All **11 attributes**:

| Attribute | Type | Default | Meaning |
| --- | --- | --- | --- |
| `paperSize` | String | `""` | CSS `@page` `size` value, e.g. `"A4"` or `"A4 landscape"`. |
| `margin` | String | `""` | CSS `@page` `margin` shorthand, e.g. `"20mm"`. |
| `headerContent` | String | `""` | CSS `content` expression for the `@top-center` margin box, e.g. `"\"Acme Corp\""`. |
| `footerContent` | String | `""` | CSS `content` expression for the `@bottom-center` margin box, e.g. `"counter(page) \" / \" counter(pages)"`. |
| `userStyleSheetUris` | String[] | `{}` | Extra user stylesheets, **appended** to the configured defaults. |
| `conformance` | `Configuration.Conformance` | `PDF` | Conformance profile for this type. **Limitation:** the default `PDF` means "unset" — it inherits the site/global conformance and cannot force plain PDF over it. |
| `outputIntentIdentifier` | String | `""` | Output-intent identifier. |
| `outputIntentProfileClasspath` | String | `""` | Classpath location of the output-intent ICC profile (read server-side). |
| `cmykIccProfileClasspath` | String | `""` | Classpath location of the CMYK ICC profile. |
| `configurationJson` | String | `""` | Raw-configuration escape hatch, per-view layer. |
| `javaScript` | `JavaScript` enum | `DEFAULT` | Tri-state: `DEFAULT` inherits (site/global, ultimately on), `ENABLED` forces on, `DISABLED` forces off. An enum rather than a boolean so "unset" stays distinct. |

---

## Per-document editor fields (`HasPdfRenderingData`)

Content types that implement `HasPdfRendering` get a **PDFreactor**
cluster on the content edit form:

| Field | Type | Meaning |
| --- | --- | --- |
| Generate PDF on publish | checkbox, **checked by default** | Publishing this content generates a PDF (subject to the site toggle and the developer marker). Unchecking opts this document out. The plugin still honors the legacy stored opt-out (`pdfreactor.skipPdfOnPublish`) from older revisions when the new field has never been saved. |
| Show schedule-date preview control | checkbox, off by default | Adds a calendar control to the PDF preview that renders the content as it would look at a future date. |
| Debug build | checkbox, gated | Preview/Show PDF produce a PDFreactor debug build ([debugging tools](https://www.pdfreactor.com/product/doc_html/manual-lib.html#DebuggingTools)). Diagnostic only — never cached, stored, or published. Visible only while the admin gate "Allow debug/inspectable PDF builds" is on. |
| Inspectable build | checkbox, gated | Preview/Show PDF produce an inspectable build for the PDFreactor Inspector ([inspectable documents](https://www.pdfreactor.com/product/doc_html/manual-lib.html#InspectableDocuments)). Same gating and non-persistence as Debug build. |
| Generated PDF Date | read-only | When the most recent PDF for this content was generated. |
| Generated PDF Status | read-only | Result of the most recent generation: success (page count, size, diagnostics counts) or the failure reason. |
| Document Creator / Subject / Keywords | text | Per-document metadata overrides (Document Metadata section). |
| Add Bookmarks / Add Links / Add Tags | toggles | Per-document feature overrides (Document Features section). |
| Viewer Page Layout / Viewer: Fit Window / Viewer: Display Document Title | dropdown / toggles | Per-document viewer-preference overrides (Viewer Preferences section). |

All override fields inherit when unset, following the standard scalar
semantics. `validateConformance` deliberately has no per-document
override.

---

## Programmatic options: `PdfRenderOptions`

For code that calls `PdfReactorService` directly. Built with
`PdfRenderOptions.builder()`; every option is optional:

- Page geometry: `paperSize`, `margin`, `headerContent`, `footerContent`
- Stylesheets: `addStyleSheet(PdfStyleSheet)` (cumulative)
- Rendering: `baseUrl`, `javaScriptEnabled`, `conformance`
- Metadata: `title`, `author`
- Color: `outputIntentIdentifier`, `outputIntentProfileData(byte[])`,
  `cmykIccProfileData(byte[])`, `colorConversionEnabled`,
  `colorConversionIntent`
- Error policies: `failOnMissingResources` (preview leaves this off and
  surfaces diagnostics; publish/generate set it), `failOnLicenseProblems`
  (on by default)
- Execution: `async`, `conversionTimeoutSeconds`
- Troubleshooting: `debug`, `inspectable`
- Escape hatch: `configurationJson` (deep-merged as the per-call layer)

`PdfRenderOptions.fromAnnotated(viewModelClass)` seeds a builder from
`@DefaultPdfReactorConfiguration`; explicit values set on the builder
override the seeded ones. See the
[integration guide](integration-guide.md) for usage.

---

## ICC profiles (`IccProfile` records)

ICC profiles are reusable content records, picked by reference wherever
a profile is needed (output intent and CMYK conversion, global or
per-site — the same profile can serve several fields and sites).

| Field | Required | Meaning |
| --- | --- | --- |
| Name | yes | Descriptive name, e.g. "ISO Coated v2 (ECI)" — what the picker shows. |
| File | yes | The `.icc`/`.icm` profile file. Validated on save: a file without an ICC signature is rejected with a field error. |
| Description | no | Free-form notes (color space, source, intended use). |

Profile bytes are read server-side and embedded into the conversion
request, so the PDFreactor host needs no network access to the CMS
storage. ICC profiles are created/edited through a plain Save dialog (no
publish/workflow), and they participate in the conversion cache key — a
changed profile correctly invalidates cached PDFs.

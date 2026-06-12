# Template guide

For template developers building and styling the print output. Every
print concept is introduced in plugin context and linked to the
official
[PDFreactor manual](https://www.pdfreactor.com/product/doc_html/manual-lib.html);
the plugin's integration internals are the
[integration guide](integration-guide.md)'s job.

## How your HTML reaches PDFreactor

PDFreactor is a browser-grade HTML renderer: what gets converted is the
**full HTML page** your ViewModel/template produces for the content —
the same markup the live site serves. There is no separate "PDF
template" unless you make one (see print stylesheets below).

The plugin obtains that page in one of two ways:

- **In-request render** (editor preview, Show PDF, Convert again): the
  page is rendered through the View System inside the current request —
  including drafts and schedule-date previews.
- **Permalink fetch** (publish automation): the background task has no
  request, so it fetches the content's **permalink** over HTTP and
  converts the response. Implication: content without a permalink
  cannot be converted on publish, and the page must render meaningfully
  when fetched anonymously.

### URLs inside the page

PDFreactor fetches the page's resources (images, stylesheets, fonts)
itself, resolving relative URLs against a **base URL**:

- Relative URLs are fine *if* the base URL is right. The plugin sets it
  to the page's public URL; administrators can override it
  (`Base Url` in settings / `pdfreactor/baseUrl`).
- Absolute URLs must be reachable **from the PDFreactor service** —
  remember it may run in a container/host that cannot see
  `localhost` or internal-only hostnames.
- ICC profiles and the generated `@page` geometry are embedded in the
  conversion request itself and need no fetching.

## Print styling with CSS Paged Media

Paged output is styled with standard CSS Paged Media — a stylesheet
discipline, not a new template language. The concepts, each with its
official chapter:

- **Page size & margins** — the `@page` rule sets the paper format and
  print margins:
  [page size and orientation](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PageSizeAndOrientation).

  ```css
  @page {
      size: A4;
      margin: 20mm 15mm;
  }
  ```

- **Headers & footers** — margin boxes (`@top-center`,
  `@bottom-center`, …) put running content into the page margins:
  [headers and footers](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PageHeaderAndFooter).
- **Page counters** — `counter(page)` / `counter(pages)` inside margin
  boxes: [page counters](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PageCounters).

  ```css
  @page {
      @bottom-center { content: counter(page) " / " counter(pages); }
  }
  ```

- **Page breaks** — `break-before`, `break-after`, `break-inside`,
  plus `orphans`/`widows` control where content may split:
  [controlling breaks](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ControllingBreaks).
- Beyond these basics, PDFreactor supports named pages, page selectors
  (`:first`, `:left`/`:right`, `:nth()`), footnotes, cross-references,
  and more — browse the
  [pagination chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#Pagination).

## Where the geometry comes from: annotation vs. stylesheet

The integrating developer can declare basic page geometry on the
content type's ViewModel:

```java
@DefaultPdfReactorConfiguration(
        paperSize = "A4",
        margin = "20mm",
        footerContent = "counter(page) \" / \" counter(pages)")
```

The plugin compiles these values into a generated `@page` user
stylesheet and injects it into the conversion — convenient defaults
without touching any CSS file.

Rule of thumb: the annotation is for the **simple frame** (paper size,
margin, a one-line header/footer). The moment you want styled headers,
named pages, per-section breaks, or print typography, write a real
**print stylesheet** and keep the annotation minimal. Both can coexist:
user stylesheets are *additive*, and your stylesheet can override the
generated rule (normal CSS cascade).

## User stylesheets, JavaScript, schedule-date preview

- **User stylesheets** ([chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#userStyleSheets))
  are injected into the conversion without the page referencing them —
  the natural home for print CSS you don't want the live site to load.
  They stack across configuration levels: deploy/site defaults, the
  ViewModel annotation's `userStyleSheetUris`, and per-call additions
  all apply together. Alternatively, link print CSS directly in your
  template with `media="print"`; PDFreactor honors print media.
- **JavaScript during layout**
  ([chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#JavaScript)):
  page JavaScript runs during conversion **by default**, so
  client-side-rendered fragments appear in the PDF. It can be switched
  off globally, per site, or per type (the annotation's tri-state
  `javaScript`) when scripts misbehave in print or cost conversion
  time.
- **Schedule-date preview**: with a document's "Show schedule-date
  preview control" enabled, editors can render the PDF preview as the
  content would look on a future date. Your templates need nothing
  special — the plugin renders through Brightspot's preview-database
  mechanism — but expect drafts/scheduled changes to show up.

## Your feedback loop

- **PDF preview** in the editor is the fast iteration cycle: edit
  template/CSS, refresh the preview. The preview is *lenient*: a
  missing image or stylesheet still renders, and a warning banner names
  every missing resource (publish and Show PDF, by contrast, fail
  closed on missing resources rather than storing a broken PDF).
- **Diagnostics**: generation failures show a parsed problem report —
  the failure kind, de-duplicated details (e.g. the offending URL), and
  the PDFreactor conversion log.
- **Debug / inspectable builds** (admin-gated per document): when you
  need to see *why* layout came out wrong, a debug build attaches
  intermediate documents and logs
  ([debugging tools](https://www.pdfreactor.com/product/doc_html/manual-lib.html#DebuggingTools)),
  and an inspectable build can be opened in the PDFreactor Inspector
  ([inspectable documents](https://www.pdfreactor.com/product/doc_html/manual-lib.html#InspectableDocuments)).
  These are never stored or published.

## Example

The dev harness in this repository is the minimal working example: the
sample `Article` type renders through `ArticleViewModel`
(`core/src/main/java/com/realobjects/brightspot/pdfreactor/devapp/`),
which emits a complete HTML page with embedded screen and print CSS and
declares its page frame via `@DefaultPdfReactorConfiguration`. Run it
per the [quick start](quick-start.md#alternative-run-the-bundled-dev-harness)
and use the seeded articles to watch every mechanism above in action —
including one article that deliberately references missing resources to
demonstrate the diagnostics.

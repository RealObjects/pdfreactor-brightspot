# PDFreactor concepts in plugin context

A shared glossary for all three audiences: each PDFreactor concept the
plugin surfaces, what the plugin does with it, and where the official
[PDFreactor manual](https://www.pdfreactor.com/product/doc_html/manual-lib.html)
covers it in depth. The [admin guide](admin-guide.md),
[integration guide](integration-guide.md), and
[template guide](template-guide.md) link here instead of re-explaining.

## Conformance profiles (PDF/A, PDF/UA, PDF/X)

Standards the output document can be required to meet:

- **PDF/A** — long-term archiving (self-contained, no external
  dependencies). [Manual: PDF/A](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFAConformance)
- **PDF/UA** — accessibility (tagged, machine-readable structure).
  [Manual: PDF/UA](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFUAConformance)
- **PDF/X** — print exchange (color-managed, print-ready).
  [Manual: PDF/X](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFXConformance)

In the plugin: the **Conformance** dropdown (global/site) or the
`conformance` annotation attribute; unset means plain PDF. PDF/A and
PDF/X variants generally require an output intent (below). Optional
**validation** ([manual](https://www.pdfreactor.com/product/doc_html/manual-lib.html#conformance-validation))
is the global-only `pdfreactor/validateConformance` setting: with it
on, a document violating the selected profile fails generation with a
clear error instead of being stored.

## Output intents & ICC profiles

An **ICC profile** is a file describing a color space precisely. An
**output intent** embeds such a profile (plus an identifier naming the
printing condition) into the PDF, telling consumers how to interpret
its colors — required by most PDF/A and PDF/X variants.
[Manual: output intent](https://www.pdfreactor.com/product/doc_html/manual-lib.html#OutputIntent)

In the plugin: ICC profiles are reusable, pickable records (uploaded
once, validated on save, referenced from any ICC field on any site).
Their bytes are embedded into the conversion request — the PDFreactor
server fetches nothing. Fields: **Output Intent Identifier** +
**Output Intent ICC Profile**, configurable globally, per site, by
deploy-time URI keys, or classpath references on the annotation.

## Color conversion & rendering intents

PDFreactor can convert page colors (RGB, by web default) into a target
space such as CMYK for print, using a target ICC profile; the
**rendering intent** chooses the conversion strategy when colors fall
outside the target gamut.
[Manual: color space conversion](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ColorSpaceConversion)

In the plugin: **Enable Automatic Color Conversion** + **CMYK ICC
Profile** (global/site UI); the rendering intent is deploy-time/JSON
only (`pdfreactor/colorConversionIntent`) since it is rarely changed
per site.

## Error policies — why preview is lenient and publish fails closed

PDFreactor **error policies** decide whether a problem aborts the
conversion or merely lands in the result's diagnostics.
[Manual: error policies](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ErrorPolicies)

The plugin sets them per path, and they are plugin-owned (not
overridable, not even via configuration JSON):

- **Editor preview** is *lenient*: a missing image or stylesheet still
  yields a PDF, and a warning banner names each missing resource — the
  editor keeps a fast feedback loop.
- **Show PDF / Convert again / publish** *fail closed* on missing
  resources (`MISSING_RESOURCE` policy): a document with broken
  resources is never stored or published; the failure surface names the
  offending URL.
- **License problems** never block: every path relaxes the `LICENSE`
  policy, so an unlicensed (evaluation-mode) service produces watermarked
  output everywhere — preview, Show PDF, Convert again, and publish.
  Evaluation mode is surfaced by the health widget and the preview banner
  rather than by a failure.

Nuance worth knowing: with JavaScript processing on (the default),
PDFreactor's engine treats a *network-unreachable* resource as a
non-fatal connection error — like a browser — whereas a reachable host
answering 404 is classified as a missing resource and trips the
fail-closed policy.

## Log levels & the conversion log

Every conversion produces a log on the PDFreactor side
([manual: logging](https://www.pdfreactor.com/product/doc_html/manual-lib.html#Logging));
`pdfreactor/logLevel` (default `WARN`) sets its verbosity. The plugin
surfaces the log excerpt in every failure UI (failure page, edit-form
status, widget banner) — it is the text to attach to support tickets.

## Debugging & inspectable documents

For deep rendering analysis PDFreactor offers **debug builds**
(intermediate documents, logs, and resources attached to the PDF —
[manual: debugging tools](https://www.pdfreactor.com/product/doc_html/manual-lib.html#DebuggingTools))
and **inspectable documents** (openable in the PDFreactor Inspector —
[manual](https://www.pdfreactor.com/product/doc_html/manual-lib.html#InspectableDocuments)).

In the plugin both are per-document checkboxes behind the
administrator gate "Allow debug/inspectable PDF builds". They affect
preview and Show PDF only; diagnostic output is never cached, stored,
stamped, or published, and publishing remains a normal production PDF
while they are on.

## Asynchronous conversion & timeouts

The Web Service supports synchronous conversion (one blocking request)
and asynchronous conversion (start, poll progress, fetch the result) —
the latter suits long documents and busy services. In the plugin:
`pdfreactor/asyncDefault` (default off) and the per-call `async`
option; polling cadence via `pdfreactor/asyncPollIntervalMillis`.
Related bounds: `clientTimeoutMillis` (HTTP),
`conversionTimeoutSeconds` (server-side), `healthTimeoutMillis` (the
short probe timeout that keeps the dashboard responsive). See the
[reference](configuration.md#connection--operation).

## Licensing & evaluation mode

Unlicensed, the service runs in **evaluation mode**: fully functional,
watermarked output.
[Manual: license key](https://www.pdfreactor.com/product/doc_html/manual-lib.html#LicenseKey) /
[evaluation mode](https://www.pdfreactor.com/product/doc_html/manual-lib.html#EvaluationMode)

The plugin detects the state with a cached background probe and shows
it in the health widget (`licensed` / `evaluation` / `unknown`) and as
an informational banner above evaluation-mode previews. A license is
installed on the service
([manual](https://www.pdfreactor.com/product/doc_html/manual-lib.html#SettingLicenseKey))
or supplied per conversion via `pdfreactor/licenseKey`. Licenses:
[RealObjects](https://www.pdfreactor.com/buy/).

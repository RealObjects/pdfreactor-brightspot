# Administrator guide

For Brightspot administrators who configure and operate the plugin in
the CMS: initial setup, per-site overrides, ICC profiles and
conformance, publish automation, monitoring, licensing, and
troubleshooting — with links into the official PDFreactor documentation
wherever a PDFreactor concept comes up. Setting-by-setting tables live
in the [configuration reference](configuration.md) — this guide is the
task-oriented walkthrough.

Before any of this, a developer installs the plugin and sets the
deploy-time settings. Those include the two connection values —
the service URL and (if used) the API key — which deliberately have
**no fields in the CMS**: they are deploy-time only, so a CMS account
can never read credentials out of a form or repoint conversions at
another service. If conversions don't run at all, that's the first
thing to ask your developer/ops about.

## Initial setup in the CMS

Open **Admin → Sites & Settings**, select the global record, and find
the **PDFreactor** cluster on the **CMS** tab. All plugin settings live
in this one cluster, grouped by section headings:

- *(top, no heading)* — Base Url, Default User Style Sheet Uris,
  Conformance, Enable JavaScript Processing
- **Document Metadata** — Creator, Subject, Keywords
- **Document Features** — Add Bookmarks, Add Links, Add Tags
- **Viewer Preferences** — Viewer Page Layout, Viewer: Fit Window,
  Viewer: Display Document Title
- **Color Management** — Output Intent Identifier, Output Intent ICC
  Profile, CMYK ICC Profile, Enable Automatic Color Conversion
- **Advanced** — Configuration Json, Allow debug/inspectable PDF builds

Plus the publish-automation kill switch, **Disable Publish Automation**
(see below).

Working the form:

- Every inheritable field carries a note explaining it, and — while the
  field is blank — the value **"Currently in effect (inherited)"**. You
  can always see what a blank field actually does before deciding to
  override it.
- **Save-time validation** catches bad input where possible: invalid
  JSON in Configuration Json and non-ICC uploads in profile records are
  rejected with a field error at save, not with a conversion failure
  later.
- After saving the connection-side basics, confirm the service link via
  the **health widget** (below): it should read
  **UP — PDFreactor 12.6.0** plus the license state.

## Per-site configuration

Everything in the PDFreactor cluster can also be set on an individual
site's Settings record. The rule of thumb:

- a value set on the site **overrides** the global value for content of
  that site;
- a blank field **inherits** — the note shows you what is inherited;
- the stylesheet list **replaces** the global list when non-empty;
- the Configuration Json **merges over** the global JSON (objects
  combine, conflicting simple values take the site's).

The precise model is the
[precedence section](configuration.md#the-precedence-model) of the
reference. Typical per-site uses: a different base URL or print
stylesheet per brand site, site-specific PDF metadata, or a per-site
conformance/ICC setup.

## ICC profiles & conformance

These settings make PDFs reproduce color predictably and meet archival
or print standards. Each concept links to the official PDFreactor
chapter — none of this needs to be configured for ordinary
office-quality PDFs.

- **ICC profiles** describe a color space precisely. In the plugin they
  are reusable records: pick one in any ICC field via the standard
  reference picker (search, or **Create New** to upload a `.icc`/`.icm`
  file — uploads that are not real ICC profiles are rejected on save).
  The same profile can serve multiple fields and sites. Profile bytes
  travel inside the conversion request, so the PDFreactor server needs
  no access to the CMS.
- **Output intent** ([official chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#OutputIntent)):
  the profile + identifier embedded into PDF/A or PDF/X output that
  states which printing condition the colors are meant for. Set
  identifier and profile together.
- **Color conversion** ([official chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#ColorSpaceConversion)):
  with **Enable Automatic Color Conversion** on and a **CMYK ICC
  Profile** picked, page colors are converted for print output.
- **Conformance** ([PDF/A](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFAConformance) /
  [PDF/UA](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFUAConformance) /
  [PDF/X](https://www.pdfreactor.com/product/doc_html/manual-lib.html#PDFXConformance)):
  the **Conformance** dropdown selects the standard the output must
  meet — PDF/A for archiving, PDF/UA for accessibility, PDF/X for
  print exchange. PDF/A and PDF/X variants generally want an output
  intent configured. Optional validation
  ([chapter](https://www.pdfreactor.com/product/doc_html/manual-lib.html#conformance-validation))
  is the deploy-time `pdfreactor/validateConformance` setting (ask your
  developer); with it on, a violating document fails generation with a
  clear error instead of being stored.

## Publish automation

Publishing content can generate its PDF automatically, in the
background. Three independent switches must all allow it:

1. **Developer marker** — the content type opted in (a given for the
   types your developers wired up; nothing for you to do here).
2. **Site toggle** — the **Disable Publish Automation** checkbox in the
   PDFreactor cluster. Checked globally it stops publish PDFs
   everywhere; checked on one site, only there. This is your kill
   switch when the PDFreactor service is down for maintenance or
   misbehaving.
3. **Editor checkbox** — each document's **Generate PDF on publish**
   (checked by default); editors can opt individual documents out.

When a publish-time generation **fails**, nothing breaks for the
publish itself (the content goes live); instead:

- the document's **Generated PDF Status** shows the failure,
- a red banner appears on the document's PDF widget,
- and a **PDFreactor publish failure** notification fires — anyone can
  subscribe to it through their standard Brightspot notification
  settings (Profile → Notifications).

## Monitoring & operations

- **Health widget**: add the **PDFreactor Service** widget to any
  dashboard via the standard dashboard editor. It shows one status
  line and updates itself (~30 s) while the dashboard is open:
  - **UP — PDFreactor 12.6.0 (licensed)** — service reachable, licensed.
  - **UP — … (evaluation — …)** — reachable but unlicensed: output is
    watermarked. See Licensing below.
  - **UP — … (license state unknown)** — reachable; the license probe
    was inconclusive (e.g. it timed out). Usually resolves on a later
    poll.
  - **⚠ DOWN — …** (red) — service unreachable or failing; the line
    carries the reason.
- **Failure notifications**: subscribe to the publish-failure topic via
  your notification settings (see above).
- **Conversion log**: every failure surface (failure page, edit-form
  status, widget banner) includes a "Conversion log" disclosure with
  PDFreactor's own log for the conversion — that text is what to copy
  into a support ticket.
- **Stored-PDF retention**: the plugin keeps the most recent stored
  PDFs per document (deploy-time `pdfreactor/generatedPdfRetention`,
  default 20) and prunes older ones automatically.

## Licensing

Without a license the PDFreactor service runs in **evaluation mode**:
everything works, but PDFs carry a watermark. You see this as the
"(evaluation — …)" suffix in the health widget and as an informational
banner above PDF previews.

A license is supplied in one of two ways — neither is editable in the
CMS:

- installed on the PDFreactor service itself
  ([official instructions](https://www.pdfreactor.com/product/doc_html/manual-lib.html#SettingLicenseKey)), or
- via the deploy-time `pdfreactor/licenseKey` setting, which sends the
  key with each conversion (ask your developer/ops).

Licenses are obtained from [RealObjects](https://www.pdfreactor.com/buy/).

## Troubleshooting

Keyed by **where the failure shows up**:

| Where it surfaces | Looks like | Likely cause | Remedy | Who fixes it |
| --- | --- | --- | --- | --- |
| Health widget | ⚠ DOWN — connection error | Service down/unreachable, wrong service URL | Check the service and the deploy-time URL | Ops / developer |
| Health widget | UP … (evaluation) | No license installed | Install/supply license (see Licensing) | Ops |
| Preview pane | Warning banner over a rendered PDF | Missing resource (broken image/stylesheet URL) in the content | Fix the referenced asset; the banner names the URL | Editor / template developer |
| Preview pane | Evaluation banner | Evaluation mode | See Licensing | Ops |
| Generate failure page (after Show PDF / Convert again) | Error + remedy + "Technical details" / "Conversion log" | Broken resource (fails closed), invalid configuration, conformance violation, service error | Follow the remedy line; the log names the offender | Editor / admin / developer per the message |
| Edit form: Generated PDF Status | "Failed: …" after a publish | Publish-time generation failed (same causes as above) | Open the document's PDF widget for the full reason; fix and republish or use Convert again | Editor / admin |
| PDF widget | Red publish-failure banner | Same as above | Same as above | Editor / admin |
| Notification | PDFreactor publish failure | Same as above | Same as above | Subscriber |
| Sites & Settings save | Field error on Configuration Json / ICC file | Invalid JSON / not an ICC profile | Correct the value — nothing was saved | Admin |

For rendering problems that need a deeper look, switch on **Allow
debug/inspectable PDF builds** (Advanced section). Each affected
document then offers two extra checkboxes (**Debug build** /
**Inspectable build**) whose preview and Show PDF output are diagnostic
builds for RealObjects support and the PDFreactor Inspector
([debugging tools](https://www.pdfreactor.com/product/doc_html/manual-lib.html#DebuggingTools)).
Diagnostic output is never stored or published, and publishing is
unaffected while the toggles are on. Turn the gate off when done — it
hides the toggles again everywhere.

## What your editors see

For training purposes, the editor-visible surface is:

- **PDF preview** — a preview-pane mode rendering the draft as a paged
  PDF, with refresh, share, and (where enabled per document) a
  schedule-date control that previews the content as of a future date.
- **PDF widget** (right rail) — **Show PDF** opens the published
  content's PDF in a new tab (generating it the first time, then
  serving the stored copy while content is unchanged); a **Stored
  PDF** line shows date, page count, and file size with **Download**
  and **Convert again** actions.
- **PDFreactor cluster** (edit form) — the **Generate PDF on publish**
  checkbox (on by default), the schedule-date preview opt-in,
  read-only **Generated PDF Date/Status**, and the per-document
  overrides for metadata, document features, and viewer preferences.

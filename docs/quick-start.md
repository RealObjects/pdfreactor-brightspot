# Quick start

For Brightspot developers: from zero to a PDF preview in your own
Brightspot project in about 15 minutes. The full detail behind each
step is in the [integration guide](integration-guide.md) and the
[configuration reference](configuration.md).

If you just want to *see* the plugin without touching your own project,
skip to [the bundled dev harness](#alternative-run-the-bundled-dev-harness).

## 1. Run PDFreactor

The plugin talks to a [PDFreactor Web Service](https://www.pdfreactor.com/)
over HTTP. If you don't already have one, the official Docker image gets
you a service in one line:

```sh
docker run -d -p 9423:9423 realobjects/pdfreactor:12.7.0
```

The service answers at `http://localhost:9423/service/rest` (check
`GET /service/rest/status`). Without a license it runs in **evaluation
mode** — fully functional, but output carries a watermark. Two ways to
run licensed, in plugin terms:

- Install the license on the PDFreactor service itself — see the
  official [license key documentation](https://www.pdfreactor.com/product/doc_html/manual-lib.html#LicenseKey).
- Or supply it CMS-side via the deploy-time `pdfreactor/licenseKey`
  setting, which sends the key with each conversion.

Licenses and trials are available from
[RealObjects](https://www.pdfreactor.com/buy/).

## 2. Build the plugin and add it to your project

The plugin is distributed as source — clone and build (the Gradle
wrapper is committed; no local Gradle needed):

```sh
git clone https://github.com/RealObjects/pdfreactor-brightspot.git
cd pdfreactor-brightspot
./gradlew :plugin:build
```

Then wire it into your Brightspot project. Three equivalent paths —
pick whichever fits your infrastructure. All of them additionally need
`mavenCentral()` among your repositories (the PDFreactor Java client
resolves from Maven Central).

**Path A — local Maven repository.** Install the plugin locally:

```sh
./gradlew :plugin:publishToMavenLocal
```

and consume it:

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

**Path B — Gradle composite build.** No artifact step; point your
`settings.gradle` at the clone, substituting the published coordinates
with the clone's `:plugin` project:

```gradle
// settings.gradle
includeBuild('../pdfreactor-brightspot') {
    dependencySubstitution {
        substitute module('com.realobjects.brightspot:pdfreactor-brightspot') using project(':plugin')
    }
}
```

```gradle
// build.gradle — the substituted coordinates (no version needed)
dependencies {
    api 'com.realobjects.brightspot:pdfreactor-brightspot'
}
```

**Path C — vendored JAR.** Copy
`plugin/build/libs/plugin-1.0.0-SNAPSHOT.jar` into your project (e.g.
`libs/`) and depend on it directly:

```gradle
// build.gradle
dependencies {
    api files('libs/plugin-1.0.0-SNAPSHOT.jar')
}
```

Caveat for path C: a file dependency carries no metadata, so the
plugin's own dependencies (notably
`com.pdfreactor.webservice:pdfreactor-java-client:12.6.0`) must already
be on your classpath — declare that one yourself; the Brightspot
artifacts are there in any Brightspot project.

## 3. Minimum configuration

Exactly one setting is required — where to find the PDFreactor service.
Add it to your Dari settings (e.g. Tomcat `context.xml`):

```xml
<Environment name="pdfreactor/serviceUrl" type="java.lang.String"
             value="http://localhost:9423/service/rest" override="false" />
```

Everything else defaults sensibly; the
[configuration reference](configuration.md) lists all 32 keys.

## 4. Mark a content type

Opt a content type into PDF rendering by implementing the
`HasPdfRendering` marker:

```java
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;

public class Article extends Content implements Directory.Item, HasPdfRendering {
    // ... unchanged
}
```

That's the developer-level gate: marked types get the PDFreactor
cluster on their edit form, the right-rail PDF widget, and
publish-time generation (which editors and admins can each still turn
off — see the [admin guide](admin-guide.md)).

Optionally give the type its page geometry on its ViewModel:

```java
@DefaultPdfReactorConfiguration(
        paperSize = "A4",
        margin = "20mm",
        footerContent = "counter(page) \" / \" counter(pages)")
public class ArticleViewModel extends ViewModel<Article> implements ... {
```

## 5. See it work

Open a marked content item in the CMS:

- Switch the preview to **PDF** — the article renders as a paged PDF
  right in the preview pane (running unlicensed, an informational
  banner explains the evaluation watermark).
- Use **Show PDF** in the right-rail PDF widget — it opens the
  published content's PDF in a new tab, generating it on first use and
  serving the stored copy afterwards.
- **Publish** — a PDF is generated in the background and appears as the
  stored PDF in the widget, with date, page count, and size.

## Alternative: run the bundled dev harness

This repository doubles as a runnable example: `core/` + `web/` are a
small Brightspot app with a sample `Article` type already wired to the
plugin.

```sh
./gradlew build
docker compose up -d
```

Then open `http://localhost/cms/_seed-ui-test` once — it seeds sample
articles and answers with a JSON containing the test username and a
freshly generated password plus direct edit-page links. Log in at
`http://localhost/cms` and explore.

The harness works out of the box in evaluation mode (watermarked). To
run it licensed, copy `docker-compose.override.yml.example` to
`docker-compose.override.yml` and put your license key at the repo root
as `licensekey.xml` (gitignored) — the override mounts it into the
PDFreactor container.

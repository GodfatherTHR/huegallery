# HueGallery

A server-rendered image gallery built with Jakarta Servlet 6, JSP/JSTL and Oracle Database 21c.
Users register, upload images (stored as Oracle BLOBs), tag them, browse/search by tag, find
visually similar images using a colour histogram implemented in plain Java, and ask a local Ollama
model to find images from a natural-language prompt.

No JavaScript framework, no CSS framework, no OpenCV, no ML library in the WAR, no filesystem image
storage. The optional AI assistant talks to a local Ollama process over HTTP.

> **Design deep dive:** [docs/DESIGN.md](docs/DESIGN.md) contains the use case, ERD, activity,
> sequence, class and architecture diagrams plus the AI tool contract.
> **Proposal coverage:** [docs/PROPOSAL_COVERAGE.md](docs/PROPOSAL_COVERAGE.md) maps every
> requirement to the code and tests.

---

## 1. Feature summary

- Registration, login, logout with bcrypt password hashes and session-based authentication.
- Multipart upload with server-side decoding, MIME verification, thumbnailing and histogram storage
  in one transaction.
- Many-to-many tags, normalised on input, with autocomplete, tag cloud, ANY/ALL search and
  owner-only tag editing.
- Gallery grid with thumbnails, lazy loading and pagination (24 per page).
- Detail page with full image, lightbox, metadata, tag chips, dominant-colour swatches,
  "Find similar" results with percentage scores, owner controls.
- Binary endpoints `/image/{id}` and `/thumb/{id}` with ETag/304 and 8 KB streaming.
- "Search by example": upload a temporary query image (never saved) and rank the gallery.
- Natural-language AI search through a local Ollama model, with tag/colour/text tools and a
  deterministic keyword/colour fallback when Ollama is offline.
- Real WebP decoding through the pure-Java TwelveMonkeys ImageIO reader.
- Optional PL/SQL helpers: orphan-tag procedure, tag-count function and image-delete audit trigger.
- `ReindexHistograms` command-line backfill utility.

## 2. Technology

| Layer | Choice |
| --- | --- |
| UI | HTML5, CSS3, vanilla JavaScript (ES2017), JSP + JSTL 3 (`jakarta.tags.*`) |
| Backend | Java 24, Jakarta Servlet 6.0, Tomcat 10.1/11 (tested with 11.0) |
| Database | Oracle Database 21c (tested with 21c XE, PDB `XEPDB1`) |
| JDBC | `com.oracle.database.jdbc:ojdbc11` 23.4.0.24.05 |
| Pool | HikariCP 5.1.0 |
| JSON | Gson 2.11.0 |
| Passwords | jBCrypt 0.4 |
| WebP decode | TwelveMonkeys `imageio-webp` 3.12.0 |
| AI assistant (optional) | Ollama over local HTTP; qwen3.5:0.8b tool calling, qwen2:1.5b prompt fallback |
| Logging | SLF4J API + slf4j-simple |
| Build / test | Maven (WAR), JUnit 5 |

`pom.xml` uses `maven.compiler.release=24`, matching the proposal's Java 24 target. Build with
JDK 24 or newer.

## 3. Project layout

```
pom.xml
db.properties.example
sql/01_schema.sql          tables (idempotent drop + create)
sql/02_indexes.sql         secondary indexes (idempotent)
sql/03_seed.sql            optional demo user
sql/04_plsql.sql           optional procedures, function and delete trigger
docs/DESIGN.md             UML and architecture diagrams
docs/PROPOSAL_COVERAGE.md  requirement-to-code mapping
src/main/java/com/gallery/
  model/                   User, Image, Tag, SimilarImage, ImageBinary, PageResult
  dao/                     UserDao, ImageDao, TagDao, HistogramDao
  service/                 AuthService, ImageService, TagService, SimilarityService
  servlet/                 controllers (home, auth, upload, detail, binary, search, AI)
  filter/                  CharacterEncodingFilter, AuthFilter
  util/                    DbUtil, ImageUtil, ColourHistogram, HistogramComparator,
                           HistogramIntersectionComparator, TagParser, CsrfUtil,
                           ValidationUtil, WebUtil, Flash, ValidationException
  ai/                      AiSearchService, AiToolService, OllamaClient,
                           AiToolDefinitions, ColourNames, AiSearchResult, AiImageCard
  tool/ReindexHistograms   offline histogram backfill
src/main/resources/
  ai.properties            committed AI defaults (no secrets)
  db.properties            local Oracle credentials (git-ignored)
src/main/webapp/
  WEB-INF/web.xml
  WEB-INF/views/           JSPs (not directly reachable), including ai-search.jsp
  assets/css/style.css
  assets/js/{app,chips,upload,ai-search}.js
src/test/java/com/gallery/{util,ai}  JUnit 5 tests
```

## 4. Prerequisites

- JDK 24 (the build targets Java 24).
- Maven 3.9+.
- Oracle Database 21c (XE or EE) with a pluggable database such as `XEPDB1`.
- Tomcat 10.1 or 11 installed (not Tomcat 9 or 10.0, which use the older `javax` namespace).
- Ollama (optional, for AI search): install from <https://ollama.com>, then pull
  `qwen3.5:0.8b` and `qwen2:1.5b`.

## 5. Oracle setup

### 5.1 Create the schema

Connect as a privileged user **to the PDB** (`XEPDB1`), not to the CDB root, then create a local
user. The application needs its own schema because the scripts create tables.

```sql
-- sqlplus sys/<sys-password>@//localhost:1521/XEPDB1 as sysdba
CREATE USER gallery IDENTIFIED BY "changeit"
  QUOTA UNLIMITED ON users;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE,
      CREATE PROCEDURE, CREATE TRIGGER TO gallery;
```

`QUOTA UNLIMITED ON users` is required for the BLOB data. `CREATE PROCEDURE` and `CREATE TRIGGER`
are only needed if you install the optional PL/SQL helpers in `04_plsql.sql`.

### 5.2 Run the scripts, in order, as `GALLERY`

```bash
sqlplus gallery/changeit@//localhost:1521/XEPDB1 @sql/01_schema.sql
sqlplus gallery/changeit@//localhost:1521/XEPDB1 @sql/02_indexes.sql
sqlplus gallery/changeit@//localhost:1521/XEPDB1 @sql/03_seed.sql   # optional
sqlplus gallery/changeit@//localhost:1521/XEPDB1 @sql/04_plsql.sql  # optional PL/SQL
```

`01_schema.sql` is safe to re-run: it drops the five application tables in FK order (including
their data) before creating them. Do not run it against a schema that contains data you want to
keep.

### 5.3 Service name note (important for Oracle XE)

Oracle 21c XE is a container database. The application schema lives in the **pluggable** database
`XEPDB1`, so the JDBC URL must use `XEPDB1` as the service name:

```
jdbc:oracle:thin:@//localhost:1521/XEPDB1
```

Using `XE` connects to the CDB root and the `GALLERY` user will not be visible. If `lsnrctl status`
does not list `xepdb1`, see Troubleshooting.

The seed account is:

```
username: demo
password: DemoPass123!
```

Change or remove it before exposing the application.

## 6. Configuration and deployment

### 6.1 Database credentials

```bash
cp db.properties.example src/main/resources/db.properties
```

Edit the file:

```properties
db.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
db.username=gallery
db.password=changeit
db.driver=oracle.jdbc.OracleDriver
db.pool.maximumPoolSize=10
db.pool.minimumIdle=2
db.pool.connectionTimeoutMs=10000
```

`src/main/resources/db.properties` is git-ignored. **Never commit real credentials.** Values can
also be overridden at runtime by system properties (`db.url`, `db.username`, `db.password`) or
environment variables (`GALLERY_DB_URL`, `GALLERY_DB_USERNAME`, `GALLERY_DB_PASSWORD`), which is
useful for containers and CI.

### 6.2 Build and deploy

```bash
mvn clean package
```

This runs the unit tests and produces `target/image-gallery.war`.

```bash
cp target/image-gallery.war "$CATALINA_BASE/webapps/"
"$CATALINA_HOME/bin/startup.sh"     # or startup.bat on Windows
```

Open `http://localhost:8080/image-gallery/`. The context path comes from the WAR name; rename it
to `ROOT.war` to deploy at `/`.

For a quick standalone check on Windows:

```bat
set CATALINA_HOME=C:\path\to\apache-tomcat-10.1
set CATALINA_BASE=C:\path\to\your-base
copy target\image-gallery.war %CATALINA_BASE%\webapps\
%CATALINA_HOME%\bin\catalina.bat run
```

If Tomcat runs with a small heap, leave the defaults; uploads are capped at 8 MB and images are
downscaled before histogram computation.

### 6.3 Local Ollama assistant (optional)

The **AI search** page turns a natural-language prompt into real database searches. It uses the
local Ollama HTTP API, so prompts and images never leave the machine.

```bash
ollama serve
ollama pull qwen3.5:0.8b
ollama pull qwen2:1.5b
```

Ollama reports qwen2:1.5b as **completion only** and qwen3.5:0.8b as **tools + thinking + vision**.
The app asks `/api/show` for each model's capabilities and automatically uses:

1. **qwen3.5:0.8b** for the tool-calling loop, because qwen2:1.5b cannot call tools;
2. **qwen2:1.5b** in prompt/JSON mode when only a completion model is installed;
3. a deterministic keyword/colour search when Ollama is offline (the page shows a warning).

Defaults live in `src/main/resources/ai.properties` (committed, no secrets):

```properties
ai.ollama.url=http://127.0.0.1:11434
ai.model=qwen2:1.5b
ai.tool-model=qwen3.5:0.8b
ai.prefer-tools=true
ai.think=false
ai.timeout-seconds=180
ai.max-tool-rounds=4
ai.max-results=12
```

Each value can be overridden with a system property of the same name or an environment variable:
`GALLERY_OLLAMA_URL`, `GALLERY_AI_MODEL`, `GALLERY_AI_TOOL_MODEL`, `GALLERY_AI_PREFER_TOOLS`,
`GALLERY_AI_THINK`, `GALLERY_AI_TIMEOUT_SECONDS`, `GALLERY_AI_MAX_TOOL_ROUNDS`,
`GALLERY_AI_MAX_RESULTS`.

Thinking is disabled by default because it makes the 0.8B model much slower on CPU. Set
`ai.think=true` if you want more deliberate tool selection. The first model load can take a minute;
after that Ollama keeps it warm for 30 minutes.

## 7. How the colour histogram and similarity work

### 7.1 Histogram construction (`ColourHistogram`)

1. Decode the image with `ImageIO` (the format is detected from the bytes, never from the filename
   or the request `Content-Type`). JPEG, PNG and GIF use the JDK readers; WebP uses the
   TwelveMonkeys `imageio-webp` reader on the classpath.
2. Composite transparent pixels onto **white** and force `TYPE_INT_RGB`.
3. Downscale proportionally with bilinear interpolation so the longest side is at most **128 px**.
   This is deterministic and keeps the computation fast; it also makes the result largely
   independent of the original resolution.
4. Quantise each channel to `LEVELS = 4` levels. For an 8-bit channel value `c`:
   `level = floor(c * 4 / 256)`, giving levels 0–3 and **64 bins**.
5. Bin index:
   ```
   index = rLevel * 4 * 4 + gLevel * 4 + bLevel
   ```
6. Count pixels per bin and divide by the total number of sampled pixels, so the vector sums to
   **1.0**. This removes any dependence on image size.
7. Serialise as 64 comma-separated values with six decimal places and store in
   `IMAGE_HISTOGRAMS.bins` (about 575 characters, well under `VARCHAR2(1000)`).

### 7.2 Similarity metric (`HistogramComparator`)

The metric is behind an interface. The default implementation is **histogram intersection**:

```
S(a, b) = Σ min(a[i], b[i])        for i = 0..63
```

Because both vectors are normalised, `S` lies in `[0, 1]`: `1.0` means identical colour
distribution, `0.0` means no shared bins. The UI displays `round(S * 100)%`.

`SimilarityService.findSimilar(imageId, limit = 12)`:

1. Load the target histogram.
2. Load every other histogram in one query (`image_id` + `bins` only).
3. Parse and score them in Java.
4. Sort descending, take the top N, then load metadata for just those ids in one query.
5. Return `SimilarImage` objects with the score.

This is an O(N) in-memory scan. It is deliberately simple; see Known limitations.

### 7.3 Dominant colours (bonus)

The three highest-mass bins are converted to their centre colours:
`(level + 0.5) * 256 / 4` gives channel values 32, 96, 160 or 224. These three swatches are shown on
the detail page. Solid images naturally produce only one non-empty bin.

### 7.4 Search by example (bonus)

The uploaded query image is decoded and hashed in memory, compared against all gallery
histograms, and discarded. No row, BLOB or tag is written.

### 7.5 Reindexing

```bash
# Windows, from the exploded webapp directory
java -cp "webapps\image-gallery\WEB-INF\classes;webapps\image-gallery\WEB-INF\lib\*" \
     com.gallery.tool.ReindexHistograms

# Recompute everything
java -cp "..." com.gallery.tool.ReindexHistograms --all
```

Run it as the same OS user that can read the configured `db.properties`; the tool uses the same
`DbUtil` and connection pool settings. It only touches images; tags and users are unchanged.

## 8. Assumptions

- A single Oracle schema owns all application tables; there is no multi-tenant support.
- The image gallery is public to read; only upload, delete, tag editing and "my images" require a
  session. Search by example is public but still CSRF-protected.
- The full-resolution BLOB is the original uploaded bytes. Thumbnails are always opaque JPEGs, even
  for PNG/GIF/WebP input, because that is the most predictable format for the gallery grid.
- **WebP:** decoding is provided by the pure-Java TwelveMonkeys `imageio-webp` reader. JPEG, PNG,
  GIF and WebP uploads all decode; animated GIFs are treated as their first frame.
- **AI search:** the assistant is a local Ollama process, not a cloud call. Results are always
  database rows returned by the tools; the model writes only the short answer sentence.
- **AI model quality:** a 0.8B model is small. It can miss a tag or choose a broader tool than you
  expected, which is why the app also runs a deterministic keyword/colour fallback when a model
  returns nothing or Ollama is offline.
- EXIF orientation is not applied; images are displayed as decoded.
- Usernames are case-sensitive in Oracle and unique. Passwords are 8–72 characters (the bcrypt
  input limit).
- Tags are ASCII: lower-case letters, digits, spaces and hyphens, max 40 characters, max 10 per
  image. Anything else is stripped by `TagParser`.
- Pagination is fixed at 24 images per page.
- Similarity results include 0% matches (sorted last) rather than filtering them out, so a small
  gallery always shows the top N rows. The UI labels the scores honestly.
- Timestamps come from the database (`SYSTIMESTAMP`), not the application server.
- Local demo deployments use HTTP, so the session cookie is `HttpOnly` + `SameSite=Lax` but not
  `Secure`. Add `Secure` when serving over HTTPS.

## 9. Known limitations

- **Similarity is a linear in-memory scan.** Every "Find similar" request loads and parses every
  histogram. This is fine for hundreds or a few thousand images; it is not an indexed/ANN search.
  The interface exists so the metric can be swapped, but the scan itself would need a vector
  database or Oracle-specific indexing for scale.
- **Coarse quantisation.** With four levels per channel, perceptually different shades can land in
  different bins and score 0. For example, pure red `(255,0,0)` and a dark red `(128,0,0)` are in
  adjacent bins; pure red against dark red is 0% with pure histogram intersection. Reds such as
  `(255,0,0)` and `(200,0,0)` share a bin and score 100%. Real photographs spread mass across many
  bins, so they behave more sensibly than solid-colour extremes. This is a deliberate consequence
  of the prescribed 4×4×4 quantisation.
- No EXIF orientation, ICC colour management, or animated GIF playback.
- **AI search latency:** the first request after Ollama starts can take a minute while the model
  loads. Later requests are faster, but a CPU-only machine can still take tens of seconds. The page
  shows progress and a visible "Still working" message.
- **AI search is not a general vision model:** it does not send image bytes to the model. It uses
  the prompt, tags, titles and colour histograms. A prompt such as "a dog on a beach" only works if
  the gallery has matching tags, title/description words, or a similar colour palette.
- **Tool loop is bounded:** at most `ai.max-tool-rounds` model/tool exchanges per request, so a
  confused model cannot loop forever. If a later model call fails, the results already found are
  kept and the page shows a short warning.
- No rate limiting, email verification, password reset, account lockout, image moderation or
  antivirus scanning. Do not expose the demo directly to the internet.
- The full-image stream holds a JDBC connection while the client downloads. The 8 MB cap keeps the
  risk bounded; a production system would stage the BLOB to a temporary stream.
- One CSRF token per session (rotated at login), not one per form. Tokens are compared in constant
  time and validated on every POST.
- There is no user deletion or "delete my account" flow; deleting a user would cascade to images via
  the foreign key, but there is no UI for it.
- The tag search uses dynamically generated bind placeholders (`IN (?,?,?)`) because JDBC has no
  array binding for this case; tag values are always bound, never concatenated.
- `ReindexHistograms` must be run manually; there is no scheduled job or admin UI.

## 10. Security notes

- Every SQL statement is a `PreparedStatement`; values are bound, never concatenated.
- Every POST is protected by a session CSRF token (`_csrf`) validated server-side.
- All dynamic JSP output uses `<c:out>` or `fn:escapeXml`, including attributes and alt text.
- The upload type is verified by decoding, not by filename or `Content-Type`; stored filenames are
  sanitised to `[A-Za-z0-9._-]`.
- Uploads are capped at 8 MB, and image dimensions are checked (40 megapixels) before a full decode
  to reduce decompression-bomb risk.
- Passwords are bcrypt-hashed with cost 12 and never logged. The session is replaced at login to
  prevent session fixation.
- Deletion and tag editing re-check ownership server-side, not just in the UI.
- 403/404/500 pages are generic and do not expose stack traces or BLOB contents.
- AI prompts are capped at 500 characters; the `/api/ai-search` endpoint is CSRF-protected; model
  output is escaped by the JSP and the JavaScript uses `textContent`; prompts are not persisted.
- The Ollama URL defaults to `127.0.0.1`; do not point it at an untrusted remote service.
- AI results are always re-read from Oracle by image id. The model cannot invent a card, even if it
  invents a sentence.
- `db.properties` is git-ignored; use environment variables or a secrets manager in production.

## 11. HTTP endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/`, `/home` | Gallery + tag cloud (24/page) |
| GET/POST | `/login`, `/register` | Authentication |
| POST | `/logout` | End session |
| GET/POST | `/upload` | Upload form / pipeline (auth) |
| GET | `/my-images` | Owner's images (auth) |
| GET | `/photo/{id}` | Detail page, tags, similar images |
| GET | `/image/{id}` | Full BLOB, ETag/304, streamed |
| GET | `/thumb/{id}` | JPEG thumbnail BLOB, ETag/304 |
| POST | `/delete` | Owner-only delete (auth) |
| GET/POST | `/edit-tags` | Owner-only tag editing (auth) |
| GET | `/search?tags=a,b&mode=any\|all` | Tag search |
| GET | `/api/tags?q=...` | JSON autocomplete |
| GET/POST | `/search-by-example` | Temporary query image, not saved |
| GET/POST | `/ai-search` | AI search page (GET works without JavaScript) |
| POST | `/api/ai-search` | JSON AI search for progressive enhancement (CSRF-protected) |

## 12. Database schema

- `USERS(user_id identity PK, username unique, password_hash, created_at)`
- `IMAGES(image_id identity PK, user_id FK, title, description, file_name, mime_type, file_size,
  width, height, image_data BLOB, thumbnail BLOB, uploaded_at)`
- `TAGS(tag_id identity PK, name unique, lower-case)`
- `IMAGE_TAGS(image_id, tag_id, PK(image_id, tag_id), both FKs ON DELETE CASCADE)`
- `IMAGE_HISTOGRAMS(image_id PK/FK ON DELETE CASCADE, bins VARCHAR2(1000), computed_at)`
- Optional `IMAGE_DELETE_LOG(log_id identity PK, image_id, title, owner_user_id, mime_type,
  file_size, deleted_at)` written by `TRG_IMAGES_DELETE_LOG`.

Indexes: `IMAGE_TAGS(tag_id)`, `IMAGES(user_id)`, `IMAGES(uploaded_at DESC, image_id DESC)`.

Optional PL/SQL (`sql/04_plsql.sql`): `PRUNE_ORPHAN_TAGS(p_removed OUT NUMBER)` removes unused tags,
`TAG_IMAGE_COUNT(p_tag_id)` returns a tag's image count, and `TRG_IMAGES_DELETE_LOG` writes an audit
row whenever an image is deleted. The Java application also cleans orphan tags inline, so the web
app works without the PL/SQL script; only the audit log depends on the trigger.

Oracle-specific handling: `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY` instead of `LIMIT`, `MERGE`
for tag upserts, explicit auto-commit off/commit/rollback for multi-statement operations, BLOBs
written with `setBinaryStream` and read with `getBinaryStream`, and no full-size BLOB in list
queries.

## 13. Tests

```bash
mvn clean package
```

Unit tests (31 total, JUnit 5, programmatically generated images, no binary fixtures):

- `ColourHistogramTest` — solid red occupies one bin; normalisation sums to 1; the same synthetic
  image at two sizes produces nearly identical histograms; serialisation round-trips within the
  column limit; dominant colours come from the largest bins.
- `HistogramComparatorTest` — identical images score 1.0; red vs blue scores 0.0; a red shade beats
  blue; symmetry and bounds; mismatched vectors score 0.
- `TagParserTest` — casing, whitespace collapsing, illegal characters, deduplication, 10-tag limit,
  40-character truncation, lenient query mode.
- `ImageUtilTest` — 300 px thumbnail cap, no upscaling, white matte for transparency, non-image
  rejection, format detection, filename sanitisation, and a real WebP fixture decoded through the
  TwelveMonkeys reader.
- `ColourNamesTest` — colour words map to the expected histogram bins, pure base colours bridge the
  coarse 4×4×4 quantisation, `#rrggbb` values work, multi-colour targets are averaged, and colour
  words are extracted from free text.

The project was also exercised end-to-end against a local **Oracle 21c XE / XEPDB1** and
**Tomcat 11.0** with real multipart uploads. Checks included registration/login/logout, CSRF
rejection, owner-only deletion, BLOB byte-for-byte round trips, thumbnails, 304 ETag responses,
tag reuse and cloud counts, ANY/ALL search, similarity ranking, search by example, cascade deletion,
orphan tag cleanup, forced mid-transaction rollback (zero partial rows), the reindex tool, a real
WebP upload (stored as `image/webp`, thumbnail as JPEG), the PL/SQL delete trigger, and the AI tool
loop with qwen3.5:0.8b (tag, text, colour and find-similar tools).

## 14. Troubleshooting

### `ORA-12514: listener does not currently know of service requested`

The PDB is not registered with the listener. Check the listener and force registration:

```bash
lsnrctl status
```

```sql
-- as SYSDBA
ALTER SYSTEM SET LOCAL_LISTENER='(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=1521))' SCOPE=MEMORY;
ALTER SYSTEM REGISTER;
```

Then `lsnrctl services` should list `xepdb1` with status `READY`. On Windows the listener service is
`OracleOraDB21Home1TNSListener`; start it with `Start-Service OracleOraDB21Home1TNSListener`.

### `No suitable driver`

The WAR includes `ojdbc11`, and `DbUtil` explicitly loads `oracle.jdbc.OracleDriver` before creating
the pool. If you changed the `db.driver` value, check it matches a class present in `WEB-INF/lib`.

### `db.properties was not found on the classpath`

The real file must be at `src/main/resources/db.properties` before `mvn package`, or the pool must
be configured through the environment variables. The example file is intentionally named
`db.properties.example` so it can be committed.

### AI search says `Ollama is not reachable`

Start the local service and keep it running:

```bash
ollama serve
```

The default URL is `http://127.0.0.1:11434`. If Ollama runs elsewhere, change `ai.ollama.url` or set
`GALLERY_OLLAMA_URL`. The app still performs a local keyword/colour search when Ollama is offline,
and shows a warning on the result page.

### AI search says a model is not installed

Pull the model named in the message:

```bash
ollama pull qwen3.5:0.8b
ollama pull qwen2:1.5b
```

qwen2:1.5b is completion-only, so the app uses qwen3.5:0.8b for tool calling and keeps qwen2:1.5b
as the prompt-mode fallback.

### The first AI search is slow

The first request loads the model into memory (often a minute on CPU). Ollama keeps it warm for
30 minutes. You can raise `ai.timeout-seconds`; the page's status message explains the wait.

### AI search shows "the local model stopped before writing its summary"

A small model can emit a malformed later tool call. The app keeps the results already found and
shows a short warning instead of discarding them. Retry with a shorter prompt or increase
`ai.max-tool-rounds`.

### 404 on `/image/{id}` after an upload

Deleting an image removes its rows through `ON DELETE CASCADE`; stale browser tabs will 404. This is
expected.

## 15. Manual demo

1. Create the `GALLERY` schema, run `sql/01_schema.sql`, `sql/02_indexes.sql` and (optionally)
   `sql/03_seed.sql` and `sql/04_plsql.sql` as described above.
2. Copy `db.properties.example` to `src/main/resources/db.properties`, set the `XEPDB1` URL and
   credentials, run `mvn clean package`, deploy the WAR to Tomcat 10.1 or 11, and open
   `http://localhost:8080/image-gallery/`.
3. Register a user, or sign in as `demo` / `DemoPass123!`.
4. Upload a solid red JPEG with title "Red square" and tags `red, warm`, and a WebP image with title
   "WebP check". Confirm the redirect to the detail page, the white-matte JPEG thumbnail, the tag
   chips and the dominant-colour swatch. The WebP image should serve as `image/webp`.
5. Upload a darker red image and a blue image (tags `red, warm` and `blue, cool`). Return to the
   gallery: the grid uses `/thumb/{id}`, and the tag cloud shows `warm` with a count of 2 and `red`
   with 2.
6. Search for `red, warm` with **Any tag**: both red images match. Switch to **All tags**: the same
   two match because they have both tags. Add a third image tagged only `warm` and repeat to see the
   difference.
7. Open the red image and press **Find similar**. The darker red image appears at ~100%, the blue
   image at 0%, and the percentage badges are visible. Confirm the three swatches for a gradient
   image.
8. Open **Search by example**, upload a red image that is not in the gallery, and confirm matches
   are ranked without the gallery count increasing (the query is not saved).
9. Log in as the owner, use **Edit tags** to replace the tags, then **Delete image**. Query the
   database to confirm the `IMAGE_TAGS` links and `IMAGE_HISTOGRAMS` row are gone and unused tags
   were removed from `TAGS`.
10. Delete one `IMAGE_HISTOGRAMS` row with SQL, then run `ReindexHistograms` (or `--all`) and verify
    the row is recreated with a 575-character bin string. Optionally inspect the `/thumb/{id}` ETag
    and confirm a conditional request returns `304`.
11. Open **AI search** and ask "a warm red sunset over water" or "show me images similar to image
    4". Confirm the model badge, the tool badge, the short answer and real gallery cards. Stop
    Ollama and repeat to see the local keyword/colour fallback warning.

## 16. Colour values used in the tests

The tests generate images with `BufferedImage`; no binary files are committed. The red/blue/red-shade
scores described in the unit tests follow directly from the 64-bin quantisation and the intersection
formula; they are not hard-coded expectations that bypass the algorithm.

## 17. Local AI search: how it works

The AI page is a thin shell around the same services used by the HTML pages. The model never gets
SQL, BLOB access or permission to write.

1. `/ai-search` renders the page. With JavaScript, the form POSTs to `/api/ai-search`; without it,
   a normal GET reloads the page and the server renders the result.
2. `AiSearchService` asks Ollama for each model's capabilities with `POST /api/show`.
3. When the tool model is available, the model receives the tool schemas and returns tool calls.
   `AiToolService` executes them against `ImageDao`, `TagService` and `SimilarityService`. The
   results go back to the model, which writes a 1–3 sentence answer. The Java layer keeps the real
   image ids and renders cards from Oracle, so the model cannot invent a result card.
4. When only qwen2:1.5b is available, the model extracts a JSON query (`tags`, `colours`/`colors`,
   `text`, `answer`) and Java runs the same searches.
5. When Ollama is offline, a deterministic keyword/tag/colour search runs and the page shows a
   warning instead of failing.

Tools exposed to the model:

| Tool | What it searches |
| --- | --- |
| `list_tags` | Most-used tags with counts. |
| `search_by_tags` | Tag search with ANY/ALL and a progressive fallback (ALL → ANY → split words). |
| `search_by_text` | `LOWER(title)` / `LOWER(description)` with escaped `%` and `_`. |
| `search_by_colour` | Maps colour and mood words to a target 64-bin histogram, then ranks with histogram intersection. Pure base colours bridge the coarse bins, and 0% matches are dropped. |
| `find_similar` | The existing `SimilarityService.findSimilar`. |

Example prompts:

- `a warm red sunset over water` → tag and colour tools.
- `show me images similar to image 4` → `find_similar`.
- `green forest` or `blue ocean` → tag search, with a colour fallback.
- `something with a red carpet` → title/description and tag search.

Limits: prompt ≤ 500 characters, at most 12 results per tool, at most 24 cards, at most
`ai.max-tool-rounds` model/tool exchanges, and a 180-second request timeout. The first model load is
the slowest part; later turns reuse the warm model. The full tool contract and sequence diagram are
in [docs/DESIGN.md](docs/DESIGN.md).

Privacy: prompts go only to the configured Ollama URL, which defaults to `127.0.0.1`. No image bytes
are sent; the model sees titles, descriptions, tags and colour scores returned by the tools.

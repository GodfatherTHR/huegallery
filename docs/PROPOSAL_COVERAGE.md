# Proposal Coverage — Image Gallery with Tags and Colour-Based Similarity Search

This document maps the project proposal to the delivered implementation. It is intended for the
IDP 1 submission and for anyone checking that every proposed requirement is present.

The project is implemented as **HueGallery**, a server-rendered Java web application. The optional
local-AI search is an extension beyond the original proposal, added on request; it reuses the same
tags, title/description search and colour histograms rather than replacing them.

---

## 1. Functional requirements

| ID | Requirement | Status | Where it lives | Evidence |
| --- | --- | --- | --- | --- |
| FR-01 | Users can register and log in | Done | `AuthService`, `RegisterServlet`, `LoginServlet`, `UserDao`, bcrypt | Registration/login flow; session replaced at login; wrong-password path returns the same generic message. |
| FR-02 | Upload JPEG, PNG, GIF or WebP within the size limit | Done | `UploadServlet`, `ImageService`, `ImageUtil`, TwelveMonkeys `imageio-webp` | 8 MB limit; real WebP upload stored as `image/webp`; thumbnail stored as JPEG. |
| FR-03 | Check that an uploaded file is a real image | Done | `ImageUtil.decode` | Format detected by `ImageIO` readers, never by filename or `Content-Type`; non-image bytes rejected. |
| FR-04 | Store each image as an Oracle BLOB with a thumbnail | Done | `ImageDao.insert`, `IMAGES.image_data`, `IMAGES.thumbnail` | Full original BLOB plus 300 px JPEG thumbnail; binary endpoints stream both. |
| FR-05 | Add several tags while uploading or later | Done | `TagParser`, `TagDao`, `TagService.replaceTags`, upload/edit-tags JSPs | Many-to-many `IMAGE_TAGS`; up to 10 tags; autocomplete. |
| FR-06 | Normalise tags (`Sea`, `sea ` → one tag) | Done | `TagParser` | Lower-case, strip punctuation, collapse whitespace, deduplicate; tested in `TagParserTest`. |
| FR-07 | Visitors browse the gallery and open any photo | Done | `HomeServlet`, `ImageDetailServlet`, home/detail JSPs | Public gallery, pagination, thumbnails, lightbox, tag cloud. |
| FR-08 | Search by one or more tags, ANY or ALL | Done | `SearchServlet`, `ImageDao.searchByTags`, `SearchServlet` | `WHERE ... IN (...)` for ANY; `HAVING COUNT(DISTINCT tag_id)` for ALL; pagination. |
| FR-09 | Compute a colour histogram for every upload | Done | `ColourHistogram`, `HistogramDao`, `ImageService.upload` | 64-bin 4×4×4 histogram, normalised to 1.0, stored in `IMAGE_HISTOGRAMS.bins` in the upload transaction. |
| FR-10 | "Find similar" with a similarity percentage | Done | `SimilarityService`, `HistogramIntersectionComparator`, detail page | Histogram intersection `Σ min(a[i],b[i])`; top 12 with percentage badges. |
| FR-11 | Only the owner can edit tags or delete | Done | `DeleteImageServlet`, `EditTagsServlet`, `ImageService.delete`, `TagService.replaceTags` | Ownership checked in services/SQL, not only by hiding buttons. |
| FR-12 | If any upload step fails, nothing is saved | Done | `ImageService.upload` transaction | One connection with `autoCommit(false)`, explicit commit/rollback; verified with forced mid-transaction failure. |

## 2. Proposed scope modules

| Module | Delivered |
| --- | --- |
| Login system | Registration, login, logout, bcrypt hashes, session fixation protection, CSRF. |
| Photo upload | Multipart upload, title/description/tags, size and type checks, drag-and-drop preview. |
| Image storage | Original and thumbnail BLOBs in Oracle, transactional insert. |
| Tag management | Normalisation, autocomplete, tag cloud, owner-only editing, orphan cleanup. |
| Gallery browsing | Responsive grid, lazy thumbnails, pagination (24/page), detail page, lightbox. |
| Tag search | ANY/ALL modes, tag cloud counts, pagination. |
| Similarity search | 64-bin colour histogram, intersection score, percentage ranking, dominant-colour swatches. |
| My images | Owner-only list, delete, tag editing. |
| Search by example (bonus) | Temporary query image, never persisted. |
| Local AI search (extra) | Natural-language prompt → tags/text/colour/find-similar tools → real image cards. |

## 3. Non-functional requirements

| Requirement | Implementation |
| --- | --- |
| Security | bcrypt cost 12; prepared statements everywhere; JSP output escaped with `<c:out>`/`fn:escapeXml`; CSRF token on every POST; upload decoded by `ImageIO`; 8 MB and 40 MP limits; filename sanitised; ownership re-checked server-side; generic error pages. |
| Performance | Gallery lists never select BLOBs; `/thumb/{id}` serves small JPEGs; histogram sample capped at 128 px; ETag/304 for image endpoints; `OFFSET/FETCH` pagination. |
| Reliability | Upload, delete and tag replacement each run in one transaction with rollback; foreign keys cascade links and histograms. |
| Usability | Responsive CSS grid, mobile 320 px layout verified, visible focus rings, skip link, reduced-motion support, accessible labels and live status. |
| Maintainability | Servlet → service → DAO → Oracle layers; `HistogramComparator` interface; separate AI client/tool/service classes; JUnit 5 tests. |

## 4. Proposed technologies

| Proposal | Delivered |
| --- | --- |
| HTML, CSS, JavaScript | JSP-rendered HTML, one custom stylesheet, vanilla JS (`app.js`, `chips.js`, `upload.js`, `ai-search.js`). |
| JSP with JSTL | JSPs under `WEB-INF/views`, JSTL 3 (`jakarta.tags.*`), no scriptlets. |
| Java 24 | `pom.xml` uses `maven.compiler.release=24`; built and tested on JDK 24. |
| Oracle Database 21c | Tables, identity columns, BLOBs, `MERGE`, `OFFSET/FETCH`, sequences. |
| JDBC + HikariCP | `ojdbc11` + HikariCP 5.1.0; pool closed on undeploy. |
| Apache Tomcat 10 | Works on Tomcat 10.1/11; tested on Tomcat 11.0. |
| Maven + JUnit 5 | WAR build; 31 unit tests plus end-to-end verification. |
| SQL and PL/SQL | DDL/DML in `sql/01`–`03`; optional `sql/04_plsql.sql` adds a procedure, function and trigger. |

## 5. SQL and PL/SQL deliverables

- `sql/01_schema.sql` — five core tables, identity columns, PK/FK/UNIQUE/NOT NULL, cascades.
- `sql/02_indexes.sql` — tag lookup, owner lookup, newest-first gallery.
- `sql/03_seed.sql` — optional demo user.
- `sql/04_plsql.sql`:
  - `PRUNE_ORPHAN_TAGS(p_removed OUT NUMBER)` removes tags with no image links.
  - `TAG_IMAGE_COUNT(p_tag_id)` returns the number of images using a tag.
  - `TRG_IMAGES_DELETE_LOG` writes an audit row to `IMAGE_DELETE_LOG` when an image is deleted.
- The Java app also cleans orphan tags inline, so the web application does not depend on the
  optional PL/SQL script.

## 6. Design artefacts

The proposal says the design will include a use case diagram, ERD, activity diagram, sequence
diagram, class diagram and system architecture diagram. All are in
[DESIGN.md](DESIGN.md):

1. Use case diagram.
2. System architecture.
3. ERD.
4. Upload activity diagram.
5. Similarity sequence diagram.
6. AI search sequence diagram.
7. Class diagram.
8. AI tool contract and design decisions.

The diagrams are Mermaid and render on GitHub. No binary diagram files are required.

## 7. Bonus and extra features

| Feature | Status |
| --- | --- |
| Dominant colour swatches | Done: three strongest histogram bins shown on the detail page. |
| Search by example | Done: uploaded query image is decoded in memory, compared, and discarded. |
| Reindex utility | Done: `ReindexHistograms` backfills missing or all histograms. |
| WebP uploads | Done through the pure-Java TwelveMonkeys reader. This is the only image-decoding dependency; the histogram itself remains plain Java. |
| Natural-language AI search | Extra: local Ollama assistant with tool calling and a prompt-mode/offline fallback. |
| PL/SQL objects | Extra: procedure, function and delete-audit trigger. |
| UML documentation | Done in `docs/DESIGN.md`. |
| Proposal coverage | This document. |

## 8. Deliberate notes and deviations

- **Java version:** the proposal listed Java 24; the build targets 24. The code avoids
  version-specific tricks, but the produced classes require a Java 24 runtime.
- **Tomcat version:** the proposal listed Tomcat 10. The application uses the Jakarta namespace and
  was verified on Tomcat 11.0, which is the installed environment; Tomcat 10.1 is also supported.
- **WebP dependency:** the proposal required WebP uploads but the JDK has no WebP reader. The
  TwelveMonkeys `imageio-webp` reader supplies decoding. The similarity algorithm remains a
  hand-written Java histogram and intersection metric, as required.
- **AI model choice:** qwen2:1.5b is completion-only in Ollama, so it cannot call tools. The app
  auto-detects capabilities and uses qwen3.5:0.8b for tool calling, keeping qwen2:1.5b as the
  prompt-mode fallback. If Ollama is offline, a deterministic keyword/colour search still works.
- **Similarity score granularity:** the prescribed 4×4×4 quantisation can give 0% for adjacent
  shades and 100% for shades in the same bin. The UI labels scores honestly; the AI colour tool
  additionally uses pure base colours and drops 0% matches.
- **Not committed:** the local `images/` sample folder and the long `PROJECT_GUIDE.md` are excluded
  from the repository by request. `README.md`, `docs/DESIGN.md` and this coverage file are committed.

## 9. How to verify quickly

```bash
# 1. Build and unit tests
mvn clean package

# 2. Database
sqlplus gallery/<password>@//localhost:1521/XEPDB1 @sql/01_schema.sql
sqlplus gallery/<password>@//localhost:1521/XEPDB1 @sql/02_indexes.sql
sqlplus gallery/<password>@//localhost:1521/XEPDB1 @sql/03_seed.sql
sqlplus gallery/<password>@//localhost:1521/XEPDB1 @sql/04_plsql.sql

# 3. Deploy
copy target\image-gallery.war "%CATALINA_BASE%\webapps\"

# 4. AI (optional)
ollama serve
ollama pull qwen3.5:0.8b
ollama pull qwen2:1.5b
```

Then open `/home`, register or sign in as `demo` / `DemoPass123!`, upload a JPEG/PNG/GIF/WebP, tag
it, search by tags, open the photo and press **Find similar**, try **Search by example**, and ask
the **AI search** page a colour/scene question.

**Status: all functional requirements FR-01 through FR-12 are implemented; the optional bonus
features and the requested AI extension are implemented and verified.**

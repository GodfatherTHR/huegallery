# HueGallery — Design and UML Diagrams

This document covers the design artefacts requested in the project proposal:
use case, architecture, ERD, activity, sequence and class diagrams, plus the
design of the optional local-AI search. The diagrams are Mermaid and render
directly on GitHub, VS Code (with a Markdown preview extension) and most
Markdown viewers.

---

## 1. Scope and actors

| Actor | Capabilities |
| --- | --- |
| Visitor | Browse the gallery, open a photo, use the tag cloud, search by tag, search by example, ask the AI assistant. |
| Registered user | Everything a visitor can do, plus upload, edit tags on their own images, delete their own images, and view **My images**. |
| Ollama (external, local) | Parses a natural-language prompt and optionally calls gallery tools. |

---

## 2. Use case diagram

```mermaid
flowchart LR
    Visitor((Visitor))
    User((Registered user))
    Ollama[(Ollama\nqwen3.5:0.8b)]

    Visitor --> Browse[Browse gallery]
    Visitor --> Detail[Open photo detail]
    Visitor --> TagCloud[View tag cloud]
    Visitor --> TagSearch[Search by tags]
    Visitor --> Example[Search by example]
    Visitor --> AiSearch[Ask AI to find images]
    Visitor --> Register[Register]

    User --> Login[Log in]
    User --> Upload[Upload image]
    User --> MyImages[View my images]
    User --> EditTags[Edit own tags]
    User --> Delete[Delete own image]

    AiSearch --> Ollama
    AiSearch -.calls.-> TagSearch
    AiSearch -.calls.-> Detail
    Example -.uses.-> Similarity[Rank by colour histogram]
    Detail -.uses.-> Similarity
    Upload -.creates.-> Histogram[Compute 64-bin histogram]
    Upload -.creates.-> Thumbnail[Create thumbnail]
```

---

## 3. System architecture

```mermaid
flowchart TB
    subgraph Browser["Browser"]
        UI["JSP-rendered HTML\nCSS + vanilla JS"]
    end

    subgraph Tomcat["Tomcat 10.1 / 11"]
        Filters["CharacterEncodingFilter\nAuthFilter"]
        Servlets["Servlets\nHome, Auth, Upload, Detail,\nSearch, Binary, AI search"]
        Services["Services\nAuth, Image, Tag,\nSimilarity, AI search"]
        DAOs["DAOs\nUser, Image, Tag, Histogram"]
        Utils["Utilities\nImageUtil, ColourHistogram,\nHistogramIntersectionComparator,\nColourNames, CsrfUtil"]
        AI["AI layer\nOllamaClient\nAiToolService\nAiSearchService"]
    end

    subgraph Data["Data"]
        Oracle[("Oracle Database 21c\nUSERS, IMAGES, TAGS,\nIMAGE_TAGS, IMAGE_HISTOGRAMS,\nIMAGE_DELETE_LOG")]
        Blobs[("BLOB columns\noriginal + thumbnail")]
        Ollama[("Ollama local API\n127.0.0.1:11434")]
    end

    UI --> Filters --> Servlets --> Services --> DAOs --> Oracle
    DAOs --- Blobs
    Services --> Utils
    Servlets --> AI --> Ollama
    AI --> Services
```

The application is a classic three-tier server-rendered web app. The AI layer
is an optional fourth collaborator: it calls the same services/DAOs as the HTML
controllers, so the model never touches SQL directly.

---

## 4. Entity–relationship diagram

```mermaid
erDiagram
    USERS ||--o{ IMAGES : uploads
    IMAGES ||--o{ IMAGE_TAGS : has
    TAGS ||--o{ IMAGE_TAGS : labels
    IMAGES ||--|| IMAGE_HISTOGRAMS : has
    IMAGES ||--o{ IMAGE_DELETE_LOG : "audited by trigger"

    USERS {
        NUMBER user_id PK
        VARCHAR2 username UK
        VARCHAR2 password_hash
        TIMESTAMP created_at
    }

    IMAGES {
        NUMBER image_id PK
        NUMBER user_id FK
        VARCHAR2 title
        VARCHAR2 description
        VARCHAR2 file_name
        VARCHAR2 mime_type
        NUMBER file_size
        NUMBER width
        NUMBER height
        BLOB image_data
        BLOB thumbnail
        TIMESTAMP uploaded_at
    }

    TAGS {
        NUMBER tag_id PK
        VARCHAR2 name UK
    }

    IMAGE_TAGS {
        NUMBER image_id PK
        NUMBER tag_id PK
    }

    IMAGE_HISTOGRAMS {
        NUMBER image_id PK
        VARCHAR2 bins
        TIMESTAMP computed_at
    }

    IMAGE_DELETE_LOG {
        NUMBER log_id PK
        NUMBER image_id
        VARCHAR2 title
        NUMBER owner_user_id
        VARCHAR2 mime_type
        NUMBER file_size
        TIMESTAMP deleted_at
    }
```

`IMAGE_TAGS` is the many-to-many join table. `IMAGE_HISTOGRAMS` has a
one-to-one primary-key/foreign-key relationship with `IMAGES`. Foreign keys use
`ON DELETE CASCADE` for tag links and histograms; orphan `TAGS` rows are removed
by the application (or by the optional `PRUNE_ORPHAN_TAGS` procedure).

---

## 5. Upload activity diagram

```mermaid
flowchart TD
    A[POST /upload multipart] --> B{Declared request size <= 8 MB?}
    B -- no --> E1[Show friendly 8 MB error]
    B -- yes --> C{CSRF token valid?}
    C -- no --> E2[403 page]
    C -- yes --> D[Read title, description, tags, file part]
    D --> F[Validate title, description and tag count]
    F --> G[Decode bytes with ImageIO / TwelveMonkeys]
    G --> H{Real JPEG, PNG, GIF or WebP?}
    H -- no --> E3[Friendly format error]
    H -- yes --> I[Check 40 megapixel limit]
    I --> J[Composite transparency onto white]
    J --> K[Create JPEG thumbnail, longest side 300 px]
    K --> L[Compute 64-bin colour histogram]
    L --> M[Open connection, autoCommit = false]
    M --> N[INSERT IMAGES with full BLOB + thumbnail]
    N --> O[For each tag: MERGE TAGS, MERGE IMAGE_TAGS]
    O --> P[MERGE IMAGE_HISTOGRAMS]
    P --> Q{All statements succeeded?}
    Q -- yes --> R[COMMIT, redirect to /photo/id]
    Q -- no --> S[ROLLBACK, show error, no partial rows]
```

---

## 6. Similarity sequence diagram

```mermaid
sequenceDiagram
    actor U as Visitor
    participant D as ImageDetailServlet
    participant I as ImageDao
    participant S as SimilarityService
    participant H as HistogramDao
    participant C as HistogramIntersectionComparator

    U->>D: GET /photo/10
    D->>I: findById(10)
    I-->>D: Image metadata
    D->>S: findSimilar(10, 12)
    S->>H: findBins(10)
    H-->>S: 64 comma-separated values
    S->>H: findAllExcept(10)
    H-->>S: image_id + bins for all candidates
    loop each candidate
        S->>C: similarity(target, candidate)
        C-->>S: sum(min(a[i], b[i]))
    end
    S->>S: sort descending, take top 12
    S->>I: findByIds(top 12)
    I-->>S: metadata for the winners
    S-->>D: List<SimilarImage>
    D-->>U: HTML with score badges
```

---

## 7. AI search sequence diagram

```mermaid
sequenceDiagram
    actor U as Visitor
    participant JS as ai-search.js
    participant V as AiSearchServlet
    participant A as AiSearchService
    participant O as Ollama
    participant T as AiToolService
    participant DB as Oracle

    U->>JS: "a warm red sunset"
    JS->>V: POST /api/ai-search (prompt + CSRF)
    V->>A: search(prompt)
    A->>O: POST /api/show (capabilities)
    alt qwen3.5 supports tools
        A->>O: POST /api/chat with tool schemas
        O-->>A: tool_calls: search_by_colour(["red","warm","sunset"])
        A->>T: execute tool
        T->>DB: load histograms + rank
        DB-->>T: matching images
        A->>O: assistant tool_calls + tool result
        O-->>A: final 1-3 sentence answer
    else only qwen2:1.5b is available
        A->>O: strict JSON intent prompt
        O-->>A: tags / colours / text
        A->>T: run local tag, colour and text searches
        T->>DB: SELECT ...
        DB-->>T: images
    end
    A-->>V: answer + real images
    V-->>JS: JSON
    JS-->>U: typewriter answer + staggered image cards
```

If Ollama is offline, `AiSearchService` catches the connection error and runs a
deterministic keyword/colour search so the page still returns something useful.
The UI shows a warning explaining the degraded mode.

---

## 8. Class diagram

```mermaid
classDiagram
    class AiSearchServlet {
        -AiSearchService aiSearchService
        +doGet(request, response)
        +doPost(request, response)
        -handleApi(request, response)
    }

    class AiSearchService {
        -OllamaClient ollama
        -AiToolService toolService
        -ImageDao imageDao
        +search(prompt) AiSearchResult
        -runToolLoop(model, prompt, hits, toolsUsed)
        -runPromptMode(model, prompt, hits, toolsUsed)
        -localSearch(prompt, hits)
        -resolveModel() ModelChoice
    }

    class OllamaClient {
        +show(model) ModelInfo
        +chat(model, messages, tools) ChatReply
        +assistantMessage(reply)$ JsonObject
    }

    class AiToolService {
        +execute(name, arguments) ToolResult
        +knownTags(limit) List~Tag~
    }

    class AiToolDefinitions {
        +all()$ JsonArray
    }

    class ColourNames {
        +toHistogram(colours)$ double[]
        +extract(text)$ List~String~
        +knownNames()$ String
    }

    class AiSearchResult {
        -String answer
        -List~AiImageCard~ images
        -String model
        -String mode
        -List~String~ toolsUsed
        -long elapsedMs
        -String warning
    }

    class AiImageCard {
        -long imageId
        -String title
        -String username
        -Integer scorePercent
        -String reason
    }

    class SimilarityService {
        +findSimilar(imageId, limit) List~SimilarImage~
        +searchByHistogram(target, limit) List~SimilarImage~
        +searchByExample(data, limit) List~SimilarImage~
    }

    class ImageDao {
        +searchByTags(tags, all, offset, limit)
        +searchByText(query, limit)
        +findByIds(ids)
    }

    AiSearchServlet --> AiSearchService
    AiSearchService --> OllamaClient
    AiSearchService --> AiToolService
    AiSearchService --> AiSearchResult
    AiSearchService --> SimilarityService
    AiSearchService --> ImageDao
    AiToolService --> AiToolDefinitions
    AiToolService --> SimilarityService
    AiToolService --> ColourNames
    AiSearchResult "1" o-- "*" AiImageCard
```

---

## 9. AI tool contract

| Tool | Arguments | What it does |
| --- | --- | --- |
| `list_tags` | none | Returns the most-used tags and their image counts. |
| `search_by_tags` | `tags[]`, `match` (`any`/`all`), `limit` | Runs the existing tag SQL. Falls back from ALL → ANY → split words when a strict query is empty. |
| `search_by_text` | `query`, `limit` | `LOWER(title) LIKE ?` or `LOWER(description) LIKE ?`, with `%`/`_` escaped. |
| `search_by_colour` | `colours[]`, `limit` | Maps colour words (`red`, `warm`, `sunset`, `ocean`, `#rrggbb`) to a target 64-bin histogram and ranks the gallery with histogram intersection. |
| `find_similar` | `image_id`, `limit` | Reuses `SimilarityService.findSimilar` for an existing image. |

All tool results are JSON with an `images` array, a `count` and a short `note`.
The Java layer also keeps a deduplicated set of real image ids, so the UI always
renders database rows rather than model-invented text.

### Model choice

Ollama reports qwen2:1.5b as **completion only** and qwen3.5:0.8b as
**tools + thinking + vision**. The app therefore:

1. asks Ollama for each model's capabilities (`POST /api/show`);
2. uses qwen3.5:0.8b for the tool-calling loop when it is installed;
3. falls back to qwen2:1.5b in prompt/JSON mode when tool calling is not
   available;
4. falls back to deterministic keyword/colour search when Ollama is offline;
5. disables the thinking trace by default (`ai.think=false`) because it makes
   the 0.8B model much slower on CPU; the setting can be re-enabled;
6. keeps partial tool results if a later model call fails, showing a short
   warning instead of throwing the matches away.

The model names and timeout are configurable in `src/main/resources/ai.properties`
or with `GALLERY_AI_MODEL`, `GALLERY_AI_TOOL_MODEL`, `GALLERY_OLLAMA_URL` and
related environment variables.

---

## 10. Key design decisions

| Decision | Rationale |
| --- | --- |
| Images in Oracle BLOBs | Proposal requirement; backups and deletion stay transactional. |
| Metadata-only list queries | Gallery pages never load full BLOBs or thumbnails; `/thumb/{id}` streams them separately. |
| 64-bin 4×4×4 histogram | Simple, explainable, resolution-independent and cheap to compare. |
| Histogram intersection | Symmetric, bounded [0,1], and easy to explain to a non-specialist audience. |
| `HistogramComparator` interface | The metric can be replaced without touching the service. |
| Tags normalised in Java | Keeps the database simple and the rule testable (`TagParserTest`). |
| `MERGE` for tags and links | Concurrent uploads cannot create duplicate tag rows. |
| One transaction per upload | No image without a thumbnail or histogram; proven with rollback tests. |
| TwelveMonkeys WebP reader | Satisfies the proposal's WebP requirement while the histogram remains plain Java. |
| Local Ollama instead of a cloud API | No image data or prompts leave the machine; the model can be swapped. |
| Model tools + prompt fallback | Small local models differ in capability; the app auto-detects instead of assuming. |
| Server-rendered page + JSON endpoint | The AI page works without JavaScript and gets smooth async updates with it. |

---

## 11. Where the design lives in the code

| Concern | Files |
| --- | --- |
| Upload pipeline | `ImageService`, `UploadServlet`, `ImageUtil`, `ColourHistogram` |
| Similarity | `SimilarityService`, `HistogramDao`, `HistogramIntersectionComparator` |
| Tag rules and SQL | `TagParser`, `TagService`, `TagDao`, `ImageDao` |
| Auth and CSRF | `AuthService`, `LoginServlet`, `RegisterServlet`, `CsrfUtil`, `AuthFilter` |
| AI assistant | `com.gallery.ai.*`, `AiSearchServlet`, `ai-search.jsp`, `ai-search.js` |
| PL/SQL | `sql/04_plsql.sql` |

<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<div class="ai-page">
    <section class="ai-hero" aria-labelledby="ai-heading">
        <p class="eyebrow">Local AI &middot; Ollama</p>
        <h1 id="ai-heading">Describe the picture. We&rsquo;ll find the palette.</h1>
        <p class="ai-hero__lead">Ask in plain English. The assistant searches real tags, titles and
            64-bin colour histograms in the gallery. Nothing is sent to a cloud service.</p>

        <form class="ai-form" method="get" action="${ctx}/ai-search" role="search"
              data-ai-form data-api-url="${ctx}/api/ai-search" data-csrf="${csrfToken}">
            <div class="ai-form__row">
                <label class="sr-only" for="ai-prompt">Describe the image you are looking for</label>
                <textarea id="ai-prompt" name="q" rows="2" maxlength="500"
                          placeholder="e.g. a warm red sunset over water, or something like photo 12">${fn:escapeXml(query)}</textarea>
                <button class="btn btn--accent" type="submit" data-ai-submit>Ask the gallery</button>
            </div>
            <div class="ai-examples" role="group" aria-label="Example searches">
                <button type="button" class="chip chip--button" data-ai-example="a warm red sunset over water">a warm red sunset</button>
                <button type="button" class="chip chip--button" data-ai-example="a green forest">green forest</button>
                <button type="button" class="chip chip--button" data-ai-example="a blue ocean">blue ocean</button>
                <button type="button" class="chip chip--button" data-ai-example="show me images similar to image 1">similar to image 1</button>
            </div>
            <p class="ai-hint">Uses qwen3.5:0.8b for tool calling when available, with qwen2:1.5b as the
                prompt fallback. The first model load can take a minute.</p>
        </form>
    </section>

    <section class="ai-guide" aria-label="How AI search works">
        <div class="ai-guide__item">
            <span class="ai-guide__step">01</span>
            <h2>Describe it</h2>
            <p>A colour, a mood, a scene, or an existing image id.</p>
        </div>
        <div class="ai-guide__item">
            <span class="ai-guide__step">02</span>
            <h2>The model picks tools</h2>
            <p>Tags, titles and colour histograms. No invented results.</p>
        </div>
        <div class="ai-guide__item">
            <span class="ai-guide__step">03</span>
            <h2>Get real images</h2>
            <p>Every card is a row from Oracle, not a generated picture.</p>
        </div>
    </section>

    <div class="ai-status" data-ai-status role="status" aria-live="polite" hidden>
        <span class="ai-status__orb" aria-hidden="true"></span>
        <span data-ai-status-text>Thinking&hellip;</span>
    </div>

    <div class="alert alert--error" data-ai-error role="alert" ${empty error ? 'hidden' : ''}>
        <c:out value="${error}"/>
    </div>

    <section class="ai-results" data-ai-results ${empty result ? 'hidden' : ''}
             aria-labelledby="ai-results-heading">
        <h2 id="ai-results-heading" class="sr-only">AI search results</h2>

        <div class="ai-warning" data-ai-warning ${empty result.warning ? 'hidden' : ''}>
            <c:out value="${result.warning}"/>
        </div>

        <div class="ai-answer">
            <div class="ai-answer__meta">
                <span class="ai-badge" data-ai-model><c:out value="${result.model}"/></span>
                <span class="ai-badge ai-badge--muted" data-ai-mode>
                    <c:choose>
                        <c:when test="${result.mode == 'tools'}">tool calling</c:when>
                        <c:when test="${result.mode == 'prompt'}">prompt mode</c:when>
                        <c:when test="${not empty result}">local fallback</c:when>
                    </c:choose>
                </span>
                <span class="ai-model-line" data-ai-tools>
                    <c:choose>
                        <c:when test="${not empty result.toolsUsed}">
                            Tools:
                            <c:forEach var="tool" items="${result.toolsUsed}" varStatus="loop">
                                <span><c:out value="${tool}"/>${loop.last ? '' : ', '}</span>
                            </c:forEach>
                        </c:when>
                        <c:when test="${not empty result}">No tool calls</c:when>
                    </c:choose>
                </span>
            </div>
            <p class="ai-answer__text" data-ai-answer-wrap>
                <span data-ai-answer><c:out value="${result.answer}"/></span>
                <span class="sr-only" data-ai-answer-sr></span>
            </p>
        </div>

        <ul class="ai-grid" data-ai-grid ${empty result.images ? 'hidden' : ''}>
            <c:forEach var="card" items="${result.images}" varStatus="loop">
                <li class="card ai-card" style="--i:${loop.index}">
                    <a class="card__link" href="<c:url value='/photo/${card.imageId}'/>">
                        <span class="card__media">
                            <img class="card__image"
                                 src="<c:url value='/thumb/${card.imageId}'/>"
                                 alt="${fn:escapeXml(card.title)}"
                                 loading="lazy" decoding="async">
                            <c:if test="${not empty card.scorePercent}">
                                <span class="score-badge">${card.scorePercent}%</span>
                            </c:if>
                        </span>
                        <span class="card__body">
                            <span class="card__title"><c:out value="${card.title}"/></span>
                            <span class="card__meta">by <c:out value="${card.username}"/></span>
                            <c:if test="${not empty card.reason}">
                                <span class="ai-card__reason"><c:out value="${card.reason}"/></span>
                            </c:if>
                        </span>
                    </a>
                </li>
            </c:forEach>
        </ul>

        <div class="ai-empty" data-ai-empty ${empty result.images ? '' : 'hidden'}>
            <p class="empty-state__title">No images matched.</p>
            <p class="muted">Try a colour like &ldquo;warm red&rdquo;, a tag like &ldquo;sunset&rdquo;,
                or search by title.</p>
            <a class="btn btn--ghost" href="${ctx}/home">Browse the gallery</a>
        </div>

        <p class="ai-model-line" data-ai-meta>
            <c:if test="${not empty result}">
                <span><c:out value="${result.imageCount}"/> images</span>
                <span>&middot;</span>
                <span><c:out value="${result.elapsedSeconds}"/>s</span>
            </c:if>
        </p>
    </section>
</div>

<script src="${ctx}/assets/js/ai-search.js" defer></script>
<%@ include file="partials/footer.jsp" %>

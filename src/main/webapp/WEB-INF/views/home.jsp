<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="hero">
    <div class="hero__copy">
        <p class="eyebrow">A colour-first image archive</p>
        <h1>Find the photo you half-remember.</h1>
        <p class="hero__lead">Ask for a colour, a mood, or a scene. HueGallery compares real colour
            histograms, so a sunset finds other sunsets and a forest finds other greens.</p>

        <form class="ai-teaser" method="get" action="${ctx}/ai-search" role="search">
            <label class="sr-only" for="hero-ai">Describe the image you are looking for</label>
            <div class="ai-teaser__row">
                <span class="ai-teaser__spark" aria-hidden="true">&#10022;</span>
                <input id="hero-ai" name="q" type="search" maxlength="500"
                       placeholder="Try &ldquo;a warm red sunset over water&rdquo;" autocomplete="off">
                <button class="btn" type="submit">Ask the gallery</button>
            </div>
            <p class="ai-teaser__hint">A local Ollama model reads the prompt and searches real tags and colour histograms.</p>
        </form>

        <form class="search-form search-form--compact" method="get" action="${ctx}/search" role="search">
            <label class="sr-only" for="hero-tags">Search by tags</label>
            <input id="hero-tags" name="tags" type="search" placeholder="or search tags: red, sunset" maxlength="200">
            <button class="btn btn--ghost" type="submit">Search tags</button>
        </form>
    </div>

    <div class="hero__visual" aria-hidden="true">
        <div class="palette-card palette-card--one"><span>sunset</span></div>
        <div class="palette-card palette-card--two"><span>forest</span></div>
        <div class="palette-card palette-card--three"><span>ocean</span></div>
    </div>
</section>

<section class="tag-cloud" aria-labelledby="tag-cloud-heading">
    <div class="section-heading">
        <div>
            <h2 id="tag-cloud-heading">Popular tags</h2>
            <p class="muted">Chip size grows with the number of images.</p>
        </div>
        <a class="btn btn--ghost btn--small" href="${ctx}/ai-search">Or ask the AI</a>
    </div>
    <c:choose>
        <c:when test="${empty tagCloud}">
            <p class="muted">No tags yet. Upload an image and add a few.</p>
        </c:when>
        <c:otherwise>
            <ul class="tag-cloud__list">
                <c:forEach var="tag" items="${tagCloud}">
                    <c:url var="tagUrl" value="/search">
                        <c:param name="tags" value="${tag.name}"/>
                    </c:url>
                    <li>
                        <a class="tag-cloud__item"
                           style="font-size: calc(0.82rem + ${(tag.usageCount / maxTagCount) * 0.9}rem)"
                           href="${tagUrl}">
                            <c:out value="${tag.name}"/>
                            <span class="tag-cloud__count">${tag.usageCount}</span>
                        </a>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</section>

<section class="gallery" aria-labelledby="gallery-heading">
    <div class="section-heading">
        <div>
            <h2 id="gallery-heading">Latest images</h2>
            <p class="muted">${pageResult.total} image<c:if test="${pageResult.total != 1}">s</c:if></p>
        </div>
        <c:if test="${not empty sessionScope.currentUser}">
            <a class="btn btn--small" href="${ctx}/upload">Upload an image</a>
        </c:if>
    </div>

    <c:set var="emptyMessage" value="Nothing here yet."/>
    <c:set var="emptyHint" value="Be the first to add a photo to the gallery."/>
    <c:if test="${not empty sessionScope.currentUser}">
        <c:set var="emptyActionUrl" value="${ctx}/upload"/>
        <c:set var="emptyActionLabel" value="Upload an image"/>
    </c:if>
    <%@ include file="partials/image-grid.jsp" %>

    <c:if test="${pageResult.totalPages > 1}">
        <nav class="pagination" aria-label="Gallery pages">
            <c:if test="${pageResult.hasPrevious}">
                <c:url var="prevUrl" value="/home">
                    <c:param name="page" value="${pageResult.page - 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${prevUrl}">Previous</a>
            </c:if>
            <span class="pagination__status">Page ${pageResult.page} of ${pageResult.totalPages}</span>
            <c:if test="${pageResult.hasNext}">
                <c:url var="nextUrl" value="/home">
                    <c:param name="page" value="${pageResult.page + 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${nextUrl}">Next</a>
            </c:if>
        </nav>
    </c:if>
</section>

<%@ include file="partials/footer.jsp" %>

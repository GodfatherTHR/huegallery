<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<nav class="breadcrumb" aria-label="Breadcrumb">
    <a href="${ctx}/home">Gallery</a>
    <span aria-hidden="true">/</span>
    <span><c:out value="${image.title}"/></span>
</nav>

<article class="detail">
    <div class="detail__media">
        <button type="button" class="detail__zoom" data-lightbox-trigger
                aria-label="View <c:out value='${image.title}'/> at full size">
            <img class="detail__image" src="<c:url value='/image/${image.imageId}'/>"
                 alt="${fn:escapeXml(image.title)}">
        </button>
    </div>

    <div class="detail__info">
        <h1><c:out value="${image.title}"/></h1>
        <p class="detail__byline">
            Uploaded by <strong><c:out value="${image.username}"/></strong>
            on <fmt:formatDate value="${image.uploadedAt}" pattern="d MMM yyyy, HH:mm"/>
        </p>

        <c:if test="${not empty image.description}">
            <p class="detail__description"><c:out value="${image.description}"/></p>
        </c:if>

        <c:choose>
            <c:when test="${empty tags}">
                <p class="muted">No tags yet.</p>
            </c:when>
            <c:otherwise>
                <ul class="chip-list" aria-label="Tags">
                    <c:forEach var="tag" items="${tags}">
                        <c:url var="tagUrl" value="/search">
                            <c:param name="tags" value="${tag.name}"/>
                        </c:url>
                        <li><a class="chip" href="${tagUrl}">#<c:out value="${tag.name}"/></a></li>
                    </c:forEach>
                </ul>
            </c:otherwise>
        </c:choose>

        <dl class="facts">
            <div>
                <dt>Dimensions</dt>
                <dd>${image.width} &times; ${image.height} px</dd>
            </div>
            <div>
                <dt>File</dt>
                <dd><c:out value="${image.fileName}"/> &middot; ${image.fileSizeKb} KB</dd>
            </div>
        </dl>

        <c:if test="${not empty dominantColours}">
            <section class="dominant" aria-labelledby="dominant-heading">
                <h2 class="h-small" id="dominant-heading">Dominant colours</h2>
                <ul class="swatches">
                    <c:forEach var="colour" items="${dominantColours}">
                        <li class="swatch" title="${colour.percent}% of sampled pixels">
                            <span class="swatch__chip" style="background:#${colour.hex}"></span>
                            <span class="swatch__label">#${colour.hex} &middot; ${colour.percent}%</span>
                        </li>
                    </c:forEach>
                </ul>
            </section>
        </c:if>

        <c:url var="aiSimilarUrl" value="/ai-search">
            <c:param name="q" value="Show me images similar to ${image.title} (image id ${image.imageId})"/>
        </c:url>
        <div class="detail__actions">
            <a class="btn" href="#similar">Find similar</a>
            <a class="btn btn--ghost" href="${aiSimilarUrl}">Ask AI for similar</a>
            <c:if test="${owner}">
                <a class="btn btn--ghost" href="${ctx}/edit-tags?id=${image.imageId}">Edit tags</a>
                <form class="inline-form" method="post" action="${ctx}/delete"
                      data-confirm="Delete this image permanently?">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="id" value="${image.imageId}">
                    <button type="submit" class="btn btn--danger">Delete image</button>
                </form>
            </c:if>
        </div>
    </div>
</article>

<section class="similar" id="similar" aria-labelledby="similar-heading">
    <div class="section-heading">
        <div>
            <h2 id="similar-heading">Visually similar images</h2>
            <p class="muted">Ranked by histogram intersection. 100% means an identical colour distribution.</p>
        </div>
    </div>

    <c:choose>
        <c:when test="${empty similarImages}">
            <div class="empty-state">
                <p class="empty-state__title">No similar images yet.</p>
                <p class="muted">Upload more images to compare their colour histograms.</p>
            </div>
        </c:when>
        <c:otherwise>
            <ul class="grid grid--similar">
                <c:forEach var="match" items="${similarImages}">
                    <li class="card">
                        <a class="card__link" href="<c:url value='/photo/${match.image.imageId}'/>">
                            <span class="card__media">
                                <img class="card__image"
                                     src="<c:url value='/thumb/${match.image.imageId}'/>"
                                     alt="${fn:escapeXml(match.image.title)}"
                                     loading="lazy" decoding="async">
                                <span class="score-badge">${match.scorePercent}%</span>
                            </span>
                            <span class="card__body">
                                <span class="card__title"><c:out value="${match.image.title}"/></span>
                                <span class="card__meta">by <c:out value="${match.image.username}"/></span>
                            </span>
                        </a>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</section>

<div class="lightbox" id="lightbox" hidden>
    <button type="button" class="lightbox__close" data-lightbox-close aria-label="Close full-size view">&times;</button>
    <img class="lightbox__image" data-lightbox-image alt="">
</div>

<%@ include file="partials/footer.jsp" %>

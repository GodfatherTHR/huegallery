<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="page-narrow">
    <h1>Search by example</h1>
    <p class="muted">Upload an image to see the gallery images with the closest colour distribution.
        The query image is processed in memory and is <strong>never saved</strong>.</p>

    <c:if test="${not empty error}">
        <div class="alert alert--error" role="alert"><c:out value="${error}"/></div>
    </c:if>

    <form class="form card-form" method="post" action="${ctx}/search-by-example"
          enctype="multipart/form-data" data-upload-form>
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <div class="field">
            <label for="example-file">Query image</label>
            <div class="dropzone" data-dropzone>
                <input id="example-file" class="dropzone__input" type="file" name="image" required
                       accept="image/jpeg,image/png,image/gif,image/webp" data-file-input>
                <div class="dropzone__content" aria-hidden="true">
                    <span class="dropzone__icon">&uarr;</span>
                    <span class="dropzone__title">Drop an image here, or click to browse</span>
                    <span class="dropzone__hint">JPEG &middot; PNG &middot; GIF &middot; WebP &middot; max 8 MB</span>
                </div>
                <img class="dropzone__preview" data-preview alt="Preview of the selected image" hidden>
            </div>
            <p class="hint" data-file-message role="status"></p>
        </div>
        <div class="form-actions">
            <button class="btn" type="submit">Find matches</button>
        </div>
    </form>
</section>

<c:if test="${results != null}">
    <section class="similar" aria-labelledby="example-results-heading">
        <div class="section-heading">
            <div>
                <h2 id="example-results-heading">Closest matches</h2>
                <p class="muted">${fn:length(results)} result<c:if test="${fn:length(results) != 1}">s</c:if>,
                    ordered by colour similarity.</p>
            </div>
        </div>
        <c:choose>
            <c:when test="${empty results}">
                <div class="empty-state">
                    <p class="empty-state__title">No images to compare against yet.</p>
                    <a class="btn" href="${ctx}/home">Browse the gallery</a>
                </div>
            </c:when>
            <c:otherwise>
                <ul class="grid grid--similar">
                    <c:forEach var="match" items="${results}">
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
</c:if>

<script src="${ctx}/assets/js/upload.js" defer></script>
<%@ include file="partials/footer.jsp" %>

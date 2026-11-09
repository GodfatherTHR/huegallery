<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="gallery" aria-labelledby="search-heading">
    <h1 id="search-heading">Search by tag</h1>

    <form class="search-form search-form--wide" method="get" action="${ctx}/search" role="search">
        <label class="sr-only" for="tags">Tags, separated by commas</label>
        <input id="tags" name="tags" type="search" required maxlength="200"
               placeholder="red, sunset" value="${fn:escapeXml(tagsInput)}">
        <fieldset class="segmented">
            <legend class="sr-only">Match mode</legend>
            <label class="segmented__option">
                <input type="radio" name="mode" value="any" ${modeAll ? '' : 'checked'}>
                <span>Any tag</span>
            </label>
            <label class="segmented__option">
                <input type="radio" name="mode" value="all" ${modeAll ? 'checked' : ''}>
                <span>All tags</span>
            </label>
        </fieldset>
        <button class="btn" type="submit">Search</button>
    </form>

    <div class="section-heading section-heading--tight">
        <div>
            <h2>Results</h2>
            <p class="muted">
                <c:out value="${pageResult.total}"/> image<c:if test="${pageResult.total != 1}">s</c:if>
                matching <strong>${modeAll ? 'all' : 'any'}</strong> of:
                <c:forEach var="tag" items="${searchTags}" varStatus="loop">
                    <span class="chip chip--static">#<c:out value="${tag}"/></span>
                </c:forEach>
            </p>
        </div>
    </div>

    <c:set var="emptyMessage" value="No images matched those tags."/>
    <c:set var="emptyHint" value="Try fewer tags, or switch from ALL to ANY matching."/>
    <c:set var="emptyActionUrl" value="${ctx}/home"/>
    <c:set var="emptyActionLabel" value="Back to the gallery"/>
    <%@ include file="partials/image-grid.jsp" %>

    <c:if test="${pageResult.totalPages > 1}">
        <nav class="pagination" aria-label="Search result pages">
            <c:if test="${pageResult.hasPrevious}">
                <c:url var="prevUrl" value="/search">
                    <c:param name="tags" value="${tagsInput}"/>
                    <c:param name="mode" value="${modeAll ? 'all' : 'any'}"/>
                    <c:param name="page" value="${pageResult.page - 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${prevUrl}">Previous</a>
            </c:if>
            <span class="pagination__status">Page ${pageResult.page} of ${pageResult.totalPages}</span>
            <c:if test="${pageResult.hasNext}">
                <c:url var="nextUrl" value="/search">
                    <c:param name="tags" value="${tagsInput}"/>
                    <c:param name="mode" value="${modeAll ? 'all' : 'any'}"/>
                    <c:param name="page" value="${pageResult.page + 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${nextUrl}">Next</a>
            </c:if>
        </nav>
    </c:if>
</section>

<%@ include file="partials/footer.jsp" %>

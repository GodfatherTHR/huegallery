<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="partials/header.jsp" %>

<section class="gallery" aria-labelledby="my-images-heading">
    <div class="section-heading">
        <div>
            <h1 id="my-images-heading">My images</h1>
            <p class="muted">${pageResult.total} image<c:if test="${pageResult.total != 1}">s</c:if> uploaded by you.</p>
        </div>
        <a class="btn" href="${ctx}/upload">Upload an image</a>
    </div>

    <c:set var="emptyMessage" value="You have not uploaded any images yet."/>
    <c:set var="emptyHint" value="Share a photo and tag it so other visitors can find it."/>
    <c:set var="emptyActionUrl" value="${ctx}/upload"/>
    <c:set var="emptyActionLabel" value="Upload an image"/>
    <%@ include file="partials/image-grid.jsp" %>

    <c:if test="${pageResult.totalPages > 1}">
        <nav class="pagination" aria-label="My images pages">
            <c:if test="${pageResult.hasPrevious}">
                <c:url var="prevUrl" value="/my-images">
                    <c:param name="page" value="${pageResult.page - 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${prevUrl}">Previous</a>
            </c:if>
            <span class="pagination__status">Page ${pageResult.page} of ${pageResult.totalPages}</span>
            <c:if test="${pageResult.hasNext}">
                <c:url var="nextUrl" value="/my-images">
                    <c:param name="page" value="${pageResult.page + 1}"/>
                </c:url>
                <a class="btn btn--ghost" href="${nextUrl}">Next</a>
            </c:if>
        </nav>
    </c:if>
</section>

<%@ include file="partials/footer.jsp" %>

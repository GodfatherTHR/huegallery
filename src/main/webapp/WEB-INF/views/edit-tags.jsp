<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="page-narrow">
    <nav class="breadcrumb" aria-label="Breadcrumb">
        <a href="${ctx}/photo/${image.imageId}">Back to the image</a>
    </nav>
    <h1>Edit tags</h1>
    <p class="muted"><c:out value="${image.title}"/></p>

    <c:if test="${not empty error}">
        <div class="alert alert--error" role="alert"><c:out value="${error}"/></div>
    </c:if>

    <form class="form card-form" method="post" action="${ctx}/edit-tags">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="id" value="${image.imageId}">

        <div class="field">
            <label for="tag-input">Tags <span class="muted">(up to 10)</span></label>
            <div class="chips-input" data-chips data-initial="${fn:escapeXml(tagsValue)}">
                <ul class="chips-input__list" data-chip-list></ul>
                <input id="tag-input" class="chips-input__field" type="text" data-chip-input
                       placeholder="Type a tag and press Enter" autocomplete="off" maxlength="40">
                <input type="hidden" name="tags" value="${fn:escapeXml(tagsValue)}" data-chip-value>
                <div class="autocomplete" data-autocomplete hidden></div>
            </div>
            <p class="hint">Removing all tags is allowed. Unused tags are cleaned up automatically.</p>
        </div>

        <div class="form-actions">
            <button class="btn" type="submit">Save tags</button>
            <a class="btn btn--ghost" href="${ctx}/photo/${image.imageId}">Cancel</a>
        </div>
    </form>
</section>

<script src="${ctx}/assets/js/chips.js" defer></script>
<%@ include file="partials/footer.jsp" %>

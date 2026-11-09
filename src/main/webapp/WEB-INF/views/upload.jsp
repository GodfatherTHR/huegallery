<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="page-narrow">
    <h1>Upload an image</h1>
    <p class="muted">JPEG, PNG, GIF or WebP up to 8&nbsp;MB. The file type is verified on the server by
        decoding it, not by its name.</p>

    <c:if test="${not empty error}">
        <div class="alert alert--error" role="alert"><c:out value="${error}"/></div>
    </c:if>

    <form class="form card-form" method="post" action="${ctx}/upload"
          enctype="multipart/form-data" data-upload-form>
        <input type="hidden" name="_csrf" value="${csrfToken}">

        <div class="field">
            <label for="title">Title</label>
            <input id="title" name="title" type="text" maxlength="150" required
                   value="${fn:escapeXml(titleValue)}">
        </div>

        <div class="field">
            <label for="description">Description <span class="muted">(optional)</span></label>
            <textarea id="description" name="description" rows="4" maxlength="1000"><c:out value="${descriptionValue}"/></textarea>
        </div>

        <div class="field">
            <label for="tag-input">Tags <span class="muted">(up to 10)</span></label>
            <div class="chips-input" data-chips data-initial="${fn:escapeXml(tagsValue)}">
                <ul class="chips-input__list" data-chip-list></ul>
                <input id="tag-input" class="chips-input__field" type="text" data-chip-input
                       placeholder="Type a tag and press Enter" autocomplete="off"
                       aria-describedby="tag-help" maxlength="40">
                <input type="hidden" name="tags" value="${fn:escapeXml(tagsValue)}" data-chip-value>
                <div class="autocomplete" data-autocomplete hidden></div>
            </div>
            <p id="tag-help" class="hint">Comma-separated. Lower-cased, punctuation removed, max 40 characters each.</p>
        </div>

        <div class="field">
            <label for="file">Image file</label>
            <div class="dropzone" data-dropzone>
                <input id="file" class="dropzone__input" type="file" name="image" required
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
            <button class="btn" type="submit">Upload image</button>
            <a class="btn btn--ghost" href="${ctx}/home">Cancel</a>
        </div>
    </form>
</section>

<script src="${ctx}/assets/js/chips.js" defer></script>
<script src="${ctx}/assets/js/upload.js" defer></script>
<%@ include file="partials/footer.jsp" %>

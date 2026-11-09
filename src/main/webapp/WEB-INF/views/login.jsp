<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="auth">
    <div class="auth__card">
        <h1>Welcome back</h1>
        <p class="muted">Sign in to upload images, edit tags and manage your gallery.</p>

        <c:if test="${not empty error}">
            <div class="alert alert--error" role="alert"><c:out value="${error}"/></div>
        </c:if>

        <form class="form" method="post" action="${ctx}/login">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="next" value="${fn:escapeXml(next)}">
            <div class="field">
                <label for="username">Username</label>
                <input id="username" name="username" type="text" required maxlength="50"
                       autocomplete="username" value="${fn:escapeXml(usernameValue)}">
            </div>
            <div class="field">
                <label for="password">Password</label>
                <input id="password" name="password" type="password" required maxlength="72"
                       autocomplete="current-password">
            </div>
            <button class="btn btn--block" type="submit">Sign in</button>
        </form>

        <p class="muted auth__switch">New here? <a href="${ctx}/register">Create an account</a>.</p>
    </div>
</section>

<%@ include file="partials/footer.jsp" %>

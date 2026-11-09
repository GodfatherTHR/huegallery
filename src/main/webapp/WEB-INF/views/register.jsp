<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="partials/header.jsp" %>

<section class="auth">
    <div class="auth__card">
        <h1>Create an account</h1>
        <p class="muted">Passwords are hashed with bcrypt. Usernames may use letters, numbers, dots,
            underscores and hyphens.</p>

        <c:if test="${not empty error}">
            <div class="alert alert--error" role="alert"><c:out value="${error}"/></div>
        </c:if>

        <form class="form" method="post" action="${ctx}/register">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <div class="field">
                <label for="username">Username</label>
                <input id="username" name="username" type="text" required minlength="3" maxlength="50"
                       pattern="[A-Za-z0-9._-]{3,50}" autocomplete="username"
                       value="${fn:escapeXml(usernameValue)}">
                <p class="hint">3&ndash;50 characters: letters, numbers, dot, underscore or hyphen.</p>
            </div>
            <div class="field">
                <label for="password">Password</label>
                <input id="password" name="password" type="password" required minlength="8" maxlength="72"
                       autocomplete="new-password">
                <p class="hint">At least 8 characters.</p>
            </div>
            <div class="field">
                <label for="confirmPassword">Confirm password</label>
                <input id="confirmPassword" name="confirmPassword" type="password" required minlength="8"
                       maxlength="72" autocomplete="new-password">
            </div>
            <button class="btn btn--block" type="submit">Create account</button>
        </form>

        <p class="muted auth__switch">Already registered? <a href="${ctx}/login">Sign in</a>.</p>
    </div>
</section>

<%@ include file="partials/footer.jsp" %>

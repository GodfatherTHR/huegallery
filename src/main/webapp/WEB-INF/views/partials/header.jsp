<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title><c:out value="${empty pageTitle ? 'Gallery' : pageTitle}"/> &middot; HueGallery</title>
    <meta name="description" content="Browse, tag and search images by colour similarity.">
    <link rel="stylesheet" href="${ctx}/assets/css/style.css">
</head>
<body data-ctx="${ctx}">
<a class="skip-link" href="#main">Skip to content</a>
<header class="site-header">
    <div class="container site-header__inner">
        <a class="brand" href="${ctx}/home">
            <span class="brand__mark" aria-hidden="true"></span>
            <span>HueGallery</span>
        </a>
        <nav class="site-nav" aria-label="Main">
            <a href="${ctx}/home">Gallery</a>
            <a href="${ctx}/ai-search" ${pageTitle == 'AI image search' ? 'aria-current="page"' : ''}>AI search</a>
            <a href="${ctx}/search-by-example">Search by example</a>
            <c:choose>
                <c:when test="${not empty sessionScope.currentUser}">
                    <a href="${ctx}/upload">Upload</a>
                    <a href="${ctx}/my-images">My images</a>
                    <span class="site-nav__user"><c:out value="${sessionScope.currentUser.username}"/></span>
                    <form class="inline-form" method="post" action="${ctx}/logout">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <button type="submit" class="btn btn--ghost btn--small">Sign out</button>
                    </form>
                </c:when>
                <c:otherwise>
                    <a href="${ctx}/login">Sign in</a>
                    <a class="btn btn--small" href="${ctx}/register">Create account</a>
                </c:otherwise>
            </c:choose>
        </nav>
    </div>
</header>
<main class="container" id="main">
<c:if test="${not empty sessionScope.flash}">
    <div class="alert ${sessionScope.flash.error ? 'alert--error' : 'alert--success'}" role="status">
        <c:out value="${sessionScope.flash.text}"/>
    </div>
    <c:remove var="flash" scope="session"/>
</c:if>

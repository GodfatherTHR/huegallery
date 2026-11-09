<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Page not found &middot; HueGallery</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
<main class="error-page" id="main">
    <p class="error-page__code">404</p>
    <h1>Page not found</h1>
    <p class="muted">The page or image you were looking for does not exist, or it may have been deleted.</p>
    <a class="btn" href="${pageContext.request.contextPath}/home">Back to the gallery</a>
</main>
</body>
</html>

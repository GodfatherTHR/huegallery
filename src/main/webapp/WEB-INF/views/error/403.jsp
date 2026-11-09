<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Not allowed &middot; HueGallery</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
<main class="error-page" id="main">
    <p class="error-page__code">403</p>
    <h1>Not allowed</h1>
    <p class="muted">You do not have permission to do that. If you were signed out, reload the page and try again.</p>
    <a class="btn" href="${pageContext.request.contextPath}/home">Back to the gallery</a>
</main>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Something went wrong &middot; HueGallery</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
<main class="error-page" id="main">
    <p class="error-page__code">500</p>
    <h1>Something went wrong</h1>
    <p class="muted">The request could not be completed. The details have been written to the server log.</p>
    <a class="btn" href="${pageContext.request.contextPath}/home">Back to the gallery</a>
</main>
</body>
</html>

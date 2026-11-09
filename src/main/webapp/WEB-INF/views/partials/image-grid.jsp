<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:choose>
    <c:when test="${empty pageResult.items}">
        <div class="empty-state">
            <p class="empty-state__title"><c:out value="${emptyMessage}"/></p>
            <c:if test="${not empty emptyHint}">
                <p class="muted"><c:out value="${emptyHint}"/></p>
            </c:if>
            <c:if test="${not empty emptyActionUrl}">
                <a class="btn" href="${emptyActionUrl}"><c:out value="${emptyActionLabel}"/></a>
            </c:if>
        </div>
    </c:when>
    <c:otherwise>
        <ul class="grid">
            <c:forEach var="img" items="${pageResult.items}" varStatus="loop">
                <li class="card" style="--i:${loop.index}">
                    <a class="card__link" href="<c:url value='/photo/${img.imageId}'/>">
                        <span class="card__media">
                            <img class="card__image"
                                 src="<c:url value='/thumb/${img.imageId}'/>"
                                 alt="${fn:escapeXml(img.title)}"
                                 loading="lazy" decoding="async">
                        </span>
                        <span class="card__body">
                            <span class="card__title"><c:out value="${img.title}"/></span>
                            <span class="card__meta"><c:out value="${img.username}"/></span>
                        </span>
                    </a>
                </li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>

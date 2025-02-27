<%@ include file="/WEB-INF/jsp-include/tag-import.jsp" %>

<%@ attribute name="links" type="java.util.List"
              rtexprvalue="true" required="true" %>
<%@ attribute name="marker" type="org.zfin.marker.Marker" rtexprvalue="true" required="true" %>
<%@ attribute name="title" type="java.lang.String" rtexprvalue="true" required="false" %>

<c:if test="${empty title}">

    <c:set var="title">
        Genome Resources:
    </c:set>
    <c:if test="${marker.markerType.displayName.contains('Construct')}">
        <c:set var="title">
            OTHER&nbsp;<i>${marker.name}</i>&nbsp;${marker.markerType.displayName eq 'cDNA' ? marker.markerType.displayName : fn:toUpperCase(marker.markerType.displayName)}&nbsp;PAGES
        </c:set>

    </c:if>
</c:if>

<c:set var="loggedIn">no</c:set>

<authz:authorize access="hasRole('root')">
    <c:set var="loggedIn" value="true"/>
</authz:authorize>

<c:if test="${loggedIn && marker.genedom}">

    <span class="summaryTitle">${title}</span>
    <td>
        <other-markers marker-id="${marker.zdbID}" edit="editMode">
        </other-markers>
    </td>

</c:if>

<c:if test="${loggedIn eq 'no' || !marker.genedom}">

    <span class="summaryTitle">${title}</span>
    <td>
        <c:set var="lastRefDB" value=""/>
        <c:forEach var="link" items="${links}" varStatus="loop">

            <c:if test="${!link.displayName.contains('VEGA')}">

                <a href="${link.link}">${link.displayName}</a>
                ${link.attributionLink}<c:if test="${!loop.last}">,&nbsp;</c:if>
            </c:if>

        </c:forEach>
    </td>


</c:if>


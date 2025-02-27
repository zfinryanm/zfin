<%@ page import="org.zfin.publication.PublicationType" %>
<%@ include file="/WEB-INF/jsp-include/tag-import.jsp" %>

<z:page>
    <meta name="all-figure-view-page"/> <%-- this is used by the web testing framework to know which page it is --%>

    <c:set var="UNPUBLISHED" value="${PublicationType.UNPUBLISHED}"/>
    <c:set var="CURATION" value="${PublicationType.CURATION}"/>


    <authz:authorize access="hasRole('root')">
        <c:choose>
            <c:when test="${!empty probe}">
                <a class="dropdown-item" href="/action/publication/${publication.zdbID}/all-figures?probeZdbID=${probe.zdbID}">Prototype View</a>
            </c:when>
            <c:otherwise>
                <a class="dropdown-item" href="/action/publication/${publication.zdbID}/all-figures">Prototype View</a>
            </c:otherwise>
        </c:choose>
    </authz:authorize>


    <zfin-figure:publicationInfo publication="${publication}"
                                 submitters="${submitters}"
                                 showThisseInSituLink="${showThisseInSituLink}"
                                 showErrataAndNotes="${showErrataAndNotes}"/>

    <c:if test="${!empty probe}">
    <div class="summary">
        <table class="primary-entity-attributes">
            <tr>
                <th>Probe:</th>
                <td>
                    <zfin:link entity="${probe}"/>

                   <c:if test="${!empty probe.rating}">
                    &nbsp; <strong><a href="/zf_info/stars.html">Quality:</a></strong>
                       <zfin2:starRating rating="${probe.rating}" />
                   </c:if>
                </td>
            </tr>
                <%-- this is deviating from the old figureview, because the nice java tag we have doesn't seem to follow
             that format.  Maybe people will like this better? --%>
            <c:if test="${!empty probeSuppliers}">
                <tr>
                    <th>Supplier:</th>
                    <td><c:forEach var="supplier" items="${probeSuppliers}">
                        ${supplier.linkWithAttributionAndOrderThis}
                    </c:forEach></td>
                </tr>
            </c:if>
        </table>
    </div>
    </c:if>

    <c:forEach var="figure" items="${figures}">
        <zfin-figure:imagesAndCaption figure="${figure}"
                                      autoplayVideo="false"
                                      showMultipleMediumSizedImages="${showMultipleMediumSizedImages}"
                                      showCaption="${true}">

            <zfin-figure:expressionSummary summary="${expressionSummaryMap[figure]}" suppressProbe="true"/>

            <c:if test="${!empty expressionSummaryMap[figure].startStage}">
                <div style="margin-top: 1em;">
                    <a href="/${figure.zdbID}#expDetail">Expression / Labeling details</a>
                </div>
            </c:if>

            <zfin-figure:phenotypeSummary summary="${phenotypeSummaryMap[figure]}" />

            <c:if test="${!empty phenotypeSummaryMap[figure].fish}">
                <div style="margin-top: 1em;">
                    <a href="/${figure.zdbID}#phenoDetail">Phenotype details</a>
                </div>
            </c:if>
            <zfin-figure:constructLinks figure="${figure}"/>

        </zfin-figure:imagesAndCaption>
    </c:forEach>

    <c:choose>
        <c:when test="${publication.canShowImages && publication.type != UNPUBLISHED}">
            <zfin2:acknowledgment publication="${publication}" showElsevierMessage="${showElsevierMessage}" hasAcknowledgment="${hasAcknowledgment}"/>
        </c:when>
        <c:otherwise>
            <zfin2:subsection>
                <zfin-figure:journalAbbrev publication="${publication}"/>
            </zfin2:subsection>
        </c:otherwise>
    </c:choose>

    <script>
        jQuery(document).ready(function() {
            jQuery('.fish-label').tipsy({gravity:'sw', opacity:1, delayIn:750, delayOut:200});
        });
    </script>
</z:page>
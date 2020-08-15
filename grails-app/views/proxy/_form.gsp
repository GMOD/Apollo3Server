<%@ page import="org.bbop.apollo.system.Proxy" %>


<div class="fieldcontain ${hasErrors(bean: proxy, field: 'referenceUrl', 'error')} required">
	<label for="referenceUrl">
		<g:message code="proxy.referenceUrl.label" default="Reference Url" />
		<span class="required-indicator">*</span>
	</label>
	<g:field type="url" name="referenceUrl" required="" value="${proxy?.referenceUrl}" size="80"/>

</div>

<div class="fieldcontain ${hasErrors(bean: proxy, field: 'targetUrl', 'error')} required">
	<label for="targetUrl">
		<g:message code="proxy.targetUrl.label" default="Target Url" />
		<span class="required-indicator">*</span>
	</label>
	<g:field type="url" name="targetUrl" required="" value="${proxy?.targetUrl}" size="80"/>

</div>

<div class="fieldcontain ${hasErrors(bean: proxy, field: 'active', 'error')} ">
	<label for="active">
		<g:message code="proxy.active.label" default="Active" />

	</label>
	<g:checkBox name="active" value="${proxy?.active}" />

</div>


<div class="fieldcontain ${hasErrors(bean: proxy, field: 'fallbackOrder', 'error')} ">
	<label for="fallbackOrder">
		<g:message code="proxy.fallbackOrder.label" default="Fallback Order" />
		
	</label>
	<g:field name="fallbackOrder" type="number" value="${proxy.fallbackOrder}"/>

</div>

<br/>

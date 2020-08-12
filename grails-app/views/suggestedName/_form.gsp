<%@ page import="org.bbop.apollo.attributes.SuggestedName" %>



<div class="fieldcontain ${hasErrors(bean: suggestedName, field: 'name', 'error')} required">
	<label for="name">
		<g:message code="suggestedName.name.label" default="Name" />
		<span class="required-indicator">*</span>
	</label>
	<g:textField name="name" required="" value="${suggestedName?.name}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: suggestedName, field: 'metadata', 'error')} ">
	<label for="metadata">
		<g:message code="suggestedName.metadata.label" default="Metadata" />
		
	</label>
	<g:textField name="metadata" value="${suggestedName?.metadata}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: suggestedName, field: 'featureTypes', 'error')} ">
	<label for="featureTypes">
		<g:message code="suggestedName.featureTypes.label" default="Feature Types" />
		
	</label>
	<g:select name="featureTypes" from="${org.bbop.apollo.attributes.FeatureType.list()}"
              multiple="multiple"
              optionKey="id" size="10"
              optionValue="display"
              value="${suggestedName?.featureTypes*.id}" class="many-to-many"/>

</div>

<div class="fieldcontain ${hasErrors(bean: suggestedName, field: 'organisms', 'error')} ">
	<label for="organisms">
		<g:message code="suggestedName.organisms.label" default="Organisms" />

	</label>
	<g:select name="organisms" from="${org.bbop.apollo.organism.Organism.list()}"
			  multiple="multiple"
			  optionKey="id" size="10"
			  optionValue="commonName"
			  value="${organismFilters?.organism?.id}" class="many-to-many"/>

</div>

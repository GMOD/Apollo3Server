<%@ page import="org.bbop.apollo.attributes.CannedValue" %>



<div class="fieldcontain ${hasErrors(bean: cannedValue, field: 'label', 'error')} required">
	<label for="label">
		<g:message code="cannedValue.label.label" default="Label" />
		<span class="required-indicator">*</span>
	</label>
	<g:textField name="label" required="" value="${cannedValue?.label}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedValue, field: 'metadata', 'error')} ">
	<label for="metadata">
		<g:message code="cannedValue.metadata.label" default="Metadata" />
		
	</label>
	<g:textField name="metadata" value="${cannedValue?.metadata}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedValue, field: 'featureTypes', 'error')} ">
	<label for="featureTypes">
		<g:message code="cannedValue.featureTypes.label" default="Feature Types" />
		
	</label>
	<g:select name="featureTypes" from="${org.bbop.apollo.attributes.FeatureType.list()}" multiple="multiple" optionKey="id" size="10" value="${cannedValue?.featureTypes*.id}" class="many-to-many" optionValue="display"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedValue, field: 'organisms', 'error')} ">
	<label for="organisms">
		<g:message code="cannedValue.organisms.label" default="Organisms" />

	</label>
	<g:select name="organisms" from="${org.bbop.apollo.organism.Organism.list()}"
			  multiple="multiple"
			  optionKey="id" size="10"
			  optionValue="commonName"
			  value="${organismFilters?.organism?.id}" class="many-to-many"/>

</div>

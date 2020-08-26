<%@ page import="org.bbop.apollo.attributes.CannedKey" %>



<div class="fieldcontain ${hasErrors(bean: cannedKey, field: 'label', 'error')} required">
    <label for="label">
        <g:message code="cannedKey.label.label" default="Label"/>
        <span class="required-indicator">*</span>
    </label>
    <g:textField name="label" required="" value="${cannedKey?.label}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedKey, field: 'metadata', 'error')} ">
    <label for="metadata">
        <g:message code="cannedKey.metadata.label" default="Metadata"/>

    </label>
    <g:textField name="metadata" value="${cannedKey?.metadata}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedKey, field: 'featureTypes', 'error')} ">
    <label for="featureTypes">
        <g:message code="cannedKey.featureTypes.label" default="Feature Types"/>

    </label>
    <g:select name="featureTypes" from="${org.bbop.apollo.attributes.FeatureType.list()}" multiple="multiple" optionKey="id"
              size="10" value="${cannedKey?.featureTypes*.id}" class="many-to-many" optionValue="display"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedKey, field: 'organisms', 'error')} ">
    <label for="organisms">
        <g:message code="cannedKey.organisms.label" default="Organisms" />

    </label>
    <g:select name="organisms" from="${org.bbop.apollo.organism.Organism.list()}"
              multiple="multiple"
              optionKey="id" size="10"
              optionValue="commonName"
              value="${organismFilters?.organism?.id}" class="many-to-many"/>

</div>


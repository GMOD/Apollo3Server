<%@ page import="org.bbop.apollo.attributes.CannedComment" %>



<div class="fieldcontain ${hasErrors(bean: cannedComment, field: 'comment', 'error')} required">
	<label for="comment">
		<g:message code="cannedComment.comment.label" default="Comment" />
		<span class="required-indicator">*</span>
	</label>
	<g:textField name="comment" required="" value="${cannedComment?.comment}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedComment, field: 'metadata', 'error')} ">
	<label for="metadata">
		<g:message code="cannedComment.metadata.label" default="Metadata" />
		
	</label>
	<g:textField name="metadata" value="${cannedComment?.metadata}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedComment, field: 'featureTypes', 'error')} ">
	<label for="featureTypes">
		<g:message code="cannedComment.featureTypes.label" default="Feature Types" />
		
	</label>
	<g:select name="featureTypes" from="${org.bbop.apollo.attributes.FeatureType.list()}"
              multiple="multiple"
              optionKey="id" size="10"
              optionValue="display"
              value="${cannedComment?.featureTypes*.id}" class="many-to-many"/>

</div>

<div class="fieldcontain ${hasErrors(bean: cannedComment, field: 'organisms', 'error')} ">
	<label for="organisms">
		<g:message code="cannedComment.organisms.label" default="Organisms" />

	</label>
	<g:select name="organisms" from="${org.bbop.apollo.organism.Organism.list()}"
			  multiple="multiple"
			  optionKey="id" size="10"
			  optionValue="commonName"
			  value="${organismFilters?.organism?.id}" class="many-to-many"/>

</div>

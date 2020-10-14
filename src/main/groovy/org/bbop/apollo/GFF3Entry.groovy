package org.bbop.apollo

import org.bbop.apollo.gwt.shared.FeatureStringEnum

/**
 * Created by Deepak on 3/9/15.
 */
class GFF3Entry {

    private String seqId;
    private String source;
    private String type;
    private int start;
    private int end;
    private String score;
    private String strand;
    private String phase;
    private Map<String, String> attributes;

    GFF3Entry(String seqId, String source, String type, int start, int end, String score, String strand, String phase = ".") {
        this.seqId = seqId;
        this.source = source;
        this.type = type;
        this.start = start;
        this.end = end;
        this.score = score;
        this.strand = strand;
        this.phase = phase;
        this.attributes = new HashMap<String, String>();
    }

    String getSeqId() {
        return seqId;
    }

    void setSeqId(String seqId) {
        this.seqId = seqId;
    }

    String getSource() {
        return source;
    }

    void setSource(String source) {
        this.source = source;
    }

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    int getStart() {
        return start;
    }

    void setStart(int start) {
        this.start = start;
    }

    int getEnd() {
        return end;
    }

    void setEnd(int end) {
        this.end = end;
    }

    String getScore() {
        return score;
    }

    void setScore(String score) {
        this.score = score;
    }

    String getStrand() {
        return strand;
    }

    void setStrand(String strand) {
        this.strand = strand;
    }

    String getPhase() {
        return phase;
    }

    void setPhase(String phase) {
        this.phase = phase;
    }

    Map<String, String> getAttributes() {
        return attributes;
    }

    void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    void addAttribute(String key, String value) {
        attributes.put(key, value);
    }

    String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(String.format("%s\t%s\t%s\t%d\t%d\t%s\t%s\t%s\t", getSeqId(), getSource(), getType(), getStart(), getEnd(), getScore(), getStrand(), getPhase()));
        Iterator<Map.Entry<String, String>> iter = attributes.entrySet().iterator();
        if (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            buf.append(entry.getKey());
            buf.append("=");
            buf.append(entry.getValue());
            while (iter.hasNext()) {
                entry = iter.next();
                buf.append(";");
                buf.append(entry.getKey());
                buf.append("=");
                buf.append(entry.getValue());
            }
        }
        return buf.toString();
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        GFF3Entry gff3Entry = (GFF3Entry) o

        if (end != gff3Entry.end) return false
        if (start != gff3Entry.start) return false
        if(!attributes.get(FeatureStringEnum.EXPORT_ID.value).equals(gff3Entry.attributes.get(FeatureStringEnum.EXPORT_ID.value)))
//        if (attributes != gff3Entry.attributes) return false
        if (seqId != gff3Entry.seqId) return false
        if (type != gff3Entry.type) return false

        return true
    }

    int hashCode() {
        int result
        result = (seqId != null ? seqId.hashCode() : 0)
        result = 31 * result + (type != null ? type.hashCode() : 0)
        result = 31 * result + start
        result = 31 * result + end
        result = 31 * result + (attributes != null ? attributes.get(FeatureStringEnum.EXPORT_ID.value) : 0)
        return result
    }
}
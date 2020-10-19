package org.bbop.apollo.preference


/**
 * This class mirrors UserOrganismPreference, but NEVER persists, making it lighter-weight
 */
class UserOrganismPreferenceDTO {

    Long id
    OrganismDTO organism
    Boolean currentOrganism  // this means the "active" client token
    Boolean nativeTrackList
    SequenceDTO sequence
    UserDTO user
    Integer startbp
    Integer endbp
    String clientToken


    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o.instanceOf(UserOrganismPreferenceDTO))) return false

        UserOrganismPreferenceDTO that = (UserOrganismPreferenceDTO) o

        if (clientToken != that.clientToken) return false

        return true
    }

    int hashCode() {
        int result = clientToken.hashCode()
//        result = organism.hashCode()
//        result = 31 * result + sequence.hashCode()
//        result = 31 * result + id.hashCode()
//        result = 31 * result + user.hashCode()
        return result
    }
}

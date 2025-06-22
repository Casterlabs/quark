package co.casterlabs.quark.auth;

public record User(
    String id,
    boolean isAdmin,
    String[] playbackRegexes
) {

    public void checkAdmin() throws AuthenticationException {
        if (!this.isAdmin) {
            throw new AuthenticationException("Not an admin.");
        }
    }

    public void checkPlayback(String id) throws AuthenticationException {
        if (this.isAdmin) return;

        for (String regex : this.playbackRegexes) {
            if (id.matches(regex)) return;
        }

        throw new AuthenticationException("Playback not allowed.");
    }

}

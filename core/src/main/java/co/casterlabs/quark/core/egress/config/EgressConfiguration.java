package co.casterlabs.quark.core.egress.config;

import java.io.IOException;

import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.util.DependencyException;

public interface EgressConfiguration {

    public void create(Session session) throws IOException, DependencyException;

}

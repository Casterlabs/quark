package co.casterlabs.quark.egress.config;

import java.io.IOException;

import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.util.DependencyException;

public interface EgressConfiguration {

    public void create(Session session) throws IOException, DependencyException;

}

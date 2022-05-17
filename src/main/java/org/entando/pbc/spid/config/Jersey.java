package org.entando.pbc.spid.config;

import org.entando.pbc.spid.web.KeepMeAlive;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.ApplicationPath;

@Component
@ApplicationPath("/api")
public class Jersey extends ResourceConfig {

  @PostConstruct
  private void init() {
    registerClasses(KeepMeAlive.class);
  }

  public Jersey() {
//    packages("org.entando"); stacktrace!
  }

}

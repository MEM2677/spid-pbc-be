package org.entando.pbc.spid.web;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

@Path("greeting")
public class KeepMeAlive {

  @GET
  public String getGreeting(@QueryParam("name") String name) {
    return name;
  }

}

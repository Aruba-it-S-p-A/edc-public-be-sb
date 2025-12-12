package edc.config.security.roles;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoleConfig {


  @Value("${app.security.custom.roles.admin:ROLE_ADMIN}")
  public String ROLE_ADMIN;

  @Value("${app.security.custom.roles.adminTenant:ROLE_ADMIN_TENANT}")
  public String ROLE_ADMIN_TENANT;

  @Value("${app.security.custom.roles.userParticipant:ROLE_USER_PARTICIPANT}")
  public String ROLE_USER_PARTICIPANT;


}

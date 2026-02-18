package de.caritas.cob.agencyservice.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomSwaggerUIController {
  @Value("${springdoc.swagger-ui.path:${springfox.docuPath:/swagger-ui.html}}")
  private String swaggerUiPath;

  @RequestMapping(value = "${springfox.docuPath}")
  public String index() {
    return "redirect:" + swaggerUiPath;
  }
}

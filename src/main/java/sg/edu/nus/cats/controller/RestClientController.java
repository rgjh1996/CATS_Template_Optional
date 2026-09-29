package sg.edu.nus.cats.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RestClientController {
  @GetMapping("/rest-client")
  public String client() {
    return "api/client";
  }
}

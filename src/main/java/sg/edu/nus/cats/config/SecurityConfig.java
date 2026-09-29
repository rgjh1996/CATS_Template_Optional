package sg.edu.nus.cats.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.*;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.edu.nus.cats.repository.UserRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  org.springframework.security.core.userdetails.UserDetailsService userDetailsService(
      UserRepository users) {
    return username -> {
      var u =
          users
              .findByUsername(username)
              .orElseThrow(
                  () ->
                      new org.springframework.security.core.userdetails.UsernameNotFoundException(
                          "User not found"));
      return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
          .password(u.getPasswordHash())
          .roles(u.getRoles().stream().map(Enum::name).toArray(String[]::new))
          .disabled(!u.getEnabled())
          .build();
    };
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, UserRepository users, SecurityContextRepository contexts)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/css/**", "/js/**", "/employee/login", "/admin/login", "/error")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/manager/**")
                    .hasRole("MANAGER")
                    .requestMatchers(
                        "/applications/**",
                        "/employee",
                        "/claims/**",
                        "/ledger/**",
                        "/api/**",
                        "/rest-client")
                    .hasAnyRole("EMPLOYEE", "MANAGER")
                    .anyRequest()
                    .authenticated())
        .securityContext(c -> c.securityContextRepository(contexts))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, exception) -> {
                      if (request.getServletPath().startsWith("/api/")
                          || request.getServletPath().startsWith("/data/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Please sign in.\"}");
                      } else
                        new LoginUrlAuthenticationEntryPoint("/employee/login")
                            .commence(request, response, exception);
                    }))
        .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/employee/login"));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
              var found = users.findByUsername(auth.getName());
              if (found.isEmpty() || !found.get().getEnabled()) {
                SecurityContextHolder.clearContext();
                var session = req.getSession(false);
                if (session != null) session.invalidate();
                res.sendRedirect(req.getContextPath() + "/employee/login");
                return;
              }
              var u = found.get();
              var authorities =
                  u.getRoles().stream()
                      .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                      .toList();
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      UsernamePasswordAuthenticationToken.authenticated(
                          u.getUsername(), null, authorities));
              req.getSession()
                  .setAttribute(
                      "user",
                      new WebConfig.SessionUser(u.getId(), u.getName(), Set.copyOf(u.getRoles())));
            }
            chain.doFilter(req, res);
          }
        },
        AuthorizationFilter.class);
    return http.build();
  }
}

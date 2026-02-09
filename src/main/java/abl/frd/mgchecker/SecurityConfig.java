package abl.frd.mgchecker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable() // Keep this disabled for now so the upload works after login
                .authorizeRequests()
                .antMatchers("/login", "/css/**", "/js/**").permitAll() // Allow login assets
                .anyRequest().authenticated() // This forces login for "/" and "/upload"
                .and()
                .formLogin()
                .defaultSuccessUrl("/", true) // Go to upload page after login
                .permitAll()
                .and()
                .logout().permitAll();
    }
}

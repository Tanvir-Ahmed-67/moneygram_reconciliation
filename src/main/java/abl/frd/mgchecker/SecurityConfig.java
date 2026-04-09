package abl.frd.mgchecker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                // 1. Important: Permit access to your custom login page and static assets
                .antMatchers("/login", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                // 2. Point to your @GetMapping("/login")
                .loginPage("/login")
                // 3. This is the POST URL that Spring Security handles automatically
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/files", true)
                .failureUrl("/login?error=true") // Redirect here on wrong credentials
                .permitAll()
                .and()
                .logout()
                // 4. Match the path used in your logout button
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll();
    }
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = org.springframework.security.core.userdetails.User.withUsername("admin")
                .password(passwordEncoder.encode("M@g#123"))  // encode password
                .roles("admin")
                .build();
        return new org.springframework.security.provisioning.InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

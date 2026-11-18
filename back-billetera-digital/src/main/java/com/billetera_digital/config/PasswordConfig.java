package com.billetera_digital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * Bean para el codificador de contraseñas.
     * Utiliza BCryptPasswordEncoder para codificar las contraseñas de manera segura.
     *
     * @return un PasswordEncoder que utiliza BCrypt para codificar contraseñas
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
